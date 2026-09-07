"""Deterministic multidimensional boundary evaluation for MADRE crossings."""

from __future__ import annotations

import hashlib
import json
from enum import IntEnum
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


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


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), default=int).encode()


class SecurityEnvelope(FrozenModel):
    """Immutable traceable facts bound to a descriptor or material subject."""

    subject: str = Field(min_length=1)
    sensitivity: OrdinarySecurityLevel
    trust: OrdinarySecurityLevel
    risk: OrdinarySecurityLevel
    scopes: frozenset[str] = Field(default_factory=frozenset)
    origin: str = Field(min_length=1)
    provenance: tuple[str, ...] = ()
    derivation: tuple[str, ...] = ()
    descriptor_version: str = Field(default="1", min_length=1)
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
    allowed_scopes: frozenset[str] = Field(default_factory=lambda: frozenset({"*"}))
    allowed_execution_boundaries: frozenset[ExecutionBoundary] = Field(
        default_factory=lambda: frozenset({"local"})
    )


class SecurityPolicy(FrozenModel):
    min_requester_trust: OrdinarySecurityLevel = SecurityLevel.LEVEL_1
    max_risk_by_sensitivity: dict[OrdinarySecurityLevel, OrdinarySecurityLevel] = Field(
        default_factory=lambda: {
            SecurityLevel.LEVEL_1: SecurityLevel.LEVEL_5,
            SecurityLevel.LEVEL_2: SecurityLevel.LEVEL_5,
            SecurityLevel.LEVEL_3: SecurityLevel.LEVEL_4,
            SecurityLevel.LEVEL_4: SecurityLevel.LEVEL_3,
            SecurityLevel.LEVEL_5: SecurityLevel.LEVEL_2,
        }
    )
    min_destination_trust_by_sensitivity: dict[
        OrdinarySecurityLevel, OrdinarySecurityLevel
    ] = Field(
        default_factory=lambda: {
            SecurityLevel.LEVEL_1: SecurityLevel.LEVEL_1,
            SecurityLevel.LEVEL_2: SecurityLevel.LEVEL_2,
            SecurityLevel.LEVEL_3: SecurityLevel.LEVEL_3,
            SecurityLevel.LEVEL_4: SecurityLevel.LEVEL_4,
            SecurityLevel.LEVEL_5: SecurityLevel.LEVEL_5,
        }
    )

    @model_validator(mode="after")
    def complete_tables(self) -> SecurityPolicy:
        ordinary = set(SecurityLevel) - {SecurityLevel.SYSTEM_RESERVED}
        if set(self.max_risk_by_sensitivity) != ordinary:
            raise ValueError("max_risk_by_sensitivity must define levels 1..5")
        if set(self.min_destination_trust_by_sensitivity) != ordinary:
            raise ValueError("min_destination_trust_by_sensitivity must define levels 1..5")
        return self


class SecurityDecision(FrozenModel):
    admissible: bool
    deficits: tuple[str, ...] = ()


class SecurityAlgebra:
    """Pure relations over independent sensitivity, trust, risk and scope dimensions."""

    @staticmethod
    def visible(
        requester: SecurityEnvelope,
        target: BoundaryRequirements,
        destination: SecurityEnvelope,
        policy: SecurityPolicy,
    ) -> SecurityDecision:
        deficits: list[str] = []
        SecurityAlgebra._integrity_deficits((requester, destination), deficits)
        minimum = max(int(policy.min_requester_trust), int(target.min_requester_trust))
        if int(requester.trust) < minimum:
            deficits.append("requester_trust")
        if "*" not in target.allowed_scopes and not (
            requester.scopes & target.allowed_scopes
        ):
            deficits.append("scope_visibility")
        return SecurityDecision(admissible=not deficits, deficits=tuple(deficits))

    @staticmethod
    def evaluate(
        requester: SecurityEnvelope,
        material: SecurityEnvelope,
        target: BoundaryRequirements,
        destination: SecurityEnvelope,
        execution_boundary: ExecutionBoundary,
        policy: SecurityPolicy,
    ) -> SecurityDecision:
        deficits: list[str] = []
        SecurityAlgebra._integrity_deficits((requester, material, destination), deficits)
        minimum_requester = max(
            int(policy.min_requester_trust), int(target.min_requester_trust)
        )
        if int(requester.trust) < minimum_requester:
            deficits.append("requester_trust")
        if int(material.trust) < int(target.min_input_trust):
            deficits.append("material_trust")
        if int(material.sensitivity) > int(target.max_input_sensitivity):
            deficits.append("material_sensitivity")
        max_risk = policy.max_risk_by_sensitivity[material.sensitivity]
        if int(target.risk) > int(max_risk):
            deficits.append("target_risk")
        destination_floor = policy.min_destination_trust_by_sensitivity[material.sensitivity]
        if int(destination.trust) < int(destination_floor):
            deficits.append("destination_trust")
        if execution_boundary not in target.allowed_execution_boundaries:
            deficits.append("execution_boundary")
        if "*" not in target.allowed_scopes and not material.scopes.issubset(
            target.allowed_scopes
        ):
            deficits.append("target_scope")
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
