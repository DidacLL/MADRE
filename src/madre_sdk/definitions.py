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
    if not isinstance(value, str):
        raise TypeError(f"{field} requires str")
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

    def __post_init__(self) -> None:
        if not isinstance(self.identity, EffectProfileId):
            raise TypeError("EffectProfile.identity requires EffectProfileId")
        if not isinstance(self.risk, Risk):
            raise TypeError("EffectProfile.risk requires Risk")
        if not isinstance(self.autonomy, Autonomy):
            raise TypeError("EffectProfile.autonomy requires Autonomy")

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

    def __post_init__(self) -> None:
        if not isinstance(self.identity, SkillId):
            raise TypeError("SkillDefinition.identity requires SkillId")
        if not isinstance(self.name, DisplayName):
            raise TypeError("SkillDefinition.name requires DisplayName")
        if not isinstance(self.purpose, Purpose):
            raise TypeError("SkillDefinition.purpose requires Purpose")
        if self.inputs is not None and not isinstance(self.inputs, InputSurfaces):
            raise TypeError("SkillDefinition.inputs requires InputSurfaces")
        if self.outputs is not None and not isinstance(self.outputs, OutputSurfaces):
            raise TypeError("SkillDefinition.outputs requires OutputSurfaces")


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
        if not isinstance(self.identity, WorkflowId):
            raise TypeError("WorkflowDefinition.identity requires WorkflowId")
        if not isinstance(self.name, DisplayName):
            raise TypeError("WorkflowDefinition.name requires DisplayName")
        if not isinstance(self.purpose, Purpose):
            raise TypeError("WorkflowDefinition.purpose requires Purpose")
        if not isinstance(self.inputs, InputSurfaces):
            raise TypeError("WorkflowDefinition.inputs requires InputSurfaces")
        if not isinstance(self.outputs, OutputSurfaces):
            raise TypeError("WorkflowDefinition.outputs requires OutputSurfaces")
        if not isinstance(self.skills, tuple) or not isinstance(self.operations, tuple):
            raise TypeError("WorkflowDefinition references require tuples")
        if any(not isinstance(identity, SkillId) for identity in self.skills):
            raise TypeError("WorkflowDefinition.skills accepts only SkillId")
        if any(not isinstance(identity, OperationId) for identity in self.operations):
            raise TypeError("WorkflowDefinition.operations accepts only OperationId")
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
        if not isinstance(self.identity, OperationId):
            raise TypeError("OperationDefinition.identity requires OperationId")
        if not isinstance(self.name, DisplayName):
            raise TypeError("OperationDefinition.name requires DisplayName")
        if not isinstance(self.purpose, Purpose):
            raise TypeError("OperationDefinition.purpose requires Purpose")
        if not isinstance(self.inputs, InputSurfaces):
            raise TypeError("OperationDefinition.inputs requires InputSurfaces")
        if not isinstance(self.outputs, OutputSurfaces):
            raise TypeError("OperationDefinition.outputs requires OutputSurfaces")
        if not isinstance(self.effect_profiles, tuple):
            raise TypeError("OperationDefinition.effect_profiles requires tuple")
        if not self.effect_profiles:
            raise ValueError("OperationDefinition requires at least one EffectProfile")
        if any(not isinstance(profile, EffectProfile) for profile in self.effect_profiles):
            raise TypeError("OperationDefinition.effect_profiles accepts only EffectProfile")
        if self.repeatability is not None and not isinstance(self.repeatability, Repeatability):
            raise TypeError("OperationDefinition.repeatability requires Repeatability")
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
        if not isinstance(self.identity, AgentId):
            raise TypeError("AgentDefinition.identity requires AgentId")
        if not isinstance(self.name, DisplayName):
            raise TypeError("AgentDefinition.name requires DisplayName")
        if not isinstance(self.purpose, Purpose):
            raise TypeError("AgentDefinition.purpose requires Purpose")
        if self.inputs is not None and not isinstance(self.inputs, InputSurfaces):
            raise TypeError("AgentDefinition.inputs requires InputSurfaces")
        if self.outputs is not None and not isinstance(self.outputs, OutputSurfaces):
            raise TypeError("AgentDefinition.outputs requires OutputSurfaces")
        if not all(
            isinstance(references, tuple)
            for references in (self.skills, self.workflows, self.exposed_operations)
        ):
            raise TypeError("AgentDefinition references require tuples")
        if any(not isinstance(identity, SkillId) for identity in self.skills):
            raise TypeError("AgentDefinition.skills accepts only SkillId")
        if any(not isinstance(identity, WorkflowId) for identity in self.workflows):
            raise TypeError("AgentDefinition.workflows accepts only WorkflowId")
        if any(not isinstance(identity, OperationId) for identity in self.exposed_operations):
            raise TypeError("AgentDefinition.exposed_operations accepts only OperationId")
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
    public_operations: tuple[OperationId, ...] = ()
    public_outputs: OutputSurfaces | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.identity, ModuleId):
            raise TypeError("ModuleDefinition.identity requires ModuleId")
        if not isinstance(self.name, DisplayName):
            raise TypeError("ModuleDefinition.name requires DisplayName")
        if not isinstance(self.purpose, Purpose):
            raise TypeError("ModuleDefinition.purpose requires Purpose")
        collections = (
            self.material_types,
            self.agents,
            self.skills,
            self.workflows,
            self.operations,
            self.public_operations,
        )
        if not all(isinstance(collection, tuple) for collection in collections):
            raise TypeError("ModuleDefinition collections require tuples")
        if any(not isinstance(item, MaterialType) for item in self.material_types):
            raise TypeError("ModuleDefinition.material_types accepts only MaterialType")
        if any(not isinstance(item, AgentDefinition) for item in self.agents):
            raise TypeError("ModuleDefinition.agents accepts only AgentDefinition")
        if any(not isinstance(item, SkillDefinition) for item in self.skills):
            raise TypeError("ModuleDefinition.skills accepts only SkillDefinition")
        if any(not isinstance(item, WorkflowDefinition) for item in self.workflows):
            raise TypeError("ModuleDefinition.workflows accepts only WorkflowDefinition")
        if any(not isinstance(item, OperationDefinition) for item in self.operations):
            raise TypeError("ModuleDefinition.operations accepts only OperationDefinition")
        if any(not isinstance(item, OperationId) for item in self.public_operations):
            raise TypeError("ModuleDefinition.public_operations accepts only OperationId")
        if self.public_outputs is not None and not isinstance(self.public_outputs, OutputSurfaces):
            raise TypeError("ModuleDefinition.public_outputs requires OutputSurfaces")
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
        _require_unique(self.public_operations, "Module public Operation identities")
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
        if not set(self.public_operations).issubset(operation_ids):
            raise ValueError("Module publishes an unknown Operation")
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
        input_surfaces: list[InputSurface] = []
        output_surfaces: list[OutputSurface] = []
        for agent in self.agents:
            if agent.inputs is not None:
                input_surfaces.extend(agent.inputs.members)
            if agent.outputs is not None:
                output_surfaces.extend(agent.outputs.members)
        for skill in self.skills:
            if skill.inputs is not None:
                input_surfaces.extend(skill.inputs.members)
            if skill.outputs is not None:
                output_surfaces.extend(skill.outputs.members)
        for workflow in self.workflows:
            input_surfaces.extend(workflow.inputs.members)
            output_surfaces.extend(workflow.outputs.members)
        for operation in self.operations:
            input_surfaces.extend(operation.inputs.members)
            output_surfaces.extend(operation.outputs.members)
        if self.public_outputs is not None:
            output_surfaces.extend(self.public_outputs.members)
        _require_unique(
            tuple(surface.identity for surface in input_surfaces),
            "Module input-surface identities",
        )
        _require_unique(
            tuple(surface.identity for surface in output_surfaces),
            "Module output-surface identities",
        )
        surfaces: tuple[InputSurface | OutputSurface, ...] = (
            *input_surfaces,
            *output_surfaces,
        )
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
        for agent in self.agents:
            if agent.outputs is not None:
                values.append(agent.outputs.sensitivity)
        for skill in self.skills:
            if skill.outputs is not None:
                values.append(skill.outputs.sensitivity)
        values.extend(workflow.outputs.sensitivity for workflow in self.workflows)
        values.extend(operation.outputs.sensitivity for operation in self.operations)
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
class ModuleDirectoryEntry:
    identity: ModuleId
    name: DisplayName
    purpose: Purpose
    agents: tuple[AgentDefinition, ...]
    operations: tuple[OperationDefinition, ...]

    def __post_init__(self) -> None:
        if not isinstance(self.identity, ModuleId):
            raise TypeError("ModuleDirectoryEntry.identity requires ModuleId")
        if not isinstance(self.name, DisplayName):
            raise TypeError("ModuleDirectoryEntry.name requires DisplayName")
        if not isinstance(self.purpose, Purpose):
            raise TypeError("ModuleDirectoryEntry.purpose requires Purpose")
        if not isinstance(self.agents, tuple) or not isinstance(self.operations, tuple):
            raise TypeError("ModuleDirectoryEntry definitions require tuples")
        if any(not isinstance(agent, AgentDefinition) for agent in self.agents):
            raise TypeError("ModuleDirectoryEntry.agents accepts only AgentDefinition")
        if any(not isinstance(operation, OperationDefinition) for operation in self.operations):
            raise TypeError("ModuleDirectoryEntry.operations accepts only OperationDefinition")
        if not self.agents and not self.operations:
            raise ValueError("ModuleDirectoryEntry requires a reachable public definition")
        if any(agent.identity.module != self.identity for agent in self.agents):
            raise ValueError("Directory Agent must belong to its Module")
        if any(operation.identity.module != self.identity for operation in self.operations):
            raise ValueError("Directory Operation must belong to its Module")


@dataclass(frozen=True, slots=True)
class OperationCall:
    operation: OperationDefinition
    profile: EffectProfile
    materials: MaterialSet
    causal_participants: ResponsibilitySurfaces | None
    physical_realizers: ResponsibilitySurfaces

    def __post_init__(self) -> None:
        if not isinstance(self.operation, OperationDefinition):
            raise TypeError("OperationCall.operation requires OperationDefinition")
        if not isinstance(self.profile, EffectProfile):
            raise TypeError("OperationCall.profile requires EffectProfile")
        if not isinstance(self.materials, MaterialSet):
            raise TypeError("OperationCall.materials requires MaterialSet")
        if self.causal_participants is not None and not isinstance(
            self.causal_participants, ResponsibilitySurfaces
        ):
            raise TypeError("OperationCall.causal_participants requires ResponsibilitySurfaces")
        if not isinstance(self.physical_realizers, ResponsibilitySurfaces):
            raise TypeError("OperationCall.physical_realizers requires ResponsibilitySurfaces")
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
