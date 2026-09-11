"""Module-owned material helpers with explicit derivation security history."""

from __future__ import annotations

import hashlib
import json
from typing import Literal, cast

from pydantic import Field, JsonValue, model_validator

from madre.contracts import MaterialHandle, TransientInferenceResult, TransientMaterial, WorkRecord
from madre.interfaces import MaterialResolver
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    BindingEvidence,
    FrozenModel,
    Identifier,
    InvocationContext,
    OrdinarySecurityLevel,
    SecurityDerivation,
    SecurityHistory,
    SecurityLevel,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
    SecurityValues,
)

MaterialKind = Literal["artifact", "context_bundle"]


def content_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def _material_security(
    *,
    owner_module_id: str,
    reference: str,
    kind: MaterialKind,
    payload: JsonValue,
    sensitivity: OrdinarySecurityLevel,
    integrity: OrdinarySecurityLevel | None = None,
    publication_revision: str,
    representation_revision: str,
) -> SecurityObject:
    return SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id=owner_module_id,
            subject_kind=kind,
            publication_revision=publication_revision,
            local_id=reference,
            subject_revision=representation_revision,
        ),
        values=SecurityValues(sensitivity=sensitivity, integrity=integrity),
        binding_evidence=(BindingEvidence(key="content_digest", value=content_digest(payload)),),
    )


def _required_integrity(
    history: SecurityHistory, security_ids: tuple[str, ...]
) -> OrdinarySecurityLevel:
    if not security_ids:
        raise ValueError("derivation assurance path must not be empty")
    values: list[int] = []
    for security_id in security_ids:
        obj = history.resolve(security_id)
        if obj is None:
            raise ValueError(f"unresolved derivation participant: {security_id}")
        value = getattr(obj.values, "integrity", None)
        if value is None:
            raise ValueError(f"derivation participant lacks Integrity: {security_id}")
        values.append(int(value))
    return SecurityLevel(min(values))


def _validate_history(history: SecurityHistory) -> None:
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(history, SecurityTransition.issue())
    if not decision.admissible:
        raise ValueError(f"invalid security history: {','.join(decision.failure_codes)}")


def _history_of(source: Material | TransientMaterial) -> SecurityHistory:
    if isinstance(source, TransientMaterial):
        return source.history
    return source.security_history


def _merge_sources(
    source: Material | TransientMaterial,
    additional_sources: tuple[Material | TransientMaterial, ...],
    security_history: SecurityHistory | None = None,
) -> tuple[SecurityHistory, tuple[str, ...]]:
    sources = (source, *additional_sources)
    history = SecurityHistory()
    for item in sources:
        history = history.merge(_history_of(item)).extend(objects=(item.security,))
    if security_history is not None:
        history = history.merge(security_history)
    return history, tuple(item.security.security_id for item in sources)


