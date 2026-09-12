"""Runtime lifecycle contracts; private Material payloads remain transient."""

from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from typing import Literal

from pydantic import AwareDatetime, Field, field_validator

from madre_sdk.execution import (
    CapabilityQuery,
    ExecutionBoundary,
    ExecutionConstraints,
)
from madre_sdk.material import MaterialHandle, MaterialSpecification
from madre_sdk.security import FrozenValue, Identifier, ScopeIdentity


class WorkSubmission(FrozenValue):
    originator: ScopeIdentity
    capability: CapabilityQuery
    material: MaterialHandle
    output: MaterialSpecification
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)

    @field_validator("eligible_at")
    @classmethod
    def utc_time(cls, value: datetime | None) -> datetime | None:
        return value.astimezone(UTC) if value is not None else None


class WorkSpec(FrozenValue):
    originator: ScopeIdentity
    capability: CapabilityQuery
    material: MaterialHandle
    output: MaterialSpecification
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)


class RetryDisposition(Enum):
    RETRYABLE = "retryable"
    UNKNOWN_OUTCOME = "unknown_outcome"
    TERMINAL = "terminal"


class WorkFailure(FrozenValue):
    code: Identifier
    retry: RetryDisposition


class WorkCancellation(FrozenValue):
    requested_at: AwareDatetime
    disposition: Literal["prevented", "requested_while_running"]


class WorkRetry(FrozenValue):
    number: int = Field(ge=1)
    requested_at: AwareDatetime
    allow_unknown_outcome: bool = False
    previous_completed_at: AwareDatetime
    previous_failure: WorkFailure


class WorkAttempt(FrozenValue):
    number: int = Field(ge=1)
    retry_number: int | None = Field(default=None, ge=1)
    status: Literal["running", "succeeded", "failed"]
    started_at: AwareDatetime
    completed_at: AwareDatetime | None = None
    capability: ScopeIdentity | None = None
    execution_boundary: ExecutionBoundary | None = None
    output_digest: str | None = None
    output_size: int | None = Field(default=None, ge=0)
    failure: WorkFailure | None = None


class ResultMetadata(FrozenValue):
    digest: str
    size: int = Field(ge=0)
    produced_at: AwareDatetime
    delivery_status: Literal["awaiting_consumption", "consumed", "lost"]


WorkStatus = Literal["accepted", "running", "succeeded", "failed", "cancelled"]


class WorkRecord(FrozenValue):
    id: Identifier
    spec: WorkSpec
    status: WorkStatus
    submitted_at: AwareDatetime
    started_at: AwareDatetime | None = None
    completed_at: AwareDatetime | None = None
    failure: WorkFailure | None = None
    cancellation: WorkCancellation | None = None
    retries: tuple[WorkRetry, ...] = ()
    attempts: tuple[WorkAttempt, ...] = ()
    result: ResultMetadata | None = None


class WorkRetryRequest(FrozenValue):
    allow_unknown_outcome: bool = False
