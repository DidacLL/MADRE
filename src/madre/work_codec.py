"""Versioned queue codecs for opaque physical work snapshots."""

from __future__ import annotations

from datetime import datetime, timedelta

from pydantic import BaseModel, ConfigDict, JsonValue, TypeAdapter

from madre_sdk import (
    ComputationContract,
    ComputationId,
    ExecutionLocation,
    LatencyClass,
    LatencyPreference,
    LatencyRequirement,
    LocationPreference,
    LocationRequirement,
    Material,
    MaterialId,
    MaterialSet,
    MaterialType,
    MaterialTypeId,
    ModuleId,
    PhysicalResult,
    PhysicalRetryPolicy,
    Priority,
    Sensitivity,
    WorkRequest,
    WorkTiming,
)


class _WireModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class _ModuleIdDto(_WireModel):
    name: str
    revision: str


class _MaterialTypeIdDto(_WireModel):
    module: _ModuleIdDto
    name: str
    revision: str


class _MaterialTypeDto(_WireModel):
    identity: _MaterialTypeIdDto
    media_type: str


class _MaterialDto(_WireModel):
    name: str
    revision: str
    material_type: _MaterialTypeDto
    payload: JsonValue
    sensitivity: str


class _ComputationDto(_WireModel):
    namespace: str
    name: str
    revision: str
    accepted_inputs: tuple[_MaterialTypeIdDto, ...]
    output_type: _MaterialTypeDto


class _PhysicalRequestDto(_WireModel):
    location_requirements: tuple[tuple[str, ...], ...]
    latency_requirements: tuple[str, ...]
    location_preferences: tuple[tuple[str, ...], ...]
    latency_preferences: tuple[tuple[str, ...], ...]


class _WorkRequestDto(_WireModel):
    schema_version: int
    module: _ModuleIdDto
    materials: tuple[_MaterialDto, ...]
    computation: _ComputationDto
    physical: _PhysicalRequestDto
    not_before: datetime | None
    timeout_seconds: float
    priority: int
    maximum_attempts: int
    initial_backoff_seconds: float


class _PhysicalResultDto(_WireModel):
    schema_version: int
    computation_namespace: str
    computation_name: str
    computation_revision: str
    output_type: _MaterialTypeDto
    output: JsonValue
    started_at: datetime
    completed_at: datetime
    attempt: int


def _module_to_dto(identity: ModuleId) -> _ModuleIdDto:
    return _ModuleIdDto(name=identity.name, revision=identity.revision)


def _module_from_dto(dto: _ModuleIdDto) -> ModuleId:
    return ModuleId(dto.name, dto.revision)


def _type_id_to_dto(identity: MaterialTypeId) -> _MaterialTypeIdDto:
    return _MaterialTypeIdDto(
        module=_module_to_dto(identity.module),
        name=identity.name,
        revision=identity.revision,
    )


def _type_id_from_dto(dto: _MaterialTypeIdDto) -> MaterialTypeId:
    return MaterialTypeId(_module_from_dto(dto.module), dto.name, dto.revision)


def _type_to_dto(material_type: MaterialType[object]) -> _MaterialTypeDto:
    return _MaterialTypeDto(
        identity=_type_id_to_dto(material_type.identity),
        media_type=material_type.media_type,
    )


def _type_from_dto(dto: _MaterialTypeDto) -> MaterialType[object]:
    return MaterialType[object](_type_id_from_dto(dto.identity), dto.media_type)


