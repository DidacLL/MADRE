"""Exact Agent/Operation routing with relation-local security composition."""

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
    InteroperabilityRegistry,
    ModuleManifest,
    OperationDescriptor,
)
from madre.security import (
    ControlNormalForm,
    DirectUserAction,
    DisclosureNormalForm,
    EffectExecutionNormalForm,
    EffectProfile,
    ExecutionBoundary,
    InvocationContext,
    OperationUse,
    RelationDecision,
    SecurityEvidence,
    SecurityObject,
)


def utc_now() -> datetime:
    return datetime.now(UTC)


def material_digest(payload: JsonValue) -> str:
    return hashlib.sha256(
        json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


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
        evidence: SecurityEvidence,
        execution_boundary: ExecutionBoundary | None,
        decision: RelationDecision,
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
        completion_evidence: SecurityEvidence | None = None,
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
    """The Operation dispatched, but its external effect outcome is unknown."""


class InvalidModuleResult(RuntimeError):
    pass


class Broker:
    def __init__(self, registry: InteroperabilityRegistry, evidence: BrokerEvidenceStore) -> None:
        self._registry = registry
        self._evidence = evidence
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
        if security.integrity is None:
            raise ValueError("Operation endpoint requires an Integrity-bearing executor scope")
        self._operation_endpoints[module_id] = (
            self._attachment(module_id, security),
            security,
            endpoint,
        )

    async def invoke_agent(
        self,
        requester: InvocationContext,
        evidence: SecurityEvidence,
        target_module_id: str,
        agent_id: str,
        material: TransientMaterial,
    ) -> TransientMaterial:
        manifest = self._registry.get_module(target_module_id)
        descriptor = self._registry.get_agent(target_module_id, agent_id)
        if descriptor is None or manifest is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{agent_id}")
        endpoint, endpoint_security = self._agent_endpoint(manifest)
        invocation = InvocationContext(
            module=manifest.security, agent=descriptor.security, endpoint=endpoint_security
        )
        invocation_id, carried = self._prepare_disclosure(
            crossing_kind="agent-input",
            target_id=descriptor.id,
            target_module_id=descriptor.module_id,
            requester=requester,
            invocation=invocation,
            evidence=evidence,
            endpoint_boundary=endpoint.boundary,
            material=material,
        )
        self._event(
            invocation_id, "agent", requester, descriptor.module_id, descriptor.id, "dispatched"
        )
        try:
            output = await endpoint.invoke_agent(descriptor.id, invocation, carried, material)
        except Exception:
            self._event(
                invocation_id, "agent", requester, descriptor.module_id, descriptor.id, "failed"
            )
            raise
        return self._finish_output(
            invocation_id=invocation_id,
            crossing_kind="agent",
            requester=requester,
            invocation=invocation,
            target_module_id=descriptor.module_id,
            target_id=descriptor.id,
            boundary=endpoint.boundary,
            input_evidence=carried,
            input_scope_security_id=material.security.security_id,
            output=output,
            profile=None,
        )

    async def invoke_operation(
        self,
        requester: InvocationContext,
        evidence: SecurityEvidence,
        target_module_id: str,
        operation_id: str,
        use: OperationUse,
        material: TransientMaterial,
    ) -> TransientMaterial:
        manifest = self._registry.get_module(target_module_id)
        descriptor = self._registry.get_operation(target_module_id, operation_id)
        if descriptor is None or manifest is None:
            raise PublishedTargetNotFound(f"{target_module_id}:{operation_id}")
        profile = descriptor.effect_profile(use.profile_id)
        if profile is None or not profile.verify_identity():
            raise SecurityDenied("invalid_effect_profile")
        endpoint, endpoint_security = self._operation_endpoint(manifest)
        invocation = InvocationContext(
            module=manifest.security, endpoint=endpoint_security, operation=profile.operation
        )
        invocation_id, carried = self._prepare_operation(
            requester=requester,
            invocation=invocation,
            evidence=evidence,
            descriptor=descriptor,
            endpoint=endpoint,
            profile=profile,
            use=use,
            material=material,
        )
        self._event(
            invocation_id, "operation", requester, descriptor.module_id, descriptor.id, "dispatched"
        )
        try:
            output = await endpoint.invoke_operation(
                descriptor.id, profile.id, invocation, carried, material
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
            boundary=endpoint.boundary,
            input_evidence=carried,
            input_scope_security_id=material.security.security_id,
            output=output,
            profile=profile,
        )

    def _prepare_disclosure(
        self,
        *,
        crossing_kind: str,
        target_id: str,
        target_module_id: str,
        requester: InvocationContext,
        invocation: InvocationContext,
        evidence: SecurityEvidence,
        endpoint_boundary: ExecutionBoundary,
        material: TransientMaterial,
    ) -> tuple[str, SecurityEvidence]:
        invocation_id = uuid4().hex
        self._event(
            invocation_id,
            crossing_kind.removesuffix("-input"),
            requester,
            target_module_id,
            target_id,
            "requested",
        )
        prospective = evidence.merge(material.evidence).extend(
            objects=(material.security, *invocation.objects, *requester.objects)
        )
        action = self._direct_action(
            crossing_id=invocation_id,
            requester=requester,
            supplied=requester.direct_interaction,
            source=material.security,
            observers=invocation.objects,
            profile=None,
        )
        result = DisclosureNormalForm.compose(
            crossing_id=invocation_id,
            sources=(material.security,),
            observers=invocation.objects,
            direct_user_action=action,
            active_interaction=requester.direct_interaction,
        )
        self._record(
            invocation_id,
            crossing_kind,
            target_id,
            prospective,
            endpoint_boundary,
            result.decision(),
        )
        if result.accepted is None:
            raise SecurityDenied(",".join(result.decision().failure_codes))
        return invocation_id, prospective.extend(
            relations=(result.accepted,), decisions=(result.decision(),)
        )

    def _prepare_operation(
        self,
        *,
        requester: InvocationContext,
        invocation: InvocationContext,
        evidence: SecurityEvidence,
        descriptor: OperationDescriptor,
        endpoint: OperationEndpoint,
        profile: EffectProfile,
        use: OperationUse,
        material: TransientMaterial,
    ) -> tuple[str, SecurityEvidence]:
        invocation_id = uuid4().hex
        self._event(
            invocation_id, "operation", requester, descriptor.module_id, descriptor.id, "requested"
        )
        observers = (*invocation.objects, *use.disclosure_observers)
        controllers = (requester.selector, *use.controllers)
        executors = (invocation.endpoint,) if invocation.endpoint is not None else ()
        prospective = evidence.merge(material.evidence).extend(
            objects=(material.security, *requester.objects, *observers, *controllers, *executors)
        )
        action = self._direct_action(
            crossing_id=invocation_id,
            requester=requester,
            supplied=use.direct_interaction,
            source=material.security,
            observers=observers,
            profile=profile,
        )
        disclosure = DisclosureNormalForm.compose(
            crossing_id=invocation_id,
            sources=(material.security,),
            observers=observers,
            direct_user_action=action,
            active_interaction=requester.direct_interaction,
            operation=profile.operation,
            effect_profile_id=profile.effect_profile_id,
        )
        control = ControlNormalForm.compose(profile=profile, controllers=controllers)
        execution = EffectExecutionNormalForm.compose(profile=profile, executors=executors)
        results = (disclosure, control, execution)
        for result in results:
            self._record(
                invocation_id,
                f"operation-{result.relation_kind}",
                descriptor.id,
                prospective,
                endpoint.boundary,
                result.decision(),
            )
        failures = tuple(code for result in results for code in result.decision().failure_codes)
        if failures:
            raise SecurityDenied(",".join(failures))
        relations = tuple(result.accepted for result in results if result.accepted is not None)
        return invocation_id, prospective.extend(
            relations=relations,
            decisions=tuple(result.decision() for result in results),
        )

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
        input_evidence: SecurityEvidence,
        input_scope_security_id: str,
        output: TransientMaterial,
        profile: EffectProfile | None,
    ) -> TransientMaterial:
        digest = material_digest(output.payload)
        size = material_size(output.payload)
        self._event(
            invocation_id,
            crossing_kind,
            requester,
            target_module_id,
            target_id,
            "returned",
            output_digest=digest,
            output_size=size,
        )
        if (
            output.digest != digest
            or output.security.binding.content_digest != digest
            or not output.security.verify_binding()
            or output.evidence.resolve(output.security.security_id) != output.security
        ):
            self._event(
                invocation_id,
                crossing_kind,
                requester,
                target_module_id,
                target_id,
                "invalid-output",
                output_digest=digest,
                output_size=size,
            )
            raise InvalidModuleResult(target_id)
        prospective = input_evidence.merge(output.evidence).extend(objects=(output.security,))
        verified = self._verify_fresh_derivation(
            input_evidence=input_evidence,
            prospective=prospective,
            input_security_id=input_scope_security_id,
            output_security=output.security,
            invocation=invocation,
        )
        self._event(
            invocation_id,
            crossing_kind,
            requester,
            target_module_id,
            target_id,
            "completed",
            output_digest=digest,
            output_size=size,
            completion_context=invocation,
            completion_evidence=prospective,
            output_security_id=output.security.security_id,
            derivation_ids=verified,
        )
        action = self._direct_action(
            crossing_id=invocation_id,
            requester=requester,
            supplied=requester.direct_interaction,
            source=output.security,
            observers=requester.recipients,
            profile=profile,
        )
        result = DisclosureNormalForm.compose(
            crossing_id=invocation_id,
            sources=(output.security,),
            observers=requester.recipients,
            direct_user_action=action,
            active_interaction=requester.direct_interaction,
            operation=profile.operation if profile is not None else None,
            effect_profile_id=profile.effect_profile_id if profile is not None else None,
        )
        self._record(
            invocation_id,
            f"{crossing_kind}-output",
            requester.module_id,
            prospective,
            boundary,
            result.decision(),
        )
        if result.accepted is None:
            self._event(
                invocation_id,
                crossing_kind,
                requester,
                target_module_id,
                target_id,
                "output-security-rejected",
                output_digest=digest,
                output_size=size,
            )
            raise SecurityDenied(",".join(result.decision().failure_codes))
        accepted = prospective.extend(relations=(result.accepted,), decisions=(result.decision(),))
        self._event(
            invocation_id,
            crossing_kind,
            requester,
            target_module_id,
            target_id,
            "delivered",
            output_digest=digest,
            output_size=size,
        )
        return output.model_copy(update={"evidence": accepted})

    def _verify_fresh_derivation(
        self,
        *,
        input_evidence: SecurityEvidence,
        prospective: SecurityEvidence,
        input_security_id: str,
        output_security: SecurityObject,
        invocation: InvocationContext,
    ) -> tuple[str, ...]:
        if output_security.security_id == input_security_id:
            return ()
        incoming = {item.derivation_id for item in input_evidence.derivations}
        relation = next(
            (
                item
                for item in prospective.derivations
                if item.output_security_id == output_security.security_id
            ),
            None,
        )
        if relation is None or relation.derivation_id in incoming:
            raise InvalidModuleResult("new output material requires fresh derivation evidence")
        if not self._has_derivation_path(
            prospective, output_security.security_id, input_security_id
        ):
            raise InvalidModuleResult("new output material requires derivation continuity")
        if relation.kind == "validation":
            actual_validators = tuple(
                sorted(
                    item.security_id for item in invocation.producers if item.integrity is not None
                )
            )
            if relation.validator_security_ids != actual_validators:
                raise InvalidModuleResult("validation did not bind the actual validators")
        else:
            actual_producers = {item.security_id for item in invocation.producers}
            if not actual_producers.issubset(relation.producer_security_ids):
                raise InvalidModuleResult("derivation did not bind the actual producers")
        return (relation.derivation_id,)

    @staticmethod
    def _has_derivation_path(
        evidence: SecurityEvidence, output_security_id: str, source_security_id: str
    ) -> bool:
        sources = {
            item.output_security_id: item.source_security_ids for item in evidence.derivations
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
            pending.extend(sources.get(current, ()))
        return False

    @staticmethod
    def _direct_action(
        *,
        crossing_id: str,
        requester: InvocationContext,
        supplied: object,
        source: SecurityObject,
        observers: tuple[SecurityObject, ...],
        profile: EffectProfile | None,
    ) -> DirectUserAction | None:
        interaction = requester.direct_interaction
        if (
            supplied is None
            or interaction is None
            or supplied != interaction
            or source.binding.content_digest is None
        ):
            return None
        return DirectUserAction(
            crossing_id=crossing_id,
            interaction=interaction,
            source_security_id=source.security_id,
            source_scope_revision=source.scope.scope_revision,
            source_digest=source.binding.content_digest,
            observer_security_ids=tuple(item.security_id for item in observers),
            operation=profile.operation if profile is not None else None,
            effect_profile_id=profile.effect_profile_id if profile is not None else None,
        )

    def _record(
        self,
        crossing_id: str,
        crossing_kind: str,
        target_id: str,
        evidence: SecurityEvidence,
        boundary: ExecutionBoundary | None,
        decision: RelationDecision,
    ) -> None:
        self._evidence.record_security_decision(
            crossing_id=crossing_id,
            crossing_kind=crossing_kind,
            target_id=target_id,
            evidence=evidence,
            execution_boundary=boundary,
            decision=decision,
        )

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
        completion_evidence: SecurityEvidence | None = None,
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
            completion_evidence=completion_evidence,
            output_security_id=output_security_id,
            derivation_ids=derivation_ids,
        )
