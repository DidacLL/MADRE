"""Kernel-side physical Capability extension boundary."""

from __future__ import annotations

import asyncio
import inspect
from collections.abc import Awaitable, Callable, Mapping
from dataclasses import dataclass
from typing import Protocol

from madre_sdk import (
    ComputationId,
    MaterialType,
    MaterialTypeId,
    PhysicalProperties,
    ResponsibilitySurfaces,
    WorkRequest,
)
from madre_sdk.algebra import Privacy
from madre_sdk.material import MaterialSet


def _require_text(value: str, field: str) -> None:
    if not value or value.isspace():
        raise ValueError(f"{field} must not be blank")


@dataclass(frozen=True, slots=True)
class CapabilityId:
    name: str
    revision: str = "1"

    def __post_init__(self) -> None:
        _require_text(self.name, "CapabilityId.name")
        _require_text(self.revision, "CapabilityId.revision")


@dataclass(frozen=True, slots=True)
class ResourceId:
    name: str

    def __post_init__(self) -> None:
        _require_text(self.name, "ResourceId.name")


@dataclass(frozen=True, slots=True)
class ResourceClaim:
    resource: ResourceId
    units: int = 1

    def __post_init__(self) -> None:
        if not isinstance(self.resource, ResourceId):
            raise TypeError("ResourceClaim.resource requires ResourceId")
        if self.units < 1:
            raise ValueError("ResourceClaim.units must be positive")


@dataclass(frozen=True, slots=True)
class CapabilityInput:
    material_type: MaterialTypeId
    privacy: Privacy

    def __post_init__(self) -> None:
        if not isinstance(self.material_type, MaterialTypeId):
            raise TypeError("CapabilityInput.material_type requires MaterialTypeId")
        if not isinstance(self.privacy, Privacy):
            raise TypeError("CapabilityInput.privacy requires Privacy")


@dataclass(frozen=True, slots=True)
class CapabilityInputs:
    members: tuple[CapabilityInput, ...]

    def __post_init__(self) -> None:
        if not self.members:
            raise ValueError("CapabilityInputs requires at least one input")
        if any(not isinstance(member, CapabilityInput) for member in self.members):
            raise TypeError("CapabilityInputs accepts only CapabilityInput values")
        material_types = tuple(member.material_type for member in self.members)
        if len(material_types) != len(set(material_types)):
            raise ValueError("CapabilityInputs requires one boundary per MaterialType")

    @property
    def material_types(self) -> frozenset[MaterialTypeId]:
        return frozenset(member.material_type for member in self.members)

    def compose(self, materials: MaterialSet) -> MaterialSet:
        by_type = {member.material_type: member for member in self.members}
        actual = tuple(
            by_type.get(material.material_type.identity) for material in materials.materials
        )
        if any(item is None for item in actual):
            raise ValueError("Material type is not accepted by this Capability")
        values = tuple(item.privacy for item in actual if item is not None)
        first, *rest = values
        return materials.compose_with(Privacy.minimum(first, *rest))


@dataclass(frozen=True, slots=True)
class CapabilityDefinition:
    identity: CapabilityId
    computation: ComputationId
    output_type: MaterialType[object]
    inputs: CapabilityInputs
    properties: PhysicalProperties
    resources: tuple[ResourceClaim, ...] = ()
    responsibility: ResponsibilitySurfaces | None = None

    def __post_init__(self) -> None:
        if not isinstance(self.identity, CapabilityId):
            raise TypeError("CapabilityDefinition.identity requires CapabilityId")
        if not isinstance(self.computation, ComputationId):
            raise TypeError("CapabilityDefinition.computation requires ComputationId")
        if not isinstance(self.output_type, MaterialType):
            raise TypeError("CapabilityDefinition.output_type requires MaterialType")
        if not isinstance(self.inputs, CapabilityInputs):
            raise TypeError("CapabilityDefinition.inputs requires CapabilityInputs")
        if not isinstance(self.properties, PhysicalProperties):
            raise TypeError("CapabilityDefinition.properties requires PhysicalProperties")
        resource_ids = tuple(claim.resource for claim in self.resources)
        if len(resource_ids) != len(set(resource_ids)):
            raise ValueError("CapabilityDefinition cannot repeat a ResourceClaim")


@dataclass(frozen=True, slots=True)
class CapabilityInvocation:
    computation: ComputationId
    input_types: tuple[MaterialTypeId, ...]
    payloads: tuple[object, ...]
    timeout_seconds: float

    def __post_init__(self) -> None:
        if not self.payloads:
            raise ValueError("CapabilityInvocation requires at least one payload")
        if len(self.input_types) != len(self.payloads):
            raise ValueError("CapabilityInvocation input types and payloads must align")
        if self.timeout_seconds <= 0:
            raise ValueError("CapabilityInvocation timeout must be positive")

    @classmethod
    def from_request(cls, request: WorkRequest[object]) -> CapabilityInvocation:
        return cls(
            computation=request.computation.identity,
            input_types=tuple(
                material.material_type.identity for material in request.materials.materials
            ),
            payloads=tuple(material.payload for material in request.materials.materials),
            timeout_seconds=request.timing.timeout.total_seconds(),
        )


