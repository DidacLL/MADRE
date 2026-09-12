"""Declarative, serializable MADRE building blocks."""

from __future__ import annotations

from enum import Enum
from typing import Protocol, Self

from pydantic import Field, JsonValue, model_validator

from madre_sdk.execution import ExecutionRequest
from madre_sdk.material import Material, MaterialContract
from madre_sdk.security import (
    EffectProfile,
    FrozenValue,
    ScopeIdentity,
    SecurityScope,
    SecuritySurface,
)


class Repeatability(Enum):
    SAFE = "safe"
    IDEMPOTENT = "idempotent"
    NON_REPEATABLE = "non_repeatable"


def _owned_by(identity: ScopeIdentity, owner: str, kind: str) -> None:
    if identity.owner != owner:
        raise ValueError(f"{kind} {identity.name} belongs to another Module")


def _unique(identities: tuple[ScopeIdentity, ...], kind: str) -> None:
    if len(identities) != len(set(identities)):
        raise ValueError(f"{kind} identities must be unique")


class SkillDefinition(FrozenValue):
    identity: ScopeIdentity
    purpose: str = Field(min_length=1)
    instructions: tuple[str, ...] = ()
    resources: tuple[ScopeIdentity, ...] = ()
    input_contract: MaterialContract | None = None
    output_contract: MaterialContract | None = None
    security: SecurityScope | None = None

    @model_validator(mode="after")
    def binds_security(self) -> Self:
        if self.security is not None and self.security.identity != self.identity:
            raise ValueError("Skill security must describe the exact Skill")
        return self


class WorkflowDefinition(FrozenValue):
    identity: ScopeIdentity
    purpose: str = Field(min_length=1)
    instructions: tuple[str, ...] = ()
    input_contract: MaterialContract
    output_contract: MaterialContract
    security: SecurityScope | None = None

    @model_validator(mode="after")
    def binds_security(self) -> Self:
        if self.security is not None and self.security.identity != self.identity:
            raise ValueError("Workflow security must describe the exact Workflow")
        return self


class OperationDefinition(FrozenValue):
    identity: ScopeIdentity
    purpose: str = Field(min_length=1)
    input_contract: MaterialContract
    output_contract: MaterialContract
    effect: str = Field(min_length=1)
    repeatability: Repeatability
    security: SecurityScope
    effect_profiles: tuple[EffectProfile, ...] = Field(min_length=1)

    @model_validator(mode="after")
    def binds_profiles_and_security(self) -> Self:
        if self.security.identity != self.identity:
            raise ValueError("Operation security must describe the exact Operation")
        identities = tuple(profile.identity for profile in self.effect_profiles)
        _unique(identities, "EffectProfile")
        if any(profile.operation != self.identity for profile in self.effect_profiles):
            raise ValueError("every EffectProfile must belong to its Operation")
        return self

    @property
    def surface(self) -> SecuritySurface:
        return SecuritySurface.from_scope(self.security)


class AgentDefinition(FrozenValue):
    identity: ScopeIdentity
    purpose: str = Field(min_length=1)
    instructions: tuple[str, ...] = ()
    input_contract: MaterialContract
    output_contract: MaterialContract
    security: SecurityScope | None = None
    skills: tuple[SkillDefinition, ...] = ()
    workflows: tuple[WorkflowDefinition, ...] = ()
    operations: tuple[OperationDefinition, ...] = ()

    @model_validator(mode="after")
    def owns_members(self) -> Self:
        owner = self.identity.owner
        if self.security is not None and self.security.identity != self.identity:
            raise ValueError("Agent security must describe the exact Agent")
        for skill in self.skills:
            _owned_by(skill.identity, owner, type(skill).__name__)
        for workflow in self.workflows:
            _owned_by(workflow.identity, owner, type(workflow).__name__)
        for operation in self.operations:
            _owned_by(operation.identity, owner, type(operation).__name__)
        _unique(
            (
                *(member.identity for member in self.skills),
                *(member.identity for member in self.workflows),
                *(member.identity for member in self.operations),
            ),
            "Agent member",
        )
        return self

    @property
    def surface(self) -> SecuritySurface:
        members: list[SecurityScope | SecuritySurface] = []
        if self.security is not None:
            members.append(self.security)
        members.extend(skill.security for skill in self.skills if skill.security is not None)
        members.extend(
            workflow.security for workflow in self.workflows if workflow.security is not None
        )
        members.extend(operation.surface for operation in self.operations)
        if not members:
            raise ValueError("Agent has no security-applicable exposed surface")
        return SecuritySurface.compose(*members)


