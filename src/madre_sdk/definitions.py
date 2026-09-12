"""Declarative MADRE building blocks with constructor-owned invariants."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from madre_sdk.algebra import Autonomy, Integrity, Privacy, Risk, Sensitivity
from madre_sdk.identity import (
    AgentId,
    EffectProfileId,
    ModuleId,
    OperationId,
    SkillId,
    WorkflowId,
)
from madre_sdk.material import MaterialSet, MaterialType
from madre_sdk.surfaces import (
    InputSurface,
    InputSurfaces,
    OutputSurface,
    OutputSurfaces,
    ResponsibilitySurfaces,
)


def _require_text(value: str, field: str) -> None:
    if not value or value.isspace():
        raise ValueError(f"{field} must not be blank")


def _require_unique[IdentityT](identities: tuple[IdentityT, ...], field: str) -> None:
    if len(identities) != len(set(identities)):
        raise ValueError(f"{field} must be unique")


@dataclass(frozen=True, slots=True)
class DisplayName:
    value: str

    def __post_init__(self) -> None:
        _require_text(self.value, "DisplayName.value")


@dataclass(frozen=True, slots=True)
class Purpose:
    value: str

    def __post_init__(self) -> None:
        _require_text(self.value, "Purpose.value")


class Repeatability(Enum):
    REPEATABLE = "repeatable"
    IDEMPOTENT = "idempotent"
    SINGLE_USE = "single_use"


@dataclass(frozen=True, slots=True)
class EffectProfile:
    identity: EffectProfileId
    risk: Risk
    autonomy: Autonomy

    @property
    def operation(self) -> OperationId:
        return self.identity.operation

    @property
    def causal_demand(self) -> int:
        return min(self.risk.rank, self.autonomy.rank)


@dataclass(frozen=True, slots=True)
class SkillDefinition:
    identity: SkillId
    name: DisplayName
    purpose: Purpose
    inputs: InputSurfaces | None = None
    outputs: OutputSurfaces | None = None


@dataclass(frozen=True, slots=True)
class WorkflowDefinition:
    identity: WorkflowId
    name: DisplayName
    purpose: Purpose
    inputs: InputSurfaces
    outputs: OutputSurfaces
    skills: tuple[SkillId, ...] = ()
    operations: tuple[OperationId, ...] = ()

    def __post_init__(self) -> None:
        _require_unique(self.skills, "Workflow skills")
        _require_unique(self.operations, "Workflow operations")


@dataclass(frozen=True, slots=True)
class OperationDefinition:
    identity: OperationId
    name: DisplayName
    purpose: Purpose
    inputs: InputSurfaces
    outputs: OutputSurfaces
    effect_profiles: tuple[EffectProfile, ...]
    repeatability: Repeatability | None = None

    def __post_init__(self) -> None:
        if not self.effect_profiles:
            raise ValueError("OperationDefinition requires at least one EffectProfile")
        profile_ids = tuple(profile.identity for profile in self.effect_profiles)
        _require_unique(profile_ids, "Operation EffectProfile identities")
        if any(profile.operation != self.identity for profile in self.effect_profiles):
            raise ValueError("Every EffectProfile must belong to its Operation")

    def profile(self, identity: EffectProfileId) -> EffectProfile:
        for profile in self.effect_profiles:
            if profile.identity == identity:
                return profile
        raise ValueError("EffectProfile does not belong to this Operation")


@dataclass(frozen=True, slots=True)
class AgentDefinition:
    identity: AgentId
    name: DisplayName
    purpose: Purpose
    inputs: InputSurfaces | None = None
    outputs: OutputSurfaces | None = None
    skills: tuple[SkillId, ...] = ()
    workflows: tuple[WorkflowId, ...] = ()
    exposed_operations: tuple[OperationId, ...] = ()

    def __post_init__(self) -> None:
        _require_unique(self.skills, "Agent skills")
        _require_unique(self.workflows, "Agent workflows")
        _require_unique(self.exposed_operations, "Agent exposed Operations")


@dataclass(frozen=True, slots=True)
class ModuleDefinition:
    identity: ModuleId
    name: DisplayName
    purpose: Purpose
    material_types: tuple[MaterialType[object], ...]
    agents: tuple[AgentDefinition, ...] = ()
    skills: tuple[SkillDefinition, ...] = ()
    workflows: tuple[WorkflowDefinition, ...] = ()
    operations: tuple[OperationDefinition, ...] = ()
    public_outputs: OutputSurfaces | None = None

    def __post_init__(self) -> None:
        _require_unique(
            tuple(material_type.identity for material_type in self.material_types),
            "Module MaterialType identities",
        )
        _require_unique(tuple(agent.identity for agent in self.agents), "Module Agent identities")
        _require_unique(tuple(skill.identity for skill in self.skills), "Module Skill identities")
        _require_unique(
            tuple(workflow.identity for workflow in self.workflows),
            "Module Workflow identities",
        )
        _require_unique(
            tuple(operation.identity for operation in self.operations),
            "Module Operation identities",
        )
        self._require_ownership()
        self._require_references()
        self._require_surfaces()

    def _require_ownership(self) -> None:
        ownership = (
            all(agent.identity.module == self.identity for agent in self.agents),
            all(skill.identity.module == self.identity for skill in self.skills),
            all(workflow.identity.module == self.identity for workflow in self.workflows),
            all(operation.identity.module == self.identity for operation in self.operations),
        )
        if not all(ownership):
            raise ValueError("Every Module child definition must belong to its Module")
        if any(
            material_type.identity.module != self.identity for material_type in self.material_types
        ):
            raise ValueError("Every canonical MaterialType must belong to its Module")

    def _require_references(self) -> None:
        skill_ids = {skill.identity for skill in self.skills}
        workflow_ids = {workflow.identity for workflow in self.workflows}
        operation_ids = {operation.identity for operation in self.operations}
        for workflow in self.workflows:
            if not set(workflow.skills).issubset(skill_ids):
                raise ValueError("Workflow references an unknown Skill")
            if not set(workflow.operations).issubset(operation_ids):
                raise ValueError("Workflow references an unknown Operation")
        for agent in self.agents:
            if not set(agent.skills).issubset(skill_ids):
                raise ValueError("Agent references an unknown Skill")
            if not set(agent.workflows).issubset(workflow_ids):
                raise ValueError("Agent references an unknown Workflow")
            if not set(agent.exposed_operations).issubset(operation_ids):
                raise ValueError("Agent references an unknown Operation")

    def _require_surfaces(self) -> None:
        material_type_ids = {material_type.identity for material_type in self.material_types}
        surfaces: list[InputSurface | OutputSurface] = []
        for agent in self.agents:
            if agent.inputs is not None:
                surfaces.extend(agent.inputs.members)
            if agent.outputs is not None:
                surfaces.extend(agent.outputs.members)
        for skill in self.skills:
            if skill.inputs is not None:
                surfaces.extend(skill.inputs.members)
            if skill.outputs is not None:
                surfaces.extend(skill.outputs.members)
        for workflow in self.workflows:
            surfaces.extend(workflow.inputs.members)
            surfaces.extend(workflow.outputs.members)
        for operation in self.operations:
            surfaces.extend(operation.inputs.members)
            surfaces.extend(operation.outputs.members)
        if self.public_outputs is not None:
            surfaces.extend(self.public_outputs.members)
        if any(surface.identity.module != self.identity for surface in surfaces):
            raise ValueError("Every public surface must belong to its Module")
        if any(surface.material_type not in material_type_ids for surface in surfaces):
            raise ValueError("Every public surface must use a canonical Module MaterialType")

    def operation(self, identity: OperationId) -> OperationDefinition:
        for operation in self.operations:
            if operation.identity == identity:
                return operation
        raise ValueError("Unknown Operation")

    def sensitivity_of(self, materials: MaterialSet) -> Sensitivity:
        values = [materials.sensitivity]
        if self.public_outputs is not None:
            values.append(self.public_outputs.sensitivity)
        first, *rest = values
        return Sensitivity.maximum(first, *rest)

    def agent_privacy(self, identity: AgentId) -> Privacy:
        agent = next(
            (candidate for candidate in self.agents if candidate.identity == identity), None
        )
        if agent is None:
            raise ValueError("Unknown Agent")
        surfaces: list[InputSurface] = []
        if agent.inputs is not None:
            surfaces.extend(agent.inputs.members)
        for operation_id in agent.exposed_operations:
            surfaces.extend(self.operation(operation_id).inputs.members)
        if not surfaces:
            raise ValueError("Agent exposes no Privacy-bearing input surface")
        first, *rest = (surface.privacy for surface in surfaces)
        return Privacy.minimum(first, *rest)


@dataclass(frozen=True, slots=True)
class OperationCall:
    operation: OperationDefinition
    profile: EffectProfile
    materials: MaterialSet
    causal_participants: ResponsibilitySurfaces | None
    physical_realizers: ResponsibilitySurfaces

    def __post_init__(self) -> None:
        if self.profile not in self.operation.effect_profiles:
            raise ValueError("OperationCall profile must belong to its Operation")
        self.operation.inputs.compose(self.materials)
        causal_integrity = (
            Integrity.I5 if self.causal_participants is None else self.causal_participants.integrity
        )
        if self.profile.causal_demand > causal_integrity.rank:
            raise ValueError("Causal Integrity does not support this EffectProfile")
        if self.profile.risk.rank > self.physical_realizers.integrity.rank:
            raise ValueError("Physical Integrity does not support this EffectProfile Risk")
