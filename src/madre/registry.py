"""Live directory of running Module public definitions."""

from __future__ import annotations

from dataclasses import replace

from madre_sdk import (
    AgentDefinition,
    MaterialSet,
    ModuleDefinition,
    ModuleDirectoryEntry,
    ModuleId,
    OperationDefinition,
    OperationId,
)


class ModuleRegistry:
    def __init__(self) -> None:
        self._modules: dict[ModuleId, ModuleDefinition] = {}

    def register(self, definition: ModuleDefinition) -> None:
        self._modules[definition.identity] = definition

    def remove(self, identity: ModuleId) -> None:
        self._modules.pop(identity, None)

    def module(self, identity: ModuleId) -> ModuleDefinition | None:
        return self._modules.get(identity)

    def modules(self) -> tuple[ModuleDefinition, ...]:
        return tuple(
            self._modules[identity]
            for identity in sorted(
                self._modules,
                key=lambda item: (item.name, item.revision),
            )
        )

    async def reachable(self, materials: MaterialSet) -> tuple[ModuleDirectoryEntry, ...]:
        entries: list[ModuleDirectoryEntry] = []
        for module in self.modules():
            operations = self._reachable_operations(module, materials)
            operation_ids = {operation.identity for operation in operations}
            agents = self._reachable_agents(module, materials, operation_ids)
            if agents or operations:
                entries.append(
                    ModuleDirectoryEntry(
                        identity=module.identity,
                        name=module.name,
                        purpose=module.purpose,
                        agents=agents,
                        operations=operations,
                    )
                )
        return tuple(entries)

    @staticmethod
    def _reachable_operations(
        module: ModuleDefinition,
        materials: MaterialSet,
    ) -> tuple[OperationDefinition, ...]:
        public_ids = set(module.public_operations)
        public_ids.update(
            operation_id for agent in module.agents for operation_id in agent.exposed_operations
        )
        reachable: list[OperationDefinition] = []
        for operation in module.operations:
            if operation.identity not in public_ids:
                continue
            try:
                operation.inputs.compose(materials)
            except ValueError:
                continue
            reachable.append(operation)
        return tuple(reachable)

    @staticmethod
    def _reachable_agents(
        module: ModuleDefinition,
        materials: MaterialSet,
        operation_ids: set[OperationId],
    ) -> tuple[AgentDefinition, ...]:
        reachable: list[AgentDefinition] = []
        for agent in module.agents:
            exposed = tuple(
                identity for identity in agent.exposed_operations if identity in operation_ids
            )
            direct = False
            if agent.inputs is not None:
                try:
                    agent.inputs.compose(materials)
                    direct = True
                except ValueError:
                    pass
            if direct or exposed:
                reachable.append(replace(agent, exposed_operations=exposed))
        return tuple(reachable)
