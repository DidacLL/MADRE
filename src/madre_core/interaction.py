"""Interactive reasoning behavior owned by the first-party CORE application."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Literal

from madre_core.client import CoreClient

ReasoningRecommendation = Literal["fast", "deeper"]

_FAST_INTERACTION_INSTRUCTION = """\
You are MADRE CORE's fast interaction behavior. Answer the user's current request directly
with a concise, useful user-facing response. Handle ordinary benign conversational,
factual, and creative requests normally. After the response, add exactly one final line:
[[MADRE_REASONING:fast]] or [[MADRE_REASONING:deeper]]. Use `fast` by default. Use `deeper`
only when one second pass by this same chat capability, using the same conversation and a
larger response budget but no new tools or external information, is likely to materially
improve correctness or completeness. Do not choose `deeper` merely because an answer could
be longer or because unavailable research or tools would help. Never output the marker
without a user-facing response, and do not explain the marker.
"""
_DEEPER_INTERACTION_INSTRUCTION = """\
You are MADRE CORE's deeper follow-up behavior. Reconsider the most recent user request
and the immediately preceding CORE answer as a draft. Produce one materially more
thorough and careful replacement answer. Analyze the request in multiple steps as useful,
check assumptions against the supplied conversation, and state uncertainty instead of
inventing facts. Do not claim external research, verification, or tool use unless the
conversation shows it actually occurred. Return only the improved user-facing answer and
do not emit a MADRE reasoning marker.
"""
_DEEPER_REQUEST = "Provide the deeper replacement answer now."
_NO_FAST_RESPONSE = "I couldn't produce a usable fast response."
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
        return CoreTurn(text=_NO_FAST_RESPONSE, reasoning="deeper")
    return CoreTurn(text=text, reasoning=recommendation)


class CoreConversation:
    """Process-local CORE conversation with explicit user-controlled deeper follow-up."""

    def __init__(
        self,
        client: CoreClient,
        *,
        max_tokens: int = 256,
        deeper_max_tokens: int = 768,
        timeout_seconds: float = 120,
    ) -> None:
        if max_tokens <= 0:
            raise ValueError("max_tokens must be positive")
        if deeper_max_tokens <= max_tokens:
            raise ValueError("deeper_max_tokens must exceed max_tokens")
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        self.client = client
        self.max_tokens = max_tokens
        self.deeper_max_tokens = deeper_max_tokens
        self.timeout_seconds = timeout_seconds
        self._messages: list[dict[str, str]] = []
        self._deeper_available = False

    @property
    def messages(self) -> tuple[dict[str, str], ...]:
        return tuple(dict(message) for message in self._messages)

    @property
    def deeper_available(self) -> bool:
        return self._deeper_available

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
        self._deeper_available = turn.reasoning == "deeper"
        return turn

    async def deepen(self) -> str:
        """Replace the latest fast draft after an explicit user escalation request."""
        if not self._deeper_available:
            raise ValueError("no deeper reasoning is available for the latest turn")
        generated = await self.client.complete(
            [
                {"role": "system", "content": _DEEPER_INTERACTION_INSTRUCTION},
                *self._messages,
                {"role": "user", "content": _DEEPER_REQUEST},
            ],
            max_tokens=self.deeper_max_tokens,
            timeout_seconds=self.timeout_seconds,
        )
        self._messages[-1] = {"role": "assistant", "content": generated}
        self._deeper_available = False
        return generated
