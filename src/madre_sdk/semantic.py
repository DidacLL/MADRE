"""Small semantic contracts shared by independent MADRE Modules."""

from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from pydantic import Field, JsonValue

from madre.contracts import TransientMaterial, WorkSubmission
from madre.interfaces import (
    AgentEndpoint,
    AgentEndpointRegistration,
    MaterialResolutionRegistration,
    ModuleRegistration,
    OperationEndpoint,
    OperationEndpointRegistration,
)
from madre.registry import (
    AgentDescriptor,
    ModuleManifest,
    OperationDescriptor,
    SkillDescriptor,
    WorkflowDescriptor,
)
from madre.security import (
    ActorSecurityValues,
    ExecutionBoundary,
    FrozenModel,
    Identifier,
    SecurityObject,
)
from madre_sdk.material import Material, MaterialRepository


class Skill(FrozenModel):
    id: Identifier
    purpose: str = Field(min_length=1)
    version: Identifier = "1"
    instructions: tuple[str, ...] = ()
    resources: tuple[Identifier, ...] = ()
    input_contract: Identifier | None = None
    output_contract: Identifier | None = None
    related_operations: tuple[Identifier, ...] = ()
    related_workflows: tuple[Identifier, ...] = ()
    compatibility: tuple[Identifier, ...] = ()
    provenance: tuple[Identifier, ...] = ()

    def descriptor(self, module_id: str) -> SkillDescriptor:
        return SkillDescriptor(
            id=self.id,
            module_id=module_id,
            purpose=self.purpose,
            version=self.version,
            instructions=self.instructions,
            resources=self.resources,
            input_contract=self.input_contract,
            output_contract=self.output_contract,
            related_operations=self.related_operations,
            related_workflows=self.related_workflows,
            compatibility=self.compatibility,
            provenance=self.provenance,
        )


class Workflow(FrozenModel):
    id: Identifier
    purpose: str = Field(min_length=1)
    version: Identifier = "1"
    instructions: tuple[str, ...] = ()
    input_contract: Identifier = "json:any"
    output_contract: Identifier = "json:any"
    provenance: tuple[Identifier, ...] = ()

    def descriptor(self, module_id: str) -> WorkflowDescriptor:
        return WorkflowDescriptor(
            id=self.id,
            module_id=module_id,
            purpose=self.purpose,
            version=self.version,
            instructions=self.instructions,
            input_contract=self.input_contract,
            output_contract=self.output_contract,
            provenance=self.provenance,
        )


class WorkPlan(Protocol):
    """Module-owned semantic planning state that can project ordinary Kernel work."""

    @property
    def id(self) -> str: ...

    def project_work(self) -> tuple[WorkSubmission, ...]: ...


class AgentBehavior(Protocol):
    async def execute(
        self,
        *,
        agent_id: str,
        instructions: tuple[str, ...],
        payload: JsonValue,
    ) -> Material: ...


class OperationBehavior(Protocol):
    async def execute(self, *, operation_id: str, payload: JsonValue) -> Material: ...


