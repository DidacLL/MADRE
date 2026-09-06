"""Fast interactive behavior owned by the first-party CORE application."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from madre_core.client import CoreClient, CoreRuntimeError

ReasoningRecommendation = Literal["fast", "deeper"]

_FAST_INTERACTION_INSTRUCTION = """\
You are MADRE CORE's fast interaction behavior. Give the user a concise, useful response
for the current conversational turn. Do not claim to have performed follow-up work that
has not happened. After the user-facing response, add exactly one final line containing
[[MADRE_REASONING:fast]] when the fast response is sufficient, or
[[MADRE_REASONING:deeper]] when materially better handling would require deeper multi-step
reasoning, verification, research, planning, or tools. The marker is only a recommendation
for CORE software; do not explain it in the user-facing response.
"""
_MARKER_PREFIX = "[[MADRE_REASONING:"
_MARKERS: dict[str, ReasoningRecommendation] = {
    "[[MADRE_REASONING:fast]]": "fast",
    "[[MADRE_REASONING:deeper]]": "deeper",
}


@dataclass(frozen=True)
class CoreTurn:
    """One fast CORE response and its bounded deeper-reasoning recommendation."""

    text: str
    reasoning: ReasoningRecommendation


def _interpret_fast_response(generated: str) -> CoreTurn:
    lines = generated.rstrip().splitlines()
    final_line = lines[-1].strip()
    recommendation = _MARKERS.get(final_line)

    if recommendation is not None:
        text = "\n".join(lines[:-1]).strip()
    elif final_line.startswith(_MARKER_PREFIX):
        text = "\n".join(lines[:-1]).strip()
        recommendation = "deeper"
    else:
        text = generated.strip()
        recommendation = "deeper"

    if not text:
        raise CoreRuntimeError("CORE fast interaction returned no user-facing response")
    return CoreTurn(text=text, reasoning=recommendation)


class CoreConversation:
    """Process-local conversation implementing CORE's fast interaction responsibility."""

    def __init__(
        self,
        client: CoreClient,
        *,
        max_tokens: int = 256,
        timeout_seconds: float = 120,
    ) -> None:
        self.client = client
        self.max_tokens = max_tokens
        self.timeout_seconds = timeout_seconds
        self._messages: list[dict[str, str]] = []

    @property
    def messages(self) -> tuple[dict[str, str], ...]:
        return tuple(dict(message) for message in self._messages)

    async def send(self, user_message: str) -> CoreTurn:
        if not user_message.strip():
            raise ValueError("user message is empty")
        pending = [*self._messages, {"role": "user", "content": user_message}]
        generated = await self.client.complete(
            [{"role": "system", "content": _FAST_INTERACTION_INSTRUCTION}, *pending],
            max_tokens=self.max_tokens,
            timeout_seconds=self.timeout_seconds,
        )
        turn = _interpret_fast_response(generated)
        self._messages = [*pending, {"role": "assistant", "content": turn.text}]
        return turn
