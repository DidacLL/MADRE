"""Private behavior of the shipped Module, using only the ordinary public SDK."""

from __future__ import annotations

from typing import Protocol

from pydantic import JsonValue

from madre_sdk.execution import CapabilityQuery, ExecutionRequest
from madre_sdk.material import Material, MaterialSpecification
from madre_sdk.security import ScopeIdentity
from madre_sdk.services import ExecutionService


class ResultInterpreter(Protocol):
    """Module-owned meaning applied after physical output returns as Material."""

    def interpret(
        self,
        source: Material[JsonValue],
        result: Material[JsonValue],
    ) -> Material[JsonValue]: ...


class InteractionBehavior:
    """One ordinary Module behavior; occupying the CORE role changes nothing here."""

    def __init__(
        self,
        module: ScopeIdentity,
        capability: CapabilityQuery,
        physical_output: MaterialSpecification,
        interpreter: ResultInterpreter,
    ) -> None:
        self._module = module
        self._capability = capability
        self._physical_output = physical_output
        self._interpreter = interpreter

    async def receive(
        self,
        material: Material[JsonValue],
        services: ExecutionService,
    ) -> Material[JsonValue]:
        result = await services.execute(
            ExecutionRequest(
                requester=self._module,
                material=material,
                capability=self._capability,
                output=self._physical_output,
            )
        )
        return self._interpreter.interpret(material, result)
