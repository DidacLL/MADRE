"""Generic Module integration seam for MADRE Kernel orchestration."""

from __future__ import annotations

from collections.abc import Callable, Mapping, Sequence
from dataclasses import dataclass
from typing import Protocol, TypeVar

from pydantic import BaseModel

from madre_kernel.contracts import (
    AgentDefinition,
    AgentDefinitionRef,
    AgentInstance,
    AgentSkillInstance,
    AgentSkillInstanceRef,
    ContextBundle,
    ContextBundleRef,
    DataSecurityFacts,
    DiscoveryPolicyRef,
    ModuleManifest,
    OperationDescriptor,
    OperationRef,
    SchemaRef,
    SkillDefinition,
    SkillRef,
    TypedPayload,
    WorkflowDefinition,
    WorkflowRef,
)

T = TypeVar("T", bound=BaseModel)
PUBLIC_DISCOVERY = DiscoveryPolicyRef(policy_id="public", revision=1)


def ref_key(value: BaseModel) -> str:
    return value.model_dump_json()


class SchemaCodecRegistry:
    def __init__(self) -> None:
        self._models: dict[str, type[BaseModel]] = {}

    def register(self, schema_ref: SchemaRef, model: type[BaseModel]) -> None:
        self._models[ref_key(schema_ref)] = model

    def encode(self, schema_ref: SchemaRef, value: BaseModel) -> TypedPayload:
        model = self._models.get(ref_key(schema_ref))
        if model is None or not isinstance(value, model):
            raise ValueError("payload does not match the exact registered SchemaRef")
        return TypedPayload(schema_ref=schema_ref, canonical_json=value.model_dump_json())

    def validate(self, payload: TypedPayload) -> None:
        model = self._models.get(ref_key(payload.schema_ref))
        if model is None:
            raise ValueError("unknown SchemaRef")
        model.model_validate_json(payload.canonical_json)

    def decode(self, payload: TypedPayload, model: type[T]) -> T:
        registered = self._models.get(ref_key(payload.schema_ref))
        if registered is not model:
            raise ValueError("requested payload type does not match exact SchemaRef")
        return model.model_validate_json(payload.canonical_json)


@dataclass(frozen=True)
class OperationMaterial:
    schema_ref: SchemaRef
    payload: BaseModel
    security: DataSecurityFacts
    purpose: str


class UnknownOperationEffect(RuntimeError):
    pass


class AgentExecutionServices(Protocol):
    async def reasoning(
        self,
        messages: Sequence[tuple[str, str]],
        material: Sequence[ContextBundleRef],
    ) -> str: ...

    def visible_operations(self) -> tuple[OperationDescriptor, ...]: ...

    def project_operation_input(
        self,
        operation: OperationRef,
        payload: BaseModel,
    ) -> ContextBundleRef: ...

    async def invoke_operation(
        self,
        operation: OperationRef,
        input_contexts: Sequence[ContextBundleRef],
    ) -> tuple[ContextBundleRef, ...]: ...

    def context(self, ref: ContextBundleRef) -> ContextBundle: ...

    def emit_agent_context(
        self,
        schema_ref: SchemaRef,
        payload: BaseModel,
        security: DataSecurityFacts,
        purpose: str,
        derived_from: Sequence[ContextBundleRef],
    ) -> ContextBundleRef: ...


class AgentManager(Protocol):
    def instantiate(self, definition: AgentDefinition) -> AgentInstance: ...

    async def run_task(
        self,
        instance: AgentInstance,
        objective: ContextBundle,
        services: AgentExecutionServices,
    ) -> ContextBundleRef: ...


OperationHandler = Callable[[tuple[ContextBundle, ...]], OperationMaterial]
InputProjector = Callable[[BaseModel], OperationMaterial]


class ModuleAdapter(Protocol):
    @property
    def manifest(self) -> ModuleManifest: ...

    def schemas(self) -> Mapping[SchemaRef, type[BaseModel]]: ...

    def operation(self, ref: OperationRef) -> OperationDescriptor | None: ...

    def skill(self, ref: SkillRef) -> SkillDefinition | None: ...

    def workflow(self, ref: WorkflowRef) -> WorkflowDefinition | None: ...

    def agent_definition(self, ref: AgentDefinitionRef) -> AgentDefinition | None: ...

    def agent_skill_instance(
        self,
        ref: AgentSkillInstanceRef,
    ) -> AgentSkillInstance | None: ...

    def agent_manager(self, ref: AgentDefinitionRef) -> AgentManager | None: ...

    def project_operation_input(
        self,
        ref: OperationRef,
        payload: BaseModel,
    ) -> OperationMaterial: ...

    async def invoke_operation(
        self,
        ref: OperationRef,
        inputs: tuple[ContextBundle, ...],
    ) -> OperationMaterial: ...


class InProcessModule:
    def __init__(
        self,
        *,
        manifest: ModuleManifest,
        schemas: Mapping[SchemaRef, type[BaseModel]],
        operations: Sequence[OperationDescriptor] = (),
        skills: Sequence[SkillDefinition] = (),
        workflows: Sequence[WorkflowDefinition] = (),
        agents: Sequence[AgentDefinition] = (),
        skill_instances: Sequence[AgentSkillInstance] = (),
        manager: AgentManager | None = None,
        input_projectors: Mapping[str, InputProjector] | None = None,
        handlers: Mapping[str, OperationHandler] | None = None,
    ) -> None:
        self._manifest = manifest
        self._schemas = dict(schemas)
        self._operations = {ref_key(item.ref): item for item in operations}
        self._skills = {ref_key(item.ref): item for item in skills}
        self._workflows = {ref_key(item.ref): item for item in workflows}
        self._agents = {ref_key(item.ref): item for item in agents}
        self._skill_instances = {ref_key(item.ref): item for item in skill_instances}
        self._manager = manager
        self._input_projectors = dict(input_projectors or {})
        self._handlers = dict(handlers or {})

    @property
    def manifest(self) -> ModuleManifest:
        return self._manifest

    def schemas(self) -> Mapping[SchemaRef, type[BaseModel]]:
        return self._schemas

    def operation(self, ref: OperationRef) -> OperationDescriptor | None:
        return self._operations.get(ref_key(ref))

    def skill(self, ref: SkillRef) -> SkillDefinition | None:
        return self._skills.get(ref_key(ref))

    def workflow(self, ref: WorkflowRef) -> WorkflowDefinition | None:
        return self._workflows.get(ref_key(ref))

    def agent_definition(self, ref: AgentDefinitionRef) -> AgentDefinition | None:
        return self._agents.get(ref_key(ref))

    def agent_skill_instance(
        self,
        ref: AgentSkillInstanceRef,
    ) -> AgentSkillInstance | None:
        return self._skill_instances.get(ref_key(ref))

    def agent_manager(self, ref: AgentDefinitionRef) -> AgentManager | None:
        return self._manager if ref_key(ref) in self._agents else None

    def project_operation_input(
        self,
        ref: OperationRef,
        payload: BaseModel,
    ) -> OperationMaterial:
        projector = self._input_projectors.get(ref_key(ref))
        if projector is None:
            raise KeyError("Module does not expose an input projector for Operation")
        return projector(payload)

    async def invoke_operation(
        self,
        ref: OperationRef,
        inputs: tuple[ContextBundle, ...],
    ) -> OperationMaterial:
        handler = self._handlers.get(ref_key(ref))
        if handler is None:
            raise KeyError("Module does not implement Operation")
        return handler(inputs)
