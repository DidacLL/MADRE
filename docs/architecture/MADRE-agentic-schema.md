# MADRE agentic schema

Status: **reviewed clean-slate implementation baseline**

This document is the concrete schema authority for the clean-slate MADRE agentic environment.

Authority order:

```text
MADRE.md
    product contract

docs/architecture/MADRE-agentic-architecture.md
    architectural rationale and edge cases

docs/architecture/MADRE-agentic-schema.md
    current concrete schema
```

Superseded schema-review corrections belong to Git history, not to an override chain of active documents.

The schema is language-neutral. It defines semantic types, identities, ownership, persistence and invariants rather than Python class layout or database tables.

---

## 1. Governing boundaries

```text
Module
    owns domain semantics, Scope meaning, classification of its material,
    Operations and any Agent system it manages

CORE
    is a replaceable privileged Module role

AgentDefinition
    is a revision of one stable logical Agent

AgentInstance
    is one concrete working actor

SkillDefinition
    is a Module-published reusable ability package

AgentSkillInstance
    is a logical-Agent-owned pinned installation of a Skill

WorkflowDefinition
    is a first-class reusable explicit procedure

ContextBundle
    is bounded typed semantic material with provenance and security facts

WorkPlan / AgentTask
    are durable semantic orchestration state

MADRE Kernel
    resolves, coordinates, persists semantic orchestration and mechanically
    evaluates/enforces crossings

MADRE Runtime
    schedules and durably executes physical WorkRecords

Capability
    performs bounded computation
```

There is no canonical `Tool`, `Routine`, `WorkPlanStep`, Agent subclass hierarchy, universal Session/memory model, universal Workflow DSL or global knowledge/learning ontology.

---

## 2. Entity map

```text
MADRE Kernel installation
  |
  +-- CoreRoleAssignment --> ModuleRef
  |
  +-- policy-filtered Module registry
        |
        +-- ModuleManifest
              |
              +-- OperationDescriptor[]
              +-- SkillDefinition[]
              +-- WorkflowDefinition[]
              +-- AgentDefinition[]
              +-- Context offers

AgentRef
  +-- AgentDefinitionRef @ revision
  +-- AgentSkillInstanceRef @ independent revision
  +-- 0..N AgentInstanceRef

SkillDefinition
  +-- OperationRequirement[]
  +-- 0..N contributed WorkflowRef

AgentDefinition
  +-- opaque ModuleAgentDefinitionRef
  +-- exact AgentSkillInstanceRef[]
  +-- direct WorkflowRef[]
  +-- ActorSecurityFacts
  +-- resolution descriptors

AgentInstance
  +-- opaque ModuleAgentInstanceRef
  +-- 0..N opaque AgentStateRef

Module / Agent manager
  --typed bounded projection--> ContextBundle

WorkPlan
  +-- AgentTask[]
        +-- AgentRequirement
        +-- resolved AgentDefinitionRef?
        +-- AgentInstanceRef?
        +-- AgentSkillInstanceRef[]
        +-- Workflow requirements/selection
        +-- ContextBundleRef[]
        +-- OperationRequirement[]
        +-- prerequisite AgentTaskRef[]
        +-- OperationInvocationRecord[]
        +-- RuntimeEvidenceLink[] --> Runtime WorkRecord --> Capability
```

Semantic and physical layers remain separate:

```text
AgentTask != WorkflowDefinition
AgentTask != Runtime WorkRecord
Operation != Capability
WorkPlan != Runtime queue
```

---

## 3. Common reference types

```text
OpaqueId        non-empty opaque identifier
PositiveInt     integer >= 1
Instant         timezone-aware persisted timestamp
```

Callers do not infer semantics from opaque identifier text. Internal APIs should use distinct value types/newtypes rather than naked strings.

### Module and published-definition identities

```text
ModuleRef
    module_id: OpaqueId

ScopeRef
    module: ModuleRef
    scope_id: OpaqueId

SchemaRef
    module: ModuleRef
    schema_id: OpaqueId
    revision: PositiveInt

ModuleResourceRef
    module: ModuleRef
    resource_id: OpaqueId
    revision: PositiveInt

OperationRef
    module: ModuleRef
    operation_id: OpaqueId
    revision: PositiveInt

SkillRef
    module: ModuleRef
    skill_id: OpaqueId
    revision: PositiveInt

WorkflowRef
    module: ModuleRef
    workflow_id: OpaqueId
    revision: PositiveInt
```

