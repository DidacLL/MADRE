"""Submission vocabulary and constraints for bounded runtime work.

ExecutionConstraints is enforced by the inference adapter. WorkSubmission is the
starting vocabulary for immediate/delayed work; its public API is not implemented yet.
"""

from datetime import UTC, datetime

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