class Agent:
    """Minimal Module-owned Agent: public identity/behavior with optional portable semantics."""

    __slots__ = (
        "id",
        "purpose",
        "instructions",
        "input_contract",
        "output_contract",
        "security",
        "skills",
        "workflows",
        "operation_ids",
        "provenance",
        "_behavior",
    )

    def __init__(
        self,
        *,
        agent_id: str,
        purpose: str,
        instructions: Sequence[str],
        security: SecurityObject,
        behavior: AgentBehavior,
        input_contract: str = "json:any",
        output_contract: str = "json:any",
        skills: Sequence[Skill] = (),
        workflows: Sequence[Workflow] = (),
        operation_ids: Sequence[str] = (),
        provenance: Sequence[str] = (),
    ) -> None:
        if not agent_id or not purpose:
            raise ValueError("Agent identity and purpose must not be empty")
        if security.subject_kind != "agent" or security.subject_id != agent_id:
            raise ValueError("Agent security must be bound to the Agent identity")
        if not security.verify_integrity():
            raise ValueError("Agent SecurityObject integrity is invalid")
        self.id = agent_id
        self.purpose = purpose
        self.instructions = tuple(instructions)
        self.input_contract = input_contract
        self.output_contract = output_contract
        self.security = security
        self.skills = tuple(skills)
        self.workflows = tuple(workflows)
        self.operation_ids = tuple(operation_ids)
        self.provenance = tuple(provenance)
        self._behavior = behavior

    @classmethod
    def from_instructions(
        cls,
        *,
        agent_id: str,
        purpose: str,
        instructions: str | Sequence[str],
        security: SecurityObject,
        behavior: AgentBehavior,
        input_contract: str = "json:any",
        output_contract: str = "json:any",
        skills: Sequence[Skill] = (),
        workflows: Sequence[Workflow] = (),
        operation_ids: Sequence[str] = (),
        provenance: Sequence[str] = (),
    ) -> Agent:
        normalized = (instructions,) if isinstance(instructions, str) else tuple(instructions)
        return cls(
            agent_id=agent_id,
            purpose=purpose,
            instructions=normalized,
            security=security,
            behavior=behavior,
            input_contract=input_contract,
            output_contract=output_contract,
            skills=skills,
            workflows=workflows,
            operation_ids=operation_ids,
            provenance=provenance,
        )

    def descriptor(self, module_id: str) -> AgentDescriptor:
        return AgentDescriptor(
            id=self.id,
            module_id=module_id,
            purpose=self.purpose,
            input_contract=self.input_contract,
            output_contract=self.output_contract,
            published_skills=tuple(skill.id for skill in self.skills),
            published_workflows=tuple(workflow.id for workflow in self.workflows),
            security=self.security,
            provenance=self.provenance,
        )

    async def execute(self, payload: JsonValue) -> Material:
        return await self._behavior.execute(
            agent_id=self.id,
            instructions=self.instructions,
            payload=payload,
        )


class Operation:
    """Bounded Module-owned callable effect over the public Operation contract."""

    __slots__ = (
        "id",
        "purpose",
        "input_contract",
        "output_contract",
        "effect",
        "repeatability",
        "security",
        "provenance",
        "_behavior",
    )

    def __init__(
        self,
        *,
        operation_id: str,
        purpose: str,
        input_contract: str,
        output_contract: str,
        effect: str,
        repeatability: str,
        security: SecurityObject,
        behavior: OperationBehavior,
        provenance: Sequence[str] = (),
    ) -> None:
        if security.subject_kind != "operation" or security.subject_id != operation_id:
            raise ValueError("Operation security must be bound to the Operation identity")
        if not security.verify_integrity():
            raise ValueError("Operation SecurityObject integrity is invalid")
        self.id = operation_id
        self.purpose = purpose
        self.input_contract = input_contract
        self.output_contract = output_contract
        self.effect = effect
        self.repeatability = repeatability
        self.security = security
        self.provenance = tuple(provenance)
        self._behavior = behavior

    def descriptor(self, module_id: str) -> OperationDescriptor:
        return OperationDescriptor(
            id=self.id,
            module_id=module_id,
            purpose=self.purpose,
            input_contract=self.input_contract,
            output_contract=self.output_contract,
            effect=self.effect,
            repeatability=self.repeatability,
            security=self.security,
            provenance=self.provenance,
        )

    async def execute(self, payload: JsonValue) -> Material:
        return await self._behavior.execute(operation_id=self.id, payload=payload)


class _AgentEndpoint(AgentEndpoint):
    def __init__(self, module: Module) -> None:
        self._module = module

    @property
    def boundary(self) -> ExecutionBoundary:
        return self._module.endpoint_boundary

    @property
    def security(self) -> SecurityObject:
        return self._module.endpoint_security

    async def invoke_agent(self, agent_id: str, payload: JsonValue) -> TransientMaterial:
        agent = self._module.agent(agent_id)
        if agent is None:
            raise KeyError(agent_id)
        return (await agent.execute(payload)).transient()


