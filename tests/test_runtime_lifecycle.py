import asyncio
from datetime import UTC, datetime, timedelta
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
    InferenceRequirement,
    MaterialHandle,
    TransientInferenceRequest,
    TransientMaterial,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.interfaces import MaterialResolver
from madre.runtime import ResultLost, WorkRuntime, content_digest
from madre.security import (
    ActorSecurityValues,
    CapabilitySecurityValues,
    MaterialSecurityValues,
    SecurityContext,
    SecurityLevel,
    SecurityObject,
)
from madre.storage import PlatformStore, open_database


def module_security(module_id: str, *, trust=SecurityLevel.LEVEL_5) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=module_id,
        subject_kind="module",
        values=ActorSecurityValues(trust=trust, isolation=SecurityLevel.LEVEL_5),
    )


def context(originator: str, *, trust=SecurityLevel.LEVEL_5) -> SecurityContext:
    return SecurityContext(objects=(module_security(originator, trust=trust),))


def material(
    reference: str,
    payload,
    *,
    sensitivity=SecurityLevel.LEVEL_2,
) -> TransientMaterial:
    return TransientMaterial(
        reference=reference,
        payload=payload,
        digest=content_digest(payload),
        security=SecurityObject.issue(
            subject_id=reference,
            subject_kind="artifact",
            values=MaterialSecurityValues(sensitivity=sensitivity),
        ),
    )


def requirement(**overrides) -> InferenceRequirement:
    return InferenceRequirement(
        hard=InferenceHardRequirements(
            specialization="structured.compute",
            modality="json",
            **overrides,
        )
    )


def capabilities(function, *, trust=SecurityLevel.LEVEL_5) -> CapabilityRegistry:
    descriptor = CapabilityDescriptor(
        id="compute",
        specialization="structured.compute",
        modality="json",
        execution_boundary="local",
        heavyweight=True,
        security=SecurityObject.issue(
            subject_id="compute",
            subject_kind="capability",
            values=CapabilitySecurityValues(
                trust=trust,
                privacy=SecurityLevel.LEVEL_5,
                risk=SecurityLevel.LEVEL_1,
            ),
        ),
    )
    result = CapabilityRegistry()
    result.register(FunctionCapability(descriptor, function))
    return result


class Resolver(MaterialResolver):
    def __init__(self, values: dict[str, TransientMaterial]) -> None:
        self.values = values
        self.requests: list[MaterialHandle] = []

    async def resolve(self, handle: MaterialHandle) -> TransientMaterial | None:
        self.requests.append(handle)
        return self.values.get(handle.reference)


def submission(originator: str, item: TransientMaterial, **kwargs) -> WorkSubmission:
    return WorkSubmission(
        originator=originator,
        security=context(originator),
        inference=requirement(),
        material=item.to_handle(coordination=f"coord:{item.reference}"),
        **kwargs,
    )


def test_accepted_durable_work_contains_no_private_payload_in_memory_or_sqlite(
    tmp_path: Path,
) -> None:
    private_input = "DURABLE-PRIVATE-INPUT-MUST-NOT-PERSIST"
    data_dir = tmp_path / "runtime"
    item = material("module.a/material/1", {"private": private_input})
    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(lambda value: value))
        record = asyncio.run(runtime.submit(submission("module.a", item)))
        assert runtime.inspect(record.id) is not None
        assert not hasattr(runtime, "_materials")
        columns = {
            row[1] for row in connection.execute("PRAGMA table_info(runtime_work)").fetchall()
        }
        assert "material_handle_json" in columns
        assert "payload" not in columns
        assert "input_json" not in columns
        assert "result_json" not in columns

    for database_file in data_dir.glob("runtime.sqlite3*"):
        assert private_input.encode() not in database_file.read_bytes()


def test_material_resolution_is_jit_after_eligibility_and_candidate_readiness(
    tmp_path: Path,
) -> None:
    future = datetime.now(UTC) + timedelta(days=1)
    item = material("module.a/material/jit", {"secret": "jit"})
    resolver = Resolver({item.reference: item})
    with open_database(tmp_path / "runtime") as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(lambda value: value))
        runtime.register_material_resolver("module.a", resolver)
        asyncio.run(runtime.submit(submission("module.a", item, eligible_at=future)))
        assert resolver.requests == []
        assert asyncio.run(runtime.run_eligible()) == 0
        assert resolver.requests == []

    denied = material(
        "module.a/material/denied",
        {"secret": "denied"},
        sensitivity=SecurityLevel.LEVEL_4,
    )
    denied_resolver = Resolver({denied.reference: denied})
    with open_database(tmp_path / "denied") as connection:
        runtime = WorkRuntime(
            PlatformStore(connection),
            capabilities(lambda value: value, trust=SecurityLevel.LEVEL_2),
        )
        runtime.register_material_resolver("module.a", denied_resolver)
        record = asyncio.run(runtime.submit(submission("module.a", denied)))
        asyncio.run(runtime.run_eligible())
        inspected = runtime.inspect(record.id)
        assert inspected is not None and inspected.failure is not None
        assert inspected.failure.code == "security_denied"
        assert denied_resolver.requests == []


