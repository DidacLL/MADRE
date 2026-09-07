"""Replaceable physical computation backends and deterministic selection."""

from __future__ import annotations

import asyncio
import time
from collections.abc import Awaitable, Callable
from typing import Protocol

import httpx
from pydantic import Field, JsonValue

from madre.contracts import CapabilityRequest, ExecutionConstraints
from madre.security import (
    BoundaryRequirements,
    ExecutionBoundary,
    FrozenModel,
    SecurityEnvelope,
)


class CapabilityError(RuntimeError):
    def __init__(self, code: str, message: str) -> None:
        self.code = code
        super().__init__(message)


class CapabilityDescriptor(FrozenModel):
    id: str = Field(min_length=1)
    kind: str = Field(min_length=1)
    modality: str = Field(min_length=1)
    model_id: str | None = Field(default=None, min_length=1)
    execution_boundary: ExecutionBoundary
    heavyweight: bool = False
    requirements: BoundaryRequirements
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
        self._adapters[descriptor.id] = adapter

    def select(self, request: CapabilityRequest) -> CapabilityAdapter | None:
        if request.capability_id is not None:
            adapter = self._adapters.get(request.capability_id)
            if adapter is None or not self._compatible(adapter.descriptor, request):
                return None
            return adapter
        candidates = [
            adapter
            for adapter in self._adapters.values()
            if self._compatible(adapter.descriptor, request)
        ]
        if not candidates:
            return None
        return sorted(
            candidates,
            key=lambda item: (
                item.descriptor.execution_boundary != "local",
                item.descriptor.id,
            ),
        )[0]

    @staticmethod
    def _compatible(descriptor: CapabilityDescriptor, request: CapabilityRequest) -> bool:
        return (
            descriptor.kind == request.kind
            and descriptor.modality == request.modality
            and (request.model_id is None or descriptor.model_id == request.model_id)
        )


class FunctionCapability:
    """Small deterministic adapter useful for embedded capabilities and tests."""

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


class OpenAICompatibleChatCapability:
    """Provider-specific chat dialect kept at the capability boundary, outside Kernel."""

    def __init__(self, descriptor: CapabilityDescriptor, endpoint: str, model: str) -> None:
        self._descriptor = descriptor
        self._endpoint = endpoint.rstrip("/")
        self._model = model

    @property
    def descriptor(self) -> CapabilityDescriptor:
        return self._descriptor

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue:
        if not isinstance(payload, dict):
            raise CapabilityError("invalid_input", "chat adapter expects a JSON object")
        started = time.monotonic()
        request = {"model": self._model, **payload, "stream": False}
        try:
            async with asyncio.timeout(constraints.timeout_seconds):
                async with httpx.AsyncClient(
                    trust_env=False,
                    follow_redirects=False,
                    timeout=constraints.timeout_seconds,
                ) as client:
                    response = await client.post(f"{self._endpoint}/chat/completions", json=request)
                    response.raise_for_status()
                    parsed = response.json()
        except (TimeoutError, httpx.TimeoutException) as exc:
            raise CapabilityError("timeout", "capability exceeded the time limit") from exc
        except httpx.HTTPStatusError as exc:
            message = f"capability returned HTTP {exc.response.status_code}"
            raise CapabilityError("http_status", message) from exc
        except httpx.RequestError as exc:
            raise CapabilityError("connection", "could not communicate with capability") from exc
        if not isinstance(parsed, dict):
            raise CapabilityError("invalid_response", "capability returned non-object JSON")
        return {"provider_response": parsed, "elapsed_seconds": time.monotonic() - started}
