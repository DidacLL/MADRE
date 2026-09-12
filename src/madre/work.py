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
        if self.number < 1:
            raise ValueError("WorkAttempt.number must be positive")
        if not self.capability_name or self.capability_name.isspace():
            raise ValueError("WorkAttempt.capability_name must not be blank")


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

    @property
    def attempts_used(self) -> int:
        return len(self.attempts)
