import sqlite3
from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.contracts import WorkSubmission
from madre.inference import CapabilityError, ChatResult
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


def test_future_eligibility_is_rejected_without_creating_work(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    future = datetime.now(UTC) + timedelta(hours=1)

    with TestClient(create_app(runtime_settings)) as client:
        response = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "eligible_at": future.isoformat()},
        )
        assert response.status_code == 409

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        count = connection.execute("SELECT COUNT(*) FROM runtime_work").fetchone()[0]
    assert count == 0


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


def test_restart_reconciles_eligible_unstarted_work_but_preserves_future(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    submission = WorkSubmission.model_validate(SUBMISSION)
    future_submission = submission.model_copy(
        update={"eligible_at": utc_now() + timedelta(hours=1)}
    )

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        store.create("unstarted-work", submission, utc_now())
        store.create("future-work", future_submission, utc_now())

    with TestClient(create_app(runtime_settings)) as client:
        unstarted = client.get("/v1/work/unstarted-work", headers=AUTH).json()
        future = client.get("/v1/work/future-work", headers=AUTH).json()

    assert unstarted["status"] == "failed"
    assert unstarted["failure"]["code"] == "interrupted_before_attempt"
    assert unstarted["attempts"] == []
    assert future["status"] == "accepted"
    assert future["failure"] is None
    assert future["attempts"] == []
