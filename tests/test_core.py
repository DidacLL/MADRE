import asyncio
import json
import os
import sqlite3
import subprocess
import sys
from pathlib import Path

import httpx
import pytest

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.inference import CapabilityError, ChatResult
from madre_core import CORE_APPLICATION_ID, CoreClient, CoreConversation, CoreRuntimeError

CAPABILITIES = {
    "local-chat": CapabilityConfig(
        endpoint="http://127.0.0.1:8080/v1",
        model="test-model",
    )
}


def test_core_conversation_uses_runtime_http_boundary_and_stable_identity(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    observed_messages = []

    async def fake_invoke(capability, request, constraints):
        observed_messages.append([message.model_dump() for message in request.messages])
        reply = (
            "first answer\n[[MADRE_REASONING:fast]]"
            if len(observed_messages) == 1
            else "second answer\n[[MADRE_REASONING:deeper]]"
        )
        return ChatResult(
            text=reply,
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)
    app = create_app(runtime_settings)

    async def exercise():
        async with app.router.lifespan_context(app):
            transport = httpx.ASGITransport(app=app)
            client = CoreClient(
                "http://127.0.0.1:8731",
                "test-token",
                "local-chat",
                transport=transport,
            )
            conversation = CoreConversation(client, max_tokens=32, timeout_seconds=5)
            first = await conversation.send("first question")
            second = await conversation.send("second question")
            return first, second, conversation.messages

    first, second, history = asyncio.run(exercise())

    assert CORE_APPLICATION_ID == "madre-core"
    assert first.text == "first answer"
    assert first.reasoning == "fast"
    assert second.text == "second answer"
    assert second.reasoning == "deeper"
    assert history == (
        {"role": "user", "content": "first question"},
        {"role": "assistant", "content": "first answer"},
        {"role": "user", "content": "second question"},
        {"role": "assistant", "content": "second answer"},
    )

    first_messages, second_messages = observed_messages
    assert first_messages[0]["role"] == "system"
    assert "fast interaction behavior" in first_messages[0]["content"]
    assert "Use `fast` by default" in first_messages[0]["content"]
    assert "same chat capability" in first_messages[0]["content"]
    assert first_messages[1:] == [{"role": "user", "content": "first question"}]
    assert second_messages[0]["role"] == "system"
    assert second_messages[1:] == [
        {"role": "user", "content": "first question"},
        {"role": "assistant", "content": "first answer"},
        {"role": "user", "content": "second question"},
    ]

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute(
            "SELECT application_id, status, priority FROM runtime_work ORDER BY queue_sequence"
        ).fetchall()
    assert rows == [
        ("madre-core", "succeeded", 10),
        ("madre-core", "succeeded", 10),
    ]


def test_core_deeper_follow_up_is_scheduled_without_waiting(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    observed_requests = []
    deeper_entered = asyncio.Event()
    release_deeper = asyncio.Event()

    async def fake_invoke(capability, request, constraints):
        observed_requests.append(request)
        if len(observed_requests) == 1:
            text = "fast draft\n[[MADRE_REASONING:deeper]]"
        else:
            deeper_entered.set()
            await release_deeper.wait()
            text = "materially improved answer"
        return ChatResult(
            text=text,
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)
    app = create_app(runtime_settings)

    async def exercise():
        async with app.router.lifespan_context(app):
            client = CoreClient(
                "http://127.0.0.1:8731",
                "test-token",
                "local-chat",
                poll_interval_seconds=0.001,
                transport=httpx.ASGITransport(app=app),
            )
            conversation = CoreConversation(
                client,
                max_tokens=32,
                deeper_max_tokens=96,
                timeout_seconds=5,
            )
            turn = await conversation.send("hard question")
            assert turn.reasoning == "deeper"
            assert conversation.deeper_available

            work_id = await conversation.schedule_deeper()
            assert work_id
            assert conversation.deeper_pending
            assert not conversation.deeper_available
            await asyncio.wait_for(deeper_entered.wait(), timeout=1)
            assert await conversation.collect_deeper() is None

            release_deeper.set()
            update = None
            while update is None:
                await asyncio.sleep(0.001)
                update = await conversation.collect_deeper()
            return update, conversation.messages, conversation.deeper_pending

    update, history, deeper_pending = asyncio.run(exercise())

    assert update.text == "materially improved answer"
    assert update.applied_to_history
    assert not deeper_pending
    assert history == (
        {"role": "user", "content": "hard question"},
        {"role": "assistant", "content": "materially improved answer"},
    )
    assert len(observed_requests) == 2
    fast_request, deeper_request = observed_requests
    assert fast_request.max_tokens == 32
    assert deeper_request.max_tokens == 96
    deeper_messages = [message.model_dump() for message in deeper_request.messages]
    assert deeper_messages[0]["role"] == "system"
    assert "deeper follow-up behavior" in deeper_messages[0]["content"]
    assert deeper_messages[1:] == [
        {"role": "user", "content": "hard question"},
        {"role": "assistant", "content": "fast draft"},
        {"role": "user", "content": "Provide the deeper replacement answer now."},
    ]

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute(
            "SELECT application_id, status, priority FROM runtime_work ORDER BY queue_sequence"
        ).fetchall()
    assert rows == [
        ("madre-core", "succeeded", 10),
        ("madre-core", "succeeded", -10),
    ]


def test_late_deeper_result_does_not_rewrite_advanced_conversation():
    post_count = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal post_count
        if request.method == "POST":
            post_count += 1
            submission = json.loads(request.content)
            if post_count == 1:
                assert submission["priority"] == 10
                text = "fast draft\n[[MADRE_REASONING:deeper]]"
                work_id = "fast-1"
            elif post_count == 2:
                assert submission["priority"] == -10
                return httpx.Response(
                    201,
                    json={
                        "id": "deep-1",
                        "status": "accepted",
                        "result": None,
                        "failure": None,
                    },
                )
            else:
                assert submission["priority"] == 10
                text = "follow-up answer\n[[MADRE_REASONING:fast]]"
                work_id = "fast-2"
            return httpx.Response(
                201,
                json={
                    "id": work_id,
                    "status": "succeeded",
                    "result": {"text": text},
                    "failure": None,
                },
            )
        assert request.url.path == "/v1/work/deep-1"
        return httpx.Response(
            200,
            json={
                "id": "deep-1",
                "status": "succeeded",
                "result": {"text": "late improved answer"},
                "failure": None,
            },
        )

    client = CoreClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        transport=httpx.MockTransport(handler),
    )
    conversation = CoreConversation(client)

    async def exercise():
        first = await conversation.send("hard question")
        assert first.reasoning == "deeper"
        await conversation.schedule_deeper()
        second = await conversation.send("continue from the draft")
        update = await conversation.collect_deeper()
        return second, update, conversation.messages

    second, update, history = asyncio.run(exercise())

    assert second.text == "follow-up answer"
    assert update is not None
    assert update.text == "late improved answer"
    assert not update.applied_to_history
    assert history == (
        {"role": "user", "content": "hard question"},
        {"role": "assistant", "content": "fast draft"},
        {"role": "user", "content": "continue from the draft"},
        {"role": "assistant", "content": "follow-up answer"},
    )


def test_core_deeper_requires_latest_deeper_recommendation():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            201,
            json={
                "id": "work-1",
                "status": "succeeded",
                "result": {"text": "enough\n[[MADRE_REASONING:fast]]"},
                "failure": None,
            },
        )

    client = CoreClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        transport=httpx.MockTransport(handler),
    )
    conversation = CoreConversation(client)
    turn = asyncio.run(conversation.send("simple question"))

    assert turn.reasoning == "fast"
    with pytest.raises(ValueError, match="no deeper reasoning is available"):
        asyncio.run(conversation.schedule_deeper())


