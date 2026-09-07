"""Kernel-owned durable semantic/evidence store, separate from Runtime persistence."""

from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)

_TABLES = {
    "module_manifest": "module_manifest",
    "core_role": "core_role_assignment",
    "operation": "operation_definition",
    "skill": "skill_definition",
    "workflow": "workflow_definition",
    "agent_definition": "agent_definition",
    "agent_skill_instance": "agent_skill_instance",
    "agent_instance": "agent_instance",
    "context": "context_bundle",
    "work_plan": "work_plan",
    "agent_task": "agent_task",
    "operation_invocation": "operation_invocation",
    "security_decision": "security_decision",
    "runtime_link": "runtime_evidence_link",
}


class KernelStore:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(path)
        for table in _TABLES.values():
            self._connection.execute(
                f"""
                CREATE TABLE IF NOT EXISTS {table} (
                    record_key TEXT PRIMARY KEY,
                    payload TEXT NOT NULL
                )
                """
            )
        self._connection.commit()

    def close(self) -> None:
        self._connection.close()

    @staticmethod
    def _table(kind: str) -> str:
        try:
            return _TABLES[kind]
        except KeyError as exc:
            raise ValueError(f"unsupported Kernel record kind: {kind}") from exc

    def put(self, kind: str, key: str, value: BaseModel) -> None:
        table = self._table(kind)
        self._connection.execute(
            f"INSERT OR REPLACE INTO {table}(record_key, payload) VALUES (?, ?)",
            (key, value.model_dump_json()),
        )
        self._connection.commit()

    def get(self, kind: str, key: str, model: type[T]) -> T | None:
        table = self._table(kind)
        row = self._connection.execute(
            f"SELECT payload FROM {table} WHERE record_key = ?",
            (key,),
        ).fetchone()
        return None if row is None else model.model_validate_json(row[0])

    def all(self, kind: str, model: type[T]) -> tuple[T, ...]:
        table = self._table(kind)
        rows = self._connection.execute(
            f"SELECT payload FROM {table} ORDER BY record_key"
        ).fetchall()
        return tuple(model.model_validate_json(row[0]) for row in rows)

    def count(self, kind: str) -> int:
        table = self._table(kind)
        row = self._connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()
        return int(row[0]) if row is not None else 0
