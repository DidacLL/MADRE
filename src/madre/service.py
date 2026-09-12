"""Local HTTP representation of Kernel work and the live Module directory."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import datetime

from fastapi import FastAPI, Header, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict

from madre.capabilities import CapabilityError, CapabilityUnavailable
from madre.config import Settings
from madre.registry import ModuleRegistry
from madre.runtime import (
    CancellationConflict,
    IdempotencyConflict,
    Kernel,
    ResultUnavailable,
    WorkNotFound,
)
from madre.storage import WorkQueueStore, open_database
from madre.work import WorkAttempt, WorkId, WorkRecord
from madre.work_codec import MaterialSetJsonCodec, PhysicalResultJsonCodec, WorkRequestJsonCodec
from madre_sdk import ModuleDefinitionJsonCodec, ModuleDirectoryJsonCodec, ModuleId


class _WireModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class _AttemptDto(_WireModel):
    number: int
    status: str
    started_at: datetime
    completed_at: datetime | None
    capability_name: str
    capability_revision: str
    failure_code: str | None


class _WorkRecordDto(_WireModel):
    id: str
    module_name: str
    module_revision: str
    computation_namespace: str
    computation_name: str
    computation_revision: str
    status: str
    submitted_at: datetime
    not_before: datetime | None
    priority: int
    maximum_attempts: int
    attempts: tuple[_AttemptDto, ...]
    completed_at: datetime | None
    failure_code: str | None
    delivery: str | None


def _attempt_dto(attempt: WorkAttempt) -> _AttemptDto:
    return _AttemptDto(
        number=attempt.number,
        status=attempt.status.value,
        started_at=attempt.started_at,
        completed_at=attempt.completed_at,
        capability_name=attempt.capability_name,
        capability_revision=attempt.capability_revision,
        failure_code=None if attempt.failure is None else attempt.failure.code,
    )


def _record_response(record: WorkRecord, *, status_code: int = 200) -> Response:
    dto = _WorkRecordDto(
        id=record.identity.value,
        module_name=record.module.name,
        module_revision=record.module.revision,
        computation_namespace=record.computation.namespace,
        computation_name=record.computation.name,
        computation_revision=record.computation.revision,
        status=record.status.value,
        submitted_at=record.submitted_at,
        not_before=record.not_before,
        priority=record.priority,
        maximum_attempts=record.maximum_attempts,
        attempts=tuple(_attempt_dto(attempt) for attempt in record.attempts),
        completed_at=record.completed_at,
        failure_code=None if record.failure is None else record.failure.code,
        delivery=None if record.delivery is None else record.delivery.value,
    )
    return Response(dto.model_dump_json(), status_code=status_code, media_type="application/json")


def _json_response(encoded: str, *, status_code: int = 200) -> Response:
    return Response(encoded, status_code=status_code, media_type="application/json")


async def _encoded_body(request: Request) -> str:
    try:
        return (await request.body()).decode("utf-8")
    except UnicodeDecodeError as exc:
        raise HTTPException(status_code=422, detail="request body must be UTF-8 JSON") from exc


class _ApplicationState:
    def __init__(self) -> None:
        self.kernel: Kernel | None = None
        self.modules: ModuleRegistry | None = None

    def clear(self) -> None:
        self.kernel = None
        self.modules = None


def create_app(settings: Settings) -> FastAPI:
    state = _ApplicationState()
    request_codec = WorkRequestJsonCodec()
    result_codec = PhysicalResultJsonCodec()
    material_codec = MaterialSetJsonCodec()
    definition_codec = ModuleDefinitionJsonCodec()
    directory_codec = ModuleDirectoryJsonCodec()

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        with open_database(settings.data_dir) as connection:
            kernel = Kernel(
                settings.capabilities(),
                WorkQueueStore(connection, request_codec),
                request_codec=request_codec,
                result_codec=result_codec,
            )
            state.kernel = kernel
            state.modules = ModuleRegistry()
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
        if state.kernel is None:
            raise HTTPException(status_code=503, detail="runtime unavailable")
        return state.kernel

    def modules() -> ModuleRegistry:
        if state.modules is None:
            raise HTTPException(status_code=503, detail="Module directory unavailable")
        return state.modules

    @app.post("/v1/modules")
    async def register_module(request: Request) -> Response:
        try:
            definition = definition_codec.decode(await _encoded_body(request))
        except (TypeError, ValueError) as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        modules().register(definition)
        return _json_response(definition_codec.encode(definition))

    @app.delete("/v1/modules/{module_name}", status_code=204)
    async def remove_module(module_name: str, revision: str = "1") -> Response:
        modules().remove(ModuleId(module_name, revision))
        return Response(status_code=204)

    @app.post("/v1/modules/reachable")
    async def reachable_modules(request: Request) -> Response:
        try:
            materials = material_codec.decode(await _encoded_body(request))
            entries = await modules().reachable(materials)
        except (TypeError, ValueError) as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        return _json_response(directory_codec.encode(entries))

    @app.post("/v1/executions")
    async def execute(request: Request) -> Response:
        try:
            work = request_codec.decode(await _encoded_body(request))
            result = await kernel().submit(work)
        except CapabilityUnavailable as exc:
            raise HTTPException(status_code=503, detail="physical capability unavailable") from exc
        except CapabilityError as exc:
            raise HTTPException(status_code=502, detail="physical capability failed") from exc
        except (TypeError, ValueError) as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        return _json_response(result_codec.encode(result))

    @app.post("/v1/work", status_code=202)
    async def enqueue(
        request: Request,
        idempotency_key: str | None = Header(default=None, alias="Idempotency-Key"),
    ) -> Response:
        try:
            work = request_codec.decode(await _encoded_body(request))
            record = await kernel().enqueue(work, idempotency_key=idempotency_key)
        except IdempotencyConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        except (TypeError, ValueError) as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        response = _record_response(record, status_code=202)
        response.headers["Location"] = f"/v1/work/{record.identity.value}"
        return response

    @app.get("/v1/work/{work_id}")
    async def inspect(work_id: str) -> Response:
        record = kernel().inspect(WorkId(work_id))
        if record is None:
            raise HTTPException(status_code=404, detail="work not found")
        return _record_response(record)

    @app.post("/v1/work/{work_id}/cancel")
    async def cancel(work_id: str) -> Response:
        try:
            return _record_response(kernel().cancel(WorkId(work_id)))
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except CancellationConflict as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc

    @app.post("/v1/work/{work_id}/result")
    async def consume_result(work_id: str) -> Response:
        try:
            result = kernel().consume_result(WorkId(work_id))
        except WorkNotFound as exc:
            raise HTTPException(status_code=404, detail="work not found") from exc
        except ResultUnavailable as exc:
            raise HTTPException(status_code=409, detail=str(exc)) from exc
        return _json_response(result_codec.encode(result))

    return app
