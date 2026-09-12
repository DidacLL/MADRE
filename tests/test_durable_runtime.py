from __future__ import annotations

import asyncio
from datetime import UTC, datetime, timedelta
from pathlib import Path

from madre import CapabilityError, CapabilityRegistry, Kernel
from madre.storage import WorkQueueStore, open_database
from madre.work import DeliveryStatus, WorkStatus
from madre_sdk import (
    ExecutionLocation,
    Material,
    MaterialId,
    MaterialSet,
    PhysicalResult,
    PhysicalRetryPolicy,
    Privacy,
    Sensitivity,
    WorkRequest,
)
from tests.kernel_fixtures import build_capability, build_contracts


def _request(*, sensitivity: Sensitivity, attempts: int = 1) -> WorkRequest[str]:
    module, prompt_type, _, computation = build_contracts()
    return WorkRequest(
        module=module,
        materials=MaterialSet.of(
            Material(MaterialId(module, "durable-input"), prompt_type, "queued", sensitivity)
        ),
        computation=computation,
        retry=PhysicalRetryPolicy(attempts, timedelta(0)),
    )


def _private_registry(output: object = "durable-output") -> CapabilityRegistry:
    module, prompt_type, result_type, computation = build_contracts()
    registry = CapabilityRegistry()
    registry.register(
        build_capability(
            identity="durable-private",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.P5,
            location=ExecutionLocation.OWNER_DEVICE,
            invoke=lambda _: output,
        )
    )
    return registry


def test_opaque_input_and_pending_physical_result_survive_restart(tmp_path: Path) -> None:
    request = _request(sensitivity=Sensitivity.S5)

    with open_database(tmp_path) as connection:
        first_store = WorkQueueStore(connection)
        first_kernel = Kernel(_private_registry(), first_store)
        queued = asyncio.run(first_kernel.enqueue(request, idempotency_key="durable"))
        assert first_store.load_request(queued.identity) == request

    with open_database(tmp_path) as connection:
        second_store = WorkQueueStore(connection)
        second_kernel = Kernel(_private_registry(), second_store)
        assert asyncio.run(second_kernel.run_eligible()) == 1
        completed = second_kernel.inspect(queued.identity)
        assert completed is not None
        assert completed.status is WorkStatus.SUCCEEDED
        assert completed.delivery is DeliveryStatus.PENDING
        assert second_store.load_request(queued.identity) is None

    with open_database(tmp_path) as connection:
        third_kernel = Kernel(_private_registry(), WorkQueueStore(connection))
        result = third_kernel.consume_result(queued.identity)
        assert isinstance(result, PhysicalResult)
        assert result.output == "durable-output"
        delivered = third_kernel.inspect(queued.identity)
        assert delivered is not None
        assert delivered.delivery is DeliveryStatus.DELIVERED


def test_current_unavailability_waits_without_recording_an_attempt(tmp_path: Path) -> None:
    module, prompt_type, result_type, computation = build_contracts()
    now = [datetime(2026, 9, 12, 12, tzinfo=UTC)]
    registry = CapabilityRegistry()
    registry.register(
        build_capability(
            identity="external-only",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.UNKNOWN,
            location=ExecutionLocation.EXTERNAL,
            invoke=lambda _: "unused",
        )
    )

    with open_database(tmp_path) as connection:
        kernel = Kernel(registry, WorkQueueStore(connection), clock=lambda: now[0])
        queued = asyncio.run(kernel.enqueue(_request(sensitivity=Sensitivity.S5)))
        assert asyncio.run(kernel.run_eligible()) == 0
        waiting = kernel.inspect(queued.identity)
        assert waiting is not None
        assert waiting.status is WorkStatus.QUEUED
        assert waiting.attempts == ()

        registry.register(
            build_capability(
                identity="owner-private",
                computation=computation,
                prompt_type=prompt_type,
                result_type=result_type,
                privacy=Privacy.P5,
                location=ExecutionLocation.OWNER_DEVICE,
                invoke=lambda _: "available",
            )
        )
        now[0] += timedelta(seconds=2)
        assert asyncio.run(kernel.run_eligible()) == 1
        result = kernel.consume_result(queued.identity)
        assert result.output == "available"


def test_physical_failure_retries_according_to_request_policy(tmp_path: Path) -> None:
    calls = 0

    def flaky(_: tuple[object, ...]) -> object:
        nonlocal calls
        calls += 1
        if calls == 1:
            raise CapabilityError("fixture_interruption")
        return "recovered"

    module, prompt_type, result_type, computation = build_contracts()
    registry = CapabilityRegistry()
    registry.register(
        build_capability(
            identity="flaky-physical",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.P5,
            location=ExecutionLocation.OWNER_DEVICE,
            invoke=flaky,
        )
    )

    with open_database(tmp_path) as connection:
        kernel = Kernel(registry, WorkQueueStore(connection))
        queued = asyncio.run(kernel.enqueue(_request(sensitivity=Sensitivity.S5, attempts=2)))
        assert asyncio.run(kernel.run_eligible()) == 2
        record = kernel.inspect(queued.identity)
        assert record is not None
        assert record.status is WorkStatus.SUCCEEDED
        assert [attempt.status.value for attempt in record.attempts] == ["failed", "succeeded"]
        assert kernel.consume_result(queued.identity).output == "recovered"


def test_sqlite_contains_only_work_lifecycle_tables(tmp_path: Path) -> None:
    with open_database(tmp_path) as connection:
        store = WorkQueueStore(connection)
        assert store.table_names() == (
            "runtime_attempt",
            "runtime_scheduler_state",
            "runtime_work",
        )
