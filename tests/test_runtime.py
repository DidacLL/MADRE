import asyncio
import sqlite3
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


def test_heavyweight_local_inference_is_globally_admitted_across_applications(
    tmp_path, monkeypatch
):
    runtime_settings = settings(tmp_path)
    first_entered = asyncio.Event()
    release_first = asyncio.Event()
    second_persisted = asyncio.Event()
    invocation_order = []
    active_invocations = 0

    async def fake_invoke(capability, request, constraints):
        nonlocal active_invocations
        label = request.messages[0].content
        active_invocations += 1
        assert active_invocations == 1
        invocation_order.append(f"{label}:entered")
        try:
            if label == "first":
                first_entered.set()
                await release_first.wait()
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
    first = WorkSubmission.model_validate(
        {
            **SUBMISSION,
            "application_id": "application-a",
            "input": {
                "messages": [{"role": "user", "content": "first"}],
                "max_tokens": 32,
            },
        }
    )
    second = WorkSubmission.model_validate(
        {
            **SUBMISSION,
            "application_id": "application-b",
            "input": {
                "messages": [{"role": "user", "content": "second"}],
                "max_tokens": 32,
            },
        }
    )

    with open_database(runtime_settings.data_dir) as connection:
        store = WorkStore(connection)
        original_create = store.create

        def create_with_signal(work_id, submission, submitted_at):
            original_create(work_id, submission, submitted_at)
            if submission.application_id == "application-b":
                second_persisted.set()

        monkeypatch.setattr(store, "create", create_with_signal)
        runtime = WorkRuntime(runtime_settings, store)

        async def exercise_concurrency():
            first_task = asyncio.create_task(runtime.submit(first))
            await first_entered.wait()
            second_task = asyncio.create_task(runtime.submit(second))
            await second_persisted.wait()

            rows = connection.execute(
                "SELECT application_id, status FROM runtime_work ORDER BY application_id"
            ).fetchall()
            assert [(row[0], row[1]) for row in rows] == [
                ("application-a", "running"),
                ("application-b", "accepted"),
            ]
            assert connection.execute("SELECT COUNT(*) FROM runtime_attempt").fetchone()[0] == 1
            assert invocation_order == ["first:entered"]

            release_first.set()
            first_record, second_record = await asyncio.gather(first_task, second_task)
            return first_record, second_record

        first_record, second_record = asyncio.run(exercise_concurrency())

    assert first_record.status == "succeeded"
    assert second_record.status == "succeeded"
    assert first_record.submission.application_id == "application-a"
    assert second_record.submission.application_id == "application-b"
    assert invocation_order == [
        "first:entered",
        "first:completed",
        "second:entered",
        "second:completed",
    ]


def test_failing_heavyweight_local_inference_releases_admission(tmp_path, monkeypatch):
    runtime_settings = settings(tmp_path)
    first_entered = asyncio.Event()
    release_failure = asyncio.Event()
    second_entered = asyncio.Event()

    async def fake_invoke(capability, request, constraints):
        label = request.messages[0].content
        if label == "first":
            first_entered.set()
            await release_failure.wait()
            raise CapabilityError("connection", "first invocation failed")
        second_entered.set()
        return ChatResult(
            text="second result",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    first = WorkSubmission.model_validate(
        {
            **SUBMISSION,
            "application_id": "application-a",
            "input": {
                "messages": [{"role": "user", "content": "first"}],
                "max_tokens": 32,
            },
        }
    )
    second = WorkSubmission.model_validate(
        {
            **SUBMISSION,
            "application_id": "application-b",
            "input": {
                "messages": [{"role": "user", "content": "second"}],
                "max_tokens": 32,
            },
        }
    )

    with open_database(runtime_settings.data_dir) as connection:
        runtime = WorkRuntime(runtime_settings, WorkStore(connection))

        async def exercise_failure_release():
            first_task = asyncio.create_task(runtime.submit(first))
            await first_entered.wait()
            second_task = asyncio.create_task(runtime.submit(second))
            await asyncio.sleep(0)
            assert not second_entered.is_set()

            release_failure.set()
            first_record, second_record = await asyncio.gather(first_task, second_task)
            return first_record, second_record

        first_record, second_record = asyncio.run(exercise_failure_release())

    assert first_record.status == "failed"
    assert first_record.failure is not None
    assert first_record.failure.code == "connection"
    assert second_entered.is_set()
    assert second_record.status == "succeeded"


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