### Agent identity normalization

A stable logical Agent identity is independent from both its configuration revision and its concrete runtime instances.

```text
AgentRef
    module: ModuleRef
    agent_id: OpaqueId

AgentDefinitionRef
    agent: AgentRef
    revision: PositiveInt

AgentSkillInstanceRef
    agent: AgentRef
    skill_instance_id: OpaqueId
    revision: PositiveInt

AgentInstanceRef
    instance_id: OpaqueId
```

Consequences:

- changing Agent configuration normally publishes a new `AgentDefinitionRef` under the same `AgentRef`;
- creating a distinct named/customized logical Agent allocates a new `AgentRef` and may preserve derivation provenance;
- an unchanged `AgentSkillInstanceRef` can be reused by later AgentDefinition revisions;
- changing that installed Skill publishes a new `AgentSkillInstanceRef` revision independently;
- historical AgentDefinition revisions keep their exact Skill-instance references.

Example:

```text
AgentRef A
SkillInstance X@1

AgentDefinition A@3
    skills = [X@1]
    workflows = [workflow-a]

AgentDefinition A@4
    skills = [X@1]
    workflows = [workflow-a, workflow-b]

SkillInstance X@2
AgentDefinition A@5
    skills = [X@2]
```

### Runtime/orchestration references

```text
ContextBundleRef
    bundle_id: OpaqueId

WorkPlanRef
    plan_id: OpaqueId

AgentTaskRef
    plan: WorkPlanRef
    task_id: OpaqueId

OperationInvocationRef
    invocation_id: OpaqueId

SecurityDecisionRef
    decision_id: OpaqueId

RuntimeWorkRef
    work_id: OpaqueId

ModuleArtifactRef
    module: ModuleRef
    artifact_id: OpaqueId
```

### Opaque Agent-manager references

```text
ModuleAgentDefinitionRef
    module: ModuleRef
    definition_id: OpaqueId

ModuleAgentInstanceRef
    module: ModuleRef
    instance_id: OpaqueId

AgentStateRef
    module: ModuleRef
    state_id: OpaqueId
```

For an Agent managed by Module M, manager definition/instance references and every `AgentStateRef` must also belong to M.

Kernel never interprets the target of these references.

---

## 4. Provenance and discovery

```text
DiscoveryPolicyRef
    policy_id: OpaqueId
    revision: PositiveInt

IntendedUseRef
    module: ModuleRef
    use_id: OpaqueId

DefinitionProvenance<R>
    created_at: Instant
    created_by: installation owner | ModuleRef | AgentRef
    derived_from: R | null
    source_resources: ModuleResourceRef[]
```

`derived_from` is same-kind. Derivation creates new identity; it never edits upstream history.

Discovery is policy-filtered metadata exposure. It does not grant Operation admissibility.

---

## 5. Security algebra

MADRE security is algebraic admissibility, not ACL/grant state.

Do not model authority as:

```text
AgentTask.operation_grants
permission/capability tokens issued by a planner
persisted authorized=true semantic state
```

A crossing is evaluated from current facts at the point where it would physically happen.

### Levels

```text
SecurityLevel
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5
```

`SYSTEM_RESERVED` is a Kernel/system sentinel outside the ordinary scale. Module/Agent ordinary facts use only levels 1..5.

Initial independent dimensions:

```text
sensitivity    higher => material is more sensitive
trust          higher => material/actor is more trusted
risk           higher => actor/operation/execution presents more risk
```

They are never summed, averaged or collapsed into one scalar. The shared range only simplifies dimension-specific comparison and diagnostics.

Scope is separate and non-numeric:

```text
ScopeRef
    module
    scope_id
```

The Module owns Scope meaning. Kernel does not infer Scope from payload text.

### Fact objects

