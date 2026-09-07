import asyncio
from datetime import UTC, datetime, timedelta

import pytest
from pydantic import ValidationError

from madre import Settings
from madre.config import CapabilityConfig
from madre.contracts import WorkRetryRequest, WorkSubmission
from madre.inference import CapabilityError, ChatResult
from madre.runtime import IdempotencyConflict, WorkRuntime
from madre.storage import WorkStore, open_database

CAPABILITIES = {
    "local-chat": CapabilityConfig(
        endpoint="http://127.0.0.1:8080/v1",
        model="test-model",
    )
}


def settings(tmp_path):
    return Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)


def submission(
    application_id: str,
    label: str,
    *,
    priority: int = 0,
    eligible_at: datetime | None = None,
) -> WorkSubmission:
    return WorkSubmission.model_validate(
        {
            "application_id": application_id,
            "capability_id": "local-chat",
            "input": {
                "messages": [{"role": "user", "content": label}],
                "max_tokens": 32,
            },
            "eligible_at": eligible_at,
            "priority": priority,
            "constraints": {"timeout_seconds": 5, "local_only": True},
        }
    )


def test_priority_defaults_to_zero_and_is_bounded():
    default = WorkSubmission.model_validate(
        {
            "application_id": "application-a",
            "capability_id": "local-chat",
            "input": {"messages": [{"role": "user", "content": "default"}]},
        }
    )
    assert default.priority == 0

    for invalid in (-101, 101):
        with pytest.raises(ValidationError):
            submission("application-a", "invalid", priority=invalid)


def test_scheduler_rotates_apps_and_uses_priority_within_each_turn(tmp_path, monkeypatch):
    invocation_order = []

    async def fake_invoke(capability, request, constraints):
        label = request.messages[0].content
        invocation_order.append(label)
        return ChatResult(
            text=f"{label} result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with open_database(settings(tmp_path).data_dir) as connection:
        runtime = WorkRuntime(settings(tmp_path), WorkStore(connection))

        async def exercise():
            await runtime.submit(submission("application-a", "a-low", priority=-10))
            await runtime.submit(submission("application-a", "a-high-1", priority=100))
            await runtime.submit(submission("application-a", "a-high-2", priority=100))
            await runtime.submit(submission("application-b", "b-low-1", priority=-100))
            await runtime.submit(submission("application-b", "b-low-2", priority=-100))
            return await runtime.run_eligible()

        executed = asyncio.run(exercise())

    assert executed == 5
    assert invocation_order == [
        "a-high-1",
        "b-low-1",
        "a-high-2",
        "b-low-2",
        "a-low",
    ]


def test_retry_gets_a_new_fifo_position_inside_its_application(tmp_path, monkeypatch):
    invocation_order = []
    retry_invocations = 0
    now = [datetime(2035, 1, 1, 12, 0, tzinfo=UTC)]

    async def fake_invoke(capability, request, constraints):
        nonlocal retry_invocations
        label = request.messages[0].content
        invocation_order.append(label)
        if label == "retry-me":
            retry_invocations += 1
            if retry_invocations == 1:
                raise CapabilityError("fixture_failure", "first attempt fails")
        return ChatResult(
            text=f"{label} result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection), clock=lambda: now[0])

        async def exercise():
            original = await runtime.submit(submission("application-a", "retry-me"))
            assert await runtime.run_eligible() == 1
            failed = runtime.inspect(original.id)
            assert failed is not None
            assert failed.status == "failed"

            now[0] += timedelta(seconds=1)
            newer = await runtime.submit(submission("application-a", "newer"))
            now[0] += timedelta(seconds=1)
            await runtime.retry(
                original.id,
                WorkRetryRequest(),
                idempotency_key="retry-once",
            )
            assert await runtime.run_eligible() == 2
            return runtime.inspect(newer.id), runtime.inspect(original.id)

        newer_record, retried_record = asyncio.run(exercise())

    assert invocation_order == ["retry-me", "newer", "retry-me"]
    assert newer_record is not None and newer_record.status == "succeeded"
    assert retried_record is not None and retried_record.status == "succeeded"
    assert retried_record.attempts[-1].retry_number == 1


def test_scheduler_cursor_survives_restart(tmp_path):
    runtime_settings = settings(tmp_path)
    now = datetime(2035, 1, 1, 12, 0, tzinfo=UTC)

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        assert store.create("a-1", submission("application-a", "a-1"), now)
        assert store.create("a-2", submission("application-a", "a-2"), now)
        assert store.create("b-1", submission("application-b", "b-1"), now)

        assert store.next_eligible(now) == "a-1"
        assert store.start_attempt("a-1", now) == 1
        store.succeed("a-1", 1, {"text": "a-1 result"}, now)

    with open_database(runtime_settings.data_dir) as connection:
        recovered = WorkStore(connection)
        assert recovered.next_eligible(now) == "b-1"
        assert recovered.start_attempt("b-1", now) == 1
        recovered.succeed("b-1", 1, {"text": "b-1 result"}, now)
        assert recovered.next_eligible(now) == "a-2"


def test_newly_eligible_work_joins_fair_rotation_during_existing_backlog(tmp_path, monkeypatch):
    invocation_order = []
    now = [datetime(2035, 1, 1, 12, 0, tzinfo=UTC)]
    delayed_at = now[0] + timedelta(seconds=5)

    async def fake_invoke(capability, request, constraints):
        label = request.messages[0].content
        invocation_order.append(label)
        if label == "a-first":
            now[0] += timedelta(seconds=10)
        return ChatResult(
            text=f"{label} result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection), clock=lambda: now[0])

        async def exercise():
            await runtime.submit(submission("application-a", "a-first", priority=10))
            await runtime.submit(submission("application-a", "a-second"))
            await runtime.submit(submission("application-b", "b-delayed", eligible_at=delayed_at))
            return await runtime.run_eligible()

        executed = asyncio.run(exercise())

    assert executed == 3
    assert invocation_order == ["a-first", "b-delayed", "a-second"]


def test_priority_is_part_of_idempotent_submission_intent(tmp_path):
    runtime_settings = settings(tmp_path)

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection))

        async def exercise():
            accepted = await runtime.submit(
                submission("application-a", "same logical request", priority=5),
                idempotency_key="same-key",
            )
            replay = await runtime.submit(
                submission("application-a", "same logical request", priority=5),
                idempotency_key="same-key",
            )
            assert replay.id == accepted.id

            with pytest.raises(IdempotencyConflict):
                await runtime.submit(
                    submission("application-a", "same logical request", priority=6),
                    idempotency_key="same-key",
                )

        asyncio.run(exercise())
