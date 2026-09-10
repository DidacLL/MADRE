from __future__ import annotations

import asyncio
import json
from pathlib import Path

import pytest

from madre.broker import Broker, SecurityDenied, UnknownOperationEffect
from madre.registry import InteroperabilityRegistry
from madre.security import InvocationContext, SecurityHistory, SecurityLevel
from madre.storage import PlatformStore, open_database
from madre_sdk import (
    Agent,
    AgentBehavior,
    Artifact,
    EffectProfile,
    Module,
    Operation,
    OperationBehavior,
    TransientMaterial,
    effect_profile,
    participant_security,
)

REQUESTER = "module.requester"
TARGET = "module.target"


def requester_security(*, privacy: SecurityLevel = SecurityLevel.LEVEL_5):
    return participant_security(
        owner_module_id=REQUESTER,
        subject_id=REQUESTER,
        subject_kind="module",
        privacy=privacy,
        integrity=SecurityLevel.LEVEL_5,
    )


def input_material(
    requester,
    *,
    sensitivity: SecurityLevel = SecurityLevel.LEVEL_3,
    integrity: SecurityLevel = SecurityLevel.LEVEL_5,
) -> Artifact:
    return Artifact.create(
        owner_module_id=REQUESTER,
        artifact_id="request.input",
        payload={"input": "value"},
        sensitivity=sensitivity,
        integrity=integrity,
        security_history=SecurityHistory(objects=(requester,)),
    )


class EchoAgent(AgentBehavior):
    def __init__(self, producer_security_id: str) -> None:
        self.producer_security_id = producer_security_id
        self.called = False

    async def execute(
        self,
        *,
        agent_id: str,
        instructions: tuple[str, ...],
        security: SecurityHistory,
        invocation: InvocationContext,
        material: TransientMaterial,
    ) -> Artifact:
        del agent_id, instructions
        self.called = True
        return Artifact.derive_from(
            source=material,
            owner_module_id=TARGET,
            artifact_id="target.agent.output",
            payload={"echo": material.payload},
            producer_security_ids=(self.producer_security_id,),
            invocation=invocation,
            sensitivity=SecurityLevel.LEVEL_3,
            security_history=security,
        )


class EchoOperation(OperationBehavior):
    def __init__(self, producer_security_id: str, *, fail: bool = False) -> None:
        self.producer_security_id = producer_security_id
        self.fail = fail
        self.called = False
        self.profile_id: str | None = None

    async def execute(
        self,
        *,
        operation_id: str,
        effect_profile_id: str,
        security: SecurityHistory,
        invocation: InvocationContext,
        material: TransientMaterial,
    ) -> Artifact:
        del operation_id
        self.called = True
        self.profile_id = effect_profile_id
        if self.fail:
            raise RuntimeError("effect outcome unknown")
        return Artifact.derive_from(
            source=material,
            owner_module_id=TARGET,
            artifact_id="target.operation.output",
            payload={"effect_profile": effect_profile_id},
            producer_security_ids=(self.producer_security_id,),
            invocation=invocation,
            sensitivity=SecurityLevel.LEVEL_3,
            security_history=security,
        )


def make_agent_module(*, privacy: SecurityLevel) -> tuple[Module, EchoAgent]:
    module_security = participant_security(
        owner_module_id=TARGET,
        subject_id=TARGET,
        subject_kind="module",
        privacy=privacy,
        integrity=SecurityLevel.LEVEL_5,
    )
    agent_security = participant_security(
        owner_module_id=TARGET,
        subject_id="target.agent",
        subject_kind="agent",
        privacy=privacy,
        integrity=SecurityLevel.LEVEL_5,
    )
    behavior = EchoAgent(module_security.security_id)
    agent = Agent.from_instructions(
        agent_id="target.agent",
        purpose="Echo input",
        instructions="Echo the input",
        security=agent_security,
        behavior=behavior,
    )
    return (
        Module(
            module_id=TARGET,
            version="1",
            description="Target Agent module",
            security=module_security,
            agents=(agent,),
        ),
        behavior,
    )


