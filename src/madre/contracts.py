"""Public execution contracts. Durable records deliberately exclude content bytes."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Annotated, Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, JsonValue, field_validator

from madre.security import SecurityEnvelope


class FrozenModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class ExecutionConstraints(FrozenModel):
    timeout_seconds: float = Field(default=120, gt=0, le=3600, allow_inf_nan=False)
    local_only: bool = True


CorrelationValue = Annotated[
    str,
    Field(
        min_length=1,
        max_length=160,
        pattern=r"^[A-Za-z0-9][A-Za-z0-9._:/@-]*$",
    ),
]


class CapabilityRequest(FrozenModel):
    capability_id: str | None = Field(default=None, min_length=1)
    kind: str = Field(min_length=1)
    modality: str = Field(default="text", min_length=1)
    model_id: str | None = Field(default=None, min_length=1)


class ImmediateMaterial(FrozenModel):
    kind: Literal["immediate"] = "immediate"
    reference: str = Field(min_length=1)
    payload: JsonValue
    envelope: SecurityEnvelope


class DelayedMaterial(FrozenModel):
    kind: Literal["delayed"] = "delayed"
    reference: str = Field(min_length=1)
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    envelope: SecurityEnvelope


ExecutionMaterial = Annotated[ImmediateMaterial | DelayedMaterial, Field(discriminator="kind")]


class WorkSubmission(FrozenModel):
    originator: str = Field(min_length=1)
    requester_envelope: SecurityEnvelope
    capability: CapabilityRequest
    material: ExecutionMaterial
    eligible_at: AwareDatetime | None = None
    priority: int = Field(default=0, ge=-100, le=100)
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
    correlation: dict[str, CorrelationValue] = Field(default_factory=dict)

    @field_validator("eligible_at")
    @classmethod
    def utc_time(cls, value: datetime | None) -> datetime | None:
        return value.astimezone(UTC) if value is not None else None


class WorkSpec(FrozenModel):
    originator: str
    requester_envelope: SecurityEnvelope
    capability: CapabilityRequest
    material_reference: str
    input_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    material_envelope: SecurityEnvelope
    eligible_at: AwareDatetime | None = None
    priority: int = Field(default=0, ge=-100, le=100)
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
    correlation: dict[str, CorrelationValue] = Field(default_factory=dict)


class WorkFailure(FrozenModel):
    code: str = Field(min_length=1)
    message: str = Field(min_length=1)


class WorkCancellation(FrozenModel):
    requested_at: AwareDatetime
    disposition: Literal["prevented", "requested_while_running"]


class WorkRetry(FrozenModel):
    number: int = Field(ge=1)
    requested_at: AwareDatetime
    allow_unknown_outcome: bool = False
    previous_completed_at: AwareDatetime
    previous_failure: WorkFailure


class WorkAttempt(FrozenModel):
    number: int = Field(ge=1)
    retry_number: int | None = Field(default=None, ge=1)
    status: Literal["running", "succeeded", "failed"]
    started_at: AwareDatetime
    completed_at: AwareDatetime | None = None
    capability_id: str | None = None
    output_digest: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")
    output_size: int | None = Field(default=None, ge=0)
    failure: WorkFailure | None = None


class ResultEvidence(FrozenModel):
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    size: int = Field(ge=0)
    produced_at: AwareDatetime
    delivery_status: Literal["awaiting_consumption", "consumed", "lost"]


WorkStatus = Literal["accepted", "running", "succeeded", "failed", "cancelled"]


class WorkRecord(FrozenModel):
    id: str = Field(min_length=1)
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


class WorkRetryRequest(FrozenModel):
    allow_unknown_outcome: bool = False
