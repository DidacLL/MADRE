"""Adapter for one external OpenAI-compatible transport protocol.

Authentication, headers, and provider environment configuration are supplied by the
injected HTTP client and are not MADRE concepts.
"""

from __future__ import annotations

import asyncio
from collections.abc import Callable
from typing import Literal
from urllib.parse import urlsplit

import httpx
from pydantic import ConfigDict, Field, JsonValue, TypeAdapter
from pydantic.main import BaseModel

from madre.capabilities import CapabilityError
from madre_sdk.execution import (
    CapabilityDefinition,
    CostClass,
    ExecutionBoundary,
    ExecutionConstraints,
    LatencyClass,
    QualityTier,
    ReasoningEffort,
)


class OpenAIChatConfig(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    kind: Literal["openai_chat"] = "openai_chat"
    endpoint: str
    model: str = Field(min_length=1)
    boundary: ExecutionBoundary = ExecutionBoundary.LOCAL
    latency: LatencyClass = LatencyClass.STANDARD
    quality: QualityTier = QualityTier.STANDARD
    reasoning_efforts: frozenset[ReasoningEffort] = frozenset(
        {ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.HIGH}
    )
    cost: CostClass = CostClass.FREE
    heavyweight: bool = True


class OpenAICompatibleChatCapability:
    def __init__(
        self,
        definition: CapabilityDefinition,
        config: OpenAIChatConfig,
        client: httpx.AsyncClient | None = None,
        client_factory: Callable[[float], httpx.AsyncClient] | None = None,
    ) -> None:
        parsed = urlsplit(config.endpoint)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("OpenAI-compatible endpoint must be an HTTP(S) base URL")
        if client is not None and client_factory is not None:
            raise ValueError("inject either one client or one client factory")
        self._definition = definition
        self._endpoint = config.endpoint.rstrip("/")
        self._model = config.model
        self._client = client
        self._client_factory = client_factory or (
            lambda timeout: httpx.AsyncClient(timeout=timeout)
        )

    @property
    def definition(self) -> CapabilityDefinition:
        return self._definition

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue:
        if not isinstance(payload, dict):
            raise CapabilityError("invalid_input")
        request = dict(payload)
        request["model"] = self._model
        request["stream"] = False
        try:
            async with asyncio.timeout(constraints.timeout_seconds):
                if self._client is not None:
                    response = await self._client.post(
                        f"{self._endpoint}/chat/completions", json=request
                    )
                else:
                    async with self._client_factory(constraints.timeout_seconds) as client:
                        response = await client.post(
                            f"{self._endpoint}/chat/completions", json=request
                        )
                response.raise_for_status()
                parsed: JsonValue = TypeAdapter(JsonValue).validate_python(response.json())
        except (TimeoutError, httpx.TimeoutException) as exc:
            raise CapabilityError("timeout") from exc
        except httpx.HTTPStatusError as exc:
            raise CapabilityError("http_status") from exc
        except httpx.RequestError as exc:
            raise CapabilityError("connection") from exc
        if not isinstance(parsed, dict):
            raise CapabilityError("invalid_response")
        return parsed