class ModuleDefinition(FrozenValue):
    identity: ScopeIdentity
    description: str = Field(min_length=1)
    security: SecurityScope | None = None
    managed_scopes: tuple[SecurityScope, ...] = ()
    agents: tuple[AgentDefinition, ...] = ()
    skills: tuple[SkillDefinition, ...] = ()
    workflows: tuple[WorkflowDefinition, ...] = ()
    operations: tuple[OperationDefinition, ...] = ()
    discovery_terms: tuple[str, ...] = ()

    @model_validator(mode="after")
    def owns_members(self) -> Self:
        owner = self.identity.owner
        if self.identity.name != owner:
            raise ValueError("a Module identity name must equal its owner")
        if self.security is not None and self.security.identity != self.identity:
            raise ValueError("Module security must describe the exact Module")
        for agent in self.agents:
            _owned_by(agent.identity, owner, type(agent).__name__)
        for skill in self.skills:
            _owned_by(skill.identity, owner, type(skill).__name__)
        for workflow in self.workflows:
            _owned_by(workflow.identity, owner, type(workflow).__name__)
        for operation in self.operations:
            _owned_by(operation.identity, owner, type(operation).__name__)
        _unique(
            (
                *(member.identity for member in self.agents),
                *(member.identity for member in self.skills),
                *(member.identity for member in self.workflows),
                *(member.identity for member in self.operations),
            ),
            "Module member",
        )
        return self

    @property
    def surface(self) -> SecuritySurface:
        members: list[SecurityScope | SecuritySurface] = []
        if self.security is not None:
            members.append(self.security)
        members.extend(self.managed_scopes)
        members.extend(agent.surface for agent in self.agents)
        members.extend(skill.security for skill in self.skills if skill.security is not None)
        members.extend(
            workflow.security for workflow in self.workflows if workflow.security is not None
        )
        members.extend(operation.surface for operation in self.operations)
        if not members:
            raise ValueError("Module has no security-applicable managed or exposed surface")
        return SecuritySurface.compose(*members)


class ExecutionServices(Protocol):
    async def execute(self, request: ExecutionRequest) -> Material[JsonValue]: ...


class ModuleBehavior(Protocol):
    async def receive(
        self,
        material: Material[JsonValue],
        services: ExecutionServices,
    ) -> Material[JsonValue]: ...


class AgentBehavior(Protocol):
    async def receive(
        self,
        definition: AgentDefinition,
        material: Material[JsonValue],
        services: ExecutionServices,
    ) -> Material[JsonValue]: ...


class OperationBehavior(Protocol):
    async def execute(
        self,
        definition: OperationDefinition,
        profile: EffectProfile,
        material: Material[JsonValue],
    ) -> Material[JsonValue]: ...


class ModuleRuntime:
    """Binds private executable behavior to a declarative Module definition."""

    def __init__(
        self,
        definition: ModuleDefinition,
        behavior: ModuleBehavior,
        services: ExecutionServices,
    ) -> None:
        self.definition = definition
        self._behavior = behavior
        self._services = services

    async def receive(self, material: Material[JsonValue]) -> Material[JsonValue]:
        return await self._behavior.receive(material, self._services)
