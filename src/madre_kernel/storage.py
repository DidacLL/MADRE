"""Kernel-owned durable semantic/evidence store, separate from Runtime persistence."""

from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


class KernelStore:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path)
        self._connection.execute(
            """
            CREATE TABLE IF NOT EXISTS semantic_record (
                kind TEXT NOT NULL,
                record_key TEXT NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (kind, record_key)
            )
            """
        )
        self._connection.commit()

    def close(self) -> None:
        self._connection.close()

    def put(self, kind: str, key: str, value: BaseModel) -> None:
        self._connection.execute(
            "INSERT OR REPLACE INTO semantic_record(kind, record_key, payload) VALUES (?, ?, ?)",
            (kind, key, value.model_dump_json()),
        )
        self._connection.commit()

    def get(self, kind: str, key: str, model: type[T]) -> T | None:
        row = self._connection.execute(
            "SELECT payload FROM semantic_record WHERE kind = ? AND record_key = ?",
            (kind, key),
        ).fetchone()
        return None if row is None else model.model_validate_json(row[0])

    def all(self, kind: str, model: type[T]) -> tuple[T, ...]:
        rows = self._connection.execute(
            "SELECT payload FROM semantic_record WHERE kind = ? ORDER BY record_key",
            (kind,),
        ).fetchall()
        return tuple(model.model_validate_json(row[0]) for row in rows)

    def count(self, kind: str) -> int:
        row = self._connection.execute(
            "SELECT COUNT(*) FROM semantic_record WHERE kind = ?", (kind,)
        ).fetchone()
        return int(row[0]) if row is not None else 0
