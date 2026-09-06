from __future__ import annotations

import contextlib
import os
from pathlib import Path

from ._common import BusyWorker


def worker_lock_path(db_path: Path) -> Path:
    return Path(str(db_path) + ".worker.lock")


@contextlib.contextmanager
def worker_lock(db_path: Path):
    lock_path = worker_lock_path(db_path)
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    handle = open(lock_path, "a+b")
    try:
        if os.name == "nt":
            import msvcrt

            handle.seek(0, os.SEEK_END)
            if handle.tell() == 0:
                handle.write(b"\0")
                handle.flush()
            handle.seek(0)
            try:
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            except OSError as exc:
                raise BusyWorker(f"worker busy for {db_path}") from exc
        else:
            import fcntl

            try:
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            except (BlockingIOError, OSError) as exc:
                raise BusyWorker(f"worker busy for {db_path}") from exc

        yield
    finally:
        if os.name == "nt":
            if not handle.closed:
                import msvcrt

                try:
                    handle.seek(0)
                    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
                except OSError:
                    pass
        else:
            if not handle.closed:
                import fcntl

                try:
                    fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
                except OSError:
                    pass
        handle.close()
