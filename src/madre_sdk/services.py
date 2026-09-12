"""Segregated Module-facing service ports."""

from __future__ import annotations

from typing import Protocol

from pydantic import JsonValue

from madre_sdk.execution import ExecutionRequest
from madre_sdk.material import Material


class ExecutionService(Protocol):
    async def execute(self, request: ExecutionRequest) -> Material[JsonValue]: ...
