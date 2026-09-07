"""Interactive terminal entry point for the first-party CORE application."""

import argparse
import asyncio
import os
import sys

from madre_core.client import CoreClient, CoreRuntimeError
from madre_core.interaction import CoreConversation


def _credential(token_env: str) -> str:
    token = os.environ.get(token_env, "")
    if not token.strip():
        raise ValueError(f"required runtime credential environment variable is unset: {token_env}")
    return token


async def _collect_deeper(conversation: CoreConversation) -> None:
    try:
        update = await conversation.collect_deeper()
    except CoreRuntimeError as exc:
        print(f"CORE deeper error: {exc}", file=sys.stderr)
        return
    if update is None:
        return
    if update.applied_to_history:
        print(f"core(deeper)> {update.text}")
    else:
        print(f"core(deeper, late)> {update.text}")
        print("reasoning> late deeper result did not rewrite conversation history")


async def _interactive(args: argparse.Namespace) -> None:
    client = CoreClient(
        args.runtime_url,
        _credential(args.token_env),
        args.capability,
    )
    conversation = CoreConversation(
        client,
        max_tokens=args.max_tokens,
        deeper_max_tokens=args.deeper_max_tokens,
        timeout_seconds=args.timeout,
    )
    print("MADRE CORE — type /exit or /quit to stop; /deeper schedules a recommended deeper pass.")
    while True:
        try:
            user_message = input("you> ")
        except (EOFError, KeyboardInterrupt):
            print()
            return

        await _collect_deeper(conversation)
        command = user_message.strip().lower()
        if command in {"/exit", "/quit"}:
            return
        if not user_message.strip():
            continue
        try:
            if command == "/deeper":
                await conversation.schedule_deeper()
                print("reasoning> deeper scheduled")
                continue
            turn = await conversation.send(user_message)
        except (CoreRuntimeError, ValueError) as exc:
            print(f"CORE error: {exc}", file=sys.stderr)
            continue
        print(f"core> {turn.text}")
        print(f"reasoning> {turn.reasoning}")
        if turn.reasoning == "deeper":
            print("deeper> /deeper")


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="madre-core",
        description="Interactive first-party CORE client for a running MADRE runtime.",
    )
    parser.add_argument("--runtime-url", default="http://127.0.0.1:8731")
    parser.add_argument("--capability", default="local-chat")
    parser.add_argument("--token-env", default="MADRE_API_TOKEN")
    parser.add_argument("--max-tokens", type=int, default=256)
    parser.add_argument("--deeper-max-tokens", type=int, default=768)
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    try:
        asyncio.run(_interactive(args))
    except (CoreRuntimeError, OSError, ValueError) as exc:
        print(f"CORE error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
