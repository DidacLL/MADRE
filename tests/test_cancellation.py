import asyncio
import time
from datetime import UTC, datetime

import httpx
import pytest
from fastapi.testclient import TestClient

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.contracts import WorkFailure, WorkRetryRequest, WorkSubmission
from madre.inference import ChatResult
from madre.runtime import RetryConflict, WorkRuntime
from madre.storage import WorkStore, open_database, utc_now
from madre_core import CoreClient, CoreRuntimeError

AUTH = {"Authorization": "Bearer test-token"}
CAPABILITIES = {
    "local-chat": CapabilityConfig(
        endpoint="http://127.0.0.1:8080/v1",
        model="test-model",
    )
}
SUBMISSION = {
    "application_id": "cancel-app",
    "capability_id": "local-chat",
    "input": {
        "messages": [{"role": "user", "content": "cancel me"}],
        "max_tokens": 32,
    },
    "constraints": {"timeout_seconds": 5, "local_only": True},
}


def settings(tmp_path):
    return Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)


def wait_for_terminal(client: TestClient, work_id: str, timeout: float = 2.0) -> dict:
    deadline = time.monotonic() + timeout
    while True:
        response = client.get(f"/v1/work/{work_id}", headers=AUTH)
        assert response.status_code == 200
        work = response.json()
        if work["status"] in {"succeeded", "failed", "cancelled"}:
            return work
        if time.monotonic() >= deadline:
            raise AssertionError(f"work {work_id} did not become terminal: {work}")
        time.sleep(0.01)


def test_future_work_can_be_cancelled_before_any_attempt_and_survives_restart(
    tmp_path, monkeypatch
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    invocations = 0

    async def fake_invoke(capability, request, constraints):
        nonlocal invocations
        invocations += 1
        raise AssertionError("cancelled work must not invoke its capability")

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)
    submission = {**SUBMISSION, "eligible_at": "2099-01-01T00:00:00Z"}

    with TestClient(create_app(runtime_settings)) as client:
        accepted = client.post(
            "/v1/work",
            headers={**AUTH, "Idempotency-Key": "cancel-submission"},
            json=submission,
        ).json()
        assert accepted["status"] == "accepted"
        assert accepted["cancellation"] is None

        response = client.post(f"/v1/work/{accepted['id']}/cancel", headers=AUTH)
        assert response.status_code == 200
        cancelled = response.json()
        assert cancelled["status"] == "cancelled"
        assert cancelled["completed_at"] is not None
        assert cancelled["failure"] is None
        assert cancelled["result"] is None
        assert cancelled["attempts"] == []
        assert cancelled["cancellation"]["disposition"] == "prevented"

        duplicate = client.post(f"/v1/work/{accepted['id']}/cancel", headers=AUTH)
        assert duplicate.status_code == 200
        assert duplicate.json() == cancelled

        replay = client.post(
            "/v1/work",
            headers={**AUTH, "Idempotency-Key": "cancel-submission"},
            json=submission,
        )
        assert replay.status_code == 201
        assert replay.json() == cancelled

    with TestClient(create_app(runtime_settings)) as client:
        recovered = client.get(f"/v1/work/{accepted['id']}", headers=AUTH)
        assert recovered.status_code == 200
        assert recovered.json() == cancelled

    assert invocations == 0


