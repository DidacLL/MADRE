# MADRE agentic schema

Status: **clean-slate schema proposal for Owner review**

This document derives the minimum concrete schema needed to begin implementing the
agentic environment defined by `MADRE.md` and `MADRE-agentic-architecture.md` on
`architecture/modular-agentic-clean-slate`.

It is intentionally a schema and ownership design, not a runtime implementation. It
does not import the discarded `ReasoningModule` ontology and does not derive from PR
#38 dataclasses.

No correction to the clean-slate `MADRE.md` is required by this derivation.

---

## 1. Design decisions

The schema is based on the following decisions.

1. **Published semantic definitions are immutable revisions.** `OperationDescriptor`,
   `SkillDefinition`, `WorkflowDefinition` and `AgentDefinition` use Module-scoped
   identities plus explicit positive revisions. A later Module publication never
   silently changes an already-pinned Agent or WorkPlan.
2. **Module identity is installation-scoped and stable.** A Module does not need a
   globally unique identifier. References to Module-owned definitions are typed and
   contain the owning `ModuleRef`.
3. **CORE assignment is Kernel configuration, not Module self-description.** A Module
   cannot grant itself CORE privilege by setting a manifest flag.
4. **Skills and Workflows remain first-class independent definitions.** Skills point to
   contributed Workflow revisions. AgentDefinitions point to direct Workflow revisions.
   Workflows do not contain reverse parent pointers.
5. **Skill references do not grant Operation authority.** Skills may describe required
   or relevant Operations, but an AgentInstance receives only explicit task Operation
   grants that are further narrowed by its AgentDefinition and Kernel policy.
6. **AgentDefinition and AgentInstance are separate identities.** Concurrent instances
   of the same pinned AgentDefinition never share working state implicitly.
7. **ContextBundle is immutable transfer data.** Classification, flow constraints and
   derivation provenance travel with the payload.
8. **WorkPlan and AgentTask do not embed Runtime lifecycle.** Open/in-flight/readiness
   conditions are derived from persisted facts. There is no `pending/running/blocked`
   semantic status enum.
9. **WorkPlan owns AgentTasks relationally, not as an embedded mutable graph.** Tasks are
   independently addressable by `(plan_id, task_id)`, which permits durable incremental
   creation without rewriting the plan record.
10. **Runtime correlation is Kernel-owned many-to-many evidence.** Runtime `WorkRecord`
    remains unchanged and unaware of semantic meaning. An AgentTask may correlate with
    zero, one or many Runtime records.
11. **Security is a small deterministic algebra, not prompt policy.** Initial labels,
    visibility rules, destination clearance and egress constraints are value objects.
    They can later be replaced or extended without changing entity ownership.
12. **Security policy may tighten after publication.** Pinning a semantic definition
    preserves behavior/configuration identity; it never freezes an old authorization
    decision or overrides current Kernel policy.

---

## 2. Schema notation and shared primitives

The field tables below are language-neutral. Suggested implementation mappings are:

```text
string          non-empty UTF-8 string
Instant         timezone-aware timestamp, normalized for persistence
JsonValue       JSON scalar/object/array value
JsonSchema      validated JSON Schema object for a bounded input/output contract
PositiveInt     integer >= 1
Duration        non-negative duration
Digest          content digest, algorithm-qualified when serialized
```

Persisted records should be serialized through a versioned envelope rather than by
assuming one programming-language class layout is permanent:

```text
StoredRecord<T>
  schema: string       # e.g. "madre.agentic/skill-definition@1"
  value: T
```

`schema` is serialization versioning. It is distinct from a semantic definition's
`revision`.

### 2.1 Scoped identifiers and typed references

Identifiers are opaque values. Their scope is part of the type.

```text
ModuleRef
  module_id: string                 # unique within one MADRE installation

OperationRef
  module: ModuleRef
  operation_id: string              # unique within the Module
  revision: PositiveInt

SkillRef
  module: ModuleRef
  skill_id: string                  # unique within the Module definition store
  revision: PositiveInt

WorkflowRef
  module: ModuleRef
  workflow_id: string               # unique within the Module definition store
  revision: PositiveInt

AgentDefinitionRef
  module: ModuleRef
  agent_id: string                  # unique within the responsible Module
  revision: PositiveInt

AgentInstanceRef
  instance_id: string               # unique within one MADRE installation

ContextBundleRef
  bundle_id: string                 # unique within one MADRE installation

WorkPlanRef
  plan_id: string                   # unique within one MADRE installation

AgentTaskRef
  plan: WorkPlanRef
  task_id: string                   # unique within one WorkPlan

OperationInvocationRef
  invocation_id: string             # unique within one MADRE installation

RuntimeWorkRef
  work_id: string                   # existing Runtime WorkRecord.id

ModuleArtifactRef
  module: ModuleRef
  artifact_id: string               # opaque to Kernel; interpreted by owning Module
```

The same raw string used in different reference types is not interchangeable.
Application code should use distinct value classes/newtypes rather than passing naked
strings through internal APIs.

### 2.2 Security primitives

The first implementation needs a deliberately small lattice.

```text
Sensitivity = PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED

SecurityLabel
  sensitivity: Sensitivity
  compartments: set[string]         # empty means no compartment restriction
```

A destination clearance covers a label when its sensitivity is at least as high and it
contains every required compartment.

```text
TrustBoundary = LOCAL_TRUSTED | LOCAL_ISOLATED | REMOTE

SecurityProfile
  clearance: SecurityLabel
  boundary: TrustBoundary
```

`SecurityProfile` is a value object, not a separately discoverable/persisted entity. It
is embedded where a Module or Agent definition declares its constraints.

