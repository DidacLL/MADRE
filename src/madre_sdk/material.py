"""Module-owned material with exact scope facts and separate derivation evidence."""

from __future__ import annotations

import hashlib
import json
from typing import Literal

from pydantic import Field, JsonValue, model_validator

from madre.contracts import MaterialHandle, TransientInferenceResult, TransientMaterial, WorkRecord
from madre.interfaces import MaterialResolver
from madre.security import (
    DerivationEvidence,
    FrozenModel,
    Identifier,
    Integrity,
    InvocationContext,
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
    sources: tuple[SecurityObject, ...] = (),
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
        sensitivity_sources=sources,
        binding=ScopeBinding(content_digest=content_digest(payload)),
    )


def _evidence_of(source: Material | TransientMaterial) -> SecurityEvidence:
    return source.evidence


def _sources(
    source: Material | TransientMaterial,
    additional: tuple[Material | TransientMaterial, ...],
    evidence: SecurityEvidence | None,
) -> tuple[SecurityEvidence, tuple[SecurityObject, ...]]:
    values = (source, *additional)
    merged = SecurityEvidence()
    for item in values:
        merged = merged.merge(_evidence_of(item)).extend(objects=(item.security,))
    if evidence is not None:
        merged = merged.merge(evidence)
    return merged, tuple(item.security for item in values)


