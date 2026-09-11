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
from madre.registry import (
    AgentDescriptor,
    InteroperabilityRegistry,
    ModuleManifest,
    OperationDescriptor,
)
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    Control,
    Disclosure,
    EffectExecution,
    EffectProfile,
    ExecutionBoundary,
    InvocationContext,
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
    def completed_derivation_ids(self) -> tuple[str, ...]: ...

    def completed_output_ids(self) -> tuple[str, ...]: ...

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
        completion_context: InvocationContext | None = None,
        completion_history: SecurityHistory | None = None,
        output_security_id: str | None = None,
        derivation_ids: tuple[str, ...] = (),
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
        self._agent_endpoints: dict[str, tuple[ModuleManifest, SecurityObject, AgentEndpoint]] = {}
        self._operation_endpoints: dict[
            str, tuple[ModuleManifest, SecurityObject, OperationEndpoint]
        ] = {}

    def attach_agent_endpoint(self, module_id: str, endpoint: AgentEndpoint) -> None:
        security = endpoint.security
        self._agent_endpoints[module_id] = (
            self._attachment(module_id, security),
            security,
            endpoint,
        )

    def attach_operation_endpoint(self, module_id: str, endpoint: OperationEndpoint) -> None:
        security = endpoint.security
        self._operation_endpoints[module_id] = (
            self._attachment(module_id, security),
            security,
            endpoint,
        )

    async def invoke_agent(
        self,
        requester: InvocationContext,
        security: SecurityHistory,
        target_module_id: str,
        agent_id: str,
        material: TransientMaterial,
    ) -> TransientMaterial:
        manifest = self._registry.get_module(target_module_id)
        descriptor = (
            next((item for item in manifest.agents if item.id == agent_id), None)
            if manifest
            else None
        )
        if descriptor is None or manifest is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{agent_id}")
        endpoint, endpoint_security = self._agent_endpoint(manifest)
        invocation = InvocationContext(
            module=manifest.security, agent=descriptor.security, endpoint=endpoint_security
        )
        return await self._invoke_agent(
            requester=requester,
            invocation=invocation,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            material=material,
        )

    async def invoke_operation(
        self,
        requester: InvocationContext,
        security: SecurityHistory,
        target_module_id: str,
        operation_id: str,
        effect_profile_id: str,
        material: TransientMaterial,
        controller_security_ids: tuple[str, ...] = (),
    ) -> TransientMaterial:
        manifest = self._registry.get_module(target_module_id)
        descriptor = (
            next((item for item in manifest.operations if item.id == operation_id), None)
            if manifest
            else None
        )
        if descriptor is None or manifest is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{operation_id}")
        endpoint, endpoint_security = self._operation_endpoint(manifest)
        profile = descriptor.effect_profile(effect_profile_id)
        if profile is None:
            raise SecurityDenied("invalid_effect_profile")
        invocation = InvocationContext(
            module=manifest.security, endpoint=endpoint_security, operation=profile.operation
        )
        return await self._invoke_operation(
            requester=requester,
            invocation=invocation,
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
        requester: InvocationContext,
        invocation: InvocationContext,
        security: SecurityHistory,
        descriptor: AgentDescriptor,
        endpoint: AgentEndpoint,
        material: TransientMaterial,
    ) -> TransientMaterial:
        invocation_id, input_history = self._prepare_agent_input(
            requester=requester,
            invocation=invocation,
            security=security,
            descriptor=descriptor,
            endpoint=endpoint,
            material=material,
        )
        self._event(
            invocation_id,
            "agent",
            requester,
            descriptor.module_id,
            descriptor.id,
            "dispatched",
        )
        boundary = endpoint.boundary
        try:
            output = await endpoint.invoke_agent(descriptor.id, invocation, input_history, material)
        except Exception:
            self._event(
                invocation_id,
                "agent",
                requester,
                descriptor.module_id,
                descriptor.id,
                "failed",
            )
            raise
        return self._finish_output(
            invocation_id=invocation_id,
            crossing_kind="agent",
            requester=requester,
            invocation=invocation,
            target_module_id=descriptor.module_id,
            target_id=descriptor.id,
            boundary=boundary,
            input_history=input_history,
            input_material_security_id=material.security.security_id,
            output=output,
        )

    async def _invoke_operation(
        self,
        *,
        requester: InvocationContext,
        invocation: InvocationContext,
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
            requester=requester,
            invocation=invocation,
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
            requester,
            descriptor.module_id,
            descriptor.id,
            "dispatched",
        )
        boundary = endpoint.boundary
        try:
            output = await endpoint.invoke_operation(
                descriptor.id,
                profile.id,
                invocation,
                input_history,
                material,
            )
        except Exception as exc:
            self._event(
                invocation_id,
                "operation",
                requester,
                descriptor.module_id,
                descriptor.id,
                "unknown-effect",
            )
            raise UnknownOperationEffect(descriptor.id) from exc
        return self._finish_output(
            invocation_id=invocation_id,
            crossing_kind="operation",
            requester=requester,
            invocation=invocation,
            target_module_id=descriptor.module_id,
            target_id=descriptor.id,
            boundary=boundary,
            input_history=input_history,
            input_material_security_id=material.security.security_id,
            output=output,
        )

    def _prepare_agent_input(
        self,
        *,
        requester: InvocationContext,
        invocation: InvocationContext,
        security: SecurityHistory,
        descriptor: AgentDescriptor,
        endpoint: AgentEndpoint,
        material: TransientMaterial,
    ) -> tuple[str, SecurityHistory]:
        invocation_id = uuid4().hex
        self._event(
            invocation_id,
            "agent",
            requester,
            descriptor.module_id,
            descriptor.id,
            "requested",
        )
        prospective = security.merge(material.history).extend(
            objects=(material.security, *invocation.objects, *requester.objects)
        )
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=material.security.security_id,
                    path_security_ids=tuple(obj.security_id for obj in invocation.objects),
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
        requester: InvocationContext,
        invocation: InvocationContext,
        security: SecurityHistory,
        descriptor: OperationDescriptor,
        endpoint: OperationEndpoint,
        profile: EffectProfile,
        material: TransientMaterial,
        controller_security_ids: tuple[str, ...],
    ) -> tuple[str, SecurityHistory]:
        invocation_id = uuid4().hex
        self._event(
            invocation_id,
            "operation",
            requester,
            descriptor.module_id,
            descriptor.id,
            "requested",
        )
        prospective = security.merge(material.history).extend(
            objects=(material.security, *invocation.objects, *requester.objects, profile.security)
        )
        path = [obj.security_id for obj in invocation.objects]
        if profile.discloses_material:
            path.append(profile.security.security_id)
        controllers = tuple(
            dict.fromkeys(
                (
                    requester.selector_security_id,
                    *controller_security_ids,
                )
            )
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
                executor_security_ids=tuple(obj.security_id for obj in invocation.objects),
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
        requester: InvocationContext,
        invocation: InvocationContext,
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
            requester,
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
                requester,
                target_module_id,
                target_id,
                "invalid-output",
                output_digest=output_digest,
                output_size=output_size,
            )
            raise InvalidModuleResult(target_id)

        prospective = input_history.merge(output.history).extend(objects=(output.security,))
        structural = self._security_evaluator.evaluate(prospective, SecurityTransition.issue())
        if not structural.admissible:
            raise InvalidModuleResult(",".join(structural.failure_codes))
        completed = set(self._evidence.completed_derivation_ids())
        incoming = {item.derivation_id for item in input_history.derivations}
        fresh = [
            item
            for item in prospective.derivations
            if item.derivation_id not in incoming and item.derivation_id not in completed
        ]
        actual = set(invocation.producer_security_ids)
        verified: set[str] = set()
        for relation in fresh:
            on_output_path = self._has_derivation_path(
                prospective, output.security.security_id, relation.output_security_id
            )
            if relation.kind == "validation":
                if not on_output_path or set(relation.validator_security_ids) != actual:
                    raise InvalidModuleResult("unverified_validation_participation")
                verified.add(relation.derivation_id)
            elif on_output_path and actual.issubset(relation.producer_security_ids):
                verified.add(relation.derivation_id)
            # Other ordinary ancestry is Module-private provenance. Do not invent
            # this invocation's participation in unrelated historical transformations.
        pass_through = output.security.security_id == input_material_security_id
        completed_output = output.security.security_id in self._evidence.completed_output_ids()
        if not pass_through:
            output_relation = next(
                (
                    item
                    for item in prospective.derivations
                    if item.output_security_id == output.security.security_id
                ),
                None,
            )
            if output_relation is None or not self._has_derivation_path(
                prospective, output.security.security_id, input_material_security_id
            ):
                raise InvalidModuleResult("new output material requires derivation continuity")
            if not completed_output:
                if output_relation.derivation_id not in {item.derivation_id for item in fresh}:
                    raise InvalidModuleResult("historical output is not a pass-through")
                if output_relation.derivation_id not in verified:
                    raise InvalidModuleResult("missing_actual_derivation_participant")
        # Stable relation identities are bound to actual completion separately; no
        # ephemeral invocation identifier enters SecurityDerivation identity.
        self._event(
            invocation_id,
            crossing_kind,
            requester,
            target_module_id,
            target_id,
            "completed",
            output_digest=output_digest,
            output_size=output_size,
            completion_context=invocation,
            completion_history=prospective,
            output_security_id=output.security.security_id,
            derivation_ids=tuple(sorted(verified)),
        )
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=output.security.security_id,
                    path_security_ids=requester.recipient_security_ids,
                ),
            )
        )
        decision = self._security_evaluator.evaluate(prospective, transition)
        self._evidence.record_security_decision(
            crossing_id=invocation_id,
            crossing_kind=f"{crossing_kind}-output",
            target_id=requester.module_id,
            history=prospective,
            transition=transition,
            execution_boundary=boundary,
            decision=decision,
        )
        if not decision.admissible:
            self._event(
                invocation_id,
                crossing_kind,
                requester,
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
            requester,
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
        completed = set(self._evidence.completed_derivation_ids())
        if any(
            relation.kind == "validation" and relation.derivation_id not in completed
            for relation in history.derivations
        ):
            raise SecurityDenied("unverified_validation_participation")
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

    def _attachment(self, module_id: str, security: SecurityObject) -> ModuleManifest:
        manifest = self._registry.get_module(module_id)
        if manifest is None:
            raise PublishedTargetNotFound(module_id)
        InvocationContext(module=manifest.security, endpoint=security)
        return manifest

    def _agent_endpoint(self, manifest: ModuleManifest) -> tuple[AgentEndpoint, SecurityObject]:
        attached = self._agent_endpoints.get(manifest.module_id)
        if attached is None:
            raise ModuleEndpointUnavailable(manifest.module_id)
        publication, security, endpoint = attached
        if publication != manifest or endpoint.security != security:
            raise ModuleEndpointUnavailable("stale Agent endpoint attachment")
        return endpoint, security

    def _operation_endpoint(
        self, manifest: ModuleManifest
    ) -> tuple[OperationEndpoint, SecurityObject]:
        attached = self._operation_endpoints.get(manifest.module_id)
        if attached is None:
            raise ModuleEndpointUnavailable(manifest.module_id)
        publication, security, endpoint = attached
        if publication != manifest or endpoint.security != security:
            raise ModuleEndpointUnavailable("stale Operation endpoint attachment")
        return endpoint, security

    def _event(
        self,
        invocation_id: str,
        crossing_kind: str,
        requester: InvocationContext,
        target_module_id: str,
        target_id: str,
        event: str,
        *,
        output_digest: str | None = None,
        output_size: int | None = None,
        completion_context: InvocationContext | None = None,
        completion_history: SecurityHistory | None = None,
        output_security_id: str | None = None,
        derivation_ids: tuple[str, ...] = (),
    ) -> None:
        self._evidence.record_broker_event(
            invocation_id=invocation_id,
            crossing_kind=crossing_kind,
            requester_module_id=requester.module_id,
            target_module_id=target_module_id,
            target_id=target_id,
            event=event,
            observed_at=utc_now(),
            output_digest=output_digest,
            output_size=output_size,
            completion_context=completion_context,
            completion_history=completion_history,
            output_security_id=output_security_id,
            derivation_ids=derivation_ids,
        )
