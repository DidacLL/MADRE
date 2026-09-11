"""Public interoperability descriptors. Registries expose existence and routing, not authority."""

from __future__ import annotations

from typing import Protocol

from pydantic import Field, model_validator

from madre.security import (
    EffectProfile,
    FrozenModel,
    Identifier,
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
    revision: Identifier = "1"
    purpose: str = Field(min_length=1)
    input_contract: Identifier
    output_contract: Identifier
    effect: Identifier
    repeatability: Identifier
    effect_profiles: tuple[EffectProfile, ...] = Field(min_length=1)
    provenance: tuple[Identifier, ...] = ()

    @model_validator(mode="after")
    def profiles_belong_to_operation(self) -> OperationDescriptor:
        profile_ids = [profile.id for profile in self.effect_profiles]
        if len(profile_ids) != len(set(profile_ids)):
            raise ValueError("EffectProfile identities must be unique within an Operation")
        for profile in self.effect_profiles:
            if not profile.verify_identity():
                raise ValueError("EffectProfile identity is invalid")
            operation = profile.operation
            if (
                operation.module_id != self.module_id
                or operation.operation_id != self.id
                or operation.operation_revision != self.revision
            ):
                raise ValueError("EffectProfile must be bound to its owning Operation")
        return self

    def effect_profile(self, profile_id: str) -> EffectProfile | None:
        return next((profile for profile in self.effect_profiles if profile.id == profile_id), None)


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
        ref = self.security.scope
        if (
            ref.owner_module_id != self.module_id
            or ref.scope_id != self.module_id
            or ref.publication_revision != self.version
            or self.security.privacy is None
        ):
            raise ValueError("Module security must be bound to the Module identity/revision")
        if not self.security.verify_binding():
            raise ValueError("Module SecurityObject binding is invalid")

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
            agent_ref = descriptor.security.scope
            if (
                agent_ref.owner_module_id != self.module_id
                or agent_ref.scope_id != descriptor.id
                or agent_ref.publication_revision != self.version
                or descriptor.security.privacy is None
                or not descriptor.security.verify_binding()
            ):
                raise ValueError(f"invalid Agent SecurityObject: {descriptor.id}")
        for descriptor in self.operations:
            for profile in descriptor.effect_profiles:
                if profile.operation.publication_revision != self.version:
                    raise ValueError(
                        f"EffectProfile publication revision does not match Module: {descriptor.id}"
                    )
        return self


class RegistryStoreProtocol(Protocol):
    def put_manifest(self, manifest: ModuleManifest) -> None: ...
    def manifests(self) -> tuple[ModuleManifest, ...]: ...


class InteroperabilityRegistry:
    def __init__(self, store: RegistryStoreProtocol) -> None:
        self._store = store

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

    def list_agents(self) -> tuple[AgentDescriptor, ...]:
        visible = [
            descriptor for manifest in self._store.manifests() for descriptor in manifest.agents
        ]
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))

    def list_skills(self) -> tuple[SkillDescriptor, ...]:
        visible = [
            descriptor for manifest in self._store.manifests() for descriptor in manifest.skills
        ]
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))

    def list_workflows(self) -> tuple[WorkflowDescriptor, ...]:
        visible = [
            descriptor for manifest in self._store.manifests() for descriptor in manifest.workflows
        ]
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))

    def list_operations(self) -> tuple[OperationDescriptor, ...]:
        visible = [
            descriptor for manifest in self._store.manifests() for descriptor in manifest.operations
        ]
        return tuple(sorted(visible, key=lambda descriptor: (descriptor.module_id, descriptor.id)))
