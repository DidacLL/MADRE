"""Submission and inspection contracts for bounded runtime work."""

from datetime import UTC, datetime
from typing import Literal

from pydantic import AwareDatetime, Field, JsonValue, field_validator

from madre.config import StrictModel


class ExecutionConstraints(StrictModel):
    timeout_seconds: float = Field(default=120, gt=0, le=3600, allow_inf_nan=False)
    local_only: bool = True


class WorkSubmission(StrictModel):
    application_id: str = Field(min_length=1)
    capability_id: str = Field(min_length=1)
    input: dict[str, JsonValue]
    eligible_at: AwareDatetime | None = None
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)

    @field_validator("eligible_at")
    @classmethod
    def utc_time(cls, value: datetime | None) -> datetime | None:
        return value.astimezone(UTC) if value is not None else None


class WorkRetryRequest(StrictModel):
    allow_unknown_outcome: bool = False


WorkStatus = Literal["accepted", "running", "succeeded", "failed", "cancelled"]
AttemptStatus = Literal["running", "succeeded", "failed"]
CancellationDisposition = Literal["prevented", "requested_while_running"]


class WorkFailure(StrictModel):
    code: str = Field(min_length=1)
    message: str = Field(min_length=1)


class WorkCancellation(StrictModel):
    requested_at: AwareDatetime
    disposition: CancellationDisposition


class WorkRetry(StrictModel):
    number: int = Field(ge=1)
    requested_at: AwareDatetime
    allow_unknown_outcome: bool = False
    previous_completed_at: AwareDatetime
    previous_failure: WorkFailure


class WorkAttempt(StrictModel):
    number: int = Field(ge=1)
    retry_number: int | None = Field(default=None, ge=1)
    status: AttemptStatus
    started_at: AwareDatetime
    completed_at: AwareDatetime | None = None
    result: dict[str, JsonValue] | None = None
    failure: WorkFailure | None = None


class WorkRecord(StrictModel):
    id: str = Field(min_length=1)
    submission: WorkSubmission
    status: WorkStatus
    submitted_at: AwareDatetime
    started_at: AwareDatetime | None = None
    completed_at: AwareDatetime | None = None
    result: dict[str, JsonValue] | None = None
    failure: WorkFailure | None = None
    cancellation: WorkCancellation | None = None
    retries: list[WorkRetry] = Field(default_factory=list)
    attempts: list[WorkAttempt] = Field(default_factory=list)