def make_operation_module(
    profiles: tuple[EffectProfile, ...],
    *,
    fail: bool = False,
) -> tuple[Module, EchoOperation]:
    module_security = participant_security(
        owner_module_id=TARGET,
        subject_id=TARGET,
        subject_kind="module",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    behavior = EchoOperation(module_security.security_id, fail=fail)
    operation = Operation(
        operation_id="target.operation",
        purpose="Bounded effect",
        input_contract="json:any",
        output_contract="json:any",
        effect="bounded-test-effect",
        repeatability="explicit",
        effect_profiles=profiles,
        behavior=behavior,
    )
    return (
        Module(
            module_id=TARGET,
            version="1",
            description="Target Operation module",
            security=module_security,
            operations=(operation,),
        ),
        behavior,
    )


def profile(
    profile_id: str,
    *,
    risk: SecurityLevel,
    autonomy: SecurityLevel,
    privacy: SecurityLevel | None = None,
    discloses_material: bool = False,
) -> EffectProfile:
    return effect_profile(
        owner_module_id=TARGET,
        operation_id="target.operation",
        profile_id=profile_id,
        risk=risk,
        autonomy=autonomy,
        integrity=SecurityLevel.LEVEL_5,
        privacy=privacy,
        discloses_material=discloses_material,
    )


def broker_fixture(tmp_path: Path):
    database = open_database(tmp_path)
    connection = database.__enter__()
    store = PlatformStore(connection)
    registry = InteroperabilityRegistry(store)
    broker = Broker(registry, store)
    return database, connection, store, registry, broker


def test_registry_discovery_is_not_global_security_authority(tmp_path: Path) -> None:
    database, _, _, registry, _ = broker_fixture(tmp_path)
    try:
        module, _ = make_agent_module(privacy=SecurityLevel.LEVEL_1)
        module.register(registry)
        secret = input_material(
            requester_security(),
            sensitivity=SecurityLevel.LEVEL_5,
        )
        discovered = registry.discover_agents(secret.security_history)
        assert [(item.module_id, item.id) for item in discovered] == [(TARGET, "target.agent")]
    finally:
        database.__exit__(None, None, None)


def test_agent_broker_builds_only_actual_disclosure_transition(tmp_path: Path) -> None:
    database, connection, _, registry, broker = broker_fixture(tmp_path)
    try:
        module, behavior = make_agent_module(privacy=SecurityLevel.LEVEL_5)
        module.register(registry)
        module.register_agent_endpoint(broker)
        requester = requester_security()
        source = input_material(requester)
        output = asyncio.run(
            broker.invoke_agent(
                InvocationContext(module=requester),
                source.security_history,
                TARGET,
                "target.agent",
                source.transient(),
            )
        )
        assert behavior.called
        assert output.payload == {"echo": source.payload}
        rows = connection.execute(
            "SELECT crossing_kind,transition_json,admissible FROM security_decision ORDER BY id"
        ).fetchall()
        input_row = next(row for row in rows if row["crossing_kind"] == "agent-input")
        transition = json.loads(input_row["transition_json"])
        assert input_row["admissible"] == 1
        assert transition["disclosures"]
        assert transition["control"] is None
        assert transition["effect_execution"] is None
        assert len(output.history.transitions) == 2
    finally:
        database.__exit__(None, None, None)


def test_agent_disclosure_rejects_low_privacy_path_before_dispatch(tmp_path: Path) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        module, behavior = make_agent_module(privacy=SecurityLevel.LEVEL_2)
        module.register(registry)
        module.register_agent_endpoint(broker)
        requester = requester_security()
        source = input_material(
            requester,
            sensitivity=SecurityLevel.LEVEL_5,
        )
        with pytest.raises(SecurityDenied, match="confidentiality_capacity_below_sensitivity"):
            asyncio.run(
                broker.invoke_agent(
                    InvocationContext(module=requester),
                    source.security_history,
                    TARGET,
                    "target.agent",
                    source.transient(),
                )
            )
        assert not behavior.called
    finally:
        database.__exit__(None, None, None)


def test_low_integrity_display_material_is_not_an_effect_controller(tmp_path: Path) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        module, behavior = make_agent_module(privacy=SecurityLevel.LEVEL_5)
        module.register(registry)
        module.register_agent_endpoint(broker)
        requester = requester_security()
        source = input_material(
            requester,
            sensitivity=SecurityLevel.LEVEL_1,
            integrity=SecurityLevel.LEVEL_1,
        )
        output = asyncio.run(
            broker.invoke_agent(
                InvocationContext(module=requester),
                source.security_history,
                TARGET,
                "target.agent",
                source.transient(),
            )
        )
        assert behavior.called
        assert output.payload is not None
    finally:
        database.__exit__(None, None, None)


def test_direct_user_effect_passes_control_but_still_requires_effect_integrity(
    tmp_path: Path,
) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        direct = profile(
            "direct",
            risk=SecurityLevel.LEVEL_5,
            autonomy=SecurityLevel.LEVEL_1,
        )
        module, behavior = make_operation_module((direct,))
        module.register(registry)
        module.register_operation_endpoint(broker)
        requester = requester_security()
        source = input_material(
            requester,
            sensitivity=SecurityLevel.LEVEL_1,
            integrity=SecurityLevel.LEVEL_1,
        )
        output = asyncio.run(
            broker.invoke_operation(
                InvocationContext(module=requester),
                source.security_history,
                TARGET,
                "target.operation",
                "direct",
                source.transient(),
            )
        )
        assert behavior.called
        assert behavior.profile_id == "direct"
        assert output.payload == {"effect_profile": "direct"}
    finally:
        database.__exit__(None, None, None)


def test_autonomous_high_risk_effect_rejects_low_integrity_controller(tmp_path: Path) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        autonomous = profile(
            "autonomous",
            risk=SecurityLevel.LEVEL_5,
            autonomy=SecurityLevel.LEVEL_5,
        )
        module, behavior = make_operation_module((autonomous,))
        module.register(registry)
        module.register_operation_endpoint(broker)
        requester = requester_security()
        source = input_material(
            requester,
            sensitivity=SecurityLevel.LEVEL_1,
            integrity=SecurityLevel.LEVEL_1,
        )
        with pytest.raises(SecurityDenied, match="control_integrity_below_demand"):
            asyncio.run(
                broker.invoke_operation(
                    InvocationContext(module=requester),
                    source.security_history,
                    TARGET,
                    "target.operation",
                    "autonomous",
                    source.transient(),
                )
            )
        assert not behavior.called
    finally:
        database.__exit__(None, None, None)


def test_publishing_effect_profile_privacy_is_on_actual_disclosure_path(tmp_path: Path) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        publishing = profile(
            "public",
            risk=SecurityLevel.LEVEL_1,
            autonomy=SecurityLevel.LEVEL_1,
            privacy=SecurityLevel.LEVEL_1,
            discloses_material=True,
        )
        module, behavior = make_operation_module((publishing,))
        module.register(registry)
        module.register_operation_endpoint(broker)
        requester = requester_security()
        source = input_material(
            requester,
            sensitivity=SecurityLevel.LEVEL_5,
            integrity=SecurityLevel.LEVEL_5,
        )
        with pytest.raises(SecurityDenied, match="confidentiality_capacity_below_sensitivity"):
            asyncio.run(
                broker.invoke_operation(
                    InvocationContext(module=requester),
                    source.security_history,
                    TARGET,
                    "target.operation",
                    "public",
                    source.transient(),
                )
            )
        assert not behavior.called
    finally:
        database.__exit__(None, None, None)


def test_caller_can_select_only_published_immutable_effect_profile(tmp_path: Path) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        direct = profile(
            "direct",
            risk=SecurityLevel.LEVEL_1,
            autonomy=SecurityLevel.LEVEL_1,
        )
        module, behavior = make_operation_module((direct,))
        module.register(registry)
        module.register_operation_endpoint(broker)
        requester = requester_security()
        source = input_material(requester, sensitivity=SecurityLevel.LEVEL_1)
        with pytest.raises(SecurityDenied, match="invalid_effect_profile"):
            asyncio.run(
                broker.invoke_operation(
                    InvocationContext(module=requester),
                    source.security_history,
                    TARGET,
                    "target.operation",
                    "invented-profile",
                    source.transient(),
                )
            )
        assert not behavior.called
    finally:
        database.__exit__(None, None, None)


def test_operation_exception_is_unknown_effect_and_is_not_retried(tmp_path: Path) -> None:
    database, _, _, registry, broker = broker_fixture(tmp_path)
    try:
        direct = profile(
            "direct",
            risk=SecurityLevel.LEVEL_1,
            autonomy=SecurityLevel.LEVEL_1,
        )
        module, behavior = make_operation_module((direct,), fail=True)
        module.register(registry)
        module.register_operation_endpoint(broker)
        requester = requester_security()
        source = input_material(requester, sensitivity=SecurityLevel.LEVEL_1)
        with pytest.raises(UnknownOperationEffect):
            asyncio.run(
                broker.invoke_operation(
                    InvocationContext(module=requester),
                    source.security_history,
                    TARGET,
                    "target.operation",
                    "direct",
                    source.transient(),
                )
            )
        assert behavior.called
    finally:
        database.__exit__(None, None, None)
