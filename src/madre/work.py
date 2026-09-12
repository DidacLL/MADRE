"""Immutable public views of durable physical work."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum

from madre_sdk import ComputationId, ModuleId


@dataclass(frozen=True, slots=True)
class WorkId:
    value: str

    def __post_init__(self) -> None:
        if not isinstance(self.value, str):
            raise TypeError("WorkId.value requires str")
        if not self.value or self.value.isspace():
            raise ValueError("WorkId.value must not be blank")


class WorkStatus(Enum):
    QUEUED = "queued"
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"
    CANCELLED = "cancelled"


class AttemptStatus(Enum):
    RUNNING = "running"
    SUCCEEDED = "succeeded"
    FAILED = "failed"


class DeliveryStatus(Enum):
    PENDING = "pending"
    DELIVERED = "delivered"


@dataclass(frozen=True, slots=True)
class WorkFailure:
    code: str

    def __post_init__(self) -> None:
        if not isinstance(self.code, str):
            raise TypeError("WorkFailure.code requires str")
        if not self.code or self.code.isspace():
            raise ValueError("WorkFailure.code must not be blank")


@dataclass(frozen=True, slots=True)
class WorkAttempt:
    number: int
    status: AttemptStatus
    started_at: datetime
    completed_at: datetime | None
    capability_name: str
    capability_revision: str
    failure: WorkFailure | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.number, int) or isinstance(self.number, bool):
            raise TypeError("WorkAttempt.number requires int")
        if self.number < 1:
            raise ValueError("WorkAttempt.number must be positive")
        if not isinstance(self.status, AttemptStatus):
            raise TypeError("WorkAttempt.status requires AttemptStatus")
        if not isinstance(self.started_at, datetime):
            raise TypeError("WorkAttempt.started_at requires datetime")
        if self.completed_at is not None and not isinstance(self.completed_at, datetime):
            raise TypeError("WorkAttempt.completed_at requires datetime")
        if not isinstance(self.capability_name, str):
            raise TypeError("WorkAttempt.capability_name requires str")
        if not self.capability_name or self.capability_name.isspace():
            raise ValueError("WorkAttempt.capability_name must not be blank")
        if not isinstance(self.capability_revision, str):
            raise TypeError("WorkAttempt.capability_revision requires str")
        if not self.capability_revision or self.capability_revision.isspace():
            raise ValueError("WorkAttempt.capability_revision must not be blank")
        if self.failure is not None and not isinstance(self.failure, WorkFailure):
            raise TypeError("WorkAttempt.failure requires WorkFailure")


@dataclass(frozen=True, slots=True)
class WorkRecord:
    identity: WorkId
    module: ModuleId
    computation: ComputationId
    status: WorkStatus
    submitted_at: datetime
    not_before: datetime | None
    priority: int
    maximum_attempts: int
    attempts: tuple[WorkAttempt, ...]
    completed_at: datetime | None = None
    failure: WorkFailure | None = None
    delivery: DeliveryStatus | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.identity, WorkId):
            raise TypeError("WorkRecord.identity requires WorkId")
        if not isinstance(self.module, ModuleId):
            raise TypeError("WorkRecord.module requires ModuleId")
        if not isinstance(self.computation, ComputationId):
            raise TypeError("WorkRecord.computation requires ComputationId")
        if not isinstance(self.status, WorkStatus):
            raise TypeError("WorkRecord.status requires WorkStatus")
        if not isinstance(self.submitted_at, datetime):
            raise TypeError("WorkRecord.submitted_at requires datetime")
        if self.not_before is not None and not isinstance(self.not_before, datetime):
            raise TypeError("WorkRecord.not_before requires datetime")
        if not isinstance(self.priority, int) or isinstance(self.priority, bool):
            raise TypeError("WorkRecord.priority requires int")
        if not isinstance(self.maximum_attempts, int) or isinstance(self.maximum_attempts, bool):
            raise TypeError("WorkRecord.maximum_attempts requires int")
        if not isinstance(self.attempts, tuple):
            raise TypeError("WorkRecord.attempts requires tuple")
        if any(not isinstance(attempt, WorkAttempt) for attempt in self.attempts):
            raise TypeError("WorkRecord.attempts accepts only WorkAttempt")
        if self.completed_at is not None and not isinstance(self.completed_at, datetime):
            raise TypeError("WorkRecord.completed_at requires datetime")
        if self.failure is not None and not isinstance(self.failure, WorkFailure):
            raise TypeError("WorkRecord.failure requires WorkFailure")
        if self.delivery is not None and not isinstance(self.delivery, DeliveryStatus):
            raise TypeError("WorkRecord.delivery requires DeliveryStatus")

    @property
    def attempts_used(self) -> int:
        return len(self.attempts)
