"""One private protocol adapter for OpenAI-compatible chat transports."""

from __future__ import annotations

import asyncio
from collections.abc import Callable
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, Field, JsonValue, TypeAdapter

from madre.capabilities import CapabilityDefinition, CapabilityError, CapabilityInvocation


class OpenAIChatConfig(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    endpoint: str
    model: str = Field(min_length=1)


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

    def is_available(self) -> bool:
        return True

    async def invoke(self, request: CapabilityInvocation) -> object:
        if len(request.payloads) != 1 or not isinstance(request.payloads[0], dict):
            raise CapabilityError("invalid_input")
        provider_payload = dict(request.payloads[0])
        provider_payload["model"] = self._model
        provider_payload["stream"] = False
        try:
            async with asyncio.timeout(request.timeout_seconds):
                if self._client is not None:
                    response = await self._client.post(
                        f"{self._endpoint}/chat/completions",
                        json=provider_payload,
                    )
                else:
                    async with self._client_factory(request.timeout_seconds) as client:
                        response = await client.post(
                            f"{self._endpoint}/chat/completions",
                            json=provider_payload,
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
