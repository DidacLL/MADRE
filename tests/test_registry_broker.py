import asyncio
from pathlib import Path

import pytest

from madre.broker import Broker, SecurityDenied, UnknownOperationEffect
from madre.contracts import ImmediateMaterial
from madre.registry import (
    AgentDescriptor,
    InteroperabilityRegistry,
    ModuleManifest,
    OperationDescriptor,
)
from madre.runtime import content_digest
from madre.security import SecurityContext, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def sec(
    subject: str,
    *,
    origin: str = "fixture",
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    sensitivity: SecurityLevel = SecurityLevel.LEVEL_1,
    risk: SecurityLevel = SecurityLevel.LEVEL_1,
    scopes: set[str] | None = None,
) -> SecurityEnvelope:
    return SecurityEnvelope.issue(
        subject=subject,
        sensitivity=sensitivity,
        trust=trust,
        risk=risk,
        scopes=scopes or {"shared"},
        origin=origin,
        provenance=("fixture",),
    )


def context(module_id: str, *, trust: SecurityLevel = SecurityLevel.LEVEL_5) -> SecurityContext:
    return SecurityContext(envelopes=(sec(module_id, origin=module_id, trust=trust),))


def material(
    reference: str,
    payload,
    *,
    origin: str,
    sensitivity=SecurityLevel.LEVEL_1,
    trust=SecurityLevel.LEVEL_5,
):
    digest = content_digest(payload)
    return ImmediateMaterial(
        reference=reference,
        payload=payload,
        envelope=sec(digest, origin=origin, sensitivity=sensitivity, trust=trust),
    )


class Endpoint:
    boundary = "local"

    def __init__(self, module_id: str) -> None:
        self.module_id = module_id
        self.security = sec(f"{module_id}:endpoint", origin=module_id)
        self.calls: list[tuple[str, str, object]] = []
        self.fail_operation = False
        self.output_sensitivity = SecurityLevel.LEVEL_1
        self.output_trust = SecurityLevel.LEVEL_5

    async def invoke_agent(self, agent_id: str, payload):
        self.calls.append(("agent", agent_id, payload))
        return material(
            "result/agent",
            {"agent": agent_id},
            origin=self.module_id,
            sensitivity=self.output_sensitivity,
            trust=self.output_trust,
        )

    async def invoke_operation(self, operation_id: str, payload):
        self.calls.append(("operation", operation_id, payload))
        if self.fail_operation:
            raise RuntimeError("effect may already have occurred")
        return material(
            "result/operation",
            {"operation": operation_id},
            origin=self.module_id,
            sensitivity=self.output_sensitivity,
            trust=self.output_trust,
        )


def manifest(
    module_id: str,
    *,
    exports: bool = False,
    descriptor_risk=SecurityLevel.LEVEL_1,
) -> ModuleManifest:
    kwargs = {}
    if exports:
        kwargs = {
            "agents": (
                AgentDescriptor(
                    id="analyse",
                    module_id=module_id,
                    purpose="Analyse explicit input",
                    input_contract="json:any",
                    output_contract="json:any",
                    security=sec("analyse", risk=descriptor_risk),
                ),
            ),
            "operations": (
                OperationDescriptor(
                    id="write",
                    module_id=module_id,
                    purpose="Perform an explicit bounded mutation",
                    input_contract="json:any",
                    output_contract="json:any",
                    effect="module-state-mutation",
                    repeatability="not-repeatable",
                    security=sec("write", risk=descriptor_risk),
                ),
            ),
        }
    return ModuleManifest(
        module_id=module_id,
        version="1",
        description="Module-owned implementation boundary",
        discovery_terms=("analysis",),
        **kwargs,
    )


def test_registry_discovery_uses_carried_context_not_registered_requester_authority(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        registry = InteroperabilityRegistry(PlatformStore(connection))
        registry.register(manifest("module.b", exports=True, descriptor_risk=SecurityLevel.LEVEL_4))
        assert registry.discover_agents(context("module.a", trust=SecurityLevel.LEVEL_2)) == ()
        assert [item.id for item in registry.discover_agents(context("module.a"))] == ["analyse"]
        registry.register(ModuleManifest(module_id="module.a", version="99", description="changed"))
        assert [item.id for item in registry.discover_agents(context("module.a"))] == ["analyse"]


def test_descriptor_identity_is_not_forced_into_an_invented_owner_namespace() -> None:
    published = manifest("module.b", exports=True)
    assert published.agents[0].id == "analyse"
    assert published.agents[0].module_id == "module.b"


def test_explicit_agent_and_operation_invocation_evaluates_both_transfer_directions(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b", exports=True))
        endpoint = Endpoint("module.b")
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)

        agent_result = asyncio.run(
            broker.invoke_agent(
                "module.a",
                context("module.a"),
                "analyse",
                material("call/agent", {"question": "explicit"}, origin="module.a"),
            )
        )
        operation_result = asyncio.run(
            broker.invoke_operation(
                "module.a",
                context("module.a"),
                "write",
                material("call/operation", {"value": 1}, origin="module.a"),
            )
        )
        assert agent_result == {"agent": "analyse"}
        assert operation_result == {"operation": "write"}
        decisions = connection.execute(
            "SELECT crossing_kind,admissible FROM security_decision ORDER BY id"
        ).fetchall()
        assert [(row[0], row[1]) for row in decisions] == [
            ("agent-input", 1),
            ("agent-output", 1),
            ("operation-input", 1),
            ("operation-output", 1),
        ]


def test_broker_blocks_output_when_new_output_facts_make_carried_context_inadmissible(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b", exports=True))
        endpoint = Endpoint("module.b")
        endpoint.output_sensitivity = SecurityLevel.LEVEL_4
        endpoint.output_trust = SecurityLevel.LEVEL_2
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)
        with pytest.raises(SecurityDenied):
            asyncio.run(
                broker.invoke_agent(
                    "module.a",
                    context("module.a"),
                    "analyse",
                    material("call/agent", {"question": "explicit"}, origin="module.a"),
                )
            )
        assert (
            connection.execute(
                "SELECT event FROM broker_event ORDER BY id DESC LIMIT 1"
            ).fetchone()[0]
            == "output-security-rejected"
        )


def test_operation_exception_is_recorded_as_unknown_effect(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b", exports=True))
        endpoint = Endpoint("module.b")
        endpoint.fail_operation = True
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)
        with pytest.raises(UnknownOperationEffect):
            asyncio.run(
                broker.invoke_operation(
                    "module.a",
                    context("module.a"),
                    "write",
                    material("call/operation", {"value": 1}, origin="module.a"),
                )
            )
        assert (
            connection.execute(
                "SELECT event FROM broker_event ORDER BY id DESC LIMIT 1"
            ).fetchone()[0]
            == "unknown-effect"
        )
