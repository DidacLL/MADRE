"""Language-neutral public execution contracts; durable records contain no private bytes."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Literal

from pydantic import AwareDatetime, Field, JsonValue, field_validator, model_validator

from madre.security import (
    ExecutionBoundary,
    FrozenModel,
    Identifier,
    OrdinarySecurityLevel,
    SecurityHistory,
    SecurityObject,
)

LatencyClass = Literal["interactive", "standard", "batch"]
ReasoningEffort = Literal["low", "medium", "high"]
QualityTier = Literal["basic", "standard", "high"]
CostPolicy = Literal["free_only", "paid_allowed"]
LocalityRequirement = Literal["local_only", "non_remote", "any"]
PreferenceDimension = Literal[
    "mechanism",
    "model",
    "provider",
    "cost",
    "execution_boundary",
    "latency",
    "reasoning_effort",
    "quality",
]


class ExecutionConstraints(FrozenModel):
    timeout_seconds: float = Field(default=120, gt=0, allow_inf_nan=False)


class CorrelationEntry(FrozenModel):
    key: Identifier
    value: Identifier


class InferenceHardRequirements(FrozenModel):
    specialization: Identifier
    modality: Identifier = "text"
    latency_class: LatencyClass | None = None
    reasoning_effort: ReasoningEffort | None = None
    quality_tier: QualityTier | None = None
    cost_policy: CostPolicy = "paid_allowed"
    locality: LocalityRequirement = "any"
    required_resources: frozenset[Identifier] = Field(default_factory=frozenset)
    provider_id: Identifier | None = None
    model_id: Identifier | None = None
    mechanism_id: Identifier | None = None


class InferencePreferences(FrozenModel):
    mechanism_ids: tuple[Identifier, ...] = ()
    model_ids: tuple[Identifier, ...] = ()
    provider_ids: tuple[Identifier, ...] = ()
    prefer_free: bool = False
    execution_boundaries: tuple[ExecutionBoundary, ...] = ()
    latency_classes: tuple[LatencyClass, ...] = ()
    reasoning_efforts: tuple[ReasoningEffort, ...] = ()
    quality_tiers: tuple[QualityTier, ...] = ()


class FallbackPolicy(FrozenModel):
    allow_unlisted: bool = True
    preference_order: tuple[PreferenceDimension, ...] = (
        "mechanism",
        "model",
        "provider",
        "cost",
        "execution_boundary",
        "latency",
        "reasoning_effort",
        "quality",
    )

    @model_validator(mode="after")
    def unique_dimensions(self) -> FallbackPolicy:
        if len(self.preference_order) != len(set(self.preference_order)):
            raise ValueError("preference_order dimensions must be unique")
        return self


class InferenceRequirement(FrozenModel):
    hard: InferenceHardRequirements
    preferences: InferencePreferences = Field(default_factory=InferencePreferences)
    fallback: FallbackPolicy = Field(default_factory=FallbackPolicy)


class TransientMaterial(FrozenModel):
    reference: Identifier
    payload: JsonValue
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    security: SecurityObject
    history: SecurityHistory = Field(default_factory=SecurityHistory)

    @model_validator(mode="after")
    def security_matches_reference(self) -> TransientMaterial:
        if self.security.subject_ref.subject_kind not in {"artifact", "context_bundle"}:
            raise ValueError("transient material requires artifact/context_bundle security")
        if self.security.subject_ref.local_id != self.reference:
            raise ValueError("material SecurityObject subject must equal material reference")
        if not self.security.verify_binding():
            raise ValueError("material SecurityObject binding is invalid")
        if self.security.evidence_value("content_digest") != self.digest:
            raise ValueError("material SecurityObject must bind the material digest")
        if self.history.resolve(self.security.security_id) is None:
            raise ValueError("material history must contain its SecurityObject")
        return self

    def to_handle(self, *, coordination: str | None = None) -> MaterialHandle:
        return MaterialHandle(
            reference=self.reference,
            digest=self.digest,
            security=self.security,
            history=self.history,
            coordination=coordination,
        )


class MaterialHandle(FrozenModel):
    reference: Identifier
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    security: SecurityObject
    history: SecurityHistory = Field(default_factory=SecurityHistory)
    coordination: Identifier | None = None

    @model_validator(mode="after")
    def security_matches_reference(self) -> MaterialHandle:
        if self.security.subject_ref.subject_kind not in {"artifact", "context_bundle"}:
            raise ValueError("MaterialHandle requires artifact/context_bundle security")
        if self.security.subject_ref.local_id != self.reference:
            raise ValueError("MaterialHandle SecurityObject subject must equal material reference")
        if not self.security.verify_binding():
            raise ValueError("MaterialHandle SecurityObject binding is invalid")
        if self.security.evidence_value("content_digest") != self.digest:
            raise ValueError("MaterialHandle SecurityObject must bind the material digest")
        if self.history.resolve(self.security.security_id) is None:
            raise ValueError("material history must contain its SecurityObject")
        return self


class WorkSubmission(FrozenModel):
    originator: Identifier
    security: SecurityHistory
    inference: InferenceRequirement
    material: MaterialHandle
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
    correlation: tuple[CorrelationEntry, ...] = ()

    @field_validator("eligible_at")
    @classmethod
    def utc_time(cls, value: datetime | None) -> datetime | None:
        return value.astimezone(UTC) if value is not None else None

    @model_validator(mode="after")
    def unique_correlation_keys(self) -> WorkSubmission:
        keys = [entry.key for entry in self.correlation]
        if len(keys) != len(set(keys)):
            raise ValueError("correlation keys must be unique")
        return self


class WorkSpec(FrozenModel):
    originator: Identifier
    security: SecurityHistory
    inference: InferenceRequirement
    material: MaterialHandle
    eligible_at: AwareDatetime | None = None
    priority: int = 0
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)
    correlation: tuple[CorrelationEntry, ...] = ()


class TransientInferenceRequest(FrozenModel):
    originator: Identifier
    security: SecurityHistory
    inference: InferenceRequirement
    material: TransientMaterial
    constraints: ExecutionConstraints = Field(default_factory=ExecutionConstraints)


class TransientInferenceResult(FrozenModel):
    payload: JsonValue
    capability_id: Identifier
    provider_id: Identifier | None = None
    model_id: Identifier | None = None
    execution_boundary: ExecutionBoundary
    output_digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    output_size: int = Field(ge=0)
    output_integrity: OrdinarySecurityLevel | None
    producer_security_ids: tuple[Identifier, ...]
    source_security_ids: tuple[Identifier, ...]
    security: SecurityHistory


class WorkFailure(FrozenModel):
    code: Identifier


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
    capability_id: Identifier | None = None
    provider_id: Identifier | None = None
    model_id: Identifier | None = None
    execution_boundary: ExecutionBoundary | None = None
    security_transition_id: Identifier | None = None
    output_digest: str | None = Field(default=None, pattern=r"^[0-9a-f]{64}$")
    output_size: int | None = Field(default=None, ge=0)
    failure: WorkFailure | None = None


class ResultEvidence(FrozenModel):
    digest: str = Field(pattern=r"^[0-9a-f]{64}$")
    size: int = Field(ge=0)
    produced_at: AwareDatetime
    delivery_status: Literal["awaiting_consumption", "consumed", "lost"]
    output_integrity: OrdinarySecurityLevel | None
    producer_security_ids: tuple[Identifier, ...]
    source_security_ids: tuple[Identifier, ...]


WorkStatus = Literal["accepted", "running", "succeeded", "failed", "cancelled"]


class WorkRecord(FrozenModel):
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


class WorkRetryRequest(FrozenModel):
    allow_unknown_outcome: bool = False
