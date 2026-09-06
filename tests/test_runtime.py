import asyncio
import sqlite3
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


def test_delayed_work_survives_restart_and_executes_at_eligibility(tmp_path, monkeypatch):
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
        recovered = WorkRuntime(
            runtime_settings,
            WorkStore(connection),
            clock=lambda: current[0],
        )
        before = recovered.inspect(work_id)
        assert before is not None
        assert before.status == "accepted"
        assert asyncio.run(recovered.run_eligible()) == 0
        assert invoked_at == []

        current[0] = eligible_at - timedelta(microseconds=1)
        assert asyncio.run(recovered.run_eligible()) == 0
        assert invoked_at == []

        current[0] = eligible_at
        assert asyncio.run(recovered.run_eligible()) == 1
        completed = recovered.inspect(work_id)

    assert completed is not None
    assert invoked_at == [eligible_at]
    assert completed.status == "succeeded"
    assert completed.started_at == eligible_at
    assert completed.result is not None
    assert completed.result["text"] == "recovered delayed result"
    assert completed.attempts[0].status == "succeeded"