class CapabilityError(RuntimeError):
    def __init__(self, code: str, message: str = "physical Capability failed") -> None:
        _require_text(code, "CapabilityError.code")
        self.code = code
        super().__init__(message)


class CapabilityUnavailable(RuntimeError):
    pass


class CapabilityAdapter(Protocol):
    @property
    def definition(self) -> CapabilityDefinition: ...

    def is_available(self) -> bool: ...

    async def invoke(self, request: CapabilityInvocation) -> object: ...


class CapabilitySelection(Protocol):
    def select(
        self,
        request: WorkRequest[object],
        capabilities: tuple[CapabilityAdapter, ...],
    ) -> CapabilityAdapter: ...


class DeterministicCapabilitySelection:
    def select(
        self,
        request: WorkRequest[object],
        capabilities: tuple[CapabilityAdapter, ...],
    ) -> CapabilityAdapter:
        if not capabilities:
            raise CapabilityUnavailable("no installed Capability can currently perform this work")
        return min(
            capabilities,
            key=lambda adapter: (
                *(
                    preference.rank(adapter.definition.properties)
                    for preference in request.preferences
                ),
                adapter.definition.identity.name,
                adapter.definition.identity.revision,
            ),
        )


class CapabilityRegistry:
    def __init__(self, selection: CapabilitySelection | None = None) -> None:
        self._adapters: dict[CapabilityId, CapabilityAdapter] = {}
        self._selection = selection or DeterministicCapabilitySelection()

    def register(self, adapter: CapabilityAdapter) -> None:
        identity = adapter.definition.identity
        if identity in self._adapters:
            raise ValueError(f"Capability is already registered: {identity.name}")
        self._adapters[identity] = adapter

    def remove(self, identity: CapabilityId) -> None:
        self._adapters.pop(identity, None)

    def select(self, request: WorkRequest[object]) -> CapabilityAdapter:
        matching = tuple(
            adapter for adapter in self._adapters.values() if self._can_use(adapter, request)
        )
        return self._selection.select(request, matching)

    @staticmethod
    def _can_use(adapter: CapabilityAdapter, request: WorkRequest[object]) -> bool:
        definition = adapter.definition
        if definition.computation != request.computation.identity:
            return False
        if definition.output_type.identity != request.computation.output_type.identity:
            return False
        request_types = {
            material.material_type.identity for material in request.materials.materials
        }
        if not request_types.issubset(definition.inputs.material_types):
            return False
        try:
            definition.inputs.compose(request.materials)
        except ValueError:
            return False
        if not all(
            requirement.matches(definition.properties) for requirement in request.requirements
        ):
            return False
        return adapter.is_available()


class ResourceCoordinator(Protocol):
    def reserve(self, claims: tuple[ResourceClaim, ...]) -> ResourceReservation: ...


class ResourceReservation:
    def __init__(
        self,
        claims: tuple[ResourceClaim, ...],
        capacities: Mapping[ResourceId, int],
        semaphores: dict[ResourceId, asyncio.Semaphore],
    ) -> None:
        self._claims = tuple(sorted(claims, key=lambda claim: claim.resource.name))
        self._capacities = capacities
        self._semaphores = semaphores
        self._acquired: list[asyncio.Semaphore] = []

    async def __aenter__(self) -> None:
        for claim in self._claims:
            capacity = self._capacities.get(claim.resource, 1)
            if claim.units > capacity:
                self._release()
                raise CapabilityUnavailable(
                    f"resource capacity is insufficient: {claim.resource.name}"
                )
            semaphore = self._semaphores.setdefault(
                claim.resource,
                asyncio.Semaphore(capacity),
            )
            for _ in range(claim.units):
                await semaphore.acquire()
                self._acquired.append(semaphore)

    async def __aexit__(self, exc_type: object, exc: object, traceback: object) -> None:
        self._release()

    def _release(self) -> None:
        while self._acquired:
            self._acquired.pop().release()


class CapacityResourceCoordinator:
    def __init__(self, capacities: Mapping[ResourceId, int] | None = None) -> None:
        supplied = dict(capacities or {})
        if any(capacity < 1 for capacity in supplied.values()):
            raise ValueError("Resource capacities must be positive")
        self._capacities = supplied
        self._semaphores: dict[ResourceId, asyncio.Semaphore] = {}

    def reserve(self, claims: tuple[ResourceClaim, ...]) -> ResourceReservation:
        return ResourceReservation(claims, self._capacities, self._semaphores)


class FunctionCapability:
    """Deterministic in-process fixture or installed mechanism implementation."""

    def __init__(
        self,
        definition: CapabilityDefinition,
        function: Callable[[tuple[object, ...]], object | Awaitable[object]],
        *,
        availability: Callable[[], bool] | None = None,
    ) -> None:
        self._definition = definition
        self._function = function
        self._availability = availability or (lambda: True)

    @property
    def definition(self) -> CapabilityDefinition:
        return self._definition

    def is_available(self) -> bool:
        return self._availability()

    async def invoke(self, request: CapabilityInvocation) -> object:
        try:
            async with asyncio.timeout(request.timeout_seconds):
                output = self._function(request.payloads)
                if inspect.isawaitable(output):
                    return await output
                return output
        except TimeoutError as exc:
            raise CapabilityError("timeout") from exc
