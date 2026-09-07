"""Validated semantic contracts for the first MADRE AgenticLoop."""

from __future__ import annotations

from datetime import datetime
from enum import IntEnum, StrEnum
from typing import Annotated

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, field_validator, model_validator

OpaqueId = Annotated[str, Field(min_length=1)]
PositiveInt = Annotated[int, Field(ge=1)]


class SemanticModel(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)


class ModuleRef(SemanticModel):
    module_id: OpaqueId


class ScopeRef(SemanticModel):
    module: ModuleRef
    scope_id: OpaqueId


class SchemaRef(SemanticModel):
    module: ModuleRef
    schema_id: OpaqueId
    revision: PositiveInt


class OperationRef(SemanticModel):
    module: ModuleRef
    operation_id: OpaqueId
    revision: PositiveInt


class SkillRef(SemanticModel):
    module: ModuleRef
    skill_id: OpaqueId
    revision: PositiveInt


class WorkflowRef(SemanticModel):
    module: ModuleRef
    workflow_id: OpaqueId
    revision: PositiveInt


class AgentRef(SemanticModel):
    module: ModuleRef
    agent_id: OpaqueId


class AgentDefinitionRef(SemanticModel):
    agent: AgentRef
    revision: PositiveInt


class AgentSkillInstanceRef(SemanticModel):
    agent: AgentRef
    skill_instance_id: OpaqueId
    revision: PositiveInt


class AgentInstanceRef(SemanticModel):
    instance_id: OpaqueId


class AgentStateRef(SemanticModel):
    module: ModuleRef
    state_id: OpaqueId


class ContextBundleRef(SemanticModel):
    bundle_id: OpaqueId


class WorkPlanRef(SemanticModel):
    plan_id: OpaqueId


class AgentTaskRef(SemanticModel):
    plan: WorkPlanRef
    task_id: OpaqueId


class OperationInvocationRef(SemanticModel):
    invocation_id: OpaqueId


class SecurityDecisionRef(SemanticModel):
    decision_id: OpaqueId


class RuntimeWorkRef(SemanticModel):
    work_id: OpaqueId


class ModuleAgentDefinitionRef(SemanticModel):
    module: ModuleRef
    definition_id: OpaqueId


class ModuleAgentInstanceRef(SemanticModel):
    module: ModuleRef
    instance_id: OpaqueId


class SkillResourceRef(SemanticModel):
    module: ModuleRef
    resource_id: OpaqueId
    revision: PositiveInt


class WorkflowRecipeRef(SemanticModel):
    module: ModuleRef
    recipe_id: OpaqueId
    revision: PositiveInt


class DiscoveryPolicyRef(SemanticModel):
    policy_id: OpaqueId
    revision: PositiveInt


class SecurityLevel(IntEnum):
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5


def _ordinary(value: SecurityLevel) -> SecurityLevel:
    if value is SecurityLevel.SYSTEM_RESERVED:
        raise ValueError("SYSTEM_RESERVED is not valid for ordinary security facts")
    return value


class DataSecurityFacts(SemanticModel):
    sensitivity: SecurityLevel
    trust: SecurityLevel
    scopes: frozenset[ScopeRef]

    _sensitivity = field_validator("sensitivity")(_ordinary)
    _trust = field_validator("trust")(_ordinary)


class ActorSecurityFacts(SemanticModel):
    trust: SecurityLevel
    maximum_handled_sensitivity: SecurityLevel
    execution_risk: SecurityLevel

    _trust = field_validator("trust")(_ordinary)
    _maximum = field_validator("maximum_handled_sensitivity")(_ordinary)
    _risk = field_validator("execution_risk")(_ordinary)


class ExecutionBoundary(StrEnum):
    LOCAL_TRUSTED = "local_trusted"
    LOCAL_ISOLATED = "local_isolated"
    REMOTE = "remote"


class ClassificationTransform(StrEnum):
    NONE = "none"
    MAY_RECALCULATE = "may_recalculate"


