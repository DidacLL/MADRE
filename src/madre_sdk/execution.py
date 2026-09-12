"""Public contracts for requesting physical execution."""

from __future__ import annotations

from enum import Enum
from typing import Self

from pydantic import Field, JsonValue, model_validator

from madre_sdk.material import Material, MaterialSpecification
from madre_sdk.security import FrozenValue, IdentityKind, ScopeIdentity, SecuritySurface


class ExecutionBoundary(Enum):
    LOCAL = "local"
    ISOLATED = "isolated"
    REMOTE = "remote"


class LatencyClass(Enum):
    INTERACTIVE = "interactive"
    STANDARD = "standard"
    BATCH = "batch"


class ExecutionConstraints(FrozenValue):
    timeout_seconds: float = Field(default=120, gt=0, allow_inf_nan=False)


class CapabilityProperties(FrozenValue):
    specialization: ScopeIdentity
    modality: ScopeIdentity
    boundary: ExecutionBoundary
    latency: LatencyClass = LatencyClass.STANDARD
    resources: frozenset[ScopeIdentity] = frozenset()
    heavyweight: bool = False

    @model_validator(mode="after")
    def has_typed_properties(self) -> Self:
        self.specialization.require(IdentityKind.SPECIALIZATION, "Capability specialization")
        self.modality.require(IdentityKind.MODALITY, "Capability modality")
        for resource in self.resources:
            resource.require(IdentityKind.RESOURCE, "Capability resource")
        return self


class CapabilityDefinition(FrozenValue):
    """A physical mechanism and the exact surface it exposes during execution."""

    identity: ScopeIdentity
    properties: CapabilityProperties
    security: SecuritySurface

    @model_validator(mode="after")
    def has_observer_privacy(self) -> Self:
        self.identity.require(IdentityKind.CAPABILITY, "CapabilityDefinition")
        if self.security.privacy is None:
            raise ValueError("a Capability execution surface must carry Privacy")
        return self


class CapabilityQuery(FrozenValue):
    specialization: ScopeIdentity
    modality: ScopeIdentity
    mechanism: ScopeIdentity | None = None
    boundaries: tuple[ExecutionBoundary, ...] = ()
    latencies: tuple[LatencyClass, ...] = ()
    required_resources: frozenset[ScopeIdentity] = frozenset()

    @model_validator(mode="after")
    def has_typed_properties(self) -> Self:
        self.specialization.require(IdentityKind.SPECIALIZATION, "Capability specialization")
        self.modality.require(IdentityKind.MODALITY, "Capability modality")
        if self.mechanism is not None:
            self.mechanism.require(IdentityKind.CAPABILITY, "Capability mechanism")
        for resource in self.required_resources:
            resource.require(IdentityKind.RESOURCE, "required Capability resource")
        return self

    def accepts(self, capability: CapabilityDefinition) -> bool:
        properties = capability.properties
        required = (
            properties.specialization == self.specialization,
            properties.modality == self.modality,
            self.mechanism is None or capability.identity == self.mechanism,
            not self.boundaries or properties.boundary in self.boundaries,
            not self.latencies or properties.latency in self.latencies,
            self.required_resources.issubset(properties.resources),
        )
        return all(required)

    def rank(self, capability: CapabilityDefinition) -> tuple[int, ...]:
        properties = capability.properties
        return (
            self._position(self.boundaries, properties.boundary),
            self._position(self.latencies, properties.latency),
        )

    @staticmethod
    def _position[PreferenceT](
        values: tuple[PreferenceT, ...],
        candidate: PreferenceT,
    ) -> int:
        if not values:
            return 0
        try:
            return values.index(candidate)
        except ValueError:
            return len(values)


class ExecutionRequest(FrozenValue):
    requester: ScopeIdentity
    material: Material[JsonValue]
    capability: CapabilityQuery
    output: MaterialSpecification
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)

    @model_validator(mode="after")
    def creates_new_module_owned_material(self) -> Self:
        self.requester.require(IdentityKind.MODULE, "ExecutionRequest requester")
        if self.output.identity == self.material.identity:
            raise ValueError("physical output must be a new Material identity")
        if self.output.identity.owner != self.requester.owner:
            raise ValueError("physical output must be owned by the requesting Module")
        return self
