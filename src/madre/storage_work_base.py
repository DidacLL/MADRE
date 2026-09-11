"""Shared helpers for durable executable-work persistence."""

from __future__ import annotations

import sqlite3
from datetime import datetime

from madre.contracts import WorkAttempt, WorkFailure, WorkRetry
from madre.security import (
    SecurityEvidence,
    audit_security_evidence,
)
from madre.storage_db import _json


class WorkStoreBase:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self.connection = connection

    def _persist_security_evidence(self, evidence: SecurityEvidence) -> None:
        failures = audit_security_evidence(evidence)
        if failures:
            raise ValueError(
                "invalid security evidence: " + ",".join(item.code for item in failures)
            )
        for obj in evidence.objects:
            row = self.connection.execute(
                "SELECT object_json FROM security_object WHERE security_id=?", (obj.security_id,)
            ).fetchone()
            payload = _json(obj)
            if row is not None and str(row["object_json"]) != payload:
                raise ValueError(f"conflicting persisted SecurityID: {obj.security_id}")
            if row is None:
                self.connection.execute(
                    "INSERT INTO security_object(security_id,object_json) VALUES (?,?)",
                    (obj.security_id, payload),
                )
        for relation in evidence.relations:
            row = self.connection.execute(
                "SELECT relation_json FROM security_relation WHERE relation_id=?",
                (relation.relation_id,),
            ).fetchone()
            payload = _json(relation)
            if row is not None and str(row["relation_json"]) != payload:
                raise ValueError(f"conflicting persisted relation identity: {relation.relation_id}")
            if row is None:
                self.connection.execute(
                    """INSERT INTO security_relation(
                           relation_id,relation_kind,relation_json
                       ) VALUES (?,?,?)""",
                    (relation.relation_id, relation.relation_kind, payload),
                )
        for derivation in evidence.derivations:
            row = self.connection.execute(
                "SELECT derivation_json FROM security_derivation WHERE derivation_id=?",
                (derivation.derivation_id,),
            ).fetchone()
            payload = _json(derivation)
            if row is not None and str(row["derivation_json"]) != payload:
                raise ValueError(
                    f"conflicting persisted derivation identity: {derivation.derivation_id}"
                )
            if row is None:
                self.connection.execute(
                    "INSERT INTO security_derivation(derivation_id,derivation_json) VALUES (?,?)",
                    (derivation.derivation_id, payload),
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
                    capability_id=row["capability_id"],
                    provider_id=row["provider_id"],
                    model_id=row["model_id"],
                    execution_boundary=row["execution_boundary"],
                    disclosure_relation_id=row["disclosure_relation_id"],
                    output_digest=row["output_digest"],
                    output_size=row["output_size"],
                    failure=failure,
                )
            )
        return tuple(attempts)