class OperationSecurityFacts(SemanticModel):
    risk: SecurityLevel
    minimum_input_trust: SecurityLevel
    maximum_input_sensitivity: SecurityLevel
    source_scopes: frozenset[ScopeRef]
    destination_scopes: frozenset[ScopeRef]
    execution_boundary: ExecutionBoundary
    classification_transform: ClassificationTransform = ClassificationTransform.NONE

    _risk = field_validator("risk")(_ordinary)
    _minimum_trust = field_validator("minimum_input_trust")(_ordinary)
    _maximum_sensitivity = field_validator("maximum_input_sensitivity")(_ordinary)


class EffectKind(StrEnum):
    NONE = "none"
    LOCAL_MUTATION = "local_mutation"
    EXTERNAL_EFFECT = "external_effect"


class Repeatability(StrEnum):
    REPEATABLE = "repeatable"
    IDEMPOTENT = "idempotent"
    NON_REPEATABLE = "non_repeatable"
    UNKNOWN = "unknown"


class InterruptedOutcome(StrEnum):
    DETERMINATE = "determinate"
    MAY_BE_UNKNOWN = "may_be_unknown"


class EffectSemantics(SemanticModel):
    kind: EffectKind
    repeatability: Repeatability
    interrupted_outcome: InterruptedOutcome


class DefinitionProvenance(SemanticModel):
    created_at: AwareDatetime
    created_by: ModuleRef


class SkillInstanceProvenance(SemanticModel):
    created_by: ModuleRef
    source_skill: SkillRef


class ScopeDescriptor(SemanticModel):
    ref: ScopeRef
    name: str = Field(min_length=1)
    description: str = Field(min_length=1)


class ModuleManifest(SemanticModel):
    module: ModuleRef
    revision: PositiveInt
    name: str = Field(min_length=1)
    description: str = Field(min_length=1)
    visibility: DiscoveryPolicyRef
    scopes: tuple[ScopeDescriptor, ...] = ()
    operations: tuple[OperationRef, ...] = ()
    skills: tuple[SkillRef, ...] = ()
    agents: tuple[AgentDefinitionRef, ...] = ()

    @model_validator(mode="after")
    def own_scopes(self) -> ModuleManifest:
        if any(scope.ref.module != self.module for scope in self.scopes):
            raise ValueError("ModuleManifest scopes must be owned by the manifest Module")
        return self


class CoreRoleAssignment(SemanticModel):
    module: ModuleRef
    assigned_at: AwareDatetime
    policy_revision: PositiveInt


class OperationDescriptor(SemanticModel):
    ref: OperationRef
    name: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    input_schema: SchemaRef
    output_schema: SchemaRef
    security: OperationSecurityFacts
    effect_semantics: EffectSemantics
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance


class RequirementStrength(StrEnum):
    REQUIRED = "required"
    RELEVANT = "relevant"


class OperationRequirement(SemanticModel):
    operation: OperationRef
    strength: RequirementStrength


class SkillDefinition(SemanticModel):
    ref: SkillRef
    name: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    descriptive_resources: tuple[SkillResourceRef, ...] = ()
    operation_requirements: tuple[OperationRequirement, ...] = ()
    workflow_refs: tuple[WorkflowRef, ...] = ()
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance


class AgentSkillInstance(SemanticModel):
    ref: AgentSkillInstanceRef
    source_skill: SkillRef
    adopted_workflows: tuple[WorkflowRef, ...] = ()
    created_at: AwareDatetime
    provenance: SkillInstanceProvenance

    @model_validator(mode="after")
    def matching_source(self) -> AgentSkillInstance:
        if self.provenance.source_skill != self.source_skill:
            raise ValueError("Skill instance provenance must pin the same source Skill")
        return self


class AgentResolutionDescriptor(SemanticModel):
    namespace_module: ModuleRef
    descriptor_id: OpaqueId


class ActorSecurityRequirement(SemanticModel):
    minimum_trust: SecurityLevel | None = None
    minimum_handled_sensitivity: SecurityLevel | None = None
    maximum_execution_risk: SecurityLevel | None = None

    @field_validator("minimum_trust", "minimum_handled_sensitivity", "maximum_execution_risk")
    @classmethod
    def ordinary_when_present(cls, value: SecurityLevel | None) -> SecurityLevel | None:
        return None if value is None else _ordinary(value)


