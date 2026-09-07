"""Durable work records, idempotent submission, and scheduler queries."""

from __future__ import annotations

import json
import sqlite3
from datetime import datetime

from madre.contracts import (
    ResultEvidence,
    WorkCancellation,
    WorkFailure,
    WorkRecord,
    WorkSpec,
)
from madre.storage_db import _json
from madre.storage_work_base import WorkStoreBase


class WorkRecordStore(WorkStoreBase):
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
                        id,originator,capability_json,material_reference,input_digest,
                        material_envelope_json,eligible_at,priority,constraints_json,
                        correlation_json,idempotency_key,status,submitted_at,enqueued_at,
                        queue_sequence
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?, 'accepted',?,?,?)
                    """,
                    (
                        work_id,
                        spec.originator,
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
        failure = (
            WorkFailure(code=row["error_code"]) if row["error_code"] is not None else None
        )
        cancellation = None
        if row["cancellation_requested_at"] is not None:
            cancellation = WorkCancellation(
                requested_at=datetime.fromisoformat(row["cancellation_requested_at"]),
                disposition=row["cancellation_disposition"],
            )
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
                datetime.fromisoformat(row["completed_at"]) if row["completed_at"] else None
            ),
            failure=failure,
            cancellation=cancellation,
            retries=self._retries(work_id),
            attempts=self._attempts(work_id),
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
            if row is None:
                raise RuntimeError("eligible originator lost its accepted work")
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

