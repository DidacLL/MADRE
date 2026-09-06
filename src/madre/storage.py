"""Exclusive runtime database ownership and durable work storage."""

import hashlib
import json
import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime
from pathlib import Path

from filelock import FileLock, Timeout

from madre.contracts import WorkAttempt, WorkFailure, WorkRecord, WorkSubmission

_STORAGE_DDL = """
CREATE TABLE runtime_work (
    id TEXT PRIMARY KEY,
    application_id TEXT NOT NULL,
    capability_id TEXT NOT NULL,
    input_json TEXT NOT NULL,
    eligible_at TEXT,
    constraints_json TEXT NOT NULL,
    idempotency_key TEXT,
    status TEXT NOT NULL CHECK (status IN ('accepted', 'running', 'succeeded', 'failed')),
    submitted_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    result_json TEXT,
    error_code TEXT,
    error_message TEXT
);

CREATE UNIQUE INDEX runtime_work_idempotency
ON runtime_work(application_id, idempotency_key)
WHERE idempotency_key IS NOT NULL;

CREATE TABLE runtime_attempt (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    status TEXT NOT NULL CHECK (status IN ('running', 'succeeded', 'failed')),
    started_at TEXT NOT NULL,
    completed_at TEXT,
    result_json TEXT,
    error_code TEXT,
    error_message TEXT,
    PRIMARY KEY (work_id, number)
);
"""


def _format_id() -> int:
    fingerprint = int.from_bytes(hashlib.sha256(_STORAGE_DDL.encode()).digest()[:4], "big")
    return fingerprint & 0x7FFFFFFF or 1


_STORAGE_FORMAT_ID = _format_id()


def utc_now() -> datetime:
    return datetime.now(UTC)


def _json(value: object) -> str:
    return json.dumps(value, separators=(",", ":"), sort_keys=True)