class AgentRequirement(SemanticModel):
    preferred_definition: AgentDefinitionRef | None = None
    resolution_descriptors: tuple[AgentResolutionDescriptor, ...] = ()
    required_skill_sources: tuple[SkillRef, ...] = ()
    required_workflows: tuple[WorkflowRef, ...] = ()
    security_requirement: ActorSecurityRequirement | None = None


class WorkflowDefinition(SemanticModel):
    ref: WorkflowRef
    name: str = Field(min_length=1)
    purpose: str = Field(min_length=1)
    input_schema: SchemaRef | None
    output_schema: SchemaRef | None
    recipe: WorkflowRecipeRef
    operation_requirements: tuple[OperationRequirement, ...] = ()
    delegation_requirements: tuple[AgentRequirement, ...] = ()
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance


class AgentDefinition(SemanticModel):
    ref: AgentDefinitionRef
    name: str = Field(min_length=1)
    description: str = Field(min_length=1)
    manager_definition_ref: ModuleAgentDefinitionRef
    skill_instances: tuple[AgentSkillInstanceRef, ...] = ()
    direct_workflows: tuple[WorkflowRef, ...] = ()
    security: ActorSecurityFacts
    resolution_descriptors: tuple[AgentResolutionDescriptor, ...] = ()
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance

    @model_validator(mode="after")
    def ownership(self) -> AgentDefinition:
        if self.manager_definition_ref.module != self.ref.agent.module:
            raise ValueError("Agent manager definition must belong to AgentRef.module")
        if any(skill.agent != self.ref.agent for skill in self.skill_instances):
            raise ValueError("AgentDefinition Skill instances must belong to the logical Agent")
        return self


class AgentInstance(SemanticModel):
    ref: AgentInstanceRef
    definition: AgentDefinitionRef
    manager_instance_ref: ModuleAgentInstanceRef
    state_refs: tuple[AgentStateRef, ...] = ()
    created_at: AwareDatetime
    closed_at: AwareDatetime | None = None

    @model_validator(mode="after")
    def ownership(self) -> AgentInstance:
        module = self.definition.agent.module
        if self.manager_instance_ref.module != module:
            raise ValueError("Agent manager instance must belong to the responsible Module")
        if any(state.module != module for state in self.state_refs):
            raise ValueError("Agent states must belong to the responsible Module")
        return self


class TypedPayload(SemanticModel):
    schema: SchemaRef
    canonical_json: str = Field(min_length=2)


class ContextProvenance(SemanticModel):
    producer_module: ModuleRef
    source_contexts: tuple[ContextBundleRef, ...] = ()


class ContextBundle(SemanticModel):
    ref: ContextBundleRef
    owner_module: ModuleRef
    purpose: str = Field(min_length=1)
    payload: TypedPayload
    security: DataSecurityFacts
    created_at: AwareDatetime
    derived_from: tuple[ContextBundleRef, ...] = ()
    derivation_operation: OperationInvocationRef | None = None
    provenance: ContextProvenance


class OwnerPlanOrigin(SemanticModel):
    request_context: ContextBundleRef | None = None


class PlanCompletion(SemanticModel):
    completed_at: AwareDatetime
    context_outputs: tuple[ContextBundleRef, ...]


class PlanTermination(SemanticModel):
    terminated_at: AwareDatetime
    reason_code: OpaqueId


class WorkPlan(SemanticModel):
    ref: WorkPlanRef
    objective: ContextBundleRef
    origin: OwnerPlanOrigin
    orchestrator_requirement: AgentRequirement | None = None
    orchestrator_definition: AgentDefinitionRef | None = None
    created_at: AwareDatetime
    updated_at: AwareDatetime
    completion: PlanCompletion | None = None
    termination: PlanTermination | None = None
    retention_until: AwareDatetime | None = None
    derived_from: WorkPlanRef | None = None

    @model_validator(mode="after")
    def one_terminal_fact(self) -> WorkPlan:
        if self.completion is not None and self.termination is not None:
            raise ValueError("WorkPlan cannot be both completed and terminated")
        return self


class OutputExpectation(SemanticModel):
    name: str = Field(min_length=1)
    schema: SchemaRef


class TaskCompletion(SemanticModel):
    completed_at: AwareDatetime
    context_outputs: tuple[ContextBundleRef, ...]


class TaskTermination(SemanticModel):
    terminated_at: AwareDatetime
    reason_code: OpaqueId


