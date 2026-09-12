"""Nominal identities for public MADRE domain objects."""

from __future__ import annotations

from dataclasses import dataclass


def _require_text(value: str, field: str) -> None:
    if not value or value.isspace():
        raise ValueError(f"{field} must not be blank")


def _require_module(value: object, field: str) -> None:
    if not isinstance(value, ModuleId):
        raise TypeError(f"{field} requires ModuleId")


@dataclass(frozen=True, slots=True)
class ModuleId:
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_text(self.name, "ModuleId.name")
        _require_text(self.revision, "ModuleId.revision")


@dataclass(frozen=True, slots=True)
class AgentId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "AgentId.module")
        _require_text(self.name, "AgentId.name")
        _require_text(self.revision, "AgentId.revision")


@dataclass(frozen=True, slots=True)
class SkillId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "SkillId.module")
        _require_text(self.name, "SkillId.name")
        _require_text(self.revision, "SkillId.revision")


@dataclass(frozen=True, slots=True)
class WorkflowId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "WorkflowId.module")
        _require_text(self.name, "WorkflowId.name")
        _require_text(self.revision, "WorkflowId.revision")


@dataclass(frozen=True, slots=True)
class OperationId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "OperationId.module")
        _require_text(self.name, "OperationId.name")
        _require_text(self.revision, "OperationId.revision")


@dataclass(frozen=True, slots=True)
class EffectProfileId:
    operation: OperationId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        if not isinstance(self.operation, OperationId):
            raise TypeError("EffectProfileId.operation requires OperationId")
        _require_text(self.name, "EffectProfileId.name")
        _require_text(self.revision, "EffectProfileId.revision")


@dataclass(frozen=True, slots=True)
class MaterialId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "MaterialId.module")
        _require_text(self.name, "MaterialId.name")
        _require_text(self.revision, "MaterialId.revision")


@dataclass(frozen=True, slots=True)
class MaterialTypeId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "MaterialTypeId.module")
        _require_text(self.name, "MaterialTypeId.name")
        _require_text(self.revision, "MaterialTypeId.revision")


@dataclass(frozen=True, slots=True)
class SurfaceId:
    module: ModuleId
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_module(self.module, "SurfaceId.module")
        _require_text(self.name, "SurfaceId.name")
        _require_text(self.revision, "SurfaceId.revision")


@dataclass(frozen=True, slots=True)
class ComputationId:
    namespace: str
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_text(self.namespace, "ComputationId.namespace")
        _require_text(self.name, "ComputationId.name")
        _require_text(self.revision, "ComputationId.revision")
