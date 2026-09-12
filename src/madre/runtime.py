"""Kernel routing, scheduling, and delivery of typed physical work."""

from __future__ import annotations

import asyncio
import hashlib
from collections.abc import Callable
from datetime import UTC, datetime
from typing import cast
from uuid import uuid4

from madre.capabilities import (
    CapabilityAdapter,
    CapabilityError,
    CapabilityInvocation,
    CapabilityRegistry,
    CapabilityUnavailable,
    CapacityResourceCoordinator,
    ResourceCoordinator,
)
from madre.storage import WorkQueueStore, utc_now
from madre.work import WorkFailure, WorkId, WorkRecord, WorkStatus
from madre_sdk import PhysicalResult, PhysicalResultJsonCodec, WorkRequest, WorkRequestJsonCodec


class IdempotencyConflict(RuntimeError):
    pass


class WorkNotFound(RuntimeError):
    pass


class CancellationConflict(RuntimeError):
    pass


class ResultUnavailable(RuntimeError):
    pass


class Kernel:
    def __init__(
        self,
        capabilities: CapabilityRegistry,
        store: WorkQueueStore | None = None,
        resources: ResourceCoordinator | None = None,
        *,
        clock: Callable[[], datetime] | None = None,
        request_codec: WorkRequestJsonCodec | None = None,
        result_codec: PhysicalResultJsonCodec | None = None,
    ) -> None:
        self._capabilities = capabilities
        self._store = store
        self._resources = resources or CapacityResourceCoordinator()
        self._clock = clock or utc_now
        self._request_codec = request_codec or WorkRequestJsonCodec()
        self._result_codec = result_codec or PhysicalResultJsonCodec()
        self._schedule_changed = asyncio.Event()
        if self._store is not None:
            self._store.recover_interrupted(self._clock())

    async def submit[OutputT](self, request: WorkRequest[OutputT]) -> PhysicalResult[OutputT]:
        physical_request = cast(WorkRequest[object], request)
        adapter = self._capabilities.select(physical_request)
        result = await self._invoke(adapter, physical_request, attempt=1)
        return cast(PhysicalResult[OutputT], result)

    async def enqueue[OutputT](
        self,
        request: WorkRequest[OutputT],
        *,
        idempotency_key: str | None = None,
    ) -> WorkRecord:
        store = self._require_store()
        key = self._idempotency_key(idempotency_key)
        physical_request = cast(WorkRequest[object], request)
        encoded = self._request_codec.encode(physical_request)
        digest = hashlib.sha256(encoded.encode()).hexdigest()
        if key is not None:
            existing = store.idempotent_match(request.module, key)
            if existing is not None:
                record, previous_digest = existing
                if previous_digest != digest:
                    raise IdempotencyConflict("idempotency key belongs to different physical work")
                return record
        identity = WorkId(uuid4().hex)
        if not store.create(identity, physical_request, encoded, digest, self._clock(), key):
            if key is None:
                raise RuntimeError("unique work identity collided")
            existing = store.idempotent_match(request.module, key)
            if existing is None:
                raise RuntimeError("idempotent work disappeared")
            record, previous_digest = existing
            if previous_digest != digest:
                raise IdempotencyConflict("idempotency key belongs to different physical work")
            return record
        self._schedule_changed.set()
        return self._require(identity)

    async def run_eligible(self) -> int:
        store = self._require_store()
        attempts = 0
        while identity := store.next_eligible(self._clock()):
            attempted = await self._run_durable(identity)
            attempts += int(attempted)
        return attempts

    async def run_scheduler(self) -> None:
        store = self._require_store()
        while True:
            self._schedule_changed.clear()
            await self.run_eligible()
            next_time = store.next_eligibility()
            if next_time is None:
                await self._schedule_changed.wait()
                continue
            delay = max((next_time - self._clock()).total_seconds(), 0.0)
            if delay == 0:
                continue
            try:
                await asyncio.wait_for(self._schedule_changed.wait(), timeout=delay)
            except TimeoutError:
                pass

    def inspect(self, identity: WorkId) -> WorkRecord | None:
        return self._require_store().get(identity)

    def cancel(self, identity: WorkId) -> WorkRecord:
        store = self._require_store()
        current = self._require(identity)
        if current.status is WorkStatus.CANCELLED:
            return current
        if not store.cancel(identity, self._clock()):
            raise CancellationConflict("only queued work can be cancelled")
        self._schedule_changed.set()
        return self._require(identity)

    def consume_result(self, identity: WorkId) -> PhysicalResult[object]:
        store = self._require_store()
        self._require(identity)
        encoded = store.consume_result(identity)
        if encoded is None:
            raise ResultUnavailable("work has no pending physical result")
        return self._result_codec.decode(encoded)

    async def _run_durable(self, identity: WorkId) -> bool:
        store = self._require_store()
        request = store.load_request(identity)
        if request is None:
            raise RuntimeError("queued work has no opaque request snapshot")
        try:
            adapter = self._capabilities.select(request)
        except CapabilityUnavailable:
            store.defer_unavailable(identity, self._clock())
            return False
        started_at = self._clock()
        attempt = store.start_attempt(identity, adapter.definition.identity, started_at)
        if attempt is None:
            return False
        try:
            result = await self._invoke(adapter, request, attempt)
        except CapabilityError as exc:
            store.finish_failure(identity, attempt, WorkFailure(exc.code), self._clock())
            self._schedule_changed.set()
            return True
        encoded_result = self._result_codec.encode(result)
        store.finish_success(identity, attempt, encoded_result, self._clock())
        return True

    async def _invoke(
        self,
        adapter: CapabilityAdapter,
        request: WorkRequest[object],
        attempt: int,
    ) -> PhysicalResult[object]:
        invocation = CapabilityInvocation.from_request(request)
        started_at = datetime.now(UTC)
        try:
            async with self._resources.reserve(adapter.definition.resources):
                output = await adapter.invoke(invocation)
        except CapabilityError:
            raise
        except Exception as exc:
            raise CapabilityError("internal_error") from exc
        completed_at = datetime.now(UTC)
        return PhysicalResult[object](
            computation=request.computation.identity,
            output_type=request.computation.output_type,
            output=output,
            started_at=started_at,
            completed_at=completed_at,
            attempt=attempt,
        )

    @staticmethod
    def _idempotency_key(value: str | None) -> str | None:
        if value is None:
            return None
        if not value or value.isspace():
            raise ValueError("idempotency key must not be blank")
        return value

    def _require(self, identity: WorkId) -> WorkRecord:
        record = self._require_store().get(identity)
        if record is None:
            raise WorkNotFound(identity.value)
        return record

    def _require_store(self) -> WorkQueueStore:
        if self._store is None:
            raise RuntimeError("durable work requires WorkQueueStore")
        return self._store
