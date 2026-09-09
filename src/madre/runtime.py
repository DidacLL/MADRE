"""Deterministic work lifecycle with carried security state and transient content."""

from __future__ import annotations

import asyncio
import hashlib
import json
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from datetime import datetime
from typing import Protocol
from uuid import uuid4

from pydantic import JsonValue

from madre.capabilities import CapabilityAdapter, CapabilityError, CapabilityRegistry
from madre.contracts import (
    ImmediateMaterial,
    WorkFailure,
    WorkRecord,
    WorkRetryRequest,
    WorkSpec,
    WorkSubmission,
)
from madre.security import SecurityAlgebra
from madre.storage import PlatformStore, utc_now


def content_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def content_size(payload: JsonValue) -> int:
    return len(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode())


class MaterialProvider(Protocol):
    async def resolve(self, reference: str) -> ImmediateMaterial | None: ...


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


class WorkRuntime:
    def __init__(
        self,
        store: PlatformStore,
        capabilities: CapabilityRegistry,
        *,
        clock: Callable[[], datetime] | None = None,
    ) -> None:
        self.store = store
        self.capabilities = capabilities
        self._clock = clock or utc_now
        self._schedule_changed = asyncio.Event()
        self._heavyweight_local = asyncio.Lock()
        self._materials: dict[str, ImmediateMaterial] = {}
        self._results: dict[str, JsonValue] = {}
        self._providers: dict[str, MaterialProvider] = {}
        self.store.fail_interrupted_attempts(self._clock())
        self.store.mark_unconsumed_results_lost()

    def register_material_provider(self, originator: str, provider: MaterialProvider) -> None:
        if not originator:
            raise ValueError("originator must not be empty")
        self._providers[originator] = provider

    async def submit(
        self,
        submission: WorkSubmission,
        *,
        idempotency_key: str | None = None,
    ) -> WorkRecord:
        material = submission.material
        if not material.envelope.verify_integrity():
            raise ValueError("material security envelope integrity is invalid")
        if isinstance(material, ImmediateMaterial):
            digest = content_digest(material.payload)
            if material.envelope.subject != digest:
                raise ValueError("material envelope is not bound to the submitted payload")
        else:
            digest = material.digest
            if material.envelope.subject != digest:
                raise ValueError("material envelope is not bound to the expected digest")

        security = submission.security.extend(material.envelope)
        spec = WorkSpec(
            originator=submission.originator,
            security=security,
            capability=submission.capability,
            material_reference=material.reference,
            input_digest=digest,
            material_envelope=material.envelope,
            eligible_at=submission.eligible_at,
            priority=submission.priority,
            constraints=submission.constraints,
            correlation=submission.correlation,
        )
        key = self._idempotency_key(idempotency_key)
        if key is not None:
            existing = self.store.get_by_idempotency_key(spec.originator, key)
            if existing is not None:
                if existing.spec != spec:
                    raise IdempotencyConflict("idempotency key refers to different work")
                if isinstance(material, ImmediateMaterial):
                    self._materials.setdefault(existing.id, material)
                return existing

        work_id = uuid4().hex
        admission = SecurityAlgebra.evaluate(security)
        self.store.record_security_decision(
            crossing_id=work_id,
            crossing_kind="work-admission",
            target_id=submission.capability.capability_id or submission.capability.kind,
            context=security,
            execution_boundary=None,
            decision=admission,
        )
        if not admission.admissible:
            raise ValueError(f"security algebra rejected work: {','.join(admission.deficits)}")

        accepted_at = self._clock()
        created = self.store.create(work_id, spec, accepted_at, idempotency_key=key)
        if not created:
            assert key is not None
            existing = self.store.get_by_idempotency_key(spec.originator, key)
            if existing is None:
                raise RuntimeError("idempotent work disappeared")
            if existing.spec != spec:
                raise IdempotencyConflict("idempotency key refers to different work")
            return existing
        if isinstance(material, ImmediateMaterial):
            self._materials[work_id] = material
        self._schedule_changed.set()
        return self._require(work_id)

    async def run_eligible(self) -> int:
        executed = 0
        while work_id := self.store.next_eligible(self._clock()):
            await self._execute(work_id)
            executed += 1
        return executed

    async def run_scheduler(self) -> None:
        while True:
            self._schedule_changed.clear()
            await self.run_eligible()
            next_eligibility = self.store.next_eligibility()
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
        key = self._idempotency_key(idempotency_key)
        assert key is not None
        prior = self.store.retry_policy_by_key(work_id, key)
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
        if (
            self.store.requeue_failed(
                work_id,
                key,
                request.allow_unknown_outcome,
                self._clock(),
            )
            is None
        ):
            raise RetryConflict("work is no longer failed")
        self._schedule_changed.set()
        return self._require(work_id)

    async def cancel(self, work_id: str) -> WorkRecord:
        record = self._require(work_id)
        if record.cancellation is not None:
            return record
        disposition = self.store.request_cancellation(work_id, self._clock())
        if disposition == "terminal":
            raise CancellationConflict("terminal work cannot be cancelled")
        if disposition is None:
            raise WorkNotFound(work_id)
        if disposition == "prevented":
            self._discard_material(work_id)
        self._schedule_changed.set()
        return self._require(work_id)

    def inspect(self, work_id: str) -> WorkRecord | None:
        return self.store.get(work_id)

    def consume_result(self, work_id: str) -> JsonValue:
        record = self._require(work_id)
        if record.result is None:
            raise ResultUnavailable("work has no produced result")
        if record.result.delivery_status == "lost":
            raise ResultLost("result content was lost before consumption")
        if record.result.delivery_status == "consumed":
            raise ResultUnavailable("result content was already consumed")
        if work_id not in self._results:
            self.store.mark_result_lost(work_id)
            raise ResultLost("transient result content is unavailable")
        result = self._results.pop(work_id)
        self.store.mark_result_consumed(work_id)
        return result

    async def _execute(self, work_id: str) -> WorkRecord:
        record = self._require(work_id)
        if record.status != "accepted":
            return record
        if record.spec.eligible_at is not None and record.spec.eligible_at > self._clock():
            return record

        candidates = self.capabilities.candidates(record.spec.capability, record.spec.constraints)
        if not candidates:
            self._discard_material(work_id)
            self.store.fail(work_id, WorkFailure(code="no_capability"), self._clock())
            return self._require(work_id)

        material = await self._resolve_material(work_id, record)
        if material is None:
            return self._require(work_id)

        adapter: CapabilityAdapter | None = None
        for candidate in candidates:
            descriptor = candidate.descriptor
            context = record.spec.security.extend(descriptor.security)
            decision = SecurityAlgebra.evaluate(context)
            self.store.record_security_decision(
                crossing_id=work_id,
                crossing_kind="capability-candidate",
                target_id=descriptor.id,
                context=context,
                execution_boundary=descriptor.execution_boundary,
                decision=decision,
            )
            if decision.admissible:
                adapter = candidate
                break

        if adapter is None:
            self._discard_material(work_id)
            self.store.fail(work_id, WorkFailure(code="security_denied"), self._clock())
            return self._require(work_id)

        descriptor = adapter.descriptor
        result: JsonValue = None
        failure: WorkFailure | None = None
        async with self._capability_slot(adapter):
            attempt = self.store.start_attempt(
                work_id,
                descriptor.id,
                descriptor.model_id,
                descriptor.execution_boundary,
                self._clock(),
            )
            if attempt is None:
                return self._require(work_id)
            try:
                result = await adapter.execute(material.payload, record.spec.constraints)
            except CapabilityError as exc:
                failure = WorkFailure(code=exc.code or "capability_error")
            except Exception:
                failure = WorkFailure(code="internal_error")

        self._discard_material(work_id)
        if failure is not None:
            self.store.fail(work_id, failure, self._clock(), attempt_number=attempt)
            return self._require(work_id)

        digest = content_digest(result)
        size = content_size(result)
        self._results[work_id] = result
        self.store.succeed(work_id, attempt, digest, size, self._clock())
        return self._require(work_id)

    async def _resolve_material(self, work_id: str, record: WorkRecord) -> ImmediateMaterial | None:
        material = self._materials.get(work_id)
        if material is None:
            provider = self._providers.get(record.spec.originator)
            if provider is not None:
                material = await provider.resolve(record.spec.material_reference)
        if material is None:
            self._discard_material(work_id)
            self.store.fail(work_id, WorkFailure(code="material_unavailable"), self._clock())
            return None
        digest = content_digest(material.payload)
        if (
            digest != record.spec.input_digest
            or material.reference != record.spec.material_reference
            or material.envelope != record.spec.material_envelope
            or not material.envelope.verify_integrity()
        ):
            self._discard_material(work_id)
            self.store.fail(work_id, WorkFailure(code="material_integrity"), self._clock())
            return None
        return material

    @asynccontextmanager
    async def _capability_slot(self, adapter: CapabilityAdapter) -> AsyncIterator[None]:
        descriptor = adapter.descriptor
        if descriptor.heavyweight and descriptor.execution_boundary == "local":
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

    def _discard_material(self, work_id: str) -> None:
        self._materials.pop(work_id, None)

    def _require(self, work_id: str) -> WorkRecord:
        record = self.store.get(work_id)
        if record is None:
            raise WorkNotFound(work_id)
        return record
