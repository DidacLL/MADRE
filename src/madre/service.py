"""Local HTTP transport over MADRE execution and interoperability contracts."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Response

from madre.adapters.openai import OpenAICompatibleChatCapability
from madre.capabilities import CapabilityDescriptor, CapabilityRegistry
from madre.config import Settings
from madre.contracts import (
    TransientInferenceRequest,
    TransientInferenceResult,
    WorkRecord,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.registry import InteroperabilityRegistry, ModuleManifest
from madre.runtime import (
    CancellationConflict,
    IdempotencyConflict,
    ResultLost,
    ResultUnavailable,
    RetryConflict,
    TransientInferenceError,
    WorkNotFound,
    WorkRuntime,
)
from madre.security import CapabilitySecurityValues, SecurityObject
from madre.storage import PlatformStore, open_database


def _capabilities(settings: Settings) -> CapabilityRegistry:
    registry = CapabilityRegistry()
    for capability_id, config in settings.capabilities.items():
        security = SecurityObject.issue(
            subject_id=capability_id,
            subject_kind="capability",
            values=CapabilitySecurityValues(
                trust=config.trust,
                privacy=config.privacy,
                risk=config.risk,
            ),
            origin=f"adapter:{capability_id}",
            provenance=("openai-compatible",),
        )
        descriptor = CapabilityDescriptor(
            id=capability_id,
            specialization="model.inference.chat",
            modality="text",
            provider_id=config.provider_id,
            model_id=config.model,
            execution_boundary=config.boundary,
            latency_class=config.latency_class,
            supported_reasoning_efforts=config.reasoning_efforts,
            quality_tier=config.quality_tier,
            paid=config.paid,
            resources=config.resources,
            heavyweight=config.heavyweight,
            security=security,
        )
        registry.register(OpenAICompatibleChatCapability(descriptor, config))
    return registry


def create_app(settings: Settings) -> FastAPI:
    state: dict[str, object] = {}

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        with open_database(settings.data_dir) as connection:
            store = PlatformStore(connection)
            registry = InteroperabilityRegistry(store)
            runtime = WorkRuntime(store, _capabilities(settings))
            state["runtime"] = runtime
            state["registry"] = registry
            scheduler = asyncio.create_task(runtime.run_scheduler())
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

    def runtime() -> WorkRuntime:
        value = state.get("runtime")
        if not isinstance(value, WorkRuntime):
            raise HTTPException(status_code=503, detail="runtime unavailable")
        return value

    def interoperability() -> InteroperabilityRegistry:
        value = state.get("registry")
        if not isinstance(value, InteroperabilityRegistry):
            raise HTTPException(status_code=503, detail="registry unavailable")
        return value

    @app.post("/v1/registry/modules", response_model=ModuleManifest)
    async def register_module(manifest: ModuleManifest) -> ModuleManifest:
        interoperability().register(manifest)
        return manifest

    @app.post("/v1/inference", response_model=TransientInferenceResult)
    async def transient_inference(request: TransientInferenceRequest) -> TransientInferenceResult:
        try:
            return await runtime().infer(request)
        except TransientInferenceError as exc:
            raise HTTPException(status_code=422, detail=exc.code) from exc

    @app.post("/v1/work", response_model=WorkRecord, status_code=202)
    async def submit_work(
        submission: WorkSubmission,
        response: Response,
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ) -> WorkRecord:
        try:
            record = await runtime().submit(submission, idempotency_key=idempotency_key)
        except IdempotencyConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        response.headers["Location"] = f"/v1/work/{record.id}"
        return record

    @app.get("/v1/work/{work_id}", response_model=WorkRecord)
    async def inspect_work(work_id: str) -> WorkRecord:
        record = runtime().inspect(work_id)
        if record is None:
            raise HTTPException(status_code=404, detail="work not found")
        return record

    @app.post("/v1/work/{work_id}/cancel", response_model=WorkRecord)
    async def cancel_work(work_id: str) -> WorkRecord:
        try:
            return await runtime().cancel(work_id)
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
            return await runtime().retry(work_id, request, idempotency_key=idempotency_key)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except RetryConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

    @app.post("/v1/work/{work_id}/result")
    async def consume_result(work_id: str) -> object:
        try:
            return runtime().consume_result(work_id)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except ResultLost as exc:
            raise HTTPException(status_code=410, detail=str(exc)) from exc
        except ResultUnavailable as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    return app