Visibility uses an allow-only subject selector:

```text
PolicySubjectRef = one of:
  OwnerSubject
  CoreRoleSubject
  ModuleSubject(ModuleRef)
  AgentDefinitionSubject(AgentDefinitionRef)

VisibilityPolicy
  visible_to: set[PolicySubjectRef]
```

The Kernel may impose stricter installation policy. Entity visibility is always the
intersection of Module visibility, entity visibility and current Kernel policy.

Context transfer has independent flow constraints:

```text
FlowConstraints
  remote_egress_allowed: bool
  allowed_destinations: set[PolicySubjectRef] | null
  allowed_operations: set[OperationRef] | null
  expires_at: Instant | null
```

`null` means no additional allowlist at that dimension; it never means bypass current
Kernel policy.

### 2.3 Provenance primitives

```text
DefinitionProvenance<R>
  authored_by: PolicySubjectRef
  created_at: Instant
  derived_from: R | null
  source_uri: string | null
  source_digest: Digest | null
```

`derived_from` is same-kind: a derived Skill points to a Skill revision, a derived
Workflow to a Workflow revision, and a derived AgentDefinition to an AgentDefinition
revision. Creating a customized definition allocates a new identity and starts its own
revision sequence.

Workflow membership is intentionally not recorded as provenance. A Skill contributes a
Workflow by referencing it; an AgentDefinition owns a direct Workflow attachment by
referencing it. This avoids bidirectional object graphs and permits the same Workflow
revision to be reused deliberately in more than one configuration.

---

## 3. Entity/reference map

```text
                             Kernel installation state
                                      |
                                      +-- CoreRoleAssignment --> ModuleRef
                                      |
                                      +-- Module registry
                                             |
                                             v
                                        ModuleManifest
                                         /    |    \
                                        /     |     \
                                       v      v      v
                              OperationRef  SkillRef  AgentDefinitionRef
                                  |           |              |
                                  v           v              v
                         OperationDescriptor SkillDefinition AgentDefinition
                                                |              |
                                                | workflow_refs| direct_workflow_refs
                                                +-------> WorkflowDefinition

Module --bounded projection--> ContextBundle

AgentDefinition --instantiate--> AgentInstance
                                      |
                                      | works for
                                      v
WorkPlan -------------------------> AgentTask
  ^                                   |
  | derived_from                      +-- ContextBundleRef[]
WorkPlan                              +-- SkillRef[]
                                      +-- WorkflowRef?
                                      +-- explicit OperationRef grants
                                      +-- child AgentTask rows
                                      |
                                      +--> OperationInvocation
                                      |          |
                                      |          +--> produced ContextBundleRef[]
                                      |
                                      +--> RuntimeCorrelation --> Runtime WorkRecord

Runtime WorkRecord --> Capability execution evidence
```

There is no canonical `Tool`, `Routine`, `WorkPlanStep`, planner subclass, team entity,
knowledge-candidate entity or 1:1 AgentTask/Runtime-work link.

---

## 4. ModuleManifest

### Responsibility

Publish one Module's current MADRE-facing semantic contract and discovery surface.

### Fields

| Field | Type | Cardinality | Meaning |
| --- | --- | ---: | --- |
| `module` | `ModuleRef` | 1 | Stable installation identity. |
| `manifest_revision` | `PositiveInt` | 1 | Monotonic revision of the manifest publication. |
| `name` | `string` | 1 | Human-readable name. |
| `description` | `string` | 1 | Module purpose/domain boundary. |
| `scope` | `ScopeDescriptor[]` | 0..N | Semantic routing/discovery hints. |
| `visibility` | `VisibilityPolicy` | 1 | Who may discover that this Module exists. |
| `security` | `SecurityProfile` | 1 | Module trust/clearance declaration. |
| `operations` | `OperationRef[]` | 0..N | Currently advertised Operation revisions. |
| `skills` | `SkillRef[]` | 0..N | Currently advertised Skill revisions. |
| `agents` | `AgentDefinitionRef[]` | 0..N | Currently advertised AgentDefinition revisions. |
| `context_offers` | `ContextOfferDescriptor[]` | 0..N | Bounded context kinds/purposes the Module can project. |

Suggested value objects:

```text
ScopeDescriptor
  name: string
  description: string
  tags: set[string]

ContextOfferDescriptor
  context_key: string
  purpose: string
  output_schema: JsonSchema
  default_label: SecurityLabel
```

### Identity/reference semantics

`ModuleRef` is stable while the installation considers this the same semantic Module.
Manifest revisions are replaceable publications. Definitions have their own revisioned
identities and are not identified by manifest position.

### Persistence

Kernel registry persists the latest accepted manifest and may retain older manifest
revisions for inspection. Old pinned definitions remain in their responsible definition
stores even after they are no longer advertised by the latest manifest.

### Ownership/authority

The Module owns and signs/serves the content of its manifest. Kernel decides whether to
register it and who may discover it.

### Invariants

- A manifest may advertise zero Agents, zero Skills and zero Operations.
- Every advertised definition reference must resolve to the same `ModuleRef` unless a
  future explicit import mechanism says otherwise.
- A manifest cannot assign itself the CORE role.
- Module visibility is an upper bound; child descriptors cannot widen it.

### Intentionally not stored

- Internal application/database schema.
- Runtime Capability configuration.
- WorkPlans or AgentTasks.
- CORE privilege.
- Arbitrary Module-owned domain state.

---

## 5. CoreRoleAssignment

A separate representation **is required** because CORE is privileged Kernel policy, not
Module self-description.

### Fields

```text
CoreRoleAssignment
  module: ModuleRef
  assigned_at: Instant
  assigned_by: OwnerSubject
  policy_revision: PositiveInt
```

