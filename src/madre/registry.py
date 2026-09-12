"""Catalog of declarative public Module definitions."""

from __future__ import annotations

from typing import Protocol

from madre_sdk.semantic import (
    AgentDefinition,
    ModuleDefinition,
    OperationDefinition,
    SkillDefinition,
    WorkflowDefinition,
)


class RegistryStore(Protocol):
    def put_module(self, definition: ModuleDefinition) -> None: ...
    def modules(self) -> tuple[ModuleDefinition, ...]: ...


class InteroperabilityRegistry:
    """Discovery only: presence in this catalog grants no execution or security status."""

    def __init__(self, store: RegistryStore) -> None:
        self._store = store

    def register(self, definition: ModuleDefinition) -> None:
        self._store.put_module(definition)

    def module(self, module_id: str) -> ModuleDefinition | None:
        return next(
            (
                definition
                for definition in self._store.modules()
                if definition.identity.owner == module_id
            ),
            None,
        )

    def agents(self) -> tuple[AgentDefinition, ...]:
        return tuple(agent for module in self._store.modules() for agent in module.agents)

    def skills(self) -> tuple[SkillDefinition, ...]:
        return tuple(skill for module in self._store.modules() for skill in module.skills)

    def workflows(self) -> tuple[WorkflowDefinition, ...]:
        return tuple(workflow for module in self._store.modules() for workflow in module.workflows)

    def operations(self) -> tuple[OperationDefinition, ...]:
        return tuple(
            operation for module in self._store.modules() for operation in module.operations
        )
