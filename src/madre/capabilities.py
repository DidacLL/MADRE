"""Replaceable physical computation backends and deterministic selection."""

from __future__ import annotations

import asyncio
import ipaddress
import time
from collections.abc import Awaitable, Callable
from typing import Protocol
from urllib.parse import urlsplit

import httpx
from pydantic import JsonValue

from madre.contracts import CapabilityRequest, ExecutionConstraints
from madre.security import (
    BoundaryRequirements,
    ExecutionBoundary,
    FrozenModel,
    Identifier,
    SecurityEnvelope,
)


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
        candidates = [
            adapter
            for adapter in self._adapters.values()
            if self._compatible(adapter.descriptor, request)
            and (not constraints.local_only or adapter.descriptor.execution_boundary == "local")
        ]
        return tuple(
            sorted(
                candidates,
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


def _literal_loopback(endpoint: str) -> bool:
    hostname = urlsplit(endpoint).hostname or ""
    try:
        return ipaddress.ip_address(hostname).is_loopback
    except ValueError:
        return False


class OpenAICompatibleChatCapability:
    """Provider-specific chat dialect kept at the capability boundary, outside Kernel."""

    def __init__(self, descriptor: CapabilityDescriptor, endpoint: str, model: str) -> None:
        parsed = urlsplit(endpoint)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("endpoint must be an HTTP(S) base URL")
        if descriptor.execution_boundary == "local" and not _literal_loopback(endpoint):
            raise ValueError("local capabilities require a literal loopback endpoint")
        self._descriptor = descriptor
        self._endpoint = endpoint.rstrip("/")
        self._model = model

    @property
    def descriptor(self) -> CapabilityDescriptor:
        return self._descriptor

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue:
        if not isinstance(payload, dict):
            raise CapabilityError("invalid_input")
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
            raise CapabilityError("timeout") from exc
        except httpx.HTTPStatusError as exc:
            raise CapabilityError("http_status") from exc
        except httpx.RequestError as exc:
            raise CapabilityError("connection") from exc
        if not isinstance(parsed, dict):
            raise CapabilityError("invalid_response")
        return {"provider_response": parsed, "elapsed_seconds": time.monotonic() - started}