This is a singleton installation-scoped Kernel configuration record.

### Persistence and authority

Persist durably as Kernel configuration. Only the Owner/Kernel administration path may
change it. A Module manifest cannot mutate it.

### Invariants

- At most one active assignment exists.
- Assignment does not bypass Module visibility, ContextBundle flow constraints,
  Operation authorization or current security policy.
- Replacing CORE changes future resolution policy; it does not rewrite historical Agent
  or WorkPlan references.

### Intentionally not stored

CORE-specific subclasses, hidden storage access, Runtime bypasses or global Module data.

---

## 6. OperationDescriptor

### Responsibility

Describe one bounded callable function exposed by one Module without exposing provider
mechanics.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `OperationRef` | 1 |
| `name` | `string` | 1 |
| `purpose` | `string` | 1 |
| `input_schema` | `JsonSchema` | 1 |
| `output_schema` | `JsonSchema` | 1 |
| `visibility` | `VisibilityPolicy` | 1 |
| `side_effects` | `SideEffectKind` | 1 |
| `retry_semantics` | `RetrySemantics` | 1 |
| `security` | `OperationSecurity` | 1 |
| `provenance` | `DefinitionProvenance<OperationRef>` | 1 |

```text
SideEffectKind = NONE | LOCAL_MUTATION | EXTERNAL_EFFECT
RetrySemantics = REPEATABLE | IDEMPOTENT | NOT_SAFE_OR_UNKNOWN
EgressBoundary = NONE | LOCAL | REMOTE

OperationSecurity
  required_caller_clearance: SecurityLabel
  maximum_input_label: SecurityLabel
  egress: EgressBoundary
  may_lower_classification: bool = false
```

### Identity/reference semantics

Operation identity is `(module, operation_id, revision)`. Any incompatible change to
input/output, security or effect semantics publishes a new revision. This is slightly
stricter than the minimum stated only for Skill/Workflow revisions, but is required to
avoid a pinned Workflow silently invoking a changed contract.

### Persistence

Published descriptor revisions are immutable and retained at least as long as a
retained WorkPlan or AgentDefinition references them.

### Ownership/authority

Owning Module defines the Operation contract and implements it. Kernel validates caller
visibility, explicit grant, context flow and current policy before invocation.

### Invariants

- Skill or Workflow references to an Operation never grant invocation authority.
- `NOT_SAFE_OR_UNKNOWN` work is never blindly retried after uncertain external effect.
- A lower-classification output derived from higher-classification input is valid only
  when `may_lower_classification` is true **and** current Kernel policy authorizes that
  exact transformation.
- Provider-specific HTTP/MCP/script details are adapter state, not Agent-visible schema.

### Intentionally not stored

Runtime WorkRecord state, Capability identity, arbitrary implementation configuration or
Module database identifiers.

---

## 7. SkillDefinition

### Responsibility

Publish one reusable, composable Agent ability package.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `SkillRef` | 1 |
| `name` | `string` | 1 |
| `purpose` | `string` | 1 |
| `description` | `string` | 1 |
| `instructions` | `string[]` | 0..N |
| `harness_fragments` | `string[]` | 0..N |
| `operation_requirements` | `OperationRequirement[]` | 0..N |
| `context_expectations` | `ContextExpectation[]` | 0..N |
| `expected_artifacts` | `ArtifactConvention[]` | 0..N |
| `workflow_refs` | `WorkflowRef[]` | 0..N |
| `visibility` | `VisibilityPolicy` | 1 |
| `required_security` | `SecurityLabel` | 1 |
| `provenance` | `DefinitionProvenance<SkillRef>` | 1 |

```text
OperationRequirement
  operation: OperationRef
  required: bool

ContextExpectation
  purpose: string
  schema: JsonSchema | null
  minimum_label: SecurityLabel | null

ArtifactConvention
  kind: string
  description: string
```

### Identity/reference semantics

`SkillRef` pins an exact revision. A customized Skill receives a new Module-scoped
`skill_id`, starts at revision 1 and points `derived_from` at its upstream revision.

User-authored Skills are persisted by the Module responsible for that user's definition
store, normally the standard CORE Module in the first implementation. User authorship is
preserved in provenance; it does not require a new global ownership species.

### Persistence

Published revisions are immutable and retained while referenced by AgentDefinitions,
Workflows under review, AgentTasks or retained WorkPlans.

### Ownership/authority

The `ModuleRef` in the Skill identity is the publishing/management authority. Other
Agents may attach the Skill without acquiring that Module's domain ownership.

### Invariants

- `workflow_refs` may be empty.
- Referenced Operations are semantic requirements/recommendations, not grants.
- Updating an upstream Skill creates a new revision; existing AgentDefinitions remain
  pinned.
- Derivation never mutates upstream history.

### Intentionally not stored

Agent working memory, live task state, implicit Module storage access or autonomous
learning/promotion state.

---

## 8. WorkflowDefinition

### Responsibility

Publish one explicit reusable recipe without introducing a generic workflow DSL.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `WorkflowRef` | 1 |
| `name` | `string` | 1 |
| `purpose` | `string` | 1 |
| `expected_input` | `JsonSchema | null` | 0..1 |
| `expected_output` | `JsonSchema | null` | 0..1 |
| `instructions` | `string` | 1 |
| `operation_refs` | `OperationRef[]` | 0..N |
| `delegation_requirements` | `AgentRequirement[]` | 0..N |
| `expected_artifacts` | `ArtifactConvention[]` | 0..N |
| `visibility` | `VisibilityPolicy` | 1 |
| `required_security` | `SecurityLabel` | 1 |
| `provenance` | `DefinitionProvenance<WorkflowRef>` | 1 |

