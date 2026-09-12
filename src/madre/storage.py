"""SQLite queue storage for opaque physical input and pending output."""

from __future__ import annotations

import hashlib
import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime, timedelta
from pathlib import Path

from filelock import FileLock, Timeout

from madre.capabilities import CapabilityId
from madre.work import (
    AttemptStatus,
    DeliveryStatus,
    WorkAttempt,
    WorkFailure,
    WorkId,
    WorkRecord,
    WorkStatus,
)
from madre_sdk import ComputationId, ModuleId, WorkRequest, WorkRequestJsonCodec

_STORAGE_DDL = """
CREATE TABLE runtime_work (
    id TEXT PRIMARY KEY,
    module_name TEXT NOT NULL,
    module_revision TEXT NOT NULL,
    computation_namespace TEXT NOT NULL,
    computation_name TEXT NOT NULL,
    computation_revision TEXT NOT NULL,
    request_json TEXT,
    request_digest TEXT NOT NULL,
    idempotency_key TEXT,
    status TEXT NOT NULL CHECK (status IN ('queued','running','succeeded','failed','cancelled')),
    submitted_at TEXT NOT NULL,
    not_before TEXT,
    priority INTEGER NOT NULL CHECK (priority BETWEEN 0 AND 100),
    maximum_attempts INTEGER NOT NULL CHECK (maximum_attempts >= 1),
    backoff_seconds REAL NOT NULL CHECK (backoff_seconds >= 0),
    queue_sequence INTEGER NOT NULL UNIQUE,
    completed_at TEXT,
    error_code TEXT,
    result_json TEXT,
    delivery_status TEXT CHECK (
        delivery_status IS NULL OR delivery_status IN ('pending','delivered')
    ),
    UNIQUE(module_name,module_revision,idempotency_key)
);

CREATE TABLE runtime_attempt (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    status TEXT NOT NULL CHECK (status IN ('running','succeeded','failed')),
    started_at TEXT NOT NULL,
    completed_at TEXT,
    capability_name TEXT NOT NULL,
    capability_revision TEXT NOT NULL,
    error_code TEXT,
    PRIMARY KEY(work_id,number)
);

CREATE TABLE runtime_scheduler_state (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    next_queue_sequence INTEGER NOT NULL CHECK (next_queue_sequence >= 1)
);
INSERT INTO runtime_scheduler_state(singleton,next_queue_sequence) VALUES (1,1);
"""


def utc_now() -> datetime:
    return datetime.now(UTC)


def _storage_format() -> int:
    raw = hashlib.sha256(_STORAGE_DDL.encode()).digest()[:4]
    return int.from_bytes(raw, "big") & 0x7FFFFFFF or 1


