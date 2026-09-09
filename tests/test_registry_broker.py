import asyncio
from pathlib import Path

import pytest

from madre.broker import (
    Broker,
    ModuleEndpointUnavailable,
    SecurityDenied,
    UnknownOperationEffect,
)
from madre.contracts import TransientMaterial
from madre.registry import (
    AgentDescriptor,
    InteroperabilityRegistry,
    ModuleManifest,
    OperationDescriptor,
)
from madre.runtime import content_digest
from madre.security import (
    ActorSecurityValues,
    MaterialSecurityValues,
    OperationSecurityValues,
    SecurityContext,
    SecurityLevel,
    SecurityObject,
)
from madre.storage import PlatformStore, open_database


def actor(subject: str, *, kind: str, trust=SecurityLevel.LEVEL_5) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=subject,
        subject_kind=kind,  # type: ignore[arg-type]
        values=ActorSecurityValues(trust=trust, isolation=SecurityLevel.LEVEL_5),
        origin="fixture",
    )


def operation_security(subject: str, *, risk=SecurityLevel.LEVEL_1) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=subject,
        subject_kind="operation",
        values=OperationSecurityValues(risk=risk, autonomy=SecurityLevel.LEVEL_2),
        origin="fixture",
    )


def context(module_id: str, *, trust=SecurityLevel.LEVEL_5) -> SecurityContext:
    return SecurityContext(objects=(actor(module_id, kind="module", trust=trust),))


def material(
    reference: str,
    payload,
    *,
    sensitivity=SecurityLevel.LEVEL_1,
) -> TransientMaterial:
    return TransientMaterial(
        reference=reference,
        payload=payload,
        digest=content_digest(payload),
        security=SecurityObject.issue(
            subject_id=reference,
            subject_kind="artifact",
            values=MaterialSecurityValues(sensitivity=sensitivity),
            origin="fixture",
        ),
    )


class Endpoint:
    boundary = "local"

    def __init__(self, module_id: str, *, trust=SecurityLevel.LEVEL_5) -> None:
        self.module_id = module_id
        self.security = actor(f"{module_id}:endpoint", kind="endpoint", trust=trust)
        self.calls: list[tuple[str, str, object]] = []
        self.fail_operation = False
        self.output_sensitivity = SecurityLevel.LEVEL_1

    async def invoke_agent(self, agent_id: str, payload):
        self.calls.append(("agent", agent_id, payload))
        return material(
            "result/agent",
            {"agent": agent_id},
            sensitivity=self.output_sensitivity,
        )

    async def invoke_operation(self, operation_id: str, payload):
        self.calls.append(("operation", operation_id, payload))
        if self.fail_operation:
            raise RuntimeError("effect may already have occurred")
        return material(
            "result/operation",
            {"operation": operation_id},
            sensitivity=self.output_sensitivity,
        )


def manifest(module_id: str, *, module_trust=SecurityLevel.LEVEL_5) -> ModuleManifest:
    return ModuleManifest(
        module_id=module_id,
        version="1",
        description="Module-owned implementation boundary",
        security=actor(module_id, kind="module", trust=module_trust),
        discovery_terms=("analysis",),
        agents=(
            AgentDescriptor(
                id="analyse",
                module_id=module_id,
                purpose="Analyse explicit input",
                input_contract="json:any",
                output_contract="json:any",
                security=actor("analyse", kind="agent"),
            ),
        ),
        operations=(
            OperationDescriptor(
                id="write",
                module_id=module_id,
                purpose="Perform an explicit bounded mutation",
                input_contract="json:any",
                output_contract="json:any",
                effect="module-state-mutation",
                repeatability="not-repeatable",
                security=operation_security("write"),
            ),
        ),
    )


