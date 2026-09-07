import asyncio
from pathlib import Path

from madre.broker import Broker
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
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    scopes: set[str] | None = None,
) -> SecurityEnvelope:
    return SecurityEnvelope.issue(
        subject=subject,
        sensitivity=SecurityLevel.LEVEL_1,
        trust=trust,
        risk=SecurityLevel.LEVEL_1,
        scopes=scopes or {"shared"},
        origin="test-module",
        provenance=("manifest",),
    )


def material(payload) -> ImmediateMaterial:
    digest = content_digest(payload)
    return ImmediateMaterial(reference="call/1", payload=payload, envelope=sec(digest))


class Endpoint:
    boundary = "local"

    def __init__(self) -> None:
        self.calls: list[tuple[str, str, object]] = []

    async def invoke_agent(self, agent_id: str, payload):
        self.calls.append(("agent", agent_id, payload))
        return {"agent": agent_id}

    async def invoke_operation(self, operation_id: str, payload):
        self.calls.append(("operation", operation_id, payload))
        return {"operation": operation_id}


def manifest() -> ModuleManifest:
    requirements = BoundaryRequirements(
        min_requester_trust=SecurityLevel.LEVEL_4,
        max_input_sensitivity=SecurityLevel.LEVEL_5,
        risk=SecurityLevel.LEVEL_1,
        allowed_scopes=frozenset({"shared"}),
        allowed_execution_boundaries=frozenset({"local"}),
    )
    return ModuleManifest(
        module_id="module.b",
        version="1",
        description="Module-owned implementation boundary",
        discovery_terms=("analysis",),
        security=sec("module:module.b", scopes={"shared"}),
        agents=(
            AgentDescriptor(
                id="module.b/agent/analyse",
                module_id="module.b",
                purpose="Analyse explicit input",
                input_contract="json:any",
                output_contract="json:any",
                requirements=requirements,
                security=sec("descriptor:agent", scopes={"shared"}),
            ),
        ),
        operations=(
            OperationDescriptor(
                id="module.b/operation/write",
                module_id="module.b",
                purpose="Perform an explicit bounded mutation",
                input_contract="json:any",
                output_contract="json:any",
                effect="module-state-mutation",
                repeatability="not-repeatable-without-owner-evidence",
                requirements=requirements,
                security=sec("descriptor:operation", scopes={"shared"}),
            ),
        ),
    )


def test_registry_discovery_is_boundary_filtered_and_durable(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest())
        low = sec("module.low", trust=SecurityLevel.LEVEL_2, scopes={"shared"})
        high = sec("module.high", trust=SecurityLevel.LEVEL_5, scopes={"shared"})
        assert registry.discover_agents(low) == ()
        assert [item.id for item in registry.discover_agents(high)] == [
            "module.b/agent/analyse"
        ]
        assert [item.id for item in registry.discover_operations(high)] == [
            "module.b/operation/write"
        ]

    with open_database(data_dir) as connection:
        registry = InteroperabilityRegistry(PlatformStore(connection))
        assert registry.get_agent("module.b/agent/analyse") is not None


def test_explicit_agent_and_operation_invocation_dispatch_to_module_owned_implementation(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest())
        endpoint = Endpoint()
        broker = Broker(registry, store)
        broker.attach_module("module.b", endpoint)
        requester = sec("module.a", trust=SecurityLevel.LEVEL_5, scopes={"shared"})

        agent_result = asyncio.run(
            broker.invoke_agent(
                requester,
                "module.b/agent/analyse",
                material({"question": "explicit"}),
            )
        )
        operation_result = asyncio.run(
            broker.invoke_operation(
                requester,
                "module.b/operation/write",
                material({"value": 1}),
            )
        )

        assert agent_result == {"agent": "module.b/agent/analyse"}
        assert operation_result == {"operation": "module.b/operation/write"}
        assert endpoint.calls == [
            ("agent", "module.b/agent/analyse", {"question": "explicit"}),
            ("operation", "module.b/operation/write", {"value": 1}),
        ]
        decisions = connection.execute(
            "SELECT crossing_kind,target_id,admissible FROM security_decision ORDER BY id"
        ).fetchall()
        assert [(row[0], row[1], row[2]) for row in decisions] == [
            ("agent", "module.b/agent/analyse", 1),
            ("operation", "module.b/operation/write", 1),
        ]