```text
DataSecurityFacts
    sensitivity: OrdinarySecurityLevel
    trust: OrdinarySecurityLevel
    scopes: set[ScopeRef]

ActorSecurityFacts
    trust: OrdinarySecurityLevel
    maximum_handled_sensitivity: OrdinarySecurityLevel
    execution_risk: OrdinarySecurityLevel

ExecutionBoundary
    LOCAL_TRUSTED
    LOCAL_ISOLATED
    REMOTE

ClassificationTransform
    NONE
    MAY_RECALCULATE

OperationSecurityFacts
    risk: OrdinarySecurityLevel
    minimum_input_trust: OrdinarySecurityLevel
    maximum_input_sensitivity: OrdinarySecurityLevel
    source_scopes: set[ScopeRef]
    destination_scopes: set[ScopeRef]
    boundary: ExecutionBoundary
    classification_transform: ClassificationTransform
```

Responsibility split:

```text
Domain Module
    creates/classifies/projects DataSecurityFacts
    owns Scope semantics
    declares OperationSecurityFacts
    performs semantic minimization/anonymization/projection

Agent-managing Module
    declares ActorSecurityFacts

Kernel
    mechanically evaluates relations
    physically prevents rejected crossings/invocations
```

No prompt, Agent output, Skill, Workflow or WorkPlan field can override the Kernel evaluation.

### Initial deterministic predicates

At minimum:

- reserved value `0` is rejected in ordinary Module/Agent facts;
- material sensitivity must be within Agent handling ceiling;
- material sensitivity must be within Operation input ceiling;
- material trust must meet Operation trust floor;
- Agent trust must meet current policy for actual sensitivity/risk/boundary/intended use;
- Agent execution risk must stay within current policy ceiling;
- Operation risk must stay within current policy ceiling;
- source/destination Scope transitions must be explicitly declared and policy-permitted;
- actual execution boundary must match the Operation contract and current policy;
- lowering sensitivity requires an explicit Module-owned transformation Operation with `MAY_RECALCULATE`, producing new material which is independently evaluated.

Policy thresholds may depend on other dimensions, but remain lookup/relational predicates rather than arithmetic aggregation.

### Decision evidence

```text
SecurityDeficit
    dimension
    supplied_level: SecurityLevel | null
    required_level: SecurityLevel | null
    scope: ScopeRef | null
    explanation_code: OpaqueId

SecurityDecisionEvidence
    ref: SecurityDecisionRef
    evaluated_at: Instant
    task: AgentTaskRef | null
    material: ContextBundleRef[]
    agent: AgentInstanceRef | null
    operation: OperationRef | null
    boundary: ExecutionBoundary
    intended_use: IntendedUseRef | null
    policy_revision: PositiveInt
    accepted: bool
    deficits: SecurityDeficit[]
```

A decision is immutable execution evidence, never a reusable credential. Later crossings are re-evaluated.

---

## 6. ModuleManifest and CORE role

```text
ModuleManifest
    module: ModuleRef
    revision: PositiveInt
    name: string
    description: string
    visibility: DiscoveryPolicyRef
    scopes: ScopeDescriptor[]
    operations: OperationRef[]
    skills: SkillRef[]
    workflows: WorkflowRef[]
    agents: AgentDefinitionRef[]
    context_offers: ContextOfferDescriptor[]
```

A Module may expose zero Operations, Skills, Workflows or Agents.

Published manifest revisions are immutable. Kernel owns registration and policy-filtered discovery; the Module owns the manifest content and Scope meaning.

CORE assignment is separate:

```text
CoreRoleAssignment
    module: ModuleRef
    assigned_at: Instant
    policy_revision: PositiveInt
```

At most one active CORE assignment exists. CORE is a replaceable Module role; changing it affects future role-based resolution only and never rewrites existing Agent/Skill/Workflow/WorkPlan ownership or identity.

---

## 7. OperationDescriptor

Operation is the only canonical callable MADRE abstraction.

```text
OperationDescriptor
    ref: OperationRef
    name: string
    purpose: string
    input_schema: SchemaRef
    output_schema: SchemaRef
    security: OperationSecurityFacts
    effect_semantics: EffectSemantics
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance<OperationRef>
```

```text
EffectKind
    NONE
    LOCAL_MUTATION
    EXTERNAL_EFFECT

Repeatability
    REPEATABLE
    IDEMPOTENT
    NON_REPEATABLE
    UNKNOWN

InterruptedOutcome
    DETERMINATE
    MAY_BE_UNKNOWN
```

External scripts, APIs, MCP tools, local functions and application actions are adapter internals behind Module-owned Operations.

Skill/Workflow/AgentTask references to Operations describe requirements or guidance, never authority.

