from __future__ import annotations

import asyncio
import inspect
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

from madre.adapters.openai import OpenAIChatConfig
from madre.capabilities import (
    CapabilityDescriptor,
    CapabilityError,
    CapabilityRegistry,
    FunctionCapability,
)
from madre.contracts import (
    InferenceHardRequirements,
    InferenceRequirement,
    TransientInferenceRequest,
    TransientInferenceResult,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.registry import InteroperabilityRegistry, ModuleManifest
from madre.runtime import TransientInferenceError, WorkRuntime
from madre.security import (
    DirectUserInteraction,
    DisclosureNormalForm,
    Integrity,
    Privacy,
    ScopeBinding,
    SecurityObject,
    SecurityScopeRef,
    Sensitivity,
)
from madre.storage import PlatformStore, open_database
from madre_sdk import Artifact, MaterialRepository, participant_security


class CountingRepository(MaterialRepository):
    def __init__(self) -> None:
        super().__init__()
        self.resolutions = 0

    async def resolve(self, handle):
        self.resolutions += 1
        return await super().resolve(handle)


def observer(name: str, privacy: Privacy, boundary: str) -> CapabilityDescriptor:
    security = SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id="adapter.owner", scope_id=name, publication_revision="1"
        ),
        privacy=privacy,
        binding=ScopeBinding(contract_digest="b" * 64),
    )
    return CapabilityDescriptor(
        id=name,
        specialization="model.inference.chat",
        modality="text",
        execution_boundary=boundary,  # type: ignore[arg-type]
        security=security,
    )


def requirements() -> InferenceRequirement:
    return InferenceRequirement(
        hard=InferenceHardRequirements(specialization="model.inference.chat")
    )


def store(path: Path):
    return open_database(path)


def test_capability_privacy_is_declared_not_derived_from_location() -> None:
    registry = CapabilityRegistry()
    registry.register(
        FunctionCapability(
            observer("remote-private", Privacy.MODULE_PRIVATE, "remote"), lambda x: x
        )
    )
    registry.register(
        FunctionCapability(observer("local-public", Privacy.PUBLIC, "local"), lambda x: x)
    )
    assert {item.descriptor.id for item in registry.candidates(requirements())} == {
        "remote-private",
        "local-public",
    }
    assert OpenAIChatConfig(endpoint="http://localhost:1234", model="m").privacy is Privacy.UNKNOWN


def test_privacy_bearing_capability_rejects_none() -> None:
    invalid = SecurityObject.issue(
        scope=SecurityScopeRef(
            owner_module_id="adapter.owner", scope_id="invalid", publication_revision="1"
        )
    )
    descriptor = CapabilityDescriptor(
        id="invalid",
        specialization="model.inference.chat",
        modality="text",
        execution_boundary="local",
        security=invalid,
    )
    with pytest.raises(ValueError, match="requires Privacy"):
        CapabilityRegistry().register(FunctionCapability(descriptor, lambda x: x))


