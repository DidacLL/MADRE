# MADRE execution contract

This document defines the smallest concrete MADRE-facing execution vocabulary justified by the current architecture.

It is intentionally limited to values whose meaning changes admission, scheduling, model/capability selection, physical execution, recovery or runtime evidence.

## 1. Contract layers

```text
Module-owned semantic layer
    Agent / Skills / Workflows / WorkPlan / UI / domain state
        |
        | projects executable work
        v
MADRE execution contract
    WorkSubmission / WorkRecord / WorkAttempt
    scheduling and capability requirements
    execution-boundary factors
    runtime evidence
        |
        v
Capability adapter
    provider-specific request / response / lifecycle
```

Semantic identifiers may appear only as opaque correlation metadata unless a runtime policy explicitly uses them.

## 2. Reference values

References identify runtime records and registered endpoints. They are routing/correlation values rather than execution authority.

```text
OpaqueId
    non-empty opaque value

ModuleRef
    module_id: OpaqueId

OperationRef
    module: ModuleRef
    operation_id: OpaqueId
    revision: positive integer

CapabilityRef
    capability_id: OpaqueId

WorkRef
    work_id: OpaqueId

AttemptRef
    work: WorkRef
    attempt_no: positive integer

SecurityDecisionRef
    decision_id: OpaqueId
```

A Module may use its own internal identifiers inside opaque correlation values. MADRE does not interpret them as domain identities.

## 3. Typed execution payload

MADRE must be able to carry different execution modalities without making one provider protocol canonical.

```text
SchemaRef
    namespace: OpaqueId
    schema_id: OpaqueId
    revision: positive integer

PayloadEncoding
    implementation-extensible encoding identifier

TypedPayload
    schema_ref: SchemaRef
    encoding: PayloadEncoding
    content: bytes
```

The runtime validates the payload against the exact schema/codec required by the selected capability adapter where validation is applicable.

A chat-completion adapter may define its own message schema. Another adapter may define embeddings, image, audio or structured reasoning schemas.

## 4. Security levels

```text
SecurityLevel
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5
```

Ordinary boundary values use `LEVEL_1..LEVEL_5`.

The independent dimensions are:

```text
sensitivity
trust
risk
```

The shared range enables uniform deterministic comparison. The dimensions are never arithmetically combined into one score.

## 5. Scope boundary

Scope/domain compatibility is non-numeric.

```text
ScopeRef
    namespace: OpaqueId
    scope_id: OpaqueId
```

A scope value is opaque to Kernel semantics. Runtime policy can compare explicit scope relations supplied by registered boundary contracts.

Possession of a `ScopeRef` carries no authority.

## 6. Material boundary

Every bounded material item supplied to a governed execution carries the current values relevant to that material.

```text
MaterialBoundary
    sensitivity: SecurityLevel
    trust: SecurityLevel
    scopes: set[ScopeRef]
```

The values describe the actual material being sent in this execution.

If a Module creates a new minimized/anonymized/derived payload, the new payload receives a new `MaterialBoundary` appropriate to the actual derived content.

## 7. Actor boundary

The Module-side reasoning/execution actor contributes the values needed for the current crossing.

```text
ActorBoundary
    trust: SecurityLevel
    maximum_handled_sensitivity: SecurityLevel
    execution_risk: SecurityLevel
```

The execution algebra consumes these values directly. It does not require a global Agent identity, Agent definition or Agent state object.

A Module with integrated/stateless interaction logic can provide the same boundary values without materializing a separate Agent configuration.

## 8. Execution destination boundary

The actual selected execution destination contributes the current values required for admission.

```text
ExecutionBoundaryKind
    LOCAL_TRUSTED
    LOCAL_ISOLATED
    REMOTE

DestinationBoundary
    trust: SecurityLevel
    execution_risk: SecurityLevel
    kind: ExecutionBoundaryKind
```

These values come from the actual execution path selected for the attempt, not from a semantic WorkPlan.

## 9. Operation boundary

A Module-exposed Operation can carry an immutable execution boundary profile for one exact revision.

```text
ClassificationTransform
    NONE
    MAY_RECALCULATE

OperationBoundary
    risk: SecurityLevel
    minimum_input_trust: SecurityLevel
    maximum_input_sensitivity: SecurityLevel
    source_scopes: set[ScopeRef]
    destination_scopes: set[ScopeRef]
    execution_boundary: ExecutionBoundaryKind
    classification_transform: ClassificationTransform
```

This profile is one input to algebraic evaluation; it is not an authorization grant.

## 10. Security policy and evaluation

Kernel evaluates one attempted crossing from the current boundary values.

Conceptually:

```text
SecurityDecision = SecurityAlgebra.evaluate(
    material_boundaries,
    actor_boundary,
    operation_boundary?,
    destination_boundary,
    current_policy,
)
```

A policy revision defines deterministic relations such as:

```text
for every material:
    material.sensitivity <= actor.maximum_handled_sensitivity

when operation is present:
    material.trust >= operation.minimum_input_trust
    material.sensitivity <= operation.maximum_input_sensitivity

actor.execution_risk <=
    policy.actor_risk_limit(materials, destination)

operation.risk <=
    policy.operation_risk_limit(materials, destination)

scope relation is compatible with the explicit crossing

actual destination kind is compatible with the operation/work requirement
```

`actor_risk_limit` and `operation_risk_limit` may depend on material sensitivity/trust, destination and other current dimensions. This remains a lookup/relational calculation rather than addition or averaging.

Every governed execution is re-evaluated immediately before dispatch with the actual destination selected for that attempt.

## 11. Security decision evidence

```text
SecurityDecisionEvidence
    ref: SecurityDecisionRef
    evaluated_at: timestamp
    policy_revision: positive integer
    material_boundaries: MaterialBoundary[]
    actor_boundary: ActorBoundary
    operation_boundary: OperationBoundary | null
    destination_boundary: DestinationBoundary
    accepted: bool
    deficits: SecurityDeficit[]
```

A deficit is diagnostic evidence for a failed relation.

```text
SecurityDeficit
    dimension: sensitivity | trust | risk | scope | execution_boundary | policy
    supplied_level: SecurityLevel | null
    required_level: SecurityLevel | null
    explanation_code: OpaqueId
```

An accepted decision describes one past evaluation. It is never reused as authority for another crossing.

## 12. Capability requirement

A Module describes the semantic/operational properties required of computation without embedding provider-specific request mechanics.

```text
CapabilityRequirement
    capability_class: OpaqueId
    preferred_capabilities: CapabilityRef[]
    exact_capability: CapabilityRef | null
    locality: LocalityRequirement
    resource_requirements: ResourceRequirement[]
    output_schema_ref: SchemaRef | null
```

```text
LocalityRequirement
    LOCAL_REQUIRED
    LOCAL_PREFERRED
    REMOTE_PERMITTED
```

The exact field set can grow only when a concrete selection/scheduling behavior requires it.

A Module may request an exact configured capability/model. Runtime still validates availability, boundary algebra and execution policy.

## 13. Scheduling request

Scheduling fields are independent from semantic WorkPlan meaning.

```text
SchedulingRequest
    eligible_at: timestamp | null
    priority: implementation-defined bounded value
    budget: BudgetConstraint[]
    resource_class: OpaqueId | null
    scheduling_group: SchedulingGroupRef | null
```

`SchedulingGroupRef` is an opaque runtime scheduling grouping when an actual fairness/admission policy needs group-level behavior.

The runtime may also carry member ordering/count hints when a concrete scheduler consumes them.

## 14. Correlation metadata

```text
CorrelationMetadata
    origin_module: ModuleRef
    values: map[OpaqueId, OpaqueValue]
```

Correlation values can identify a Module-owned conversation, Agent invocation, Workflow, WorkPlan or task for the originator.

MADRE stores/returns them without deriving semantic execution behavior unless a value is explicitly promoted into a separate typed scheduling field.

Correlation is not authorization.

## 15. WorkSubmission

The durable runtime input is conceptually:

```text
WorkSubmission
    input_payloads: TypedPayload[]
    material_boundaries: MaterialBoundary[]
    actor_boundary: ActorBoundary
    capability_requirement: CapabilityRequirement
    scheduling: SchedulingRequest
    correlation: CorrelationMetadata
    intended_execution: IntendedExecution
    idempotency_key: OpaqueId | null
    retry_contract: RetryContract
```

`IntendedExecution` identifies the bounded capability/action class required by Runtime without encoding a semantic Agent task ontology.

```text
IntendedExecution
    capability_class: OpaqueId
    operation_ref: OperationRef | null
```

If an Operation is invoked, Runtime resolves the exact Operation boundary/effect profile before admission.

## 16. Retry and effect contract

Physical retry semantics are explicit because durable work may be interrupted after an external effect.

```text
Repeatability
    REPEATABLE
    IDEMPOTENT
    NON_REPEATABLE
    UNKNOWN

InterruptedOutcome
    DETERMINATE
    MAY_BE_UNKNOWN

RetryContract
    repeatability: Repeatability
    interrupted_outcome: InterruptedOutcome
```

Automatic or requested retry must preserve truthful prior attempt evidence.

A work item with `MAY_BE_UNKNOWN` cannot be treated as safely repeatable solely because the process restarted.

## 17. WorkRecord

An accepted submission becomes durable runtime truth.

