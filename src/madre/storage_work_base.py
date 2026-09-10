"""Shared helpers for durable executable-work persistence."""

from __future__ import annotations

import sqlite3
from datetime import datetime

from madre.contracts import WorkAttempt, WorkFailure, WorkRetry
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    SecurityDerivation,
    SecurityHistory,
    SecurityObject,
    SecurityTransition,
)
from madre.storage_db import _json


class WorkStoreBase:
    def __init__(self, connection: sqlite3.Connection) -> None:
        self.connection = connection

    def _persist_security_history(self, history: SecurityHistory) -> None:
        if history.transitions or history.derivations:
            # Published immutable ancestry cannot fork or cycle merely by being
            # split across separate histories/completions. These are structural
            # checks, never extra participants in a prospective transition.
            stored_relations = tuple(
                SecurityDerivation.model_validate_json(row[0])
                for row in self.connection.execute(
                    "SELECT derivation_json FROM security_derivation"
                ).fetchall()
            )
            ids = {
                sid
                for relation in stored_relations
                for sid in (
                    relation.output_security_id,
                    *relation.source_security_ids,
                    *relation.producer_security_ids,
                    *relation.validator_security_ids,
                )
            }
            objects = tuple(
                SecurityObject.model_validate_json(row[0])
                for sid in sorted(ids)
                for row in self.connection.execute(
                    "SELECT object_json FROM security_object WHERE security_id=?", (sid,)
                ).fetchall()
            )
            combined = history.merge(SecurityHistory(objects=objects, derivations=stored_relations))
            decision = DEFAULT_SECURITY_EVALUATOR.evaluate(combined, SecurityTransition.issue())
            if not decision.admissible:
                raise ValueError(
                    "invalid realized security history: " + ",".join(decision.failure_codes)
                )
        for obj in history.objects:
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
        for transition in history.transitions:
            row = self.connection.execute(
                "SELECT transition_json FROM security_transition WHERE transition_id=?",
                (transition.transition_id,),
            ).fetchone()
            payload = _json(transition)
            if row is not None and str(row["transition_json"]) != payload:
                raise ValueError(
                    f"conflicting persisted transition identity: {transition.transition_id}"
                )
            if row is None:
                self.connection.execute(
                    "INSERT INTO security_transition(transition_id,transition_json) VALUES (?,?)",
                    (transition.transition_id, payload),
                )
        for derivation in history.derivations:
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
                    security_transition_id=row["security_transition_id"],
                    output_digest=row["output_digest"],
                    output_size=row["output_size"],
                    failure=failure,
                )
            )
        return tuple(attempts)