def test_runtime_candidate_denial_does_not_enter_carried_evidence(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    denied = observer("a-denied", Privacy.PUBLIC, "local")
    accepted = observer("b-accepted", Privacy.SECRET, "remote")
    registry.register(FunctionCapability(denied, lambda _: {"ok": True}))
    registry.register(FunctionCapability(accepted, lambda _: {"ok": True}))
    material = Artifact.create(
        owner_module_id="owner",
        artifact_id="input",
        payload={"secret": True},
        sensitivity=Sensitivity.S5,
    )
    with store(tmp_path) as connection:
        platform = PlatformStore(connection)
        runtime = WorkRuntime(platform, registry)
        result = asyncio.run(
            runtime.infer(
                TransientInferenceRequest(
                    originator="owner",
                    evidence=material.evidence,
                    inference=requirements(),
                    material=material.transient(),
                )
            )
        )
        assert result.capability_id == "b-accepted"
        assert result.evidence.resolve(denied.security.security_id) is None
        assert result.evidence.resolve(accepted.security.security_id) == accepted.security
        decisions = connection.execute(
            "SELECT target_id,admissible FROM security_decision ORDER BY id"
        ).fetchall()
        assert [(row[0], row[1]) for row in decisions] == [
            ("a-denied", 0),
            ("b-accepted", 1),
        ]


def test_transient_runtime_binds_a_fresh_direct_user_crossing(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    public = observer("public", Privacy.PUBLIC, "remote")
    registry.register(FunctionCapability(public, lambda _: {"published": True}))
    material = Artifact.create(
        owner_module_id="owner",
        artifact_id="account-number",
        payload="1234",
        sensitivity=Sensitivity.S5,
    )
    interaction = DirectUserInteraction(
        interaction_scope=SecurityScopeRef(
            owner_module_id="owner", scope_id="posting-ui", publication_revision="1"
        ),
        interaction_revision="1",
        execution_id="live-post",
    )
    with store(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        result = asyncio.run(
            runtime.infer(
                TransientInferenceRequest(
                    originator="owner",
                    evidence=material.evidence,
                    inference=requirements(),
                    material=material.transient(),
                    direct_interaction=interaction,
                )
            )
        )
        disclosure = result.evidence.relations[-1]
        assert isinstance(disclosure, DisclosureNormalForm)
        assert disclosure.direct_user_action is not None
        assert disclosure.direct_user_action.interaction == interaction

        with pytest.raises(TransientInferenceError, match="security_denied"):
            asyncio.run(
                runtime.infer(
                    TransientInferenceRequest(
                        originator="another-module",
                        evidence=material.evidence,
                        inference=requirements(),
                        material=material.transient(),
                        direct_interaction=interaction,
                    )
                )
            )


def test_durable_work_has_no_direct_interaction_and_persists_relation_id(
    tmp_path: Path,
) -> None:
    registry = CapabilityRegistry()
    capability = observer("private", Privacy.SECRET, "local")
    registry.register(FunctionCapability(capability, lambda _: {"done": True}))
    material = Artifact.create(
        owner_module_id="owner",
        artifact_id="input",
        payload="payload",
        sensitivity=Sensitivity.S4,
    )
    with store(tmp_path) as connection:
        platform = PlatformStore(connection)
        runtime = WorkRuntime(platform, registry)
        repository = MaterialRepository()
        handle = repository.retain(material)
        runtime.register_material_resolver("owner", repository)
        submission = WorkSubmission(
            originator="owner",
            evidence=material.evidence,
            inference=requirements(),
            material=handle,
        )
        assert "direct_interaction" not in WorkSubmission.model_fields
        record = asyncio.run(runtime.submit(submission))
        assert record.status == "accepted"
        asyncio.run(runtime.run_eligible())
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert completed.result is not None
        assert "output_integrity" not in type(completed.result).model_fields
        assert completed.attempts[0].disclosure_relation_id is not None
        columns = {
            row[1] for row in connection.execute("PRAGMA table_info(runtime_work)").fetchall()
        }
        assert "security_evidence_json" in columns
        assert "security_history_json" not in columns
        assert "output_integrity" not in columns


def test_durable_denial_happens_before_private_material_resolution(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(
        FunctionCapability(observer("public", Privacy.PUBLIC, "remote"), lambda value: value)
    )
    material = Artifact.create(
        owner_module_id="owner",
        artifact_id="secret",
        payload="private bytes",
        sensitivity=Sensitivity.S5,
    )
    repository = CountingRepository()
    handle = repository.retain(material)
    with store(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        runtime.register_material_resolver("owner", repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="owner",
                    evidence=material.evidence,
                    inference=requirements(),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        denied = runtime.inspect(record.id)
        assert denied is not None and denied.failure is not None
        assert denied.failure.code == "security_denied"
        assert denied.attempts == ()
        assert repository.resolutions == 0


def test_retry_recomposes_the_same_relation_and_result_loss_remains_truthful(
    tmp_path: Path,
) -> None:
    calls = 0

    def sometimes_fails(value):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise CapabilityError("temporary")
        return value

    registry = CapabilityRegistry()
    registry.register(
        FunctionCapability(observer("private", Privacy.SECRET, "local"), sometimes_fails)
    )
    material = Artifact.create(
        owner_module_id="owner",
        artifact_id="retry-input",
        payload={"work": True},
        sensitivity=Sensitivity.S4,
    )
    repository = MaterialRepository()
    handle = repository.retain(material)
    with store(tmp_path) as connection:
        platform = PlatformStore(connection)
        runtime = WorkRuntime(platform, registry)
        runtime.register_material_resolver("owner", repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="owner",
                    evidence=material.evidence,
                    inference=requirements(),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None and failed.status == "failed"
        asyncio.run(runtime.retry(record.id, WorkRetryRequest(), idempotency_key="retry-once"))
        asyncio.run(runtime.run_eligible())
        succeeded = runtime.inspect(record.id)
        assert succeeded is not None and succeeded.status == "succeeded"
        assert len(succeeded.attempts) == 2
        assert (
            succeeded.attempts[0].disclosure_relation_id
            == succeeded.attempts[1].disclosure_relation_id
        )
        replacement = WorkRuntime(platform, registry)
        lost = replacement.inspect(record.id)
        assert lost is not None and lost.result is not None
        assert lost.result.delivery_status == "lost"


def test_delayed_work_can_be_cancelled_without_resolution(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(
        FunctionCapability(observer("private", Privacy.SECRET, "local"), lambda value: value)
    )
    material = Artifact.create(
        owner_module_id="owner",
        artifact_id="later",
        payload="later",
        sensitivity=Sensitivity.S3,
    )
    repository = CountingRepository()
    handle = repository.retain(material)
    with store(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        runtime.register_material_resolver("owner", repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="owner",
                    evidence=material.evidence,
                    inference=requirements(),
                    material=handle,
                    eligible_at=datetime.now(UTC) + timedelta(hours=1),
                )
            )
        )
        cancelled = asyncio.run(runtime.cancel(record.id))
        assert cancelled.status == "cancelled"
        assert cancelled.cancellation is not None
        assert cancelled.cancellation.disposition == "prevented"
        assert repository.resolutions == 0


def test_registry_is_an_honest_deterministic_catalog(tmp_path: Path) -> None:
    module_b = participant_security(
        owner_module_id="b", scope_id="b", privacy=Privacy.SECRET, integrity=Integrity.I5
    )
    module_a = participant_security(
        owner_module_id="a", scope_id="a", privacy=Privacy.SECRET, integrity=Integrity.I5
    )
    with store(tmp_path) as connection:
        registry = InteroperabilityRegistry(PlatformStore(connection))
        registry.register(
            ModuleManifest(module_id="b", version="1", description="B", security=module_b)
        )
        registry.register(
            ModuleManifest(module_id="a", version="1", description="A", security=module_a)
        )
        assert [item.module_id for item in registry._store.manifests()] == ["a", "b"]
        assert registry.list_agents() == ()
        assert registry.list_skills() == ()
        assert registry.list_workflows() == ()
        assert registry.list_operations() == ()
        for name in ("list_agents", "list_skills", "list_workflows", "list_operations"):
            assert len(inspect.signature(getattr(registry, name)).parameters) == 0


def test_generated_inference_contract_has_no_integrity_claim() -> None:
    assert "output_integrity" not in TransientInferenceResult.model_fields
