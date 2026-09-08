"""Explicit Agent/Operation routing with carried boundary algebra."""

from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime
from typing import Protocol
from uuid import uuid4

from pydantic import JsonValue

from madre.contracts import ImmediateMaterial
from madre.registry import AgentDescriptor, InteroperabilityRegistry, OperationDescriptor
from madre.security import (
    ExecutionBoundary,
    SecurityAlgebra,
    SecurityContext,
    SecurityDecision,
    SecurityEnvelope,
)


def utc_now() -> datetime:
    return datetime.now(UTC)


def material_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def material_size(payload: JsonValue) -> int:
    return len(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode())


class ModuleEndpoint(Protocol):
    @property
    def boundary(self) -> ExecutionBoundary: ...

    @property
    def security(self) -> SecurityEnvelope: ...

    async def invoke_agent(self, agent_id: str, payload: JsonValue) -> ImmediateMaterial: ...

    async def invoke_operation(
        self, operation_id: str, payload: JsonValue
    ) -> ImmediateMaterial: ...


class BrokerEvidenceStore(Protocol):
    def record_security_decision(
        self,
        *,
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        context: SecurityContext,
        execution_boundary: ExecutionBoundary | None,
        decision: SecurityDecision,
    ) -> None: ...

    def record_broker_event(
        self,
        *,
        invocation_id: str,
        crossing_kind: str,
        requester_module_id: str,
        target_module_id: str,
        target_id: str,
        event: str,
        observed_at: datetime,
        output_digest: str | None = None,
        output_size: int | None = None,
    ) -> None: ...


class SecurityDenied(RuntimeError):
    pass


class PublishedTargetNotFound(RuntimeError):
    pass


class ModuleEndpointUnavailable(RuntimeError):
    pass


class UnknownOperationEffect(RuntimeError):
    """The Operation was dispatched but its external effect is not known."""


class InvalidModuleResult(RuntimeError):
    pass


class Broker:
    def __init__(
        self,
        registry: InteroperabilityRegistry,
        evidence: BrokerEvidenceStore,
    ) -> None:
        self._registry = registry
        self._evidence = evidence
        self._endpoints: dict[str, ModuleEndpoint] = {}

    def attach_module(self, module_id: str, endpoint: ModuleEndpoint) -> None:
        self._endpoints[module_id] = endpoint

    async def invoke_agent(
        self,
        requester_module_id: str,
        security: SecurityContext,
        agent_id: str,
        material: ImmediateMaterial,
    ) -> JsonValue:
        descriptor = self._registry.get_agent(agent_id)
        if descriptor is None:
            raise PublishedTargetNotFound(agent_id)
        return await self._invoke(
            crossing_kind="agent",
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            material=material,
        )

    async def invoke_operation(
        self,
        requester_module_id: str,
        security: SecurityContext,
        operation_id: str,
        material: ImmediateMaterial,
    ) -> JsonValue:
        descriptor = self._registry.get_operation(operation_id)
        if descriptor is None:
            raise PublishedTargetNotFound(operation_id)
        return await self._invoke(
            crossing_kind="operation",
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            material=material,
        )

    async def _invoke(
        self,
        *,
        crossing_kind: str,
        requester_module_id: str,
        security: SecurityContext,
        descriptor: AgentDescriptor | OperationDescriptor,
        material: ImmediateMaterial,
    ) -> JsonValue:
        invocation_id = uuid4().hex
        endpoint = self._endpoint(descriptor.module_id)
        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "requested",
        )

        input_context = security.extend(material.envelope, descriptor.security, endpoint.security)
        input_decision = self._evaluate(
            crossing_id=invocation_id,
            crossing_kind=f"{crossing_kind}-input",
            target_id=descriptor.id,
            context=input_context,
            execution_boundary=endpoint.boundary,
            material=material,
        )
        if not input_decision.admissible:
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                descriptor.module_id,
                descriptor.id,
                "input-security-rejected",
            )
            raise SecurityDenied(",".join(input_decision.deficits))

        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "dispatched",
        )
        try:
            if crossing_kind == "agent":
                output = await endpoint.invoke_agent(descriptor.id, material.payload)
            else:
                output = await endpoint.invoke_operation(descriptor.id, material.payload)
        except Exception as exc:
            event = "failed" if crossing_kind == "agent" else "unknown-effect"
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                descriptor.module_id,
                descriptor.id,
                event,
            )
            if crossing_kind == "operation":
                raise UnknownOperationEffect(descriptor.id) from exc
            raise

        output_digest = material_digest(output.payload)
        output_size = material_size(output.payload)
        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "returned",
            output_digest=output_digest,
            output_size=output_size,
        )
        if output.envelope.subject != output_digest or not output.envelope.verify_integrity():
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                descriptor.module_id,
                descriptor.id,
                "invalid-output",
                output_digest=output_digest,
                output_size=output_size,
            )
            raise InvalidModuleResult(descriptor.id)

        output_context = input_context.extend(output.envelope)
        output_decision = SecurityAlgebra.evaluate(output_context)
        self._evidence.record_security_decision(
            crossing_id=invocation_id,
            crossing_kind=f"{crossing_kind}-output",
            target_id=requester_module_id,
            context=output_context,
            execution_boundary=endpoint.boundary,
            decision=output_decision,
        )
        if not output_decision.admissible:
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                descriptor.module_id,
                descriptor.id,
                "output-security-rejected",
                output_digest=output_digest,
                output_size=output_size,
            )
            raise SecurityDenied(",".join(output_decision.deficits))

        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "delivered",
            output_digest=output_digest,
            output_size=output_size,
        )
        return output.payload

    def _endpoint(self, module_id: str) -> ModuleEndpoint:
        endpoint = self._endpoints.get(module_id)
        if endpoint is None:
            raise ModuleEndpointUnavailable(module_id)
        return endpoint

    def _evaluate(
        self,
        *,
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        context: SecurityContext,
        execution_boundary: ExecutionBoundary,
        material: ImmediateMaterial,
    ) -> SecurityDecision:
        if material_digest(material.payload) != material.envelope.subject:
            decision = SecurityDecision(
                admissible=False,
                deficits=("material_integrity",),
                sensitivity=context.sensitivity,
                trust=context.trust,
                risk=context.risk,
                scopes=context.scopes,
            )
        else:
            decision = SecurityAlgebra.evaluate(context)
        self._evidence.record_security_decision(
            crossing_id=crossing_id,
            crossing_kind=crossing_kind,
            target_id=target_id,
            context=context,
            execution_boundary=execution_boundary,
            decision=decision,
        )
        return decision

    def _event(
        self,
        invocation_id: str,
        crossing_kind: str,
        requester_module_id: str,
        target_module_id: str,
        target_id: str,
        event: str,
        *,
        output_digest: str | None = None,
        output_size: int | None = None,
    ) -> None:
        self._evidence.record_broker_event(
            invocation_id=invocation_id,
            crossing_kind=crossing_kind,
            requester_module_id=requester_module_id,
            target_module_id=target_module_id,
            target_id=target_id,
            event=event,
            observed_at=utc_now(),
            output_digest=output_digest,
            output_size=output_size,
        )
