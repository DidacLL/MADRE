"""Durable public Module catalog and execution metadata."""

from __future__ import annotations

from madre.storage_db import utc_now
from madre.storage_work import WorkStore
from madre_sdk.semantic import ModuleDefinition


class PlatformStore(WorkStore):
    def put_module(self, definition: ModuleDefinition) -> None:
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO module_definition(module_id,definition_json,updated_at) VALUES (?,?,?)
                ON CONFLICT(module_id) DO UPDATE
                SET definition_json=excluded.definition_json, updated_at=excluded.updated_at
                """,
                (
                    definition.identity.owner,
                    definition.model_dump_json(),
                    utc_now().isoformat(),
                ),
            )

    def modules(self) -> tuple[ModuleDefinition, ...]:
        rows = self.connection.execute(
            "SELECT definition_json FROM module_definition ORDER BY module_id"
        ).fetchall()
        return tuple(ModuleDefinition.model_validate_json(row["definition_json"]) for row in rows)
