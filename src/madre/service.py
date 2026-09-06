"""Foreground service shell. Work endpoints are intentionally not registered yet."""

import secrets
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import Depends, FastAPI, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from madre.config import Settings
from madre.storage import SCHEMA_VERSION, open_database


def create_app(settings: Settings) -> FastAPI:
    token = settings.token()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        with open_database(settings.data_dir):
            yield

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

    @app.get("/health")
    def health() -> dict[str, str | int]:
        return {"status": "ok", "schema_version": SCHEMA_VERSION}

    return app
