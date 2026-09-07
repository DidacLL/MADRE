"""Reusable execution lifecycle beneath transport adapters."""

import asyncio
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from datetime import datetime
from uuid import uuid4

from pydantic import ValidationError

from madre.config import CapabilityConfig, Settings
from madre.contracts import WorkFailure, WorkRecord, WorkRetryRequest, WorkSubmission
from madre.inference import CapabilityError, ChatInput, ChatResult, invoke_chat
from madre.storage import WorkStore, utc_now


class IdempotencyConflict(RuntimeError):
    """An application reused one submission key for different work."""


class WorkNotFound(RuntimeError):
    """A requested durable work record does not exist."""


class RetryConflict(RuntimeError):
    """A retry request conflicts with durable work state or prior retry intent."""


class CancellationConflict(RuntimeError):
    """A cancellation request conflicts with already terminal work."""


class WorkRuntime:
    def __init__(
        self,
        settings: Settings,
        store: WorkStore,
        *,
        clock: Callable[[], datetime] | None = None,
    ):
        self.settings = settings
        self.store = store
        self._clock = clock or utc_now
        self._schedule_changed = asyncio.Event()
        self._heavyweight_local_inference = asyncio.Lock()
        self.store.fail_interrupted_attempts(self._clock())

    async def submit(
        self, submission: WorkSubmission, *, idempotency_key: str | None = None
    ) -> WorkRecord:
        if idempotency_key is not None:
            existing = self.store.get_by_idempotency_key(submission.application_id, idempotency_key)
            if existing is not None:
                return self._replay(existing, submission)

        accepted_at = self._clock()
        work_id = uuid4().hex
        created = self.store.create(
            work_id,
            submission,
            accepted_at,
            idempotency_key=idempotency_key,
        )
        if not created:
            assert idempotency_key is not None
            existing = self.store.get_by_idempotency_key(submission.application_id, idempotency_key)
            if existing is None:
                raise RuntimeError("durable idempotent work disappeared")
            return self._replay(existing, submission)

        self._schedule_changed.set()
        return self._require(work_id)

    async def retry(
        self,
        work_id: str,
        request: WorkRetryRequest,
        *,
        idempotency_key: str,
    ) -> WorkRecord:
        prior_policy = self.store.retry_policy_by_key(work_id, idempotency_key)
        if prior_policy is not None:
            if prior_policy != request.allow_unknown_outcome:
                raise RetryConflict(
                    "idempotency key was already used with a different retry policy"
                )
            return self._require(work_id)

        record = self.store.get(work_id)
        if record is None:
            raise WorkNotFound(f"work not found: {work_id}")
        if record.status != "failed":
            raise RetryConflict("only failed work can be retried")
        if record.cancellation is not None:
            raise RetryConflict("work with a cancellation request cannot be retried")
        if (
            record.failure is not None
            and record.failure.code == "interrupted"
            and not request.allow_unknown_outcome
        ):
            raise RetryConflict(
                "interrupted work has an unknown capability outcome; "
                "set allow_unknown_outcome=true to permit another invocation"
            )

        retry_number = self.store.requeue_failed(
            work_id,
            idempotency_key,
            request.allow_unknown_outcome,
            self._clock(),
        )
        if retry_number is None:
            raise RetryConflict("work is no longer failed")

        self._schedule_changed.set()
        return self._require(work_id)

    async def cancel(self, work_id: str) -> WorkRecord:
        record = self.store.get(work_id)
        if record is None:
            raise WorkNotFound(f"work not found: {work_id}")
        if record.cancellation is not None:
            return record

        disposition = self.store.request_cancellation(work_id, self._clock())
        if disposition is None:
            raise WorkNotFound(f"work not found: {work_id}")
        if disposition == "terminal":
            raise CancellationConflict("completed work cannot be cancelled")
        if disposition == "prevented":
            self._schedule_changed.set()
        return self._require(work_id)

    async def run_eligible(self) -> int:
        """Execute all work eligible at the current runtime clock."""
        eligible_at = self._clock()
        executed = 0
        while work_id := self.store.next_eligible(eligible_at):
            await self._execute_accepted(work_id)
            executed += 1
        return executed

    async def run_scheduler(self) -> None:
        """Continuously recover and execute accepted work when it becomes eligible."""
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

    async def _execute_accepted(self, work_id: str) -> WorkRecord:
        record = self._require(work_id)
        if record.status != "accepted":
            return record

        now = self._clock()
        if record.submission.eligible_at is not None and record.submission.eligible_at > now:
            return record

        capability = self.settings.capabilities.get(record.submission.capability_id)
        if capability is None:
            self.store.fail(
                work_id,
                WorkFailure(
                    code="unknown_capability",
                    message=f"unknown configured capability: {record.submission.capability_id}",
                ),
                self._clock(),
            )
            return self._require(work_id)

        try:
            chat_input = ChatInput.model_validate(record.submission.input)
        except ValidationError:
            self.store.fail(
                work_id,
                WorkFailure(
                    code="invalid_input",
                    message="work input is not valid for the configured chat-completion capability",
                ),
                self._clock(),
            )
            return self._require(work_id)

        result: ChatResult | None = None
        failure: WorkFailure | None = None
        async with self._heavyweight_local_inference_slot(capability):
            attempt_number = self.store.start_attempt(work_id, self._clock())
            if attempt_number is None:
                return self._require(work_id)

            try:
                result = await invoke_chat(capability, chat_input, record.submission.constraints)
            except CapabilityError as exc:
                failure = WorkFailure(code=exc.code, message=str(exc))
            except Exception:
                failure = WorkFailure(
                    code="internal_error",
                    message="capability execution failed unexpectedly",
                )

        if failure is not None:
            self.store.fail(
                work_id,
                failure,
                self._clock(),
                attempt_number=attempt_number,
            )
        else:
            assert result is not None
            self.store.succeed(
                work_id,
                attempt_number,
                result.model_dump(mode="json"),
                self._clock(),
            )
        return self._require(work_id)

    @asynccontextmanager
    async def _heavyweight_local_inference_slot(
        self, capability: CapabilityConfig
    ) -> AsyncIterator[None]:
        if capability.kind == "chat_completions" and capability.boundary == "local":
            async with self._heavyweight_local_inference:
                yield
            return
        yield

    def inspect(self, work_id: str) -> WorkRecord | None:
        return self.store.get(work_id)

    @staticmethod
    def _replay(existing: WorkRecord, submission: WorkSubmission) -> WorkRecord:
        if existing.submission != submission:
            raise IdempotencyConflict(
                "idempotency key was already used with a different work submission"
            )
        return existing

    def _require(self, work_id: str) -> WorkRecord:
        record = self.store.get(work_id)
        if record is None:
            raise RuntimeError(f"durable work disappeared: {work_id}")
        return record
