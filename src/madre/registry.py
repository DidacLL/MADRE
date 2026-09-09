"""Public interoperability descriptors. Registries expose existence and routing, not authority."""

from __future__ import annotations

from typing import Protocol

from pydantic import Field, model_validator

from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    FrozenModel,
    Identifier,
    SecurityContext,
    SecurityEvaluator,
    SecurityObject,
)


class AgentDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1)
    input_contract: Identifier
    output_contract: Identifier
    published_skills: tuple[Identifier, ...] = ()
    published_workflows: tuple[Identifier, ...] = ()
    security: SecurityObject
    provenance: tuple[Identifier, ...] = ()


class SkillDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1)
    version: Identifier
    instructions: tuple[str, ...] = ()
    resources: tuple[Identifier, ...] = ()
    input_contract: Identifier | None = None
    output_contract: Identifier | None = None
    related_operations: tuple[Identifier, ...] = ()
    related_workflows: tuple[Identifier, ...] = ()
    compatibility: tuple[Identifier, ...] = ()
    provenance: tuple[Identifier, ...] = ()


class WorkflowDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1)
    version: Identifier
    instructions: tuple[str, ...] = ()
    input_contract: Identifier
    output_contract: Identifier
    provenance: tuple[Identifier, ...] = ()


class OperationDescriptor(FrozenModel):
    id: Identifier
    module_id: Identifier
    purpose: str = Field(min_length=1)
    input_contract: Identifier
    output_contract: Identifier
    effect: Identifier
    repeatability: Identifier
    security: SecurityObject
    provenance: tuple[Identifier, ...] = ()


PublicDescriptor = AgentDescriptor | SkillDescriptor | WorkflowDescriptor | OperationDescriptor


class ModuleManifest(FrozenModel):
    module_id: Identifier
    version: Identifier
    description: str = Field(min_length=1)
    security: SecurityObject
    discovery_terms: tuple[str, ...] = ()
    agents: tuple[AgentDescriptor, ...] = ()
    skills: tuple[SkillDescriptor, ...] = ()
    workflows: tuple[WorkflowDescriptor, ...] = ()
    operations: tuple[OperationDescriptor, ...] = ()
    provenance: tuple[Identifier, ...] = ()

    @model_validator(mode="after")
    def exports_belong_to_module(self) -> ModuleManifest:
        if self.security.subject_kind != "module" or self.security.subject_id != self.module_id:
            raise ValueError("Module security must be bound to the Module identity")
        if not self.security.verify_integrity():
            raise ValueError("Module SecurityObject integrity is invalid")

        exports: tuple[PublicDescriptor, ...] = (
            *self.agents,
            *self.skills,
            *self.workflows,
            *self.operations,
        )
        for descriptor in exports:
            if descriptor.module_id != self.module_id:
                raise ValueError("every exported descriptor must identify its owning Module")
        for descriptor in self.agents:
            if (
                descriptor.security.subject_kind != "agent"
                or descriptor.security.subject_id != descriptor.id
                or not descriptor.security.verify_integrity()
            ):
                raise ValueError(f"invalid Agent SecurityObject: {descriptor.id}")
        for descriptor in self.operations:
            if (
                descriptor.security.subject_kind != "operation"
                or descriptor.security.subject_id != descriptor.id
                or not descriptor.security.verify_integrity()
            ):
                raise ValueError(f"invalid Operation SecurityObject: {descriptor.id}")
        return self


class RegistryStoreProtocol(Protocol):
    def put_manifest(self, manifest: ModuleManifest) -> None: ...

    def manifests(self) -> tuple[ModuleManifest, ...]: ...


class InteroperabilityRegistry:
    def __init__(
        self,
        store: RegistryStoreProtocol,
        *,
        security_evaluator: SecurityEvaluator = DEFAULT_SECURITY_EVALUATOR,
    ) -> None:
        self._store = store
        self._security_evaluator = security_evaluator

    def register(self, manifest: ModuleManifest) -> None:
        self._store.put_manifest(manifest)

    def get_module(self, module_id: str) -> ModuleManifest | None:
        return next((item for item in self._store.manifests() if item.module_id == module_id), None)

    def get_agent(self, module_id: str, agent_id: str) -> AgentDescriptor | None:
        manifest = self.get_module(module_id)
        if manifest is None:
            return None
        return next((item for item in manifest.agents if item.id == agent_id), None)

    def get_operation(self, module_id: str, operation_id: str) -> OperationDescriptor | None:
        manifest = self.get_module(module_id)
        if manifest is None:
            return None
        return next((item for item in manifest.operations if item.id == operation_id), None)

    def discover_agents(self, security: SecurityContext) -> tuple[AgentDescriptor, ...]:
        visible: list[AgentDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.agents
                if self._security_evaluator.evaluate(
                    security.extend(manifest.security, descriptor.security)
                ).admissible
            )
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))

    def discover_skills(self, security: SecurityContext) -> tuple[SkillDescriptor, ...]:
        visible: list[SkillDescriptor] = []
        for manifest in self._store.manifests():
            if self._security_evaluator.evaluate(security.extend(manifest.security)).admissible:
                visible.extend(manifest.skills)
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))

    def discover_workflows(self, security: SecurityContext) -> tuple[WorkflowDescriptor, ...]:
        visible: list[WorkflowDescriptor] = []
        for manifest in self._store.manifests():
            if self._security_evaluator.evaluate(security.extend(manifest.security)).admissible:
                visible.extend(manifest.workflows)
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))

    def discover_operations(self, security: SecurityContext) -> tuple[OperationDescriptor, ...]:
        visible: list[OperationDescriptor] = []
        for manifest in self._store.manifests():
            visible.extend(
                descriptor
                for descriptor in manifest.operations
                if self._security_evaluator.evaluate(
                    security.extend(manifest.security, descriptor.security)
                ).admissible
            )
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))
