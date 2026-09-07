"""Exclusive durable ownership for public descriptors, work metadata and evidence."""

from __future__ import annotations

import hashlib
import json
import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime
from pathlib import Path

from filelock import FileLock, Timeout
from pydantic import BaseModel

from madre.contracts import (
    ResultEvidence,
    WorkAttempt,
    WorkCancellation,
    WorkFailure,
    WorkRecord,
    WorkRetry,
    WorkSpec,
)
from madre.registry import ModuleManifest
from madre.security import SecurityDecision

_STORAGE_DDL = """
CREATE TABLE runtime_work (
    id TEXT PRIMARY KEY,
    originator TEXT NOT NULL,
    requester_envelope_json TEXT NOT NULL,
    capability_json TEXT NOT NULL,
    material_reference TEXT NOT NULL,
    input_digest TEXT NOT NULL,
    material_envelope_json TEXT NOT NULL,
    eligible_at TEXT,
    priority INTEGER NOT NULL CHECK (priority BETWEEN -100 AND 100),
    constraints_json TEXT NOT NULL,
    correlation_json TEXT NOT NULL,
    idempotency_key TEXT,
    status TEXT NOT NULL CHECK (status IN ('accepted','running','succeeded','failed','cancelled')),
    submitted_at TEXT NOT NULL,
    enqueued_at TEXT NOT NULL,
    queue_sequence INTEGER NOT NULL UNIQUE CHECK (queue_sequence >= 1),
    started_at TEXT,
    completed_at TEXT,
    error_code TEXT,
    error_message TEXT,
    cancellation_requested_at TEXT,
    cancellation_disposition TEXT CHECK (
        cancellation_disposition IS NULL OR
        cancellation_disposition IN ('prevented','requested_while_running')
    ),
    output_digest TEXT,
    output_size INTEGER,
    output_produced_at TEXT,
    delivery_status TEXT CHECK (
        delivery_status IS NULL OR delivery_status IN ('awaiting_consumption','consumed','lost')
    )
);
CREATE UNIQUE INDEX runtime_work_idempotency
ON runtime_work(originator,idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE runtime_scheduler_state (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    last_originator TEXT,
    next_queue_sequence INTEGER NOT NULL CHECK (next_queue_sequence >= 1)
);
INSERT INTO runtime_scheduler_state(singleton,last_originator,next_queue_sequence)
VALUES (1,NULL,1);

CREATE TABLE runtime_retry (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    idempotency_key TEXT NOT NULL,
    allow_unknown_outcome INTEGER NOT NULL CHECK (allow_unknown_outcome IN (0,1)),
    requested_at TEXT NOT NULL,
    previous_completed_at TEXT NOT NULL,
    previous_error_code TEXT NOT NULL,
    previous_error_message TEXT NOT NULL,
    PRIMARY KEY(work_id,number),
    UNIQUE(work_id,idempotency_key)
);

CREATE TABLE runtime_attempt (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    retry_number INTEGER,
    status TEXT NOT NULL CHECK (status IN ('running','succeeded','failed')),
    started_at TEXT NOT NULL,
    completed_at TEXT,
    capability_id TEXT,
    output_digest TEXT,
    output_size INTEGER,
    error_code TEXT,
    error_message TEXT,
    PRIMARY KEY(work_id,number)
);

CREATE TABLE module_manifest (
    module_id TEXT PRIMARY KEY,
    manifest_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE security_decision (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    crossing_kind TEXT NOT NULL,
    requester_subject TEXT NOT NULL,
    target_id TEXT NOT NULL,
    admissible INTEGER NOT NULL CHECK (admissible IN (0,1)),
    deficits_json TEXT NOT NULL,
    decided_at TEXT NOT NULL
);
"""


def utc_now() -> datetime:
    return datetime.now(UTC)


def _json(value: object) -> str:
    if isinstance(value, BaseModel):
        value = value.model_dump(mode="json")
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def _format_id() -> int:
    value = int.from_bytes(hashlib.sha256(_STORAGE_DDL.encode()).digest()[:4], "big")
    return value & 0x7FFFFFFF or 1


_STORAGE_FORMAT_ID = _format_id()