def test_registry_discovery_uses_carried_security_not_registration_as_authority(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        registry = InteroperabilityRegistry(PlatformStore(connection))
        registry.register(manifest("module.b", module_trust=SecurityLevel.LEVEL_2))
        sensitive = material(
            "context/private",
            {"private": True},
            sensitivity=SecurityLevel.LEVEL_4,
        )
        denied_context = context("module.a").extend(sensitive.security)
        assert registry.discover_agents(denied_context) == ()
        assert [item.id for item in registry.discover_agents(context("module.a"))] == ["analyse"]

        registry.register(manifest("module.b", module_trust=SecurityLevel.LEVEL_5))
        assert [item.id for item in registry.discover_agents(denied_context)] == ["analyse"]
        # Changed registry facts affect new discovery only; registration is not authority.
        assert registry.get_module("module.a") is None


def test_agent_and_operation_endpoints_are_interface_segregated(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b"))
        endpoint = Endpoint("module.b")
        broker = Broker(registry, store)
        broker.attach_agent_endpoint("module.b", endpoint)

        result = asyncio.run(
            broker.invoke_agent(
                "module.a",
                context("module.a"),
                "analyse",
                material("call/agent", {"question": "explicit"}),
            )
        )
        assert result == {"agent": "analyse"}
        with pytest.raises(ModuleEndpointUnavailable):
            asyncio.run(
                broker.invoke_operation(
                    "module.a",
                    context("module.a"),
                    "write",
                    material("call/op", {"x": 1}),
                )
            )


def test_explicit_agent_and_operation_brokering_evaluates_return_boundary(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b"))
        endpoint = Endpoint("module.b")
        broker = Broker(registry, store)
        broker.attach_agent_endpoint("module.b", endpoint)
        broker.attach_operation_endpoint("module.b", endpoint)

        assert asyncio.run(
            broker.invoke_agent(
                "module.a",
                context("module.a"),
                "analyse",
                material("call/agent", {"question": "explicit"}),
            )
        ) == {"agent": "analyse"}
        assert asyncio.run(
            broker.invoke_operation(
                "module.a",
                context("module.a"),
                "write",
                material("call/operation", {"value": 1}),
            )
        ) == {"operation": "write"}
        decisions = connection.execute(
            "SELECT crossing_kind,admissible,evaluator FROM security_decision ORDER BY id"
        ).fetchall()
        assert [(row[0], row[1]) for row in decisions] == [
            ("agent-input", 1),
            ("agent-output", 1),
            ("operation-input", 1),
            ("operation-output", 1),
        ]
        assert all(row[2] for row in decisions)


def test_broker_blocks_sensitive_output_on_weaker_return_path(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b"))
        endpoint = Endpoint("module.b", trust=SecurityLevel.LEVEL_2)
        endpoint.output_sensitivity = SecurityLevel.LEVEL_4
        broker = Broker(registry, store)
        broker.attach_agent_endpoint("module.b", endpoint)
        with pytest.raises(SecurityDenied):
            asyncio.run(
                broker.invoke_agent(
                    "module.a",
                    context("module.a"),
                    "analyse",
                    material("call/agent", {"question": "explicit"}),
                )
            )
        assert (
            connection.execute(
                "SELECT event FROM broker_event ORDER BY id DESC LIMIT 1"
            ).fetchone()[0]
            == "output-security-rejected"
        )


def test_operation_exception_is_recorded_as_unknown_effect_and_not_retried(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.b"))
        endpoint = Endpoint("module.b")
        endpoint.fail_operation = True
        broker = Broker(registry, store)
        broker.attach_operation_endpoint("module.b", endpoint)
        with pytest.raises(UnknownOperationEffect):
            asyncio.run(
                broker.invoke_operation(
                    "module.a",
                    context("module.a"),
                    "write",
                    material("call/operation", {"value": 1}),
                )
            )
        assert len(endpoint.calls) == 1
        assert (
            connection.execute(
                "SELECT event FROM broker_event ORDER BY id DESC LIMIT 1"
            ).fetchone()[0]
            == "unknown-effect"
        )
