"""Deterministic multidimensional boundary evaluation for MADRE crossings."""

from __future__ import annotations

import hashlib
import json
from enum import IntEnum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field

Identifier = Annotated[
    str,
    Field(
        min_length=1,
        max_length=160,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/@-]*$",
    ),
]
ScopeIdentifier = Annotated[
    str,
    Field(
        min_length=1,
        max_length=160,
        pattern=r"^(\*|[A-Za-z0-9][A-Za-z0-9._:/@-]*)$",
    ),
]


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
ExecutionBoundary = Literal["local", "isolated", "remote"]
SecurityTable = tuple[
    OrdinarySecurityLevel,
    OrdinarySecurityLevel,
    OrdinarySecurityLevel,
    OrdinarySecurityLevel,
    OrdinarySecurityLevel,
]


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), default=int).encode()


class SecurityEnvelope(FrozenModel):
    """Immutable traceable facts bound to a descriptor or material subject."""

    subject: Identifier
    sensitivity: OrdinarySecurityLevel
    trust: OrdinarySecurityLevel
    risk: OrdinarySecurityLevel
    scopes: frozenset[ScopeIdentifier] = Field(default_factory=frozenset)
    origin: Identifier
    provenance: tuple[Identifier, ...] = ()
    derivation: tuple[Identifier, ...] = ()
    descriptor_version: Identifier = "1"
    integrity: str = Field(pattern=r"^[0-9a-f]{64}$")

    def integrity_payload(self) -> dict[str, object]:
        return {
            "subject": self.subject,
            "sensitivity": int(self.sensitivity),
            "trust": int(self.trust),
            "risk": int(self.risk),
            "scopes": sorted(self.scopes),
            "origin": self.origin,
            "provenance": list(self.provenance),
            "derivation": list(self.derivation),
            "descriptor_version": self.descriptor_version,
        }

    def verify_integrity(self) -> bool:
        expected = hashlib.sha256(_canonical(self.integrity_payload())).hexdigest()
        return expected == self.integrity

    @classmethod
    def issue(
        cls,
        *,
        subject: str,
        sensitivity: OrdinarySecurityLevel,
        trust: OrdinarySecurityLevel,
        risk: OrdinarySecurityLevel,
        scopes: frozenset[str] | set[str] = frozenset(),
        origin: str,
        provenance: tuple[str, ...] = (),
        derivation: tuple[str, ...] = (),
        descriptor_version: str = "1",
    ) -> SecurityEnvelope:
        payload = {
            "subject": subject,
            "sensitivity": int(sensitivity),
            "trust": int(trust),
            "risk": int(risk),
            "scopes": sorted(scopes),
            "origin": origin,
            "provenance": list(provenance),
            "derivation": list(derivation),
            "descriptor_version": descriptor_version,
        }
        return cls(
            subject=subject,
            sensitivity=sensitivity,
            trust=trust,
            risk=risk,
            scopes=frozenset(scopes),
            origin=origin,
            provenance=provenance,
            derivation=derivation,
            descriptor_version=descriptor_version,
            integrity=hashlib.sha256(_canonical(payload)).hexdigest(),
        )


class BoundaryRequirements(FrozenModel):
    min_requester_trust: OrdinarySecurityLevel = SecurityLevel.LEVEL_1
    min_input_trust: OrdinarySecurityLevel = SecurityLevel.LEVEL_1
    max_input_sensitivity: OrdinarySecurityLevel = SecurityLevel.LEVEL_5
    risk: OrdinarySecurityLevel = SecurityLevel.LEVEL_1
    allowed_scopes: frozenset[ScopeIdentifier] = Field(
        default_factory=lambda: frozenset({"*"})
    )
    allowed_execution_boundaries: frozenset[ExecutionBoundary] = Field(
        default_factory=lambda: frozenset({"local"})
    )


class SecurityPolicy(FrozenModel):
    min_requester_trust: OrdinarySecurityLevel = SecurityLevel.LEVEL_1
    max_risk_by_sensitivity: SecurityTable = (
        SecurityLevel.LEVEL_5,
        SecurityLevel.LEVEL_5,
        SecurityLevel.LEVEL_4,
        SecurityLevel.LEVEL_3,
        SecurityLevel.LEVEL_2,
    )
    min_destination_trust_by_sensitivity: SecurityTable = (
        SecurityLevel.LEVEL_1,
        SecurityLevel.LEVEL_2,
        SecurityLevel.LEVEL_3,
        SecurityLevel.LEVEL_4,
        SecurityLevel.LEVEL_5,
    )

    def max_risk(self, sensitivity: OrdinarySecurityLevel) -> OrdinarySecurityLevel:
        return self.max_risk_by_sensitivity[int(sensitivity) - 1]

    def min_destination_trust(
        self, sensitivity: OrdinarySecurityLevel
    ) -> OrdinarySecurityLevel:
        return self.min_destination_trust_by_sensitivity[int(sensitivity) - 1]