def test_restart_and_retry_reacquire_module_owned_material(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    item = material("module.a/material/retry", {"private": "reacquire"})
    resolver = Resolver({item.reference: item})
    calls = 0

    def fail_once(payload):
        nonlocal calls
        calls += 1
        if calls == 1:
            raise CapabilityError("provider_failure", "private detail")
        return {"reused": payload}

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(fail_once))
        record = asyncio.run(runtime.submit(submission("module.a", item)))
        assert resolver.requests == []

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(fail_once))
        runtime.register_material_resolver("module.a", resolver)
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None and failed.status == "failed"
        assert len(resolver.requests) == 1

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(fail_once))
        runtime.register_material_resolver("module.a", resolver)
        asyncio.run(runtime.retry(record.id, WorkRetryRequest(), idempotency_key="retry-1"))
        asyncio.run(runtime.run_eligible())
        succeeded = runtime.inspect(record.id)
        assert succeeded is not None and succeeded.status == "succeeded"
        assert len(resolver.requests) == 2
        assert runtime.consume_result(record.id) == {"reused": item.payload}


def test_material_unavailable_and_continuity_mismatches_fail_safely(tmp_path: Path) -> None:
    original = material("module.a/material/original", {"x": 1})

    cases = {
        "unavailable": None,
        "reference": material("module.a/material/other", {"x": 1}),
        "digest": material("module.a/material/original", {"x": 2}),
        "security": TransientMaterial(
            reference=original.reference,
            payload=original.payload,
            digest=original.digest,
            security=SecurityObject.issue(
                subject_id=original.reference,
                subject_kind="artifact",
                values=MaterialSecurityValues(sensitivity=SecurityLevel.LEVEL_3),
            ),
        ),
    }
    for name, resolved in cases.items():
        with open_database(tmp_path / name) as connection:
            runtime = WorkRuntime(PlatformStore(connection), capabilities(lambda value: value))
            values = {} if resolved is None else {original.reference: resolved}
            runtime.register_material_resolver("module.a", Resolver(values))
            record = asyncio.run(runtime.submit(submission("module.a", original)))
            asyncio.run(runtime.run_eligible())
            inspected = runtime.inspect(record.id)
            assert inspected is not None and inspected.failure is not None
            expected = "material_unavailable" if name == "unavailable" else "material_integrity"
            assert inspected.failure.code == expected


def test_scheduler_fairness_priority_cancellation_and_interrupted_recovery(tmp_path: Path) -> None:
    order: list[str] = []

    def execute(payload):
        order.append(payload["name"])
        return payload

    data_dir = tmp_path / "runtime"
    resolver_values: dict[str, TransientMaterial] = {}
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, capabilities(execute))
        resolver = Resolver(resolver_values)
        for originator in ("a", "b"):
            runtime.register_material_resolver(originator, resolver)
        for originator, name, priority in [
            ("a", "a-low", 0),
            ("a", "a-high", 50),
            ("b", "b", 0),
        ]:
            item = material(name, {"name": name})
            resolver_values[name] = item
            asyncio.run(runtime.submit(submission(originator, item, priority=priority)))
        asyncio.run(runtime.run_eligible())
        assert order[:3] == ["a-high", "b", "a-low"]

        cancel_item = material("cancel", {"name": "cancel"})
        resolver_values[cancel_item.reference] = cancel_item
        cancel_record = asyncio.run(
            runtime.submit(
                submission(
                    "a",
                    cancel_item,
                    eligible_at=datetime.now(UTC) + timedelta(days=1),
                )
            )
        )
        assert asyncio.run(runtime.cancel(cancel_record.id)).status == "cancelled"

        interrupted_item = material("interrupted", {"name": "interrupted"})
        resolver_values[interrupted_item.reference] = interrupted_item
        interrupted = asyncio.run(runtime.submit(submission("a", interrupted_item)))
        assert (
            store.start_attempt(
                interrupted.id,
                "compute",
                None,
                None,
                "local",
                datetime.now(UTC),
            )
            == 1
        )

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(execute))
        recovered = runtime.inspect(interrupted.id)
        assert recovered is not None and recovered.status == "failed"
        assert recovered.failure is not None and recovered.failure.code == "interrupted"


