"""Ordinary Module-owned Material values."""

from __future__ import annotations

import hashlib
import json
from collections.abc import Mapping
from typing import Protocol, TypeVar

from pydantic import Field, JsonValue, model_validator

from madre_sdk.security import (
    FrozenValue,
    IdentityKind,
    ScopeIdentity,
    SecurityScope,
    SecuritySurface,
)

PayloadT = TypeVar("PayloadT")
ResolvedT = TypeVar("ResolvedT", covariant=True)


def _canonical_payload(payload: JsonValue) -> bytes:
    return json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()


class MaterialContract(FrozenValue):
    """Stable semantic contract identity, independent of one wire language."""

    identity: ScopeIdentity
    media_type: str = Field(min_length=1)

    @model_validator(mode="after")
    def has_contract_identity(self) -> MaterialContract:
        self.identity.require(IdentityKind.MATERIAL_CONTRACT, "MaterialContract")
        return self


class MaterialSpecification(FrozenValue):
    """Module-owned identity and facts for an output that does not exist yet."""

    identity: ScopeIdentity
    contract: MaterialContract
    security: SecurityScope

    @model_validator(mode="after")
    def scope_matches_material(self) -> MaterialSpecification:
        self.identity.require(IdentityKind.MATERIAL, "MaterialSpecification")
        if self.security.identity != self.identity:
            raise ValueError("Material security must describe the same exact Material identity")
        if self.security.sensitivity is None:
            raise ValueError("Sensitivity applies to Material")
        return self


class Material[PayloadT](FrozenValue):
    """An ordinary value with independent identity and applicable security facts."""

    identity: ScopeIdentity
    contract: MaterialContract
    payload: PayloadT
    security: SecurityScope

    @model_validator(mode="after")
    def scope_matches_material(self) -> Material[PayloadT]:
        self.identity.require(IdentityKind.MATERIAL, "Material")
        if self.security.identity != self.identity:
            raise ValueError("Material security must describe the same exact Material identity")
        if self.security.sensitivity is None:
            raise ValueError("Sensitivity applies to Material")
        return self

    @property
    def surface(self) -> SecuritySurface:
        return SecuritySurface.from_scope(self.security)

    @property
    def digest(self) -> str:
        return hashlib.sha256(_canonical_payload(self.payload)).hexdigest()  # type: ignore[arg-type]

    @property
    def size(self) -> int:
        return len(_canonical_payload(self.payload))  # type: ignore[arg-type]

    def handle(self) -> MaterialHandle:
        return MaterialHandle(
            identity=self.identity,
            contract=self.contract,
            digest=self.digest,
            security=self.security,
        )


class MaterialHandle(FrozenValue):
    """Reference-only durable identity; it contains no Material payload."""

    identity: ScopeIdentity
    contract: MaterialContract
    digest: str
    security: SecurityScope

    @model_validator(mode="after")
    def scope_matches_material(self) -> MaterialHandle:
        self.identity.require(IdentityKind.MATERIAL, "MaterialHandle")
        if self.security.identity != self.identity:
            raise ValueError("Material security must describe the same exact Material identity")
        if self.security.sensitivity is None:
            raise ValueError("Sensitivity applies to Material")
        invalid_character = any(character not in "0123456789abcdef" for character in self.digest)
        if len(self.digest) != 64 or invalid_character:
            raise ValueError("digest must be a lowercase SHA-256 value")
        return self


class MaterialResolver(Protocol[ResolvedT]):
    async def resolve(self, handle: MaterialHandle) -> ResolvedT | None: ...


class MaterialRepository(MaterialResolver[Material[JsonValue]]):
    """A small Module-owned repository; Kernel receives only its resolver port."""

    def __init__(self) -> None:
        self._materials: dict[ScopeIdentity, Material[JsonValue]] = {}

    def put(self, material: Material[JsonValue]) -> None:
        existing = self._materials.get(material.identity)
        if existing is not None and existing != material:
            raise ValueError(
                f"Material identity already has different content: {material.identity.name}"
            )
        self._materials[material.identity] = material

    async def resolve(self, handle: MaterialHandle) -> Material[JsonValue] | None:
        material = self._materials.get(handle.identity)
        if material is None or material.handle() != handle:
            return None
        return material

    def snapshot(self) -> Mapping[ScopeIdentity, Material[JsonValue]]:
        return dict(self._materials)