`instructions` is deliberately an opaque validated text recipe in the first schema. A
structured DSL should be introduced only after real workflows demonstrate a stable
structure worth encoding.

### Identity/reference semantics

Workflow identity is independent of Skill and Agent membership. A Skill contributes a
Workflow by placing its `WorkflowRef` in `workflow_refs`; an AgentDefinition directly
attaches one through `direct_workflow_refs`.

A direct private user Workflow therefore needs no fake Skill. It is simply a private
WorkflowDefinition revision managed by the responsible definition Module and referenced
only by the desired AgentDefinition.

### Persistence

Same immutable-revision rules as SkillDefinition.

### Ownership/authority

Publishing Module owns the definition record. An Agent using the Workflow does not gain
authority over the publishing Module or referenced Operations.

### Invariants

- No exclusive parent field.
- No reverse `skill_id` or `agent_id` pointer.
- Referenced Operations still require explicit task grants and Kernel authorization.
- A derived custom Workflow has a new identity and provenance link.

### Intentionally not stored

Live execution graph, Runtime work IDs, task state, learned success scores or hidden
planner state.

---

## 9. AgentDefinition

### Responsibility

Publish reusable Agent configuration that can be instantiated concurrently.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `AgentDefinitionRef` | 1 |
| `name` | `string` | 1 |
| `description` | `string` | 1 |
| `base_instructions` | `string[]` | 0..N |
| `skill_refs` | `SkillRef[]` | 0..N |
| `direct_workflow_refs` | `WorkflowRef[]` | 0..N |
| `operation_scope` | `OperationScope` | 1 |
| `security` | `SecurityProfile` | 1 |
| `instance_policy` | `InstancePolicy` | 1 |
| `resolution_tags` | `set[string]` | 0..N |
| `visibility` | `VisibilityPolicy` | 1 |
| `provenance` | `DefinitionProvenance<AgentDefinitionRef>` | 1 |

```text
OperationScope
  allowed_modules: set[ModuleRef] | null
  allowed_operations: set[OperationRef] | null

InstancePolicy
  durable_checkpoint_while_active: bool
  retention_after_close: Duration | null
```

`OperationScope` is a **ceiling**, not an authorization source. `null` means this
AgentDefinition imposes no additional restriction on that dimension. Actual Operations
must still be explicitly granted to the AgentTask and allowed by Kernel policy.

### Identity/reference semantics

`AgentDefinitionRef.module` is the responsible Module. The exact revision is pinned in
AgentTasks/WorkPlans once resolved. A customized Agent receives a new identity and
`derived_from` link.

### Persistence

Published revisions are immutable. Definition revisions needed by retained WorkPlans are
kept resolvable even if no longer advertised.

### Ownership/authority

Responsible Module manages the AgentDefinition. That responsibility does not imply
implicit access to the Module's domain state.

### Invariants

- Many AgentInstances may reference the same definition revision concurrently.
- Skill and Workflow revisions are explicit.
- Skills do not grant Operations.
- The definition cannot widen Module or installation security policy.
- Persistence/persona behavior is policy data, not an Agent subclass.

### Intentionally not stored

Current conversation/task context, current working memory, WorkPlan state, Runtime work
or Module database data.

---

## 10. AgentRequirement

`AgentRequirement` is a value object used by AgentTask and Workflow delegation; it is not
a persisted Agent species.

```text
AgentRequirement
  preferred_definition: AgentDefinitionRef | null
  required_tags: set[string]
  required_skills: set[SkillRef]
  minimum_clearance: SecurityLabel
```

The Kernel resolves this only against definitions visible and permitted to the current
principal/context. Persisting the requirement separately from the resolved binding is
important for the missing-Agent case: a future resolver can explain what was required
without silently substituting a different actor.

---

## 11. AgentInstance

### Responsibility

Represent one actual working reasoning actor and its restart-relevant working state.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `AgentInstanceRef` | 1 |
| `definition` | `AgentDefinitionRef` | 1 |
| `task` | `AgentTaskRef` | 1 in initial implementation |
| `created_at` | `Instant` | 1 |
| `checkpointed_at` | `Instant | null` | 0..1 |
| `working_state` | `JsonValue | null` | 0..1 |
| `working_state_label` | `SecurityLabel | null` | 0..1 |
| `closed_at` | `Instant | null` | 0..1 |

### Identity/reference semantics

Instance identity is installation-scoped and never reused. It points to one exact
AgentDefinition revision.

The initial implementation is task-scoped. Future persistent/persona behavior may
change assignment policy without introducing subclasses; if one instance later serves
multiple tasks, task-instance binding can be normalized into a separate relation without
changing AgentDefinition identity.

### Persistence

If the AgentDefinition's `instance_policy.durable_checkpoint_while_active` is true,
checkpoint state is durable for restart continuity. Closed instances are retained only
as required by plan/research retention; rich persistent persona memory is deferred.

### Ownership/authority

Kernel controls instance lifecycle. The responsible Module owns any future semantic
learning/memory policy. Working state is never authority to access a Module.

### Invariants

- Two instances never share mutable working state implicitly.
- Resuming an instance re-evaluates current security policy; a stale checkpoint cannot
  authorize a newly forbidden flow.
- `working_state_label` must dominate every sensitive value persisted in working state.

### Intentionally not stored

Module domain persistence, global memory ontology, Runtime lifecycle or an inherited
Agent subclass type.

---

## 12. ContextBundle

### Responsibility

