"""SQLite lifecycle and schema for MADRE durable metadata/evidence."""

from __future__ import annotations

import hashlib
import json
import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import UTC, datetime
from pathlib import Path

from filelock import FileLock, Timeout
from pydantic import BaseModel

_STORAGE_DDL = """
CREATE TABLE runtime_work (
    id TEXT PRIMARY KEY,
    originator TEXT NOT NULL,
    capability_json TEXT NOT NULL,
    material_reference TEXT NOT NULL,
    input_digest TEXT NOT NULL,
    material_envelope_json TEXT NOT NULL,
    eligible_at TEXT,
    priority INTEGER NOT NULL CHECK (priority BETWEEN -100 AND 100),
    constraints_json TEXT NOT NULL,
    correlation_json TEXT NOT NULL,
    idempotency_key TEXT,
    status TEXT NOT NULL CHECK (status IN ('accepted','running','succeeded','failed','cancelled')),
    submitted_at TEXT NOT NULL,
    enqueued_at TEXT NOT NULL,
    queue_sequence INTEGER NOT NULL UNIQUE CHECK (queue_sequence >= 1),
    started_at TEXT,
    completed_at TEXT,
    error_code TEXT,
    cancellation_requested_at TEXT,
    cancellation_disposition TEXT CHECK (
        cancellation_disposition IS NULL OR
        cancellation_disposition IN ('prevented','requested_while_running')
    ),
    output_digest TEXT,
    output_size INTEGER,
    output_produced_at TEXT,
    delivery_status TEXT CHECK (
        delivery_status IS NULL OR delivery_status IN ('awaiting_consumption','consumed','lost')
    )
);
CREATE UNIQUE INDEX runtime_work_idempotency
ON runtime_work(originator,idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE runtime_scheduler_state (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    last_originator TEXT,
    next_queue_sequence INTEGER NOT NULL CHECK (next_queue_sequence >= 1)
);
INSERT INTO runtime_scheduler_state(singleton,last_originator,next_queue_sequence)
VALUES (1,NULL,1);

CREATE TABLE runtime_retry (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    idempotency_key TEXT NOT NULL,
    allow_unknown_outcome INTEGER NOT NULL CHECK (allow_unknown_outcome IN (0,1)),
    requested_at TEXT NOT NULL,
    previous_completed_at TEXT NOT NULL,
    previous_error_code TEXT NOT NULL,
    PRIMARY KEY(work_id,number),
    UNIQUE(work_id,idempotency_key)
);

CREATE TABLE runtime_attempt (
    work_id TEXT NOT NULL REFERENCES runtime_work(id) ON DELETE CASCADE,
    number INTEGER NOT NULL CHECK (number >= 1),
    retry_number INTEGER,
    status TEXT NOT NULL CHECK (status IN ('running','succeeded','failed')),
    started_at TEXT NOT NULL,
    completed_at TEXT,
    capability_id TEXT,
    model_id TEXT,
    execution_boundary TEXT,
    output_digest TEXT,
    output_size INTEGER,
    error_code TEXT,
    PRIMARY KEY(work_id,number)
);

CREATE TABLE module_manifest (
    module_id TEXT PRIMARY KEY,
    manifest_json TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE security_decision (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    crossing_id TEXT NOT NULL,
    crossing_kind TEXT NOT NULL,
    target_id TEXT NOT NULL,
    requester_subject TEXT NOT NULL,
    requester_integrity TEXT NOT NULL,
    material_subject TEXT NOT NULL,
    material_integrity TEXT NOT NULL,
    target_requirements_json TEXT NOT NULL,
    target_subject TEXT NOT NULL,
    target_integrity TEXT NOT NULL,
    destination_subject TEXT NOT NULL,
    destination_integrity TEXT NOT NULL,
    execution_boundary TEXT NOT NULL,
    admissible INTEGER NOT NULL CHECK (admissible IN (0,1)),
    deficits_json TEXT NOT NULL,
    decided_at TEXT NOT NULL
);

CREATE TABLE broker_event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    invocation_id TEXT NOT NULL,
    crossing_kind TEXT NOT NULL,
    requester_module_id TEXT NOT NULL,
    target_module_id TEXT NOT NULL,
    target_id TEXT NOT NULL,
    event TEXT NOT NULL,
    observed_at TEXT NOT NULL,
    output_digest TEXT,
    output_size INTEGER
);
"""


def utc_now() -> datetime:
    return datetime.now(UTC)


def _json(value: object) -> str:
    def default(item: object) -> object:
        if isinstance(item, BaseModel):
            return item.model_dump(mode="json")
        raise TypeError(f"unsupported durable JSON value: {type(item).__name__}")

    return json.dumps(value, sort_keys=True, separators=(",", ":"), default=default)


def _format_id() -> int:
    value = int.from_bytes(hashlib.sha256(_STORAGE_DDL.encode()).digest()[:4], "big")
    return value & 0x7FFFFFFF or 1


_STORAGE_FORMAT_ID = _format_id()


def _connect(database: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(database)
    connection.row_factory = sqlite3.Row
    return connection


def _discard_development_storage(database: Path) -> None:
    for suffix in ("", "-wal", "-shm", "-journal"):
        Path(f"{database}{suffix}").unlink(missing_ok=True)


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
        database = data_dir / "runtime.sqlite3"
        existed = database.exists()
        connection = _connect(database)
        try:
            format_id = connection.execute("PRAGMA user_version").fetchone()[0]
            incompatible = format_id not in (0, _STORAGE_FORMAT_ID) or (existed and format_id == 0)
            if incompatible:
                connection.close()
                _discard_development_storage(database)
                connection = _connect(database)
                format_id = 0
            connection.execute("PRAGMA foreign_keys=ON")
            connection.execute("PRAGMA journal_mode=WAL")
            if format_id == 0:
                with connection:
                    connection.executescript(_STORAGE_DDL)
                    connection.execute(f"PRAGMA user_version={_STORAGE_FORMAT_ID}")
            yield connection
        finally:
            connection.close()
    finally:
        lock.release()
