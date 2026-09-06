"""Small commands around reusable package functions."""

import argparse
import asyncio
import json
from pathlib import Path

import uvicorn

from madre.config import load_settings
from madre.contracts import ExecutionConstraints
from madre.inference import CapabilityError, ChatInput, ChatMessage, invoke_chat
from madre.service import create_app


def main() -> None:
    parser = argparse.ArgumentParser(prog="madre")
    parser.add_argument("--config", type=Path, required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check-config")
    commands.add_parser("serve")
    probe = commands.add_parser("probe")
    probe.add_argument("capability")
    probe.add_argument("--prompt", default="Reply with a short greeting.")
    probe.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    try:
        settings = load_settings(args.config)
        if args.command == "check-config":
            settings.token()
            print(json.dumps({"valid": True, "capabilities": list(settings.capabilities)}))
        elif args.command == "serve":
            uvicorn.run(create_app(settings), host=settings.host, port=settings.port, workers=1)
        else:
            capability = settings.capabilities.get(args.capability)
            if capability is None:
                raise ValueError(f"unknown configured capability: {args.capability}")
            result = asyncio.run(
                invoke_chat(
                    capability,
                    ChatInput(messages=[ChatMessage(role="user", content=args.prompt)]),
                    ExecutionConstraints(timeout_seconds=args.timeout),
                )
            )
            print(result.model_dump_json())
    except CapabilityError as exc:
        print(json.dumps({"error": exc.code, "message": str(exc)}))
        raise SystemExit(1) from exc
    except (OSError, ValueError, RuntimeError) as exc:
        print(json.dumps({"error": "configuration_or_runtime", "message": str(exc)}))
        raise SystemExit(1) from exc
