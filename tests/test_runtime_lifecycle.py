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
    CapabilityRequest,
    DelayedMaterial,
    ImmediateMaterial,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.registry import InteroperabilityRegistry, ModuleManifest
from madre.runtime import MaterialProvider, ResultLost, WorkRuntime, content_digest
from madre.security import SecurityContext, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def sec(
    subject: str,
    *,
    origin: str = "fixture",
    trust: SecurityLevel = SecurityLevel.LEVEL_5,
    sensitivity: SecurityLevel = SecurityLevel.LEVEL_1,
) -> SecurityEnvelope:
    return SecurityEnvelope.issue(
        subject=subject,
        sensitivity=sensitivity,
        trust=trust,
        risk=SecurityLevel.LEVEL_1,
        scopes={"runtime"},
        origin=origin,
    )


def context(originator: str) -> SecurityContext:
    return SecurityContext(envelopes=(sec(originator, origin=originator),))


def make_material(reference: str, payload, origin: str) -> ImmediateMaterial:
    digest = content_digest(payload)
    return ImmediateMaterial(
        reference=reference,
        payload=payload,
        envelope=sec(digest, origin=origin, sensitivity=SecurityLevel.LEVEL_2),
    )


def capabilities(function) -> CapabilityRegistry:
    descriptor = CapabilityDescriptor(
        id="compute",
        kind="compute",
        modality="json",
        execution_boundary="local",
        heavyweight=True,
        security=sec("compute"),
    )
    result = CapabilityRegistry()
    result.register(FunctionCapability(descriptor, function))
    return result


class Provider(MaterialProvider):
    def __init__(self, material: ImmediateMaterial):
        self.material = material
        self.requests: list[str] = []

    async def resolve(self, reference: str) -> ImmediateMaterial | None:
        self.requests.append(reference)
        return self.material if reference == self.material.reference else None


def test_delayed_work_survives_restart_while_material_stays_with_originator(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    payload = {"private": "originator-retained"}
    material = make_material("module.a/material/42", payload, "module.a")
    delayed = DelayedMaterial(
        reference=material.reference,
        digest=content_digest(payload),
        envelope=material.envelope,
    )
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, capabilities(lambda value: {"seen": value}))
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    security=context("module.a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=delayed,
                )
            )
        )
        assert runtime.inspect(record.id).status == "accepted"  # type: ignore[union-attr]

    provider = Provider(material)
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, capabilities(lambda value: {"seen": value}))
        runtime.register_material_provider("module.a", provider)
        assert asyncio.run(runtime.run_eligible()) == 1
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert provider.requests == [material.reference]
        assert runtime.consume_result(record.id) == {"seen": payload}


def test_delayed_work_keeps_carried_security_when_registry_metadata_changes(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    payload = {"private": "carried-boundary-state"}
    material = make_material("module.a/material/43", payload, "module.a")
    delayed = DelayedMaterial(
        reference=material.reference,
        digest=content_digest(payload),
        envelope=material.envelope,
    )
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(ModuleManifest(module_id="module.a", version="1", description="before"))
        runtime = WorkRuntime(store, capabilities(lambda value: value))
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    security=context("module.a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=delayed,
                )
            )
        )
        registry.register(ModuleManifest(module_id="module.a", version="2", description="after"))
        runtime.register_material_provider("module.a", Provider(material))
        asyncio.run(runtime.run_eligible())
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        stored = connection.execute(
            """
            SELECT context_json FROM security_decision
            WHERE crossing_id=? AND crossing_kind='capability-candidate'
            """,
            (record.id,),
        ).fetchone()[0]
        assert '"version":"2"' not in stored
        assert '"version":"1"' not in stored


def test_unconsumed_result_is_truthfully_marked_lost_after_restart(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, capabilities(lambda value: {"done": value}))
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    security=context("module.a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=make_material("m/1", {"x": 1}, "module.a"),
                )
            )
        )
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


def test_scheduler_fairness_priority_cancellation_retry_and_recovery(tmp_path: Path) -> None:
    order: list[str] = []
    attempts = {"fail-once": 0}

    def execute(payload):
        name = payload["name"]
        order.append(name)
        if name == "fail-once" and attempts[name] == 0:
            attempts[name] += 1
            raise CapabilityError("provider_failure", "private provider detail")
        return {"name": name}

    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, capabilities(execute))
        for originator, name, priority in [
            ("a", "a-low", 0),
            ("a", "a-high", 50),
            ("b", "b", 0),
        ]:
            asyncio.run(
                runtime.submit(
                    WorkSubmission(
                        originator=originator,
                        security=context(originator),
                        capability=CapabilityRequest(kind="compute", modality="json"),
                        material=make_material(name, {"name": name}, originator),
                        priority=priority,
                    )
                )
            )
        asyncio.run(runtime.run_eligible())
        assert order[:3] == ["a-high", "b", "a-low"]

        future = datetime.now(UTC) + timedelta(days=1)
        cancel_record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="a",
                    security=context("a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=make_material("cancel", {"name": "cancel"}, "a"),
                    eligible_at=future,
                )
            )
        )
        cancelled = asyncio.run(runtime.cancel(cancel_record.id))
        assert cancelled.status == "cancelled"
        assert cancel_record.id not in runtime._materials

        failure_material = make_material("fail-once", {"name": "fail-once"}, "a")
        failure_record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="a",
                    security=context("a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=failure_material,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(failure_record.id)
        assert failed is not None and failed.status == "failed"
        runtime.register_material_provider("a", Provider(failure_material))
        asyncio.run(runtime.retry(failure_record.id, WorkRetryRequest(), idempotency_key="retry-1"))
        asyncio.run(runtime.run_eligible())
        succeeded = runtime.inspect(failure_record.id)
        assert succeeded is not None and succeeded.status == "succeeded"
        assert succeeded.attempts[-1].capability_id == "compute"
        assert succeeded.attempts[-1].execution_boundary == "local"

        interrupted_material = make_material("interrupted", {"name": "interrupted"}, "z")
        interrupted = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="z",
                    security=context("z"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=interrupted_material,
                )
            )
        )
        assert store.start_attempt(interrupted.id, "compute", None, "local", datetime.now(UTC)) == 1

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), capabilities(execute))
        recovered = runtime.inspect(interrupted.id)
        assert recovered is not None and recovered.status == "failed"
        assert recovered.failure is not None and recovered.failure.code == "interrupted"
