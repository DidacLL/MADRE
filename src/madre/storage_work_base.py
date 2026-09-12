"""Shared helpers for durable executable-work persistence."""

from __future__ import annotations

import sqlite3
from datetime import datetime

from madre.contracts import WorkAttempt, WorkFailure, WorkRetry
from madre_sdk.execution import ExecutionBoundary
from madre_sdk.security import ScopeIdentity


class WorkStoreBase:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self.connection = connection

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
                previous_failure=WorkFailure(code=row["previous_error_code"]),
            )
            for row in rows
        )

    def _attempts(self, work_id: str) -> tuple[WorkAttempt, ...]:
        rows = self.connection.execute(
            "SELECT * FROM runtime_attempt WHERE work_id=? ORDER BY number", (work_id,)
        ).fetchall()
        attempts = []
        for row in rows:
            failure = WorkFailure(code=row["error_code"]) if row["error_code"] is not None else None
            attempts.append(
                WorkAttempt(
                    number=row["number"],
                    retry_number=row["retry_number"],
                    status=row["status"],
                    started_at=datetime.fromisoformat(row["started_at"]),
                    completed_at=datetime.fromisoformat(row["completed_at"])
                    if row["completed_at"]
                    else None,
                    capability=ScopeIdentity.model_validate_json(row["capability_identity_json"])
                    if row["capability_identity_json"]
                    else None,
                    execution_boundary=ExecutionBoundary(row["execution_boundary"])
                    if row["execution_boundary"]
                    else None,
                    output_digest=row["output_digest"],
                    output_size=row["output_size"],
                    failure=failure,
                )
            )
        return tuple(attempts)
