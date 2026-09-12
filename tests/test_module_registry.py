from __future__ import annotations

import asyncio

from madre.registry import ModuleRegistry
from madre_sdk import Material, MaterialId, MaterialSet, Sensitivity
from tests.sdk_fixtures import build_module_definition


def test_live_registry_returns_only_currently_reachable_public_definitions() -> None:
    definition = build_module_definition()
    secret_type, _, report_type = definition.material_types
    registry = ModuleRegistry()
    registry.register(definition)

    secret = MaterialSet.of(
        Material(
            MaterialId(definition.identity, "registry-secret"),
            secret_type,
            "secret",
            Sensitivity.S5,
        )
    )
    secret_entries = asyncio.run(registry.reachable(secret))
    assert len(secret_entries) == 1
    assert [operation.identity.name for operation in secret_entries[0].operations] == [
        "private-summary"
    ]
    assert secret_entries[0].agents[0].exposed_operations == (definition.operations[0].identity,)

    minimized = MaterialSet.of(
        Material(
            MaterialId(definition.identity, "registry-minimized"),
            report_type,
            {"count": 3},
            Sensitivity.S2,
        )
    )
    minimized_entries = asyncio.run(registry.reachable(minimized))
    assert [operation.identity.name for operation in minimized_entries[0].operations] == [
        "publish-minimized"
    ]


def test_registry_is_live_and_replacement_is_by_nominal_module_identity() -> None:
    definition = build_module_definition()
    first_runtime = ModuleRegistry()
    first_runtime.register(definition)
    assert first_runtime.module(definition.identity) is definition

    restarted_runtime = ModuleRegistry()
    assert restarted_runtime.modules() == ()
    restarted_runtime.register(definition)
    restarted_runtime.remove(definition.identity)
    assert restarted_runtime.modules() == ()
