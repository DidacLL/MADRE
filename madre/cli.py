from __future__ import annotations

import argparse
import json
import sys

from .runtime import BusyWorker, RuntimeSliceError, inspect_state, run_worker_once, submit_work


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="python -m madre")
    parser.add_argument("--db", required=True, help="SQLite database path")
    commands = parser.add_subparsers(dest="command", required=True)

    submit = commands.add_parser("submit", help="durably queue one model action")
    submit.add_argument("--text", required=True, help="submitted context text")
    submit.add_argument("--mode", choices=("foreground", "delayed"), default="foreground")
    submit.add_argument("--not-before", help="UTC ISO-8601 time for delayed work")
    submit.add_argument("--binding", default="fake-a", help="model binding reference")
    submit.add_argument("--scope", default="local-only", help="context scope")
    submit.add_argument("--request-id")
    submit.add_argument("--source-ref")

    commands.add_parser("worker", help="recover and execute at most one eligible item")

    inspect = commands.add_parser("inspect", help="read persisted runtime state without mutation")
    inspect.add_argument("--work-id")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = _build_parser()
    args = parser.parse_args(argv)
    try:
        if args.command == "submit":
            result = submit_work(
                args.db,
                args.text,
                mode=args.mode,
                not_before=args.not_before,
                binding_id=args.binding,
                scope=args.scope,
                request_id=args.request_id,
                source_ref=args.source_ref,
            )
        elif args.command == "worker":
            result = run_worker_once(args.db)
        else:
            result = inspect_state(args.db, work_id=args.work_id)
    except BusyWorker as exc:
        print(json.dumps({"status": "busy", "error": str(exc)}, sort_keys=True), file=sys.stderr)
        return 3
    except (RuntimeSliceError, ValueError) as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, sort_keys=True), file=sys.stderr)
        return 2

    print(json.dumps(result, indent=2, sort_keys=True))
    return 0
