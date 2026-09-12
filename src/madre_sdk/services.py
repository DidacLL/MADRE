"""Segregated Module-facing service ports."""

from __future__ import annotations

from typing import Protocol

from pydantic import JsonValue

from madre_sdk.execution import ExecutionRequest
from madre_sdk.material import Material


class ExecutionService(Protocol):
    async def execute(self, request: ExecutionRequest) -> Material[JsonValue]: ...


class ModuleServices:
    """The capabilities granted to one Module behavior, with no semantic helpers."""

    def __init__(self, execution: ExecutionService) -> None:
        self._execution = execution

    async def execute(self, request: ExecutionRequest) -> Material[JsonValue]:
        return await self._execution.execute(request)
