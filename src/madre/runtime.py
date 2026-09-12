"""Kernel execution: physical mechanism selection, lifecycle, and delivery."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from datetime import datetime
from uuid import uuid4

from pydantic import JsonValue

from madre.capabilities import CapabilityAdapter, CapabilityError, CapabilityRegistry
from madre.contracts import WorkFailure, WorkRecord, WorkRetryRequest, WorkSpec, WorkSubmission
from madre.interfaces import MaterialResolution
from madre.storage import PlatformStore, utc_now
from madre_sdk.execution import ExecutionRequest
from madre_sdk.material import Material
from madre_sdk.security import Disclosure, ScopeIdentity, SecurityMismatch


class IdempotencyConflict(RuntimeError):
    pass


class WorkNotFound(RuntimeError):
    pass


class RetryConflict(RuntimeError):
    pass


class CancellationConflict(RuntimeError):
    pass


class ResultUnavailable(RuntimeError):
    pass


class ResultLost(ResultUnavailable):
    pass


class ExecutionUnavailable(RuntimeError):
    pass


class Kernel:
    def __init__(
        self,
        capabilities: CapabilityRegistry,
        store: PlatformStore | None = None,
        *,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self.capabilities = capabilities
        self.store = store
        self._clock = clock or utc_now
        self._schedule_changed = asyncio.Event()
        self._heavyweight_local = asyncio.Lock()
        self._results: dict[str, Material[JsonValue]] = {}
        self._resolvers: dict[ScopeIdentity, MaterialResolution] = {}
        if store is not None:
            store.fail_interrupted_attempts(self._clock())
            store.mark_unconsumed_results_lost()

    def register_material_resolver(
        self,
        module: ScopeIdentity,
        resolver: MaterialResolution,
    ) -> None:
        if module.name != module.owner:
            raise ValueError("material resolver identity must describe a Module")
        self._resolvers[module] = resolver

    async def execute(self, request: ExecutionRequest) -> Material[JsonValue]:
        adapter = self.capabilities.select(request.capability)
        if adapter is None:
            raise ExecutionUnavailable("no physical Capability matches the request")

        # This is ordinary algebraic construction. There is no evaluator, candidate
        # decision, persisted relation, exception, or historical operand.
        Disclosure(
            sources=request.material.surface,
            observers=adapter.definition.security,
        )

        try:
            async with self._capability_slot(adapter):
                payload = await adapter.execute(request.material.payload, request.constraints)
        except CapabilityError:
            raise
        except Exception as exc:
            raise CapabilityError("internal_error") from exc

        return Material[JsonValue](
            identity=request.output.identity,
            contract=request.output.contract,
            payload=payload,
            security=request.output.security,
        )

    async def submit(
        self, submission: WorkSubmission, *, idempotency_key: str | None = None
    ) -> WorkRecord:
        store = self._require_store()
        spec = WorkSpec(**submission.model_dump())
        key = self._idempotency_key(idempotency_key)
        if key is not None:
            existing = store.get_by_idempotency_key(spec.originator.owner, key)
            if existing is not None:
                if existing.spec != spec:
                    raise IdempotencyConflict("idempotency key refers to different work")
                return existing

        work_id = uuid4().hex
        created = store.create(work_id, spec, self._clock(), idempotency_key=key)
        if not created:
            assert key is not None
            existing = store.get_by_idempotency_key(spec.originator.owner, key)
            if existing is None:
                raise RuntimeError("idempotent work disappeared")
            if existing.spec != spec:
                raise IdempotencyConflict("idempotency key refers to different work")
            return existing
        self._schedule_changed.set()
        return self._require(work_id)

    async def run_eligible(self) -> int:
        store = self._require_store()
        executed = 0
        while work_id := store.next_eligible(self._clock()):
            await self._execute_durable(work_id)
            executed += 1
        return executed

    async def run_scheduler(self) -> None:
        store = self._require_store()
        while True:
            self._schedule_changed.clear()
            await self.run_eligible()
            next_eligibility = store.next_eligibility()
            if next_eligibility is None:
                await self._schedule_changed.wait()
                continue
            delay = max((next_eligibility - self._clock()).total_seconds(), 0.0)
            if delay == 0:
                continue
            try:
                await asyncio.wait_for(self._schedule_changed.wait(), timeout=delay)
            except TimeoutError:
                pass

    async def retry(
        self,
        work_id: str,
        request: WorkRetryRequest,
        *,
        idempotency_key: str,
    ) -> WorkRecord:
        store = self._require_store()
        key = self._idempotency_key(idempotency_key)
        assert key is not None
        prior = store.retry_policy_by_key(work_id, key)
        if prior is not None:
            if prior != request.allow_unknown_outcome:
                raise RetryConflict("retry idempotency key has different policy")
            return self._require(work_id)
        record = self._require(work_id)
        if record.status != "failed":
            raise RetryConflict("only failed work can be retried")
        if record.cancellation is not None:
            raise RetryConflict("cancelled work cannot be retried")
        if (
            record.failure is not None
            and record.failure.code == "interrupted"
            and not request.allow_unknown_outcome
        ):
            raise RetryConflict("interrupted work requires allow_unknown_outcome=true")
        if store.requeue_failed(work_id, key, request.allow_unknown_outcome, self._clock()) is None:
            raise RetryConflict("work is no longer failed")
        self._schedule_changed.set()
        return self._require(work_id)

    async def cancel(self, work_id: str) -> WorkRecord:
        store = self._require_store()
        record = self._require(work_id)
        if record.cancellation is not None:
            return record
        disposition = store.request_cancellation(work_id, self._clock())
        if disposition == "terminal":
            raise CancellationConflict("terminal work cannot be cancelled")
        if disposition is None:
            raise WorkNotFound(work_id)
        self._schedule_changed.set()
        return self._require(work_id)

    def inspect(self, work_id: str) -> WorkRecord | None:
        return self._require_store().get(work_id)

    def consume_result(self, work_id: str) -> Material[JsonValue]:
        store = self._require_store()
        record = self._require(work_id)
        if record.result is None:
            raise ResultUnavailable("work has no produced result")
        if record.result.delivery_status == "lost":
            raise ResultLost("result content was lost before consumption")
        if record.result.delivery_status == "consumed":
            raise ResultUnavailable("result content was already consumed")
        if work_id not in self._results:
            store.mark_result_lost(work_id)
            raise ResultLost("transient result content is unavailable")
        result = self._results.pop(work_id)
        store.mark_result_consumed(work_id)
        return result

    async def _execute_durable(self, work_id: str) -> WorkRecord:
        store = self._require_store()
        record = self._require(work_id)
        if record.status != "accepted":
            return record
        material = await self._resolve_material(record)
        if material is None:
            return self._require(work_id)
        adapter = self.capabilities.select(record.spec.capability)
        if adapter is None:
            store.fail(work_id, WorkFailure(code="no_capability"), self._clock())
            return self._require(work_id)

        attempt = store.start_attempt(
            work_id,
            adapter.definition.identity,
            adapter.definition.properties.boundary,
            self._clock(),
        )
        if attempt is None:
            return self._require(work_id)

        request = ExecutionRequest(
            requester=record.spec.originator,
            material=material,
            capability=record.spec.capability,
            output=record.spec.output,
            constraints=record.spec.constraints,
        )
        try:
            result = await self.execute(request)
        except SecurityMismatch:
            store.fail(
                work_id,
                WorkFailure(code="security_mismatch"),
                self._clock(),
                attempt_number=attempt,
            )
            return self._require(work_id)
        except CapabilityError as exc:
            store.fail(
                work_id,
                WorkFailure(code=exc.code),
                self._clock(),
                attempt_number=attempt,
            )
            return self._require(work_id)

        self._results[work_id] = result
        store.succeed(
            work_id,
            attempt,
            result.digest,
            result.size,
            self._clock(),
        )
        return self._require(work_id)

    async def _resolve_material(self, record: WorkRecord) -> Material[JsonValue] | None:
        store = self._require_store()
        resolver = self._resolvers.get(record.spec.originator)
        if resolver is None:
            store.fail(record.id, WorkFailure(code="material_unavailable"), self._clock())
            return None
        material = await resolver.resolve(record.spec.material)
        if material is None or material.handle() != record.spec.material:
            store.fail(record.id, WorkFailure(code="material_integrity"), self._clock())
            return None
        return material

    @asynccontextmanager
    async def _capability_slot(self, adapter: CapabilityAdapter) -> AsyncIterator[None]:
        properties = adapter.definition.properties
        if properties.heavyweight and properties.boundary.value == "local":
            async with self._heavyweight_local:
                yield
            return
        yield

    @staticmethod
    def _idempotency_key(value: str | None) -> str | None:
        if value is None:
            return None
        if not value:
            raise ValueError("idempotency key must not be empty")
        return value

    def _require(self, work_id: str) -> WorkRecord:
        record = self._require_store().get(work_id)
        if record is None:
            raise WorkNotFound(work_id)
        return record

    def _require_store(self) -> PlatformStore:
        if self.store is None:
            raise RuntimeError("durable work requires a PlatformStore")
        return self.store