Carry one immutable bounded semantic payload across a Module/Agent/Operation boundary
with enough metadata for deterministic information-flow enforcement.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `ContextBundleRef` | 1 |
| `owner_module` | `ModuleRef` | 1 |
| `purpose` | `string` | 1 |
| `payload` | `JsonValue` | 1 |
| `label` | `SecurityLabel` | 1 |
| `flow` | `FlowConstraints` | 1 |
| `created_at` | `Instant` | 1 |
| `derived_from` | `ContextBundleRef[]` | 0..N |
| `derivation_operation` | `OperationInvocationRef | null` | 0..1 |
| `payload_digest` | `Digest | null` | 0..1 |

Large files remain Module-owned artifacts referenced through bounded payload values; the
first schema does not require a universal blob store.

### Identity/reference semantics

ContextBundle identity is immutable. A transformation always creates a new bundle ID;
it never edits the original classification in place.

### Persistence

Persist while referenced by an active/retained AgentTask or WorkPlan, subject to
security-specific retention. Sensitive bundles may have shorter retention than the plan
that references them; the plan then truthfully retains an unresolved/expired reference.

### Ownership/authority

`owner_module` is authoritative for the bundle it issued. Kernel brokers flow. The
consumer cannot reclassify it.

### Invariants

- Direct transfer requires destination clearance, visibility/authorization, flow
  constraints and egress policy all to permit it.
- If an output label is lower than any input label, `derivation_operation` is required,
  that Operation revision must allow classification lowering, and current Kernel policy
  must authorize the transformation.
- `derived_from` preserves provenance across minimization/anonymization/declassification.
- Prompt/model output alone can never create a lower-security authorization fact.

### Intentionally not stored

Universal truth/knowledge type, domain database row ownership, implicit Agent memory or
Runtime execution state.

---

## 13. WorkPlan

### Responsibility

Persist semantic continuity for one objective across foreground interaction, delayed
execution and restart.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `WorkPlanRef` | 1 |
| `objective` | `string` | 1 |
| `objective_label` | `SecurityLabel` | 1 |
| `origin` | `PlanOrigin` | 1 |
| `orchestrator_requirement` | `AgentRequirement | null` | 0..1 |
| `orchestrator_definition` | `AgentDefinitionRef | null` | 0..1 |
| `created_at` | `Instant` | 1 |
| `updated_at` | `Instant` | 1 |
| `completion` | `PlanCompletion | null` | 0..1 |
| `termination` | `PlanTermination | null` | 0..1 |
| `retention_until` | `Instant | null` | 0..1 |
| `derived_from` | `WorkPlanRef | null` | 0..1 |

```text
PlanOrigin = one of:
  OwnerPlanOrigin(source_ref: string | null)
  ModulePlanOrigin(module: ModuleRef, source_ref: string | null)
  AgentTaskPlanOrigin(task: AgentTaskRef)

PlanCompletion
  completed_at: Instant
  summary: string | null
  artifacts: ModuleArtifactRef[]
  context_outputs: ContextBundleRef[]

PlanTermination
  terminated_at: Instant
  reason: string
```

### Identity/reference semantics

Plan ID is immutable and never reused. Replay/clone allocates a new plan and sets
`derived_from`; it never modifies prior historical evidence.

AgentTasks are not embedded in the persisted WorkPlan value. They are rows/documents
owned by the plan and selected by `AgentTaskRef.plan`.

### Persistence

Durable Kernel/WorkPlan store. Active plans survive process restart. Closed plans remain
for configurable inspection/replay retention.

### Ownership/authority

MADRE Kernel owns orchestration persistence. Modules retain authority over their domain
state and artifacts referenced by the plan.

### Invariants

- No semantic `pending/running/blocked/failed` status.
- Open plan is derived from absence of `completion` and `termination`.
- At most one of `completion` and `termination` is present.
- `orchestrator_definition`, once resolved, is an exact revision and is never silently
  rewritten if it later disappears.
- Plan security does not imply domain authority.

### Intentionally not stored

Runtime queue state, copied Module database state, one `work_id`, live graph revision
history or universal user-approval state.

---

## 14. AgentTask

### Responsibility

Persist one objective-specific semantic assignment within a WorkPlan.

### Fields

| Field | Type | Cardinality |
| --- | --- | ---: |
| `ref` | `AgentTaskRef` | 1 |
| `parent_task` | `AgentTaskRef | null` | 0..1 |
| `objective` | `string` | 1 |
| `objective_label` | `SecurityLabel` | 1 |
| `agent_requirement` | `AgentRequirement` | 1 |
| `resolved_agent` | `AgentDefinitionRef | null` | 0..1 |
| `agent_instance` | `AgentInstanceRef | null` | 0..1 |
| `skill_refs` | `SkillRef[]` | 0..N |
| `additional_workflow_refs` | `WorkflowRef[]` | 0..N |
| `selected_workflow` | `WorkflowRef | null` | 0..1 |
| `context_refs` | `ContextBundleRef[]` | 0..N |
| `operation_grants` | `OperationRef[]` | 0..N |
| `prerequisite_tasks` | `AgentTaskRef[]` | 0..N |
| `eligible_at` | `Instant | null` | 0..1 |
| `expected_outputs` | `OutputExpectation[]` | 0..N |
| `created_at` | `Instant` | 1 |
| `completion` | `TaskCompletion | null` | 0..1 |
| `termination` | `TaskTermination | null` | 0..1 |

```text
OutputExpectation
  name: string
  description: string
  schema: JsonSchema | null

TaskCompletion
  completed_at: Instant
  result: JsonValue | null
  context_outputs: ContextBundleRef[]
  artifacts: ModuleArtifactRef[]
  summary: string | null

TaskTermination
  terminated_at: Instant
  reason: string
```

### Identity/reference semantics

