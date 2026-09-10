"""Durable work attempt, retry, cancellation, recovery, and result evidence."""

from __future__ import annotations

from datetime import datetime

from madre.contracts import WorkFailure
from madre.security import ExecutionBoundary, OrdinarySecurityLevel
from madre.storage_db import _json
from madre.storage_work_records import WorkRecordStore


class WorkStore(WorkRecordStore):
    def start_attempt(
        self,
        work_id: str,
        capability_id: str,
        provider_id: str | None,
        model_id: str | None,
        execution_boundary: ExecutionBoundary,
        security_transition_id: str,
        started_at: datetime,
    ) -> int | None:
        with self.connection:
            updated = self.connection.execute(
                "UPDATE runtime_work SET status='running', started_at=COALESCE(started_at,?) WHERE id=? AND status='accepted'",
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
                    work_id,number,retry_number,status,started_at,capability_id,
                    provider_id,model_id,execution_boundary,security_transition_id
                ) VALUES (?,?,?,'running',?,?,?,?,?,?)
                """,
                (
                    work_id,
                    number,
                    retry_number,
                    started_at.isoformat(),
                    capability_id,
                    provider_id,
                    model_id,
                    execution_boundary,
                    security_transition_id,
                ),
            )
            return number

    def succeed(
        self,
        work_id: str,
        attempt_number: int,
        output_digest: str,
        output_size: int,
        output_integrity: OrdinarySecurityLevel,
        producer_security_ids: tuple[str, ...],
        source_security_ids: tuple[str, ...],
        completed_at: datetime,
    ) -> None:
        with self.connection:
            self.connection.execute(
                """
                UPDATE runtime_attempt SET status='succeeded', completed_at=?, output_digest=?,
                    output_size=?, error_code=NULL WHERE work_id=? AND number=?
                """,
                (completed_at.isoformat(), output_digest, output_size, work_id, attempt_number),
            )
            self.connection.execute(
                """
                UPDATE runtime_work SET status='succeeded', completed_at=?, error_code=NULL,
                    output_digest=?, output_size=?, output_produced_at=?, output_integrity=?,
                    result_producer_security_ids_json=?, result_source_security_ids_json=?,
                    delivery_status='awaiting_consumption' WHERE id=?
                """,
                (
                    completed_at.isoformat(),
                    output_digest,
                    output_size,
                    completed_at.isoformat(),
                    int(output_integrity),
                    _json(list(producer_security_ids)),
                    _json(list(source_security_ids)),
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
                    "UPDATE runtime_attempt SET status='failed', completed_at=?, error_code=? WHERE work_id=? AND number=?",
                    (completed_at.isoformat(), failure.code, work_id, attempt_number),
                )
            self.connection.execute(
                """
                UPDATE runtime_work SET status='failed',completed_at=?,error_code=?,
                    output_digest=NULL,output_size=NULL,output_produced_at=NULL,output_integrity=NULL,
                    result_producer_security_ids_json=NULL,result_source_security_ids_json=NULL,
                    delivery_status=NULL WHERE id=?
                """,
                (completed_at.isoformat(), failure.code, work_id),
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
                    """UPDATE runtime_work SET status='cancelled', completed_at=?,
                       cancellation_requested_at=?, cancellation_disposition='prevented' WHERE id=?""",
                    (requested_at.isoformat(), requested_at.isoformat(), work_id),
                )
                return "prevented"
            if row["status"] == "running":
                self.connection.execute(
                    """UPDATE runtime_work SET cancellation_requested_at=?,
                       cancellation_disposition='requested_while_running' WHERE id=?""",
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
                "SELECT completed_at,error_code FROM runtime_work WHERE id=? AND status='failed'",
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
                """INSERT INTO runtime_retry(work_id,number,idempotency_key,allow_unknown_outcome,
                   requested_at,previous_completed_at,previous_error_code) VALUES (?,?,?,?,?,?,?)""",
                (
                    work_id,
                    number,
                    key,
                    int(allow_unknown_outcome),
                    requested_at.isoformat(),
                    previous["completed_at"],
                    previous["error_code"],
                ),
            )
            sequence = self._take_queue_sequence()
            self.connection.execute(
                """
                UPDATE runtime_work SET status='accepted', enqueued_at=?, queue_sequence=?,
                    completed_at=NULL,error_code=NULL,output_digest=NULL,output_size=NULL,
                    output_produced_at=NULL,output_integrity=NULL,result_producer_security_ids_json=NULL,
                    result_source_security_ids_json=NULL,delivery_status=NULL WHERE id=?
                """,
                (requested_at.isoformat(), sequence, work_id),
            )
            return number

    def fail_interrupted_attempts(self, now: datetime) -> int:
        with self.connection:
            rows = self.connection.execute("SELECT id FROM runtime_work WHERE status='running'").fetchall()
            for row in rows:
                work_id = str(row["id"])
                attempt = self.connection.execute(
                    "SELECT number FROM runtime_attempt WHERE work_id=? AND status='running' ORDER BY number DESC LIMIT 1",
                    (work_id,),
                ).fetchone()
                if attempt is not None:
                    self.connection.execute(
                        "UPDATE runtime_attempt SET status='failed', completed_at=?, error_code='interrupted' WHERE work_id=? AND number=?",
                        (now.isoformat(), work_id, attempt["number"]),
                    )
                self.connection.execute(
                    "UPDATE runtime_work SET status='failed', completed_at=?, error_code='interrupted' WHERE id=?",
                    (now.isoformat(), work_id),
                )
            return len(rows)

    def mark_unconsumed_results_lost(self) -> int:
        with self.connection:
            updated = self.connection.execute(
                "UPDATE runtime_work SET delivery_status='lost' WHERE delivery_status='awaiting_consumption'"
            )
            return updated.rowcount

    def mark_result_consumed(self, work_id: str) -> None:
        with self.connection:
            self.connection.execute(
                "UPDATE runtime_work SET delivery_status='consumed' WHERE id=? AND delivery_status='awaiting_consumption'",
                (work_id,),
            )

    def mark_result_lost(self, work_id: str) -> None:
        with self.connection:
            self.connection.execute(
                "UPDATE runtime_work SET delivery_status='lost' WHERE id=? AND delivery_status='awaiting_consumption'",
                (work_id,),
            )
