import asyncio
import sqlite3
import threading
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


class MutableClock:
    def __init__(self, value: datetime):
        self.value = value

    def __call__(self) -> datetime:
        return self.value


def settings(tmp_path):
    return Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)


def test_http_immediate_work_executes_and_persists_success(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def fake_invoke(capability, request, constraints):
        assert capability.model == "test-model"
        assert request.messages[0].content == "Hello"
        assert constraints.local_only
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
        work = response.json()
        assert work["status"] == "succeeded"
        assert work["submission"]["application_id"] == "independent-app"
        assert work["result"]["text"] == "Hello from inference"
        assert work["attempts"][0]["status"] == "succeeded"
        work_id = work["id"]

        inspected = client.get(f"/v1/work/{work_id}", headers=AUTH)
        assert inspected.status_code == 200
        assert inspected.json() == work

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        row = connection.execute(
            "SELECT status, result_json FROM runtime_work WHERE id = ?", (work_id,)
        ).fetchone()
    assert row is not None
    assert row[0] == "succeeded"
    assert "Hello from inference" in row[1]


def test_capability_failure_is_durable_and_inspectable_after_restart(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def fail_invoke(capability, request, constraints):
        raise CapabilityError("connection", "could not communicate with capability endpoint")

    monkeypatch.setattr("madre.runtime.invoke_chat", fail_invoke)
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        response = client.post("/v1/work", headers=AUTH, json=SUBMISSION)
        assert response.status_code == 201
        work = response.json()
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


def test_unknown_capability_and_invalid_input_are_durable_failures(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        unknown = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "capability_id": "missing"},
        ).json()
        assert unknown["status"] == "failed"
        assert unknown["failure"]["code"] == "unknown_capability"
        assert unknown["attempts"] == []

        invalid = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "input": {"unexpected": True}},
        ).json()
        assert invalid["status"] == "failed"
        assert invalid["failure"]["code"] == "invalid_input"
        assert invalid["attempts"] == []


def test_http_future_work_is_durably_accepted_without_execution(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    invoked = False

    async def fake_invoke(capability, request, constraints):
        nonlocal invoked
        invoked = True
        raise AssertionError("future work executed before eligibility")

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
        assert work["submission"]["eligible_at"] == future.isoformat()
        work_id = work["id"]

        inspected = client.get(f"/v1/work/{work_id}", headers=AUTH)
        assert inspected.status_code == 200
        assert inspected.json() == work
        assert not invoked

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        row = connection.execute(
            "SELECT status, eligible_at FROM runtime_work WHERE id = ?", (work_id,)
        ).fetchone()
    assert row == ("accepted", future.isoformat())


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


def test_delayed_work_survives_restart_and_executes_only_when_eligible(
    tmp_path, monkeypatch
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    accepted_at = datetime(2030, 1, 1, tzinfo=UTC)
    eligible_at = accepted_at + timedelta(hours=1)
    clock = MutableClock(accepted_at)
    monkeypatch.setattr("madre.runtime.utc_now", clock)
    invocations: list[datetime] = []

    async def fake_invoke(capability, request, constraints):
        invocations.append(clock())
        return ChatResult(
            text="delayed result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    submission = WorkSubmission.model_validate(
        {**SUBMISSION, "eligible_at": eligible_at.isoformat()}
    )

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection))
        accepted = asyncio.run(runtime.submit(submission, now=accepted_at))
        work_id = accepted.id
        assert accepted.status == "accepted"
        assert accepted.attempts == []

    clock.value = eligible_at - timedelta(microseconds=1)
    with open_database(runtime_settings.data_dir) as connection:
        recovered = WorkRuntime(runtime_settings, WorkStore(connection))
        assert recovered.inspect(work_id).status == "accepted"
        assert asyncio.run(recovered.execute_eligible(now=clock())) == 0
        assert recovered.inspect(work_id).status == "accepted"
        assert invocations == []

        clock.value = eligible_at
        assert asyncio.run(recovered.execute_eligible(now=clock())) == 1
        completed = recovered.inspect(work_id)

    assert completed is not None
    assert completed.status == "succeeded"
    assert completed.result["text"] == "delayed result"
    assert len(completed.attempts) == 1
    assert completed.started_at >= eligible_at
    assert invocations == [eligible_at]


def test_background_scheduler_executes_future_work_at_eligibility(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    invoked = threading.Event()

    async def fake_invoke(capability, request, constraints):
        invoked.set()
        return ChatResult(
            text="scheduled result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    eligible_at = datetime.now(UTC) + timedelta(milliseconds=250)

    with TestClient(create_app(runtime_settings)) as client:
        accepted = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "eligible_at": eligible_at.isoformat()},
        ).json()
        assert accepted["status"] == "accepted"
        assert accepted["attempts"] == []
        assert invoked.wait(timeout=2)

        work = accepted
        for _ in range(20):
            work = client.get(f"/v1/work/{accepted['id']}", headers=AUTH).json()
            if work["status"] == "succeeded":
                break

    assert work["status"] == "succeeded"
    assert work["result"]["text"] == "scheduled result"
    assert datetime.fromisoformat(work["started_at"]) >= eligible_at
    assert len(work["attempts"]) == 1
