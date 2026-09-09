"""Durable public registry, security-decision, and broker evidence persistence."""

from __future__ import annotations

from datetime import datetime

from madre.registry import ModuleManifest
from madre.security import ExecutionBoundary, SecurityContext, SecurityDecision
from madre.storage_db import _json, utc_now
from madre.storage_work import WorkStore


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
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        context: SecurityContext,
        execution_boundary: ExecutionBoundary | None,
        decision: SecurityDecision,
    ) -> None:
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO security_decision(
                    crossing_id,crossing_kind,target_id,context_json,evaluator,evidence_json,
                    execution_boundary,admissible,deficits_json,decided_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    crossing_id,
                    crossing_kind,
                    target_id,
                    _json(context),
                    decision.evaluator,
                    _json(decision.evidence),
                    execution_boundary,
                    int(decision.admissible),
                    _json(list(decision.deficits)),
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
