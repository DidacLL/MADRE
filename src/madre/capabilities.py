"""Replaceable physical inference/execution mechanisms and deterministic selection."""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import Protocol

from pydantic import Field, JsonValue

from madre.contracts import (
    ExecutionConstraints,
    InferencePreferences,
    InferenceRequirement,
    LatencyClass,
    PreferenceDimension,
    QualityTier,
    ReasoningEffort,
)
from madre.security import (
    BoundarySecurityValues,
    CapabilitySecurityValues,
    ExecutionBoundary,
    FrozenModel,
    Identifier,
    SecurityObject,
)


class CapabilityError(RuntimeError):
    def __init__(self, code: str, message: str = "capability execution failed") -> None:
        self.code = code
        super().__init__(message)


class CapabilityDescriptor(FrozenModel):
    id: Identifier
    specialization: Identifier
    modality: Identifier
    provider_id: Identifier | None = None
    model_id: Identifier | None = None
    execution_boundary: ExecutionBoundary
    latency_class: LatencyClass = "standard"
    supported_reasoning_efforts: frozenset[ReasoningEffort] = frozenset({"low", "medium", "high"})
    quality_tier: QualityTier = "standard"
    paid: bool = False
    resources: frozenset[Identifier] = frozenset()
    heavyweight: bool = False
    disclosure_boundaries: tuple[SecurityObject, ...] = Field(min_length=1)
    security: SecurityObject


class CapabilityAdapter(Protocol):
    @property
    def descriptor(self) -> CapabilityDescriptor: ...

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue: ...


class CapabilityRegistry:
    def __init__(self) -> None:
        self._adapters: dict[str, CapabilityAdapter] = {}

    def register(self, adapter: CapabilityAdapter) -> None:
        descriptor = adapter.descriptor
        if descriptor.id in self._adapters:
            raise ValueError(f"duplicate capability id: {descriptor.id}")
        ref = descriptor.security.subject_ref
        if ref.subject_kind != "capability":
            raise ValueError("Capability requires capability SecurityObject")
        if ref.local_id != descriptor.id:
            raise ValueError("Capability SecurityObject subject must equal capability id")
        if not descriptor.security.verify_binding():
            raise ValueError("Capability SecurityObject binding is invalid")
        if not isinstance(descriptor.security.values, CapabilitySecurityValues):
            raise ValueError("Capability requires Privacy and Assurance values")
        values = descriptor.security.values
        if values.assurance is None:
            raise ValueError("Capability requires Privacy and Assurance values")
        for boundary in descriptor.disclosure_boundaries:
            if not boundary.verify_binding() or not isinstance(
                boundary.values, BoundarySecurityValues
            ):
                raise ValueError("Capability requires bound disclosure boundaries")
        self._adapters[descriptor.id] = adapter

    def candidates(self, request: InferenceRequirement) -> tuple[CapabilityAdapter, ...]:
        compatible = [
            adapter
            for adapter in self._adapters.values()
            if self._hard_compatible(adapter.descriptor, request)
            and self._fallback_compatible(adapter.descriptor, request)
        ]
        return tuple(sorted(compatible, key=lambda item: self._sort_key(item.descriptor, request)))

    def select(self, request: InferenceRequirement) -> CapabilityAdapter | None:
        candidates = self.candidates(request)
        return candidates[0] if candidates else None

    @staticmethod
    def _hard_compatible(descriptor: CapabilityDescriptor, request: InferenceRequirement) -> bool:
        hard = request.hard
        if descriptor.specialization != hard.specialization or descriptor.modality != hard.modality:
            return False
        if hard.latency_class is not None and descriptor.latency_class != hard.latency_class:
            return False
        if (
            hard.reasoning_effort is not None
            and hard.reasoning_effort not in descriptor.supported_reasoning_efforts
        ):
            return False
        if hard.quality_tier is not None and descriptor.quality_tier != hard.quality_tier:
            return False
        if hard.cost_policy == "free_only" and descriptor.paid:
            return False
        if hard.locality == "local_only" and descriptor.execution_boundary != "local":
            return False
        if hard.locality == "non_remote" and descriptor.execution_boundary == "remote":
            return False
        if not hard.required_resources.issubset(descriptor.resources):
            return False
        if hard.provider_id is not None and descriptor.provider_id != hard.provider_id:
            return False
        if hard.model_id is not None and descriptor.model_id != hard.model_id:
            return False
        return hard.mechanism_id is None or descriptor.id == hard.mechanism_id

    @classmethod
    def _fallback_compatible(
        cls, descriptor: CapabilityDescriptor, request: InferenceRequirement
    ) -> bool:
        if request.fallback.allow_unlisted:
            return True
        preferences = request.preferences
        checks = (
            (preferences.mechanism_ids, descriptor.id in preferences.mechanism_ids),
            (preferences.model_ids, descriptor.model_id in preferences.model_ids),
            (preferences.provider_ids, descriptor.provider_id in preferences.provider_ids),
            (
                preferences.execution_boundaries,
                descriptor.execution_boundary in preferences.execution_boundaries,
            ),
            (preferences.latency_classes, descriptor.latency_class in preferences.latency_classes),
            (
                preferences.reasoning_efforts,
                bool(set(preferences.reasoning_efforts) & descriptor.supported_reasoning_efforts),
            ),
            (preferences.quality_tiers, descriptor.quality_tier in preferences.quality_tiers),
        )
        return all(not values or matched for values, matched in checks)

    @classmethod
    def _sort_key(
        cls, descriptor: CapabilityDescriptor, request: InferenceRequirement
    ) -> tuple[int | str, ...]:
        return (
            *(
                cls._preference_rank(descriptor, request.preferences, dimension)
                for dimension in request.fallback.preference_order
            ),
            descriptor.id,
        )

    @staticmethod
    def _preference_rank(
        descriptor: CapabilityDescriptor,
        preferences: InferencePreferences,
        dimension: PreferenceDimension,
    ) -> int:
        values: tuple[object, ...]
        candidate: object
        if dimension == "mechanism":
            values, candidate = preferences.mechanism_ids, descriptor.id
        elif dimension == "model":
            values, candidate = preferences.model_ids, descriptor.model_id
        elif dimension == "provider":
            values, candidate = preferences.provider_ids, descriptor.provider_id
        elif dimension == "cost":
            return int(preferences.prefer_free and descriptor.paid)
        elif dimension == "execution_boundary":
            values, candidate = preferences.execution_boundaries, descriptor.execution_boundary
        elif dimension == "latency":
            values, candidate = preferences.latency_classes, descriptor.latency_class
        elif dimension == "quality":
            values, candidate = preferences.quality_tiers, descriptor.quality_tier
        else:
            values = preferences.reasoning_efforts
            if not values:
                return 0
            matching = [
                index
                for index, value in enumerate(values)
                if value in descriptor.supported_reasoning_efforts
            ]
            return min(matching, default=len(values) + 1)
        if not values:
            return 0
        try:
            return values.index(candidate)
        except ValueError:
            return len(values) + 1


class FunctionCapability:
    """Deterministic local/embedded mechanism adapter, also useful in tests."""

    def __init__(
        self,
        descriptor: CapabilityDescriptor,
        function: Callable[[JsonValue], JsonValue | Awaitable[JsonValue]],
    ) -> None:
        self._descriptor = descriptor
        self._function = function

    @property
    def descriptor(self) -> CapabilityDescriptor:
        return self._descriptor

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue:
        async with asyncio.timeout(constraints.timeout_seconds):
            value = self._function(payload)
            if isinstance(value, Awaitable):
                return await value
            return value