Task identity is scoped by WorkPlan. Child delegation creates another ordinary AgentTask
with `parent_task`; no team/group ontology is introduced.

`agent_requirement` is preserved even after `resolved_agent` is populated. This is
required to diagnose a missing pinned Agent later and to offer future substitution
without silently changing history.

### Effective Agent environment

At execution time the Kernel derives:

```text
pinned AgentDefinition
+ AgentDefinition Skill refs
+ AgentTask Skill refs
+ direct Agent Workflow refs
+ Workflows contributed by effective Skills
+ AgentTask additional Workflow refs
+ selected Workflow (if any)
+ AgentTask ContextBundles
+ AgentTask explicit Operation grants
+ current Kernel policy
= AgentInstance environment
```

An Operation is callable only if it is in `operation_grants`, allowed by the
AgentDefinition's `operation_scope`, visible, compatible with all involved security
labels and permitted by current Kernel policy.

### Readiness

Readiness is computed, not persisted as lifecycle state. Minimum initial computation:

```text
not completed or terminated
AND every prerequisite task has TaskCompletion
AND eligible_at is null or has arrived
AND resolved/potential AgentDefinition is visible and permitted
AND every required ContextBundle can legally flow
AND selected Workflow/Skills resolve
AND required Operations are available and permitted
```

A user decision is not a universal state. A future concrete requirement may add another
typed prerequisite without changing the task lifecycle model.

### Persistence

Durable with its WorkPlan. AgentTask records survive restart regardless of whether their
AgentInstance or Runtime work is currently present.

### Ownership/authority

Kernel owns task orchestration. Modules own the meaning/effects of Operations and
artifacts used by the task.

### Invariants

- No 1:1 Workflow relation: `selected_workflow` is optional.
- No `work_id` field.
- No embedded child-task list; children are queried by `parent_task`.
- At most one of `completion` and `termination` is present.
- Operation/Runtime failures may be recorded as evidence without forcing a canonical
  semantic `failed` lifecycle state.

### Intentionally not stored

Runtime lifecycle, Module domain rows, implicit Operation authority or global blocking
reason enum.

---

## 15. OperationInvocationRecord

This is a Kernel trace record, not a new callable architectural species. It is justified
by three current requirements: several Operations may occur inside one AgentTask,
external side effects need truthful evidence, and a ContextBundle classification-lowering
transformation needs deterministic provenance.

### Fields

```text
OperationInvocationRecord
  ref: OperationInvocationRef
  task: AgentTaskRef
  operation: OperationRef
  requested_by: AgentInstanceRef
  requested_at: Instant
  input_contexts: ContextBundleRef[]
  input_digest: Digest | null
  outcome: OperationOutcome | null
  produced_contexts: ContextBundleRef[]
  artifacts: ModuleArtifactRef[]

OperationOutcome = one of:
  OperationSuccess(completed_at: Instant, summary: string | null)
  OperationFailure(completed_at: Instant, code: string, message: string)
  UnknownExternalEffect(observed_at: Instant, message: string)
```

Raw sensitive inputs need not be duplicated in trace storage; ContextBundle references
and an optional digest are sufficient for the first implementation.

### Persistence/authority

Kernel trace store. Append/close semantics only; an observed unknown external effect is
never rewritten into success without new evidence.

### Intentionally not stored

Runtime scheduler state or provider-specific execution details.

---

## 16. RuntimeCorrelation

### Responsibility

Associate semantic orchestration with existing Runtime evidence without making Runtime
aware of WorkPlan/Agent semantics.

### Fields

```text
RuntimeCorrelation
  correlation_id: string
  task: AgentTaskRef
  work: RuntimeWorkRef
  operation_invocation: OperationInvocationRef | null
  purpose: string
  created_at: Instant
```

Each relation is one row. Therefore:

```text
one AgentTask -> 0..N RuntimeCorrelation -> 0..N WorkRecord
one WorkRecord -> 1..N correlations if a future shared execution legitimately serves
                  more than one semantic consumer
```

The initial implementation should normally create one semantic consumer per work item;
the schema does not encode that as an invariant.

### Persistence/authority

Kernel owns correlations. Runtime continues to persist only `WorkSubmission`,
`WorkRecord`, attempts, retry/cancellation and Capability evidence.

The current Runtime's request-level `Idempotency-Key` should be used whenever Kernel may
need to repeat a submission after transport uncertainty. Kernel correlation identity and
Runtime idempotency are related operationally but are not the same entity.

Before non-repeatable external Operation execution is delegated through Runtime, the
implementation must ensure correlation creation and idempotent submission can be
reconciled after a Kernel crash. The existing synchronous submit response is sufficient
for the first local agent-environment slice; stronger cross-store reconciliation is an
implementation requirement for later durable side-effecting Runtime materialization.

### Invariants

- `RuntimeCorrelation` is never embedded into `WorkRecord`.
- Runtime does not inspect `task`, Workflow, Skill or Module semantics.
- An AgentTask never derives semantic success solely from the existence of one Runtime
  WorkRecord; its AgenticLoop may require more reasoning, Operations or child tasks.

---

## 17. Ownership and persistence summary

