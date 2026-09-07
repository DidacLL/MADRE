"""Authenticated HTTP adapter from Kernel reasoning to ordinary MADRE Runtime work."""

from __future__ import annotations

import asyncio
import ipaddress
from collections.abc import Sequence
from typing import Literal
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, ValidationError

from madre_kernel.contracts import RuntimeWorkRef

WireScalar = str | int | float | bool | None


class RuntimeBoundaryError(RuntimeError):
    pass


class RuntimeReasoningResult(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    work: RuntimeWorkRef
    text: str


class _Failure(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    code: str
    message: str


class _WorkRecord(BaseModel):
    model_config = ConfigDict(extra="ignore", frozen=True)

    id: str
    status: Literal["accepted", "running", "succeeded", "failed", "cancelled"]
    result: dict[str, WireScalar] | None = None
    failure: _Failure | None = None


def _loopback_origin(value: str) -> str:
    parsed = urlsplit(value)
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.hostname
        or parsed.username
        or parsed.password
        or parsed.query
        or parsed.fragment
        or parsed.path not in {"", "/"}
    ):
        raise ValueError("runtime URL must be a loopback HTTP(S) origin")
    try:
        if not ipaddress.ip_address(parsed.hostname).is_loopback:
            raise ValueError("runtime URL must use a literal loopback address")
    except ValueError as exc:
        raise ValueError("runtime URL must use a literal loopback address") from exc
    _ = parsed.port
    return value.rstrip("/")


class KernelRuntimeClient:
    def __init__(
        self,
        runtime_url: str,
        token: str,
        capability_id: str,
        *,
        application_id: str,
        poll_interval_seconds: float = 0.01,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        if not token.strip() or not capability_id.strip() or not application_id.strip():
            raise ValueError("runtime token, capability id and application id are required")
        self.runtime_url = _loopback_origin(runtime_url)
        self.token = token
        self.capability_id = capability_id
        self.application_id = application_id
        self.poll_interval_seconds = poll_interval_seconds
        self.transport = transport

    async def reason(self, messages: Sequence[tuple[str, str]]) -> RuntimeReasoningResult:
        wire_messages = [{"role": role, "content": content} for role, content in messages]
        submission = {
            "application_id": self.application_id,
            "capability_id": self.capability_id,
            "input": {"messages": wire_messages, "max_tokens": 256},
            "priority": 0,
            "constraints": {"timeout_seconds": 120, "local_only": True},
        }
        async with self._client() as client:
            record = self._record(await client.post("/v1/work", json=submission))
            while record.status in {"accepted", "running"}:
                await asyncio.sleep(self.poll_interval_seconds)
                record = self._record(await client.get(f"/v1/work/{record.id}"))
        if record.status == "failed":
            detail = (
                record.failure.message if record.failure is not None else "unknown Runtime failure"
            )
            raise RuntimeBoundaryError(detail)
        if record.status != "succeeded" or record.result is None:
            raise RuntimeBoundaryError(
                f"Runtime work {record.id} did not produce a successful result"
            )
        text = record.result.get("text")
        if not isinstance(text, str) or not text.strip():
            raise RuntimeBoundaryError(f"Runtime work {record.id} returned no reasoning text")
        return RuntimeReasoningResult(
            work=RuntimeWorkRef(work_id=record.id),
            text=text,
        )

    def _client(self) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self.runtime_url,
            headers={"Authorization": f"Bearer {self.token}"},
            trust_env=False,
            follow_redirects=False,
            timeout=httpx.Timeout(None, connect=5.0),
            transport=self.transport,
        )

    @staticmethod
    def _record(response: httpx.Response) -> _WorkRecord:
        try:
            response.raise_for_status()
            return _WorkRecord.model_validate(response.json())
        except (httpx.HTTPStatusError, ValueError, ValidationError) as exc:
            raise RuntimeBoundaryError("MADRE Runtime returned invalid work data") from exc
