from __future__ import annotations

import asyncio
import inspect
from collections.abc import Awaitable, Callable

from madre import (
    CapabilityDefinition,
    CapabilityId,
    CapabilityInput,
    CapabilityInputs,
    ResourceClaim,
)
from madre.capabilities import CapabilityInvocation
from madre_sdk import (
    ComputationContract,
    ComputationId,
    ExecutionLocation,
    LatencyClass,
    MaterialType,
    MaterialTypeId,
    ModuleId,
    PhysicalProperties,
    Privacy,
)


class FixtureCapability:
    def __init__(
        self,
        definition: CapabilityDefinition,
        function: Callable[[tuple[object, ...]], object | Awaitable[object]],
    ) -> None:
        self._definition = definition
        self._function = function

    @property
    def definition(self) -> CapabilityDefinition:
        return self._definition

    def is_available(self) -> bool:
        return True

    async def invoke(self, request: CapabilityInvocation) -> object:
        async with asyncio.timeout(request.timeout_seconds):
            output = self._function(request.payloads)
            if inspect.isawaitable(output):
                return await output
            return output


def build_capability(
    *,
    identity: str,
    computation: ComputationContract[str],
    prompt_type: MaterialType[str],
    result_type: MaterialType[str],
    privacy: Privacy,
    location: ExecutionLocation,
    invoke: Callable[[tuple[object, ...]], object],
    resources: tuple[ResourceClaim, ...] = (),
) -> FixtureCapability:
    return FixtureCapability(
        CapabilityDefinition(
            identity=CapabilityId(identity),
            computation=computation.identity,
            output_type=result_type.identity,
            inputs=CapabilityInputs(
                (
                    CapabilityInput(prompt_type.identity, privacy),
                    CapabilityInput(result_type.identity, privacy),
                )
            ),
            properties=PhysicalProperties(location, LatencyClass.INTERACTIVE),
            resources=resources,
        ),
        invoke,
    )


def build_contracts() -> tuple[
    ModuleId,
    MaterialType[str],
    MaterialType[str],
    ComputationContract[str],
]:
    module = ModuleId("fixture-module")
    prompt_type = MaterialType[str](MaterialTypeId(module, "prompt"), "text/plain")
    result_type = MaterialType[str](MaterialTypeId(module, "result"), "text/plain")
    computation = ComputationContract[str](
        ComputationId("madre.fixture", "deterministic-text"),
        frozenset((prompt_type.identity, result_type.identity)),
        result_type,
    )
    return module, prompt_type, result_type, computation