class Artifact(FrozenModel):
    id: Identifier
    payload: JsonValue
    security: SecurityObject
    security_history: SecurityHistory

    @model_validator(mode="after")
    def validate_security(self) -> Artifact:
        if (
            self.security.subject_ref.subject_kind != "artifact"
            or self.security.subject_ref.local_id != self.id
        ):
            raise ValueError("Artifact security must be bound to the Artifact identity")
        if not self.security.verify_binding():
            raise ValueError("Artifact SecurityObject binding is invalid")
        if self.security.evidence_value("content_digest") != content_digest(self.payload):
            raise ValueError("Artifact SecurityObject does not bind its representation")
        if self.security_history.resolve(self.security.security_id) is None:
            raise ValueError("Artifact history must contain the Artifact SecurityObject")
        return self

    @classmethod
    def create(
        cls,
        *,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel,
        integrity: OrdinarySecurityLevel | None = None,
        publication_revision: str = "1",
        representation_revision: str = "1",
        security_history: SecurityHistory | None = None,
    ) -> Artifact:
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            kind="artifact",
            payload=payload,
            sensitivity=sensitivity,
            integrity=integrity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        history = (security_history or SecurityHistory()).extend(objects=(security,))
        _validate_history(history)
        return cls(id=artifact_id, payload=payload, security=security, security_history=history)

    @classmethod
    def from_inference_result(
        cls,
        *,
        invocation: InvocationContext,
        owner_module_id: str,
        artifact_id: str,
        source: Material,
        result: TransientInferenceResult,
        sensitivity: OrdinarySecurityLevel,
        integrity: OrdinarySecurityLevel | None = None,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        if result.output_digest != content_digest(result.payload):
            raise ValueError("inference result digest mismatch")
        production = tuple(
            sorted(set((*result.producer_security_ids, *invocation.producer_security_ids)))
        )
        production_history = result.security.extend(objects=invocation.objects)
        if integrity is not None:
            raise ValueError("Use an explicit control-material derivation or validation procedure")
        desired = None
        history = source.security_history.merge(production_history)
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            kind="artifact",
            payload=result.payload,
            sensitivity=sensitivity,
            integrity=desired,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        history = history.extend(objects=(security,))
        derivation = SecurityDerivation.issue(
            kind="ordinary",
            output_security_id=security.security_id,
            source_security_ids=result.source_security_ids,
            producer_security_ids=production,
        )
        history = history.extend(derivations=(derivation,))
        _validate_history(history)
        return cls(
            id=artifact_id, payload=result.payload, security=security, security_history=history
        )

    @classmethod
    def from_work_result(
        cls,
        *,
        invocation: InvocationContext,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        record: WorkRecord,
        sensitivity: OrdinarySecurityLevel,
        integrity: OrdinarySecurityLevel | None = None,
        publication_revision: str = "1",
        representation_revision: str = "1",
    ) -> Artifact:
        evidence = record.result
        if evidence is None or evidence.digest != content_digest(payload):
            raise ValueError("work result evidence does not match payload")
        production = tuple(
            sorted(set((*evidence.producer_security_ids, *invocation.producer_security_ids)))
        )
        production_history = record.spec.security.extend(objects=invocation.objects)
        if integrity is not None:
            raise ValueError("Use an explicit control-material derivation or validation procedure")
        desired = None
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=artifact_id,
            kind="artifact",
            payload=payload,
            sensitivity=sensitivity,
            integrity=desired,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        history = production_history.extend(objects=(security,))
        derivation = SecurityDerivation.issue(
            kind="ordinary",
            output_security_id=security.security_id,
            source_security_ids=evidence.source_security_ids,
            producer_security_ids=production,
        )
        history = history.extend(derivations=(derivation,))
        _validate_history(history)
        return cls(id=artifact_id, payload=payload, security=security, security_history=history)

    @classmethod
    def derive_from(
        cls,
        *,
        invocation: InvocationContext,
        source: Material | TransientMaterial,
        owner_module_id: str,
        artifact_id: str,
        payload: JsonValue,
        producer_security_ids: tuple[str, ...],
        sensitivity: OrdinarySecurityLevel,
        integrity: OrdinarySecurityLevel | None = None,
        additional_sources: tuple[Material | TransientMaterial, ...] = (),
        publication_revision: str = "1",
        representation_revision: str = "1",
        security_history: SecurityHistory | None = None,
        procedure: SecuritySubjectRef | None = None,
    ) -> Artifact:
        history, source_ids = _merge_sources(source, additional_sources, security_history)
        history = history.extend(objects=invocation.objects)
        producer_security_ids = tuple(
            sorted(set((*producer_security_ids, *invocation.producer_security_ids)))
        )
        desired = integrity
        if desired is not None:
            assurance = _required_integrity(
                history,
                tuple(
                    item
                    for item in (*source_ids, *producer_security_ids)
                    if (obj := history.resolve(item)) is not None
                    and obj.values.integrity is not None
                ),
            )
            if desired > assurance:
                raise ValueError("ordinary derivation cannot increase Integrity")
        output = cls.create(
            owner_module_id=owner_module_id,
            artifact_id=artifact_id,
            payload=payload,
            sensitivity=sensitivity,
            integrity=desired,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            security_history=history,
        )
        relation = SecurityDerivation.issue(
            kind="transform" if procedure else "ordinary",
            procedure=procedure,
            output_security_id=output.security.security_id,
            source_security_ids=source_ids,
            producer_security_ids=producer_security_ids,
        )
        final_history = output.security_history.extend(derivations=(relation,))
        _validate_history(final_history)
        return output.model_copy(update={"security_history": final_history})

    def derive(
        self,
        *,
        invocation: InvocationContext,
        artifact_id: str,
        payload: JsonValue,
        producer_security_ids: tuple[str, ...],
        sensitivity: OrdinarySecurityLevel | None = None,
        integrity: OrdinarySecurityLevel | None = None,
        representation_revision: str = "1",
    ) -> Artifact:
        values = self.security.values
        return Artifact.derive_from(
            invocation=invocation,
            source=self,
            owner_module_id=self.security.subject_ref.owner_module_id,
            artifact_id=artifact_id,
            payload=payload,
            producer_security_ids=producer_security_ids,
            sensitivity=sensitivity or cast(OrdinarySecurityLevel, values.sensitivity),
            integrity=integrity,
            publication_revision=self.security.subject_ref.publication_revision,
            representation_revision=representation_revision,
        )

    def validated(
        self,
        *,
        invocation: InvocationContext,
        procedure: SecuritySubjectRef,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel | None = None,
        integrity: OrdinarySecurityLevel | None = None,
        representation_revision: str = "1",
    ) -> Artifact:
        values = self.security.values
        history = self.security_history.extend(objects=invocation.objects)
        validator_security_ids = invocation.producer_security_ids
        assurance = _required_integrity(history, validator_security_ids)
        desired = integrity or assurance
        if int(desired) > int(assurance):
            raise ValueError("validated representation exceeds validator assurance")
        output = Artifact.create(
            owner_module_id=invocation.module_id,
            artifact_id=artifact_id,
            payload=payload,
            sensitivity=sensitivity or cast(OrdinarySecurityLevel, values.sensitivity),
            integrity=desired,
            publication_revision=invocation.module.subject_ref.publication_revision,
            representation_revision=representation_revision,
            security_history=history,
        )
        relation = SecurityDerivation.issue(
            kind="validation",
            procedure=procedure,
            output_security_id=output.security.security_id,
            source_security_ids=(self.security.security_id,),
            validator_security_ids=validator_security_ids,
        )
        history = output.security_history.extend(derivations=(relation,))
        _validate_history(history)
        return output.model_copy(update={"security_history": history})

    def transient(self) -> TransientMaterial:
        return TransientMaterial(
            reference=self.id,
            payload=self.payload,
            digest=content_digest(self.payload),
            security=self.security,
            history=self.security_history,
        )

    def handle(self, *, coordination: str | None = None) -> MaterialHandle:
        return self.transient().to_handle(coordination=coordination)


class ContextBundle(FrozenModel):
    id: Identifier
    purpose: str = Field(min_length=1)
    payload: JsonValue
    security: SecurityObject
    security_history: SecurityHistory

    @model_validator(mode="after")
    def validate_security(self) -> ContextBundle:
        if (
            self.security.subject_ref.subject_kind != "context_bundle"
            or self.security.subject_ref.local_id != self.id
        ):
            raise ValueError("ContextBundle security must be bound to the ContextBundle identity")
        if not self.security.verify_binding():
            raise ValueError("ContextBundle SecurityObject binding is invalid")
        if self.security.evidence_value("content_digest") != content_digest(self.payload):
            raise ValueError("ContextBundle SecurityObject does not bind its representation")
        if self.security_history.resolve(self.security.security_id) is None:
            raise ValueError("ContextBundle history must contain its SecurityObject")
        return self

    @classmethod
    def create(
        cls,
        *,
        owner_module_id: str,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel,
        integrity: OrdinarySecurityLevel | None = None,
        publication_revision: str = "1",
        representation_revision: str = "1",
        security_history: SecurityHistory | None = None,
    ) -> ContextBundle:
        security = _material_security(
            owner_module_id=owner_module_id,
            reference=bundle_id,
            kind="context_bundle",
            payload=payload,
            sensitivity=sensitivity,
            integrity=integrity,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
        )
        history = (security_history or SecurityHistory()).extend(objects=(security,))
        _validate_history(history)
        return cls(
            id=bundle_id,
            purpose=purpose,
            payload=payload,
            security=security,
            security_history=history,
        )

    @classmethod
    def select(
        cls,
        *,
        owner_module_id: str,
        bundle_id: str,
        purpose: str,
        members: tuple[Artifact, ...],
    ) -> ContextBundle:
        """Build a new bundle containing exactly the retained member representations."""
        if not members or len({item.id for item in members}) != len(members):
            raise ValueError("Selection requires nonempty uniquely named members")
        history = SecurityHistory().merge(*(item.security_history for item in members))
        levels = [item.security.values.sensitivity for item in members]
        if any(value is None for value in levels):
            raise ValueError("Selected members require Sensitivity")
        result = cls.create(
            owner_module_id=owner_module_id,
            bundle_id=bundle_id,
            purpose=purpose,
            payload={item.id: item.payload for item in members},
            sensitivity=max(value for value in levels if value is not None),
            security_history=history,
        )
        relation = SecurityDerivation.issue(
            kind="selection",
            output_security_id=result.security.security_id,
            source_security_ids=tuple(item.security.security_id for item in members),
        )
        history = result.security_history.extend(derivations=(relation,))
        _validate_history(history)
        return result.model_copy(update={"security_history": history})

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
        producer_security_ids: tuple[str, ...],
        sensitivity: OrdinarySecurityLevel,
        integrity: OrdinarySecurityLevel | None = None,
        additional_sources: tuple[Material | TransientMaterial, ...] = (),
        publication_revision: str = "1",
        representation_revision: str = "1",
        security_history: SecurityHistory | None = None,
    ) -> ContextBundle:
        history, source_ids = _merge_sources(source, additional_sources, security_history)
        history = history.extend(objects=invocation.objects)
        producer_security_ids = tuple(
            sorted(set((*producer_security_ids, *invocation.producer_security_ids)))
        )
        desired = integrity
        if desired is not None:
            assurance = _required_integrity(history, (*source_ids, *producer_security_ids))
            if desired > assurance:
                raise ValueError("ordinary derivation cannot increase Integrity")
        output = cls.create(
            owner_module_id=owner_module_id,
            bundle_id=bundle_id,
            purpose=purpose,
            payload=payload,
            sensitivity=sensitivity,
            integrity=desired,
            publication_revision=publication_revision,
            representation_revision=representation_revision,
            security_history=history,
        )
        relation = SecurityDerivation.issue(
            kind="ordinary",
            output_security_id=output.security.security_id,
            source_security_ids=source_ids,
            producer_security_ids=producer_security_ids,
        )
        final_history = output.security_history.extend(derivations=(relation,))
        _validate_history(final_history)
        return output.model_copy(update={"security_history": final_history})

    def derive(
        self,
        *,
        invocation: InvocationContext,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        producer_security_ids: tuple[str, ...],
        sensitivity: OrdinarySecurityLevel | None = None,
        integrity: OrdinarySecurityLevel | None = None,
        representation_revision: str = "1",
    ) -> ContextBundle:
        values = self.security.values
        return ContextBundle.derive_from(
            invocation=invocation,
            source=self,
            owner_module_id=self.security.subject_ref.owner_module_id,
            bundle_id=bundle_id,
            purpose=purpose,
            payload=payload,
            producer_security_ids=producer_security_ids,
            sensitivity=sensitivity or cast(OrdinarySecurityLevel, values.sensitivity),
            integrity=integrity,
            publication_revision=self.security.subject_ref.publication_revision,
            representation_revision=representation_revision,
        )

    def transient(self) -> TransientMaterial:
        return TransientMaterial(
            reference=self.id,
            payload=self.payload,
            digest=content_digest(self.payload),
            security=self.security,
            history=self.security_history,
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
            or material.history != handle.history
        ):
            return None
        return material
