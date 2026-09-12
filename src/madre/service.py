"""Local HTTP transport over execution and declarative catalog contracts."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Response
from pydantic import JsonValue

from madre.adapters.openai import OpenAICompatibleChatCapability
from madre.capabilities import CapabilityRegistry
from madre.catalog import ModuleCatalog
from madre.config import Settings
from madre.contracts import WorkRecord, WorkRetryRequest, WorkSubmission
from madre.runtime import (
    CancellationConflict,
    ExecutionUnavailable,
    IdempotencyConflict,
    Kernel,
    ResultLost,
    ResultUnavailable,
    RetryConflict,
    WorkNotFound,
)
from madre.storage import PlatformStore, open_database
from madre_sdk.execution import ExecutionRequest
from madre_sdk.material import Material
from madre_sdk.security import SecurityMismatch
from madre_sdk.semantic import ModuleDefinition


def _capabilities(settings: Settings) -> CapabilityRegistry:
    registry = CapabilityRegistry()
    for installed in settings.capabilities:
        registry.register(OpenAICompatibleChatCapability(installed.definition, installed.adapter))
    return registry


def create_app(settings: Settings) -> FastAPI:
    state: dict[str, object] = {}

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        with open_database(settings.data_dir) as connection:
            store = PlatformStore(connection)
            catalog = ModuleCatalog(store)
            kernel = Kernel(_capabilities(settings), store)
            state["kernel"] = kernel
            state["catalog"] = catalog
            scheduler = asyncio.create_task(kernel.run_scheduler())
            try:
                yield
            finally:
                scheduler.cancel()
                try:
                    await scheduler
                except asyncio.CancelledError:
                    pass
                state.clear()

    app = FastAPI(title="MADRE", lifespan=lifespan)

    def kernel() -> Kernel:
        value = state.get("kernel")
        if not isinstance(value, Kernel):
            raise HTTPException(status_code=503, detail="runtime unavailable")
        return value

    def catalog() -> ModuleCatalog:
        value = state.get("catalog")
        if not isinstance(value, ModuleCatalog):
            raise HTTPException(status_code=503, detail="catalog unavailable")
        return value

    @app.post("/v1/modules", response_model=ModuleDefinition)
    async def register_module(definition: ModuleDefinition) -> ModuleDefinition:
        catalog().register(definition)
        return definition

    @app.post("/v1/executions", response_model=Material[JsonValue])
    async def execute(request: ExecutionRequest) -> Material[JsonValue]:
        try:
            return await kernel().execute(request)
        except ExecutionUnavailable as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except SecurityMismatch as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

    @app.post("/v1/work", response_model=WorkRecord, status_code=202)
    async def submit_work(
        submission: WorkSubmission,
        response: Response,
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ) -> WorkRecord:
        try:
            record = await kernel().submit(submission, idempotency_key=idempotency_key)
        except IdempotencyConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        response.headers["Location"] = f"/v1/work/{record.id}"
        return record

    @app.get("/v1/work/{work_id}", response_model=WorkRecord)
    async def inspect_work(work_id: str) -> WorkRecord:
        record = kernel().inspect(work_id)
        if record is None:
            raise HTTPException(status_code=404, detail="work not found")
        return record

    @app.post("/v1/work/{work_id}/cancel", response_model=WorkRecord)
    async def cancel_work(work_id: str) -> WorkRecord:
        try:
            return await kernel().cancel(work_id)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except CancellationConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    @app.post("/v1/work/{work_id}/retry", response_model=WorkRecord)
    async def retry_work(
        work_id: str,
        request: WorkRetryRequest,
        idempotency_key: str = Header(alias="Idempotency-Key"),
    ) -> WorkRecord:
        try:
            return await kernel().retry(work_id, request, idempotency_key=idempotency_key)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except RetryConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    @app.post("/v1/work/{work_id}/result", response_model=Material[JsonValue])
    async def consume_result(work_id: str) -> Material[JsonValue]:
        try:
            return kernel().consume_result(work_id)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except ResultLost as exc:
            raise HTTPException(status_code=410, detail=str(exc)) from exc
        except ResultUnavailable as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    return app
