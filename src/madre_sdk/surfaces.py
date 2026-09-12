"""Role-specific public surfaces and their intrinsic composition."""

from __future__ import annotations

from dataclasses import dataclass

from madre_sdk.algebra import Integrity, Privacy, Sensitivity
from madre_sdk.identity import MaterialTypeId, SurfaceId
from madre_sdk.material import MaterialSet


@dataclass(frozen=True, slots=True)
class InputSurface:
    identity: SurfaceId
    material_type: MaterialTypeId
    privacy: Privacy

    def __post_init__(self) -> None:
        if not isinstance(self.identity, SurfaceId):
            raise TypeError("InputSurface.identity requires SurfaceId")
        if not isinstance(self.material_type, MaterialTypeId):
            raise TypeError("InputSurface.material_type requires MaterialTypeId")
        if not isinstance(self.privacy, Privacy):
            raise TypeError("InputSurface.privacy requires Privacy")

    def compose(self, materials: MaterialSet) -> MaterialSet:
        if any(
            material.material_type.identity != self.material_type
            for material in materials.materials
        ):
            raise ValueError("Material type is not accepted by this input surface")
        return materials.compose_with(self.privacy)


@dataclass(frozen=True, slots=True)
class OutputSurface:
    identity: SurfaceId
    material_type: MaterialTypeId
    sensitivity: Sensitivity

    def __post_init__(self) -> None:
        if not isinstance(self.identity, SurfaceId):
            raise TypeError("OutputSurface.identity requires SurfaceId")
        if not isinstance(self.material_type, MaterialTypeId):
            raise TypeError("OutputSurface.material_type requires MaterialTypeId")
        if not isinstance(self.sensitivity, Sensitivity):
            raise TypeError("OutputSurface.sensitivity requires Sensitivity")


@dataclass(frozen=True, slots=True)
class ResponsibilitySurface:
    identity: SurfaceId
    integrity: Integrity

    def __post_init__(self) -> None:
        if not isinstance(self.identity, SurfaceId):
            raise TypeError("ResponsibilitySurface.identity requires SurfaceId")
        if not isinstance(self.integrity, Integrity):
            raise TypeError("ResponsibilitySurface.integrity requires Integrity")


@dataclass(frozen=True, slots=True)
class InputSurfaces:
    members: tuple[InputSurface, ...]

    def __post_init__(self) -> None:
        if not self.members:
            raise ValueError("InputSurfaces requires at least one surface")
        if any(not isinstance(member, InputSurface) for member in self.members):
            raise TypeError("InputSurfaces accepts only InputSurface values")
        identities = tuple(member.identity for member in self.members)
        if len(identities) != len(set(identities)):
            raise ValueError("InputSurfaces cannot repeat a surface identity")
        material_types = tuple(member.material_type for member in self.members)
        if len(material_types) != len(set(material_types)):
            raise ValueError("InputSurfaces requires one surface per MaterialType")

    @classmethod
    def of(cls, first: InputSurface, *rest: InputSurface) -> InputSurfaces:
        return cls((first, *rest))

    @property
    def privacy(self) -> Privacy:
        first, *rest = (member.privacy for member in self.members)
        return Privacy.minimum(first, *rest)

    def compose(self, materials: MaterialSet) -> MaterialSet:
        by_type = {member.material_type: member for member in self.members}
        actual = tuple(
            by_type.get(material.material_type.identity) for material in materials.materials
        )
        if any(surface is None for surface in actual):
            raise ValueError("Material type is not accepted by these input surfaces")
        privacy_values = tuple(surface.privacy for surface in actual if surface is not None)
        first, *rest = privacy_values
        privacy = Privacy.minimum(first, *rest)
        return materials.compose_with(privacy)


@dataclass(frozen=True, slots=True)
class OutputSurfaces:
    members: tuple[OutputSurface, ...]

    def __post_init__(self) -> None:
        if not self.members:
            raise ValueError("OutputSurfaces requires at least one surface")
        if any(not isinstance(member, OutputSurface) for member in self.members):
            raise TypeError("OutputSurfaces accepts only OutputSurface values")
        identities = tuple(member.identity for member in self.members)
        if len(identities) != len(set(identities)):
            raise ValueError("OutputSurfaces cannot repeat a surface identity")

    @classmethod
    def of(cls, first: OutputSurface, *rest: OutputSurface) -> OutputSurfaces:
        return cls((first, *rest))

    @property
    def sensitivity(self) -> Sensitivity:
        first, *rest = (member.sensitivity for member in self.members)
        return Sensitivity.maximum(first, *rest)


@dataclass(frozen=True, slots=True)
class ResponsibilitySurfaces:
    members: tuple[ResponsibilitySurface, ...]

    def __post_init__(self) -> None:
        if not self.members:
            raise ValueError("ResponsibilitySurfaces requires at least one surface")
        if any(not isinstance(member, ResponsibilitySurface) for member in self.members):
            raise TypeError("ResponsibilitySurfaces accepts only ResponsibilitySurface values")
        identities = tuple(member.identity for member in self.members)
        if len(identities) != len(set(identities)):
            raise ValueError("ResponsibilitySurfaces cannot repeat a surface identity")

    @classmethod
    def of(
        cls,
        first: ResponsibilitySurface,
        *rest: ResponsibilitySurface,
    ) -> ResponsibilitySurfaces:
        return cls((first, *rest))

    @property
    def integrity(self) -> Integrity:
        first, *rest = (member.integrity for member in self.members)
        return Integrity.minimum(first, *rest)
