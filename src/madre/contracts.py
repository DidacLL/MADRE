"""Runtime lifecycle contracts; private Material payloads remain transient."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Literal, Self

from pydantic import AwareDatetime, Field, JsonValue, field_validator, model_validator

from madre_sdk.execution import (
    CapabilityQuery,
    ExecutionBoundary,
    ExecutionConstraints,
)
from madre_sdk.material import MaterialHandle, MaterialSpecification
from madre_sdk.security import FrozenValue, Identifier, ScopeIdentity


class CorrelationEntry(FrozenValue):
    key: Identifier
    value: Identifier


class WorkSubmission(FrozenValue):
    originator: ScopeIdentity
    capability: CapabilityQuery
    material: MaterialHandle
    output: MaterialSpecification
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
    correlation: tuple[CorrelationEntry, ...] = ()

    @field_validator("eligible_at")
    @classmethod
    def utc_time(cls, value: datetime | None) -> datetime | None:
        return value.astimezone(UTC) if value is not None else None

    @model_validator(mode="after")
    def unique_correlation_keys(self) -> Self:
        keys = tuple(entry.key for entry in self.correlation)
        if len(keys) != len(set(keys)):
            raise ValueError("correlation keys must be unique")
        return self


class WorkSpec(FrozenValue):
    originator: ScopeIdentity
    capability: CapabilityQuery
    material: MaterialHandle
    output: MaterialSpecification
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
    correlation: tuple[CorrelationEntry, ...] = ()


class WorkFailure(FrozenValue):
    code: Identifier


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


class ResultEvidence(FrozenValue):
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
    result: ResultEvidence | None = None


class WorkRetryRequest(FrozenValue):
    allow_unknown_outcome: bool = False


class TransientDiagnostic(FrozenValue):
    """Runtime-only failure detail; never part of Material or algebra state."""

    code: Identifier
    detail: str = ""


JsonPayload = JsonValue
