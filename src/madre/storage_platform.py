"""Durable public registry, security-decision, and broker evidence persistence."""

from __future__ import annotations

import json
from datetime import datetime

from madre.registry import ModuleManifest
from madre.security import (
    ExecutionBoundary,
    InvocationContext,
    RelationDecision,
    SecurityEvidence,
)
from madre.storage_db import _json, utc_now
from madre.storage_work import WorkStore


class PlatformStore(WorkStore):
    def put_manifest(self, manifest: ModuleManifest) -> None:
        objects = [manifest.security, *(agent.security for agent in manifest.agents)]
        evidence = SecurityEvidence(objects=tuple(objects))
        with self.connection:
            self._persist_security_evidence(evidence)
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
        evidence: SecurityEvidence,
        execution_boundary: ExecutionBoundary | None,
        decision: RelationDecision,
    ) -> None:
        with self.connection:
            # Prospective scope facts and the decision are evidence. A denied relation is not
            # inserted into accepted SecurityEvidence or the canonical relation table.
            self._persist_security_evidence(SecurityEvidence(objects=evidence.objects))
            self.connection.execute(
                """
                INSERT INTO security_decision(
                    crossing_id,crossing_kind,target_id,relation_id,relation_kind,
                    algebra_version,decision_json,execution_boundary,admissible,
                    failure_codes_json,decided_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                (
                    crossing_id,
                    crossing_kind,
                    target_id,
                    decision.relation_id,
                    decision.relation_kind,
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
        completion_context: InvocationContext | None = None,
        completion_evidence: SecurityEvidence | None = None,
        output_security_id: str | None = None,
        derivation_ids: tuple[str, ...] = (),
    ) -> None:
        with self.connection:
            if completion_evidence is not None:
                self._persist_security_evidence(completion_evidence)
            self.connection.execute(
                """
                INSERT INTO broker_event(
                    invocation_id,crossing_kind,requester_module_id,target_module_id,
                    target_id,event,observed_at,output_digest,output_size,
                    completion_context_json,completion_evidence_json,output_security_id,
                    derivation_ids_json
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                    _json(completion_context) if completion_context else None,
                    _json(completion_evidence) if completion_evidence else None,
                    output_security_id,
                    _json(derivation_ids),
                ),
            )

    def completed_derivation_ids(self) -> tuple[str, ...]:
        rows = self.connection.execute(
            "SELECT derivation_ids_json FROM broker_event WHERE event='completed'"
        ).fetchall()
        return tuple(sorted({value for row in rows for value in json.loads(row[0])}))

    def completed_output_ids(self) -> tuple[str, ...]:
        rows = self.connection.execute(
            "SELECT output_security_id FROM broker_event WHERE event='completed'"
        ).fetchall()
        return tuple(sorted({row[0] for row in rows}))
