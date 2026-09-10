"""Explicit Agent/Operation routing with transition-local Security Algebra evaluation."""

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
    Control,
    Disclosure,
    EffectExecution,
    EffectProfile,
    ExecutionBoundary,
    SecurityDecision,
    SecurityEvaluator,
    SecurityHistory,
    SecurityObject,
    SecurityTransition,
    StructuralFailure,
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
        history: SecurityHistory,
        transition: SecurityTransition,
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
        security: SecurityHistory,
        target_module_id: str,
        agent_id: str,
        material: TransientMaterial,
    ) -> TransientMaterial:
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
        security: SecurityHistory,
        target_module_id: str,
        operation_id: str,
        effect_profile_id: str,
        material: TransientMaterial,
        controller_security_ids: tuple[str, ...] = (),
    ) -> TransientMaterial:
        descriptor = self._registry.get_operation(target_module_id, operation_id)
        if descriptor is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{operation_id}")
        endpoint = self._operation_endpoint(target_module_id)
        return await self._invoke_operation(
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            effect_profile_id=effect_profile_id,
            material=material,
            controller_security_ids=controller_security_ids,
        )

    async def _invoke_agent(
        self,
        *,
        requester_module_id: str,
        security: SecurityHistory,
        descriptor: AgentDescriptor,
        endpoint: AgentEndpoint,
        material: TransientMaterial,
    ) -> TransientMaterial:
        invocation_id, input_history = self._prepare_agent_input(
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
            output = await endpoint.invoke_agent(descriptor.id, input_history, material)
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
            target_module_id=descriptor.module_id,
            target_id=descriptor.id,
            boundary=endpoint.boundary,
            input_history=input_history,
            input_material_security_id=material.security.security_id,
            output=output,
        )

    async def _invoke_operation(
        self,
        *,
        requester_module_id: str,
        security: SecurityHistory,
        descriptor: OperationDescriptor,
        endpoint: OperationEndpoint,
        effect_profile_id: str,
        material: TransientMaterial,
        controller_security_ids: tuple[str, ...],
    ) -> TransientMaterial:
        profile = descriptor.effect_profile(effect_profile_id)
        if profile is None:
            raise SecurityDenied("invalid_effect_profile")
        invocation_id, input_history = self._prepare_operation_input(
            requester_module_id=requester_module_id,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            profile=profile,
            material=material,
            controller_security_ids=controller_security_ids,
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
            output = await endpoint.invoke_operation(
                descriptor.id,
                profile.id,
                input_history,
                material,
            )
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
            target_module_id=descriptor.module_id,
            target_id=descriptor.id,
            boundary=endpoint.boundary,
            input_history=input_history,
            input_material_security_id=material.security.security_id,
            output=output,
        )

    def _prepare_agent_input(
        self,
        *,
        requester_module_id: str,
        security: SecurityHistory,
        descriptor: AgentDescriptor,
        endpoint: AgentEndpoint,
        material: TransientMaterial,
    ) -> tuple[str, SecurityHistory]:
        invocation_id = uuid4().hex
        manifest = self._registry.get_module(descriptor.module_id)
        if manifest is None:
            raise PublishedTargetNotFound(descriptor.module_id)
        self._event(
            invocation_id,
            "agent",
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "requested",
        )
        prospective = security.merge(material.history).extend(
            objects=(material.security, manifest.security, descriptor.security, endpoint.security)
        )
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=material.security.security_id,
                    path_security_ids=(
                        manifest.security.security_id,
                        descriptor.security.security_id,
                        endpoint.security.security_id,
                    ),
                ),
            )
        )
        self._require_admissible(
            crossing_id=invocation_id,
            crossing_kind="agent-input",
            target_id=descriptor.id,
            history=prospective,
            transition=transition,
            execution_boundary=endpoint.boundary,
            material=material,
        )
        return invocation_id, prospective.extend(transitions=(transition,))

    def _prepare_operation_input(
        self,
        *,
        requester_module_id: str,
        security: SecurityHistory,
        descriptor: OperationDescriptor,
        endpoint: OperationEndpoint,
        profile: EffectProfile,
        material: TransientMaterial,
        controller_security_ids: tuple[str, ...],
    ) -> tuple[str, SecurityHistory]:
        invocation_id = uuid4().hex
        manifest = self._registry.get_module(descriptor.module_id)
        if manifest is None:
            raise PublishedTargetNotFound(descriptor.module_id)
        self._event(
            invocation_id,
            "operation",
            requester_module_id,
            descriptor.module_id,
            descriptor.id,
            "requested",
        )
        prospective = security.merge(material.history).extend(
            objects=(material.security, manifest.security, endpoint.security, profile.security)
        )
        path = [manifest.security.security_id, endpoint.security.security_id]
        if profile.discloses_material:
            path.append(profile.security.security_id)
        controllers = tuple(
            dict.fromkeys((material.security.security_id, *controller_security_ids))
        )
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=material.security.security_id,
                    path_security_ids=tuple(path),
                ),
            ),
            control=Control(
                effect_profile_security_id=profile.security.security_id,
                controller_security_ids=controllers,
            ),
            effect_execution=EffectExecution(
                operation=profile.operation,
                effect_profile_security_id=profile.security.security_id,
                executor_security_ids=(
                    manifest.security.security_id,
                    endpoint.security.security_id,
                ),
            ),
        )
        self._require_admissible(
            crossing_id=invocation_id,
            crossing_kind="operation-input",
            target_id=descriptor.id,
            history=prospective,
            transition=transition,
            execution_boundary=endpoint.boundary,
            material=material,
        )
        return invocation_id, prospective.extend(transitions=(transition,))

    def _finish_output(
        self,
        *,
        invocation_id: str,
        crossing_kind: str,
        requester_module_id: str,
        target_module_id: str,
        target_id: str,
        boundary: ExecutionBoundary,
        input_history: SecurityHistory,
        input_material_security_id: str,
        output: TransientMaterial,
    ) -> TransientMaterial:
        output_digest = material_digest(output.payload)
        output_size = material_size(output.payload)
        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            target_module_id,
            target_id,
            "returned",
            output_digest=output_digest,
            output_size=output_size,
        )
        if (
            output.digest != output_digest
            or output.security.evidence_value("content_digest") != output_digest
            or not output.security.verify_binding()
        ):
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                target_module_id,
                target_id,
                "invalid-output",
                output_digest=output_digest,
                output_size=output_size,
            )
            raise InvalidModuleResult(target_id)

        prospective = input_history.merge(output.history).extend(objects=(output.security,))
        if (
            output.security.security_id not in input_history.security_ids
            and not self._has_derivation_path(
                prospective,
                output.security.security_id,
                input_material_security_id,
            )
        ):
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                target_module_id,
                target_id,
                "invalid-output",
                output_digest=output_digest,
                output_size=output_size,
            )
            raise InvalidModuleResult("new output material requires derivation continuity")
        requester = self._requester_module_security(prospective, requester_module_id)
        if requester is None:
            raise InvalidModuleResult("requester security is absent from carried history")
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=output.security.security_id,
                    path_security_ids=(requester.security_id,),
                ),
            )
        )
        decision = self._security_evaluator.evaluate(prospective, transition)
        self._evidence.record_security_decision(
            crossing_id=invocation_id,
            crossing_kind=f"{crossing_kind}-output",
            target_id=requester_module_id,
            history=prospective,
            transition=transition,
            execution_boundary=boundary,
            decision=decision,
        )
        if not decision.admissible:
            self._event(
                invocation_id,
                crossing_kind,
                requester_module_id,
                target_module_id,
                target_id,
                "output-security-rejected",
                output_digest=output_digest,
                output_size=output_size,
            )
            raise SecurityDenied(",".join(decision.failure_codes))

        accepted = prospective.extend(transitions=(transition,))
        self._event(
            invocation_id,
            crossing_kind,
            requester_module_id,
            target_module_id,
            target_id,
            "delivered",
            output_digest=output_digest,
            output_size=output_size,
        )
        return output.model_copy(update={"history": accepted})

    def _require_admissible(
        self,
        *,
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        history: SecurityHistory,
        transition: SecurityTransition,
        execution_boundary: ExecutionBoundary,
        material: TransientMaterial,
    ) -> None:
        if material_digest(material.payload) != material.digest:
            decision = SecurityDecision(
                admissible=False,
                transition_id=transition.transition_id,
                failures=(
                    StructuralFailure(
                        code="invalid_security_binding",
                        security_ids=(material.security.security_id,),
                        detail="material_digest",
                    ),
                ),
            )
        else:
            decision = self._security_evaluator.evaluate(history, transition)
        self._evidence.record_security_decision(
            crossing_id=crossing_id,
            crossing_kind=crossing_kind,
            target_id=target_id,
            history=history,
            transition=transition,
            execution_boundary=execution_boundary,
            decision=decision,
        )
        if not decision.admissible:
            raise SecurityDenied(",".join(decision.failure_codes))

    @staticmethod
    def _has_derivation_path(
        history: SecurityHistory,
        output_security_id: str,
        source_security_id: str,
    ) -> bool:
        sources_by_output = {
            relation.output_security_id: relation.source_security_ids
            for relation in history.derivations
        }
        pending = [output_security_id]
        visited: set[str] = set()
        while pending:
            current = pending.pop()
            if current == source_security_id:
                return True
            if current in visited:
                continue
            visited.add(current)
            pending.extend(sources_by_output.get(current, ()))
        return False

    @staticmethod
    def _requester_module_security(
        history: SecurityHistory,
        module_id: str,
    ) -> SecurityObject | None:
        matches = [
            obj
            for obj in history.objects
            if obj.subject_ref.subject_kind == "module"
            and obj.subject_ref.owner_module_id == module_id
            and obj.subject_ref.local_id == module_id
        ]
        return matches[0] if len(matches) == 1 else None

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
