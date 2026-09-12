"""Explicit JSON boundary codec for declarative Module definitions."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict

from madre_sdk.algebra import Autonomy, Privacy, Risk, Sensitivity
from madre_sdk.definitions import (
    AgentDefinition,
    DisplayName,
    EffectProfile,
    ModuleDefinition,
    OperationDefinition,
    Purpose,
    Repeatability,
    SkillDefinition,
    WorkflowDefinition,
)
from madre_sdk.identity import (
    AgentId,
    EffectProfileId,
    MaterialTypeId,
    ModuleId,
    OperationId,
    SkillId,
    SurfaceId,
    WorkflowId,
)
from madre_sdk.material import MaterialType
from madre_sdk.surfaces import (
    InputSurface,
    InputSurfaces,
    OutputSurface,
    OutputSurfaces,
)


class _WireModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class _ModuleIdDto(_WireModel):
    name: str
    revision: str


class _OwnedIdDto(_WireModel):
    module: _ModuleIdDto
    name: str
    revision: str


class _EffectProfileIdDto(_WireModel):
    operation: _OwnedIdDto
    name: str
    revision: str


class _MaterialTypeDto(_WireModel):
    identity: _OwnedIdDto
    media_type: str


class _InputSurfaceDto(_WireModel):
    identity: _OwnedIdDto
    material_type: _OwnedIdDto
    privacy: str


class _OutputSurfaceDto(_WireModel):
    identity: _OwnedIdDto
    material_type: _OwnedIdDto
    sensitivity: str


class _EffectProfileDto(_WireModel):
    identity: _EffectProfileIdDto
    risk: str
    autonomy: str


class _SkillDto(_WireModel):
    identity: _OwnedIdDto
    name: str
    purpose: str
    inputs: tuple[_InputSurfaceDto, ...] | None
    outputs: tuple[_OutputSurfaceDto, ...] | None


class _WorkflowDto(_WireModel):
    identity: _OwnedIdDto
    name: str
    purpose: str
    inputs: tuple[_InputSurfaceDto, ...]
    outputs: tuple[_OutputSurfaceDto, ...]
    skills: tuple[_OwnedIdDto, ...]
    operations: tuple[_OwnedIdDto, ...]


class _OperationDto(_WireModel):
    identity: _OwnedIdDto
    name: str
    purpose: str
    inputs: tuple[_InputSurfaceDto, ...]
    outputs: tuple[_OutputSurfaceDto, ...]
    effect_profiles: tuple[_EffectProfileDto, ...]
    repeatability: str | None


class _AgentDto(_WireModel):
    identity: _OwnedIdDto
    name: str
    purpose: str
    inputs: tuple[_InputSurfaceDto, ...] | None
    outputs: tuple[_OutputSurfaceDto, ...] | None
    skills: tuple[_OwnedIdDto, ...]
    workflows: tuple[_OwnedIdDto, ...]
    exposed_operations: tuple[_OwnedIdDto, ...]


class _ModuleDefinitionDto(_WireModel):
    schema_version: int
    identity: _ModuleIdDto
    name: str
    purpose: str
    material_types: tuple[_MaterialTypeDto, ...]
    agents: tuple[_AgentDto, ...]
    skills: tuple[_SkillDto, ...]
    workflows: tuple[_WorkflowDto, ...]
    operations: tuple[_OperationDto, ...]
    public_outputs: tuple[_OutputSurfaceDto, ...] | None


def _module_id_to_dto(identity: ModuleId) -> _ModuleIdDto:
    return _ModuleIdDto(name=identity.name, revision=identity.revision)


def _module_id_from_dto(dto: _ModuleIdDto) -> ModuleId:
    return ModuleId(name=dto.name, revision=dto.revision)


def _owned_id_to_dto(
    identity: AgentId | SkillId | WorkflowId | OperationId | MaterialTypeId | SurfaceId,
) -> _OwnedIdDto:
    return _OwnedIdDto(
        module=_module_id_to_dto(identity.module),
        name=identity.name,
        revision=identity.revision,
    )


def _agent_id(dto: _OwnedIdDto) -> AgentId:
    return AgentId(_module_id_from_dto(dto.module), dto.name, dto.revision)


def _skill_id(dto: _OwnedIdDto) -> SkillId:
    return SkillId(_module_id_from_dto(dto.module), dto.name, dto.revision)


def _workflow_id(dto: _OwnedIdDto) -> WorkflowId:
    return WorkflowId(_module_id_from_dto(dto.module), dto.name, dto.revision)


def _operation_id(dto: _OwnedIdDto) -> OperationId:
    return OperationId(_module_id_from_dto(dto.module), dto.name, dto.revision)


def _material_type_id(dto: _OwnedIdDto) -> MaterialTypeId:
    return MaterialTypeId(_module_id_from_dto(dto.module), dto.name, dto.revision)


def _surface_id(dto: _OwnedIdDto) -> SurfaceId:
    return SurfaceId(_module_id_from_dto(dto.module), dto.name, dto.revision)


def _input_surface_to_dto(surface: InputSurface) -> _InputSurfaceDto:
    return _InputSurfaceDto(
        identity=_owned_id_to_dto(surface.identity),
        material_type=_owned_id_to_dto(surface.material_type),
        privacy=surface.privacy.name,
    )


def _input_surface_from_dto(dto: _InputSurfaceDto) -> InputSurface:
    return InputSurface(
        identity=_surface_id(dto.identity),
        material_type=_material_type_id(dto.material_type),
        privacy=Privacy[dto.privacy],
    )


def _output_surface_to_dto(surface: OutputSurface) -> _OutputSurfaceDto:
    return _OutputSurfaceDto(
        identity=_owned_id_to_dto(surface.identity),
        material_type=_owned_id_to_dto(surface.material_type),
        sensitivity=surface.sensitivity.name,
    )


def _output_surface_from_dto(dto: _OutputSurfaceDto) -> OutputSurface:
    return OutputSurface(
        identity=_surface_id(dto.identity),
        material_type=_material_type_id(dto.material_type),
        sensitivity=Sensitivity[dto.sensitivity],
    )


def _inputs_to_dto(inputs: InputSurfaces) -> tuple[_InputSurfaceDto, ...]:
    return tuple(_input_surface_to_dto(surface) for surface in inputs.members)


def _inputs_from_dto(inputs: tuple[_InputSurfaceDto, ...]) -> InputSurfaces:
    return InputSurfaces(tuple(_input_surface_from_dto(surface) for surface in inputs))


def _outputs_to_dto(outputs: OutputSurfaces) -> tuple[_OutputSurfaceDto, ...]:
    return tuple(_output_surface_to_dto(surface) for surface in outputs.members)


def _outputs_from_dto(outputs: tuple[_OutputSurfaceDto, ...]) -> OutputSurfaces:
    return OutputSurfaces(tuple(_output_surface_from_dto(surface) for surface in outputs))


def _profile_to_dto(profile: EffectProfile) -> _EffectProfileDto:
    identity = profile.identity
    return _EffectProfileDto(
        identity=_EffectProfileIdDto(
            operation=_owned_id_to_dto(identity.operation),
            name=identity.name,
            revision=identity.revision,
        ),
        risk=profile.risk.name,
        autonomy=profile.autonomy.name,
    )


def _profile_from_dto(dto: _EffectProfileDto) -> EffectProfile:
    operation = _operation_id(dto.identity.operation)
    return EffectProfile(
        identity=EffectProfileId(operation, dto.identity.name, dto.identity.revision),
        risk=Risk[dto.risk],
        autonomy=Autonomy[dto.autonomy],
    )


def _skill_to_dto(skill: SkillDefinition) -> _SkillDto:
    return _SkillDto(
        identity=_owned_id_to_dto(skill.identity),
        name=skill.name.value,
        purpose=skill.purpose.value,
        inputs=None if skill.inputs is None else _inputs_to_dto(skill.inputs),
        outputs=None if skill.outputs is None else _outputs_to_dto(skill.outputs),
    )


def _skill_from_dto(dto: _SkillDto) -> SkillDefinition:
    return SkillDefinition(
        identity=_skill_id(dto.identity),
        name=DisplayName(dto.name),
        purpose=Purpose(dto.purpose),
        inputs=None if dto.inputs is None else _inputs_from_dto(dto.inputs),
        outputs=None if dto.outputs is None else _outputs_from_dto(dto.outputs),
    )


def _workflow_to_dto(workflow: WorkflowDefinition) -> _WorkflowDto:
    return _WorkflowDto(
        identity=_owned_id_to_dto(workflow.identity),
        name=workflow.name.value,
        purpose=workflow.purpose.value,
        inputs=_inputs_to_dto(workflow.inputs),
        outputs=_outputs_to_dto(workflow.outputs),
        skills=tuple(_owned_id_to_dto(identity) for identity in workflow.skills),
        operations=tuple(_owned_id_to_dto(identity) for identity in workflow.operations),
    )


def _workflow_from_dto(dto: _WorkflowDto) -> WorkflowDefinition:
    return WorkflowDefinition(
        identity=_workflow_id(dto.identity),
        name=DisplayName(dto.name),
        purpose=Purpose(dto.purpose),
        inputs=_inputs_from_dto(dto.inputs),
        outputs=_outputs_from_dto(dto.outputs),
        skills=tuple(_skill_id(identity) for identity in dto.skills),
        operations=tuple(_operation_id(identity) for identity in dto.operations),
    )


def _operation_to_dto(operation: OperationDefinition) -> _OperationDto:
    return _OperationDto(
        identity=_owned_id_to_dto(operation.identity),
        name=operation.name.value,
        purpose=operation.purpose.value,
        inputs=_inputs_to_dto(operation.inputs),
        outputs=_outputs_to_dto(operation.outputs),
        effect_profiles=tuple(_profile_to_dto(profile) for profile in operation.effect_profiles),
        repeatability=None if operation.repeatability is None else operation.repeatability.value,
    )


def _operation_from_dto(dto: _OperationDto) -> OperationDefinition:
    return OperationDefinition(
        identity=_operation_id(dto.identity),
        name=DisplayName(dto.name),
        purpose=Purpose(dto.purpose),
        inputs=_inputs_from_dto(dto.inputs),
        outputs=_outputs_from_dto(dto.outputs),
        effect_profiles=tuple(_profile_from_dto(profile) for profile in dto.effect_profiles),
        repeatability=None if dto.repeatability is None else Repeatability(dto.repeatability),
    )


def _agent_to_dto(agent: AgentDefinition) -> _AgentDto:
    return _AgentDto(
        identity=_owned_id_to_dto(agent.identity),
        name=agent.name.value,
        purpose=agent.purpose.value,
        inputs=None if agent.inputs is None else _inputs_to_dto(agent.inputs),
        outputs=None if agent.outputs is None else _outputs_to_dto(agent.outputs),
        skills=tuple(_owned_id_to_dto(identity) for identity in agent.skills),
        workflows=tuple(_owned_id_to_dto(identity) for identity in agent.workflows),
        exposed_operations=tuple(
            _owned_id_to_dto(identity) for identity in agent.exposed_operations
        ),
    )


def _agent_from_dto(dto: _AgentDto) -> AgentDefinition:
    return AgentDefinition(
        identity=_agent_id(dto.identity),
        name=DisplayName(dto.name),
        purpose=Purpose(dto.purpose),
        inputs=None if dto.inputs is None else _inputs_from_dto(dto.inputs),
        outputs=None if dto.outputs is None else _outputs_from_dto(dto.outputs),
        skills=tuple(_skill_id(identity) for identity in dto.skills),
        workflows=tuple(_workflow_id(identity) for identity in dto.workflows),
        exposed_operations=tuple(_operation_id(identity) for identity in dto.exposed_operations),
    )


class ModuleDefinitionJsonCodec:
    """Versioned JSON representation of one immutable ModuleDefinition graph."""

    VERSION = 1

    def encode(self, definition: ModuleDefinition) -> str:
        dto = _ModuleDefinitionDto(
            schema_version=self.VERSION,
            identity=_module_id_to_dto(definition.identity),
            name=definition.name.value,
            purpose=definition.purpose.value,
            material_types=tuple(
                _MaterialTypeDto(
                    identity=_owned_id_to_dto(material_type.identity),
                    media_type=material_type.media_type,
                )
                for material_type in definition.material_types
            ),
            agents=tuple(_agent_to_dto(agent) for agent in definition.agents),
            skills=tuple(_skill_to_dto(skill) for skill in definition.skills),
            workflows=tuple(_workflow_to_dto(workflow) for workflow in definition.workflows),
            operations=tuple(_operation_to_dto(operation) for operation in definition.operations),
            public_outputs=(
                None
                if definition.public_outputs is None
                else _outputs_to_dto(definition.public_outputs)
            ),
        )
        return dto.model_dump_json()

    def decode(self, encoded: str) -> ModuleDefinition:
        dto = _ModuleDefinitionDto.model_validate_json(encoded)
        if dto.schema_version != self.VERSION:
            raise ValueError(f"Unsupported ModuleDefinition schema: {dto.schema_version}")
        return ModuleDefinition(
            identity=_module_id_from_dto(dto.identity),
            name=DisplayName(dto.name),
            purpose=Purpose(dto.purpose),
            material_types=tuple(
                MaterialType[object](
                    identity=_material_type_id(material_type.identity),
                    media_type=material_type.media_type,
                )
                for material_type in dto.material_types
            ),
            agents=tuple(_agent_from_dto(agent) for agent in dto.agents),
            skills=tuple(_skill_from_dto(skill) for skill in dto.skills),
            workflows=tuple(_workflow_from_dto(workflow) for workflow in dto.workflows),
            operations=tuple(_operation_from_dto(operation) for operation in dto.operations),
            public_outputs=(
                None if dto.public_outputs is None else _outputs_from_dto(dto.public_outputs)
            ),
        )