def _connect(database: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(database)
    connection.row_factory = sqlite3.Row
    return connection


def _discard_development_storage(database: Path) -> None:
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
        existed = database.exists()
        connection = _connect(database)
        try:
            format_id = connection.execute("PRAGMA user_version").fetchone()[0]
            incompatible = format_id not in (0, _STORAGE_FORMAT_ID) or (existed and format_id == 0)
            if incompatible:
                connection.close()
                _discard_development_storage(database)
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
    def __init__(self, connection: sqlite3.Connection) -> None:
        self.connection = connection

    def create(
        self,
        work_id: str,
        spec: WorkSpec,
        submitted_at: datetime,
        *,
        idempotency_key: str | None = None,
    ) -> bool:
        try:
            with self.connection:
                sequence = self._take_queue_sequence()
                self.connection.execute(
                    """
                    INSERT INTO runtime_work(
                        id,originator,requester_envelope_json,capability_json,
                        material_reference,input_digest,material_envelope_json,eligible_at,
                        priority,constraints_json,correlation_json,idempotency_key,status,
                        submitted_at,enqueued_at,queue_sequence
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?, 'accepted',?,?,?)
                    """,
                    (
                        work_id,
                        spec.originator,
                        _json(spec.requester_envelope),
                        _json(spec.capability),
                        spec.material_reference,
                        spec.input_digest,
                        _json(spec.material_envelope),
                        spec.eligible_at.isoformat() if spec.eligible_at else None,
                        spec.priority,
                        _json(spec.constraints),
                        _json(spec.correlation),
                        idempotency_key,
                        submitted_at.isoformat(),
                        submitted_at.isoformat(),
                        sequence,
                    ),
                )
        except sqlite3.IntegrityError:
            if (
                idempotency_key is None
                or self._idempotency_id(spec.originator, idempotency_key) is None
            ):
                raise
            return False
        return True

    def get_by_idempotency_key(self, originator: str, key: str) -> WorkRecord | None:
        identity = self._idempotency_id(originator, key)
        return self.get(identity) if identity else None

    def get(self, work_id: str | None) -> WorkRecord | None:
        if work_id is None:
            return None
        row = self.connection.execute(
            "SELECT * FROM runtime_work WHERE id=?", (work_id,)
        ).fetchone()
        if row is None:
            return None
        spec = WorkSpec.model_validate(
            {
                "originator": row["originator"],
                "requester_envelope": json.loads(row["requester_envelope_json"]),
                "capability": json.loads(row["capability_json"]),
                "material_reference": row["material_reference"],
                "input_digest": row["input_digest"],
                "material_envelope": json.loads(row["material_envelope_json"]),
                "eligible_at": row["eligible_at"],
                "priority": row["priority"],
                "constraints": json.loads(row["constraints_json"]),
                "correlation": json.loads(row["correlation_json"]),
            }
        )
        failure = None
        if row["error_code"] is not None:
            failure = WorkFailure(code=row["error_code"], message=row["error_message"])
        cancellation = None
        if row["cancellation_requested_at"] is not None:
            cancellation = WorkCancellation(
                requested_at=datetime.fromisoformat(row["cancellation_requested_at"]),
                disposition=row["cancellation_disposition"],
            )
        retries = self._retries(work_id)
        attempts = self._attempts(work_id)
        result = None
        if row["output_digest"] is not None:
            result = ResultEvidence(
                digest=row["output_digest"],
                size=row["output_size"],
                produced_at=datetime.fromisoformat(row["output_produced_at"]),
                delivery_status=row["delivery_status"],
            )
        return WorkRecord(
            id=work_id,
            spec=spec,
            status=row["status"],
            submitted_at=datetime.fromisoformat(row["submitted_at"]),
            started_at=datetime.fromisoformat(row["started_at"]) if row["started_at"] else None,
            completed_at=(
                datetime.fromisoformat(row["completed_at"])
                if row["completed_at"]
                else None
            ),
            failure=failure,
            cancellation=cancellation,
            retries=retries,
            attempts=attempts,
            result=result,
        )

    def next_eligible(self, now: datetime) -> str | None:
        with self.connection:
            rows = self.connection.execute(
                """
                SELECT DISTINCT originator FROM runtime_work
                WHERE status='accepted' AND (eligible_at IS NULL OR eligible_at<=?)
                ORDER BY originator
                """,
                (now.isoformat(),),
            ).fetchall()
            if not rows:
                return None
            originators = [str(row["originator"]) for row in rows]
            cursor = self.connection.execute(
                "SELECT last_originator FROM runtime_scheduler_state WHERE singleton=1"
            ).fetchone()["last_originator"]
            originator = originators[0]
            if cursor is not None:
                originator = next((item for item in originators if item > cursor), originators[0])
            row = self.connection.execute(
                """
                SELECT id FROM runtime_work
                WHERE originator=? AND status='accepted' AND (eligible_at IS NULL OR eligible_at<=?)
                ORDER BY priority DESC,
                    CASE WHEN eligible_at IS NOT NULL AND eligible_at>enqueued_at
                         THEN eligible_at ELSE enqueued_at END,
                    queue_sequence
                LIMIT 1
                """,
                (originator, now.isoformat()),
            ).fetchone()
            self.connection.execute(
                "UPDATE runtime_scheduler_state SET last_originator=? WHERE singleton=1",
                (originator,),
            )
            return str(row["id"])

    def next_eligibility(self) -> datetime | None:
        row = self.connection.execute(
            """
            SELECT MIN(eligible_at) AS eligible_at
            FROM runtime_work
            WHERE status='accepted' AND eligible_at IS NOT NULL
            """
        ).fetchone()
        return datetime.fromisoformat(row["eligible_at"]) if row["eligible_at"] else None

    def start_attempt(self, work_id: str, capability_id: str, started_at: datetime) -> int | None:
        with self.connection:
            updated = self.connection.execute(
                """
                UPDATE runtime_work
                SET status='running', started_at=COALESCE(started_at,?)
                WHERE id=? AND status='accepted'
                """,
                (started_at.isoformat(), work_id),
            )
            if updated.rowcount != 1:
                return None
            number = int(
                self.connection.execute(
                    "SELECT COALESCE(MAX(number),0)+1 AS n FROM runtime_attempt WHERE work_id=?",
                    (work_id,),
                ).fetchone()["n"]
            )
            retry_number = self.connection.execute(
                "SELECT MAX(number) AS n FROM runtime_retry WHERE work_id=?", (work_id,)
            ).fetchone()["n"]
            self.connection.execute(
                """
                INSERT INTO runtime_attempt(
                    work_id,number,retry_number,status,started_at,capability_id
                ) VALUES (?,?,?,'running',?,?)
                """,
                (work_id, number, retry_number, started_at.isoformat(), capability_id),
            )
            return number

    def succeed(
        self,
        work_id: str,
        attempt_number: int,
        output_digest: str,
        output_size: int,
        completed_at: datetime,
    ) -> None:
        with self.connection:
            self.connection.execute(
                """
                UPDATE runtime_attempt
                SET status='succeeded', completed_at=?, output_digest=?, output_size=?,
                    error_code=NULL, error_message=NULL
                WHERE work_id=? AND number=?
                """,
                (completed_at.isoformat(), output_digest, output_size, work_id, attempt_number),
            )
            self.connection.execute(
                """
                UPDATE runtime_work
                SET status='succeeded', completed_at=?, error_code=NULL, error_message=NULL,
                    output_digest=?, output_size=?, output_produced_at=?,
                    delivery_status='awaiting_consumption'
                WHERE id=?
                """,
                (
                    completed_at.isoformat(),
                    output_digest,
                    output_size,
                    completed_at.isoformat(),
                    work_id,
                ),
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
                    SET status='failed', completed_at=?, error_code=?, error_message=?
                    WHERE work_id=? AND number=?
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
                UPDATE runtime_work SET status='failed',completed_at=?,error_code=?,error_message=?,
                    output_digest=NULL,output_size=NULL,output_produced_at=NULL,delivery_status=NULL
                WHERE id=?
                """,
                (completed_at.isoformat(), failure.code, failure.message, work_id),
            )

    def request_cancellation(self, work_id: str, requested_at: datetime) -> str | None:
        with self.connection:
            row = self.connection.execute(
                "SELECT status,cancellation_disposition FROM runtime_work WHERE id=?", (work_id,)
            ).fetchone()
            if row is None:
                return None
            if row["cancellation_disposition"] is not None:
                return str(row["cancellation_disposition"])
            if row["status"] == "accepted":
                self.connection.execute(
                    """
                    UPDATE runtime_work
                    SET status='cancelled', completed_at=?, cancellation_requested_at=?,
                        cancellation_disposition='prevented'
                    WHERE id=?
                    """,
                    (requested_at.isoformat(), requested_at.isoformat(), work_id),
                )
                return "prevented"
            if row["status"] == "running":
                self.connection.execute(
                    """
                    UPDATE runtime_work
                    SET cancellation_requested_at=?,
                        cancellation_disposition='requested_while_running'
                    WHERE id=?
                    """,
                    (requested_at.isoformat(), work_id),
                )
                return "requested_while_running"
            return "terminal"

    def retry_policy_by_key(self, work_id: str, key: str) -> bool | None:
        row = self.connection.execute(
            "SELECT allow_unknown_outcome FROM runtime_retry WHERE work_id=? AND idempotency_key=?",
            (work_id, key),
        ).fetchone()
        return bool(row["allow_unknown_outcome"]) if row else None

    def requeue_failed(
        self,
        work_id: str,
        key: str,
        allow_unknown_outcome: bool,
        requested_at: datetime,
    ) -> int | None:
        with self.connection:
            previous = self.connection.execute(
                """
                SELECT completed_at,error_code,error_message
                FROM runtime_work
                WHERE id=? AND status='failed'
                """,
                (work_id,),
            ).fetchone()
            if previous is None:
                return None
            number = int(
                self.connection.execute(
                    "SELECT COALESCE(MAX(number),0)+1 AS n FROM runtime_retry WHERE work_id=?",
                    (work_id,),
                ).fetchone()["n"]
            )
            self.connection.execute(
                """
                INSERT INTO runtime_retry(
                    work_id, number, idempotency_key, allow_unknown_outcome, requested_at,
                    previous_completed_at, previous_error_code, previous_error_message
                ) VALUES (?,?,?,?,?,?,?,?)
                """,
                (
                    work_id,
                    number,
                    key,
                    int(allow_unknown_outcome),
                    requested_at.isoformat(),
                    previous["completed_at"],
                    previous["error_code"],
                    previous["error_message"],
                ),
            )
            sequence = self._take_queue_sequence()
            self.connection.execute(
                """
                UPDATE runtime_work
                SET status='accepted', enqueued_at=?, queue_sequence=?, completed_at=NULL,
                    error_code=NULL, error_message=NULL, output_digest=NULL, output_size=NULL,
                    output_produced_at=NULL, delivery_status=NULL
                WHERE id=?
                """,
                (requested_at.isoformat(), sequence, work_id),
            )
            return number

    def fail_interrupted_attempts(self, now: datetime) -> int:
        with self.connection:
            rows = self.connection.execute(
                "SELECT id FROM runtime_work WHERE status='running'"
            ).fetchall()
            for row in rows:
                work_id = str(row["id"])
                attempt = self.connection.execute(
                    """
                    SELECT number FROM runtime_attempt
                    WHERE work_id=? AND status='running'
                    ORDER BY number DESC LIMIT 1
                    """,
                    (work_id,),
                ).fetchone()
                if attempt is not None:
                    self.connection.execute(
                        """
                        UPDATE runtime_attempt
                        SET status='failed', completed_at=?, error_code='interrupted',
                            error_message='runtime stopped during capability execution'
                        WHERE work_id=? AND number=?
                        """,
                        (now.isoformat(), work_id, attempt["number"]),
                    )
                self.connection.execute(
                    """
                    UPDATE runtime_work
                    SET status='failed', completed_at=?, error_code='interrupted',
                        error_message='runtime stopped during capability execution'
                    WHERE id=?
                    """,
                    (now.isoformat(), work_id),
                )
            return len(rows)

    def mark_unconsumed_results_lost(self) -> int:
        with self.connection:
            updated = self.connection.execute(
                """
                UPDATE runtime_work
                SET delivery_status='lost'
                WHERE delivery_status='awaiting_consumption'
                """
            )
            return updated.rowcount

    def mark_result_consumed(self, work_id: str) -> None:
        with self.connection:
            self.connection.execute(
                """
                UPDATE runtime_work
                SET delivery_status='consumed'
                WHERE id=? AND delivery_status='awaiting_consumption'
                """,
                (work_id,),
            )

    def mark_result_lost(self, work_id: str) -> None:
        with self.connection:
            self.connection.execute(
                """
                UPDATE runtime_work
                SET delivery_status='lost'
                WHERE id=? AND delivery_status='awaiting_consumption'
                """,
                (work_id,),
            )

    def _take_queue_sequence(self) -> int:
        row = self.connection.execute(
            "SELECT next_queue_sequence FROM runtime_scheduler_state WHERE singleton=1"
        ).fetchone()
        sequence = int(row["next_queue_sequence"])
        self.connection.execute(
            "UPDATE runtime_scheduler_state SET next_queue_sequence=? WHERE singleton=1",
            (sequence + 1,),
        )
        return sequence

    def _idempotency_id(self, originator: str, key: str) -> str | None:
        row = self.connection.execute(
            "SELECT id FROM runtime_work WHERE originator=? AND idempotency_key=?",
            (originator, key),
        ).fetchone()
        return str(row["id"]) if row else None

    def _retries(self, work_id: str) -> tuple[WorkRetry, ...]:
        rows = self.connection.execute(
            "SELECT * FROM runtime_retry WHERE work_id=? ORDER BY number", (work_id,)
        ).fetchall()
        return tuple(
            WorkRetry(
                number=row["number"],
                requested_at=datetime.fromisoformat(row["requested_at"]),
                allow_unknown_outcome=bool(row["allow_unknown_outcome"]),
                previous_completed_at=datetime.fromisoformat(row["previous_completed_at"]),
                previous_failure=WorkFailure(
                    code=row["previous_error_code"], message=row["previous_error_message"]
                ),
            )
            for row in rows
        )

    def _attempts(self, work_id: str) -> tuple[WorkAttempt, ...]:
        rows = self.connection.execute(
            "SELECT * FROM runtime_attempt WHERE work_id=? ORDER BY number", (work_id,)
        ).fetchall()
        attempts = []
        for row in rows:
            failure = None
            if row["error_code"] is not None:
                failure = WorkFailure(code=row["error_code"], message=row["error_message"])
            attempts.append(
                WorkAttempt(
                    number=row["number"],
                    retry_number=row["retry_number"],
                    status=row["status"],
                    started_at=datetime.fromisoformat(row["started_at"]),
                    completed_at=datetime.fromisoformat(row["completed_at"])
                    if row["completed_at"]
                    else None,
                    capability_id=row["capability_id"],
                    output_digest=row["output_digest"],
                    output_size=row["output_size"],
                    failure=failure,
                )
            )
        return tuple(attempts)


class PlatformStore(WorkStore):
    def put_manifest(self, manifest: ModuleManifest) -> None:
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO module_manifest(module_id,manifest_json,updated_at) VALUES (?,?,?)
                ON CONFLICT(module_id) DO UPDATE
                SET manifest_json=excluded.manifest_json, updated_at=excluded.updated_at
                """,
                (manifest.module_id, manifest.model_dump_json(), utc_now().isoformat()),
            )

    def manifests(self) -> tuple[ModuleManifest, ...]:
        rows = self.connection.execute(
            "SELECT manifest_json FROM module_manifest ORDER BY module_id"
        ).fetchall()
        return tuple(ModuleManifest.model_validate_json(row["manifest_json"]) for row in rows)

    def record_security_decision(
        self,
        *,
        crossing_kind: str,
        requester_subject: str,
        target_id: str,
        decision: SecurityDecision,
    ) -> None:
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO security_decision(
                    crossing_kind, requester_subject, target_id, admissible,
                    deficits_json, decided_at
                ) VALUES (?,?,?,?,?,?)
                """,
                (
                    crossing_kind,
                    requester_subject,
                    target_id,
                    int(decision.admissible),
                    _json(list(decision.deficits)),
                    utc_now().isoformat(),
                ),
            )