def test_completed_work_rejects_new_cancellation_and_missing_work_is_404(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def fake_invoke(capability, request, constraints):
        return ChatResult(
            text="completed",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        accepted = client.post("/v1/work", headers=AUTH, json=SUBMISSION).json()
        succeeded = wait_for_terminal(client, accepted["id"])
        assert succeeded["status"] == "succeeded"

        conflict = client.post(f"/v1/work/{accepted['id']}/cancel", headers=AUTH)
        assert conflict.status_code == 409
        assert conflict.json()["detail"] == "completed work cannot be cancelled"

        missing = client.post("/v1/work/missing/cancel", headers=AUTH)
        assert missing.status_code == 404


def test_running_cancellation_records_intent_without_releasing_physical_execution(
    tmp_path, monkeypatch
):
    runtime_settings = settings(tmp_path)

    async def exercise():
        started = asyncio.Event()
        release = asyncio.Event()
        invocations = 0

        async def fake_invoke(capability, request, constraints):
            nonlocal invocations
            invocations += 1
            started.set()
            await release.wait()
            return ChatResult(
                text="physical invocation finished",
                model="test-model",
                finish_reason="stop",
                elapsed_seconds=0.01,
            )

        monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
        with open_database(runtime_settings.data_dir) as connection:
            runtime = WorkRuntime(runtime_settings, WorkStore(connection))
            accepted = await runtime.submit(WorkSubmission.model_validate(SUBMISSION))
            execution = asyncio.create_task(runtime.run_eligible())
            await asyncio.wait_for(started.wait(), timeout=1)

            running = runtime.inspect(accepted.id)
            assert running is not None
            assert running.status == "running"
            assert len(running.attempts) == 1
            assert running.attempts[0].status == "running"

            cancellation = await runtime.cancel(accepted.id)
            assert cancellation.status == "running"
            assert cancellation.cancellation is not None
            assert cancellation.cancellation.disposition == "requested_while_running"

            await asyncio.sleep(0)
            assert not execution.done()
            duplicate = await runtime.cancel(accepted.id)
            assert duplicate == cancellation
            assert invocations == 1

            release.set()
            assert await execution == 1
            completed = runtime.inspect(accepted.id)
            assert completed is not None
            return completed, invocations

    completed, invocations = asyncio.run(exercise())

    assert invocations == 1
    assert completed.status == "succeeded"
    assert completed.result is not None
    assert completed.result["text"] == "physical invocation finished"
    assert completed.cancellation is not None
    assert completed.cancellation.disposition == "requested_while_running"
    assert len(completed.attempts) == 1
    assert completed.attempts[0].status == "succeeded"


def test_running_cancellation_survives_interruption_and_blocks_retry(tmp_path):
    runtime_settings = settings(tmp_path)
    work_id = "cancelled-running-work"
    submission = WorkSubmission.model_validate(SUBMISSION)

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        store.create(work_id, submission, utc_now())
        store.start_attempt(work_id, utc_now())
        assert store.request_cancellation(work_id, utc_now()) == "requested_while_running"

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection))
        interrupted = runtime.inspect(work_id)
        assert interrupted is not None
        assert interrupted.status == "failed"
        assert interrupted.failure is not None
        assert interrupted.failure.code == "interrupted"
        assert interrupted.cancellation is not None
        assert interrupted.cancellation.disposition == "requested_while_running"

        async def retry():
            with pytest.raises(
                RetryConflict, match="work with a cancellation request cannot be retried"
            ):
                await runtime.retry(
                    work_id,
                    WorkRetryRequest(allow_unknown_outcome=True),
                    idempotency_key="retry-cancelled-running",
                )

        asyncio.run(retry())


def test_an_accepted_retry_can_still_be_cancelled_before_its_next_attempt(tmp_path):
    runtime_settings = settings(tmp_path)
    work_id = "cancel-retry"
    submission = WorkSubmission.model_validate(SUBMISSION)
    clock = datetime(2035, 1, 1, 12, 0, tzinfo=UTC)

    async def exercise():
        with open_database(runtime_settings.data_dir) as connection:
            store = WorkStore(connection)
            store.create(work_id, submission, clock)
            store.fail(
                work_id,
                WorkFailure(code="connection", message="temporary failure"),
                clock,
            )
            runtime = WorkRuntime(runtime_settings, store, clock=lambda: clock)
            retried = await runtime.retry(
                work_id,
                WorkRetryRequest(),
                idempotency_key="retry-then-cancel",
            )
            assert retried.status == "accepted"
            assert len(retried.retries) == 1

            cancelled = await runtime.cancel(work_id)
            return cancelled

    cancelled = asyncio.run(exercise())

    assert cancelled.status == "cancelled"
    assert cancelled.cancellation is not None
    assert cancelled.cancellation.disposition == "prevented"
    assert len(cancelled.retries) == 1
    assert cancelled.retries[0].previous_failure.code == "connection"
    assert cancelled.attempts == []


def test_core_client_treats_cancelled_work_as_terminal_runtime_control():
    inspections = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal inspections
        if request.method == "POST":
            return httpx.Response(
                201,
                json={"id": "work-1", "status": "accepted", "result": None, "failure": None},
            )
        inspections += 1
        return httpx.Response(
            200,
            json={"id": "work-1", "status": "cancelled", "result": None, "failure": None},
        )

    client = CoreClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        poll_interval_seconds=0.001,
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(CoreRuntimeError, match="was cancelled before execution"):
        asyncio.run(client.complete([{"role": "user", "content": "hello"}]))
    assert inspections == 1
