import asyncio
import json

import httpx
import pytest

from madre.config import CapabilityConfig
from madre.contracts import ExecutionConstraints
from madre.inference import CapabilityError, ChatInput, ChatMessage, invoke_chat

REQUEST = ChatInput(messages=[ChatMessage(role="user", content="Hello")])
CAPABILITY = CapabilityConfig(endpoint="http://127.0.0.1:8080/v1", model="test")


def invoke(handler, capability=CAPABILITY, timeout=1):
    return asyncio.run(
        invoke_chat(
            capability,
            REQUEST,
            ExecutionConstraints(timeout_seconds=timeout),
            transport=httpx.MockTransport(handler),
        )
    )


def test_real_wire_shape_and_result(monkeypatch):
    monkeypatch.setenv("HTTP_PROXY", "http://invalid:1")
    monkeypatch.setenv("OPENAI_API_KEY", "unused-provider-credential")
    monkeypatch.setenv("MADRE_API_TOKEN", "local-service-token")

    def handler(request):
        assert "authorization" not in request.headers
        assert str(request.url) == "http://127.0.0.1:8080/v1/chat/completions"
        assert json.loads(request.content) == {
            "model": "test",
            "messages": [{"role": "user", "content": "Hello"}],
            "max_tokens": 64,
            "stream": False,
        }
        return httpx.Response(
            200,
            json={
                "model": "test",
                "choices": [
                    {"message": {"content": "Hello!"}, "finish_reason": "stop"},
                ],
            },
        )

    result = invoke(handler)
    assert result.text == "Hello!"
    assert result.model == "test"
    assert result.elapsed_seconds >= 0


@pytest.mark.parametrize(
    "body",
    [
        {},
        {"model": "test", "choices": []},
        {
            "model": "test",
            "choices": [{"message": {"content": " "}}],
        },
    ],
)
def test_invalid_response(body):
    with pytest.raises(CapabilityError) as error:
        invoke(lambda _: httpx.Response(200, json=body))
    assert error.value.code == "invalid_response"


def test_invalid_json():
    with pytest.raises(CapabilityError) as error:
        invoke(lambda _: httpx.Response(200, content=b"not json"))
    assert error.value.code == "invalid_response"


def test_output_limit_is_preserved_in_result():
    result = invoke(
        lambda _: httpx.Response(
            200,
            json={
                "model": "test",
                "choices": [
                    {
                        "message": {"content": "Partial output"},
                        "finish_reason": "length",
                    }
                ],
            },
        )
    )
    assert result.finish_reason == "length"


def test_connection_failure():
    def handler(request):
        raise httpx.ConnectError("fixture", request=request)

    with pytest.raises(CapabilityError) as error:
        invoke(handler)
    assert error.value.code == "connection"


def test_total_timeout():
    async def handler(request):
        await asyncio.sleep(1)
        return httpx.Response(200)

    with pytest.raises(CapabilityError) as error:
        invoke(handler, timeout=0.01)
    assert error.value.code == "timeout"


@pytest.mark.parametrize("status", [302, 401, 500])
def test_http_errors_and_no_redirect_following(status):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(status, headers={"Location": "https://example.com"})

    with pytest.raises(CapabilityError) as error:
        invoke(handler)
    assert error.value.code == "http_status"
    assert len(calls) == 1


@pytest.mark.parametrize(
    "capability",
    [
        CapabilityConfig(endpoint="https://example.com/v1", model="test", boundary="remote"),
        CapabilityConfig(endpoint="https://example.com/v1", model="test", boundary="local"),
        CapabilityConfig(endpoint="http://localhost:8080/v1", model="test", boundary="local"),
    ],
)
def test_boundary_denied_before_transfer(capability):
    def handler(request):
        pytest.fail("forbidden transfer attempted")

    with pytest.raises(CapabilityError) as error:
        invoke(handler, capability=capability)
    assert error.value.code == "boundary_denied"
