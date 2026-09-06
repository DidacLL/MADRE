"""Authenticated local HTTP boundary for MADRE runtime work."""

import secrets
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from madre.config import Settings
from madre.contracts import WorkRecord, WorkSubmission
from madre.runtime import DelayedExecutionUnavailable, WorkRuntime
from madre.storage import SCHEMA_VERSION, WorkStore, open_database


def create_app(settings: Settings) -> FastAPI:
    token = settings.token()
    active_runtime: WorkRuntime | None = None

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        nonlocal active_runtime
        with open_database(settings.data_dir) as connection:
            active_runtime = WorkRuntime(settings, WorkStore(connection))
            try:
                yield
            finally:
                active_runtime = None

    bearer = HTTPBearer(auto_error=False)

    def authenticate(
        credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    ) -> None:
        if credentials is None or not secrets.compare_digest(
            credentials.credentials.encode(), token.encode()
        ):
            raise HTTPException(
                401, "invalid bearer credential", headers={"WWW-Authenticate": "Bearer"}
            )

    app = FastAPI(
        title="MADRE",
        version="0.1.0",
        lifespan=lifespan,
        dependencies=[Depends(authenticate)],
        docs_url=None,
        redoc_url=None,
        openapi_url=None,
    )

    def runtime() -> WorkRuntime:
        if active_runtime is None:
            raise RuntimeError("MADRE service runtime is not active")
        return active_runtime

    @app.get("/health")
    def health() -> dict[str, str | int]:
        return {"status": "ok", "schema_version": SCHEMA_VERSION}

    @app.post("/v1/work", response_model=WorkRecord, status_code=status.HTTP_201_CREATED)
    async def submit_work(submission: WorkSubmission) -> WorkRecord:
        try:
            return await runtime().execute_immediate(submission)
        except DelayedExecutionUnavailable as exc:
            raise HTTPException(status.HTTP_409_CONFLICT, str(exc)) from exc

    @app.get("/v1/work/{work_id}", response_model=WorkRecord)
    async def inspect_work(work_id: str) -> WorkRecord:
        record = runtime().inspect(work_id)
        if record is None:
            raise HTTPException(status.HTTP_404_NOT_FOUND, "work not found")
        return record

    return app
