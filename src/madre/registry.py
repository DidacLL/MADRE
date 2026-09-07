"""Public interoperability descriptors. Implementations remain Module-owned."""

from __future__ import annotations

from typing import Protocol

from pydantic import Field, model_validator

from madre.security import (
    BoundaryRequirements,
    FrozenModel,
    Identifier,
    SecurityAlgebra,
    SecurityEnvelope,
    SecurityPolicy,
)


class AgentDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1, max_length=2000)
    input_contract: Identifier
    output_contract: Identifier
    published_skills: tuple[Identifier, ...] = ()
    published_workflows: tuple[Identifier, ...] = ()
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[Identifier, ...] = ()


class SkillDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1, max_length=2000)
    version: Identifier
    input_contract: Identifier | None = None
    output_contract: Identifier | None = None
    related_operations: tuple[Identifier, ...] = ()
    related_workflows: tuple[Identifier, ...] = ()
    compatibility: tuple[Identifier, ...] = ()
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[Identifier, ...] = ()


class WorkflowDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1, max_length=2000)
    version: Identifier
    input_contract: Identifier
    output_contract: Identifier
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[Identifier, ...] = ()


class OperationDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1, max_length=2000)
    input_contract: Identifier
    output_contract: Identifier
    effect: Identifier
    repeatability: Identifier
    requirements: BoundaryRequirements
    security: SecurityEnvelope
    provenance: tuple[Identifier, ...] = ()


PublicDescriptor = AgentDescriptor | SkillDescriptor | WorkflowDescriptor | OperationDescriptor


class ModuleManifest(FrozenModel):
    module_id: Identifier
    version: Identifier
    description: str = Field(min_length=1, max_length=4000)
    discovery_terms: tuple[str, ...] = ()
    security: SecurityEnvelope
    inbound_requirements: BoundaryRequirements = Field(default_factory=BoundaryRequirements)
    agents: tuple[AgentDescriptor, ...] = ()
    skills: tuple[SkillDescriptor, ...] = ()
    workflows: tuple[WorkflowDescriptor, ...] = ()
    operations: tuple[OperationDescriptor, ...] = ()
    provenance: tuple[Identifier, ...] = ()

    @model_validator(mode="after")
    def ownership_and_integrity_are_explicit(self) -> ModuleManifest:
        if self.security.subject != self.module_id:
            raise ValueError("Module security envelope subject must equal module_id")
        if not self.security.verify_integrity():
            raise ValueError("Module security envelope integrity is invalid")
        exports: tuple[PublicDescriptor, ...] = (
            *self.agents,
            *self.skills,
            *self.workflows,
            *self.operations,
        )
        prefix = f"{self.module_id}/"
        for descriptor in exports:
            if descriptor.module_id != self.module_id:
                raise ValueError("every exported descriptor must identify its owning Module")
            if not descriptor.id.startswith(prefix):
                raise ValueError("exported descriptor identity must be namespaced by its Module")
            if descriptor.security.subject != descriptor.id:
                raise ValueError("descriptor security envelope subject must equal descriptor id")
            if not descriptor.security.verify_integrity():
                raise ValueError(f"invalid descriptor security envelope: {descriptor.id}")
        return self


class RegistryStoreProtocol(Protocol):
    def put_manifest(self, manifest: ModuleManifest) -> None: ...

    def manifests(self) -> tuple[ModuleManifest, ...]: ...


class InteroperabilityRegistry:
    def __init__(self, store: RegistryStoreProtocol, policy: SecurityPolicy | None = None) -> None:
        self._store = store
        self._policy = policy or SecurityPolicy()

    def register(self, manifest: ModuleManifest) -> None:
        self._store.put_manifest(manifest)

    def get_module(self, module_id: str) -> ModuleManifest | None:
        return next((item for item in self._store.manifests() if item.module_id == module_id), None)

    def require_module(self, module_id: str) -> ModuleManifest:
        manifest = self.get_module(module_id)
        if manifest is None:
            raise LookupError(f"Module is not registered: {module_id}")
        return manifest

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

    def discover_agents(self, requester_module_id: str) -> tuple[AgentDescriptor, ...]:
        requester = self.require_module(requester_module_id).security
        visible: list[AgentDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.agents
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def discover_skills(self, requester_module_id: str) -> tuple[SkillDescriptor, ...]:
        requester = self.require_module(requester_module_id).security
        visible: list[SkillDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.skills
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def discover_workflows(self, requester_module_id: str) -> tuple[WorkflowDescriptor, ...]:
        requester = self.require_module(requester_module_id).security
        visible: list[WorkflowDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.workflows
                if self._visible(requester, descriptor, manifest)
            )
        return tuple(sorted(visible, key=lambda descriptor: descriptor.id))

    def discover_operations(self, requester_module_id: str) -> tuple[OperationDescriptor, ...]:
        requester = self.require_module(requester_module_id).security
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
        if not descriptor.security.verify_integrity():
            return False
        decision = SecurityAlgebra.visible(
            requester,
            descriptor.requirements,
            descriptor.security,
            manifest.security,
            self._policy,
        )
        return decision.admissible