class WorkRequestJsonCodec:
    VERSION = 1

    def encode(self, request: WorkRequest[object]) -> str:
        payload_adapter: TypeAdapter[JsonValue] = TypeAdapter(JsonValue)
        location_requirements: list[tuple[str, ...]] = []
        latency_requirements: list[str] = []
        for requirement in request.requirements:
            if isinstance(requirement, LocationRequirement):
                location_requirements.append(
                    tuple(location.value for location in sorted(requirement.allowed, key=str))
                )
            elif isinstance(requirement, LatencyRequirement):
                latency_requirements.append(requirement.maximum.name)
            else:
                raise TypeError(f"No durable codec for requirement {type(requirement).__name__}")
        location_preferences: list[tuple[str, ...]] = []
        latency_preferences: list[tuple[str, ...]] = []
        for preference in request.preferences:
            if isinstance(preference, LocationPreference):
                location_preferences.append(tuple(location.value for location in preference.order))
            elif isinstance(preference, LatencyPreference):
                latency_preferences.append(tuple(latency.name for latency in preference.order))
            else:
                raise TypeError(f"No durable codec for preference {type(preference).__name__}")

        dto = _WorkRequestDto(
            schema_version=self.VERSION,
            module=_module_to_dto(request.module),
            materials=tuple(
                _MaterialDto(
                    name=material.identity.name,
                    revision=material.identity.revision,
                    material_type=_type_to_dto(material.material_type),
                    payload=payload_adapter.validate_python(material.payload),
                    sensitivity=material.sensitivity.name,
                )
                for material in request.materials.materials
            ),
            computation=_ComputationDto(
                namespace=request.computation.identity.namespace,
                name=request.computation.identity.name,
                revision=request.computation.identity.revision,
                accepted_inputs=tuple(
                    _type_id_to_dto(identity)
                    for identity in sorted(
                        request.computation.accepted_inputs,
                        key=lambda item: (item.module.name, item.name, item.revision),
                    )
                ),
                output_type=_type_to_dto(request.computation.output_type),
            ),
            physical=_PhysicalRequestDto(
                location_requirements=tuple(location_requirements),
                latency_requirements=tuple(latency_requirements),
                location_preferences=tuple(location_preferences),
                latency_preferences=tuple(latency_preferences),
            ),
            not_before=request.timing.not_before,
            timeout_seconds=request.timing.timeout.total_seconds(),
            priority=request.priority.value,
            maximum_attempts=request.retry.maximum_attempts,
            initial_backoff_seconds=request.retry.initial_backoff.total_seconds(),
        )
        return dto.model_dump_json()

    def decode(self, encoded: str) -> WorkRequest[object]:
        dto = _WorkRequestDto.model_validate_json(encoded)
        if dto.schema_version != self.VERSION:
            raise ValueError(f"Unsupported WorkRequest schema: {dto.schema_version}")
        module = _module_from_dto(dto.module)
        materials = tuple(
            Material[object](
                MaterialId(module, item.name, item.revision),
                _type_from_dto(item.material_type),
                item.payload,
                Sensitivity[item.sensitivity],
            )
            for item in dto.materials
        )
        computation = ComputationContract[object](
            ComputationId(
                dto.computation.namespace,
                dto.computation.name,
                dto.computation.revision,
            ),
            frozenset(_type_id_from_dto(item) for item in dto.computation.accepted_inputs),
            _type_from_dto(dto.computation.output_type),
        )
        requirements = (
            *(
                LocationRequirement(frozenset(ExecutionLocation(value) for value in requirement))
                for requirement in dto.physical.location_requirements
            ),
            *(
                LatencyRequirement(LatencyClass[value])
                for value in dto.physical.latency_requirements
            ),
        )
        preferences = (
            *(
                LocationPreference(tuple(ExecutionLocation(value) for value in preference))
                for preference in dto.physical.location_preferences
            ),
            *(
                LatencyPreference(tuple(LatencyClass[value] for value in preference))
                for preference in dto.physical.latency_preferences
            ),
        )
        return WorkRequest[object](
            module=module,
            materials=MaterialSet(materials),
            computation=computation,
            requirements=requirements,
            preferences=preferences,
            timing=WorkTiming(
                not_before=dto.not_before,
                timeout=timedelta(seconds=dto.timeout_seconds),
            ),
            priority=Priority(dto.priority),
            retry=PhysicalRetryPolicy(
                dto.maximum_attempts,
                timedelta(seconds=dto.initial_backoff_seconds),
            ),
        )


class PhysicalResultJsonCodec:
    VERSION = 1

    def encode(self, result: PhysicalResult[object]) -> str:
        payload: JsonValue = TypeAdapter(JsonValue).validate_python(result.output)
        dto = _PhysicalResultDto(
            schema_version=self.VERSION,
            computation_namespace=result.computation.namespace,
            computation_name=result.computation.name,
            computation_revision=result.computation.revision,
            output_type=_type_to_dto(result.output_type),
            output=payload,
            started_at=result.started_at,
            completed_at=result.completed_at,
            attempt=result.attempt,
        )
        return dto.model_dump_json()

    def decode(self, encoded: str) -> PhysicalResult[object]:
        dto = _PhysicalResultDto.model_validate_json(encoded)
        if dto.schema_version != self.VERSION:
            raise ValueError(f"Unsupported PhysicalResult schema: {dto.schema_version}")
        return PhysicalResult[object](
            computation=ComputationId(
                dto.computation_namespace,
                dto.computation_name,
                dto.computation_revision,
            ),
            output_type=_type_from_dto(dto.output_type),
            output=dto.output,
            started_at=dto.started_at,
            completed_at=dto.completed_at,
            attempt=dto.attempt,
        )
