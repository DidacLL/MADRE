"""Deterministic transient inference and durable work over opaque transient material."""

from __future__ import annotations

import asyncio
import hashlib
import json
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from datetime import datetime
from typing import cast
from uuid import uuid4

from pydantic import JsonValue

from madre.capabilities import CapabilityAdapter, CapabilityError, CapabilityRegistry
from madre.contracts import (
    TransientInferenceRequest,
    TransientInferenceResult,
    TransientMaterial,
    WorkFailure,
    WorkRecord,
    WorkRetryRequest,
    WorkSpec,
    WorkSubmission,
)
from madre.interfaces import MaterialResolver
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    CapabilitySecurityValues,
    Disclosure,
    MaterialSecurityValues,
    OrdinarySecurityLevel,
    SecurityDecision,
    SecurityEvaluator,
    SecurityHistory,
    SecurityLevel,
    SecurityTransition,
    StructuralFailure,
)
from madre.storage import PlatformStore, utc_now


def content_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def content_size(payload: JsonValue) -> int:
    return len(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode())


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


class TransientInferenceError(RuntimeError):
    def __init__(self, code: str) -> None:
        self.code = code
        super().__init__(code)


class WorkRuntime:
    def __init__(
        self,
        store: PlatformStore,
        capabilities: CapabilityRegistry,
        *,
        clock: Callable[[], datetime] | None = None,
        security_evaluator: SecurityEvaluator = DEFAULT_SECURITY_EVALUATOR,
    ) -> None:
        self.store = store
        self.capabilities = capabilities
        self._clock = clock or utc_now
        self._security_evaluator = security_evaluator
        self._schedule_changed = asyncio.Event()
        self._heavyweight_local = asyncio.Lock()
        self._results: dict[str, JsonValue] = {}
        self._resolvers: dict[str, MaterialResolver] = {}
        self.store.fail_interrupted_attempts(self._clock())
        self.store.mark_unconsumed_results_lost()

    def register_material_resolver(self, originator: str, resolver: MaterialResolver) -> None:
        if not originator:
            raise ValueError("originator must not be empty")
        self._resolvers[originator] = resolver

    async def infer(self, request: TransientInferenceRequest) -> TransientInferenceResult:
        self._verify_transient_material(request.material)
        invocation_id = uuid4().hex
        history = request.security.merge(request.material.history).extend(
            objects=(request.material.security,)
        )
        self._require_structural_history(
            crossing_id=invocation_id,
            crossing_kind="transient-admission",
            target_id=request.inference.hard.mechanism_id or request.inference.hard.specialization,
            history=history,
        )

        candidates = self.capabilities.candidates(request.inference)
        selection = self._select_admissible_capability(
            crossing_id=invocation_id,
            crossing_kind="transient-capability-candidate",
            history=history,
            material_security_id=request.material.security.security_id,
            candidates=candidates,
        )
        if selection is None:
            raise TransientInferenceError("no_capability" if not candidates else "security_denied")
        adapter, prospective, transition = selection
        descriptor = adapter.descriptor
        accepted = prospective.extend(transitions=(transition,))
        try:
            async with self._capability_slot(adapter):
                result = await adapter.execute(request.material.payload, request.constraints)
        except CapabilityError as exc:
            raise TransientInferenceError(exc.code or "capability_error") from exc
        except Exception as exc:
            raise TransientInferenceError("internal_error") from exc

        return TransientInferenceResult(
            payload=result,
            capability_id=descriptor.id,
            provider_id=descriptor.provider_id,
            model_id=descriptor.model_id,
            execution_boundary=descriptor.execution_boundary,
            output_digest=content_digest(result),
            output_size=content_size(result),
            output_assurance=self._generated_assurance(
                request.material, descriptor.security.values
            ),
            producer_security_ids=(descriptor.security.security_id,),
            source_security_ids=(request.material.security.security_id,),
            security=accepted,
        )

    async def submit(
        self,
        submission: WorkSubmission,
        *,
        idempotency_key: str | None = None,
    ) -> WorkRecord:
        handle = submission.material
        security = submission.security.merge(handle.history).extend(objects=(handle.security,))
        spec = WorkSpec(
            originator=submission.originator,
            security=security,
            inference=submission.inference,
            material=handle,
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
                return existing

        work_id = uuid4().hex
        self._require_structural_history(
            crossing_id=work_id,
            crossing_kind="work-admission",
            target_id=submission.inference.hard.mechanism_id
            or submission.inference.hard.specialization,
            history=security,
        )

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
            self.store.requeue_failed(work_id, key, request.allow_unknown_outcome, self._clock())
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

        candidates = self.capabilities.candidates(record.spec.inference)
        if not candidates:
            self.store.fail(work_id, WorkFailure(code="no_capability"), self._clock())
            return self._require(work_id)

        selection = self._select_admissible_capability(
            crossing_id=work_id,
            crossing_kind="capability-candidate",
            history=record.spec.security,
            material_security_id=record.spec.material.security.security_id,
            candidates=candidates,
        )
        if selection is None:
            self.store.fail(work_id, WorkFailure(code="security_denied"), self._clock())
            return self._require(work_id)
        adapter, prospective, transition = selection
        descriptor = adapter.descriptor
        result: JsonValue = None
        failure: WorkFailure | None = None
        attempt: int | None = None
        material: TransientMaterial | None = None
        async with self._capability_slot(adapter):
            material = await self._resolve_material(record)
            if material is None:
                return self._require(work_id)
            accepted_history = prospective.extend(transitions=(transition,))
            attempt = self.store.start_attempt(
                work_id,
                descriptor.id,
                descriptor.provider_id,
                descriptor.model_id,
                descriptor.execution_boundary,
                transition.transition_id,
                self._clock(),
            )
            if attempt is None:
                return self._require(work_id)
            self.store.update_security_history(work_id, accepted_history)
            try:
                result = await adapter.execute(material.payload, record.spec.constraints)
            except CapabilityError as exc:
                failure = WorkFailure(code=exc.code or "capability_error")
            except Exception:
                failure = WorkFailure(code="internal_error")

        assert attempt is not None and material is not None
        if failure is not None:
            self.store.fail(work_id, failure, self._clock(), attempt_number=attempt)
            return self._require(work_id)

        digest = content_digest(result)
        size = content_size(result)
        output_assurance = self._generated_assurance(material, descriptor.security.values)
        self._results[work_id] = result
        self.store.succeed(
            work_id,
            attempt,
            digest,
            size,
            output_assurance,
            (descriptor.security.security_id,),
            (material.security.security_id,),
            self._clock(),
        )
        return self._require(work_id)

    async def _resolve_material(self, record: WorkRecord) -> TransientMaterial | None:
        resolver = self._resolvers.get(record.spec.originator)
        if resolver is None:
            self.store.fail(record.id, WorkFailure(code="material_unavailable"), self._clock())
            return None
        material = await resolver.resolve(record.spec.material)
        if material is None:
            self.store.fail(record.id, WorkFailure(code="material_unavailable"), self._clock())
            return None
        if (
            material.reference != record.spec.material.reference
            or material.digest != record.spec.material.digest
            or content_digest(material.payload) != record.spec.material.digest
            or material.security != record.spec.material.security
            or material.history != record.spec.material.history
            or not material.security.verify_binding()
        ):
            self.store.fail(record.id, WorkFailure(code="material_assurance"), self._clock())
            return None
        return material

    def _select_admissible_capability(
        self,
        *,
        crossing_id: str,
        crossing_kind: str,
        history: SecurityHistory,
        material_security_id: str,
        candidates: tuple[CapabilityAdapter, ...],
    ) -> tuple[CapabilityAdapter, SecurityHistory, SecurityTransition] | None:
        for candidate in candidates:
            descriptor = candidate.descriptor
            prospective = history.extend(
                objects=(descriptor.security, *descriptor.disclosure_boundaries)
            )
            transition = SecurityTransition.issue(
                disclosures=(
                    Disclosure(
                        material_security_id=material_security_id,
                        boundary_security_ids=tuple(
                            x.security_id for x in descriptor.disclosure_boundaries
                        ),
                    ),
                )
            )
            decision = self._security_evaluator.evaluate(prospective, transition)
            self.store.record_security_decision(
                crossing_id=crossing_id,
                crossing_kind=crossing_kind,
                target_id=descriptor.id,
                history=prospective,
                transition=transition,
                execution_boundary=descriptor.execution_boundary,
                decision=decision,
            )
            if decision.admissible:
                return candidate, prospective, transition
        return None

    def _require_structural_history(
        self,
        *,
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        history: SecurityHistory,
    ) -> None:
        transition = SecurityTransition.issue()
        decision = self._security_evaluator.evaluate(history, transition)
        completed = set(self.store.completed_derivation_ids())
        if any(
            relation.kind == "transform" and relation.derivation_id not in completed
            for relation in history.derivations
        ):
            decision = SecurityDecision(
                admissible=False,
                transition_id=transition.transition_id,
                failures=(
                    *decision.failures,
                    StructuralFailure(code="unverified_transform_execution"),
                ),
            )
        self.store.record_security_decision(
            crossing_id=crossing_id,
            crossing_kind=crossing_kind,
            target_id=target_id,
            history=history,
            transition=transition,
            execution_boundary=None,
            decision=decision,
        )
        if not decision.admissible:
            if crossing_kind.startswith("transient"):
                raise TransientInferenceError("security_denied")
            raise ValueError(
                f"security evaluator rejected work: {','.join(decision.failure_codes)}"
            )

    @staticmethod
    def _generated_assurance(
        material: TransientMaterial,
        capability_values: object,
    ) -> OrdinarySecurityLevel:
        material_values = cast(MaterialSecurityValues, material.security.values)
        if material_values.assurance is None:
            raise TransientInferenceError("security_denied")
        if (
            not isinstance(capability_values, CapabilitySecurityValues)
            or capability_values.assurance is None
        ):
            raise TransientInferenceError("security_denied")
        value = min(int(material_values.assurance), int(capability_values.assurance))
        return cast(OrdinarySecurityLevel, SecurityLevel(value))

    @staticmethod
    def _verify_transient_material(material: TransientMaterial) -> None:
        if content_digest(material.payload) != material.digest:
            raise TransientInferenceError("material_assurance")
        if not material.security.verify_binding():
            raise TransientInferenceError("material_assurance")

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

    def _require(self, work_id: str) -> WorkRecord:
        record = self.store.get(work_id)
        if record is None:
            raise WorkNotFound(work_id)
        return record
