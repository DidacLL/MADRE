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
from madre.security import BoundaryRequirements, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def sec(
    subject: str,
    *,
    origin: str = "test-installation",
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    sensitivity: SecurityLevel = SecurityLevel.LEVEL_1,
    scopes: set[str] | None = None,
) -> SecurityEnvelope:
    return SecurityEnvelope.issue(
        subject=subject,
        sensitivity=sensitivity,
        trust=trust,
        risk=SecurityLevel.LEVEL_1,
        scopes=scopes or {"shared"},
        origin=origin,
        provenance=("manifest",),
    )


def material(reference: str, payload, *, origin: str, sensitivity=SecurityLevel.LEVEL_1):
    digest = content_digest(payload)
    return ImmediateMaterial(
        reference=reference,
        payload=payload,
        envelope=sec(digest, origin=origin, sensitivity=sensitivity),
    )


class Endpoint:
    boundary = "local"

    def __init__(self, module_id: str) -> None:
        self.module_id = module_id
        self.calls: list[tuple[str, str, object]] = []
        self.fail_operation = False
        self.output_sensitivity = SecurityLevel.LEVEL_1

    async def invoke_agent(self, agent_id: str, payload):
        self.calls.append(("agent", agent_id, payload))
        return material(
            "result/agent",
            {"agent": agent_id},
            origin=self.module_id,
            sensitivity=self.output_sensitivity,
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
        )


def manifest(
    module_id: str,
    *,
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    max_inbound: SecurityLevel = SecurityLevel.LEVEL_5,
    exports: bool = False,
) -> ModuleManifest:
    requirements = BoundaryRequirements(
        min_requester_trust=SecurityLevel.LEVEL_4,
        max_input_sensitivity=SecurityLevel.LEVEL_5,
        risk=SecurityLevel.LEVEL_1,
        allowed_scopes=frozenset({"shared"}),
        allowed_execution_boundaries=frozenset({"local"}),
    )
    kwargs = {}
    if exports:
        kwargs = {
            "agents": (
                AgentDescriptor(
                    id=f"{module_id}/agent/analyse",
                    module_id=module_id,
                    purpose="Analyse explicit input",
                    input_contract="json:any",
                    output_contract="json:any",
                    requirements=requirements,
                    security=sec(f"{module_id}/agent/analyse", scopes={"shared"}),
                ),
            ),
            "operations": (
                OperationDescriptor(
                    id=f"{module_id}/operation/write",
                    module_id=module_id,
                    purpose="Perform an explicit bounded mutation",
                    input_contract="json:any",
                    output_contract="json:any",
                    effect="module-state-mutation",
                    repeatability="not-repeatable",
                    requirements=requirements,
                    security=sec(f"{module_id}/operation/write", scopes={"shared"}),
                ),
            ),
        }
    return ModuleManifest(
        module_id=module_id,
        version="1",
        description="Module-owned implementation boundary",
        discovery_terms=("analysis",),
        security=sec(module_id, trust=trust, scopes={"shared"}),
        inbound_requirements=BoundaryRequirements(
            max_input_sensitivity=max_inbound,
            allowed_scopes=frozenset({"shared"}),
            allowed_execution_boundaries=frozenset({"local"}),
        ),
        **kwargs,
    )


def test_registry_discovery_uses_registered_requester_boundary(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b", exports=True))
        registry.register(manifest("module.low", trust=SecurityLevel.LEVEL_2))
        registry.register(manifest("module.high", trust=SecurityLevel.LEVEL_5))
        assert registry.discover_agents("module.low") == ()
        assert [item.id for item in registry.discover_agents("module.high")] == [
            "module.b/agent/analyse"
        ]

    with open_database(data_dir) as connection:
        registry = InteroperabilityRegistry(PlatformStore(connection))
        assert registry.get_agent("module.b/agent/analyse") is not None


def test_descriptor_identity_must_be_namespaced_by_owner() -> None:
    with pytest.raises(ValueError):
        ModuleManifest(
            module_id="module.b",
            version="1",
            description="invalid",
            security=sec("module.b"),
            agents=(
                AgentDescriptor(
                    id="foreign/agent/x",
                    module_id="module.b",
                    purpose="x",
                    input_contract="json:any",
                    output_contract="json:any",
                    requirements=BoundaryRequirements(),
                    security=sec("foreign/agent/x"),
                ),
            ),
        )


def test_explicit_agent_and_operation_invocation_evaluates_both_transfer_directions(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.a"))
        registry.register(manifest("module.b", exports=True))
        endpoint = Endpoint("module.b")
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)

        agent_result = asyncio.run(
            broker.invoke_agent(
                "module.a",
                "module.b/agent/analyse",
                material("call/agent", {"question": "explicit"}, origin="module.a"),
            )
        )
        operation_result = asyncio.run(
            broker.invoke_operation(
                "module.a",
                "module.b/operation/write",
                material("call/operation", {"value": 1}, origin="module.a"),
            )
        )

        assert agent_result == {"agent": "module.b/agent/analyse"}
        assert operation_result == {"operation": "module.b/operation/write"}
        decisions = connection.execute(
            "SELECT crossing_kind,admissible FROM security_decision ORDER BY id"
        ).fetchall()
        assert [(row[0], row[1]) for row in decisions] == [
            ("agent-input", 1),
            ("agent-output", 1),
            ("operation-input", 1),
            ("operation-output", 1),
        ]
        events = [
            row[0]
            for row in connection.execute(
                "SELECT event FROM broker_event ORDER BY id"
            ).fetchall()
        ]
        assert events == [
            "requested",
            "dispatched",
            "returned",
            "delivered",
            "requested",
            "dispatched",
            "returned",
            "delivered",
        ]


def test_broker_blocks_output_that_requester_cannot_receive(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.a", max_inbound=SecurityLevel.LEVEL_1))
        registry.register(manifest("module.b", exports=True))
        endpoint = Endpoint("module.b")
        endpoint.output_sensitivity = SecurityLevel.LEVEL_3
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)

        with pytest.raises(SecurityDenied):
            asyncio.run(
                broker.invoke_agent(
                    "module.a",
                    "module.b/agent/analyse",
                    material("call/agent", {"question": "explicit"}, origin="module.a"),
                )
            )
        assert connection.execute(
            "SELECT event FROM broker_event ORDER BY id DESC LIMIT 1"
        ).fetchone()[0] == "output-security-rejected"


def test_operation_exception_is_recorded_as_unknown_effect(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.a"))
        registry.register(manifest("module.b", exports=True))
        endpoint = Endpoint("module.b")
        endpoint.fail_operation = True
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)

        with pytest.raises(UnknownOperationEffect):
            asyncio.run(
                broker.invoke_operation(
                    "module.a",
                    "module.b/operation/write",
                    material("call/operation", {"value": 1}, origin="module.a"),
                )
            )
        assert connection.execute(
            "SELECT event FROM broker_event ORDER BY id DESC LIMIT 1"
        ).fetchone()[0] == "unknown-effect"