def _connect(path: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    return connection


def _discard_development_database(path: Path) -> None:
    for suffix in ("", "-wal", "-shm", "-journal"):
        Path(f"{path}{suffix}").unlink(missing_ok=True)


@contextmanager
def open_database(data_dir: Path) -> Iterator[sqlite3.Connection]:
    directory = data_dir.resolve()
    directory.mkdir(parents=True, exist_ok=True)
    lock = FileLock(directory / "runtime.lock", timeout=0)
    try:
        lock.acquire()
    except Timeout as exc:
        raise RuntimeError("another MADRE runtime owns this data directory") from exc
    try:
        path = directory / "runtime.sqlite3"
        existed = path.exists()
        connection = _connect(path)
        try:
            format_id = int(connection.execute("PRAGMA user_version").fetchone()[0])
            incompatible = format_id not in (0, _storage_format()) or (existed and format_id == 0)
            if incompatible:
                connection.close()
                _discard_development_database(path)
                connection = _connect(path)
                format_id = 0
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("PRAGMA journal_mode=WAL")
            if format_id == 0:
                with connection:
                    connection.executescript(_STORAGE_DDL)
                    connection.execute(f"PRAGMA user_version={_storage_format()}")
            yield connection
        finally:
            connection.close()
    finally:
        lock.release()


class WorkQueueStore:
    def __init__(
        self,
        connection: sqlite3.Connection,
        request_codec: WorkRequestJsonCodec | None = None,
    ) -> None:
        self._connection = connection
        self._request_codec = request_codec or WorkRequestJsonCodec()

    def create(
        self,
        identity: WorkId,
        request: WorkRequest[object],
        encoded: str,
        digest: str,
        submitted_at: datetime,
        idempotency_key: str | None,
    ) -> bool:
        try:
            with self._connection:
                sequence = self._take_sequence()
                self._connection.execute(
                    """
                    INSERT INTO runtime_work(
                        id,module_name,module_revision,computation_namespace,
                        computation_name,computation_revision,request_json,request_digest,
                        idempotency_key,status,submitted_at,not_before,priority,
                        maximum_attempts,backoff_seconds,queue_sequence
                    ) VALUES (?,?,?,?,?,?,?,?,?,'queued',?,?,?,?,?,?)
                    """,
                    (
                        identity.value,
                        request.module.name,
                        request.module.revision,
                        request.computation.identity.namespace,
                        request.computation.identity.name,
                        request.computation.identity.revision,
                        encoded,
                        digest,
                        idempotency_key,
                        submitted_at.isoformat(),
                        request.timing.not_before.isoformat()
                        if request.timing.not_before is not None
                        else None,
                        request.priority.value,
                        request.retry.maximum_attempts,
                        request.retry.initial_backoff.total_seconds(),
                        sequence,
                    ),
                )
        except sqlite3.IntegrityError:
            return False
        return True

    def idempotent_match(
        self,
        module: ModuleId,
        key: str,
    ) -> tuple[WorkRecord, str] | None:
        row = self._connection.execute(
            """SELECT id,request_digest FROM runtime_work
               WHERE module_name=? AND module_revision=? AND idempotency_key=?""",
            (module.name, module.revision, key),
        ).fetchone()
        if row is None:
            return None
        record = self.get(WorkId(str(row["id"])))
        assert record is not None
        return record, str(row["request_digest"])

    def get(self, identity: WorkId) -> WorkRecord | None:
        row = self._connection.execute(
            "SELECT * FROM runtime_work WHERE id=?",
            (identity.value,),
        ).fetchone()
        if row is None:
            return None
        return WorkRecord(
            identity=identity,
            module=ModuleId(str(row["module_name"]), str(row["module_revision"])),
            computation=ComputationId(
                str(row["computation_namespace"]),
                str(row["computation_name"]),
                str(row["computation_revision"]),
            ),
            status=WorkStatus(str(row["status"])),
            submitted_at=datetime.fromisoformat(str(row["submitted_at"])),
            not_before=(
                datetime.fromisoformat(str(row["not_before"]))
                if row["not_before"] is not None
                else None
            ),
            priority=int(row["priority"]),
            maximum_attempts=int(row["maximum_attempts"]),
            attempts=self._attempts(identity),
            completed_at=(
                datetime.fromisoformat(str(row["completed_at"]))
                if row["completed_at"] is not None
                else None
            ),
            failure=WorkFailure(str(row["error_code"])) if row["error_code"] is not None else None,
            delivery=DeliveryStatus(str(row["delivery_status"]))
            if row["delivery_status"] is not None
            else None,
        )

    def load_request(self, identity: WorkId) -> WorkRequest[object] | None:
        row = self._connection.execute(
            "SELECT request_json FROM runtime_work WHERE id=?",
            (identity.value,),
        ).fetchone()
        if row is None or row["request_json"] is None:
            return None
        return self._request_codec.decode(str(row["request_json"]))

    def next_eligible(self, now: datetime) -> WorkId | None:
        row = self._connection.execute(
            """
            SELECT id FROM runtime_work
            WHERE status='queued' AND (not_before IS NULL OR not_before<=?)
            ORDER BY priority DESC, COALESCE(not_before,submitted_at), queue_sequence
            LIMIT 1
            """,
            (now.isoformat(),),
        ).fetchone()
        return WorkId(str(row["id"])) if row is not None else None

    def next_eligibility(self) -> datetime | None:
        row = self._connection.execute(
            """SELECT MIN(not_before) AS next_time FROM runtime_work
               WHERE status='queued' AND not_before IS NOT NULL"""
        ).fetchone()
        return (
            datetime.fromisoformat(str(row["next_time"])) if row["next_time"] is not None else None
        )

    def defer_unavailable(self, identity: WorkId, now: datetime) -> None:
        row = self._connection.execute(
            "SELECT backoff_seconds FROM runtime_work WHERE id=? AND status='queued'",
            (identity.value,),
        ).fetchone()
        if row is None:
            return
        delay = max(float(row["backoff_seconds"]), 1.0)
        with self._connection:
            self._connection.execute(
                "UPDATE runtime_work SET not_before=? WHERE id=? AND status='queued'",
                ((now + timedelta(seconds=delay)).isoformat(), identity.value),
            )

    def start_attempt(
        self,
        identity: WorkId,
        capability: CapabilityId,
        started_at: datetime,
    ) -> int | None:
        with self._connection:
            updated = self._connection.execute(
                "UPDATE runtime_work SET status='running' WHERE id=? AND status='queued'",
                (identity.value,),
            )
            if updated.rowcount != 1:
                return None
            row = self._connection.execute(
                "SELECT COALESCE(MAX(number),0)+1 AS number FROM runtime_attempt WHERE work_id=?",
                (identity.value,),
            ).fetchone()
            number = int(row["number"])
            self._connection.execute(
                """INSERT INTO runtime_attempt(
                       work_id,number,status,started_at,capability_name,capability_revision
                   ) VALUES (?,?,'running',?,?,?)""",
                (
                    identity.value,
                    number,
                    started_at.isoformat(),
                    capability.name,
                    capability.revision,
                ),
            )
            return number

    def finish_success(
        self,
        identity: WorkId,
        attempt: int,
        encoded_result: str,
        completed_at: datetime,
    ) -> None:
        with self._connection:
            self._connection.execute(
                """UPDATE runtime_attempt
                   SET status='succeeded',completed_at=?,error_code=NULL
                   WHERE work_id=? AND number=?""",
                (completed_at.isoformat(), identity.value, attempt),
            )
            self._connection.execute(
                """UPDATE runtime_work
                   SET status='succeeded',completed_at=?,error_code=NULL,
                       request_json=NULL,result_json=?,delivery_status='pending'
                   WHERE id=?""",
                (completed_at.isoformat(), encoded_result, identity.value),
            )

    def finish_failure(
        self,
        identity: WorkId,
        attempt: int,
        failure: WorkFailure,
        completed_at: datetime,
    ) -> None:
        with self._connection:
            self._connection.execute(
                """UPDATE runtime_attempt
                   SET status='failed',completed_at=?,error_code=?
                   WHERE work_id=? AND number=?""",
                (completed_at.isoformat(), failure.code, identity.value, attempt),
            )
            row = self._connection.execute(
                """SELECT maximum_attempts,backoff_seconds
                   FROM runtime_work WHERE id=?""",
                (identity.value,),
            ).fetchone()
            retry = attempt < int(row["maximum_attempts"])
            if retry:
                not_before = completed_at + timedelta(seconds=float(row["backoff_seconds"]))
                sequence = self._take_sequence()
                self._connection.execute(
                    """UPDATE runtime_work
                       SET status='queued',not_before=?,queue_sequence=?,error_code=?
                       WHERE id=?""",
                    (not_before.isoformat(), sequence, failure.code, identity.value),
                )
            else:
                self._connection.execute(
                    """UPDATE runtime_work
                       SET status='failed',completed_at=?,error_code=? WHERE id=?""",
                    (completed_at.isoformat(), failure.code, identity.value),
                )

    def cancel(self, identity: WorkId, cancelled_at: datetime) -> bool:
        with self._connection:
            updated = self._connection.execute(
                """UPDATE runtime_work
                   SET status='cancelled',completed_at=?,request_json=NULL
                   WHERE id=? AND status='queued'""",
                (cancelled_at.isoformat(), identity.value),
            )
            return updated.rowcount == 1

    def consume_result(self, identity: WorkId) -> str | None:
        with self._connection:
            row = self._connection.execute(
                """SELECT result_json FROM runtime_work
                   WHERE id=? AND delivery_status='pending'""",
                (identity.value,),
            ).fetchone()
            if row is None or row["result_json"] is None:
                return None
            encoded = str(row["result_json"])
            self._connection.execute(
                """UPDATE runtime_work
                   SET result_json=NULL,delivery_status='delivered' WHERE id=?""",
                (identity.value,),
            )
            return encoded

    def recover_interrupted(self, now: datetime) -> int:
        rows = self._connection.execute(
            "SELECT id FROM runtime_work WHERE status='running'"
        ).fetchall()
        recovered = 0
        for row in rows:
            identity = WorkId(str(row["id"]))
            attempt = self._connection.execute(
                """SELECT number FROM runtime_attempt
                   WHERE work_id=? AND status='running' ORDER BY number DESC LIMIT 1""",
                (identity.value,),
            ).fetchone()
            if attempt is None:
                continue
            self.finish_failure(identity, int(attempt["number"]), WorkFailure("interrupted"), now)
            recovered += 1
        return recovered

    def cleanup_failed_inputs(self, completed_before: datetime) -> int:
        with self._connection:
            updated = self._connection.execute(
                """UPDATE runtime_work SET request_json=NULL
                   WHERE status='failed' AND completed_at<? AND request_json IS NOT NULL""",
                (completed_before.isoformat(),),
            )
            return updated.rowcount

    def table_names(self) -> tuple[str, ...]:
        rows = self._connection.execute(
            "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
        ).fetchall()
        return tuple(str(row["name"]) for row in rows)

    def _attempts(self, identity: WorkId) -> tuple[WorkAttempt, ...]:
        rows = self._connection.execute(
            "SELECT * FROM runtime_attempt WHERE work_id=? ORDER BY number",
            (identity.value,),
        ).fetchall()
        return tuple(
            WorkAttempt(
                number=int(row["number"]),
                status=AttemptStatus(str(row["status"])),
                started_at=datetime.fromisoformat(str(row["started_at"])),
                completed_at=(
                    datetime.fromisoformat(str(row["completed_at"]))
                    if row["completed_at"] is not None
                    else None
                ),
                capability_name=str(row["capability_name"]),
                capability_revision=str(row["capability_revision"]),
                failure=WorkFailure(str(row["error_code"]))
                if row["error_code"] is not None
                else None,
            )
            for row in rows
        )

    def _take_sequence(self) -> int:
        row = self._connection.execute(
            "SELECT next_queue_sequence FROM runtime_scheduler_state WHERE singleton=1"
        ).fetchone()
        sequence = int(row["next_queue_sequence"])
        self._connection.execute(
            """UPDATE runtime_scheduler_state
               SET next_queue_sequence=? WHERE singleton=1""",
            (sequence + 1,),
        )
        return sequence
