from __future__ import annotations

import asyncio

import pytest
from pydantic import JsonValue

from madre import CapabilityRegistry, FunctionCapability, Kernel
from madre_core import InteractionBehavior
from madre_sdk import (
    CapabilityDefinition,
    CapabilityProperties,
    CapabilityQuery,
    ExecutionBoundary,
    ExecutionRequest,
    Material,
    MaterialContract,
    MaterialSpecification,
    ModuleDefinition,
    ModuleRuntime,
    Privacy,
    ScopeIdentity,
    SecurityMismatch,
    SecurityScope,
    SecuritySurface,
    Sensitivity,
)


def identity(owner: str, name: str) -> ScopeIdentity:
    return ScopeIdentity(owner=owner, name=name)


SPECIALIZATION = identity("madre.execution", "language-inference")
MODALITY = identity("madre.execution", "structured-text")


def contract(owner: str, name: str) -> MaterialContract:
    return MaterialContract(
        identity=identity(owner, name),
        media_type="application/json",
    )


def material(
    owner: str,
    name: str,
    payload: JsonValue,
    sensitivity: Sensitivity,
) -> Material[JsonValue]:
    material_identity = identity(owner, name)
    return Material[JsonValue](
        identity=material_identity,
        contract=contract(owner, "structured-content"),
        payload=payload,
        security=SecurityScope(
            identity=material_identity,
            sensitivity=sensitivity,
        ),
    )


def capability(
    name: str,
    privacy: Privacy,
    boundary: ExecutionBoundary,
) -> CapabilityDefinition:
    capability_identity = identity("physical", name)
    return CapabilityDefinition(
        identity=capability_identity,
        properties=CapabilityProperties(
            specialization=SPECIALIZATION,
            modality=MODALITY,
            boundary=boundary,
        ),
        security=SecuritySurface.compose(
            SecurityScope(
                identity=capability_identity,
                privacy=privacy,
            )
        ),
    )


class InterpretPhysicalResult:
    def __init__(self, final_identity: ScopeIdentity) -> None:
        self.final_identity = final_identity
        self.seen_physical_identity: ScopeIdentity | None = None

    def interpret(
        self,
        source: Material[JsonValue],
        result: Material[JsonValue],
    ) -> Material[JsonValue]:
        self.seen_physical_identity = result.identity
        return Material[JsonValue](
            identity=self.final_identity,
            contract=source.contract,
            payload={
                "module_interpretation": result.payload,
                "source": source.identity.name,
            },
            security=SecurityScope(
                identity=self.final_identity,
                sensitivity=Sensitivity.S2,
            ),
        )


def test_ordinary_module_owns_interpretation_after_capability_execution() -> None:
    module_identity = identity("module", "module")
    module_definition = ModuleDefinition(
        identity=module_identity,
        description="Ordinary first-party Module",
        managed_scopes=(
            SecurityScope(
                identity=identity("module", "managed-context"),
                sensitivity=Sensitivity.S3,
            ),
        ),
    )
    input_material = material(
        "module",
        "input",
        {"prompt": "bounded input"},
        Sensitivity.S3,
    )
    raw_identity = identity("module", "physical-output")
    final_identity = identity("module", "interpreted-output")
    raw_output = MaterialSpecification(
        identity=raw_identity,
        contract=contract("module", "physical-response"),
        security=SecurityScope(
            identity=raw_identity,
            sensitivity=Sensitivity.S3,
        ),
    )
    mechanism = capability(
        "deterministic",
        Privacy.MODULE_PRIVATE,
        ExecutionBoundary.LOCAL,
    )
    calls: list[JsonValue] = []
    registry = CapabilityRegistry()
    registry.register(
        FunctionCapability(
            mechanism,
            lambda payload: (
                calls.append(payload) or {"text": "physical output", "operation": "not executable"}
            ),
        )
    )
    kernel = Kernel(registry)
    interpreter = InterpretPhysicalResult(final_identity)
    behavior = InteractionBehavior(
        module=module_definition,
        capability=CapabilityQuery(
            specialization=SPECIALIZATION,
            modality=MODALITY,
        ),
        physical_output=raw_output,
        interpreter=interpreter,
    )
    runtime = ModuleRuntime(
        definition=module_definition,
        behavior=behavior,
        services=kernel,
    )

    result = asyncio.run(runtime.receive(input_material))

    assert calls == [input_material.payload]
    assert interpreter.seen_physical_identity == raw_identity
    assert result.identity == final_identity
    assert result.payload == {
        "module_interpretation": {
            "text": "physical output",
            "operation": "not executable",
        },
        "source": "input",
    }
    assert result.security.sensitivity is Sensitivity.S2


def test_incompatible_capability_addition_ends_request_without_execution() -> None:
    calls = 0

    def should_not_execute(payload: JsonValue) -> JsonValue:
        nonlocal calls
        calls += 1
        return payload

    registry = CapabilityRegistry()
    registry.register(
        FunctionCapability(
            capability("public-remote", Privacy.PUBLIC, ExecutionBoundary.REMOTE),
            should_not_execute,
        )
    )
    kernel = Kernel(registry)
    source = material("module", "secret", {"secret": True}, Sensitivity.S5)
    output_identity = identity("module", "uncreated-output")

    with pytest.raises(SecurityMismatch, match="S5 exceeds PUBLIC"):
        asyncio.run(
            kernel.execute(
                ExecutionRequest(
                    requester=identity("module", "module"),
                    material=source,
                    capability=CapabilityQuery(
                        specialization=SPECIALIZATION,
                        modality=MODALITY,
                    ),
                    output=MaterialSpecification(
                        identity=output_identity,
                        contract=contract("module", "physical-response"),
                        security=SecurityScope(
                            identity=output_identity,
                            sensitivity=Sensitivity.S5,
                        ),
                    ),
                )
            )
        )

    assert calls == 0


def test_execution_boundary_does_not_infer_privacy() -> None:
    local = capability("local", Privacy.UNKNOWN, ExecutionBoundary.LOCAL)
    remote = capability("remote", Privacy.UNKNOWN, ExecutionBoundary.REMOTE)

    assert local.security.privacy is Privacy.UNKNOWN
    assert remote.security.privacy is Privacy.UNKNOWN
