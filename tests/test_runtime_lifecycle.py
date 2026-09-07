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
from madre.runtime import (
    MaterialProvider,
    ResultLost,
    WorkRuntime,
    content_digest,
)
from madre.security import BoundaryRequirements, SecurityEnvelope, SecurityLevel
from madre.storage import PlatformStore, open_database


def sec(subject: str, scopes: set[str] | None = None) -> SecurityEnvelope:
    return SecurityEnvelope.issue(
        subject=subject,
        sensitivity=SecurityLevel.LEVEL_2,
        trust=SecurityLevel.LEVEL_5,
        risk=SecurityLevel.LEVEL_1,
        scopes=scopes or {"*"},
        origin="test",
    )


def make_material(reference: str, payload) -> ImmediateMaterial:
    digest = content_digest(payload)
    return ImmediateMaterial(reference=reference, payload=payload, envelope=sec(digest))


def registry(function) -> CapabilityRegistry:
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
        security=sec("capability:compute"),
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
    material = make_material("module.a/material/42", payload)
    delayed = DelayedMaterial(
        reference=material.reference,
        digest=content_digest(payload),
        envelope=material.envelope,
    )
    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry(lambda value: {"seen": value}))
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    requester_envelope=sec("module.a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=delayed,
                )
            )
        )
        assert runtime.inspect(record.id).status == "accepted"  # type: ignore[union-attr]

    provider = Provider(material)
    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry(lambda value: {"seen": value}))
        runtime.register_material_provider("module.a", provider)
        assert asyncio.run(runtime.run_eligible()) == 1
        completed = runtime.inspect(record.id)
        assert completed is not None and completed.status == "succeeded"
        assert provider.requests == [material.reference]
        assert runtime.consume_result(record.id) == {"seen": payload}


def test_unconsumed_result_is_truthfully_marked_lost_after_restart(tmp_path: Path) -> None:
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry(lambda value: {"done": value}))
        record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="module.a",
                    requester_envelope=sec("module.a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=make_material("m/1", {"x": 1}),
                )
            )
        )
        asyncio.run(runtime.run_eligible())
        produced = runtime.inspect(record.id)
        assert produced is not None and produced.result is not None
        assert produced.result.delivery_status == "awaiting_consumption"

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry(lambda value: value))
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
            raise CapabilityError("provider_failure", "deterministic failure")
        return {"name": name}

    data_dir = tmp_path / "runtime"
    materials: dict[str, ImmediateMaterial] = {}
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        runtime = WorkRuntime(store, registry(execute))
        records = []
        for originator, name, priority in [
            ("a", "a-low", 0),
            ("a", "a-high", 50),
            ("b", "b", 0),
        ]:
            material = make_material(name, {"name": name})
            materials[name] = material
            records.append(
                asyncio.run(
                    runtime.submit(
                        WorkSubmission(
                            originator=originator,
                            requester_envelope=sec(originator),
                            capability=CapabilityRequest(kind="compute", modality="json"),
                            material=material,
                            priority=priority,
                        )
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
                    requester_envelope=sec("a"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=make_material("cancel", {"name": "cancel"}),
                    eligible_at=future,
                )
            )
        )
        cancelled = asyncio.run(runtime.cancel(cancel_record.id))
        assert cancelled.status == "cancelled"

        failure_material = make_material("fail-once", {"name": "fail-once"})
        failure_record = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="a",
                    requester_envelope=sec("a"),
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
        assert runtime.inspect(failure_record.id).status == "succeeded"  # type: ignore[union-attr]

        interrupted_material = make_material("interrupted", {"name": "interrupted"})
        interrupted = asyncio.run(
            runtime.submit(
                WorkSubmission(
                    originator="z",
                    requester_envelope=sec("z"),
                    capability=CapabilityRequest(kind="compute", modality="json"),
                    material=interrupted_material,
                )
            )
        )
        assert store.start_attempt(interrupted.id, "compute", datetime.now(UTC)) == 1

    with open_database(data_dir) as connection:
        runtime = WorkRuntime(PlatformStore(connection), registry(execute))
        recovered = runtime.inspect(interrupted.id)
        assert recovered is not None and recovered.status == "failed"
        assert recovered.failure is not None and recovered.failure.code == "interrupted"
