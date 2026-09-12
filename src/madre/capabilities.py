"""Physical execution mechanisms and deterministic selection."""

from __future__ import annotations

import asyncio
import inspect
from collections.abc import Awaitable, Callable
from typing import Protocol

from pydantic import JsonValue

from madre.contracts import RetryDisposition
from madre_sdk.execution import (
    CapabilityDefinition,
    CapabilityQuery,
    ExecutionConstraints,
)
from madre_sdk.security import ScopeIdentity


class CapabilityError(RuntimeError):
    def __init__(
        self,
        code: str,
        message: str = "capability execution failed",
        *,
        retry: RetryDisposition = RetryDisposition.RETRYABLE,
    ) -> None:
        self.code = code
        self.retry = retry
        super().__init__(message)


class CapabilityAdapter(Protocol):
    @property
    def definition(self) -> CapabilityDefinition: ...

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue: ...


class CapabilitySelection(Protocol):
    def select(
        self,
        query: CapabilityQuery,
        capabilities: tuple[CapabilityAdapter, ...],
    ) -> CapabilityAdapter | None: ...


class RankedCapabilitySelection:
    """Selection by the typed query's declared preference order."""

    def select(
        self,
        query: CapabilityQuery,
        capabilities: tuple[CapabilityAdapter, ...],
    ) -> CapabilityAdapter | None:
        compatible = tuple(adapter for adapter in capabilities if query.accepts(adapter.definition))
        if not compatible:
            return None
        return min(
            compatible,
            key=lambda adapter: (*query.rank(adapter.definition), adapter.definition.identity.name),
        )


class CapabilityRegistry:
    def __init__(self, selection: CapabilitySelection | None = None) -> None:
        self._adapters: dict[ScopeIdentity, CapabilityAdapter] = {}
        self._selection = selection or RankedCapabilitySelection()

    def register(self, adapter: CapabilityAdapter) -> None:
        identity = adapter.definition.identity
        if identity in self._adapters:
            raise ValueError(f"duplicate Capability identity: {identity.name}")
        self._adapters[identity] = adapter

    def select(self, query: CapabilityQuery) -> CapabilityAdapter | None:
        return self._selection.select(query, tuple(self._adapters.values()))


class FunctionCapability:
    """A physical in-process mechanism with an injected implementation."""

    def __init__(
        self,
        definition: CapabilityDefinition,
        function: Callable[[JsonValue], JsonValue | Awaitable[JsonValue]],
    ) -> None:
        self._definition = definition
        self._function = function

    @property
    def definition(self) -> CapabilityDefinition:
        return self._definition

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue:
        async with asyncio.timeout(constraints.timeout_seconds):
            value = self._function(payload)
            if inspect.isawaitable(value):
                return await value
            return value
