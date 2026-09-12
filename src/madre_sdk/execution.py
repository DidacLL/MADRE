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

    def __post_init__(self) -> None:
        if not isinstance(self.location, ExecutionLocation):
            raise TypeError("PhysicalProperties.location requires ExecutionLocation")
        if not isinstance(self.latency, LatencyClass):
            raise TypeError("PhysicalProperties.latency requires LatencyClass")


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
        if not isinstance(self.allowed, frozenset):
            raise TypeError("LocationRequirement.allowed requires frozenset")
        if not self.allowed:
            raise ValueError("LocationRequirement requires at least one location")
        if any(not isinstance(location, ExecutionLocation) for location in self.allowed):
            raise TypeError("LocationRequirement accepts only ExecutionLocation")

    def matches(self, properties: PhysicalProperties) -> bool:
        return properties.location in self.allowed


@dataclass(frozen=True, slots=True)
class LatencyRequirement(PhysicalRequirement):
    maximum: LatencyClass

    def __post_init__(self) -> None:
        if not isinstance(self.maximum, LatencyClass):
            raise TypeError("LatencyRequirement.maximum requires LatencyClass")

    def matches(self, properties: PhysicalProperties) -> bool:
        return int(properties.latency.value) <= int(self.maximum.value)


@dataclass(frozen=True, slots=True)
class LocationPreference(PhysicalPreference):
    order: tuple[ExecutionLocation, ...]

    def __post_init__(self) -> None:
        if not isinstance(self.order, tuple):
            raise TypeError("LocationPreference.order requires tuple")
        if not self.order:
            raise ValueError("LocationPreference requires at least one location")
        if any(not isinstance(location, ExecutionLocation) for location in self.order):
            raise TypeError("LocationPreference accepts only ExecutionLocation")
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
        if not isinstance(self.order, tuple):
            raise TypeError("LatencyPreference.order requires tuple")
        if not self.order:
            raise ValueError("LatencyPreference requires at least one latency class")
        if any(not isinstance(latency, LatencyClass) for latency in self.order):
            raise TypeError("LatencyPreference accepts only LatencyClass")
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
        if not isinstance(self.identity, ComputationId):
            raise TypeError("ComputationContract.identity requires ComputationId")
        if not isinstance(self.accepted_inputs, frozenset):
            raise TypeError("ComputationContract.accepted_inputs requires frozenset")
        if not self.accepted_inputs:
            raise ValueError("ComputationContract requires at least one accepted input type")
        if any(not isinstance(identity, MaterialTypeId) for identity in self.accepted_inputs):
            raise TypeError("ComputationContract.accepted_inputs accepts only MaterialTypeId")
        if not isinstance(self.output_type, MaterialType):
            raise TypeError("ComputationContract.output_type requires MaterialType")


@dataclass(frozen=True, slots=True)
class WorkTiming:
    not_before: datetime | None = None
    timeout: timedelta = timedelta(minutes=2)

    def __post_init__(self) -> None:
        if self.not_before is not None and not isinstance(self.not_before, datetime):
            raise TypeError("WorkTiming.not_before requires datetime")
        if not isinstance(self.timeout, timedelta):
            raise TypeError("WorkTiming.timeout requires timedelta")
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
        if not isinstance(self.value, int) or isinstance(self.value, bool):
            raise TypeError("Priority.value requires int")
        if not 0 <= self.value <= 100:
            raise ValueError("Priority.value must be between 0 and 100")


@dataclass(frozen=True, slots=True)
class PhysicalRetryPolicy:
    maximum_attempts: int = 1
    initial_backoff: timedelta = timedelta(0)

    def __post_init__(self) -> None:
        if not isinstance(self.maximum_attempts, int) or isinstance(self.maximum_attempts, bool):
            raise TypeError("PhysicalRetryPolicy.maximum_attempts requires int")
        if not isinstance(self.initial_backoff, timedelta):
            raise TypeError("PhysicalRetryPolicy.initial_backoff requires timedelta")
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
        if not isinstance(self.module, ModuleId):
            raise TypeError("WorkRequest.module requires ModuleId")
        if not isinstance(self.materials, MaterialSet):
            raise TypeError("WorkRequest.materials requires MaterialSet")
        if not isinstance(self.computation, ComputationContract):
            raise TypeError("WorkRequest.computation requires ComputationContract")
        if not isinstance(self.requirements, tuple) or not isinstance(self.preferences, tuple):
            raise TypeError("WorkRequest physical constraints require tuples")
        if any(not isinstance(item, PhysicalRequirement) for item in self.requirements):
            raise TypeError("WorkRequest.requirements accepts only PhysicalRequirement")
        if any(not isinstance(item, PhysicalPreference) for item in self.preferences):
            raise TypeError("WorkRequest.preferences accepts only PhysicalPreference")
        if not isinstance(self.timing, WorkTiming):
            raise TypeError("WorkRequest.timing requires WorkTiming")
        if not isinstance(self.priority, Priority):
            raise TypeError("WorkRequest.priority requires Priority")
        if not isinstance(self.retry, PhysicalRetryPolicy):
            raise TypeError("WorkRequest.retry requires PhysicalRetryPolicy")
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
        if not isinstance(self.computation, ComputationId):
            raise TypeError("PhysicalResult.computation requires ComputationId")
        if not isinstance(self.output_type, MaterialType):
            raise TypeError("PhysicalResult.output_type requires MaterialType")
        if not isinstance(self.started_at, datetime) or not isinstance(self.completed_at, datetime):
            raise TypeError("PhysicalResult timestamps require datetime")
        if not isinstance(self.attempt, int) or isinstance(self.attempt, bool):
            raise TypeError("PhysicalResult.attempt requires int")
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
