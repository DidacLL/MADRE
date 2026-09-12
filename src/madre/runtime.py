"""Kernel routing of typed physical work."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import cast

from madre.capabilities import (
    CapabilityError,
    CapabilityInvocation,
    CapabilityRegistry,
    CapacityResourceCoordinator,
    ResourceCoordinator,
)
from madre_sdk import PhysicalResult, WorkRequest


class Kernel:
    def __init__(
        self,
        capabilities: CapabilityRegistry,
        resources: ResourceCoordinator | None = None,
    ) -> None:
        self.capabilities = capabilities
        self.resources = resources or CapacityResourceCoordinator()

    async def submit[OutputT](self, request: WorkRequest[OutputT]) -> PhysicalResult[OutputT]:
        physical_request = cast(WorkRequest[object], request)
        adapter = self.capabilities.select(physical_request)
        invocation = CapabilityInvocation.from_request(physical_request)
        started_at = datetime.now(UTC)
        try:
            async with self.resources.reserve(adapter.definition.resources):
                output = await adapter.invoke(invocation)
        except CapabilityError:
            raise
        except Exception as exc:
            raise CapabilityError("internal_error") from exc
        completed_at = datetime.now(UTC)
        return PhysicalResult(
            computation=request.computation.identity,
            output_type=request.computation.output_type,
            output=cast(OutputT, output),
            started_at=started_at,
            completed_at=completed_at,
            attempt=1,
        )
