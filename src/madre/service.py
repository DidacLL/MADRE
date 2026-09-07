"""Authenticated local HTTP transport over the architecture-neutral work runtime."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, Header, HTTPException, Response, status

from madre.capabilities import (
    CapabilityDescriptor,
    CapabilityRegistry,
    OpenAICompatibleChatCapability,
)
from madre.config import Settings
from madre.contracts import WorkRecord, WorkRetryRequest, WorkSubmission
from madre.registry import InteroperabilityRegistry, ModuleManifest
from madre.runtime import (
    CancellationConflict,
    IdempotencyConflict,
    OriginatorNotRegistered,
    ResultLost,
    ResultUnavailable,
    RetryConflict,
    WorkNotFound,
    WorkRuntime,
)
from madre.security import BoundaryRequirements, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def _capabilities(settings: Settings) -> CapabilityRegistry:
    registry = CapabilityRegistry()
    for capability_id, config in settings.capabilities.items():
        envelope = SecurityEnvelope.issue(
            subject=capability_id,
            sensitivity=SecurityLevel.LEVEL_1,
            trust=config.trust,
            risk=config.risk,
            scopes={"*"},
            origin="local-config",
        )
        requirements = BoundaryRequirements(
            max_input_sensitivity=config.max_input_sensitivity,
            risk=config.risk,
            allowed_execution_boundaries=frozenset({config.boundary}),
        )
        descriptor = CapabilityDescriptor(
            id=capability_id,
            kind="model.inference.chat",
            modality="text",
            model_id=config.model,
            execution_boundary=config.boundary,
            heavyweight=config.heavyweight,
            requirements=requirements,
            security=envelope,
        )
        registry.register(
            OpenAICompatibleChatCapability(descriptor, config.endpoint, config.model)
        )
    return registry


def create_app(settings: Settings) -> FastAPI:
    state: dict[str, object] = {}

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        with open_database(settings.data_dir) as connection:
            store = PlatformStore(connection)
            registry = InteroperabilityRegistry(store)
            runtime = WorkRuntime(store, _capabilities(settings), registry)
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

    def authenticated(authorization: str | None = Header(default=None)) -> None:
        expected = f"Bearer {settings.token()}"
        if authorization != expected:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="unauthorized")

    def runtime(_: Annotated[None, Depends(authenticated)]) -> WorkRuntime:
        value = state.get("runtime")
        if not isinstance(value, WorkRuntime):
            raise HTTPException(status_code=503, detail="runtime unavailable")
        return value

    def interoperability(
        _: Annotated[None, Depends(authenticated)],
    ) -> InteroperabilityRegistry:
        value = state.get("registry")
        if not isinstance(value, InteroperabilityRegistry):
            raise HTTPException(status_code=503, detail="registry unavailable")
        return value

    RuntimeDep = Annotated[WorkRuntime, Depends(runtime)]
    RegistryDep = Annotated[InteroperabilityRegistry, Depends(interoperability)]

    @app.post("/v1/registry/modules", response_model=ModuleManifest)
    async def register_module(manifest: ModuleManifest, registry: RegistryDep) -> ModuleManifest:
        registry.register(manifest)
        return manifest

    @app.post("/v1/work", response_model=WorkRecord, status_code=202)
    async def submit_work(
        submission: WorkSubmission,
        response: Response,
        service: RuntimeDep,
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ) -> WorkRecord:
        try:
            record = await service.submit(submission, idempotency_key=idempotency_key)
        except OriginatorNotRegistered as exc:
            raise HTTPException(status_code=403, detail="originator is not registered") from exc
        except IdempotencyConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        response.headers["Location"] = f"/v1/work/{record.id}"
        return record

    @app.get("/v1/work/{work_id}", response_model=WorkRecord)
    async def inspect_work(work_id: str, service: RuntimeDep) -> WorkRecord:
        record = service.inspect(work_id)
        if record is None:
            raise HTTPException(status_code=404, detail="work not found")
        return record

    @app.post("/v1/work/{work_id}/cancel", response_model=WorkRecord)
    async def cancel_work(work_id: str, service: RuntimeDep) -> WorkRecord:
        try:
            return await service.cancel(work_id)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except CancellationConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    @app.post("/v1/work/{work_id}/retry", response_model=WorkRecord)
    async def retry_work(
        work_id: str,
        request: WorkRetryRequest,
        service: RuntimeDep,
        idempotency_key: str = Header(alias="Idempotency-Key"),
    ) -> WorkRecord:
        try:
            return await service.retry(work_id, request, idempotency_key=idempotency_key)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except RetryConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

    @app.post("/v1/work/{work_id}/result")
    async def consume_result(work_id: str, service: RuntimeDep) -> object:
        try:
            return service.consume_result(work_id)
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except ResultLost as exc:
            raise HTTPException(status_code=410, detail=str(exc)) from exc
        except ResultUnavailable as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    return app
