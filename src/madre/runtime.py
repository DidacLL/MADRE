"""Reusable execution lifecycle beneath transport adapters."""

from datetime import datetime
from uuid import uuid4

from pydantic import ValidationError

from madre.config import Settings
from madre.contracts import WorkFailure, WorkRecord, WorkSubmission
from madre.inference import CapabilityError, ChatInput, invoke_chat
from madre.storage import WorkStore, utc_now


class DelayedExecutionUnavailable(RuntimeError):
    pass


class WorkRuntime:
    def __init__(self, settings: Settings, store: WorkStore):
        self.settings = settings
        self.store = store
        self.store.fail_interrupted(utc_now())

    async def execute_immediate(
        self,
        submission: WorkSubmission,
        *,
        now: datetime | None = None,
    ) -> WorkRecord:
        accepted_at = now or utc_now()
        if submission.eligible_at is not None and submission.eligible_at > accepted_at:
            raise DelayedExecutionUnavailable(
                "future eligibility is not implemented yet; submit work when it becomes eligible"
            )

        work_id = uuid4().hex
        self.store.create(work_id, submission, accepted_at)

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

    def inspect(self, work_id: str) -> WorkRecord | None:
        return self.store.get(work_id)

    def _require(self, work_id: str) -> WorkRecord:
        record = self.store.get(work_id)
        if record is None:
            raise RuntimeError(f"durable work disappeared: {work_id}")
        return record
