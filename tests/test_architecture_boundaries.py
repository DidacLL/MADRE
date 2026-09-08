import asyncio
from pathlib import Path

from madre.adapters.openai import OpenAIChatConfig, OpenAICompatibleChatCapability
from madre.capabilities import (
    CapabilityDescriptor,
    CapabilityError,
    CapabilityRegistry,
    FunctionCapability,
)
from madre.contracts import (
    CapabilityRequest,
    ExecutionConstraints,
    ImmediateMaterial,
    WorkSubmission,
)
from madre.runtime import WorkRuntime, content_digest
from madre.security import SecurityAlgebra, SecurityContext, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def envelope(
    subject: str,
    *,
    origin: str = "test",
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
        scopes=scopes or {"domain.a"},
        origin=origin,
        provenance=("fixture",),
    )


def security(originator: str, *, trust: SecurityLevel = SecurityLevel.LEVEL_5) -> SecurityContext:
    return SecurityContext(envelopes=(envelope(originator, origin=originator, trust=trust),))


def capability(
    capability_id: str,
    function,
    *,
    boundary: str = "local",
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    risk: SecurityLevel = SecurityLevel.LEVEL_1,
) -> FunctionCapability:
    descriptor = CapabilityDescriptor(
        id=capability_id,
        kind="structured.compute",
        modality="json",
        execution_boundary=boundary,
        heavyweight=True,
        security=envelope(capability_id, trust=trust, risk=risk, scopes={"compute"}),
    )
    return FunctionCapability(descriptor, function)


def immediate(reference: str, payload, *, origin: str, sensitivity=SecurityLevel.LEVEL_2):
    digest = content_digest(payload)
    return ImmediateMaterial(
        reference=reference,
        payload=payload,
        envelope=envelope(digest, origin=origin, sensitivity=sensitivity),
    )


def test_security_envelope_tampering_is_detected() -> None:
    material = envelope("material", trust=SecurityLevel.LEVEL_4)
    tampered = material.model_copy(update={"trust": SecurityLevel.LEVEL_5})
    decision = SecurityAlgebra.evaluate(SecurityContext(envelopes=(tampered,)))
    assert not decision.admissible
    assert "invalid_integrity:material" in decision.deficits


def test_runtime_persistence_never_contains_input_output_or_provider_message(
    tmp_path: Path,
) -> None:
    private_input = "PROMPT-CONTENT-MUST-NOT-PERSIST"
    private_output = "MODEL-OUTPUT-MUST-NOT-PERSIST"
    private_error = "PROVIDER-ERROR-MUST-NOT-PERSIST"
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        capabilities = CapabilityRegistry()
        capabilities.register(capability("structured", lambda payload: {"answer": private_output}))
        capabilities.register(
            capability(
                "failing",
                lambda payload: (_ for _ in ()).throw(
                    CapabilityError("provider_failure", private_error)
                ),
            )
        )
        runtime = WorkRuntime(store, capabilities)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    security=security("module.a"),
                    capability=CapabilityRequest(
                        capability_id="structured",
                        kind="structured.compute",
                        modality="json",
                    ),
                    material=immediate(
                        "origin/task/1", {"prompt": private_input}, origin="module.a"
                    ),
                )
            )
        )
        assert asyncio.run(runtime.run_eligible()) == 1
        assert runtime.consume_result(record.id) == {"answer": private_output}

        failed = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    security=security("module.a"),
                    capability=CapabilityRequest(
                        capability_id="failing",
                        kind="structured.compute",
                        modality="json",
                    ),
                    material=immediate("origin/task/2", {"x": 1}, origin="module.a"),
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        inspected = runtime.inspect(failed.id)
        assert inspected is not None and inspected.failure is not None
        assert inspected.failure.code == "provider_failure"

        columns = {
            row[1] for row in connection.execute("PRAGMA table_info(runtime_work)").fetchall()
        }
        assert "input_json" not in columns
        assert "result_json" not in columns
        assert "payload" not in columns
        assert "error_message" not in columns

    for database_file in data_dir.glob("runtime.sqlite3*"):
        raw = database_file.read_bytes()
        assert private_input.encode() not in raw
        assert private_output.encode() not in raw
        assert private_error.encode() not in raw


def test_local_only_is_an_explicit_execution_constraint_not_a_default_policy() -> None:
    capabilities = CapabilityRegistry()
    capabilities.register(capability("remote", lambda payload: payload, boundary="remote"))
    request = CapabilityRequest(kind="structured.compute", modality="json")
    assert capabilities.select(request, ExecutionConstraints()) is not None
    assert capabilities.select(request, ExecutionConstraints(local_only=True)) is None


def test_provider_endpoint_policy_remains_inside_adapter_not_kernel_security() -> None:
    config = OpenAIChatConfig(
        endpoint="https://api.example.test/v1",
        model="provider-model",
        boundary="remote",
    )
    descriptor = CapabilityDescriptor(
        id="provider-chat",
        kind="model.inference.chat",
        modality="text",
        model_id=config.model,
        execution_boundary=config.boundary,
        security=envelope("provider-chat", risk=SecurityLevel.LEVEL_2),
    )
    adapter = OpenAICompatibleChatCapability(descriptor, config)
    assert adapter.descriptor.execution_boundary == "remote"


def test_model_execution_contract_is_not_chat_specific(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        capabilities = CapabilityRegistry()
        capabilities.register(
            capability(
                "vector",
                lambda payload: {"vector": [value * 2 for value in payload["vector"]]},
            )
        )
        runtime = WorkRuntime(store, capabilities)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.math",
                    security=security("module.math"),
                    capability=CapabilityRequest(kind="structured.compute", modality="json"),
                    material=immediate("vector/1", {"vector": [1, 2, 3]}, origin="module.math"),
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        assert runtime.consume_result(record.id) == {"vector": [2, 4, 6]}


def test_runtime_skips_candidate_rejected_by_carried_additive_algebra(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        capabilities = CapabilityRegistry()
        capabilities.register(
            capability("a-blocked", lambda payload: {"bad": True}, trust=SecurityLevel.LEVEL_1)
        )
        capabilities.register(capability("b-allowed", lambda payload: {"selected": "b-allowed"}))
        runtime = WorkRuntime(store, capabilities)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    security=security("module.a"),
                    capability=CapabilityRequest(kind="structured.compute", modality="json"),
                    material=immediate("candidate/1", {"x": 1}, origin="module.a"),
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        assert runtime.consume_result(record.id) == {"selected": "b-allowed"}
        decisions = connection.execute(
            """
            SELECT target_id, admissible
            FROM security_decision
            WHERE crossing_id=? AND crossing_kind='capability-candidate'
            ORDER BY id
            """,
            (record.id,),
        ).fetchall()
        assert [(row[0], row[1]) for row in decisions] == [("a-blocked", 0), ("b-allowed", 1)]


def test_security_context_accumulates_without_mutating_prior_state() -> None:
    initial = security("module.a")
    boundary = envelope("operation", risk=SecurityLevel.LEVEL_3, scopes={"domain.b"})
    derived = initial.extend(boundary)
    assert len(initial.envelopes) == 1
    assert len(derived.envelopes) == 2
    assert derived.risk == SecurityLevel.LEVEL_3
    assert derived.scopes == frozenset({"domain.a", "domain.b"})
