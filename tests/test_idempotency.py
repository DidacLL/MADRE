import asyncio
import sqlite3
import threading
import time
from datetime import UTC, datetime, timedelta

from fastapi.testclient import TestClient

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.inference import ChatResult

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


def headers(key: str) -> dict[str, str]:
    return {**AUTH, "Idempotency-Key": key}


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


def test_http_idempotency_replays_running_and_terminal_work_without_duplicate_execution(
    tmp_path, monkeypatch
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    entered = threading.Event()
    release = threading.Event()
    invocations = 0

    async def fake_invoke(capability, request, constraints):
        nonlocal invocations
        invocations += 1
        entered.set()
        while not release.is_set():
            await asyncio.sleep(0.001)
        return ChatResult(
            text="one durable result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        first = client.post(
            "/v1/work",
            headers=headers("submission-1"),
            json=SUBMISSION,
        )
        assert first.status_code == 201
        accepted = first.json()
        assert accepted["status"] == "accepted"
        work_id = accepted["id"]

        assert entered.wait(1)
        retry_while_running = client.post(
            "/v1/work",
            headers=headers("submission-1"),
            json=SUBMISSION,
        )
        assert retry_while_running.status_code == 201
        running = retry_while_running.json()
        assert running["id"] == work_id
        assert running["status"] == "running"
        assert len(running["attempts"]) == 1

        release.set()
        completed = wait_for_terminal(client, work_id)
        assert completed["status"] == "succeeded"

        retry_after_completion = client.post(
            "/v1/work",
            headers=headers("submission-1"),
            json=SUBMISSION,
        )
        assert retry_after_completion.status_code == 201
        assert retry_after_completion.json() == completed

    assert invocations == 1
    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        assert connection.execute("SELECT COUNT(*) FROM runtime_work").fetchone()[0] == 1
        assert connection.execute("SELECT COUNT(*) FROM runtime_attempt").fetchone()[0] == 1


def test_idempotency_conflict_rejects_different_work_without_mutating_original(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    future = datetime.now(UTC) + timedelta(hours=1)
    original_submission = {**SUBMISSION, "eligible_at": future.isoformat()}
    changed_submission = {
        **original_submission,
        "input": {
            "messages": [{"role": "user", "content": "Different work"}],
            "max_tokens": 32,
        },
    }

    with TestClient(create_app(settings(tmp_path))) as client:
        first = client.post(
            "/v1/work",
            headers=headers("conflicting-key"),
            json=original_submission,
        )
        assert first.status_code == 201
        original = first.json()

        conflict = client.post(
            "/v1/work",
            headers=headers("conflicting-key"),
            json=changed_submission,
        )
        assert conflict.status_code == 409
        assert conflict.json()["detail"] == (
            "idempotency key was already used with a different work submission"
        )

        inspected = client.get(f"/v1/work/{original['id']}", headers=AUTH)
        assert inspected.status_code == 200
        assert inspected.json() == original


def test_idempotency_key_is_scoped_to_application_and_omission_keeps_distinct_work(
    tmp_path, monkeypatch
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    future = datetime.now(UTC) + timedelta(hours=1)
    first_submission = {**SUBMISSION, "eligible_at": future.isoformat()}
    second_application = {
        **first_submission,
        "application_id": "another-application",
    }

    with TestClient(create_app(settings(tmp_path))) as client:
        first = client.post(
            "/v1/work",
            headers=headers("shared-key"),
            json=first_submission,
        ).json()
        second = client.post(
            "/v1/work",
            headers=headers("shared-key"),
            json=second_application,
        ).json()
        without_key_a = client.post("/v1/work", headers=AUTH, json=first_submission).json()
        without_key_b = client.post("/v1/work", headers=AUTH, json=first_submission).json()

    assert first["id"] != second["id"]
    assert without_key_a["id"] != without_key_b["id"]
    assert len({first["id"], second["id"], without_key_a["id"], without_key_b["id"]}) == 4


def test_idempotent_accepted_work_replays_after_service_restart(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    future = datetime.now(UTC) + timedelta(hours=1)
    submission = {**SUBMISSION, "eligible_at": future.isoformat()}

    with TestClient(create_app(runtime_settings)) as client:
        first = client.post(
            "/v1/work",
            headers=headers("restart-key"),
            json=submission,
        )
        assert first.status_code == 201
        accepted = first.json()
        assert accepted["status"] == "accepted"

    with TestClient(create_app(runtime_settings)) as client:
        replay = client.post(
            "/v1/work",
            headers=headers("restart-key"),
            json=submission,
        )
        assert replay.status_code == 201
        assert replay.json() == accepted


def test_idempotency_key_header_is_bounded(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    future = datetime.now(UTC) + timedelta(hours=1)
    response = None

    with TestClient(create_app(settings(tmp_path))) as client:
        response = client.post(
            "/v1/work",
            headers=headers("x" * 129),
            json={**SUBMISSION, "eligible_at": future.isoformat()},
        )

    assert response.status_code == 422
