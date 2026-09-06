"""Runtime database ownership and versioning; work storage is the next behavior."""

import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

from filelock import FileLock, Timeout

SCHEMA_VERSION = 1


@contextmanager
def open_database(data_dir: Path) -> Iterator[sqlite3.Connection]:
    data_dir = data_dir.resolve()
    data_dir.mkdir(parents=True, exist_ok=True)
    lock = FileLock(data_dir / "runtime.lock", timeout=0)
    try:
        lock.acquire()
    except Timeout as exc:
        raise RuntimeError("another MADRE runtime owns this data directory") from exc
    try:
        connection = sqlite3.connect(data_dir / "runtime.sqlite3")
        try:
            version = connection.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, SCHEMA_VERSION):
                raise RuntimeError(f"unsupported runtime schema version: {version}")
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("PRAGMA journal_mode=WAL")
            if version == 0:
                with connection:
                    connection.execute(f"PRAGMA user_version={SCHEMA_VERSION}")
            yield connection
        finally:
            connection.close()
    finally:
        lock.release()
