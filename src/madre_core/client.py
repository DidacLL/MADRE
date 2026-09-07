"""HTTP-only client used by the first-party CORE application."""

from __future__ import annotations

import asyncio
import ipaddress
from collections.abc import Sequence
from typing import Literal
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, JsonValue, ValidationError

CORE_APPLICATION_ID = "madre-core"


class CoreRuntimeError(RuntimeError):
    """A runtime boundary failure that can be reported directly to a CORE user."""


class _Failure(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    code: str
    message: str


class _WorkRecord(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    id: str
    status: Literal["accepted", "running", "succeeded", "failed", "cancelled"]
    result: dict[str, JsonValue] | None = None
    failure: _Failure | None = None


def _runtime_url(value: str) -> str:
    parsed = urlsplit(value)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ValueError("runtime URL has an invalid port") from exc
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
        or parsed.path not in {"", "/"}
    ):
        raise ValueError("runtime URL must be a loopback HTTP(S) origin without credentials")
    try:
        address = ipaddress.ip_address(parsed.hostname)
    except ValueError as exc:
        raise ValueError("runtime URL must use a literal loopback address") from exc
    if not address.is_loopback:
        raise ValueError("runtime URL must use a literal loopback address")
    if port is None:
        return value.rstrip("/")
    return value.rstrip("/")


class CoreClient:
    """Submit CORE chat work through MADRE's ordinary authenticated HTTP API."""

    def __init__(
        self,
        runtime_url: str,
        token: str,
        capability_id: str,
        *,
        poll_interval_seconds: float = 0.2,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if not token.strip():
            raise ValueError("runtime bearer credential is empty")
        if not capability_id.strip():
            raise ValueError("capability id is empty")
        if poll_interval_seconds <= 0:
            raise ValueError("poll interval must be positive")
        self.runtime_url = _runtime_url(runtime_url)
        self.token = token
        self.capability_id = capability_id
        self.poll_interval_seconds = poll_interval_seconds
        self.transport = transport

    async def complete(
        self,
        messages: Sequence[dict[str, str]],
        *,
        max_tokens: int = 256,
        timeout_seconds: float = 120,
    ) -> str:
        if not messages:
            raise ValueError("CORE requires at least one chat message")
        if max_tokens <= 0:
            raise ValueError("max_tokens must be positive")
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")

        payload_messages: list[dict[str, str]] = []
        for message in messages:
            role = message.get("role", "")
            content = message.get("content", "")
            if role not in {"system", "user", "assistant"} or not content.strip():
                raise ValueError("CORE chat messages require a valid role and non-empty content")
            payload_messages.append({"role": role, "content": content})

        submission = {
            "application_id": CORE_APPLICATION_ID,
            "capability_id": self.capability_id,
            "input": {"messages": payload_messages, "max_tokens": max_tokens},
            "constraints": {"timeout_seconds": timeout_seconds, "local_only": True},
        }
        headers = {"Authorization": f"Bearer {self.token}"}
        timeout = httpx.Timeout(None, connect=5.0)

        try:
            async with httpx.AsyncClient(
                base_url=self.runtime_url,
                headers=headers,
                trust_env=False,
                follow_redirects=False,
                timeout=timeout,
                transport=self.transport,
            ) as client:
                response = await client.post("/v1/work", json=submission)
                record = self._record(response)
                while record.status in {"accepted", "running"}:
                    await asyncio.sleep(self.poll_interval_seconds)
                    response = await client.get(f"/v1/work/{record.id}")
                    record = self._record(response)
        except httpx.TimeoutException as exc:
            raise CoreRuntimeError("timed out communicating with the MADRE runtime") from exc
        except httpx.RequestError as exc:
            raise CoreRuntimeError("could not communicate with the MADRE runtime") from exc

        if record.status == "failed":
            if record.failure is None:
                raise CoreRuntimeError(f"MADRE work {record.id} failed without failure details")
            raise CoreRuntimeError(
                f"MADRE work failed [{record.failure.code}]: {record.failure.message}"
            )
        if record.status == "cancelled":
            raise CoreRuntimeError(f"MADRE work {record.id} was cancelled before execution")
        if record.status != "succeeded" or record.result is None:
            raise CoreRuntimeError(f"MADRE work {record.id} returned no terminal result")

        text = record.result.get("text")
        if not isinstance(text, str) or not text.strip():
            raise CoreRuntimeError(f"MADRE work {record.id} succeeded without assistant text")
        return text

    @staticmethod
    def _record(response: httpx.Response) -> _WorkRecord:
        if response.status_code == 401:
            raise CoreRuntimeError("MADRE runtime rejected the bearer credential")
        try:
            response.raise_for_status()
        except httpx.HTTPStatusError as exc:
            raise CoreRuntimeError(f"MADRE runtime returned HTTP {response.status_code}") from exc
        try:
            return _WorkRecord.model_validate(response.json())
        except (ValueError, ValidationError) as exc:
            raise CoreRuntimeError("MADRE runtime returned invalid work data") from exc
