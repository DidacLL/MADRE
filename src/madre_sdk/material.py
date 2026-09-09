"""Module-owned material helpers over MADRE's public execution contracts."""

from __future__ import annotations

import hashlib
import json
from typing import Literal, cast

from pydantic import Field, JsonValue, model_validator

from madre.contracts import MaterialHandle, TransientInferenceResult, TransientMaterial
from madre.interfaces import MaterialResolver
from madre.security import (
    FrozenModel,
    Identifier,
    MaterialSecurityValues,
    OrdinarySecurityLevel,
    SecurityObject,
)

MaterialKind = Literal["artifact", "context_bundle"]


def content_digest(payload: JsonValue) -> str:
    """Return the canonical digest used by public MADRE material contracts."""
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def _material_security(
    reference: str,
    kind: MaterialKind,
    sensitivity: OrdinarySecurityLevel,
    intended_use: OrdinarySecurityLevel | None,
) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=reference,
        subject_kind=kind,
        values=MaterialSecurityValues(
            sensitivity=sensitivity,
            intended_use=intended_use,
        ),
    )


class Artifact(FrozenModel):
    """One Module-owned material representation with its own security binding."""

    id: Identifier
    payload: JsonValue
    security: SecurityObject

    @model_validator(mode="after")
    def validate_security(self) -> Artifact:
        if self.security.subject_kind != "artifact" or self.security.subject_id != self.id:
            raise ValueError("Artifact security must be bound to the Artifact identity")
        if not self.security.verify_integrity():
            raise ValueError("Artifact SecurityObject integrity is invalid")
        return self

    @classmethod
    def create(
        cls,
        *,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel,
        intended_use: OrdinarySecurityLevel | None = None,
    ) -> Artifact:
        return cls(
            id=artifact_id,
            payload=payload,
            security=_material_security(
                artifact_id,
                "artifact",
                sensitivity,
                intended_use,
            ),
        )

    @classmethod
    def from_inference_result(
        cls,
        *,
        artifact_id: str,
        result: TransientInferenceResult,
        sensitivity: OrdinarySecurityLevel,
        intended_use: OrdinarySecurityLevel | None = None,
    ) -> Artifact:
        return cls.create(
            artifact_id=artifact_id,
            payload=result.payload,
            sensitivity=sensitivity,
            intended_use=intended_use,
        )

    def derive(
        self,
        *,
        artifact_id: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel | None = None,
        intended_use: OrdinarySecurityLevel | None = None,
    ) -> Artifact:
        values = cast(MaterialSecurityValues, self.security.values)
        return Artifact.create(
            artifact_id=artifact_id,
            payload=payload,
            sensitivity=sensitivity or values.sensitivity,
            intended_use=values.intended_use if intended_use is None else intended_use,
        )

    def transient(self) -> TransientMaterial:
        return TransientMaterial(
            reference=self.id,
            payload=self.payload,
            digest=content_digest(self.payload),
            security=self.security,
        )

    def handle(self, *, coordination: str | None = None) -> MaterialHandle:
        return self.transient().to_handle(coordination=coordination)


class ContextBundle(FrozenModel):
    """Purpose-scoped Module material prepared for a concrete boundary/use."""

    id: Identifier
    purpose: str = Field(min_length=1)
    payload: JsonValue
    security: SecurityObject

    @model_validator(mode="after")
    def validate_security(self) -> ContextBundle:
        if self.security.subject_kind != "context_bundle" or self.security.subject_id != self.id:
            raise ValueError("ContextBundle security must be bound to the ContextBundle identity")
        if not self.security.verify_integrity():
            raise ValueError("ContextBundle SecurityObject integrity is invalid")
        return self

    @classmethod
    def create(
        cls,
        *,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel,
        intended_use: OrdinarySecurityLevel | None = None,
    ) -> ContextBundle:
        return cls(
            id=bundle_id,
            purpose=purpose,
            payload=payload,
            security=_material_security(
                bundle_id,
                "context_bundle",
                sensitivity,
                intended_use,
            ),
        )

    def derive(
        self,
        *,
        bundle_id: str,
        purpose: str,
        payload: JsonValue,
        sensitivity: OrdinarySecurityLevel | None = None,
        intended_use: OrdinarySecurityLevel | None = None,
    ) -> ContextBundle:
        values = cast(MaterialSecurityValues, self.security.values)
        return ContextBundle.create(
            bundle_id=bundle_id,
            purpose=purpose,
            payload=payload,
            sensitivity=sensitivity or values.sensitivity,
            intended_use=values.intended_use if intended_use is None else intended_use,
        )

    def transient(self) -> TransientMaterial:
        return TransientMaterial(
            reference=self.id,
            payload=self.payload,
            digest=content_digest(self.payload),
            security=self.security,
        )

    def handle(self, *, coordination: str | None = None) -> MaterialHandle:
        return self.transient().to_handle(coordination=coordination)


Material = Artifact | ContextBundle


class MaterialRepository(MaterialResolver):
    """Simple Module-owned resolver for prepared durable material.

    It intentionally provides no Kernel persistence. Modules may replace it with their own durable
    repository while preserving the same public MaterialResolver contract.
    """

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
        if material.digest != handle.digest or material.security != handle.security:
            return None
        return material