def test_unconsumed_result_is_truthfully_lost_after_restart(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    item = material("module.a/material/result", {"x": 1})
    with open_database(data_dir) as connection:
        runtime = WorkRuntime(
            PlatformStore(connection), capabilities(lambda value: {"done": value})
        )
        runtime.register_material_resolver("module.a", Resolver({item.reference: item}))
        record = asyncio.run(runtime.submit(submission("module.a", item)))
        asyncio.run(runtime.run_eligible())
        produced = runtime.inspect(record.id)
        assert produced is not None and produced.result is not None
        assert produced.result.delivery_status == "awaiting_consumption"

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(lambda value: value))
        reopened = runtime.inspect(record.id)
        assert reopened is not None and reopened.result is not None
        assert reopened.result.delivery_status == "lost"
        with pytest.raises(ResultLost):
            runtime.consume_result(record.id)


def test_generated_results_can_be_reused_by_module_without_kernel_semantics(tmp_path: Path) -> None:
    values: dict[str, TransientMaterial] = {}
    resolver = Resolver(values)
    with open_database(tmp_path / "runtime") as connection:
        runtime = WorkRuntime(
            PlatformStore(connection), capabilities(lambda value: {"wrapped": value})
        )
        runtime.register_material_resolver("module.a", resolver)
        first = material("input/1", {"fact": 1})
        values[first.reference] = first
        first_record = asyncio.run(runtime.submit(submission("module.a", first)))
        asyncio.run(runtime.run_eligible())
        generated = runtime.consume_result(first_record.id)

        second = material("generated/1", generated)
        values[second.reference] = second
        second_record = asyncio.run(runtime.submit(submission("module.a", second)))
        asyncio.run(runtime.run_eligible())
        assert runtime.consume_result(second_record.id) == {"wrapped": generated}


def test_jit_resolution_waits_until_heavyweight_local_slot_is_available(tmp_path: Path) -> None:
    durable_item = material("durable/slot", {"kind": "durable"})
    transient_item = material("transient/slot", {"kind": "transient"})
    resolver = Resolver({durable_item.reference: durable_item})
    entered = asyncio.Event()
    release = asyncio.Event()
    running = 0
    max_running = 0

    async def execute(payload):
        nonlocal running, max_running
        running += 1
        max_running = max(max_running, running)
        if payload["kind"] == "transient":
            entered.set()
            await release.wait()
        running -= 1
        return payload

    async def scenario() -> None:
        with open_database(tmp_path / "runtime") as connection:
            runtime = WorkRuntime(PlatformStore(connection), capabilities(execute))
            runtime.register_material_resolver("module.a", resolver)
            durable = await runtime.submit(submission("module.a", durable_item))
            transient_request = TransientInferenceRequest(
                originator="module.a",
                security=context("module.a"),
                inference=requirement(),
                material=transient_item,
            )
            transient_task = asyncio.create_task(runtime.infer(transient_request))
            await entered.wait()
            durable_task = asyncio.create_task(runtime.run_eligible())
            await asyncio.sleep(0)
            assert resolver.requests == []
            release.set()
            await transient_task
            assert await durable_task == 1
            assert resolver.requests == [durable_item.to_handle(coordination="coord:durable/slot")]
            assert runtime.inspect(durable.id) is not None

    asyncio.run(scenario())
    assert max_running == 1


def test_carried_security_objects_survive_restart_and_ignore_later_registry_state(
    tmp_path: Path,
) -> None:
    from madre.registry import InteroperabilityRegistry, ModuleManifest

    data_dir = tmp_path / "runtime"
    item = material("module.a/material/security", {"private": "carried"})
    initial_module_security = module_security("module.a", trust=SecurityLevel.LEVEL_5)
    submission_context = SecurityContext(objects=(initial_module_security,))
    work = WorkSubmission(
        originator="module.a",
        security=submission_context,
        inference=requirement(),
        material=item.to_handle(),
    )
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(
            ModuleManifest(
                module_id="module.a",
                version="1",
                description="initial",
                security=initial_module_security,
            )
        )
        runtime = WorkRuntime(store, capabilities(lambda value: value))
        record = asyncio.run(runtime.submit(work))
        registry.register(
            ModuleManifest(
                module_id="module.a",
                version="2",
                description="changed",
                security=module_security("module.a", trust=SecurityLevel.LEVEL_1),
            )
        )

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(lambda value: value))
        runtime.register_material_resolver("module.a", Resolver({item.reference: item}))
        restored = runtime.inspect(record.id)
        assert restored is not None
        assert restored.spec.security.objects[0] == initial_module_security
        asyncio.run(runtime.run_eligible())
        assert runtime.inspect(record.id).status == "succeeded"  # type: ignore[union-attr]
