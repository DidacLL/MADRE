"""Small semantic contracts shared by independent MADRE Modules."""

from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from pydantic import Field

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
    EffectProfile,
    ExecutionBoundary,
    FrozenModel,
    Identifier,
    InvocationContext,
    SecurityEvidence,
    SecurityObject,
)
from madre_sdk.material import Material, MaterialRepository
from madre_sdk.security import participant_security
from madre_sdk.services import ExecutionServices, ModuleServices


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
    @property
    def id(self) -> str: ...

    def project_work(self) -> tuple[WorkSubmission, ...]: ...


class AgentBehavior(Protocol):
    async def execute(
        self,
        *,
        agent_id: str,
        instructions: tuple[str, ...],
        evidence: SecurityEvidence,
        invocation: InvocationContext,
        services: ExecutionServices,
        material: TransientMaterial,
    ) -> Material: ...


class OperationBehavior(Protocol):
    async def execute(
        self,
        *,
        operation_id: str,
        effect_profile_id: str,
        invocation: InvocationContext,
        services: ExecutionServices,
        evidence: SecurityEvidence,
        material: TransientMaterial,
    ) -> Material: ...


class Agent:
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
        if security.scope.scope_id != agent_id or security.privacy is None:
            raise ValueError("Agent security must be bound to the Agent identity")
        if not security.verify_binding():
            raise ValueError("Agent SecurityObject binding is invalid")
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
        if self.security.scope.owner_module_id != module_id:
            raise ValueError("Agent SecurityObject is bound to another Module")
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

    async def _execute(
        self,
        material: TransientMaterial,
        *,
        evidence: SecurityEvidence,
        invocation: InvocationContext,
        services: ExecutionServices,
    ) -> Material:
        return await self._behavior.execute(
            agent_id=self.id,
            instructions=self.instructions,
            evidence=evidence,
            invocation=invocation,
            services=services,
            material=material,
        )


class Operation:
    __slots__ = (
        "id",
        "revision",
        "purpose",
        "input_contract",
        "output_contract",
        "effect",
        "repeatability",
        "effect_profiles",
        "provenance",
        "_profiles",
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
        effect_profiles: Sequence[EffectProfile],
        behavior: OperationBehavior,
        revision: str = "1",
        provenance: Sequence[str] = (),
    ) -> None:
        if not effect_profiles:
            raise ValueError("Operation requires at least one immutable EffectProfile")
        self.id = operation_id
        self.revision = revision
        self.purpose = purpose
        self.input_contract = input_contract
        self.output_contract = output_contract
        self.effect = effect
        self.repeatability = repeatability
        self.effect_profiles = tuple(effect_profiles)
        self.provenance = tuple(provenance)
        self._profiles = {profile.id: profile for profile in effect_profiles}
        if len(self._profiles) != len(self.effect_profiles):
            raise ValueError("EffectProfile identities must be unique within an Operation")
        for profile in self.effect_profiles:
            if (
                profile.operation.operation_id != operation_id
                or profile.operation.operation_revision != revision
            ):
                raise ValueError("EffectProfile must be bound to this Operation revision")
        self._behavior = behavior

    def effect_profile(self, profile_id: str) -> EffectProfile | None:
        return self._profiles.get(profile_id)

    def descriptor(self, module_id: str) -> OperationDescriptor:
        for profile in self.effect_profiles:
            if profile.operation.module_id != module_id:
                raise ValueError("EffectProfile is bound to another Module")
        return OperationDescriptor(
            id=self.id,
            module_id=module_id,
            revision=self.revision,
            purpose=self.purpose,
            input_contract=self.input_contract,
            output_contract=self.output_contract,
            effect=self.effect,
            repeatability=self.repeatability,
            effect_profiles=self.effect_profiles,
            provenance=self.provenance,
        )

    async def _execute(
        self,
        material: TransientMaterial,
        *,
        effect_profile_id: str,
        invocation: InvocationContext,
        services: ExecutionServices,
        evidence: SecurityEvidence,
    ) -> Material:
        if effect_profile_id not in self._profiles:
            raise KeyError(effect_profile_id)
        return await self._behavior.execute(
            operation_id=self.id,
            effect_profile_id=effect_profile_id,
            evidence=evidence,
            invocation=invocation,
            services=services,
            material=material,
        )


class _AgentEndpoint(AgentEndpoint):
    def __init__(self, module: Module) -> None:
        self._module = module

    @property
    def boundary(self) -> ExecutionBoundary:
        return self._module.endpoint_boundary

    @property
    def security(self) -> SecurityObject:
        return self._module.endpoint_security

    async def invoke_agent(
        self,
        agent_id: str,
        invocation: InvocationContext,
        evidence: SecurityEvidence,
        material: TransientMaterial,
    ) -> TransientMaterial:
        return (
            await self._module.execute_agent(
                agent_id, material, evidence=evidence, invocation=invocation
            )
        ).transient()