class SecurityDecision(FrozenModel):
    admissible: bool
    deficits: tuple[Identifier, ...] = ()


class SecurityAlgebra:
    """Pure relations over independent sensitivity, trust, risk and scope dimensions."""

    @staticmethod
    def visible(
        requester: SecurityEnvelope,
        target: BoundaryRequirements,
        target_envelope: SecurityEnvelope,
        destination: SecurityEnvelope,
        policy: SecurityPolicy,
    ) -> SecurityDecision:
        deficits: list[str] = []
        SecurityAlgebra._integrity_deficits(
            (requester, target_envelope, destination), deficits
        )
        minimum = max(int(policy.min_requester_trust), int(target.min_requester_trust))
        if int(requester.trust) < minimum:
            deficits.append("requester_trust")
        if "*" not in target.allowed_scopes and not (
            requester.scopes & target.allowed_scopes
        ):
            deficits.append("scope_visibility")
        if "*" not in target_envelope.scopes and not (
            requester.scopes & target_envelope.scopes
        ):
            deficits.append("target_scope_visibility")
        if "*" not in destination.scopes and not (
            requester.scopes & destination.scopes
        ):
            deficits.append("destination_scope_visibility")
        return SecurityDecision(admissible=not deficits, deficits=tuple(deficits))

    @staticmethod
    def evaluate(
        requester: SecurityEnvelope,
        material: SecurityEnvelope,
        target: BoundaryRequirements,
        target_envelope: SecurityEnvelope,
        destination: SecurityEnvelope,
        execution_boundary: ExecutionBoundary,
        policy: SecurityPolicy,
    ) -> SecurityDecision:
        deficits: list[str] = []
        SecurityAlgebra._integrity_deficits(
            (requester, material, target_envelope, destination), deficits
        )
        minimum_requester = max(
            int(policy.min_requester_trust), int(target.min_requester_trust)
        )
        if int(requester.trust) < minimum_requester:
            deficits.append("requester_trust")
        if int(material.trust) > int(requester.trust):
            deficits.append("material_trust_provenance")
        if int(material.trust) < int(target.min_input_trust):
            deficits.append("material_trust")
        if int(material.sensitivity) > int(target.max_input_sensitivity):
            deficits.append("material_sensitivity")
        allowed_risk = policy.max_risk(material.sensitivity)
        if int(requester.risk) > int(allowed_risk):
            deficits.append("requester_risk")
        if max(int(target.risk), int(target_envelope.risk)) > int(allowed_risk):
            deficits.append("target_risk")
        if int(destination.risk) > int(allowed_risk):
            deficits.append("destination_risk")
        trust_floor = policy.min_destination_trust(material.sensitivity)
        if int(target_envelope.trust) < int(trust_floor):
            deficits.append("target_trust")
        if int(destination.trust) < int(trust_floor):
            deficits.append("destination_trust")
        if execution_boundary not in target.allowed_execution_boundaries:
            deficits.append("execution_boundary")
        if "*" not in target.allowed_scopes and not material.scopes.issubset(
            target.allowed_scopes
        ):
            deficits.append("target_scope")
        if "*" not in target_envelope.scopes and not material.scopes.issubset(
            target_envelope.scopes
        ):
            deficits.append("target_envelope_scope")
        if "*" not in destination.scopes and not material.scopes.issubset(
            destination.scopes
        ):
            deficits.append("destination_scope")
        if "*" not in requester.scopes and not material.scopes.issubset(requester.scopes):
            deficits.append("requester_scope")
        return SecurityDecision(admissible=not deficits, deficits=tuple(deficits))

    @staticmethod
    def _integrity_deficits(
        envelopes: tuple[SecurityEnvelope, ...], deficits: list[str]
    ) -> None:
        for envelope in envelopes:
            if not envelope.verify_integrity():
                deficits.append(f"invalid_integrity:{envelope.subject}")
