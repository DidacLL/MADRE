"""Public interoperability descriptors. Implementations remain Module-owned."""

from __future__ import annotations

from typing import Protocol

from pydantic import Field, model_validator

from madre.security import (
    BoundaryRequirements,
    FrozenModel,
    SecurityAlgebra,
    SecurityEnvelope,
    SecurityPolicy,
)


class AgentDescriptor(FrozenModel):
    id: str = Field(min_length=1)
    module_id: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    input_contract: str = Field(min_length=1)
    output_contract: str = Field(min_length=1)
    published_skills: tuple[str, ...] = ()
    published_workflows: tuple[str, ...] = ()
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[str, ...] = ()


class SkillDescriptor(FrozenModel):
    id: str = Field(min_length=1)
    module_id: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    version: str = Field(min_length=1)
    input_contract: str | None = None
    output_contract: str | None = None
    related_operations: tuple[str, ...] = ()
    related_workflows: tuple[str, ...] = ()
    compatibility: tuple[str, ...] = ()
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[str, ...] = ()


class WorkflowDescriptor(FrozenModel):
    id: str = Field(min_length=1)
    module_id: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    version: str = Field(min_length=1)
    input_contract: str = Field(min_length=1)
    output_contract: str = Field(min_length=1)
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[str, ...] = ()


class OperationDescriptor(FrozenModel):
    id: str = Field(min_length=1)
    module_id: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    input_contract: str = Field(min_length=1)
    output_contract: str = Field(min_length=1)
    effect: str = Field(min_length=1)
    repeatability: str = Field(min_length=1)
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[str, ...] = ()


PublicDescriptor = AgentDescriptor | SkillDescriptor | WorkflowDescriptor | OperationDescriptor


class ModuleManifest(FrozenModel):
    module_id: str = Field(min_length=1)
    version: str = Field(min_length=1)
    description: str = Field(min_length=1)
    discovery_terms: tuple[str, ...] = ()
    security: SecurityEnvelope
    agents: tuple[AgentDescriptor, ...] = ()
    skills: tuple[SkillDescriptor, ...] = ()
    workflows: tuple[WorkflowDescriptor, ...] = ()
    operations: tuple[OperationDescriptor, ...] = ()
    provenance: tuple[str, ...] = ()

    @model_validator(mode="after")
    def ownership_is_explicit(self) -> ModuleManifest:
        exports: tuple[PublicDescriptor, ...] = (
            *self.agents,
            *self.skills,
            *self.workflows,
            *self.operations,
        )
        for descriptor in exports:
            if descriptor.module_id != self.module_id:
                raise ValueError("every exported descriptor must identify its owning Module")
        if not self.security.verify_integrity():
            raise ValueError("Module security envelope integrity is invalid")
        return self


class RegistryStoreProtocol(Protocol):
    def put_manifest(self, manifest: ModuleManifest) -> None: ...

    def manifests(self) -> tuple[ModuleManifest, ...]: ...


class InteroperabilityRegistry:
    def __init__(self, store: RegistryStoreProtocol, policy: SecurityPolicy | None = None) -> None:
        self._store = store
        self._policy = policy or SecurityPolicy()

    def register(self, manifest: ModuleManifest) -> None:
        exports: tuple[PublicDescriptor, ...] = (
            *manifest.agents,
            *manifest.skills,
            *manifest.workflows,
            *manifest.operations,
        )
        for descriptor in exports:
            if not descriptor.security.verify_integrity():
                raise ValueError(f"invalid descriptor security envelope: {descriptor.id}")
        self._store.put_manifest(manifest)

    def get_module(self, module_id: str) -> ModuleManifest | None:
        return next((item for item in self._store.manifests() if item.module_id == module_id), None)

    def get_agent(self, agent_id: str) -> AgentDescriptor | None:
        for manifest in self._store.manifests():
            for descriptor in manifest.agents:
                if descriptor.id == agent_id:
                    return descriptor
        return None

    def get_operation(self, operation_id: str) -> OperationDescriptor | None:
        for manifest in self._store.manifests():
            for descriptor in manifest.operations:
                if descriptor.id == operation_id:
                    return descriptor
        return None

    def discover_agents(self, requester: SecurityEnvelope) -> tuple[AgentDescriptor, ...]:
        visible: list[AgentDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.agents
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def discover_skills(self, requester: SecurityEnvelope) -> tuple[SkillDescriptor, ...]:
        visible: list[SkillDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.skills
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def discover_workflows(
        self, requester: SecurityEnvelope
    ) -> tuple[WorkflowDescriptor, ...]:
        visible: list[WorkflowDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.workflows
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def discover_operations(
        self, requester: SecurityEnvelope
    ) -> tuple[OperationDescriptor, ...]:
        visible: list[OperationDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.operations
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def _visible(
        self,
        requester: SecurityEnvelope,
        descriptor: PublicDescriptor,
        manifest: ModuleManifest,
    ) -> bool:
        decision = SecurityAlgebra.visible(
            requester,
            descriptor.requirements,
            manifest.security,
            self._policy,
        )
        return decision.admissible
