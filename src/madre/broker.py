"""Explicit Agent/Operation routing without semantic Kernel selection."""

from __future__ import annotations

import hashlib
import json
from typing import Protocol

from pydantic import JsonValue

from madre.contracts import ImmediateMaterial
from madre.registry import AgentDescriptor, InteroperabilityRegistry, OperationDescriptor
from madre.security import (
    ExecutionBoundary,
    SecurityAlgebra,
    SecurityDecision,
    SecurityEnvelope,
    SecurityPolicy,
)


def material_digest(payload: JsonValue) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


class ModuleEndpoint(Protocol):
    @property
    def boundary(self) -> ExecutionBoundary: ...

    async def invoke_agent(self, agent_id: str, payload: JsonValue) -> JsonValue: ...
    async def invoke_operation(self, operation_id: str, payload: JsonValue) -> JsonValue: ...


class BrokerEvidenceStore(Protocol):
    def record_security_decision(
        self,
        *,
        crossing_kind: str,
        requester_subject: str,
        target_id: str,
        decision: SecurityDecision,
    ) -> None: ...


class SecurityDenied(RuntimeError):
    pass


class PublishedTargetNotFound(RuntimeError):
    pass


class ModuleEndpointUnavailable(RuntimeError):
    pass


class Broker:
    def __init__(
        self,
        registry: InteroperabilityRegistry,
        evidence: BrokerEvidenceStore,
        policy: SecurityPolicy | None = None,
    ) -> None:
        self._registry = registry
        self._evidence = evidence
        self._policy = policy or SecurityPolicy()
        self._endpoints: dict[str, ModuleEndpoint] = {}

    def attach_module(self, module_id: str, endpoint: ModuleEndpoint) -> None:
        if self._registry.get_module(module_id) is None:
            raise ValueError("Module must publish a manifest before attaching an endpoint")
        self._endpoints[module_id] = endpoint

    async def invoke_agent(
        self,
        requester: SecurityEnvelope,
        agent_id: str,
        material: ImmediateMaterial,
    ) -> JsonValue:
        descriptor = self._registry.get_agent(agent_id)
        if descriptor is None:
            raise PublishedTargetNotFound(agent_id)
        endpoint = self._endpoint(descriptor.module_id)
        self._authorize("agent", requester, descriptor, material, endpoint.boundary)
        return await endpoint.invoke_agent(agent_id, material.payload)

    async def invoke_operation(
        self,
        requester: SecurityEnvelope,
        operation_id: str,
        material: ImmediateMaterial,
    ) -> JsonValue:
        descriptor = self._registry.get_operation(operation_id)
        if descriptor is None:
            raise PublishedTargetNotFound(operation_id)
        endpoint = self._endpoint(descriptor.module_id)
        self._authorize("operation", requester, descriptor, material, endpoint.boundary)
        return await endpoint.invoke_operation(operation_id, material.payload)

    def _endpoint(self, module_id: str) -> ModuleEndpoint:
        endpoint = self._endpoints.get(module_id)
        if endpoint is None:
            raise ModuleEndpointUnavailable(module_id)
        return endpoint

    def _authorize(
        self,
        crossing_kind: str,
        requester: SecurityEnvelope,
        descriptor: AgentDescriptor | OperationDescriptor,
        material: ImmediateMaterial,
        execution_boundary: ExecutionBoundary,
    ) -> None:
        digest = material_digest(material.payload)
        if digest != material.envelope.subject:
            decision = SecurityDecision(admissible=False, deficits=("material_digest",))
        else:
            manifest = self._registry.get_module(descriptor.module_id)
            if manifest is None:
                raise PublishedTargetNotFound(descriptor.module_id)
            decision = SecurityAlgebra.evaluate(
                requester,
                material.envelope,
                descriptor.requirements,
                manifest.security,
                execution_boundary,
                self._policy,
            )
        self._evidence.record_security_decision(
            crossing_kind=crossing_kind,
            requester_subject=requester.subject,
            target_id=descriptor.id,
            decision=decision,
        )
        if not decision.admissible:
            raise SecurityDenied(",".join(decision.deficits))
