"""Exclusive runtime database ownership and durable work storage."""

import hashlib
import json
import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime
from pathlib import Path

from filelock import FileLock, Timeout

from madre.contracts import (
    WorkAttempt,
    WorkCancellation,
    WorkFailure,
    WorkRecord,
    WorkRetry,
    WorkSubmission,
)

_STORAGE_DDL = """
CREATE TABLE runtime_work (
    id TEXT PRIMARY KEY,
    application_id TEXT NOT NULL,
    capability_id TEXT NOT NULL,
    input_json TEXT NOT NULL,
    eligible_at TEXT,
    priority INTEGER NOT NULL CHECK (priority BETWEEN -100 AND 100),
    constraints_json TEXT NOT NULL,
    idempotency_key TEXT,
    status TEXT NOT NULL CHECK (
        status IN ('accepted', 'running', 'succeeded', 'failed', 'cancelled')
    ),
    submitted_at TEXT NOT NULL,
    enqueued_at TEXT NOT NULL,
    started_at TEXT,
    completed_at TEXT,
    result_json TEXT,
    error_code TEXT,
    error_message TEXT,
    cancellation_requested_at TEXT,
    cancellation_disposition TEXT CHECK (
        cancellation_disposition IS NULL
        OR cancellation_disposition IN ('prevented', 'requested_while_running')
    ),
    CHECK (
        (cancellation_requested_at IS NULL AND cancellation_disposition IS NULL)
        OR (cancellation_requested_at IS NOT NULL AND cancellation_disposition IS NOT NULL)
    )
);

CREATE UNIQUE INDEX runtime_work_idempotency
ON runtime_work(application_id, idempotency_key)
WHERE idempotency_key IS NOT NULL;

CREATE TABLE runtime_scheduler_state (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    last_application_id TEXT
);

INSERT INTO runtime_scheduler_state (singleton, last_application_id)
VALUES (1, NULL);

CREATE TABLE runtime_retry (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    idempotency_key TEXT NOT NULL,
    allow_unknown_outcome INTEGER NOT NULL CHECK (allow_unknown_outcome IN (0, 1)),
    requested_at TEXT NOT NULL,
    previous_completed_at TEXT NOT NULL,
    previous_error_code TEXT NOT NULL,
    previous_error_message TEXT NOT NULL,
    PRIMARY KEY (work_id, number),
    UNIQUE (work_id, idempotency_key)
);

CREATE TABLE runtime_attempt (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    retry_number INTEGER CHECK (retry_number IS NULL OR retry_number >= 1),
    status TEXT NOT NULL CHECK (status IN ('running', 'succeeded', 'failed')),
    started_at TEXT NOT NULL,
    completed_at TEXT,
    result_json TEXT,
    error_code TEXT,
    error_message TEXT,
    PRIMARY KEY (work_id, number),
    FOREIGN KEY (work_id, retry_number) REFERENCES runtime_retry(work_id, number)
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
                        priority, constraints_json, idempotency_key, status,
                        submitted_at, enqueued_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'accepted', ?, ?)
                    """,
                    (
                        work_id,
                        submission.application_id,
                        submission.capability_id,
                        _json(submission.input),
                        submission.eligible_at.isoformat() if submission.eligible_at else None,
                        submission.priority,
                        submission.constraints.model_dump_json(),
                        idempotency_key,
                        submitted_at.isoformat(),
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

    def retry_policy_by_key(self, work_id: str, idempotency_key: str) -> bool | None:
        row = self.connection.execute(
            """
            SELECT allow_unknown_outcome
            FROM runtime_retry
            WHERE work_id = ? AND idempotency_key = ?
            """,
            (work_id, idempotency_key),
        ).fetchone()
        return bool(row["allow_unknown_outcome"]) if row is not None else None

    def requeue_failed(
        self,
        work_id: str,
        idempotency_key: str,
        allow_unknown_outcome: bool,
        requested_at: datetime,
    ) -> int | None:
        with self.connection:
            previous = self.connection.execute(
                """
                SELECT completed_at, error_code, error_message
                FROM runtime_work
                WHERE id = ? AND status = 'failed'
                """,
                (work_id,),
            ).fetchone()
            if previous is None:
                return None
            if (
                previous["completed_at"] is None
                or previous["error_code"] is None
                or previous["error_message"] is None
            ):
                raise RuntimeError("failed work lacks durable terminal evidence")

            row = self.connection.execute(
                """
                SELECT COALESCE(MAX(number), 0) + 1 AS number
                FROM runtime_retry
                WHERE work_id = ?
                """,
                (work_id,),
            ).fetchone()
            number = int(row["number"])
            self.connection.execute(
                """
                INSERT INTO runtime_retry (
                    work_id, number, idempotency_key, allow_unknown_outcome, requested_at,
                    previous_completed_at, previous_error_code, previous_error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    work_id,
                    number,
                    idempotency_key,
                    int(allow_unknown_outcome),
                    requested_at.isoformat(),
                    previous["completed_at"],
                    previous["error_code"],
                    previous["error_message"],
                ),
            )
            updated = self.connection.execute(
                """
                UPDATE runtime_work
                SET status = 'accepted', enqueued_at = ?, completed_at = NULL,
                    result_json = NULL, error_code = NULL, error_message = NULL
                WHERE id = ? AND status = 'failed'
                """,
                (requested_at.isoformat(), work_id),
            )
            if updated.rowcount != 1:
                raise RuntimeError("failed work changed during durable retry transition")
        return number

    def request_cancellation(self, work_id: str, requested_at: datetime) -> str | None:
        requested = requested_at.isoformat()
        with self.connection:
            row = self.connection.execute(
                """
                SELECT status, cancellation_disposition
                FROM runtime_work
                WHERE id = ?
                """,
                (work_id,),
            ).fetchone()
            if row is None:
                return None
            if row["cancellation_disposition"] is not None:
                return str(row["cancellation_disposition"])

            if row["status"] == "accepted":
                updated = self.connection.execute(
                    """
                    UPDATE runtime_work
                    SET status = 'cancelled', completed_at = ?, result_json = NULL,
                        error_code = NULL, error_message = NULL,
                        cancellation_requested_at = ?, cancellation_disposition = 'prevented'
                    WHERE id = ? AND status = 'accepted' AND cancellation_requested_at IS NULL
                    """,
                    (requested, requested, work_id),
                )
                if updated.rowcount == 1:
                    return "prevented"

            if row["status"] == "running":
                updated = self.connection.execute(
                    """
                    UPDATE runtime_work
                    SET cancellation_requested_at = ?,
                        cancellation_disposition = 'requested_while_running'
                    WHERE id = ? AND status = 'running' AND cancellation_requested_at IS NULL
                    """,
                    (requested, work_id),
                )
                if updated.rowcount == 1:
                    return "requested_while_running"

            current = self.connection.execute(
                """
                SELECT cancellation_disposition
                FROM runtime_work
                WHERE id = ?
                """,
                (work_id,),
            ).fetchone()
            if current is not None and current["cancellation_disposition"] is not None:
                return str(current["cancellation_disposition"])
        return "terminal"

    def next_eligible(self, now: datetime) -> str | None:
        with self.connection:
            application_rows = self.connection.execute(
                """
                SELECT DISTINCT application_id
                FROM runtime_work
                WHERE status = 'accepted' AND (eligible_at IS NULL OR eligible_at <= ?)
                ORDER BY application_id
                """,
                (now.isoformat(),),
            ).fetchall()
            if not application_rows:
                return None

            cursor_row = self.connection.execute(
                """
                SELECT last_application_id
                FROM runtime_scheduler_state
                WHERE singleton = 1
                """
            ).fetchone()
            if cursor_row is None:
                raise RuntimeError("durable scheduler state disappeared")

            applications = [str(row["application_id"]) for row in application_rows]
            cursor = cursor_row["last_application_id"]
            application_id = applications[0]
            if cursor is not None:
                application_id = next(
                    (candidate for candidate in applications if candidate > cursor),
                    applications[0],
                )

            work = self.connection.execute(
                """
                SELECT id
                FROM runtime_work
                WHERE application_id = ?
                    AND status = 'accepted'
                    AND (eligible_at IS NULL OR eligible_at <= ?)
                ORDER BY
                    priority DESC,
                    CASE
                        WHEN eligible_at IS NOT NULL AND eligible_at > enqueued_at
                            THEN eligible_at
                        ELSE enqueued_at
                    END,
                    enqueued_at,
                    submitted_at,
                    id
                LIMIT 1
                """,
                (application_id, now.isoformat()),
            ).fetchone()
            if work is None:
                raise RuntimeError("eligible application lost its accepted work")

            self.connection.execute(
                """
                UPDATE runtime_scheduler_state
                SET last_application_id = ?
                WHERE singleton = 1
                """,
                (application_id,),
            )
        return str(work["id"])

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
            retry_row = self.connection.execute(
                "SELECT MAX(number) AS number FROM runtime_retry WHERE work_id = ?",
                (work_id,),
            ).fetchone()
            retry_number = retry_row["number"]
            self.connection.execute(
                """
                INSERT INTO runtime_attempt (
                    work_id, number, retry_number, status, started_at
                ) VALUES (?, ?, ?, 'running', ?)
                """,
                (work_id, number, retry_number, started_at.isoformat()),
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
        retries = self.connection.execute(
            "SELECT * FROM runtime_retry WHERE work_id = ? ORDER BY number", (work_id,)
        ).fetchall()
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
                priority=row["priority"],
                constraints=json.loads(row["constraints_json"]),
            ),
            status=row["status"],
            submitted_at=row["submitted_at"],
            started_at=row["started_at"],
            completed_at=row["completed_at"],
            result=json.loads(row["result_json"]) if row["result_json"] else None,
            failure=self._failure(row),
            cancellation=(
                WorkCancellation(
                    requested_at=row["cancellation_requested_at"],
                    disposition=row["cancellation_disposition"],
                )
                if row["cancellation_requested_at"] is not None
                else None
            ),
            retries=[
                WorkRetry(
                    number=retry["number"],
                    requested_at=retry["requested_at"],
                    allow_unknown_outcome=bool(retry["allow_unknown_outcome"]),
                    previous_completed_at=retry["previous_completed_at"],
                    previous_failure=WorkFailure(
                        code=retry["previous_error_code"],
                        message=retry["previous_error_message"],
                    ),
                )
                for retry in retries
            ],
            attempts=[
                WorkAttempt(
                    number=attempt["number"],
                    retry_number=attempt["retry_number"],
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