Conceptually:

```text
WorkRecord
    ref: WorkRef
    accepted_submission
    accepted_at
    eligibility facts
    cancellation facts
    terminal result/failure facts
    attempt refs
    selected execution evidence
```

The exact storage schema is implementation-owned. The invariant is that accepted work is recoverable and inspectable independently of the originating Module process.

## 18. WorkAttempt

```text
WorkAttempt
    ref: AttemptRef
    started_at
    selected_capability: CapabilityRef
    destination_boundary: DestinationBoundary
    security_decision: SecurityDecisionRef
    finished_at: timestamp | null
    outcome: AttemptOutcome
    metrics: ExecutionMetric[]
```

Attempt outcome records physical truth such as:

```text
SUCCESS
DETERMINATE_FAILURE
INTERRUPTED
UNKNOWN_EXTERNAL_EFFECT
CANCELLED_BEFORE_EXECUTION
```

Generated output is stored/referenced as execution result material.

## 19. Runtime result

```text
WorkResult
    work: WorkRef
    output_payloads: TypedPayload[]
    output_material_boundaries: MaterialBoundary[]
    evidence_refs: OpaqueId[]
```

The output is returned to the originating Module, which interprets it according to its Agent/Workflow/WorkPlan/domain semantics.

A capability response never directly becomes a domain mutation.

## 20. Operation descriptor

An exposed Module Operation can be registered conceptually as:

```text
OperationDescriptor
    ref: OperationRef
    purpose: string
    input_schema_refs: SchemaRef[]
    output_schema_refs: SchemaRef[]
    boundary: OperationBoundary
    effects: OperationEffectContract
    routing_metadata: OpaqueMetadata
```

```text
OperationEffectContract
    repeatability: Repeatability
    interrupted_outcome: InterruptedOutcome
```

Kernel uses the descriptor only for routing, validation, security/effect evaluation and execution evidence.

The destination Module owns implementation and domain mutation.

## 21. Module registry projection

MADRE may maintain a deterministic registry of integration endpoints.

Conceptually:

```text
ModuleRegistryEntry
    module: ModuleRef
    descriptor_revision
    routing_metadata
    operation_refs
    context_projection_metadata
    surface_projection_metadata
    boundary_metadata
```

The registry can additionally expose the installation's configured `default_module_ref`.

Module-owned intelligence interprets routing metadata. Runtime uses exact explicit references for dispatch.

## 22. Context/reference retention

Durable work can store either the actual bounded payload required for execution or a stable bounded reference whose retrieval semantics are part of the accepted work contract.

The runtime must preserve enough information for restart-safe execution.

A reference to Module-private state does not authorize broad retrieval. Any material crossing at execution time must have current boundary values and pass the algebra.

## 23. Runtime evidence

Runtime evidence is execution provenance.

Minimum useful evidence may include:

```text
WorkRef
origin Module
correlation metadata
accepted/eligible timestamps
selected capability/model
resource admission decision
security decision
attempts
result/failure
cancellation/retry/recovery
execution metrics
```

Raw model chain-of-thought is not required for runtime provenance.

## 24. Ownership matrix

| Contract concept | Runtime interprets? | Semantic owner |
| --- | --- | --- |
| WorkSubmission lifecycle | yes | MADRE |
| eligible_at / priority / resource constraints | yes | MADRE execution semantics |
| capability/model requirement | yes | Module requirement + MADRE physical selection |
| security boundary values | yes, algebraically | crossing participants / current execution boundary |
| WorkAttempt / result/failure | yes | MADRE execution truth |
| correlation metadata | opaque | Module |
| Agent identity/state | no | Module |
| Skill/Workflow | no | Module |
| WorkPlan/objective rationale | no | Module |
| conversation/UI state | no | Module |
| domain truth/knowledge | no | Module |
| domain mutation | no | destination Module Operation |

## 25. Contract invariants

1. Every accepted work item has one durable runtime identity.
2. Immediate and delayed work use the same acceptance/lifecycle path.
3. The runtime can recover accepted work without reconstructing Module semantic state.
4. A Module can reconstruct semantic correlation from returned opaque metadata and its own persistence.
5. Capability/provider dialects remain behind adapters.
6. Final physical selection is made from submitted requirements, current runtime evidence and current boundary policy.
7. Security evaluation consumes current normalized boundary values and is repeated for each governed crossing.
8. IDs/references are routing and evidence values rather than security grants.
9. A minimized/derived payload has its own current material boundary rather than a relabeled source record.
10. Domain mutations remain inside Module-owned Operations.
11. Unknown external effects remain distinguishable from determinate failure.
12. Runtime evidence records execution truth without standardizing Agent/Skill/Workflow/WorkPlan semantics.
