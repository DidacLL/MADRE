"""Version-one wire vocabulary for the next work-execution implementation.

These types do not claim that submission or execution is implemented.
"""

from datetime import UTC, datetime
from typing import Literal

from pydantic import AwareDatetime, Field, JsonValue, field_validator

from madre.config import StrictModel

WorkState = Literal["queued", "running", "succeeded", "failed", "cancelled", "interrupted"]


class ExecutionConstraints(StrictModel):
    timeout_seconds: float = Field(default=120, gt=0, le=3600, allow_inf_nan=False)
    local_only: bool = True


class WorkSubmission(StrictModel):
    application_id: str = Field(min_length=1)
    capability_id: str = Field(min_length=1)
    input: dict[str, JsonValue]
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)

    @field_validator("eligible_at")
    @classmethod
    def utc_time(cls, value: datetime | None) -> datetime | None:
        return value.astimezone(UTC) if value is not None else None


class Failure(StrictModel):
    code: str
    message: str
    outcome_uncertain: bool = False


class AttemptEvidence(StrictModel):
    attempt_id: str
    capability_id: str
    endpoint: str
    model: str
    boundary: Literal["local", "remote"]
    started_at: AwareDatetime
    finished_at: AwareDatetime | None = None
    failure: Failure | None = None


class WorkSnapshot(StrictModel):
    work_id: str
    application_id: str
    state: WorkState
    accepted_at: AwareDatetime
    eligible_at: AwareDatetime
    result: JsonValue = None
    failure: Failure | None = None
    attempts: list[AttemptEvidence] = Field(default_factory=list)
