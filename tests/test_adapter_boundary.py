from __future__ import annotations

import asyncio
import json
from pathlib import Path

import httpx

from madre.adapters.openai import OpenAICompatibleChatCapability
from madre.capabilities import CapabilityInvocation
from madre.config import load_settings
from madre_sdk import ExecutionLocation, Privacy


def test_installation_supplies_privacy_independently_of_physical_location() -> None:
    settings = load_settings(Path(__file__).parents[1] / "madre.example.toml")
    installed = settings.openai_chat[0]
    capability = installed.install()

    assert capability.definition.properties.location is ExecutionLocation.OWNER_DEVICE
    assert capability.definition.inputs.members[0].privacy is Privacy.P4


def test_adapter_configuration_cannot_be_overridden_by_submitted_payload() -> None:
    settings = load_settings(Path(__file__).parents[1] / "madre.example.toml")
    installed = settings.openai_chat[0]
    captured: dict[str, object] = {}

    async def handle(request: httpx.Request) -> httpx.Response:
        captured.update(json.loads(request.content))
        return httpx.Response(200, json={"choices": [{"message": {"content": "done"}}]})

    async def invoke() -> object:
        async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as client:
            adapter = OpenAICompatibleChatCapability(
                installed.install().definition,
                installed.adapter,
                client=client,
            )
            return await adapter.invoke(
                CapabilityInvocation(
                    computation=adapter.definition.computation,
                    input_types=(adapter.definition.inputs.members[0].material_type,),
                    payloads=({"model": "submitted-model", "stream": True, "messages": []},),
                    timeout_seconds=5,
                )
            )

    result = asyncio.run(invoke())

    assert captured["model"] == "local-model"
    assert captured["stream"] is False
    assert result == {"choices": [{"message": {"content": "done"}}]}