class AgentTask(SemanticModel):
    ref: AgentTaskRef
    parent_task: AgentTaskRef | None
    objective: ContextBundleRef
    agent_requirement: AgentRequirement
    resolved_agent: AgentDefinitionRef | None = None
    agent_instance: AgentInstanceRef | None = None
    skill_instances: tuple[AgentSkillInstanceRef, ...] = ()
    workflow_requirements: tuple[WorkflowRef, ...] = ()
    selected_workflow: WorkflowRef | None = None
    context_refs: tuple[ContextBundleRef, ...] = ()
    operation_requirements: tuple[OperationRequirement, ...] = ()
    prerequisite_tasks: tuple[AgentTaskRef, ...] = ()
    eligible_at: AwareDatetime | None = None
    expected_outputs: tuple[OutputExpectation, ...] = ()
    created_at: AwareDatetime
    completion: TaskCompletion | None = None
    termination: TaskTermination | None = None

    @model_validator(mode="after")
    def one_terminal_fact(self) -> AgentTask:
        if self.completion is not None and self.termination is not None:
            raise ValueError("AgentTask cannot be both completed and terminated")
        return self


class SecurityDeficitDimension(StrEnum):
    SENSITIVITY = "sensitivity"
    TRUST = "trust"
    RISK = "risk"
    SCOPE = "scope"
    EXECUTION_BOUNDARY = "execution_boundary"
    POLICY = "policy"


class SecurityDeficit(SemanticModel):
    dimension: SecurityDeficitDimension
    supplied_level: SecurityLevel | None = None
    required_level: SecurityLevel | None = None
    scope: ScopeRef | None = None
    explanation_code: OpaqueId


class SecurityDecisionEvidence(SemanticModel):
    ref: SecurityDecisionRef
    evaluated_at: AwareDatetime
    task: AgentTaskRef | None
    material: tuple[ContextBundleRef, ...]
    agent: AgentInstanceRef | None
    operation: OperationRef | None
    boundary: ExecutionBoundary
    policy_revision: PositiveInt
    accepted: bool
    deficits: tuple[SecurityDeficit, ...]

    @model_validator(mode="after")
    def accepted_has_no_deficits(self) -> SecurityDecisionEvidence:
        if self.accepted and self.deficits:
            raise ValueError("accepted security decision cannot contain deficits")
        return self


class OperationOutcomeKind(StrEnum):
    SECURITY_REJECTED = "security_rejected"
    SUCCESS = "success"
    FAILURE = "failure"
    UNKNOWN_EXTERNAL_EFFECT = "unknown_external_effect"


class OperationOutcome(SemanticModel):
    kind: OperationOutcomeKind
    observed_at: AwareDatetime
    code: OpaqueId | None = None


class OperationInvocationRecord(SemanticModel):
    ref: OperationInvocationRef
    task: AgentTaskRef
    operation: OperationRef
    requested_by: AgentInstanceRef
    requested_at: AwareDatetime
    input_contexts: tuple[ContextBundleRef, ...]
    security_decision: SecurityDecisionRef
    dispatched_at: AwareDatetime | None = None
    outcome: OperationOutcome | None = None
    produced_contexts: tuple[ContextBundleRef, ...] = ()


class RuntimeEvidencePurpose(StrEnum):
    AGENT_REASONING = "agent_reasoning"
    OPERATION_EXECUTION = "operation_execution"


class RuntimeEvidenceLink(SemanticModel):
    task: AgentTaskRef
    runtime_work: RuntimeWorkRef
    operation_invocation: OperationInvocationRef | None
    purpose: RuntimeEvidencePurpose
    created_at: AwareDatetime


class ObjectiveText(SemanticModel):
    text: str = Field(min_length=1)


class FinalAnswer(SemanticModel):
    text: str = Field(min_length=1)


class CalculateInput(SemanticModel):
    left: int
    right: int
    operator: Annotated[str, Field(pattern="^multiply$")] = "multiply"


class CalculateOutput(SemanticModel):
    value: int


class PrivateFixturePayload(SemanticModel):
    private_text: str = Field(min_length=1)


class MinimizedFixturePayload(SemanticModel):
    summary: str = Field(min_length=1)


def utc_now() -> datetime:
    return datetime.now().astimezone()
