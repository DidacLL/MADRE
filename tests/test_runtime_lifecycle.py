from __future__ import annotations

import asyncio
from pathlib import Path

import pytest

from madre.capabilities import (
    CapabilityDescriptor,
    CapabilityError,
    CapabilityRegistry,
    FunctionCapability,
)
from madre.contracts import (
    InferenceHardRequirements,
    InferencePreferences,
    InferenceRequirement,
    TransientInferenceRequest,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.runtime import ResultLost, TransientInferenceError, WorkRuntime
from madre.security import (
    SecurityHistory,
    SecurityLevel,
    SecurityObject,
    SecuritySubjectRef,
    SecurityValues,
)
from madre.storage import PlatformStore, open_database
from madre_sdk import Artifact, MaterialRepository, participant_security

ORIGINATOR = "module.runtime-test"


def module_security():
    return participant_security(
        owner_module_id=ORIGINATOR,
        subject_id=ORIGINATOR,
        subject_kind="module",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )


def artifact(
    payload,
    *,
    sensitivity: SecurityLevel = SecurityLevel.LEVEL_5,
    integrity: SecurityLevel = SecurityLevel.LEVEL_5,
) -> Artifact:
    owner = module_security()
    return Artifact.create(
        owner_module_id=ORIGINATOR,
        artifact_id="runtime.input",
        payload=payload,
        sensitivity=sensitivity,
        integrity=integrity,
        security_history=SecurityHistory(objects=(owner,)),
    )


def capability(
    capability_id: str,
    function,
    *,
    privacy: SecurityLevel = SecurityLevel.LEVEL_5,
    integrity: SecurityLevel = SecurityLevel.LEVEL_5,
    heavyweight: bool = False,
) -> FunctionCapability:
    security = SecurityObject.issue(
        subject_ref=SecuritySubjectRef(
            owner_module_id="madre.platform",
            subject_kind="capability",
            publication_revision="1",
            local_id=capability_id,
        ),
        values=SecurityValues(privacy=privacy, integrity=integrity),
    )
    return FunctionCapability(
        CapabilityDescriptor(
            id=capability_id,
            specialization="model.inference.chat",
            modality="text",
            execution_boundary="local",
            heavyweight=heavyweight,
            security=security,
        ),
        function,
    )


def requirement(*mechanism_ids: str) -> InferenceRequirement:
    return InferenceRequirement(
        hard=InferenceHardRequirements(specialization="model.inference.chat"),
        preferences=InferencePreferences(mechanism_ids=mechanism_ids),
    )


class CountingResolver:
    def __init__(self, material) -> None:
        self.material = material
        self.calls = 0

    async def resolve(self, handle):
        self.calls += 1
        if handle.reference != self.material.reference:
            return None
        return self.material


def test_transient_selection_rejects_weak_privacy_without_poisoning_history(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    low = capability(
        "a-low",
        lambda payload: {"wrong": payload},
        privacy=SecurityLevel.LEVEL_2,
    )
    high = capability(
        "b-high",
        lambda payload: {"ok": payload},
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_4,
    )
    registry.register(low)
    registry.register(high)
    source = artifact(
        {"secret": "value"},
        sensitivity=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, registry)
        result = asyncio.run(
            runtime.infer(
                TransientInferenceRequest(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("a-low", "b-high"),
                    material=source.transient(),
                )
            )
        )
        assert result.capability_id == "b-high"
        assert result.output_integrity is None
        assert low.descriptor.security.security_id in {
            row["security_id"]
            for row in connection.execute("SELECT security_id FROM security_object").fetchall()
        }
        assert low.descriptor.security.security_id not in result.security.security_ids
        assert high.descriptor.security.security_id in result.security.security_ids
        assert len(result.security.transitions) == 1


def test_durable_security_denial_happens_before_jit_material_resolution(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(
        capability(
            "remote-weak",
            lambda payload: payload,
            privacy=SecurityLevel.LEVEL_2,
        )
    )
    source = artifact({"secret": "do not resolve"}, sensitivity=SecurityLevel.LEVEL_5)
    repository = MaterialRepository()
    handle = repository.retain(source)
    resolver = CountingResolver(source.transient())
    with open_database(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        runtime.register_material_resolver(ORIGINATOR, resolver)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("remote-weak"),
                    material=handle,
                )
            )
        )
        assert record.status == "accepted"
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None
        assert failed.status == "failed"
        assert failed.failure is not None and failed.failure.code == "security_denied"
        assert resolver.calls == 0


def test_durable_success_persists_accepted_transition_and_no_private_bytes(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(capability("local", lambda payload: {"private-output-marker": payload}))
    source = artifact({"private-input-marker": "sensitive"})
    repository = MaterialRepository()
    handle = repository.retain(source)
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, registry)
        runtime.register_material_resolver(ORIGINATOR, repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("local"),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert completed.result is not None
        assert completed.result.output_integrity is None
        assert completed.attempts[0].security_transition_id is not None
        assert len(completed.spec.security.transitions) == 1
        assert (
            connection.execute("SELECT COUNT(*) AS n FROM security_transition").fetchone()["n"] == 1
        )
    for path in tmp_path.glob("runtime.sqlite3*"):
        data = path.read_bytes()
        assert b"private-input-marker" not in data
        assert b"private-output-marker" not in data


def test_retry_with_same_immutable_operands_reuses_transition_identity(tmp_path: Path) -> None:
    calls = 0

    def flaky(payload):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise CapabilityError("boom")
        return {"ok": payload}

    registry = CapabilityRegistry()
    registry.register(capability("stable", flaky))
    source = artifact({"value": 1})
    repository = MaterialRepository()
    handle = repository.retain(source)
    with open_database(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        runtime.register_material_resolver(ORIGINATOR, repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("stable"),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None and failed.status == "failed"
        first_transition = failed.attempts[0].security_transition_id
        asyncio.run(
            runtime.retry(
                record.id,
                WorkRetryRequest(),
                idempotency_key="retry-1",
            )
        )
        asyncio.run(runtime.run_eligible())
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert len(completed.attempts) == 2
        assert completed.attempts[1].security_transition_id == first_transition
        assert len(completed.spec.security.transitions) == 1


def test_retry_with_different_capability_adds_new_transition(tmp_path: Path) -> None:
    first_registry = CapabilityRegistry()
    first_registry.register(
        capability("first", lambda payload: (_ for _ in ()).throw(CapabilityError("boom")))
    )
    source = artifact({"value": 1})
    repository = MaterialRepository()
    handle = repository.retain(source)
    with open_database(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), first_registry)
        runtime.register_material_resolver(ORIGINATOR, repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement(),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None and failed.status == "failed"
        first_transition = failed.attempts[0].security_transition_id

        second_registry = CapabilityRegistry()
        second_registry.register(capability("second", lambda payload: {"ok": payload}))
        runtime.capabilities = second_registry
        asyncio.run(
            runtime.retry(
                record.id,
                WorkRetryRequest(),
                idempotency_key="retry-different-capability",
            )
        )
        asyncio.run(runtime.run_eligible())
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert completed.attempts[1].security_transition_id != first_transition
        assert len(completed.spec.security.transitions) == 2


def test_material_unavailable_fails_without_starting_attempt(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(capability("local", lambda payload: payload))
    source = artifact({"value": 1})
    handle = source.handle()
    with open_database(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("local"),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None
        assert failed.failure is not None and failed.failure.code == "material_unavailable"
        assert failed.attempts == ()


def test_pending_work_can_be_cancelled_before_execution(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(capability("local", lambda payload: payload))
    source = artifact({"value": 1})
    with open_database(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("local"),
                    material=source.handle(),
                )
            )
        )
        cancelled = asyncio.run(runtime.cancel(record.id))
        assert cancelled.status == "cancelled"
        assert cancelled.cancellation is not None
        assert cancelled.cancellation.disposition == "prevented"


def test_unconsumed_result_is_marked_lost_after_restart(tmp_path: Path) -> None:
    registry = CapabilityRegistry()
    registry.register(capability("local", lambda payload: {"result": payload}))
    source = artifact({"value": 1})
    repository = MaterialRepository()
    handle = repository.retain(source)
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, registry)
        runtime.register_material_resolver(ORIGINATOR, repository)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator=ORIGINATOR,
                    security=source.security_history,
                    inference=requirement("local"),
                    material=handle,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        assert runtime.inspect(record.id).result.delivery_status == "awaiting_consumption"  # type: ignore[union-attr]
    with open_database(tmp_path) as connection:
        restarted = WorkRuntime(PlatformStore(connection), registry)
        recovered = restarted.inspect(record.id)
        assert recovered is not None and recovered.result is not None
        assert recovered.result.delivery_status == "lost"
        with pytest.raises(ResultLost):
            restarted.consume_result(record.id)


def test_transient_no_compatible_capability_is_distinct_from_security_denial(
    tmp_path: Path,
) -> None:
    source = artifact({"value": 1}, sensitivity=SecurityLevel.LEVEL_1)
    with open_database(tmp_path) as connection:
        runtime = WorkRuntime(PlatformStore(connection), CapabilityRegistry())
        with pytest.raises(TransientInferenceError) as exc_info:
            asyncio.run(
                runtime.infer(
                    TransientInferenceRequest(
                        originator=ORIGINATOR,
                        security=source.security_history,
                        inference=requirement(),
                        material=source.transient(),
                    )
                )
            )
        assert exc_info.value.code == "no_capability"
