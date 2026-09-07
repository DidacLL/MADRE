import asyncio
import time
from datetime import UTC, datetime

from fastapi.testclient import TestClient

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.contracts import WorkFailure, WorkRetryRequest, WorkSubmission
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
    "application_id": "retry-app",
    "capability_id": "local-chat",
    "input": {
        "messages": [{"role": "user", "content": "retry me"}],
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


def test_failed_work_can_be_retried_once_without_duplicate_retry_execution(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    invocations = 0

    async def fake_invoke(capability, request, constraints):
        nonlocal invocations
        invocations += 1
        if invocations == 1:
            raise CapabilityError("connection", "temporary capability failure")
        return ChatResult(
            text="retry succeeded",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        submitted = client.post(
            "/v1/work",
            headers={**AUTH, "Idempotency-Key": "submission-1"},
            json=SUBMISSION,
        ).json()
        failed = wait_for_terminal(client, submitted["id"])
        assert failed["status"] == "failed"
        assert failed["attempts"][0]["retry_number"] is None
        assert failed["retries"] == []

        retried = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-1"},
            json={},
        )
        assert retried.status_code == 202
        accepted = retried.json()
        assert accepted["status"] == "accepted"
        assert accepted["failure"] is None
        assert len(accepted["retries"]) == 1
        retry = accepted["retries"][0]
        assert retry["number"] == 1
        assert retry["allow_unknown_outcome"] is False
        assert retry["previous_completed_at"] == failed["completed_at"]
        assert retry["previous_failure"] == failed["failure"]

        succeeded = wait_for_terminal(client, submitted["id"])
        assert succeeded["status"] == "succeeded"
        assert succeeded["result"]["text"] == "retry succeeded"
        assert len(succeeded["attempts"]) == 2
        assert succeeded["attempts"][1]["retry_number"] == 1
        assert succeeded["retries"][0]["previous_failure"]["code"] == "connection"
        assert invocations == 2

        replay = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-1"},
            json={},
        )
        assert replay.status_code == 202
        assert replay.json() == succeeded
        assert invocations == 2

        original_submission_replay = client.post(
            "/v1/work",
            headers={**AUTH, "Idempotency-Key": "submission-1"},
            json=SUBMISSION,
        )
        assert original_submission_replay.status_code == 201
        assert original_submission_replay.json() == succeeded
        assert invocations == 2


def test_retry_idempotency_key_rejects_different_retry_policy(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def fail_invoke(capability, request, constraints):
        raise CapabilityError("connection", "still unavailable")

    monkeypatch.setattr("madre.runtime.invoke_chat", fail_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        submitted = client.post("/v1/work", headers=AUTH, json=SUBMISSION).json()
        failed = wait_for_terminal(client, submitted["id"])
        assert failed["status"] == "failed"

        first = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-policy"},
            json={},
        )
        assert first.status_code == 202
        wait_for_terminal(client, submitted["id"])

        conflict = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-policy"},
            json={"allow_unknown_outcome": True},
        )
        assert conflict.status_code == 409
        assert conflict.json()["detail"] == (
            "idempotency key was already used with a different retry policy"
        )
        inspected = client.get(f"/v1/work/{submitted['id']}", headers=AUTH).json()
        assert len(inspected["retries"]) == 1


def test_retry_requires_failed_work_existing_id_and_idempotency_key(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def succeed_invoke(capability, request, constraints):
        return ChatResult(
            text="already succeeded",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", succeed_invoke)

    with TestClient(create_app(settings(tmp_path))) as client:
        submitted = client.post("/v1/work", headers=AUTH, json=SUBMISSION).json()
        succeeded = wait_for_terminal(client, submitted["id"])
        assert succeeded["status"] == "succeeded"

        not_failed = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-succeeded"},
            json={},
        )
        assert not_failed.status_code == 409
        assert not_failed.json()["detail"] == "only failed work can be retried"

        missing = client.post(
            "/v1/work/missing/retry",
            headers={**AUTH, "Idempotency-Key": "retry-missing"},
            json={},
        )
        assert missing.status_code == 404

        missing_key = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers=AUTH,
            json={},
        )
        assert missing_key.status_code == 422


def test_interrupted_work_requires_explicit_unknown_outcome_consent(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    runtime_settings = settings(tmp_path)
    submission = WorkSubmission.model_validate(SUBMISSION)
    work_id = "interrupted-retry"

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        store.create(work_id, submission, utc_now())
        store.start_attempt(work_id, utc_now())

    async def succeed_invoke(capability, request, constraints):
        return ChatResult(
            text="explicit unknown-outcome retry succeeded",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", succeed_invoke)

    with TestClient(create_app(runtime_settings)) as client:
        interrupted = client.get(f"/v1/work/{work_id}", headers=AUTH).json()
        assert interrupted["failure"]["code"] == "interrupted"
        assert interrupted["attempts"][0]["status"] == "failed"

        refused = client.post(
            f"/v1/work/{work_id}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-interrupted"},
            json={},
        )
        assert refused.status_code == 409
        assert "unknown capability outcome" in refused.json()["detail"]
        assert client.get(f"/v1/work/{work_id}", headers=AUTH).json()["retries"] == []

        accepted = client.post(
            f"/v1/work/{work_id}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-interrupted"},
            json={"allow_unknown_outcome": True},
        )
        assert accepted.status_code == 202
        accepted_retry = accepted.json()["retries"][0]
        assert accepted.json()["status"] == "accepted"
        assert accepted_retry["allow_unknown_outcome"] is True
        assert accepted_retry["previous_completed_at"] == interrupted["completed_at"]
        assert accepted_retry["previous_failure"] == interrupted["failure"]

        succeeded = wait_for_terminal(client, work_id)
        assert succeeded["status"] == "succeeded"
        assert len(succeeded["retries"]) == 1
        assert succeeded["retries"][0]["previous_failure"]["code"] == "interrupted"
        assert len(succeeded["attempts"]) == 2
        assert succeeded["attempts"][0]["retry_number"] is None
        assert succeeded["attempts"][1]["retry_number"] == 1


def test_accepted_retry_survives_restart_and_executes(tmp_path, monkeypatch):
    runtime_settings = settings(tmp_path)
    submission = WorkSubmission.model_validate(SUBMISSION)
    work_id = "restart-retry"
    clock = datetime(2035, 1, 1, 12, 0, tzinfo=UTC)

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        store.create(work_id, submission, clock)
        store.fail(
            work_id,
            WorkFailure(code="connection", message="temporary failure"),
            clock,
        )
        runtime = WorkRuntime(runtime_settings, store, clock=lambda: clock)
        accepted = asyncio.run(
            runtime.retry(
                work_id,
                WorkRetryRequest(),
                idempotency_key="retry-after-restart",
            )
        )
        assert accepted.status == "accepted"
        assert len(accepted.retries) == 1
        assert accepted.retries[0].previous_failure.code == "connection"
        assert accepted.attempts == []

    async def succeed_invoke(capability, request, constraints):
        return ChatResult(
            text="recovered retry",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", succeed_invoke)

    with open_database(runtime_settings.data_dir) as connection:
        recovered = WorkRuntime(runtime_settings, WorkStore(connection), clock=lambda: clock)
        assert asyncio.run(recovered.run_eligible()) == 1
        completed = recovered.inspect(work_id)

    assert completed is not None
    assert completed.status == "succeeded"
    assert completed.result is not None
    assert completed.result["text"] == "recovered retry"
    assert len(completed.retries) == 1
    assert len(completed.attempts) == 1
    assert completed.attempts[0].retry_number == 1


def test_retry_of_pre_execution_failure_is_visible_without_fake_attempt(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    invoked = False

    async def fake_invoke(capability, request, constraints):
        nonlocal invoked
        invoked = True
        raise AssertionError("unknown capability must not invoke chat")

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = settings(tmp_path)

    with TestClient(create_app(runtime_settings)) as client:
        submitted = client.post(
            "/v1/work",
            headers=AUTH,
            json={**SUBMISSION, "capability_id": "missing"},
        ).json()
        first_failure = wait_for_terminal(client, submitted["id"])
        assert first_failure["failure"]["code"] == "unknown_capability"
        assert first_failure["attempts"] == []

        retried = client.post(
            f"/v1/work/{submitted['id']}/retry",
            headers={**AUTH, "Idempotency-Key": "retry-unknown-capability"},
            json={},
        )
        assert retried.status_code == 202
        second_failure = wait_for_terminal(client, submitted["id"])
        assert second_failure["failure"]["code"] == "unknown_capability"
        assert len(second_failure["retries"]) == 1
        retry_evidence = second_failure["retries"][0]
        assert retry_evidence["previous_failure"] == first_failure["failure"]
        assert retry_evidence["previous_completed_at"] == first_failure["completed_at"]
        assert second_failure["attempts"] == []

    assert not invoked