class _OperationEndpoint(OperationEndpoint):
    def __init__(self, module: Module) -> None:
        self._module = module

    @property
    def boundary(self) -> ExecutionBoundary:
        return self._module.endpoint_boundary

    @property
    def security(self) -> SecurityObject:
        return self._module.endpoint_security

    async def invoke_operation(
        self,
        operation_id: str,
        effect_profile_id: str,
        invocation: InvocationContext,
        evidence: SecurityEvidence,
        material: TransientMaterial,
    ) -> TransientMaterial:
        return (
            await self._module.execute_operation(
                operation_id,
                effect_profile_id,
                material,
                evidence=evidence,
                invocation=invocation,
            )
        ).transient()


class Module:
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
        services: ModuleServices | None = None,
    ) -> None:
        ref = security.scope
        if (
            ref.owner_module_id != module_id
            or ref.scope_id != module_id
            or ref.publication_revision != version
            or security.privacy is None
        ):
            raise ValueError("Module security must be bound to the Module identity/revision")
        if not security.verify_binding():
            raise ValueError("Module SecurityObject binding is invalid")
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
        if security.privacy is None:
            raise ValueError("Module requires Privacy")
        self.endpoint_security = endpoint_security or participant_security(
            owner_module_id=module_id,
            scope_id=f"{module_id}:endpoint",
            privacy=security.privacy,
            integrity=security.integrity,
            publication_revision=version,
        )
        InvocationContext(module=self.security, endpoint=self.endpoint_security)
        self.materials = materials or MaterialRepository()
        self._services = services or ModuleServices()
        self._agents = {agent.id: agent for agent in self.agents}
        self._operations = {operation.id: operation for operation in self.operations}
        if len(self._agents) != len(self.agents):
            raise ValueError("Agent identities must be unique within a Module")
        if len(self._operations) != len(self.operations):
            raise ValueError("Operation identities must be unique within a Module")
        for agent in self.agents:
            if (
                agent.security.scope.owner_module_id != module_id
                or agent.security.scope.publication_revision != version
            ):
                raise ValueError("Agent security binding must match its Module publication")
        for operation in self.operations:
            for profile in operation.effect_profiles:
                if (
                    profile.operation.module_id != module_id
                    or profile.operation.publication_revision != version
                ):
                    raise ValueError("EffectProfile binding must match its Module publication")
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

    def agent_context(self, agent_id: str) -> SecurityEvidence:
        agent = self.agent(agent_id)
        if agent is None:
            raise KeyError(agent_id)
        return SecurityEvidence(objects=(self.security, agent.security, self.endpoint_security))

    def operation_context(self, operation_id: str, effect_profile_id: str) -> SecurityEvidence:
        operation = self.operation(operation_id)
        if operation is None:
            raise KeyError(operation_id)
        profile = operation.effect_profile(effect_profile_id)
        if profile is None:
            raise KeyError(effect_profile_id)
        return SecurityEvidence(objects=(self.security, self.endpoint_security))

    async def execute_agent(
        self,
        agent_id: str,
        material: Material | TransientMaterial,
        *,
        evidence: SecurityEvidence | None = None,
        invocation: InvocationContext | None = None,
    ) -> Material:
        agent = self.agent(agent_id)
        if agent is None:
            raise KeyError(agent_id)
        transient = (
            material.transient() if not isinstance(material, TransientMaterial) else material
        )
        expected = InvocationContext(
            module=self.security,
            agent=agent.security,
            endpoint=self.endpoint_security,
            direct_interaction=invocation.direct_interaction if invocation is not None else None,
        )
        if invocation is not None and invocation != expected:
            raise ValueError("Agent invocation does not match executing publication")
        carried = (
            (evidence or self.agent_context(agent_id))
            .merge(transient.evidence)
            .extend(objects=expected.objects)
        )
        with self._services._execution(expected) as services:
            return await agent._execute(
                transient, evidence=carried, invocation=expected, services=services
            )

    async def execute_operation(
        self,
        operation_id: str,
        effect_profile_id: str,
        material: Material | TransientMaterial,
        *,
        evidence: SecurityEvidence | None = None,
        invocation: InvocationContext | None = None,
    ) -> Material:
        operation = self.operation(operation_id)
        if operation is None:
            raise KeyError(operation_id)
        transient = (
            material.transient() if not isinstance(material, TransientMaterial) else material
        )
        carried = (evidence or self.operation_context(operation_id, effect_profile_id)).merge(
            transient.evidence
        )
        profile = operation.effect_profile(effect_profile_id)
        if profile is None:
            raise KeyError(effect_profile_id)
        expected = InvocationContext(
            module=self.security,
            endpoint=self.endpoint_security,
            operation=profile.operation,
            direct_interaction=invocation.direct_interaction if invocation is not None else None,
        )
        if invocation is not None and invocation != expected:
            raise ValueError("Operation invocation does not match executing publication")
        with self._services._execution(expected) as services:
            return await operation._execute(
                transient,
                effect_profile_id=effect_profile_id,
                evidence=carried.extend(objects=expected.objects),
                invocation=expected,
                services=services,
            )
