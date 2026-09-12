"""Module-owned immutable material with exact security facts."""

from __future__ import annotations

import hashlib
import json
from typing import Literal

from pydantic import JsonValue, model_validator

from madre.contracts import MaterialHandle, TransientInferenceResult, TransientMaterial, WorkRecord
from madre.interfaces import MaterialResolver
from madre.security import (
    FrozenModel,
    Identifier,
    Integrity,
    ScopeBinding,
    SecurityEvidence,
    SecurityObject,
    SecurityScopeRef,
    Sensitivity,
)

MaterialKind = Literal["artifact", "context_bundle"]


def content_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def _material_security(
    *,
    owner_module_id: str,
    reference: str,
    payload: JsonValue,
    sensitivity: Sensitivity,
    publication_revision: str,
    representation_revision: str,
    integrity: Integrity | None = None,
) -> SecurityObject:
    return SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id=owner_module_id,
            scope_id=reference,
            publication_revision=publication_revision,
            scope_revision=representation_revision,
        ),
        sensitivity=sensitivity,
        integrity=integrity,
        binding=ScopeBinding(content_digest=content_digest(payload)),
    )


def _material_evidence(security: SecurityObject) -> SecurityEvidence:
    return SecurityEvidence(objects=(security,))


def _validate_material(
    *, security: SecurityObject, reference: str, payload: JsonValue, evidence: SecurityEvidence
) -> None:
    if security.scope.scope_id != reference or security.sensitivity is None:
        raise ValueError("material security must bind the material identity and Sensitivity")
    if not security.verify_binding() or security.binding.content_digest != content_digest(payload):
        raise ValueError("material SecurityObject does not bind its representation")
    if evidence.resolve(security.security_id) != security:
        raise ValueError("material facts must contain its exact SecurityObject")


class Artifact(FrozenModel):
    id: Identifier
    payload: JsonValue
    security: SecurityObject
    evidence: SecurityEvidence

    @model_validator(mode="after")
    def validate_security(self) -> Artifact:
        _validate_material(
            security=self.security, reference=self.id, payload=self.payload, evidence=self.evidence
        )
        return self

    @classmethod
    def create(
        cls,
        *,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: Sensitivity,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        return cls(
            id=artifact_id,
            payload=payload,
            security=security,
            evidence=_material_evidence(security),
        )

    @classmethod
    def from_inference_result(
        cls,
        *,
        owner_module_id: str,
        artifact_id: str,
        result: TransientInferenceResult,
        sensitivity: Sensitivity,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        if result.output_digest != content_digest(result.payload):
            raise ValueError("inference result digest mismatch")
        return cls.create(
            owner_module_id=owner_module_id,
            artifact_id=artifact_id,
            payload=result.payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )

    @classmethod
    def from_work_result(
        cls,
        *,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        record: WorkRecord,
        sensitivity: Sensitivity,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        result = record.result
        if result is None or result.digest != content_digest(payload):
            raise ValueError("work result does not match payload")
        return cls.create(
            owner_module_id=owner_module_id,
            artifact_id=artifact_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )

    def validated(
        self,
        *,
        procedure: SecurityScopeRef,
        validators: tuple[SecurityObject, ...],
        artifact_id: str,
        payload: JsonValue,
        integrity: Integrity,
        sensitivity: Sensitivity | None = None,
        representation_revision: str = "1",
    ) -> Artifact:
        if not validators or any(item.integrity is None for item in validators):
            raise ValueError("validation requires Integrity-bearing validators")
        bound = min(item.integrity.value for item in validators if item.integrity is not None)
        if integrity.value > bound:
            raise ValueError("validated material exceeds validator Integrity")
        if any(not item.verify_binding() for item in validators):
            raise ValueError("validator SecurityObject binding is invalid")
        current = self.security.sensitivity
        assert current is not None
        output_security = _material_security(
            owner_module_id=procedure.owner_module_id,
            reference=artifact_id,
            payload=payload,
            sensitivity=sensitivity or current,
            integrity=integrity,
            publication_revision=procedure.publication_revision,
            representation_revision=representation_revision,
        )
        return Artifact(
            id=artifact_id,
            payload=payload,
            security=output_security,
            evidence=_material_evidence(output_security),
        )

    def transient(self) -> TransientMaterial:
        return TransientMaterial(
            reference=self.id,
            payload=self.payload,
            digest=content_digest(self.payload),
            security=self.security,
            evidence=self.evidence,
        )

    def handle(self, *, coordination: str | None = None) -> MaterialHandle:
        return self.transient().to_handle(coordination=coordination)


class ContextBundle(FrozenModel):
    id: Identifier
    purpose: str
    payload: JsonValue
    security: SecurityObject
    evidence: SecurityEvidence

    @model_validator(mode="after")
    def validate_security(self) -> ContextBundle:
        if not self.purpose:
            raise ValueError("ContextBundle purpose must not be empty")
        _validate_material(
            security=self.security, reference=self.id, payload=self.payload, evidence=self.evidence
        )
        return self

    @classmethod
    def create(
        cls,
        *,
        owner_module_id: str,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        sensitivity: Sensitivity,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> ContextBundle:
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=bundle_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        return cls(
            id=bundle_id,
            purpose=purpose,
            payload=payload,
            security=security,
            evidence=_material_evidence(security),
        )

    @classmethod
    def select(
        cls,
        *,
        owner_module_id: str,
        bundle_id: str,
        purpose: str,
        members: tuple[Artifact, ...],
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> ContextBundle:
        if not members or len({item.id for item in members}) != len(members):
            raise ValueError("selection requires nonempty uniquely named members")
        if any(item.security.sensitivity is None for item in members):
            raise ValueError("selected members require Sensitivity")
        sensitivity = Sensitivity(
            max(item.security.sensitivity.value for item in members if item.security.sensitivity)
        )
        return cls.create(
            owner_module_id=owner_module_id,
            bundle_id=bundle_id,
            purpose=purpose,
            payload={item.id: item.payload for item in members},
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )

    def transient(self) -> TransientMaterial:
        return TransientMaterial(
            reference=self.id,
            payload=self.payload,
            digest=content_digest(self.payload),
            security=self.security,
            evidence=self.evidence,
        )

    def handle(self, *, coordination: str | None = None) -> MaterialHandle:
        return self.transient().to_handle(coordination=coordination)


Material = Artifact | ContextBundle


class MaterialRepository(MaterialResolver):
    def __init__(self) -> None:
        self._materials: dict[str, TransientMaterial] = {}

    def retain(self, material: Material) -> MaterialHandle:
        transient = material.transient()
        self._materials[material.id] = transient
        return transient.to_handle(coordination=f"module-material:{material.id}")

    def discard(self, reference: str) -> None:
        self._materials.pop(reference, None)

    async def resolve(self, handle: MaterialHandle) -> TransientMaterial | None:
        material = self._materials.get(handle.reference)
        if material is None:
            return None
        if (
            material.digest != handle.digest
            or material.security != handle.security
            or material.evidence != handle.evidence
        ):
            return None
        return material