| Schema | Semantic owner/authority | Persistence | Mutability |
| --- | --- | --- | --- |
| `ModuleRef` | Kernel installation registry | durable | identity stable |
| `ModuleManifest` | Module content; Kernel registration | durable current + optional history | publish new manifest revision |
| `CoreRoleAssignment` | Kernel/Owner policy | durable singleton | explicit reassignment only |
| `OperationDescriptor` | owning Module | durable while referenced | immutable revision |
| `SkillDefinition` | publishing Module | durable while referenced | immutable revision |
| `WorkflowDefinition` | publishing Module | durable while referenced | immutable revision |
| `AgentDefinition` | responsible Module | durable while referenced | immutable revision |
| `AgentInstance` | Kernel lifecycle; Module owns future memory policy | durable while active when policy requires | checkpointed working state |
| `ContextBundle` | issuing Module; Kernel brokers | durable while referenced/allowed | immutable |
| `WorkPlan` | Kernel orchestration store | durable + retention | append facts/close; no history rewrite |
| `AgentTask` | Kernel orchestration store | durable with plan | append bindings/evidence/close |
| `OperationInvocationRecord` | Kernel trace store | durable with plan/evidence retention | append/close |
| `RuntimeCorrelation` | Kernel correlation store | durable with plan/evidence retention | append-only |
| `WorkSubmission` / `WorkRecord` | existing Runtime | existing durable Runtime store | existing Runtime semantics |
| `ModuleArtifactRef` target | owning Module | Module-defined | Module-defined |

Physical tables/files/processes may differ from these ownership boundaries.

---

## 18. Security and reference rules

### 18.1 Discovery

Discovery is always a Kernel query under a subject. There is no globally readable Module,
Agent, Skill or Operation registry.

For any advertised child definition:

```text
effective_visibility =
    ModuleManifest.visibility
    INTERSECT child.visibility
    INTERSECT current Kernel installation policy
```

CORE role does not imply visibility unless current policy permits it.

### 18.2 Context flow

For ContextBundle `C` to reach destination `D`, all of these must be true:

1. `D` is authorized to participate in the current task.
2. `D`'s effective clearance covers `C.label`.
3. `C.flow.allowed_destinations`, if present, includes `D`.
4. If an Operation is the destination, `C.flow.allowed_operations`, if present, includes
   that Operation revision.
5. Remote transfer is denied unless both `C.flow.remote_egress_allowed` and the Operation
   / Capability boundary policy allow it.
6. Current Kernel policy independently permits the flow.

No prompt text can override these checks.

### 18.3 Explicit lowering of sensitivity

A lower-sensitivity derived ContextBundle must be a new immutable bundle with:

```text
derived_from = [source bundle refs]
derivation_operation = exact authorized OperationInvocationRef
```

and the invoked OperationDescriptor must have `security.may_lower_classification = true`.
Kernel policy still decides whether the requested output label is acceptable. This is the
minimum seam for minimization, anonymization and declassification without designing the
final security framework.

### 18.4 Operation authority

Operation availability, Skill mention and Workflow mention are not authority.

```text
callable operation =
  explicit AgentTask.operation_grants
  INTERSECT AgentDefinition.operation_scope
  INTERSECT discovery/visibility
  INTERSECT context-flow legality
  INTERSECT current Kernel policy
```

### 18.5 Reference resolution

Exact semantic references are never silently floated to a later revision.

- Latest advertised revision is a discovery convenience only.
- Persisted Agents/Tasks/Plans use exact refs.
- If an exact ref no longer resolves, the reference remains stored and becomes explicit
  missing-binding evidence.
- A future substitution feature may use the preserved `AgentRequirement` and provenance,
  but must create an explicit new binding decision.

---

## 19. Lifecycle and immutability rules

### Definitions

`OperationDescriptor`, `SkillDefinition`, `WorkflowDefinition` and `AgentDefinition` are
immutable after publication. Change means a new revision. Derivation means a new identity
plus provenance.

### ContextBundle

Immutable after creation. Any projection/transformation produces another bundle.

### AgentInstance

Mutable only through explicit checkpoint/close operations. Its pinned definition never
changes in place.

### WorkPlan

A WorkPlan accumulates durable orchestration facts. It has no universal status enum.
Completion/termination are terminal facts. Replay/clone creates a new plan with
`derived_from`.

### AgentTask

Task readiness is derived. Agent resolution and instance binding may be populated later,
but an already-recorded exact resolved definition is not silently replaced. Completion
or termination closes the semantic assignment.

### OperationInvocationRecord and RuntimeCorrelation

Evidence is append-only except for closing an invocation with observed outcome. Runtime
work retains its existing independent lifecycle.

---

## 20. Validation against the ten architecture tests

### A — Agentless calculator Module

`CalcModule` registers a manifest with one `OperationRef` and zero Agents. A calculator
AgentTask carries an `AgentRequirement` that resolves through `CoreRoleAssignment` to a
CORE fallback `AgentDefinitionRef`. The task explicitly grants `CalcModule.calculate`.
The resulting AgentInstance can invoke the Operation through the ordinary gateway. No
`NoAgent`, Tool or non-agentic orchestration class is required.

### B — AAAAT Skill portability

AAAAT publishes an immutable `SkillRef` plus its referenced Operations and no native
Agent. A CORE AgentDefinition can receive the pinned AAAAT Skill on an AgentTask together
with AAAAT ContextBundles and explicit AAAAT Operation grants. Later, a user-defined
AAAAT AgentDefinition can attach the same SkillRef. Skill provider, Agent owner and
Operation provider remain separate authorities.

### C — Direct custom Workflow

A private WorkflowDefinition is published in the responsible user's definition Module
with a private `VisibilityPolicy`. The desired AgentDefinition puts that exact
`WorkflowRef` in `direct_workflow_refs`. No Skill container is created and the Workflow
has no fake parent field.

### D — Skill update stability

Agent A stores `SkillRef(AAAAT, skill, revision=3)`. Publishing revision 4 changes only
what discovery advertises as current. Agent A continues resolving revision 3. Upgrade is
an explicit new AgentDefinition revision or derived Skill/Agent publication.

### E — Concurrent planner instances

