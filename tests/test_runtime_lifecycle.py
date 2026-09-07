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
from madre.security import BoundaryRequirements, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def sec(
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
        scopes=scopes or {"*"},
        origin=origin,
    )


def manifest(module_id: str, trust: SecurityLevel = SecurityLevel.LEVEL_5) -> ModuleManifest:
    return ModuleManifest(
        module_id=module_id,
        version="1",
        description="runtime test module",
        security=sec(module_id, trust=trust),
        inbound_requirements=BoundaryRequirements(
            allowed_execution_boundaries=frozenset({"local"})
        ),
    )


def make_material(reference: str, payload, origin: str) -> ImmediateMaterial:
    digest = content_digest(payload)
    return ImmediateMaterial(
        reference=reference,
        payload=payload,
        envelope=sec(digest, origin=origin),
    )


def capabilities(function) -> CapabilityRegistry:
    descriptor = CapabilityDescriptor(
        id="compute",
        kind="compute",
        modality="json",
        execution_boundary="local",
        heavyweight=True,
        requirements=BoundaryRequirements(
            risk=SecurityLevel.LEVEL_1,
            allowed_scopes=frozenset({"*"}),
            allowed_execution_boundaries=frozenset({"local"}),
        ),
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
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.a"))
        runtime = WorkRuntime(store, capabilities(lambda value: {"seen": value}), registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=delayed,
                )
            )
        )
        assert runtime.inspect(record.id).status == "accepted"  # type: ignore[union-attr]

    provider = Provider(material)
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        runtime = WorkRuntime(store, capabilities(lambda value: {"seen": value}), registry)
        runtime.register_material_provider("module.a", provider)
        assert asyncio.run(runtime.run_eligible()) == 1
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert provider.requests == [material.reference]
        assert runtime.consume_result(record.id) == {"seen": payload}


def test_delayed_execution_uses_current_requester_security_not_stale_submission_facts(
    tmp_path: Path,
) -> None:
    data_dir = tmp_path / "runtime"
    payload = {"private": "high-trust-material"}
    material = make_material("module.a/material/43", payload, "module.a")
    delayed = DelayedMaterial(
        reference=material.reference,
        digest=content_digest(payload),
        envelope=material.envelope,
    )
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.a", trust=SecurityLevel.LEVEL_5))
        runtime = WorkRuntime(store, capabilities(lambda value: value), registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=delayed,
                )
            )
        )
        registry.register(manifest("module.a", trust=SecurityLevel.LEVEL_2))
        runtime.register_material_provider("module.a", Provider(material))
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(record.id)
        assert failed is not None and failed.status == "failed"
        assert failed.failure is not None and failed.failure.code == "security_denied"
        deficits = connection.execute(
            "SELECT deficits_json FROM security_decision WHERE crossing_id=?",
            (record.id,),
        ).fetchone()[0]
        assert "material_trust_provenance" in deficits


def test_unconsumed_result_is_truthfully_marked_lost_after_restart(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        registry.register(manifest("module.a"))
        runtime = WorkRuntime(store, capabilities(lambda value: {"done": value}), registry)
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
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
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        runtime = WorkRuntime(store, capabilities(lambda value: value), registry)
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
        registry = InteroperabilityRegistry(store)
        for originator in ("a", "b", "z"):
            registry.register(manifest(originator))
        runtime = WorkRuntime(store, capabilities(execute), registry)
        for originator, name, priority in [
            ("a", "a-low", 0),
            ("a", "a-high", 50),
            ("b", "b", 0),
        ]:
            asyncio.run(
                runtime.submit(
                    WorkSubmission(
                        originator=originator,
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
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=failure_material,
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        failed = runtime.inspect(failure_record.id)
        assert failed is not None and failed.status == "failed"
        runtime.register_material_provider("a", Provider(failure_material))
        asyncio.run(
            runtime.retry(
                failure_record.id,
                WorkRetryRequest(),
                idempotency_key="retry-1",
            )
        )
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
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=interrupted_material,
                )
            )
        )
        assert (
            store.start_attempt(
                interrupted.id,
                "compute",
                None,
                "local",
                datetime.now(UTC),
            )
            == 1
        )

    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        runtime = WorkRuntime(store, capabilities(execute), registry)
        recovered = runtime.inspect(interrupted.id)
        assert recovered is not None and recovered.status == "failed"
        assert recovered.failure is not None and recovered.failure.code == "interrupted"
