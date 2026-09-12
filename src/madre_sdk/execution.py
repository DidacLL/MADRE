"""Public contracts for requesting physical execution."""

from __future__ import annotations

from enum import Enum
from typing import Self

from pydantic import Field, JsonValue, model_validator

from madre_sdk.material import Material, MaterialSpecification
from madre_sdk.security import FrozenValue, ScopeIdentity, SecuritySurface


class ExecutionBoundary(Enum):
    LOCAL = "local"
    ISOLATED = "isolated"
    REMOTE = "remote"


class LatencyClass(Enum):
    INTERACTIVE = "interactive"
    STANDARD = "standard"
    BATCH = "batch"


class ReasoningEffort(Enum):
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"


class QualityTier(Enum):
    BASIC = "basic"
    STANDARD = "standard"
    HIGH = "high"


class CostClass(Enum):
    FREE = "free"
    PAID = "paid"


class ExecutionConstraints(FrozenValue):
    timeout_seconds: float = Field(default=120, gt=0, allow_inf_nan=False)


class CapabilityProperties(FrozenValue):
    specialization: ScopeIdentity
    modality: ScopeIdentity
    boundary: ExecutionBoundary
    latency: LatencyClass = LatencyClass.STANDARD
    reasoning_efforts: frozenset[ReasoningEffort] = frozenset(
        {ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH}
    )
    quality: QualityTier = QualityTier.STANDARD
    cost: CostClass = CostClass.FREE
    resources: frozenset[ScopeIdentity] = frozenset()
    heavyweight: bool = False


class CapabilityDefinition(FrozenValue):
    """A physical mechanism and the exact surface it exposes during execution."""

    identity: ScopeIdentity
    properties: CapabilityProperties
    security: SecuritySurface

    @model_validator(mode="after")
    def has_observer_privacy(self) -> Self:
        if self.security.privacy is None:
            raise ValueError("a Capability execution surface must carry Privacy")
        return self


class CapabilityQuery(FrozenValue):
    specialization: ScopeIdentity
    modality: ScopeIdentity
    mechanism: ScopeIdentity | None = None
    boundaries: tuple[ExecutionBoundary, ...] = ()
    latencies: tuple[LatencyClass, ...] = ()
    reasoning_efforts: tuple[ReasoningEffort, ...] = ()
    qualities: tuple[QualityTier, ...] = ()
    costs: tuple[CostClass, ...] = ()
    required_resources: frozenset[ScopeIdentity] = frozenset()

    def accepts(self, capability: CapabilityDefinition) -> bool:
        properties = capability.properties
        required = (
            properties.specialization == self.specialization,
            properties.modality == self.modality,
            self.mechanism is None or capability.identity == self.mechanism,
            not self.boundaries or properties.boundary in self.boundaries,
            not self.latencies or properties.latency in self.latencies,
            not self.reasoning_efforts
            or bool(set(self.reasoning_efforts) & properties.reasoning_efforts),
            not self.qualities or properties.quality in self.qualities,
            not self.costs or properties.cost in self.costs,
            self.required_resources.issubset(properties.resources),
        )
        return all(required)

    def rank(self, capability: CapabilityDefinition) -> tuple[int, ...]:
        properties = capability.properties
        return (
            self._position(self.boundaries, properties.boundary),
            self._position(self.latencies, properties.latency),
            self._best_position(self.reasoning_efforts, properties.reasoning_efforts),
            self._position(self.qualities, properties.quality),
            self._position(self.costs, properties.cost),
        )

    @staticmethod
    def _position(values: tuple[object, ...], candidate: object) -> int:
        if not values:
            return 0
        try:
            return values.index(candidate)
        except ValueError:
            return len(values)

    @staticmethod
    def _best_position(values: tuple[object, ...], candidates: frozenset[object]) -> int:
        if not values:
            return 0
        return min(
            (index for index, value in enumerate(values) if value in candidates),
            default=len(values),
        )


class ExecutionRequest(FrozenValue):
    requester: ScopeIdentity
    material: Material[JsonValue]
    capability: CapabilityQuery
    output: MaterialSpecification
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
