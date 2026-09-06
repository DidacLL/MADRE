"""Bounded chat-completion adapter used by runtime work and the environment probe."""

import asyncio
import time
from typing import Literal

import httpx
from pydantic import BaseModel, Field

from madre.config import CapabilityConfig, StrictModel, is_loopback_endpoint
from madre.contracts import ExecutionConstraints


class CapabilityError(RuntimeError):
    def __init__(self, code: str, message: str):
        self.code = code
        super().__init__(message)


class ChatMessage(StrictModel):
    role: Literal["system", "user", "assistant"]
    content: str


class ChatInput(StrictModel):
    messages: list[ChatMessage] = Field(min_length=1)
    max_tokens: int = Field(default=64, ge=1, le=32768)


class ChatResult(StrictModel):
    text: str
    model: str
    finish_reason: Literal["stop", "length"]
    elapsed_seconds: float


class _Message(BaseModel):
    content: str = Field(min_length=1)


class _Choice(BaseModel):
    message: _Message
    finish_reason: Literal["stop", "length"]


class _Response(BaseModel):
    model: str = Field(min_length=1)
    choices: list[_Choice] = Field(min_length=1)


async def invoke_chat(
    capability: CapabilityConfig,
    request: ChatInput,
    constraints: ExecutionConstraints,
    *,
    transport: httpx.AsyncBaseTransport | None = None,
) -> ChatResult:
    if capability.boundary == "local" and not is_loopback_endpoint(capability.endpoint):
        raise CapabilityError(
            "boundary_denied", "local capabilities require a literal loopback endpoint"
        )
    if constraints.local_only and capability.boundary != "local":
        raise CapabilityError("boundary_denied", "work requires local execution")
    started = time.monotonic()
    try:
        async with asyncio.timeout(constraints.timeout_seconds):
            async with httpx.AsyncClient(
                trust_env=False,
                follow_redirects=False,
                transport=transport,
                timeout=constraints.timeout_seconds,
            ) as client:
                response = await client.post(
                    f"{capability.endpoint}/chat/completions",
                    json={"model": capability.model, **request.model_dump(), "stream": False},
                )
                response.raise_for_status()
                parsed = _Response.model_validate(response.json())
                text = parsed.choices[0].message.content
                if not text.strip():
                    raise ValueError("empty generated content")
    except (TimeoutError, httpx.TimeoutException) as exc:
        raise CapabilityError("timeout", "capability exceeded the invocation time limit") from exc
    except httpx.HTTPStatusError as exc:
        raise CapabilityError(
            "http_status", f"capability returned HTTP {exc.response.status_code}"
        ) from exc
    except httpx.RequestError as exc:
        raise CapabilityError(
            "connection", "could not communicate with capability endpoint"
        ) from exc
    except ValueError as exc:
        raise CapabilityError(
            "invalid_response", "capability returned invalid chat-completion data"
        ) from exc
    return ChatResult(
        text=text,
        model=parsed.model,
        finish_reason=parsed.choices[0].finish_reason,
        elapsed_seconds=time.monotonic() - started,
    )