One pinned planner AgentDefinition can produce Instance A/B/C with different
`AgentInstanceRef`, AgentTask, ContextBundle refs and independent checkpoint state. No
mutable state lives in AgentDefinition.

### F — Long-running WorkPlan

WorkPlan and AgentTask rows are Kernel-durable. `eligible_at` is stored on scheduled
AgentTasks independently of Runtime work. AgentInstance checkpoints can be durable while
active. After restart, Kernel reconstructs incomplete tasks from facts and re-evaluates
readiness/current policy. Runtime durability remains separate and correlations reconnect
semantic tasks to physical work evidence.

### G — Multi-runtime AgentTask

AgentTask has no `work_id`. Each Runtime materialization adds another
`RuntimeCorrelation`; Operation calls have independent `OperationInvocationRecord`s; child
delegation creates ordinary child AgentTasks. The sequence `reason -> Operation -> reason
-> Runtime work -> child AgentTask -> more Runtime work` fits without changing task
identity.

### H — Cross-domain privacy

A private calendar ContextBundle carries a high SecurityLabel and flow constraints, so a
direct AAAAT Agent transfer fails clearance/flow checks. An authorized minimization
Operation with `may_lower_classification=true` creates a **new** lower-label ContextBundle
whose `derived_from` and `derivation_operation` identify the exact source/transformation.
Only the derived bundle may then flow to AAAAT if policy permits.

### I — Private discovery

ModuleManifest and every advertised Operation/Skill/Agent definition carry visibility
policy. Kernel discovery is subject-scoped and intersects Module, entity and installation
policy. An installed Module can therefore be completely absent from an unauthorized
principal's discovery projection.

### J — Missing Agent later

WorkPlan/AgentTask persist the exact `AgentDefinitionRef` **and** the original
`AgentRequirement`. If the definition disappears, Kernel detects an unresolved pinned
binding. Provenance and requirements remain available for a future equivalent/similar
search or Owner choice. No replacement revision is silently substituted.

---

## 21. Explicitly deferred questions

These are genuine later design questions, not missing fields accidentally hidden by the
schema.

1. **Cross-installation package identity/export.** Module-scoped references are enough for
   one installation. Portable signed packages may later need publisher/package identity
   distinct from installed `ModuleRef`.
2. **Rich policy language.** The first security lattice, compartments and allowlists are
   intentionally small. Delegation of policy administration, revocation and richer
   mandatory-access-control rules are deferred.
3. **Persistent persona memory.** `InstancePolicy` only covers active checkpointing and
   retention. Cross-task semantic memory remains Module-owned future behavior.
4. **Live plan graph revision.** Tasks may be added durably, but there is no first-class
   active-plan version/patch graph.
5. **Agent equivalence/substitution.** Requirements/provenance make it possible later;
   similarity scoring and automated replacement are not defined.
6. **Autonomous Workflow/Skill learning.** No promotion/evaluation machinery exists.
7. **Workflow DSL.** Workflow instructions remain opaque recipe content until real
   reusable workflows justify stronger structure.
8. **Artifact schema.** Artifacts remain Module-owned opaque references because current
   architecture does not require a universal artifact lifecycle.
9. **General prerequisite algebra.** Initial task prerequisites are prior-task completion
   plus eligibility time. User decisions or other gates should become typed requirements
   only when a concrete objective needs them.
10. **Cross-store Runtime submission reconciliation.** Before non-repeatable external
    effects rely on Runtime materialization, Kernel correlation + Runtime idempotency must
    be made crash-reconcilable. This does not require Runtime to understand AgentTask.
11. **Operation contract retirement.** Retention rules for old Operation revisions after
    all referring plans expire need an implementation policy.
12. **AgentInstance reuse across tasks.** Initial instances are task-scoped. A future
    persistent Agent may normalize task-instance bindings if reuse becomes real behavior.

---

## 22. Recommended first implementation slice after schema approval

Implement one coherent **Kernel agent-environment bootstrap** slice, not the full durable
planner yet.

The slice should implement:

1. typed reference/value models and validation for `ModuleManifest`, security values,
   `OperationDescriptor`, `SkillDefinition`, `WorkflowDefinition`, `AgentDefinition`,
   `AgentInstance` and `ContextBundle`;
2. a Kernel-owned Module/definition registry with exact-revision lookup and
   policy-filtered discovery;
3. persisted `CoreRoleAssignment` and deterministic fallback AgentDefinition resolution;
4. AgentInstance composition from one AgentDefinition + task-supplied Skills/Workflows +
   bounded ContextBundles + explicit Operation grants;
5. a Kernel Operation gateway that enforces visibility, OperationScope, security-label
   flow and egress rules before invoking a Module adapter;
6. two concrete fixtures/adapters proving architecture rather than provider mechanics:
   - Agentless `CalcModule` with `calculate` Operation;
   - AAAAT-shaped Module fixture with a Skill and bounded context but zero native Agents;
7. deterministic tests for direct private Workflow attachment, revision pinning,
   concurrent AgentInstances, private discovery and denied/direct-vs-derived context
   flow.

The slice should deliberately **not** yet implement WorkPlan orchestration, AgentTask
scheduling, planner behavior, multi-Agent delegation or Runtime correlations. Those
schemas should land first so the next implementation can use them without redesign, but
the first executable slice should prove the most foundational boundary:

```text
Module contract
  + CORE role
  + exact AgentDefinition resolution
  + AgentInstance composition
  + bounded Context
  + explicit Operation authorization
  + structural security
```

Once that boundary works, the next substantive slice should add the durable WorkPlan /
AgentTask store and restart continuation, then connect AgentTask reasoning to ordinary
Runtime WorkRecords through `RuntimeCorrelation` without changing Runtime semantics.
