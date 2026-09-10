"""Durable public registry, security-decision, and broker evidence persistence."""

from __future__ import annotations

from datetime import datetime

from madre.registry import ModuleManifest
from madre.security import ExecutionBoundary, SecurityDecision, SecurityHistory, SecurityTransition
from madre.storage_db import _json, utc_now
from madre.storage_work import WorkStore


class PlatformStore(WorkStore):
    def put_manifest(self, manifest: ModuleManifest) -> None:
        objects = [manifest.security, *(agent.security for agent in manifest.agents)]
        objects.extend(
            profile.security
            for operation in manifest.operations
            for profile in operation.effect_profiles
        )
        history = SecurityHistory(objects=tuple(objects))
        with self.connection:
            self._persist_security_history(history)
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
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        history: SecurityHistory,
        transition: SecurityTransition,
        execution_boundary: ExecutionBoundary | None,
        decision: SecurityDecision,
    ) -> None:
        with self.connection:
            # Rejected prospective objects are useful evidence, but the rejected transition is not
            # persisted as accepted history. SecurityID possession remains non-authoritative.
            self._persist_security_history(SecurityHistory(objects=history.objects))
            self.connection.execute(
                """
                INSERT INTO security_decision(
                    crossing_id,crossing_kind,target_id,transition_id,transition_json,
                    algebra_version,decision_json,execution_boundary,admissible,
                    failure_codes_json,decided_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    crossing_id,
                    crossing_kind,
                    target_id,
                    transition.transition_id,
                    _json(transition),
                    decision.algebra_version,
                    _json(decision),
                    execution_boundary,
                    int(decision.admissible),
                    _json(list(decision.failure_codes)),
                    utc_now().isoformat(),
                ),
            )

    def record_broker_event(
        self,
        *,
        invocation_id: str,
        crossing_kind: str,
        requester_module_id: str,
        target_module_id: str,
        target_id: str,
        event: str,
        observed_at: datetime,
        output_digest: str | None = None,
        output_size: int | None = None,
    ) -> None:
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO broker_event(
                    invocation_id,crossing_kind,requester_module_id,target_module_id,
                    target_id,event,observed_at,output_digest,output_size
                ) VALUES (?,?,?,?,?,?,?,?,?)
                """,
                (
                    invocation_id,
                    crossing_kind,
                    requester_module_id,
                    target_module_id,
                    target_id,
                    event,
                    observed_at.isoformat(),
                    output_digest,
                    output_size,
                ),
            )
