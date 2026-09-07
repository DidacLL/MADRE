"""Pure deterministic security algebra for Kernel crossings."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from madre_kernel.contracts import (
    ActorSecurityFacts,
    AgentInstanceRef,
    AgentTaskRef,
    ContextBundle,
    ExecutionBoundary,
    OperationDescriptor,
    SecurityDecisionEvidence,
    SecurityDecisionRef,
    SecurityDeficit,
    SecurityDeficitDimension,
    SecurityLevel,
    utc_now,
)


@dataclass(frozen=True)
class KernelSecurityPolicy:
    revision: int = 1

    def minimum_agent_trust(
        self,
        material_sensitivity: SecurityLevel,
        operation_risk: SecurityLevel,
        boundary: ExecutionBoundary,
    ) -> SecurityLevel:
        if boundary is ExecutionBoundary.REMOTE:
            return SecurityLevel.LEVEL_4
        if material_sensitivity >= SecurityLevel.LEVEL_4:
            return SecurityLevel.LEVEL_4
        if operation_risk >= SecurityLevel.LEVEL_4:
            return SecurityLevel.LEVEL_3
        return SecurityLevel.LEVEL_2

    def maximum_agent_execution_risk(
        self,
        material_sensitivity: SecurityLevel,
        boundary: ExecutionBoundary,
    ) -> SecurityLevel:
        if (
            boundary is ExecutionBoundary.REMOTE
            or material_sensitivity >= SecurityLevel.LEVEL_4
        ):
            return SecurityLevel.LEVEL_2
        return SecurityLevel.LEVEL_4

    def maximum_operation_risk(self, boundary: ExecutionBoundary) -> SecurityLevel:
        if boundary is ExecutionBoundary.REMOTE:
            return SecurityLevel.LEVEL_2
        if boundary is ExecutionBoundary.LOCAL_ISOLATED:
            return SecurityLevel.LEVEL_3
        return SecurityLevel.LEVEL_4

    def remote_boundary_permitted(self, material: Sequence[ContextBundle]) -> bool:
        return all(
            bundle.security.sensitivity <= SecurityLevel.LEVEL_2 for bundle in material
        )


class SecurityAlgebra:
    def __init__(self, policy: KernelSecurityPolicy | None = None) -> None:
        self.policy = policy or KernelSecurityPolicy()

    def evaluate_agent_context(
        self,
        *,
        decision_ref: SecurityDecisionRef,
        task: AgentTaskRef,
        material: Sequence[ContextBundle],
        agent_ref: AgentInstanceRef,
        agent: ActorSecurityFacts,
        actual_boundary: ExecutionBoundary,
    ) -> SecurityDecisionEvidence:
        deficits: list[SecurityDeficit] = []
        maximum_material_sensitivity = SecurityLevel.LEVEL_1
        for bundle in material:
            facts = bundle.security
            maximum_material_sensitivity = max(
                maximum_material_sensitivity,
                facts.sensitivity,
            )
            if facts.sensitivity > agent.maximum_handled_sensitivity:
                deficits.append(
                    SecurityDeficit(
                        dimension=SecurityDeficitDimension.SENSITIVITY,
                        supplied_level=facts.sensitivity,
                        required_level=agent.maximum_handled_sensitivity,
                        explanation_code="agent-sensitivity-ceiling",
                    )
                )
        required_trust = self.policy.minimum_agent_trust(
            maximum_material_sensitivity,
            SecurityLevel.LEVEL_1,
            actual_boundary,
        )
        if agent.trust < required_trust:
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.TRUST,
                    supplied_level=agent.trust,
                    required_level=required_trust,
                    explanation_code="kernel-agent-trust-floor",
                )
            )
        maximum_actor_risk = self.policy.maximum_agent_execution_risk(
            maximum_material_sensitivity,
            actual_boundary,
        )
        if agent.execution_risk > maximum_actor_risk:
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.RISK,
                    supplied_level=agent.execution_risk,
                    required_level=maximum_actor_risk,
                    explanation_code="kernel-agent-risk-ceiling",
                )
            )
        if (
            actual_boundary is ExecutionBoundary.REMOTE
            and not self.policy.remote_boundary_permitted(material)
        ):
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.POLICY,
                    explanation_code="remote-material-policy",
                )
            )
        return SecurityDecisionEvidence(
            ref=decision_ref,
            evaluated_at=utc_now(),
            task=task,
            material=tuple(bundle.ref for bundle in material),
            agent=agent_ref,
            operation=None,
            boundary=actual_boundary,
            policy_revision=self.policy.revision,
            accepted=not deficits,
            deficits=tuple(deficits),
        )

    def evaluate_operation(
        self,
        *,
        decision_ref: SecurityDecisionRef,
        task: AgentTaskRef,
        material: Sequence[ContextBundle],
        agent_ref: AgentInstanceRef,
        agent: ActorSecurityFacts,
        operation: OperationDescriptor,
        actual_boundary: ExecutionBoundary,
    ) -> SecurityDecisionEvidence:
        deficits: list[SecurityDeficit] = []
        maximum_material_sensitivity = SecurityLevel.LEVEL_1

        for bundle in material:
            facts = bundle.security
            maximum_material_sensitivity = max(
                maximum_material_sensitivity,
                facts.sensitivity,
            )
            if facts.sensitivity > agent.maximum_handled_sensitivity:
                deficits.append(
                    SecurityDeficit(
                        dimension=SecurityDeficitDimension.SENSITIVITY,
                        supplied_level=facts.sensitivity,
                        required_level=agent.maximum_handled_sensitivity,
                        explanation_code="agent-sensitivity-ceiling",
                    )
                )
            if facts.sensitivity > operation.security.maximum_input_sensitivity:
                deficits.append(
                    SecurityDeficit(
                        dimension=SecurityDeficitDimension.SENSITIVITY,
                        supplied_level=facts.sensitivity,
                        required_level=operation.security.maximum_input_sensitivity,
                        explanation_code="operation-sensitivity-ceiling",
                    )
                )
            if facts.trust < operation.security.minimum_input_trust:
                deficits.append(
                    SecurityDeficit(
                        dimension=SecurityDeficitDimension.TRUST,
                        supplied_level=facts.trust,
                        required_level=operation.security.minimum_input_trust,
                        explanation_code="operation-input-trust-floor",
                    )
                )
            for scope in facts.scopes:
                if scope not in operation.security.source_scopes:
                    deficits.append(
                        SecurityDeficit(
                            dimension=SecurityDeficitDimension.SCOPE,
                            scope=scope,
                            explanation_code="operation-source-scope-mismatch",
                        )
                    )

        required_agent_trust = self.policy.minimum_agent_trust(
            maximum_material_sensitivity,
            operation.security.risk,
            actual_boundary,
        )
        if agent.trust < required_agent_trust:
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.TRUST,
                    supplied_level=agent.trust,
                    required_level=required_agent_trust,
                    explanation_code="kernel-agent-trust-floor",
                )
            )

        maximum_actor_risk = self.policy.maximum_agent_execution_risk(
            maximum_material_sensitivity,
            actual_boundary,
        )
        if agent.execution_risk > maximum_actor_risk:
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.RISK,
                    supplied_level=agent.execution_risk,
                    required_level=maximum_actor_risk,
                    explanation_code="kernel-agent-risk-ceiling",
                )
            )

        maximum_operation_risk = self.policy.maximum_operation_risk(actual_boundary)
        if operation.security.risk > maximum_operation_risk:
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.RISK,
                    supplied_level=operation.security.risk,
                    required_level=maximum_operation_risk,
                    explanation_code="kernel-operation-risk-ceiling",
                )
            )

        if actual_boundary is not operation.security.execution_boundary:
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.EXECUTION_BOUNDARY,
                    explanation_code="operation-boundary-mismatch",
                )
            )
        if (
            actual_boundary is ExecutionBoundary.REMOTE
            and not self.policy.remote_boundary_permitted(material)
        ):
            deficits.append(
                SecurityDeficit(
                    dimension=SecurityDeficitDimension.POLICY,
                    explanation_code="remote-material-policy",
                )
            )

        return SecurityDecisionEvidence(
            ref=decision_ref,
            evaluated_at=utc_now(),
            task=task,
            material=tuple(bundle.ref for bundle in material),
            agent=agent_ref,
            operation=operation.ref,
            boundary=actual_boundary,
            policy_revision=self.policy.revision,
            accepted=not deficits,
            deficits=tuple(deficits),
        )
