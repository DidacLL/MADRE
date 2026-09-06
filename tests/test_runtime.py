import asyncio
import json
import sqlite3
import threading
import time
from contextlib import suppress
from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.contracts import WorkSubmission
from madre.inference import CapabilityError, ChatResult
from madre.runtime import WorkRuntime
from madre.storage import WorkStore, open_database, utc_now

AUTH = {"Authorization": "Bearer test-token"}
CAPABILITIES = {
    "local-chat": CapabilityConfig(
        endpoint="http://127.0.0.1:8080/v1",
        model="test-model",
    )
}
SUBMISSION = {
    "application_id": "independent-app",
    "capability_id": "local-chat",
    "input": {
        "messages": [{"role": "user", "content": "Hello"}],
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
        if work["status"] in {"succeeded", "failed"}:
            return work
        if time.monotonic() >= deadline:
            raise AssertionError(f"work {work_id} did not become terminal: {work}")
        time.sleep(0.01)


def submission_for(application_id: str, label: str) -> dict:
    return {
        **SUBMISSION,
        "application_id": application_id,
        "input": {
            "messages": [{"role": "user", "content": label}],
            "max_tokens": 32,
        },
    }


def test_http_immediate_work_is_accepted_before_capability_completion_and_persists_success(
    tmp_path, monkeypatch
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    entered = threading.Event()
    release = threading.Event()

    async def fake_invoke(capability, request, constraints):
        assert capability.model == "test-model"
        assert request.messages[0].content == "Hello"
        assert constraints.local_only
        entered.set()
        while not release.is_set():
            await asyncio.sleep(0.001)
        return ChatResult(
            text="Hello from inference",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        response = client.post("/v1/work", headers=AUTH, json=SUBMISSION)
        assert response.status_code == 201
        accepted = response.json()
        assert accepted["status"] == "accepted"
        assert accepted["submission"]["application_id"] == "independent-app"
        assert accepted["attempts"] == []
        assert accepted["result"] is None
        work_id = accepted["id"]

        assert entered.wait(1)
        running = client.get(f"/v1/work/{work_id}", headers=AUTH).json()
        assert running["status"] == "running"
        assert running["attempts"][0]["status"] == "running"

        release.set()
        completed = wait_for_terminal(client, work_id)
        assert completed["status"] == "succeeded"
        assert completed["result"]["text"] == "Hello from inference"
        assert completed["attempts"][0]["status"] == "succeeded"

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        row = connection.execute(
            "SELECT status, result_json FROM runtime_work WHERE id = ?", (work_id,)
        ).fetchone()
    assert row is not None
    assert row[0] == "succeeded"
    assert "Hello from inference" in row[1]


def test_completed_submission_request_does_not_own_accepted_execution(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    entered = threading.Event()
    release = threading.Event()

    async def fake_invoke(capability, request, constraints):
        entered.set()
        while not release.is_set():
            await asyncio.sleep(0.001)
        return ChatResult(
            text="runtime-owned result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        response = client.post("/v1/work", headers=AUTH, json=SUBMISSION)
        accepted = response.json()
        assert accepted["status"] == "accepted"
        response.close()

        assert entered.wait(1)
        work_id = accepted["id"]
        after_request = client.get(f"/v1/work/{work_id}", headers=AUTH).json()
        assert after_request["status"] == "running"

        release.set()
        completed = wait_for_terminal(client, work_id)
        assert completed["status"] == "succeeded"
        assert completed["result"]["text"] == "runtime-owned result"


def test_capability_failure_is_durable_and_inspectable_after_restart(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def fail_invoke(capability, request, constraints):
        raise CapabilityError("connection", "could not communicate with capability endpoint")

    monkeypatch.setattr("madre.runtime.invoke_chat", fail_invoke)
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        response = client.post("/v1/work", headers=AUTH, json=SUBMISSION)
        assert response.status_code == 201
        accepted = response.json()
        assert accepted["status"] == "accepted"
        work = wait_for_terminal(client, accepted["id"])
        assert work["status"] == "failed"
        assert work["failure"] == {
            "code": "connection",
            "message": "could not communicate with capability endpoint",
        }
        assert work["attempts"][0]["status"] == "failed"
        work_id = work["id"]

    with TestClient(create_app(runtime_settings)) as client:
        inspected = client.get(f"/v1/work/{work_id}", headers=AUTH)
        assert inspected.status_code == 200
        assert inspected.json() == work


def test_http_second_application_is_accepted_while_local_inference_is_occupied(
    tmp_path, monkeypatch
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    first_entered = threading.Event()
    release_first = threading.Event()
    second_entered = threading.Event()
    invocation_order = []
    active_invocations = 0
    maximum_active = 0

    async def fake_invoke(capability, request, constraints):
        nonlocal active_invocations, maximum_active
        label = request.messages[0].content
        active_invocations += 1
        maximum_active = max(maximum_active, active_invocations)
        invocation_order.append(f"{label}:entered")
        try:
            if label == "first":
                first_entered.set()
                while not release_first.is_set():
                    await asyncio.sleep(0.001)
            else:
                second_entered.set()
            invocation_order.append(f"{label}:completed")
            return ChatResult(
                text=f"{label} result",
                model="test-model",
                finish_reason="stop",
                elapsed_seconds=0.01,
            )
        finally:
            active_invocations -= 1

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        first = client.post(
            "/v1/work",
            headers=AUTH,
            json=submission_for("application-a", "first"),
        ).json()
        assert first["status"] == "accepted"
        assert first_entered.wait(1)

        second = client.post(
            "/v1/work",
            headers=AUTH,
            json=submission_for("application-b", "second"),
        ).json()
        assert second["status"] == "accepted"
        assert second["attempts"] == []

        waiting = client.get(f"/v1/work/{second['id']}", headers=AUTH).json()
        assert waiting["status"] == "accepted"
        assert waiting["attempts"] == []
        assert not second_entered.is_set()
        assert invocation_order == ["first:entered"]

        release_first.set()
        first_completed = wait_for_terminal(client, first["id"])
        second_completed = wait_for_terminal(client, second["id"])

    assert first_completed["status"] == "succeeded"
    assert second_completed["status"] == "succeeded"
    assert second_entered.is_set()
    assert maximum_active == 1
    assert invocation_order == [
        "first:entered",
        "first:completed",
        "second:entered",
        "second:completed",
    ]


def test_unexpected_execution_exception_is_durable_and_scheduler_continues(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    raw_exception_detail = "fixture-secret-exception-detail"
    second_entered = threading.Event()

    async def fake_invoke(capability, request, constraints):
        label = request.messages[0].content
        if label == "explode":
            raise RuntimeError(raw_exception_detail)
        second_entered.set()
        return ChatResult(
            text="second result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        failed_submission = client.post(
            "/v1/work",
            headers=AUTH,
            json=submission_for("application-a", "explode"),
        ).json()
        later_submission = client.post(
            "/v1/work",
            headers=AUTH,
            json=submission_for("application-b", "later"),
        ).json()

        failed = wait_for_terminal(client, failed_submission["id"])
        later = wait_for_terminal(client, later_submission["id"])

    assert failed["status"] == "failed"
    assert failed["failure"] == {
        "code": "internal_error",
        "message": "capability execution failed unexpectedly",
    }
    assert len(failed["attempts"]) == 1
    assert failed["attempts"][0]["status"] == "failed"
    assert failed["attempts"][0]["failure"] == failed["failure"]
    assert raw_exception_detail not in json.dumps(failed)
    assert second_entered.is_set()
    assert later["status"] == "succeeded"
    assert later["result"]["text"] == "second result"


def test_unknown_capability_and_invalid_input_are_durable_failures(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        unknown_accepted = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "capability_id": "missing"},
        ).json()
        assert unknown_accepted["status"] == "accepted"
        unknown = wait_for_terminal(client, unknown_accepted["id"])
        assert unknown["status"] == "failed"
        assert unknown["failure"]["code"] == "unknown_capability"
        assert unknown["attempts"] == []

        invalid_accepted = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "input": {"unexpected": True}},
        ).json()
        assert invalid_accepted["status"] == "accepted"
        invalid = wait_for_terminal(client, invalid_accepted["id"])
        assert invalid["status"] == "failed"
        assert invalid["failure"]["code"] == "invalid_input"
        assert invalid["attempts"] == []


def test_future_eligibility_is_durably_accepted_without_execution(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    invoked = False

    async def fake_invoke(capability, request, constraints):
        nonlocal invoked
        invoked = True
        return ChatResult(
            text="should not run yet",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)
    future = datetime.now(UTC) + timedelta(hours=1)

    with TestClient(create_app(runtime_settings)) as client:
        response = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "eligible_at": future.isoformat()},
        )
        assert response.status_code == 201
        work = response.json()
        assert work["status"] == "accepted"
        assert work["attempts"] == []
        assert work["failure"] is None

        inspected = client.get(f"/v1/work/{work['id']}", headers=AUTH)
        assert inspected.status_code == 200
        assert inspected.json() == work

    assert not invoked
    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        row = connection.execute(
            "SELECT status, eligible_at FROM runtime_work WHERE id = ?", (work["id"],)
        ).fetchone()
    assert row == ("accepted", future.isoformat())


def test_immediate_accepted_work_survives_restart_and_executes(tmp_path, monkeypatch):
    runtime_settings = settings(tmp_path)
    submission = WorkSubmission.model_validate(SUBMISSION)

    async def fake_invoke(capability, request, constraints):
        return ChatResult(
            text="recovered immediate result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection))
        accepted = asyncio.run(runtime.submit(submission))
        assert accepted.status == "accepted"
        assert accepted.attempts == []
        work_id = accepted.id

    with open_database(runtime_settings.data_dir) as connection:
        recovered = WorkRuntime(runtime_settings, WorkStore(connection))
        assert asyncio.run(recovered.run_eligible()) == 1
        completed = recovered.inspect(work_id)

    assert completed is not None
    assert completed.status == "succeeded"
    assert completed.result is not None
    assert completed.result["text"] == "recovered immediate result"
    assert completed.attempts[0].status == "succeeded"


def test_restart_marks_incomplete_attempt_as_interrupted(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    work_id = "interrupted-work"
    submission = WorkSubmission.model_validate(SUBMISSION)

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        store.create(work_id, submission, utc_now())
        store.start_attempt(work_id, utc_now())

    with TestClient(create_app(runtime_settings)) as client:
        work = client.get(f"/v1/work/{work_id}", headers=AUTH).json()

    assert work["status"] == "failed"
    assert work["failure"]["code"] == "interrupted"
    assert work["attempts"][0]["status"] == "failed"
    assert work["attempts"][0]["failure"]["code"] == "interrupted"


def test_delayed_work_survives_restart_and_scheduler_executes_at_eligibility(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    current = [datetime(2035, 1, 1, 12, 0, tzinfo=UTC)]
    eligible_at = current[0] + timedelta(minutes=5)
    invoked_at = []

    async def fake_invoke(capability, request, constraints):
        invoked_at.append(current[0])
        return ChatResult(
            text="recovered delayed result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    delayed = WorkSubmission.model_validate({**SUBMISSION, "eligible_at": eligible_at.isoformat()})

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection), clock=lambda: current[0])
        accepted = asyncio.run(runtime.submit(delayed))
        assert accepted.status == "accepted"
        assert accepted.attempts == []
        work_id = accepted.id

    current[0] += timedelta(minutes=1)

    with open_database(runtime_settings.data_dir) as connection:

        async def exercise_recovered_scheduler():
            recovered = WorkRuntime(
                runtime_settings,
                WorkStore(connection),
                clock=lambda: current[0],
            )
            scheduler = asyncio.create_task(recovered.run_scheduler())
            try:
                await asyncio.sleep(0)
                before = recovered.inspect(work_id)
                assert before is not None
                assert before.status == "accepted"
                assert invoked_at == []

                current[0] = eligible_at - timedelta(minutes=1)
                wake_before = delayed.model_copy(
                    update={"eligible_at": eligible_at + timedelta(hours=1)}
                )
                assert (await recovered.submit(wake_before)).status == "accepted"
                for _ in range(3):
                    await asyncio.sleep(0)
                still_waiting = recovered.inspect(work_id)
                assert still_waiting is not None
                assert still_waiting.status == "accepted"
                assert invoked_at == []

                current[0] = eligible_at
                wake_due = delayed.model_copy(
                    update={"eligible_at": eligible_at + timedelta(hours=2)}
                )
                assert (await recovered.submit(wake_due)).status == "accepted"
                for _ in range(10):
                    completed = recovered.inspect(work_id)
                    if completed is not None and completed.status == "succeeded":
                        return completed
                    await asyncio.sleep(0)
                raise AssertionError("eligible recovered work was not executed by scheduler")
            finally:
                scheduler.cancel()
                with suppress(asyncio.CancelledError):
                    await scheduler

        completed = asyncio.run(exercise_recovered_scheduler())

    assert invoked_at == [eligible_at]
    assert completed.status == "succeeded"
    assert completed.started_at == eligible_at
    assert completed.result is not None
    assert completed.result["text"] == "recovered delayed result"
    assert completed.attempts[0].status == "succeeded"
