"""MADRE local runtime entrypoint."""

from __future__ import annotations

import argparse
from pathlib import Path

import uvicorn

from madre.config import load_settings
from madre.service import create_app


def main() -> None:
    parser = argparse.ArgumentParser(prog="madre")
    parser.add_argument("--config", type=Path, default=Path("madre.toml"))
    args = parser.parse_args()
    settings = load_settings(args.config)
    uvicorn.run(create_app(settings), host=settings.host, port=settings.port)


if __name__ == "__main__":
    main()
