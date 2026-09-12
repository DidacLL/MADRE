"""Module-facing contracts for physical work."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from enum import Enum

from madre_sdk.identity import ComputationId, MaterialTypeId, ModuleId
from madre_sdk.material import MaterialSet, MaterialType


class ExecutionLocation(Enum):
    OWNER_DEVICE = "owner_device"
    OWNER_NETWORK = "owner_network"
    EXTERNAL = "external"


class LatencyClass(Enum):
    INTERACTIVE = 1
    STANDARD = 2
    BATCH = 3


@dataclass(frozen=True, slots=True)
class PhysicalProperties:
    location: ExecutionLocation
    latency: LatencyClass


class PhysicalRequirement(ABC):
    @abstractmethod
    def matches(self, properties: PhysicalProperties) -> bool:
        """Return whether physical properties satisfy this exact requirement."""


class PhysicalPreference(ABC):
    @abstractmethod
    def rank(self, properties: PhysicalProperties) -> int:
        """Return a lower-is-better rank for these physical properties."""


@dataclass(frozen=True, slots=True)
class LocationRequirement(PhysicalRequirement):
    allowed: frozenset[ExecutionLocation]

    def __post_init__(self) -> None:
        if not self.allowed:
            raise ValueError("LocationRequirement requires at least one location")

    def matches(self, properties: PhysicalProperties) -> bool:
        return properties.location in self.allowed


@dataclass(frozen=True, slots=True)
class LatencyRequirement(PhysicalRequirement):
    maximum: LatencyClass

    def matches(self, properties: PhysicalProperties) -> bool:
        return int(properties.latency.value) <= int(self.maximum.value)


@dataclass(frozen=True, slots=True)
class LocationPreference(PhysicalPreference):
    order: tuple[ExecutionLocation, ...]

    def __post_init__(self) -> None:
        if not self.order:
            raise ValueError("LocationPreference requires at least one location")
        if len(self.order) != len(set(self.order)):
            raise ValueError("LocationPreference cannot repeat a location")

    def rank(self, properties: PhysicalProperties) -> int:
        try:
            return self.order.index(properties.location)
        except ValueError:
            return len(self.order)


@dataclass(frozen=True, slots=True)
class LatencyPreference(PhysicalPreference):
    order: tuple[LatencyClass, ...]

    def __post_init__(self) -> None:
        if not self.order:
            raise ValueError("LatencyPreference requires at least one latency class")
        if len(self.order) != len(set(self.order)):
            raise ValueError("LatencyPreference cannot repeat a latency class")

    def rank(self, properties: PhysicalProperties) -> int:
        try:
            return self.order.index(properties.latency)
        except ValueError:
            return len(self.order)


@dataclass(frozen=True, slots=True)
class ComputationContract[OutputT]:
    identity: ComputationId
    accepted_inputs: frozenset[MaterialTypeId]
    output_type: MaterialType[OutputT]

    def __post_init__(self) -> None:
        if not self.accepted_inputs:
            raise ValueError("ComputationContract requires at least one accepted input type")


@dataclass(frozen=True, slots=True)
class WorkTiming:
    not_before: datetime | None = None
    timeout: timedelta = timedelta(minutes=2)

    def __post_init__(self) -> None:
        if self.timeout <= timedelta(0):
            raise ValueError("WorkTiming.timeout must be positive")
        if self.not_before is not None and self.not_before.tzinfo is None:
            raise ValueError("WorkTiming.not_before must include a timezone")

    @classmethod
    def immediate(cls, *, timeout: timedelta = timedelta(minutes=2)) -> WorkTiming:
        return cls(timeout=timeout)


@dataclass(frozen=True, slots=True)
class Priority:
    value: int = 50

    def __post_init__(self) -> None:
        if not 0 <= self.value <= 100:
            raise ValueError("Priority.value must be between 0 and 100")


@dataclass(frozen=True, slots=True)
class PhysicalRetryPolicy:
    maximum_attempts: int = 1
    initial_backoff: timedelta = timedelta(0)

    def __post_init__(self) -> None:
        if self.maximum_attempts < 1:
            raise ValueError("PhysicalRetryPolicy.maximum_attempts must be positive")
        if self.initial_backoff < timedelta(0):
            raise ValueError("PhysicalRetryPolicy.initial_backoff cannot be negative")


@dataclass(frozen=True, slots=True)
class WorkRequest[OutputT]:
    module: ModuleId
    materials: MaterialSet
    computation: ComputationContract[OutputT]
    requirements: tuple[PhysicalRequirement, ...] = ()
    preferences: tuple[PhysicalPreference, ...] = ()
    timing: WorkTiming = WorkTiming()
    priority: Priority = Priority()
    retry: PhysicalRetryPolicy = PhysicalRetryPolicy()

    def __post_init__(self) -> None:
        if any(material.owner != self.module for material in self.materials.materials):
            raise ValueError("WorkRequest Material must be owned by the originating Module")
        if any(
            material.material_type.identity not in self.computation.accepted_inputs
            for material in self.materials.materials
        ):
            raise ValueError("WorkRequest MaterialType is not accepted by its computation")


@dataclass(frozen=True, slots=True)
class PhysicalResult[OutputT]:
    computation: ComputationId
    output_type: MaterialType[OutputT]
    output: OutputT
    started_at: datetime
    completed_at: datetime
    attempt: int

    def __post_init__(self) -> None:
        if self.started_at.tzinfo is None or self.completed_at.tzinfo is None:
            raise ValueError("PhysicalResult timestamps must include a timezone")
        if self.completed_at < self.started_at:
            raise ValueError("PhysicalResult cannot complete before it starts")
        if self.attempt < 1:
            raise ValueError("PhysicalResult.attempt must be positive")

    @classmethod
    def completed(
        cls,
        *,
        computation: ComputationId,
        output_type: MaterialType[OutputT],
        output: OutputT,
        attempt: int = 1,
    ) -> PhysicalResult[OutputT]:
        now = datetime.now(UTC)
        return cls(
            computation=computation,
            output_type=output_type,
            output=output,
            started_at=now,
            completed_at=now,
            attempt=attempt,
        )
