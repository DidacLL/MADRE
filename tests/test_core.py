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
        reply = "first answer" if len(observed_messages) == 1 else "second answer"
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
    assert first == "first answer"
    assert second == "second answer"
    assert history == (
        {"role": "user", "content": "first question"},
        {"role": "assistant", "content": "first answer"},
        {"role": "user", "content": "second question"},
        {"role": "assistant", "content": "second answer"},
    )
    assert observed_messages == [
        [{"role": "user", "content": "first question"}],
        [
            {"role": "user", "content": "first question"},
            {"role": "assistant", "content": "first answer"},
            {"role": "user", "content": "second question"},
        ],
    ]

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute("SELECT application_id, status FROM runtime_work").fetchall()
    assert len(rows) == 2
    assert all(row == ("madre-core", "succeeded") for row in rows)


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
