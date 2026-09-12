"""Small behavior ports shared by independent Module implementations."""

from __future__ import annotations

from typing import Protocol

from madre_sdk.definitions import ModuleDefinition, OperationCall
from madre_sdk.execution import PhysicalResult, WorkRequest
from madre_sdk.material import Material, MaterialSet


class ExecutionService(Protocol):
    async def submit[OutputT](self, request: WorkRequest[OutputT]) -> PhysicalResult[OutputT]: ...


class OperationImplementation[OutputT](Protocol):
    async def execute(self, call: OperationCall) -> Material[OutputT]: ...


class ModuleDirectory(Protocol):
    async def reachable(self, materials: MaterialSet) -> tuple[ModuleDefinition, ...]: ...
