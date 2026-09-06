from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from madre.runtime import run_worker_once, submit_work


def _hold(message: str) -> None:
    print(message, flush=True)
    sys.stdin.buffer.read(1)


def main() -> int:
    if len(sys.argv) != 3:
        raise SystemExit("usage: _process_probe.py PHASE DB")
    phase, db = sys.argv[1:]

    if phase == "submit-before-commit":
        submit_work(db, "uncommitted", before_commit=lambda work_id: _hold(f"BEFORE_COMMIT {work_id}"))
        return 0

    if phase == "worker-running":
        run_worker_once(
            db,
            hooks={
                "after_running_commit": lambda work_id, attempt_id: _hold(
                    f"RUNNING {work_id} {attempt_id}"
                )
            },
        )
        return 0

    if phase == "worker-inferred":
        run_worker_once(
            db,
            hooks={
                "after_backend_return": lambda work_id, attempt_id, output: _hold(
                    f"INFERRED {work_id} {attempt_id} {output}"
                )
            },
        )
        return 0

    raise SystemExit(f"unknown phase: {phase}")


if __name__ == "__main__":
    raise SystemExit(main())