def _connect(database: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(database)
    connection.row_factory = sqlite3.Row
    return connection


def _discard_incompatible_development_storage(database: Path) -> None:
    for suffix in ("", "-wal", "-shm", "-journal"):
        Path(f"{database}{suffix}").unlink(missing_ok=True)


@contextmanager
def open_database(data_dir: Path) -> Iterator[sqlite3.Connection]:
    data_dir = data_dir.resolve()
    data_dir.mkdir(parents=True, exist_ok=True)
    lock = FileLock(data_dir / "runtime.lock", timeout=0)
    try:
        lock.acquire()
    except Timeout as exc:
        raise RuntimeError("another MADRE runtime owns this data directory") from exc
    try:
        database = data_dir / "runtime.sqlite3"
        database_existed = database.exists()
        connection = _connect(database)
        try:
            format_id = connection.execute("PRAGMA user_version").fetchone()[0]
            incompatible = format_id not in (0, _STORAGE_FORMAT_ID) or (
                database_existed and format_id == 0
            )
            if incompatible:
                connection.close()
                _discard_incompatible_development_storage(database)
                connection = _connect(database)
                format_id = 0

            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("PRAGMA journal_mode=WAL")
            if format_id == 0:
                with connection:
                    connection.executescript(_STORAGE_DDL)
                    connection.execute(f"PRAGMA user_version={_STORAGE_FORMAT_ID}")
            yield connection
        finally:
            connection.close()
    finally:
        lock.release()


class WorkStore:
    def __init__(self, connection: sqlite3.Connection):
        self.connection = connection

    def create(
        self,
        work_id: str,
        submission: WorkSubmission,
        submitted_at: datetime,
        *,
        idempotency_key: str | None = None,
    ) -> bool:
        try:
            with self.connection:
                self.connection.execute(
                    """
                    INSERT INTO runtime_work (
                        id, application_id, capability_id, input_json, eligible_at,
                        constraints_json, idempotency_key, status, submitted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 'accepted', ?)
                    """,
                    (
                        work_id,
                        submission.application_id,
                        submission.capability_id,
                        _json(submission.input),
                        submission.eligible_at.isoformat() if submission.eligible_at else None,
                        submission.constraints.model_dump_json(),
                        idempotency_key,
                        submitted_at.isoformat(),
                    ),
                )
        except sqlite3.IntegrityError:
            if (
                idempotency_key is None
                or self._idempotency_work_id(submission.application_id, idempotency_key) is None
            ):
                raise
            return False
        return True

    def get_by_idempotency_key(
        self, application_id: str, idempotency_key: str
    ) -> WorkRecord | None:
        work_id = self._idempotency_work_id(application_id, idempotency_key)
        return self.get(work_id) if work_id is not None else None

    def next_eligible(self, now: datetime) -> str | None:
        row = self.connection.execute(
            """
            SELECT id
            FROM runtime_work
            WHERE status = 'accepted' AND (eligible_at IS NULL OR eligible_at <= ?)
            ORDER BY COALESCE(eligible_at, submitted_at), submitted_at, id
            LIMIT 1
            """,
            (now.isoformat(),),
        ).fetchone()
        return str(row["id"]) if row is not None else None

    def next_eligibility(self) -> datetime | None:
        row = self.connection.execute(
            """
            SELECT MIN(eligible_at) AS eligible_at
            FROM runtime_work
            WHERE status = 'accepted' AND eligible_at IS NOT NULL
            """
        ).fetchone()
        value = row["eligible_at"]
        return datetime.fromisoformat(value) if value is not None else None

    def start_attempt(self, work_id: str, started_at: datetime) -> int | None:
        with self.connection:
            updated = self.connection.execute(
                """
                UPDATE runtime_work
                SET status = 'running', started_at = COALESCE(started_at, ?),
                    completed_at = NULL, result_json = NULL,
                    error_code = NULL, error_message = NULL
                WHERE id = ? AND status = 'accepted'
                """,
                (started_at.isoformat(), work_id),
            )
            if updated.rowcount != 1:
                return None

            row = self.connection.execute(
                """
                SELECT COALESCE(MAX(number), 0) + 1 AS number
                FROM runtime_attempt
                WHERE work_id = ?
                """,
                (work_id,),
            ).fetchone()
            number = int(row["number"])
            self.connection.execute(
                """
                INSERT INTO runtime_attempt (work_id, number, status, started_at)
                VALUES (?, ?, 'running', ?)
                """,
                (work_id, number, started_at.isoformat()),
            )
        return number

    def succeed(
        self,
        work_id: str,
        attempt_number: int,
        result: dict[str, object],
        completed_at: datetime,
    ) -> None:
        result_json = _json(result)
        with self.connection:
            self.connection.execute(
                """
                UPDATE runtime_attempt
                SET status = 'succeeded', completed_at = ?, result_json = ?,
                    error_code = NULL, error_message = NULL
                WHERE work_id = ? AND number = ?
                """,
                (completed_at.isoformat(), result_json, work_id, attempt_number),
            )
            self.connection.execute(
                """
                UPDATE runtime_work
                SET status = 'succeeded', completed_at = ?, result_json = ?,
                    error_code = NULL, error_message = NULL
                WHERE id = ?
                """,
                (completed_at.isoformat(), result_json, work_id),
            )

    def fail(
        self,
        work_id: str,
        failure: WorkFailure,
        completed_at: datetime,
        *,
        attempt_number: int | None = None,
    ) -> None:
        with self.connection:
            if attempt_number is not None:
                self.connection.execute(
                    """
                    UPDATE runtime_attempt
                    SET status = 'failed', completed_at = ?, result_json = NULL,
                        error_code = ?, error_message = ?
                    WHERE work_id = ? AND number = ?
                    """,
                    (
                        completed_at.isoformat(),
                        failure.code,
                        failure.message,
                        work_id,
                        attempt_number,
                    ),
                )
            self.connection.execute(
                """
                UPDATE runtime_work
                SET status = 'failed', completed_at = ?, result_json = NULL,
                    error_code = ?, error_message = ?
                WHERE id = ?
                """,
                (completed_at.isoformat(), failure.code, failure.message, work_id),
            )

    def fail_interrupted_attempts(self, completed_at: datetime) -> int:
        failure = WorkFailure(
            code="interrupted",
            message=(
                "runtime stopped before durable completion was recorded; "
                "capability outcome may be unknown"
            ),
        )
        completed = completed_at.isoformat()
        with self.connection:
            running = self.connection.execute(
                "SELECT COUNT(*) AS count FROM runtime_work WHERE status = 'running'"
            ).fetchone()["count"]
            self.connection.execute(
                """
                UPDATE runtime_attempt
                SET status = 'failed', completed_at = ?, error_code = ?, error_message = ?
                WHERE status = 'running'
                """,
                (completed, failure.code, failure.message),
            )
            self.connection.execute(
                """
                UPDATE runtime_work
                SET status = 'failed', completed_at = ?, error_code = ?, error_message = ?
                WHERE status = 'running'
                """,
                (completed, failure.code, failure.message),
            )
        return int(running)

    def get(self, work_id: str) -> WorkRecord | None:
        row = self.connection.execute(
            "SELECT * FROM runtime_work WHERE id = ?", (work_id,)
        ).fetchone()
        if row is None:
            return None
        attempts = self.connection.execute(
            "SELECT * FROM runtime_attempt WHERE work_id = ? ORDER BY number", (work_id,)
        ).fetchall()
        return WorkRecord(
            id=row["id"],
            submission=WorkSubmission(
                application_id=row["application_id"],
                capability_id=row["capability_id"],
                input=json.loads(row["input_json"]),
                eligible_at=row["eligible_at"],
                constraints=json.loads(row["constraints_json"]),
            ),
            status=row["status"],
            submitted_at=row["submitted_at"],
            started_at=row["started_at"],
            completed_at=row["completed_at"],
            result=json.loads(row["result_json"]) if row["result_json"] else None,
            failure=self._failure(row),
            attempts=[
                WorkAttempt(
                    number=attempt["number"],
                    status=attempt["status"],
                    started_at=attempt["started_at"],
                    completed_at=attempt["completed_at"],
                    result=(json.loads(attempt["result_json"]) if attempt["result_json"] else None),
                    failure=self._failure(attempt),
                )
                for attempt in attempts
            ],
        )

    def _idempotency_work_id(self, application_id: str, idempotency_key: str) -> str | None:
        row = self.connection.execute(
            """
            SELECT id
            FROM runtime_work
            WHERE application_id = ? AND idempotency_key = ?
            """,
            (application_id, idempotency_key),
        ).fetchone()
        return str(row["id"]) if row is not None else None

    @staticmethod
    def _failure(row: sqlite3.Row) -> WorkFailure | None:
        if row["error_code"] is None:
            return None
        return WorkFailure(code=row["error_code"], message=row["error_message"])
