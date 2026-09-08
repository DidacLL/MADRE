"""Immutable carried security state and deterministic MADRE boundary algebra."""

from __future__ import annotations

import hashlib
import json
from enum import IntEnum
from typing import Annotated, Literal, cast

from pydantic import BaseModel, ConfigDict, Field

Identifier = Annotated[str, Field(min_length=1)]
ScopeIdentifier = Annotated[str, Field(min_length=1)]


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
    """Immutable security facts bound to one material, descriptor, actor, or boundary."""

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
        return self.integrity == hashlib.sha256(_canonical(self.integrity_payload())).hexdigest()

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


class SecurityContext(FrozenModel):
    """Security state carried by one request/work lifecycle.

    Each boundary contributes another immutable envelope. No registry entry, token,
    allowlist, or previous decision grants authority to the context.
    """

    envelopes: tuple[SecurityEnvelope, ...] = Field(min_length=1)

    def extend(self, *envelopes: SecurityEnvelope) -> SecurityContext:
        return SecurityContext(envelopes=(*self.envelopes, *envelopes))

    @property
    def sensitivity(self) -> OrdinarySecurityLevel:
        return cast(
            OrdinarySecurityLevel,
            SecurityLevel(max(int(item.sensitivity) for item in self.envelopes)),
        )

    @property
    def trust(self) -> OrdinarySecurityLevel:
        return cast(
            OrdinarySecurityLevel,
            SecurityLevel(min(int(item.trust) for item in self.envelopes)),
        )

    @property
    def risk(self) -> OrdinarySecurityLevel:
        return cast(
            OrdinarySecurityLevel,
            SecurityLevel(max(int(item.risk) for item in self.envelopes)),
        )

    @property
    def scopes(self) -> frozenset[ScopeIdentifier]:
        return frozenset(scope for item in self.envelopes for scope in item.scopes)


class SecurityDecision(FrozenModel):
    admissible: bool
    deficits: tuple[Identifier, ...] = ()
    sensitivity: OrdinarySecurityLevel
    trust: OrdinarySecurityLevel
    risk: OrdinarySecurityLevel
    scopes: frozenset[ScopeIdentifier] = Field(default_factory=frozenset)


class SecurityAlgebra:
    """Pure additive boundary algebra over the security context carried by the work."""

    @staticmethod
    def evaluate(context: SecurityContext) -> SecurityDecision:
        deficits: list[str] = []
        for envelope in context.envelopes:
            if not envelope.verify_integrity():
                deficits.append(f"invalid_integrity:{envelope.subject}")

        sensitivity = context.sensitivity
        trust = context.trust
        risk = context.risk

        if int(trust) < int(sensitivity):
            deficits.append("trust_below_sensitivity")
        if int(trust) < int(risk):
            deficits.append("trust_below_risk")

        return SecurityDecision(
            admissible=not deficits,
            deficits=tuple(deficits),
            sensitivity=sensitivity,
            trust=trust,
            risk=risk,
            scopes=context.scopes,
        )
