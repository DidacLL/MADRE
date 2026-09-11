"""OpenAI-compatible chat capability adapter.

Provider protocol, credentials, request shape, response parsing and transport errors
stay inside this adapter and are not MADRE architecture.
"""

from __future__ import annotations

import asyncio
import os
from typing import Literal
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, Field, JsonValue, TypeAdapter

from madre.capabilities import CapabilityDescriptor, CapabilityError
from madre.contracts import ExecutionConstraints, LatencyClass, QualityTier, ReasoningEffort
from madre.security import ExecutionBoundary, Privacy


class OpenAIChatConfig(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    kind: Literal["openai_chat"] = "openai_chat"
    endpoint: str
    model: str = Field(min_length=1)
    provider_id: str | None = None
    boundary: ExecutionBoundary = "local"
    latency_class: LatencyClass = "standard"
    quality_tier: QualityTier = "standard"
    reasoning_efforts: frozenset[ReasoningEffort] = frozenset({"low", "medium", "high"})
    paid: bool = False
    resources: frozenset[str] = Field(default_factory=frozenset)
    heavyweight: bool = True
    privacy: Privacy = Privacy.UNKNOWN
    api_key_env: str | None = None


class OpenAICompatibleChatCapability:
    def __init__(self, descriptor: CapabilityDescriptor, config: OpenAIChatConfig) -> None:
        parsed = urlsplit(config.endpoint)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ValueError("OpenAI-compatible endpoint must be an HTTP(S) base URL")
        self._descriptor = descriptor
        self._endpoint = config.endpoint.rstrip("/")
        self._model = config.model
        self._api_key_env = config.api_key_env

    @property
    def descriptor(self) -> CapabilityDescriptor:
        return self._descriptor

    async def execute(self, payload: JsonValue, constraints: ExecutionConstraints) -> JsonValue:
        if not isinstance(payload, dict):
            raise CapabilityError("invalid_input")
        headers: dict[str, str] = {}
        if self._api_key_env:
            api_key = os.environ.get(self._api_key_env)
            if not api_key:
                raise CapabilityError("provider_credentials")
            headers["Authorization"] = f"Bearer {api_key}"
        request = {"model": self._model, **payload, "stream": False}
        try:
            async with asyncio.timeout(constraints.timeout_seconds):
                async with httpx.AsyncClient(timeout=constraints.timeout_seconds) as client:
                    response = await client.post(
                        f"{self._endpoint}/chat/completions",
                        json=request,
                        headers=headers,
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
