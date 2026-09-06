from __future__ import annotations

import contextlib
import sqlite3
from pathlib import Path

from ._common import StorageMissing


def writer_connection(path: Path) -> sqlite3.Connection:
    path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(path, timeout=5.0, isolation_level=None)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA busy_timeout = 5000")
    return connection


def read_only_connection(path: Path) -> sqlite3.Connection:
    if not path.is_file():
        raise StorageMissing(f"storage does not exist: {path}")
    uri = path.as_uri() + "?mode=ro"
    try:
        connection = sqlite3.connect(uri, uri=True, timeout=5.0)
    except sqlite3.OperationalError as exc:
        raise StorageMissing(f"storage is not readable: {path}") from exc
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA query_only = ON")
    return connection


def initialize_storage(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE IF NOT EXISTS work_records (
            submission_seq INTEGER PRIMARY KEY AUTOINCREMENT,
            work_id TEXT NOT NULL UNIQUE,
            request_id TEXT NOT NULL,
            module_id TEXT NOT NULL,
            agent_id TEXT NOT NULL,
            plan_ref TEXT NOT NULL,
            mode TEXT NOT NULL CHECK (mode IN ('foreground', 'delayed')),
            not_before TEXT NOT NULL,
            status TEXT NOT NULL CHECK (
                status IN ('queued', 'running', 'recovered', 'completed', 'failed', 'blocked')
            ),
            context_ref TEXT NOT NULL,
            context_text TEXT NOT NULL,
            source_ref TEXT NOT NULL,
            scope TEXT NOT NULL,
            binding_id TEXT NOT NULL,
            current_attempt_id TEXT,
            attempt_count INTEGER NOT NULL DEFAULT 0,
            recovery_reason TEXT,
            submitted_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_work_schedule
            ON work_records(status, mode, not_before, submission_seq);

        CREATE TABLE IF NOT EXISTS runtime_events (
            event_id INTEGER PRIMARY KEY AUTOINCREMENT,
            work_id TEXT NOT NULL,
            attempt_id TEXT,
            transition TEXT NOT NULL,
            reason TEXT NOT NULL,
            timestamp TEXT NOT NULL,
            FOREIGN KEY(work_id) REFERENCES work_records(work_id)
        );

        CREATE INDEX IF NOT EXISTS idx_events_work
            ON runtime_events(work_id, event_id);

        CREATE TABLE IF NOT EXISTS inference_records (
            attempt_id TEXT PRIMARY KEY,
            work_id TEXT NOT NULL,
            backend_id TEXT NOT NULL,
            profile_id TEXT NOT NULL,
            policy_reason TEXT NOT NULL,
            started_at TEXT NOT NULL,
            finished_at TEXT,
            outcome TEXT NOT NULL CHECK (
                outcome IN ('running', 'succeeded', 'failed', 'interrupted')
            ),
            error_type TEXT,
            error_message TEXT,
            output_ref TEXT,
            FOREIGN KEY(work_id) REFERENCES work_records(work_id)
        );

        CREATE INDEX IF NOT EXISTS idx_inference_work
            ON inference_records(work_id, started_at);

        CREATE TABLE IF NOT EXISTS inference_outputs (
            output_id TEXT PRIMARY KEY,
            attempt_id TEXT NOT NULL UNIQUE,
            work_id TEXT NOT NULL,
            generated_text TEXT NOT NULL,
            created_at TEXT NOT NULL,
            FOREIGN KEY(attempt_id) REFERENCES inference_records(attempt_id),
            FOREIGN KEY(work_id) REFERENCES work_records(work_id)
        );
        """
    )


@contextlib.contextmanager
def transaction(connection: sqlite3.Connection):
    connection.execute("BEGIN IMMEDIATE")
    try:
        yield
    except BaseException:
        connection.rollback()
        raise
    else:
        connection.commit()


def ensure_initialized_read_only(connection: sqlite3.Connection) -> None:
    required = {
        "work_records",
        "runtime_events",
        "inference_records",
        "inference_outputs",
    }
    rows = connection.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table'"
    ).fetchall()
    present = {row["name"] for row in rows}
    if not required.issubset(present):
        raise StorageMissing("storage exists but MADRE runtime schema is not initialized")


def rows_as_dicts(rows: list[sqlite3.Row]) -> list[dict[str, object]]:
    return [dict(row) for row in rows]
