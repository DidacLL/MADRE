"""Runtime ports kept separate from declarative SDK definitions."""

from __future__ import annotations

from typing import Protocol

from pydantic import JsonValue

from madre.contracts import WorkRecord, WorkRetryRequest, WorkSubmission
from madre_sdk.execution import ExecutionRequest
from madre_sdk.material import Material, MaterialHandle
from madre_sdk.semantic import ModuleDefinition


class ModuleRegistration(Protocol):
    def register(self, definition: ModuleDefinition) -> None: ...


class TransientExecution(Protocol):
    async def execute(self, request: ExecutionRequest) -> Material[JsonValue]: ...


class DurableWorkSubmission(Protocol):
    async def submit(
        self, submission: WorkSubmission, *, idempotency_key: str | None = None
    ) -> WorkRecord: ...


class WorkInspection(Protocol):
    def inspect(self, work_id: str) -> WorkRecord | None: ...


class WorkResultAccess(Protocol):
    def consume_result(self, work_id: str) -> Material[JsonValue]: ...

    async def retry(
        self, work_id: str, request: WorkRetryRequest, *, idempotency_key: str
    ) -> WorkRecord: ...

    async def cancel(self, work_id: str) -> WorkRecord: ...


class MaterialResolution(Protocol):
    async def resolve(self, handle: MaterialHandle) -> Material[JsonValue] | None: ...