def test_core_missing_reasoning_marker_conservatively_recommends_deeper():
    submissions = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal submissions
        assert request.method == "POST"
        submissions += 1
        submission = json.loads(request.content)
        messages = submission["input"]["messages"]
        assert messages[0]["role"] == "system"
        return httpx.Response(
            201,
            json={
                "id": "work-1",
                "status": "succeeded",
                "result": {"text": "useful answer without protocol marker"},
                "failure": None,
            },
        )

    client = CoreClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        transport=httpx.MockTransport(handler),
    )
    conversation = CoreConversation(client)
    turn = asyncio.run(conversation.send("complex request"))

    assert turn.text == "useful answer without protocol marker"
    assert turn.reasoning == "deeper"
    assert submissions == 1


def test_core_marker_only_fast_response_becomes_deeper_fallback():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            201,
            json={
                "id": "work-1",
                "status": "succeeded",
                "result": {"text": "[[MADRE_REASONING:fast]]"},
                "failure": None,
            },
        )

    client = CoreClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        transport=httpx.MockTransport(handler),
    )
    conversation = CoreConversation(client)
    turn = asyncio.run(conversation.send("patata"))

    assert turn.text == "I couldn't produce a usable fast response."
    assert turn.reasoning == "deeper"
    assert conversation.deeper_available
    assert conversation.messages == (
        {"role": "user", "content": "patata"},
        {"role": "assistant", "content": "I couldn't produce a usable fast response."},
    )