Unknown/non-repeatable effects are not blindly retried after uncertain outcome evidence.

---

## 8. SkillDefinition and AgentSkillInstance

### SkillDefinition

A Module publishes a reusable ability package:

```text
SkillDefinition
    ref: SkillRef
    name: string
    purpose: string
    descriptive_resources: SkillResourceRef[]
    operation_requirements: OperationRequirement[]
    workflow_refs: WorkflowRef[]
    context_expectations: ContextExpectation[]
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance<SkillRef>
```

A Skill may have zero Workflows. It is not merely a prompt bundle or a Workflow container.

Published Skill revisions are immutable.

### AgentSkillInstance

A logical Agent owns a pinned installation of an exact Skill revision:

```text
AgentSkillInstance
    ref: AgentSkillInstanceRef
    source_skill: SkillRef
    adopted_workflows: WorkflowRef[]
    created_at: Instant
    provenance
```

The publishing Module still owns the upstream `SkillDefinition`; the Agent's responsible Module manages the Skill instance as Agent configuration.

`AgentSkillInstance` has its own revision lifecycle under stable `AgentRef`.

Initial invariants:

- `source_skill` is exact and immutable;
- adopted Workflow revisions must come from that source Skill at creation;
- a later source Skill revision never floats into the instance;
- unrelated AgentDefinition revisions may reuse the same exact AgentSkillInstance revision;
- modifying the installed Skill creates a new AgentSkillInstance revision;
- AgentSkillInstance does not confer Operation authority;
- no autonomous learning score/state/blob is standardized.

---

## 9. WorkflowDefinition

```text
WorkflowDefinition
    ref: WorkflowRef
    name: string
    purpose: string
    input_schema: SchemaRef | null
    output_schema: SchemaRef | null
    recipe: WorkflowRecipeRef
    operation_requirements: OperationRequirement[]
    delegation_requirements: AgentRequirement[]
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance<WorkflowRef>
```

WorkflowDefinition is first-class and has no mandatory Skill or Agent parent.

A Skill may contribute WorkflowRefs. An AgentDefinition may directly attach WorkflowRefs, including private/user-created procedures.

Effective Agent repertoire:

```text
AgentDefinition.direct_workflows
+
workflows adopted through AgentSkillInstances
```

Recipe material remains behind an immutable typed/opaque `WorkflowRecipeRef` until real repeated workflows justify a universal DSL.

---

## 10. AgentDefinition and AgentInstance

### AgentDefinition

MADRE standardizes the contract around an Agent, not the internal architecture of every Agent implementation.

```text
AgentDefinition
    ref: AgentDefinitionRef
    name: string
    description: string
    manager_definition_ref: ModuleAgentDefinitionRef
    skill_instances: AgentSkillInstanceRef[]
    direct_workflows: WorkflowRef[]
    security: ActorSecurityFacts
    resolution_descriptors: AgentResolutionDescriptor[]
    visibility: DiscoveryPolicyRef
    provenance: DefinitionProvenance<AgentDefinitionRef>
```

Invariants:

- `manager_definition_ref.module == ref.agent.module`;
- every Skill instance belongs to `ref.agent`;
- AgentDefinition revisions are immutable;
- concurrent AgentInstances may instantiate the same exact revision;
- Agent ownership does not imply access to Module persistence.

The universal schema deliberately does not contain prompts, model/provider configuration, conversation history, working memory, persona memory, learning memory, standard Session or state-policy enums.

### AgentInstance

```text
AgentInstance
    ref: AgentInstanceRef
    definition: AgentDefinitionRef
    manager_instance_ref: ModuleAgentInstanceRef
    state_refs: AgentStateRef[]
    created_at: Instant
    closed_at: Instant | null
```

`state_refs` cardinality is `0..N`.

Kernel does not know whether those references represent conversation state, a state machine, learning state, persistent memory, current work or something else. The responsible Module owns and interprets them.

If Agent-managed state must cross a MADRE boundary, the responsible Module projects the relevant material into a `ContextBundle` and normal security algebra applies.

---

## 11. AgentRequirement

```text
AgentRequirement
    preferred_agent: AgentRef | null
    preferred_definition: AgentDefinitionRef | null
    resolution_descriptors: AgentResolutionDescriptor[]
    required_skill_sources: SkillRef[]
    required_workflows: WorkflowRef[]
    security_requirement: ActorSecurityRequirement | null
```

