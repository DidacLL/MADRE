"""Replaceable physical computation backends and deterministic selection."""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import Protocol

from pydantic import JsonValue

from madre.contracts import CapabilityRequest, ExecutionConstraints
from madre.security import ExecutionBoundary, FrozenModel, Identifier, SecurityEnvelope


class CapabilityError(RuntimeError):
    def __init__(self, code: str, message: str = "capability execution failed") -> None:
        self.code = code
        super().__init__(message)


class CapabilityDescriptor(FrozenModel):
    id: Identifier
    kind: Identifier
    modality: Identifier
    model_id: Identifier | None = None
    execution_boundary: ExecutionBoundary
    heavyweight: bool = False
    security: SecurityEnvelope


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
        if descriptor.security.subject != descriptor.id:
            raise ValueError("Capability security envelope subject must equal capability id")
        if not descriptor.security.verify_integrity():
            raise ValueError("Capability security envelope integrity is invalid")
        self._adapters[descriptor.id] = adapter

    def candidates(
        self,
        request: CapabilityRequest,
        constraints: ExecutionConstraints,
    ) -> tuple[CapabilityAdapter, ...]:
        if request.capability_id is not None:
            adapter = self._adapters.get(request.capability_id)
            if (
                adapter is None
                or not self._compatible(adapter.descriptor, request)
                or (constraints.local_only and adapter.descriptor.execution_boundary != "local")
            ):
                return ()
            return (adapter,)
        return tuple(
            sorted(
                (
                    adapter
                    for adapter in self._adapters.values()
                    if self._compatible(adapter.descriptor, request)
                    and (
                        not constraints.local_only
                        or adapter.descriptor.execution_boundary == "local"
                    )
                ),
                key=lambda item: (
                    item.descriptor.execution_boundary != "local",
                    item.descriptor.id,
                ),
            )
        )

    def select(
        self,
        request: CapabilityRequest,
        constraints: ExecutionConstraints,
    ) -> CapabilityAdapter | None:
        candidates = self.candidates(request, constraints)
        return candidates[0] if candidates else None

    @staticmethod
    def _compatible(descriptor: CapabilityDescriptor, request: CapabilityRequest) -> bool:
        return (
            descriptor.kind == request.kind
            and descriptor.modality == request.modality
            and (request.model_id is None or descriptor.model_id == request.model_id)
        )


class FunctionCapability:
    """Deterministic local/embedded capability adapter, also useful in tests."""

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
