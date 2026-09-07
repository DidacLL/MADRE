import asyncio
from pathlib import Path

import pytest
from pydantic import ValidationError

from madre.capabilities import (
    CapabilityDescriptor,
    CapabilityError,
    CapabilityRegistry,
    FunctionCapability,
)
from madre.config import CapabilityConfig
from madre.contracts import (
    CapabilityRequest,
    CorrelationEntry,
    ImmediateMaterial,
    WorkSubmission,
)
from madre.registry import InteroperabilityRegistry, ModuleManifest
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
    origin: str = "test-installation",
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
        origin=origin,
        provenance=("fixture",),
    )


def module_manifest(
    module_id: str,
    *,
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    scopes: set[str] | None = None,
) -> ModuleManifest:
    return ModuleManifest(
        module_id=module_id,
        version="1",
        description="Test Module",
        security=envelope(module_id, trust=trust, scopes=scopes or {"*"}),
        inbound_requirements=BoundaryRequirements(
            max_input_sensitivity=SecurityLevel.LEVEL_5,
            allowed_scopes=frozenset(scopes or {"*"}),
            allowed_execution_boundaries=frozenset({"local"}),
        ),
    )


def capability(capability_id: str, function, *, boundary: str = "local") -> FunctionCapability:
    descriptor = CapabilityDescriptor(
        id=capability_id,
        kind="structured.compute",
        modality="json",
        execution_boundary=boundary,
        heavyweight=True,
        requirements=BoundaryRequirements(
            max_input_sensitivity=SecurityLevel.LEVEL_5,
            risk=SecurityLevel.LEVEL_1,
            allowed_scopes=frozenset({"*"}),
            allowed_execution_boundaries=frozenset({boundary}),
        ),
        security=envelope(capability_id, scopes={"*"}),
    )
    return FunctionCapability(descriptor, function)


def immediate(reference: str, payload, *, origin: str) -> ImmediateMaterial:
    digest = content_digest(payload)
    return ImmediateMaterial(
        reference=reference,
        payload=payload,
        envelope=envelope(digest, origin=origin),
    )


def test_security_envelope_tampering_is_detected() -> None:
    material = envelope("material", trust=SecurityLevel.LEVEL_4)
    tampered = material.model_copy(update={"trust": SecurityLevel.LEVEL_5})
    decision = SecurityAlgebra.evaluate(
        envelope("requester"),
        tampered,
        BoundaryRequirements(allowed_execution_boundaries=frozenset({"local"})),
        envelope("target", scopes={"*"}),
        envelope("destination", scopes={"*"}),
        "local",
        SecurityPolicy(),
    )
    assert not decision.admissible
    assert "invalid_integrity:material" in decision.deficits


def test_runtime_persistence_never_contains_input_output_or_error_content(tmp_path: Path) -> None:
    private_input = "PROMPT-CONTENT-MUST-NOT-PERSIST"
    private_output = "MODEL-OUTPUT-MUST-NOT-PERSIST"
    private_error = "PROVIDER-ERROR-MUST-NOT-PERSIST"
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(module_manifest("module.a"))
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
        runtime = WorkRuntime(store, capabilities, registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    capability=CapabilityRequest(
                        capability_id="structured",
                        kind="structured.compute",
                        modality="json",
                    ),
                    material=immediate(
                        "origin/task/1",
                        {"prompt": private_input},
                        origin="module.a",
                    ),
                    correlation=(CorrelationEntry(key="turn", value="turn-1"),),
                )
            )
        )
        assert asyncio.run(runtime.run_eligible()) == 1
        assert runtime.consume_result(record.id) == {"answer": private_output}

        failed = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
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
        assert runtime.inspect(failed.id).failure.code == "provider_failure"  # type: ignore[union-attr]

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


def test_durable_opaque_metadata_rejects_content_shaped_values() -> None:
    with pytest.raises(ValidationError):
        CorrelationEntry(key="private prompt", value="turn-1")
    with pytest.raises(ValidationError):
        SecurityEnvelope.issue(
            subject="material",
            sensitivity=SecurityLevel.LEVEL_2,
            trust=SecurityLevel.LEVEL_3,
            risk=SecurityLevel.LEVEL_1,
            scopes={"*"},
            origin="module.a",
            provenance=("raw private sentence with spaces",),
        )


def test_local_only_selection_cannot_fall_back_to_remote() -> None:
    capabilities = CapabilityRegistry()
    capabilities.register(capability("remote", lambda payload: payload, boundary="remote"))
    request = CapabilityRequest(kind="structured.compute", modality="json")
    from madre.contracts import ExecutionConstraints

    assert capabilities.select(request, ExecutionConstraints(local_only=True)) is None
    assert capabilities.select(request, ExecutionConstraints(local_only=False)) is not None


def test_local_http_capability_cannot_be_mislabeled_remote_endpoint() -> None:
    with pytest.raises(ValidationError):
        CapabilityConfig(endpoint="http://192.0.2.10:11434", model="test", boundary="local")


def test_model_execution_contract_is_not_chat_structured(tmp_path: Path) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(module_manifest("module.math"))
        capabilities = CapabilityRegistry()
        capabilities.register(
            capability(
                "vector",
                lambda payload: {"vector": [value * 2 for value in payload["vector"]]},
            )
        )
        runtime = WorkRuntime(store, capabilities, registry)
        submission = WorkSubmission(
            originator="module.math",
            capability=CapabilityRequest(kind="structured.compute", modality="json"),
            material=immediate("vector/1", {"vector": [1, 2, 3]}, origin="module.math"),
        )
        record = asyncio.run(runtime.submit(submission))
        asyncio.run(runtime.run_eligible())
        assert runtime.consume_result(record.id) == {"vector": [2, 4, 6]}


def test_runtime_skips_inadmissible_candidate_and_uses_next_permitted_capability(
    tmp_path: Path,
) -> None:
    with open_database(tmp_path / "runtime") as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(module_manifest("module.a"))
        capabilities = CapabilityRegistry()

        blocked_descriptor = CapabilityDescriptor(
            id="a-blocked",
            kind="structured.compute",
            modality="json",
            execution_boundary="local",
            heavyweight=False,
            requirements=BoundaryRequirements(
                max_input_sensitivity=SecurityLevel.LEVEL_1,
                risk=SecurityLevel.LEVEL_1,
                allowed_scopes=frozenset({"*"}),
                allowed_execution_boundaries=frozenset({"local"}),
            ),
            security=envelope("a-blocked", scopes={"*"}),
        )
        capabilities.register(FunctionCapability(blocked_descriptor, lambda payload: {"bad": True}))
        capabilities.register(capability("b-allowed", lambda payload: {"selected": "b-allowed"}))

        runtime = WorkRuntime(store, capabilities, registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    capability=CapabilityRequest(kind="structured.compute", modality="json"),
                    material=immediate("candidate/1", {"x": 1}, origin="module.a"),
                )
            )
        )
        asyncio.run(runtime.run_eligible())

        assert runtime.consume_result(record.id) == {"selected": "b-allowed"}
        completed = runtime.inspect(record.id)
        assert completed is not None
        assert completed.attempts[0].capability_id == "b-allowed"
        decisions = connection.execute(
            """
            SELECT target_id, admissible
            FROM security_decision
            WHERE crossing_id=? AND crossing_kind='capability-candidate'
            ORDER BY id
            """,
            (record.id,),
        ).fetchall()
        assert [(row[0], row[1]) for row in decisions] == [
            ("a-blocked", 0),
            ("b-allowed", 1),
        ]