Requirements narrow semantic resolution; they never authorize data or Operations.

Persist the requirement even after exact resolution so a missing historical binding can later be detected and explicit alternatives proposed without silently substituting another Agent.

---

## 12. Typed payloads and ContextBundle

Semantic payloads are not architectural `JsonValue`, `Any` or arbitrary maps.

```text
TypedPayload<S>
    schema: SchemaRef<S>
    value: S
```

Concrete persistence may be `SchemaRef + validated canonical encoded bytes`. JSON/CBOR/etc. are encodings, not the semantic type.

```text
ContextBundle
    ref: ContextBundleRef
    owner_module: ModuleRef
    purpose: string
    payload: TypedPayload<S> | ModuleResourceRef
    security: DataSecurityFacts
    created_at: Instant
    derived_from: ContextBundleRef[]
    derivation_operation: OperationInvocationRef | null
    provenance: ContextProvenance
```

ContextBundles are immutable.

Projection/minimization/transformation always creates a new bundle. If sensitivity is lowered, the exact derivation Operation is required, that Operation must permit recalculation, and the owning Module classifies the actual derived material.

Kernel brokers and evaluates crossings but does not reinterpret domain payload to classify it.

---

## 13. WorkPlan

```text
WorkPlan
    ref: WorkPlanRef
    objective: ContextBundleRef
    origin: PlanOrigin
    orchestrator_requirement: AgentRequirement | null
    orchestrator_definition: AgentDefinitionRef | null
    created_at: Instant
    updated_at: Instant
    completion: PlanCompletion | null
    termination: PlanTermination | null
    retention_until: Instant | null
    derived_from: WorkPlanRef | null
```

Kernel owns durable semantic orchestration; Modules retain authority over referenced domain material and artifacts.

WorkPlans survive Kernel/Runtime restart and remain inspectable for configured retention.

There is no canonical `pending/running/succeeded/failed/blocked` semantic status. Open/terminal state is derived from completion/termination facts.

Replay/clone creates a new WorkPlan with provenance rather than rewriting history.

---

## 14. AgentTask

```text
AgentTask
    ref: AgentTaskRef
    parent_task: AgentTaskRef | null
    objective: ContextBundleRef
    agent_requirement: AgentRequirement
    resolved_agent: AgentDefinitionRef | null
    agent_instance: AgentInstanceRef | null
    skill_instances: AgentSkillInstanceRef[]
    workflow_requirements: WorkflowRef[]
    selected_workflow: WorkflowRef | null
    context_refs: ContextBundleRef[]
    operation_requirements: OperationRequirement[]
    prerequisite_tasks: AgentTaskRef[]
    eligible_at: Instant | null
    expected_outputs: OutputExpectation[]
    created_at: Instant
    completion: TaskCompletion | null
    termination: TaskTermination | null
```

`operation_requirements` describe need only.

Actual Operation admissibility is recomputed from current discovery, actual Context security facts/Scopes, resolved Agent facts, Operation facts, execution boundary, intended use and Kernel policy.

Readiness is derived from prerequisites, eligibility, resolvability, exact Skill/Workflow resolution, Context availability, Operation availability and current security facts.

One AgentTask may create `0..N` child tasks, Operation invocations and Runtime evidence links.

There is no semantic `work_id`.

---

## 15. OperationInvocationRecord

```text
OperationInvocationRecord
    ref: OperationInvocationRef
    task: AgentTaskRef
    operation: OperationRef
    requested_by: AgentInstanceRef
    requested_at: Instant
    input_contexts: ContextBundleRef[]
    intended_use: IntendedUseRef | null
    security_decision: SecurityDecisionRef
    dispatched_at: Instant | null
    outcome: OperationOutcome | null
    produced_contexts: ContextBundleRef[]
    artifacts: ModuleArtifactRef[]
```

The record exists even when Kernel rejects the crossing; in that case there is no provider dispatch.

Outcomes distinguish at least security rejection, success, failure and unknown external effect.

Acceptance is not cached for retry. A later attempt gets a new current security decision.

Unknown/non-repeatable effects with uncertain outcome are never blindly replayed.

---

## 16. RuntimeEvidenceLink