class _OperationEndpoint(OperationEndpoint):
    def __init__(self, module: Module) -> None:
        self._module = module

    @property
    def boundary(self) -> ExecutionBoundary:
        return self._module.endpoint_boundary

    @property
    def security(self) -> SecurityObject:
        return self._module.endpoint_security

    async def invoke_operation(self, operation_id: str, payload: JsonValue) -> TransientMaterial:
        operation = self._module.operation(operation_id)
        if operation is None:
            raise KeyError(operation_id)
        return (await operation.execute(payload)).transient()


class Module:
    """Reference SDK Module composition; private Module semantics remain outside this class."""

    def __init__(
        self,
        *,
        module_id: str,
        version: str,
        description: str,
        security: SecurityObject,
        discovery_terms: Sequence[str] = (),
        agents: Sequence[Agent] = (),
        skills: Sequence[Skill] = (),
        workflows: Sequence[Workflow] = (),
        operations: Sequence[Operation] = (),
        provenance: Sequence[str] = (),
        endpoint_boundary: ExecutionBoundary = "local",
        endpoint_security: SecurityObject | None = None,
        materials: MaterialRepository | None = None,
    ) -> None:
        if security.subject_kind != "module" or security.subject_id != module_id:
            raise ValueError("Module security must be bound to the Module identity")
        if not security.verify_integrity():
            raise ValueError("Module SecurityObject integrity is invalid")
        self.module_id = module_id
        self.version = version
        self.description = description
        self.security = security
        self.discovery_terms = tuple(discovery_terms)
        self.agents = tuple(agents)
        self.skills = tuple(skills)
        self.workflows = tuple(workflows)
        self.operations = tuple(operations)
        self.provenance = tuple(provenance)
        self.endpoint_boundary = endpoint_boundary
        actor_values = security.values
        if not isinstance(actor_values, ActorSecurityValues):
            raise ValueError("Module security must contain actor values")
        self.endpoint_security = endpoint_security or SecurityObject.issue(
            subject_id=f"{module_id}:endpoint",
            subject_kind="endpoint",
            values=actor_values,
        )
        self.materials = materials or MaterialRepository()
        self._agents = {agent.id: agent for agent in self.agents}
        self._operations = {operation.id: operation for operation in self.operations}
        if len(self._agents) != len(self.agents):
            raise ValueError("Agent identities must be unique within a Module")
        if len(self._operations) != len(self.operations):
            raise ValueError("Operation identities must be unique within a Module")
        self._agent_endpoint = _AgentEndpoint(self)
        self._operation_endpoint = _OperationEndpoint(self)

    def manifest(self) -> ModuleManifest:
        skill_map = {skill.id: skill for skill in self.skills}
        workflow_map = {workflow.id: workflow for workflow in self.workflows}
        for agent in self.agents:
            skill_map.update({skill.id: skill for skill in agent.skills})
            workflow_map.update({workflow.id: workflow for workflow in agent.workflows})
        return ModuleManifest(
            module_id=self.module_id,
            version=self.version,
            description=self.description,
            security=self.security,
            discovery_terms=self.discovery_terms,
            agents=tuple(agent.descriptor(self.module_id) for agent in self.agents),
            skills=tuple(skill.descriptor(self.module_id) for skill in skill_map.values()),
            workflows=tuple(
                workflow.descriptor(self.module_id) for workflow in workflow_map.values()
            ),
            operations=tuple(operation.descriptor(self.module_id) for operation in self.operations),
            provenance=self.provenance,
        )

    def register(self, registration: ModuleRegistration) -> None:
        registration.register(self.manifest())

    def register_material_resolution(self, registration: MaterialResolutionRegistration) -> None:
        registration.register_material_resolver(self.module_id, self.materials)

    def register_agent_endpoint(self, registration: AgentEndpointRegistration) -> None:
        if self.agents:
            registration.attach_agent_endpoint(self.module_id, self._agent_endpoint)

    def register_operation_endpoint(self, registration: OperationEndpointRegistration) -> None:
        if self.operations:
            registration.attach_operation_endpoint(self.module_id, self._operation_endpoint)

    def agent(self, agent_id: str) -> Agent | None:
        return self._agents.get(agent_id)

    def operation(self, operation_id: str) -> Operation | None:
        return self._operations.get(operation_id)
