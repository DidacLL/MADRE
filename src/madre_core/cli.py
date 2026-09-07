"""Interactive terminal entry point for the first-party CORE application."""

import argparse
import asyncio
import os
import sys

from madre_core.client import CoreClient, CoreRuntimeError
from madre_core.interaction import CoreConversation
from madre_core.planning import (
    CorePlanningAgent,
    CoreWorkPlanError,
    create_reviewed_planning_work_plan,
    execute_reviewed_planning_work_plan,
)


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


async def _run_reviewed_plan(agent: CorePlanningAgent, objective: str) -> None:
    plan = create_reviewed_planning_work_plan(agent, objective)
    print("plan> " + " -> ".join(step.workflow_id for step in plan.steps))
    try:
        await execute_reviewed_planning_work_plan(agent, plan)
    except CoreWorkPlanError as exc:
        failed = exc.plan.step(exc.failed_step_id)
        print(f"CORE plan error: {exc}", file=sys.stderr)
        if failed.work_id is not None:
            print(f"plan> failed runtime work {failed.work_id}", file=sys.stderr)
        return

    output = plan.final_output
    if output is None:
        raise RuntimeError("reviewed WorkPlan completed without a final synthesis")
    print(f"core(plan)> {output}")


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
    planning_agent = CorePlanningAgent(
        client,
        max_tokens=args.plan_max_tokens,
        timeout_seconds=args.timeout,
    )
    print(
        "MADRE CORE — /plan <objective> runs reviewed planning; "
        "/deeper schedules a recommended deeper pass; /exit or /quit stops."
    )
    while True:
        try:
            user_message = input("you> ")
        except (EOFError, KeyboardInterrupt):
            print()
            return

        await _collect_deeper(conversation)
        stripped = user_message.strip()
        command = stripped.lower()
        if command in {"/exit", "/quit"}:
            return
        if not stripped:
            continue
        try:
            if command == "/deeper":
                await conversation.schedule_deeper()
                print("reasoning> deeper scheduled")
                continue
            if command == "/plan":
                raise ValueError("/plan requires a non-empty objective")
            if command.startswith("/plan "):
                await _run_reviewed_plan(planning_agent, stripped[len("/plan ") :])
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
    parser.add_argument("--plan-max-tokens", type=int, default=512)
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    try:
        asyncio.run(_interactive(args))
    except (CoreRuntimeError, OSError, ValueError) as exc:
        print(f"CORE error: {exc}", file=sys.stderr)
        raise SystemExit(1) from exc