```text
RuntimeEvidencePurpose
    AGENT_REASONING
    OPERATION_EXECUTION

RuntimeEvidenceLink
    task: AgentTaskRef
    runtime_work: RuntimeWorkRef
    operation_invocation: OperationInvocationRef | null
    purpose: RuntimeEvidencePurpose
    created_at: Instant
```

Initial cardinality:

```text
one AgentTask -> 0..N RuntimeEvidenceLink
one Runtime WorkRecord -> 0..1 RuntimeEvidenceLink
```

Runtime owns the referenced WorkRecord and remains unaware of this relation.

A result reused by another semantic task is reused through resulting Context/artifact/evidence, not by assigning a second semantic owner to the same physical WorkRecord.

---

## 17. Ownership and persistence

| Concept | Semantic owner | Persistence |
| --- | --- | --- |
| `ModuleManifest` | Module; Kernel registers projection | current accepted revision; historical optional |
| `CoreRoleAssignment` | Kernel/installation owner policy | durable |
| `OperationDescriptor` | Module | immutable while referenced |
| `SkillDefinition` | publishing Module | immutable while referenced |
| `WorkflowDefinition` | publishing Module | immutable while referenced |
| `AgentRef` | responsible Module | stable logical identity |
| `AgentSkillInstance` | logical Agent / responsible Module | immutable revision while referenced |
| `AgentDefinition` | responsible Module | immutable revision while referenced |
| `AgentInstance` integration record | Kernel coordinates; Module manages implementation | while active/retained plan needs it |
| `AgentStateRef` target | responsible Module | Module-defined |
| `ContextBundle` | emitting/classifying Module | while required by active/retained plans/evidence |
| `WorkPlan` | Kernel | durable |
| `AgentTask` | Kernel | durable |
| `SecurityDecisionEvidence` | Kernel | durable with relevant evidence |
| `OperationInvocationRecord` | Kernel evidence; Module owns effect | durable with plan/evidence |
| `RuntimeEvidenceLink` | Kernel | durable with evidence |
| Runtime `WorkRecord` | Runtime | existing Runtime store |

Physical table/process placement does not change semantic ownership.

---

## 18. Definition revision rules

Published `OperationDescriptor`, `SkillDefinition`, `WorkflowDefinition` and `AgentDefinition` revisions are immutable.

```text
compatible semantic change under same logical identity -> new revision
new customized/derived logical identity              -> new identity + provenance
```

Exact references persisted in plans/tasks never float to later revisions.

`AgentSkillInstance` has an independent revision lifecycle from `AgentDefinition`. Updating one installed Skill does not mutate old AgentDefinition revisions; unrelated AgentDefinition changes do not recreate unchanged Skill instances.

A missing exact historical reference remains explicit evidence; Kernel must not silently substitute a later revision.

---

## 19. Crossing rules

### Discovery

Module, Agent, Skill, Workflow and Operation existence is exposed only through subject/context policy-filtered discovery. Installed does not imply globally visible.

### Context -> Agent

Before Context reaches an AgentInstance, Kernel evaluates actual `DataSecurityFacts`/Scopes, exact Agent `ActorSecurityFacts`, actual destination/boundary and current policy. Rejection physically withholds the Context.

### Context/Agent -> Operation

Before dispatch:

1. resolve the exact OperationDescriptor and actual input ContextBundles;
2. evaluate material sensitivity/trust/scopes, Agent facts, Operation risk/input constraints, destination boundary, intended use and current policy;
3. persist decision evidence as appropriate;
4. rejection creates no provider call;
5. acceptance permits only that concrete dispatch attempt.

OperationRequirement presence is not authority.

### Minimization/reclassification

Lower-sensitivity output requires a Module-owned transformation Operation with `MAY_RECALCULATE`; output is a new immutable ContextBundle with exact source/operation provenance and its own recalculated facts.

### Runtime reasoning

Semantic material entering a Runtime Capability is evaluated against the actual Capability execution boundary. Runtime itself does not need WorkPlan/AgentTask semantics.

---

## 20. Required architecture cases

The schema must continue to represent all of these without special-case ontology:

1. **Agentless CalcModule** — CORE fallback Agent uses a normal Module Operation.
2. **AAAAT Skill portability** — Agentless Module publishes Skill/Operations; an Agent owns a pinned `AgentSkillInstance`.
3. **Direct user Workflow** — attach a Workflow directly to an Agent without manufacturing a Skill.
4. **Skill update stability** — upstream Skill revision does not mutate installed AgentSkillInstance.
5. **Independent Agent/Skill revision** — unrelated AgentDefinition revision reuses unchanged Skill instance; Skill change gets its own revision.
6. **Concurrent Agent instances** — several AgentInstances share one immutable AgentDefinition but have independent/Module-defined state refs.
7. **Alternative native Agent implementation** — another Module can implement Agent internals unlike CORE behind opaque manager refs.
8. **Long WorkPlan** — semantic continuity lasts days and survives restart independently of Runtime.
9. **Multi-runtime AgentTask** — one task may reason, invoke Operation, reason again, delegate and submit further Runtime work.
10. **Privacy minimization** — high-sensitivity Context becomes a new lower-sensitivity derived bundle only through authorized Module transformation.
11. **Private discovery** — installed Module/Agent/Skill/Operation can remain invisible to unauthorized subjects.
12. **Unknown side effect** — pre-dispatch security plus truthful evidence prevent blind retry without introducing human-approval lifecycle state.
13. **Replace CORE** — changing CORE affects future fallback resolution only; existing Agent/Skill/Workflow/WorkPlan ownership remains unchanged.
14. **Missing Agent** — retained requirement + exact historical binding allow explicit future replacement choices rather than silent substitution.

---

## 21. Explicit deferrals

Do not solve these before concrete product pressure requires them:

- complete final security policy language;
- richer Module Scope relations than initial explicit matching;
- persistent persona/memory taxonomy;
- cross-task AgentInstance reuse semantics;
- Agent equivalence/similarity/substitution algorithm;
- autonomous Skill/Workflow learning or promotion;
- universal Workflow DSL;
- universal artifact ontology;
- generalized prerequisite/gate algebra;
- cross-installation package identity/export;
- supply-chain/code-derived trust framework;
- Operation revision retirement policy;
- concrete universal schema language;
- multi-Agent team ontology;
- generic human-in-the-loop framework.

Before non-repeatable external Operations are materialized through Runtime, Kernel evidence linking and Runtime idempotent submission must be made crash-reconcilable without teaching Runtime AgentTask semantics.

---

## 22. First implementation slice

The first implementation is a thin complete vertical AgenticLoop, not another horizontal registry milestone:

```text
Agentless CalcModule
        |
        | manifest + Scope + calculate Operation
        v
Kernel policy-filtered discovery
        |
        v
CORE fallback AgentDefinition
        |
        v
responsible Module instantiates AgentInstance
        |
        v
durable WorkPlan / AgentTask
        |
        v
Runtime-backed Agent reasoning #1
        |
        v
Agent requests CalcModule.calculate
        |
        v
Kernel SecurityAlgebra.evaluate(actual material/Scope/Agent/Operation/boundary/policy)
        |
        +-- rejected -> evidence, no dispatch
        |
        v accepted
CalcModule.calculate
        |
        v
OperationInvocationRecord + typed result ContextBundle
        |
        v
Runtime-backed Agent reasoning #2
        |
        v
AgentTask completion
        |
        v
WorkPlan completion
```

The slice must establish end to end:

- ModuleManifest and Module-owned OperationDescriptor;
- separate replaceable CoreRoleAssignment and fallback Agent resolution;
- stable AgentRef, revisioned AgentDefinition and independently revisioned AgentSkillInstance identities;
- AgentDefinition as public descriptor plus opaque Module manager binding;
- AgentInstance without universal Session/state payload;
- typed immutable ContextBundles;
- normalized sensitivity/trust/risk security facts and structured deficits;
- durable WorkPlan and AgentTask;
- real ordinary Runtime-backed reasoning using existing WorkSubmission/WorkRecord behavior;
- Kernel-evaluated Operation crossing;
- SecurityDecisionEvidence and OperationInvocationRecord;
- result Context feeding the same AgentTask;
- second Runtime reasoning pass;
- semantic completion;
- RuntimeEvidenceLink outside Runtime.

A second AAAAT-shaped fixture should prove Skill pinning, unchanged Skill-instance reuse across AgentDefinition revisions, private bounded Context and minimization without reimplementing AAAAT.

Do not introduce richer planners, persistent personas, learning loops, Agent teams, a general workflow engine or a universal Workflow DSL in this first slice.
