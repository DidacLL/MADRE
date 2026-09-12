from __future__ import annotations

import asyncio
from collections.abc import Callable

import pytest

from madre import (
    CapabilityDefinition,
    CapabilityId,
    CapabilityInput,
    CapabilityInputs,
    CapabilityRegistry,
    CapabilityUnavailable,
    FunctionCapability,
    Kernel,
)
from madre_sdk import (
    ComputationContract,
    ComputationId,
    ExecutionLocation,
    LatencyClass,
    LatencyPreference,
    Material,
    MaterialId,
    MaterialSet,
    MaterialType,
    MaterialTypeId,
    ModuleId,
    PhysicalProperties,
    Privacy,
    Sensitivity,
    WorkRequest,
)


class _FixtureModule:
    def __init__(
        self,
        identity: ModuleId,
        prompt_type: MaterialType[str],
        result_type: MaterialType[str],
        computation: ComputationContract[str],
        kernel: Kernel,
    ) -> None:
        self.identity = identity
        self.prompt_type = prompt_type
        self.result_type = result_type
        self.computation = computation
        self.kernel = kernel

    async def run(self, prompt: Material[str]) -> Material[str]:
        first = await self.kernel.submit(
            WorkRequest(
                module=self.identity,
                materials=MaterialSet.of(prompt),
                computation=self.computation,
            )
        )
        interpreted = Material(
            MaterialId(self.identity, "interpreted-first-result"),
            self.result_type,
            first.output,
            Sensitivity.S3,
        )
        if interpreted.payload != "continue":
            return interpreted

        second = await self.kernel.submit(
            WorkRequest(
                module=self.identity,
                materials=MaterialSet.of(interpreted),
                computation=self.computation,
            )
        )
        return Material(
            MaterialId(self.identity, "final-result"),
            self.result_type,
            second.output,
            Sensitivity.S2,
        )


def _capability(
    *,
    identity: str,
    computation: ComputationContract[str],
    prompt_type: MaterialType[str],
    result_type: MaterialType[str],
    privacy: Privacy,
    location: ExecutionLocation,
    invoke: Callable[[tuple[object, ...]], object],
) -> FunctionCapability:
    return FunctionCapability(
        CapabilityDefinition(
            identity=CapabilityId(identity),
            computation=computation.identity,
            output_type=result_type,
            inputs=CapabilityInputs(
                (
                    CapabilityInput(prompt_type.identity, privacy),
                    CapabilityInput(result_type.identity, privacy),
                )
            ),
            properties=PhysicalProperties(location, LatencyClass.INTERACTIVE),
        ),
        invoke,
    )


def _contracts() -> tuple[
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


def test_module_owns_interpretation_and_second_physical_request() -> None:
    module, prompt_type, result_type, computation = _contracts()
    calls: list[tuple[object, ...]] = []

    def mechanism(payloads: tuple[object, ...]) -> object:
        calls.append(payloads)
        return "continue" if len(calls) == 1 else "complete"

    registry = CapabilityRegistry()
    registry.register(
        _capability(
            identity="fixture-mechanism",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.P5,
            location=ExecutionLocation.OWNER_DEVICE,
            invoke=mechanism,
        )
    )
    fixture_module = _FixtureModule(module, prompt_type, result_type, computation, Kernel(registry))
    source = Material(
        MaterialId(module, "source"),
        prompt_type,
        "begin",
        Sensitivity.S5,
    )

    result = asyncio.run(fixture_module.run(source))

    assert calls == [("begin",), ("continue",)]
    assert result.payload == "complete"
    assert result.identity == MaterialId(module, "final-result")
    assert result.sensitivity is Sensitivity.S2


def test_noncomposing_capability_is_absent_and_never_invoked() -> None:
    module, prompt_type, result_type, computation = _contracts()
    invoked: list[str] = []
    registry = CapabilityRegistry()
    registry.register(
        _capability(
            identity="third-party",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.UNKNOWN,
            location=ExecutionLocation.EXTERNAL,
            invoke=lambda _: invoked.append("third-party"),
        )
    )
    registry.register(
        _capability(
            identity="owner-private",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.P5,
            location=ExecutionLocation.OWNER_DEVICE,
            invoke=lambda _: "private-result",
        )
    )
    request = WorkRequest(
        module=module,
        materials=MaterialSet.of(
            Material(MaterialId(module, "secret"), prompt_type, "secret", Sensitivity.S5)
        ),
        computation=computation,
        preferences=(LatencyPreference((LatencyClass.INTERACTIVE, LatencyClass.STANDARD)),),
    )

    result = asyncio.run(Kernel(registry).submit(request))

    assert result.output == "private-result"
    assert invoked == []
    assert not hasattr(registry, "rejections")


def test_no_currently_usable_capability_is_ordinary_unavailability() -> None:
    module, prompt_type, result_type, computation = _contracts()
    registry = CapabilityRegistry()
    registry.register(
        _capability(
            identity="external",
            computation=computation,
            prompt_type=prompt_type,
            result_type=result_type,
            privacy=Privacy.UNKNOWN,
            location=ExecutionLocation.EXTERNAL,
            invoke=lambda _: "unused",
        )
    )
    request = WorkRequest(
        module=module,
        materials=MaterialSet.of(
            Material(MaterialId(module, "secret"), prompt_type, "secret", Sensitivity.S5)
        ),
        computation=computation,
    )

    with pytest.raises(CapabilityUnavailable):
        asyncio.run(Kernel(registry).submit(request))