def _validate_material(
    *, security: SecurityObject, reference: str, payload: JsonValue, evidence: SecurityEvidence
) -> None:
    if security.scope.scope_id != reference or security.sensitivity is None:
        raise ValueError("material security must bind the material identity and Sensitivity")
    if not security.verify_binding() or security.binding.content_digest != content_digest(payload):
        raise ValueError("material SecurityObject does not bind its representation")
    if evidence.resolve(security.security_id) != security:
        raise ValueError("material evidence must contain its exact SecurityObject")


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
        evidence: SecurityEvidence | None = None,
    ) -> Artifact:
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        carried = (evidence or SecurityEvidence()).extend(objects=(security,))
        return cls(id=artifact_id, payload=payload, security=security, evidence=carried)

    @classmethod
    def derive_from(
        cls,
        *,
        invocation: InvocationContext,
        source: Material | TransientMaterial,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: Sensitivity,
        additional_sources: tuple[Material | TransientMaterial, ...] = (),
        publication_revision: str = "1",
        representation_revision: str = "1",
        evidence: SecurityEvidence | None = None,
        transform: SecurityScopeRef | None = None,
    ) -> Artifact:
        carried, source_objects = _sources(source, additional_sources, evidence)
        ordinary_sources = () if transform is not None else source_objects
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            sources=ordinary_sources,
        )
        derivation = DerivationEvidence.issue(
            kind="transform" if transform is not None else "ordinary",
            procedure=transform,
            output_security_id=security.security_id,
            source_security_ids=tuple(item.security_id for item in source_objects),
            producer_security_ids=tuple(item.security_id for item in invocation.producers),
        )
        carried = carried.extend(objects=(*invocation.objects, security), derivations=(derivation,))
        return cls(id=artifact_id, payload=payload, security=security, evidence=carried)

    @classmethod
    def from_inference_result(
        cls,
        *,
        invocation: InvocationContext,
        owner_module_id: str,
        artifact_id: str,
        source: Material,
        result: TransientInferenceResult,
        sensitivity: Sensitivity,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        if result.output_digest != content_digest(result.payload):
            raise ValueError("inference result digest mismatch")
        source_objects = tuple(result.evidence.resolve(item) for item in result.source_security_ids)
        producer_objects = tuple(
            result.evidence.resolve(item) for item in result.producer_security_ids
        )
        if (
            any(item is None for item in (*source_objects, *producer_objects))
            or source.security.security_id not in result.source_security_ids
        ):
            raise ValueError("inference result has unresolved production evidence")
        typed_sources = tuple(item for item in source_objects if item is not None)
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            payload=result.payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            sources=typed_sources,
        )
        derivation = DerivationEvidence.issue(
            kind="ordinary",
            output_security_id=security.security_id,
            source_security_ids=result.source_security_ids,
            producer_security_ids=tuple(
                sorted(
                    {
                        *result.producer_security_ids,
                        *(item.security_id for item in invocation.producers),
                    }
                )
            ),
        )
        evidence = source.evidence.merge(result.evidence).extend(
            objects=(*invocation.objects, security), derivations=(derivation,)
        )
        return cls(id=artifact_id, payload=result.payload, security=security, evidence=evidence)

    @classmethod
    def from_work_result(
        cls,
        *,
        invocation: InvocationContext,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        source: Material,
        record: WorkRecord,
        sensitivity: Sensitivity,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        result = record.result
        if result is None or result.digest != content_digest(payload):
            raise ValueError("work result evidence does not match payload")
        source_objects = tuple(
            record.spec.evidence.resolve(item) for item in result.source_security_ids
        )
        producer_objects = tuple(
            record.spec.evidence.resolve(item) for item in result.producer_security_ids
        )
        if (
            any(item is None for item in (*source_objects, *producer_objects))
            or source.security.security_id not in result.source_security_ids
        ):
            raise ValueError("work result has unresolved production evidence")
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            sources=tuple(item for item in source_objects if item is not None),
        )
        derivation = DerivationEvidence.issue(
            kind="ordinary",
            output_security_id=security.security_id,
            source_security_ids=result.source_security_ids,
            producer_security_ids=tuple(
                sorted(
                    {
                        *result.producer_security_ids,
                        *(item.security_id for item in invocation.producers),
                    }
                )
            ),
        )
        evidence = source.evidence.merge(record.spec.evidence).extend(
            objects=(*invocation.objects, security), derivations=(derivation,)
        )
        return cls(id=artifact_id, payload=payload, security=security, evidence=evidence)

    def derive(
        self,
        *,
        invocation: InvocationContext,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: Sensitivity | None = None,
        representation_revision: str = "1",
        transform: SecurityScopeRef | None = None,
    ) -> Artifact:
        assert self.security.sensitivity is not None
        return Artifact.derive_from(
            invocation=invocation,
            source=self,
            owner_module_id=self.security.scope.owner_module_id,
            artifact_id=artifact_id,
            payload=payload,
            sensitivity=sensitivity or self.security.sensitivity,
            publication_revision=self.security.scope.publication_revision,
            representation_revision=representation_revision,
            transform=transform,
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
            raise ValueError("validation requires actual Integrity-bearing validators")
        bound = min(item.integrity.value for item in validators if item.integrity is not None)
        if integrity.value > bound:
            raise ValueError("validated projection exceeds validator Integrity")
        assert self.security.sensitivity is not None
        output_security = _material_security(
            owner_module_id=procedure.owner_module_id,
            reference=artifact_id,
            payload=payload,
            sensitivity=sensitivity or self.security.sensitivity,
            integrity=integrity,
            publication_revision=procedure.publication_revision,
            representation_revision=representation_revision,
            sources=(self.security,),
        )
        derivation = DerivationEvidence.issue(
            kind="validation",
            procedure=procedure,
            output_security_id=output_security.security_id,
            source_security_ids=(self.security.security_id,),
            validator_security_ids=tuple(item.security_id for item in validators),
        )
        evidence = self.evidence.extend(
            objects=(*validators, output_security), derivations=(derivation,)
        )
        return Artifact(
            id=artifact_id, payload=payload, security=output_security, evidence=evidence
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
    purpose: str = Field(min_length=1)
    payload: JsonValue
    security: SecurityObject
    evidence: SecurityEvidence

    @model_validator(mode="after")
    def validate_security(self) -> ContextBundle:
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
        evidence: SecurityEvidence | None = None,
    ) -> ContextBundle:
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=bundle_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        carried = (evidence or SecurityEvidence()).extend(objects=(security,))
        return cls(
            id=bundle_id,
            purpose=purpose,
            payload=payload,
            security=security,
            evidence=carried,
        )

    @classmethod
    def derive_from(
        cls,
        *,
        invocation: InvocationContext,
        source: Material | TransientMaterial,
        owner_module_id: str,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        sensitivity: Sensitivity,
        additional_sources: tuple[Material | TransientMaterial, ...] = (),
        publication_revision: str = "1",
        representation_revision: str = "1",
        evidence: SecurityEvidence | None = None,
        transform: SecurityScopeRef | None = None,
    ) -> ContextBundle:
        carried, source_objects = _sources(source, additional_sources, evidence)
        ordinary_sources = () if transform is not None else source_objects
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=bundle_id,
            payload=payload,
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            sources=ordinary_sources,
        )
        derivation = DerivationEvidence.issue(
            kind="transform" if transform is not None else "ordinary",
            procedure=transform,
            output_security_id=security.security_id,
            source_security_ids=tuple(item.security_id for item in source_objects),
            producer_security_ids=tuple(item.security_id for item in invocation.producers),
        )
        carried = carried.extend(objects=(*invocation.objects, security), derivations=(derivation,))
        return cls(
            id=bundle_id,
            purpose=purpose,
            payload=payload,
            security=security,
            evidence=carried,
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
        evidence = SecurityEvidence().merge(*(item.evidence for item in members))
        sensitivity = Sensitivity(
            max(item.security.sensitivity.value for item in members if item.security.sensitivity)
        )
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=bundle_id,
            payload={item.id: item.payload for item in members},
            sensitivity=sensitivity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            sources=tuple(item.security for item in members),
        )
        derivation = DerivationEvidence.issue(
            kind="selection",
            output_security_id=security.security_id,
            source_security_ids=tuple(item.security.security_id for item in members),
        )
        return cls(
            id=bundle_id,
            purpose=purpose,
            payload={item.id: item.payload for item in members},
            security=security,
            evidence=evidence.extend(objects=(security,), derivations=(derivation,)),
        )

    def derive(
        self,
        *,
        invocation: InvocationContext,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        sensitivity: Sensitivity | None = None,
        representation_revision: str = "1",
        transform: SecurityScopeRef | None = None,
    ) -> ContextBundle:
        assert self.security.sensitivity is not None
        return ContextBundle.derive_from(
            invocation=invocation,
            source=self,
            owner_module_id=self.security.scope.owner_module_id,
            bundle_id=bundle_id,
            purpose=purpose,
            payload=payload,
            sensitivity=sensitivity or self.security.sensitivity,
            publication_revision=self.security.scope.publication_revision,
            representation_revision=representation_revision,
            transform=transform,
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
