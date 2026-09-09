"""Bound security objects, carried composition, and the temporary compatibility evaluator."""

from __future__ import annotations

import hashlib
import json
from enum import IntEnum
from typing import Annotated, Literal, Protocol, cast

from pydantic import BaseModel, ConfigDict, Field, model_validator

Identifier = Annotated[str, Field(min_length=1)]
SecurityID = Identifier
ExecutionBoundary = Literal["local", "isolated", "remote"]


class FrozenModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class SecurityLevel(IntEnum):
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5


OrdinarySecurityLevel = Literal[
    SecurityLevel.LEVEL_1,
    SecurityLevel.LEVEL_2,
    SecurityLevel.LEVEL_3,
    SecurityLevel.LEVEL_4,
    SecurityLevel.LEVEL_5,
]


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), default=int).encode()


class MaterialSecurityValues(FrozenModel):
    kind: Literal["material"] = "material"
    sensitivity: OrdinarySecurityLevel


class ActorSecurityValues(FrozenModel):
    kind: Literal["actor"] = "actor"
    trust: OrdinarySecurityLevel
    isolation: OrdinarySecurityLevel


class OperationSecurityValues(FrozenModel):
    kind: Literal["operation"] = "operation"
    risk: OrdinarySecurityLevel
    autonomy: OrdinarySecurityLevel


class CapabilitySecurityValues(FrozenModel):
    kind: Literal["capability"] = "capability"
    trust: OrdinarySecurityLevel
    privacy: OrdinarySecurityLevel
    risk: OrdinarySecurityLevel


SecurityValues = Annotated[
    MaterialSecurityValues
    | ActorSecurityValues
    | OperationSecurityValues
    | CapabilitySecurityValues,
    Field(discriminator="kind"),
]
SecuritySubjectKind = Literal[
    "artifact",
    "context_bundle",
    "module",
    "agent",
    "operation",
    "capability",
    "endpoint",
]


class SecurityObject(FrozenModel):
    """Immutable normalized facts structurally bound to one security-relevant subject."""

    security_id: SecurityID
    subject_id: Identifier
    subject_kind: SecuritySubjectKind
    values: SecurityValues
    origin: Identifier
    provenance: tuple[Identifier, ...] = ()
    derivation: tuple[Identifier, ...] = ()
    descriptor_version: Identifier = "1"
    integrity: str = Field(pattern=r"^[0-9a-f]{64}$")

    @model_validator(mode="after")
    def values_match_subject_kind(self) -> SecurityObject:
        expected: dict[str, type[BaseModel]] = {
            "artifact": MaterialSecurityValues,
            "context_bundle": MaterialSecurityValues,
            "module": ActorSecurityValues,
            "agent": ActorSecurityValues,
            "operation": OperationSecurityValues,
            "capability": CapabilitySecurityValues,
            "endpoint": ActorSecurityValues,
        }
        if not isinstance(self.values, expected[self.subject_kind]):
            raise ValueError(f"{self.subject_kind} has incompatible security values")
        return self

    def integrity_payload(self) -> dict[str, object]:
        return {
            "security_id": self.security_id,
            "subject_id": self.subject_id,
            "subject_kind": self.subject_kind,
            "values": self.values.model_dump(mode="json"),
            "origin": self.origin,
            "provenance": list(self.provenance),
            "derivation": list(self.derivation),
            "descriptor_version": self.descriptor_version,
        }

    def verify_integrity(self) -> bool:
        return self.integrity == hashlib.sha256(_canonical(self.integrity_payload())).hexdigest()

    @classmethod
    def issue(
        cls,
        *,
        subject_id: str,
        subject_kind: SecuritySubjectKind,
        values: SecurityValues,
        origin: str,
        security_id: str | None = None,
        provenance: tuple[str, ...] = (),
        derivation: tuple[str, ...] = (),
        descriptor_version: str = "1",
    ) -> SecurityObject:
        identity = security_id or f"security:{subject_kind}:{subject_id}"
        payload = {
            "security_id": identity,
            "subject_id": subject_id,
            "subject_kind": subject_kind,
            "values": values.model_dump(mode="json"),
            "origin": origin,
            "provenance": list(provenance),
            "derivation": list(derivation),
            "descriptor_version": descriptor_version,
        }
        return cls(
            security_id=identity,
            subject_id=subject_id,
            subject_kind=subject_kind,
            values=values,
            origin=origin,
            provenance=provenance,
            derivation=derivation,
            descriptor_version=descriptor_version,
            integrity=hashlib.sha256(_canonical(payload)).hexdigest(),
        )


class SecurityContext(FrozenModel):
    """Security objects carried by one concrete request/work lifecycle."""

    objects: tuple[SecurityObject, ...] = Field(min_length=1)

    def extend(self, *objects: SecurityObject) -> SecurityContext:
        return SecurityContext(objects=(*self.objects, *objects))

    @property
    def security_ids(self) -> tuple[SecurityID, ...]:
        return tuple(item.security_id for item in self.objects)


class DecisionEvidence(FrozenModel):
    key: Identifier
    value: Identifier


class SecurityDecision(FrozenModel):
    admissible: bool
    deficits: tuple[Identifier, ...] = ()
    evaluator: Identifier
    evidence: tuple[DecisionEvidence, ...] = ()


class SecurityEvaluator(Protocol):
    def evaluate(self, context: SecurityContext) -> SecurityDecision: ...


class CompatibilitySecurityEvaluator:
    """Temporary implementation evaluator retained behind the SecurityObject seam.

    This preserves the repository's existing max/min behavior while the canonical algebra remains
    intentionally open. Callers depend only on ``SecurityEvaluator`` and ``SecurityDecision``.
    """

    name = "compatibility-max-min-v1"

    def evaluate(self, context: SecurityContext) -> SecurityDecision:
        deficits: list[str] = []
        for item in context.objects:
            if not item.verify_integrity():
                deficits.append(f"invalid_integrity:{item.security_id}")

        sensitivities = [
            int(item.values.sensitivity)
            for item in context.objects
            if isinstance(item.values, MaterialSecurityValues)
        ]
        trusts = [
            int(item.values.trust)
            for item in context.objects
            if isinstance(item.values, (ActorSecurityValues, CapabilitySecurityValues))
        ]
        risks = [
            int(item.values.risk)
            for item in context.objects
            if isinstance(item.values, (OperationSecurityValues, CapabilitySecurityValues))
        ]
        sensitivity = cast(
            OrdinarySecurityLevel,
            SecurityLevel(max(sensitivities, default=int(SecurityLevel.LEVEL_1))),
        )
        trust = cast(
            OrdinarySecurityLevel,
            SecurityLevel(min(trusts, default=int(SecurityLevel.LEVEL_5))),
        )
        risk = cast(
            OrdinarySecurityLevel,
            SecurityLevel(max(risks, default=int(SecurityLevel.LEVEL_1))),
        )

        if int(trust) < int(sensitivity):
            deficits.append("trust_below_sensitivity")
        if int(trust) < int(risk):
            deficits.append("trust_below_risk")

        return SecurityDecision(
            admissible=not deficits,
            deficits=tuple(deficits),
            evaluator=self.name,
            evidence=(
                DecisionEvidence(key="effective_sensitivity", value=str(int(sensitivity))),
                DecisionEvidence(key="effective_trust", value=str(int(trust))),
                DecisionEvidence(key="effective_risk", value=str(int(risk))),
            ),
        )


DEFAULT_SECURITY_EVALUATOR: SecurityEvaluator = CompatibilitySecurityEvaluator()
