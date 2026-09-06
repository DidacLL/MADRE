"""Reusable execution lifecycle and delayed eligibility scheduling beneath transport adapters."""

import asyncio
from contextlib import suppress
from datetime import datetime
from uuid import uuid4

from pydantic import ValidationError

from madre.config import Settings
from madre.contracts import WorkFailure, WorkRecord, WorkSubmission
from madre.inference import CapabilityError, ChatInput, invoke_chat
from madre.storage import WorkStore, utc_now


class WorkRuntime:
    def __init__(self, settings: Settings, store: WorkStore):
        self.settings = settings
        self.store = store
        self._scheduler_wake = asyncio.Event()
        self._scheduler_task: asyncio.Task[None] | None = None
        self.store.fail_interrupted(utc_now())

    async def start(self) -> None:
        if self._scheduler_task is not None:
            raise RuntimeError("work scheduler is already running")
        self._scheduler_task = asyncio.create_task(
            self._scheduler_loop(),
            name="madre-work-scheduler",
        )

    async def stop(self) -> None:
        task = self._scheduler_task
        if task is None:
            return
        self._scheduler_task = None
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task

    async def submit(
        self,
        submission: WorkSubmission,
        *,
        now: datetime | None = None,
    ) -> WorkRecord:
        accepted_at = now or utc_now()
        work_id = uuid4().hex
        self.store.create(work_id, submission, accepted_at)

        if submission.eligible_at is None or submission.eligible_at <= accepted_at:
            return await self._execute(work_id)

        self._scheduler_wake.set()
        return self._require(work_id)

    async def execute_eligible(self, *, now: datetime | None = None) -> int:
        ready_at = now or utc_now()
        executed = 0
        for work_id in self.store.list_eligible(ready_at):
            record = self.store.get(work_id)
            if record is None or record.status != "accepted":
                continue
            await self._execute(work_id)
            executed += 1
        return executed

    async def _execute(self, work_id: str) -> WorkRecord:
        submission = self._require(work_id).submission
        capability = self.settings.capabilities.get(submission.capability_id)
        if capability is None:
            self.store.fail(
                work_id,
                WorkFailure(
                    code="unknown_capability",
                    message=f"unknown configured capability: {submission.capability_id}",
                ),
                utc_now(),
            )
            return self._require(work_id)

        try:
            chat_input = ChatInput.model_validate(submission.input)
        except ValidationError:
            self.store.fail(
                work_id,
                WorkFailure(
                    code="invalid_input",
                    message="work input is not valid for the configured chat-completion capability",
                ),
                utc_now(),
            )
            return self._require(work_id)

        attempt_number = self.store.start_attempt(work_id, utc_now())
        try:
            result = await invoke_chat(capability, chat_input, submission.constraints)
        except CapabilityError as exc:
            self.store.fail(
                work_id,
                WorkFailure(code=exc.code, message=str(exc)),
                utc_now(),
                attempt_number=attempt_number,
            )
        else:
            self.store.succeed(
                work_id,
                attempt_number,
                result.model_dump(mode="json"),
                utc_now(),
            )
        return self._require(work_id)

    async def _scheduler_loop(self) -> None:
        while True:
            await self.execute_eligible()
            self._scheduler_wake.clear()
            next_eligible = self.store.next_eligible_at()
            if next_eligible is None:
                await self._scheduler_wake.wait()
                continue

            delay = max(0.0, (next_eligible - utc_now()).total_seconds())
            if delay == 0:
                continue
            try:
                await asyncio.wait_for(self._scheduler_wake.wait(), timeout=delay)
            except TimeoutError:
                pass

    def inspect(self, work_id: str) -> WorkRecord | None:
        return self.store.get(work_id)

    def _require(self, work_id: str) -> WorkRecord:
        record = self.store.get(work_id)
        if record is None:
            raise RuntimeError(f"durable work disappeared: {work_id}")
        return record
