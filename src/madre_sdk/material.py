"""Module-owned typed Material values."""

from __future__ import annotations

from dataclasses import dataclass

from madre_sdk.algebra import Sensitivity
from madre_sdk.identity import MaterialId, MaterialTypeId, ModuleId


@dataclass(frozen=True, slots=True)
class MaterialType[PayloadT]:
    identity: MaterialTypeId
    media_type: str

    def __post_init__(self) -> None:
        if not isinstance(self.identity, MaterialTypeId):
            raise TypeError("MaterialType.identity requires MaterialTypeId")
        if not self.media_type or self.media_type.isspace():
            raise ValueError("MaterialType.media_type must not be blank")


@dataclass(frozen=True, slots=True)
class Material[PayloadT]:
    identity: MaterialId
    material_type: MaterialType[PayloadT]
    payload: PayloadT
    sensitivity: Sensitivity

    def __post_init__(self) -> None:
        if not isinstance(self.identity, MaterialId):
            raise TypeError("Material.identity requires MaterialId")
        if not isinstance(self.material_type, MaterialType):
            raise TypeError("Material.material_type requires MaterialType")
        if not isinstance(self.sensitivity, Sensitivity):
            raise TypeError("Material.sensitivity requires Sensitivity")

    @property
    def owner(self) -> ModuleId:
        return self.identity.module


@dataclass(frozen=True, slots=True)
class MaterialSet:
    materials: tuple[Material[object], ...]

    def __post_init__(self) -> None:
        if not self.materials:
            raise ValueError("MaterialSet requires at least one Material")
        if any(not isinstance(material, Material) for material in self.materials):
            raise TypeError("MaterialSet accepts only Material values")
        identities = tuple(material.identity for material in self.materials)
        if len(identities) != len(set(identities)):
            raise ValueError("MaterialSet cannot repeat a Material identity")

    @classmethod
    def of(cls, first: Material[object], *rest: Material[object]) -> MaterialSet:
        return cls((first, *rest))

    @property
    def sensitivity(self) -> Sensitivity:
        first, *rest = (material.sensitivity for material in self.materials)
        return Sensitivity.maximum(first, *rest)

    def including(self, material: Material[object]) -> MaterialSet:
        return type(self)((*self.materials, material))