def test_core_surfaces_durable_capability_failure(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")

    async def fail_invoke(capability, request, constraints):
        raise CapabilityError("connection", "could not communicate with capability endpoint")

    monkeypatch.setattr("madre.runtime.invoke_chat", fail_invoke)
    runtime_settings = Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)
    app = create_app(runtime_settings)

    async def exercise():
        async with app.router.lifespan_context(app):
            client = CoreClient(
                "http://127.0.0.1:8731",
                "test-token",
                "local-chat",
                transport=httpx.ASGITransport(app=app),
            )
            await client.complete([{"role": "user", "content": "hello"}])

    with pytest.raises(
        CoreRuntimeError,
        match=r"MADRE work failed \[connection\]: could not communicate with capability endpoint",
    ):
        asyncio.run(exercise())

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        row = connection.execute(
            "SELECT application_id, status, error_code FROM runtime_work"
        ).fetchone()
    assert row == ("madre-core", "failed", "connection")


def test_core_polls_pending_work_until_terminal_success():
    inspections = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal inspections
        assert request.headers["authorization"] == "Bearer test-token"
        if request.method == "POST":
            submission = json.loads(request.content)
            assert submission["application_id"] == "madre-core"
            assert submission["capability_id"] == "local-chat"
            return httpx.Response(
                201,
                json={"id": "work-1", "status": "accepted", "result": None, "failure": None},
            )
        inspections += 1
        if inspections == 1:
            return httpx.Response(
                200,
                json={"id": "work-1", "status": "running", "result": None, "failure": None},
            )
        return httpx.Response(
            200,
            json={
                "id": "work-1",
                "status": "succeeded",
                "result": {"text": "eventual answer"},
                "failure": None,
            },
        )

    client = CoreClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        poll_interval_seconds=0.001,
        transport=httpx.MockTransport(handler),
    )
    result = asyncio.run(client.complete([{"role": "user", "content": "hello"}]))

    assert result == "eventual answer"
    assert inspections == 2


def test_core_rejects_non_loopback_runtime_url():
    with pytest.raises(ValueError, match="loopback"):
        CoreClient("https://example.com", "test-token", "local-chat")


def test_core_entry_point_help():
    executable = Path(sys.executable).parent / (
        "madre-core.exe" if os.name == "nt" else "madre-core"
    )
    result = subprocess.run(
        [str(executable), "--help"],
        capture_output=True,
        text=True,
        check=True,
    )
    assert "Interactive first-party CORE client" in result.stdout
    assert "--deeper-max-tokens" in result.stdout
