from __future__ import annotations

from dataclasses import replace

import pytest

from madre_sdk import (
    AgentDefinition,
    AgentId,
    DisplayName,
    Material,
    MaterialId,
    MaterialSet,
    ModuleDefinition,
    ModuleDefinitionJsonCodec,
    ModuleId,
    OperationId,
    Privacy,
    Purpose,
    Sensitivity,
)
from tests.sdk_fixtures import build_module_definition


def test_nontrivial_definition_round_trips_as_domain_objects() -> None:
    definition = build_module_definition()
    codec = ModuleDefinitionJsonCodec()

    encoded = codec.encode(definition)
    decoded = codec.decode(encoded)

    assert decoded == definition
    assert '"schema_version":1' in encoded
    assert "callable" not in encoded
    assert "import_path" not in encoded
    assert not hasattr(definition, "model_dump")


def test_module_sensitivity_comes_from_current_material_and_public_outputs() -> None:
    definition = build_module_definition()
    secret_type, _, report_type = definition.material_types
    secret = Material(
        MaterialId(definition.identity, "current-secret"),
        secret_type,
        "secret",
        Sensitivity.S5,
    )
    minimized = Material(
        MaterialId(definition.identity, "current-report"),
        report_type,
        {"count": 3},
        Sensitivity.S2,
    )

    assert definition.sensitivity_of(MaterialSet.of(secret)) is Sensitivity.S5
    assert definition.sensitivity_of(MaterialSet.of(minimized)) is Sensitivity.S2


def test_agent_privacy_changes_with_exact_exposed_operations() -> None:
    definition = build_module_definition()
    agent = definition.agents[0]
    private_operation = definition.operations[0]

    assert definition.agent_privacy(agent.identity) is Privacy.P3

    narrowed_agent = replace(agent, exposed_operations=(private_operation.identity,))
    narrowed = replace(definition, agents=(narrowed_agent,))
    assert narrowed.agent_privacy(agent.identity) is Privacy.P5


def test_nominal_identity_categories_and_ownership_are_constructor_invariants() -> None:
    definition = build_module_definition()
    foreign_module = ModuleId("foreign")
    invalid_agent = AgentDefinition(
        AgentId(foreign_module, "agent"),
        DisplayName("Foreign"),
        Purpose("A definition owned by another Module."),
    )

    with pytest.raises(ValueError):
        replace(definition, agents=(invalid_agent,))
    with pytest.raises(TypeError):
        AgentId(OperationId(definition.identity, "wrong-category"), "agent")  # type: ignore[arg-type]


def test_definition_has_no_universal_behavior_protocol() -> None:
    definition = build_module_definition()

    assert isinstance(definition, ModuleDefinition)
    assert not hasattr(definition, "receive")
    assert not hasattr(definition.agents[0], "receive")
