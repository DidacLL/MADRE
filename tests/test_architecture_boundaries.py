import asyncio
from pathlib import Path

from madre.capabilities import CapabilityDescriptor, CapabilityRegistry, FunctionCapability
from madre.contracts import CapabilityRequest, ImmediateMaterial, WorkSubmission
from madre.runtime import WorkRuntime, content_digest
from madre.security import (
    BoundaryRequirements,
    SecurityAlgebra,
    SecurityEnvelope,
    SecurityLevel,
    SecurityPolicy,
)
from madre.storage import PlatformStore, open_database


def envelope(
    subject: str,
    *,
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    sensitivity: SecurityLevel = SecurityLevel.LEVEL_2,
    scopes: set[str] | None = None,
) -> SecurityEnvelope:
    return SecurityEnvelope.issue(
        subject=subject,
        sensitivity=sensitivity,
        trust=trust,
        risk=SecurityLevel.LEVEL_1,
        scopes=scopes or {"domain.a"},
        origin="test",
        provenance=("fixture",),
    )


def capability(capability_id: str, function) -> FunctionCapability:
    descriptor = CapabilityDescriptor(
        id=capability_id,
        kind="structured.compute",
        modality="json",
        execution_boundary="local",
        heavyweight=True,
        requirements=BoundaryRequirements(
            max_input_sensitivity=SecurityLevel.LEVEL_5,
            risk=SecurityLevel.LEVEL_1,
            allowed_scopes=frozenset({"*"}),
            allowed_execution_boundaries=frozenset({"local"}),
        ),
        security=envelope(f"capability:{capability_id}", scopes={"*"}),
    )
    return FunctionCapability(descriptor, function)


def immediate(reference: str, payload) -> ImmediateMaterial:
    digest = content_digest(payload)
    return ImmediateMaterial(reference=reference, payload=payload, envelope=envelope(digest))


def test_security_envelope_tampering_is_detected() -> None:
    material = envelope("material", trust=SecurityLevel.LEVEL_4)
    tampered = material.model_copy(update={"trust": SecurityLevel.LEVEL_5})
    decision = SecurityAlgebra.evaluate(
        envelope("requester"),
        tampered,
        BoundaryRequirements(allowed_execution_boundaries=frozenset({"local"})),
        envelope("destination", scopes={"*"}),
        "local",
        SecurityPolicy(),
    )
    assert not decision.admissible
    assert "invalid_integrity:material" in decision.deficits


def test_runtime_persistence_never_contains_input_or_output_content(tmp_path: Path) -> None:
    private_input = "PROMPT-CONTENT-MUST-NOT-PERSIST"
    private_output = "MODEL-OUTPUT-MUST-NOT-PERSIST"
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        capabilities = CapabilityRegistry()
        capabilities.register(capability("structured", lambda payload: {"answer": private_output}))
        runtime = WorkRuntime(store, capabilities)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    requester_envelope=envelope("module.a", scopes={"*"}),
                    capability=CapabilityRequest(kind="structured.compute", modality="json"),
                    material=immediate("origin/task/1", {"prompt": private_input}),
                )
            )
        )
        assert asyncio.run(runtime.run_eligible()) == 1
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert completed.result is not None
        assert runtime.consume_result(record.id) == {"answer": private_output}
        consumed = runtime.inspect(record.id)
        assert consumed is not None and consumed.result is not None
        assert consumed.result.delivery_status == "consumed"

        columns = {
            row[1] for row in connection.execute("PRAGMA table_info(runtime_work)").fetchall()
        }
        assert "input_json" not in columns
        assert "result_json" not in columns
        assert "payload" not in columns

    for database_file in data_dir.glob("runtime.sqlite3*"):
        raw = database_file.read_bytes()
        assert private_input.encode() not in raw
        assert private_output.encode() not in raw


def test_model_execution_contract_is_not_chat_structured(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        capabilities = CapabilityRegistry()
        capabilities.register(
            capability(
                "vector",
                lambda payload: {"vector": [value * 2 for value in payload["vector"]]},
            )
        )
        runtime = WorkRuntime(PlatformStore(connection), capabilities)
        submission = WorkSubmission(
            originator="module.math",
            requester_envelope=envelope("module.math", scopes={"*"}),
            capability=CapabilityRequest(kind="structured.compute", modality="json"),
            material=immediate("vector/1", {"vector": [1, 2, 3]}),
        )
        record = asyncio.run(runtime.submit(submission))
        asyncio.run(runtime.run_eligible())
        assert runtime.consume_result(record.id) == {"vector": [2, 4, 6]}
