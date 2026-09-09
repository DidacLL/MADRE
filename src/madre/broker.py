"""Explicit Agent/Operation routing with carried security-object evaluation."""

from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime
from typing import Protocol
from uuid import uuid4

from pydantic import JsonValue

from madre.contracts import TransientMaterial
from madre.interfaces import AgentEndpoint, OperationEndpoint
from madre.registry import AgentDescriptor, InteroperabilityRegistry, OperationDescriptor
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    ExecutionBoundary,
    SecurityContext,
    SecurityDecision,
    SecurityEvaluator,
)


def utc_now() -> datetime:
    return datetime.now(UTC)


def material_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


def material_size(payload: JsonValue) -> int:
    return len(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode())


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
        *,
        security_evaluator: SecurityEvaluator = DEFAULT_SECURITY_EVALUATOR,
    ) -> None:
        self._registry = registry
        self._evidence = evidence
        self._security_evaluator = security_evaluator
        self._agent_endpoints: dict[str, AgentEndpoint] = {}
        self._operation_endpoints: dict[str, OperationEndpoint] = {}

    def attach_agent_endpoint(self, module_id: str, endpoint: AgentEndpoint) -> None:
        self._agent_endpoints[module_id] = endpoint

    def attach_operation_endpoint(self, module_id: str, endpoint: OperationEndpoint) -> None:
        self._operation_endpoints[module_id] = endpoint

    async def invoke_agent(
        self,
        requester_module_id: str,
        security: SecurityContext,
        target_module_id: str,
        agent_id: str,
        material: TransientMaterial,
    ) -> JsonValue:
        descriptor = self._registry.get_agent(target_module_id, agent_id)
        if descriptor is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{agent_id}")
        endpoint = self._agent_endpoint(target_module_id)
        return await self._invoke_agent(
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            material=material,
        )

    async def invoke_operation(
        self,
        requester_module_id: str,
        security: SecurityContext,
        target_module_id: str,
        operation_id: str,
        material: TransientMaterial,
    ) -> JsonValue:
        descriptor = self._registry.get_operation(target_module_id, operation_id)
        if descriptor is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{operation_id}")
        endpoint = self._operation_endpoint(target_module_id)
        return await self._invoke_operation(
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            material=material,
        )

    async def _invoke_agent(
        self,
        *,
        requester_module_id: str,
        security: SecurityContext,
        descriptor: AgentDescriptor,
        endpoint: AgentEndpoint,
        material: TransientMaterial,
    ) -> JsonValue:
        invocation_id, input_context = self._prepare_input(
            crossing_kind="agent",
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            material=material,
        )
        self._event(
            invocation_id,
            "agent",
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "dispatched",
        )
        try:
            output = await endpoint.invoke_agent(descriptor.id, input_context, material.payload)
        except Exception:
            self._event(
                invocation_id,
                "agent",
                requester_module_id,
                descriptor.module_id,
                descriptor.id,
                "failed",
            )
            raise
        return self._finish_output(
            invocation_id=invocation_id,
            crossing_kind="agent",
            requester_module_id=requester_module_id,
            descriptor=descriptor,
            endpoint=endpoint,
            input_context=input_context,
            output=output,
        )

    async def _invoke_operation(
        self,
        *,
        requester_module_id: str,
        security: SecurityContext,
        descriptor: OperationDescriptor,
        endpoint: OperationEndpoint,
        material: TransientMaterial,
    ) -> JsonValue:
        invocation_id, input_context = self._prepare_input(
            crossing_kind="operation",
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            material=material,
        )
        self._event(
            invocation_id,
            "operation",
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "dispatched",
        )
        try:
            output = await endpoint.invoke_operation(descriptor.id, input_context, material.payload)
        except Exception as exc:
            self._event(
                invocation_id,
                "operation",
                requester_module_id,
                descriptor.module_id,
                descriptor.id,
                "unknown-effect",
            )
            raise UnknownOperationEffect(descriptor.id) from exc
        return self._finish_output(
            invocation_id=invocation_id,
            crossing_kind="operation",
            requester_module_id=requester_module_id,
            descriptor=descriptor,
            endpoint=endpoint,
            input_context=input_context,
            output=output,
        )

    def _prepare_input(
        self,
        *,
        crossing_kind: str,
        requester_module_id: str,
        security: SecurityContext,
        descriptor: AgentDescriptor | OperationDescriptor,
        endpoint: AgentEndpoint | OperationEndpoint,
        material: TransientMaterial,
    ) -> tuple[str, SecurityContext]:
        invocation_id = uuid4().hex
        manifest = self._registry.get_module(descriptor.module_id)
        if manifest is None:
            raise PublishedTargetNotFound(descriptor.module_id)
        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "requested",
        )
        input_context = security.extend(
            material.security,
            manifest.security,
            descriptor.security,
            endpoint.security,
        )
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
        return invocation_id, input_context

    def _finish_output(
        self,
        *,
        invocation_id: str,
        crossing_kind: str,
        requester_module_id: str,
        descriptor: AgentDescriptor | OperationDescriptor,
        endpoint: AgentEndpoint | OperationEndpoint,
        input_context: SecurityContext,
        output: TransientMaterial,
    ) -> JsonValue:
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
        if (
            output.digest != output_digest
            or output.security.subject_id != output.reference
            or not output.security.verify_integrity()
        ):
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

        output_context = input_context.extend(output.security)
        output_decision = self._security_evaluator.evaluate(output_context)
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

    def _agent_endpoint(self, module_id: str) -> AgentEndpoint:
        endpoint = self._agent_endpoints.get(module_id)
        if endpoint is None:
            raise ModuleEndpointUnavailable(module_id)
        return endpoint

    def _operation_endpoint(self, module_id: str) -> OperationEndpoint:
        endpoint = self._operation_endpoints.get(module_id)
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
        material: TransientMaterial,
    ) -> SecurityDecision:
        if material_digest(material.payload) != material.digest:
            decision = SecurityDecision(
                admissible=False,
                deficits=("material_integrity",),
                evaluator="structural-material-integrity",
            )
        else:
            decision = self._security_evaluator.evaluate(context)
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
