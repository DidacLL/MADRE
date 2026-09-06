"""Prepare and inspect the local CORE product-acceptance fixture."""

from __future__ import annotations

import argparse
import json
import shutil
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLE_CONFIG = ROOT / "madre.example.toml"
ACCEPTANCE_CONFIG = ROOT / "madre.acceptance.local.toml"
ACCEPTANCE_DATA_DIR = ROOT / "dev" / "core-acceptance"
ACCEPTANCE_DB = ACCEPTANCE_DATA_DIR / "runtime.sqlite3"


def prepare() -> None:
    source = EXAMPLE_CONFIG.read_text(encoding="utf-8")
    marker = '# data_dir = "./dev/runtime"'
    replacement = 'data_dir = "./dev/core-acceptance"'
    if marker not in source:
        raise RuntimeError(f"expected configuration marker is missing: {marker}")

    ACCEPTANCE_CONFIG.write_text(source.replace(marker, replacement, 1), encoding="utf-8")
    shutil.rmtree(ACCEPTANCE_DATA_DIR, ignore_errors=True)
    print(f"wrote {ACCEPTANCE_CONFIG.relative_to(ROOT)}")
    print(f"cleared {ACCEPTANCE_DATA_DIR.relative_to(ROOT)}")


def status() -> None:
    if not ACCEPTANCE_DB.exists():
        print("count=0")
        print("latest=none")
        return

    with sqlite3.connect(ACCEPTANCE_DB) as connection:
        rows = connection.execute(
            """
            SELECT id, status, input_json
            FROM runtime_work
            WHERE application_id = ?
            ORDER BY submitted_at, id
            """,
            ("madre-core",),
        ).fetchall()

    print(f"count={len(rows)}")
    if not rows:
        print("latest=none")
        return

    work_id, work_status, input_json = rows[-1]
    work_input = json.loads(input_json)
    print(f"latest_id={work_id}")
    print(f"latest_status={work_status}")
    print(f"latest_max_tokens={work_input.get('max_tokens')}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    subcommands.add_parser("prepare", help="create a clean dedicated CORE acceptance config")
    subcommands.add_parser("status", help="show durable CORE work count and latest work budget")
    args = parser.parse_args()

    if args.command == "prepare":
        prepare()
    else:
        status()


if __name__ == "__main__":
    main()
