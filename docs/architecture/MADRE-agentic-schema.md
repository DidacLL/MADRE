# MADRE agentic schema

Status: **second clean-slate schema proposal for Owner review**

## 1. Authority and scope

This document defines the minimum coherent concrete schema required to begin implementing
the clean-slate MADRE agentic environment.

Its authority order is:

```text
MADRE.md
    product contract

MADRE-agentic-architecture.md
    architectural derivation and rationale

MADRE-agentic-schema-authority.md
    Owner-directed schema corrections and mandatory schema constraints
```

The schema at commit `d2d95c39b60ef6cdae4a6075763ae3e8fe98d823` is design
history only. Sound decisions are retained where compatible with the authorities; rejected
ACL/grant semantics, universal Agent-state assumptions, direct Skill attachment and
CORE-default configuration ownership are not retained.

No contradiction in the three current authorities requires changing them. This document
therefore changes only the concrete schema.

The schema is language-neutral. It deliberately specifies semantic types, references,
ownership and persistence rather than Python/JVM class layouts or database tables.

The governing ownership invariant remains:

```text
Module
    owns domain semantics, Scope meaning, domain material classification,
    Operations and any Agent system it manages

AgentInstance
    performs semantic/intelligence work from bounded material

MADRE Kernel
    resolves, coordinates, persists semantic orchestration and mechanically
    evaluates/enforces security crossings

MADRE Runtime
    schedules and durably executes physical WorkRecords

Capability
    performs bounded computation
```

There is no canonical `Tool`, `Routine`, `WorkPlanStep`, Agent subclass hierarchy,
universal Agent session/memory model, universal Workflow DSL or global knowledge/learning
ontology.

---

## 2. Entity and reference map

```text
                              MADRE Kernel installation
                                      |
                                      +-- CoreRoleAssignment --> ModuleRef
                                      |
                                      +-- policy-filtered Module registry
                                              |
                                              v
                                         ModuleManifest
                                     /       |        \
                                    /        |         \
                                   v         v          v
                         OperationDescriptor SkillDefinition AgentDefinition
                              |                    |             |
                              |                    |             +-- manager_definition_ref
                              |                    |             |       (opaque to Kernel)
                              |                    |             |
                              |                    |             +-- AgentSkillInstance[]
                              |                    |             |      |
                              |                    |             |      +-- exact SkillRef
                              |                    |             |      +-- adopted WorkflowRef[]
                              |                    |             |
                              |                    |             +-- direct WorkflowRef[]
                              |                    |
                              |                    +-- contributed WorkflowRef[]
                              |
                              +---------------------------> WorkflowDefinition

Module / Agent manager --typed bounded projection--> ContextBundle
                                                        |
                                                        +-- TypedPayload<SchemaRef>
                                                        |       or ModuleResourceRef
                                                        +-- DataSecurityFacts
                                                        +-- ScopeRef[]
                                                        +-- derivation provenance

AgentDefinition --instantiate through responsible Module--> AgentInstance
                                                          |
                                                          +-- manager_instance_ref
                                                          +-- 0..N AgentStateRef

WorkPlan
  |
  +-- durable AgentTask
          |
          +-- AgentRequirement
          +-- exact resolved AgentDefinitionRef?
          +-- AgentInstanceRef?
          +-- AgentSkillInstanceRef[]
          +-- Workflow requirements / selection
          +-- ContextBundleRef[]
          +-- OperationRequirement[]       # need, never authority
          +-- prerequisite AgentTaskRef[]
          +-- child AgentTasks via parent_task
          |
          +-- OperationInvocationRecord[]
          |       |
          |       +-- SecurityDecisionEvidence
          |       +-- produced ContextBundleRef[]
          |
          +-- RuntimeEvidenceLink[] ------> Runtime WorkRecord
                                               |
                                               +-- Runtime attempt/retry/cancel evidence
                                               +-- Capability invocation
```

The semantic and physical layers are deliberately not collapsed:

```text
AgentTask != WorkflowDefinition
AgentTask != Runtime WorkRecord
Operation != Capability
WorkPlan != Runtime queue
```

---

## 3. Common scalar and reference types

### 3.1 Scalar conventions

```text
OpaqueId        non-empty opaque identifier; callers do not infer meaning from its text
PositiveInt     integer >= 1
Instant         timezone-aware persisted timestamp
Digest          algorithm-qualified content digest
```

Serialized data may use JSON, CBOR, SQL columns or another representation. Serialization
format is not the semantic type system.

Persisted records SHOULD carry an implementation schema envelope such as:

```text
StoredRecord<T>
    schema_version: RecordSchemaVersion
    value: T
```

Record serialization version is independent from a published semantic definition's
`revision`.

### 3.2 Installation-scoped and Module-scoped references

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

AgentDefinitionRef
    module: ModuleRef
    agent_id: OpaqueId
    revision: PositiveInt

AgentSkillInstanceRef
    agent_definition: AgentDefinitionRef
    skill_instance_id: OpaqueId

AgentInstanceRef
    instance_id: OpaqueId

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
    work_id: OpaqueId             # existing Runtime WorkRecord.id

ModuleArtifactRef
    module: ModuleRef
    artifact_id: OpaqueId
```

The same raw identifier string in two reference types is not interchangeable. Internal
APIs SHOULD use distinct value types/newtypes rather than passing naked strings.

### 3.3 Opaque Agent-manager references

These references intentionally expose identity only. Kernel never interprets the target.

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

`ModuleAgentDefinitionRef.module`, `ModuleAgentInstanceRef.module` and every
`AgentStateRef.module` on an Agent MUST equal the responsible Module in its
`AgentDefinitionRef`.

Kernel does not know whether an Agent state is a conversation, state machine, model
session, task state, persistent memory, several concurrent works or something else.

### 3.4 Resource references used by Skills and Workflows

```text
SkillResourceRef
    module: ModuleRef
    resource_id: OpaqueId
    revision: PositiveInt

WorkflowRecipeRef
    module: ModuleRef
    recipe_id: OpaqueId
    revision: PositiveInt
```

A resource referenced by an immutable definition MUST itself resolve immutably at that
exact revision or be content-addressed equivalently. A Module update cannot mutate recipe
or Skill material behind a pinned reference.

### 3.5 Discovery and intended-use references

Discovery privacy is policy-driven but is not an Operation authorization system.

```text
DiscoveryPolicyRef
    policy_id: OpaqueId
    revision: PositiveInt

IntendedUseRef
    module: ModuleRef
    use_id: OpaqueId
```

`DiscoveryPolicyRef` identifies an installation policy rule evaluated before descriptor
material is exposed. It is not an ACL embedded in WorkPlan state and does not imply that a
discovered Operation is callable.

`IntendedUseRef` is optional semantic input to security evaluation. Its meaning is owned
by the referenced Module; it is not currently another normalized numeric dimension.

### 3.6 Definition provenance

```text
ProvenanceSubjectRef = one of:
    InstallationOwnerRef
    ModuleRef
    AgentDefinitionRef

DefinitionProvenance<R>
    created_at: Instant
    created_by: ProvenanceSubjectRef
    derived_from: R | null
    source_resources: ModuleResourceRef[]
```

`derived_from` is same-kind for published definitions. Derivation creates a new identity;
it never edits the upstream revision.

---

## 4. Normalized security facts and `SecurityAlgebra`

### 4.1 Normalized levels

```text
SecurityLevel
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5
```

`SYSTEM_RESERVED` is a Kernel/system sentinel outside the ordinary scale. It is not an
ordinary selectable value and MUST NOT be interpreted as merely weaker/safer than
`LEVEL_1`.

All Module- and Agent-declared ordinary facts use:

```text
OrdinarySecurityLevel = LEVEL_1 | LEVEL_2 | LEVEL_3 | LEVEL_4 | LEVEL_5
```

The normalized dimensions are independent:

```text
sensitivity
    higher => material is more sensitive

trust
    higher => material/actor is more trusted

risk
    higher => actor/operation/execution presents more risk
```

They are never summed, averaged or collapsed into one score. The shared 1..5 vocabulary
exists only to make dimension-specific comparisons and diagnostics simple and stable.

### 4.2 Scope is semantic, not numeric

`ScopeRef` is independent from normalized levels.

A Module owns the meaning of each Scope it publishes. Kernel does not infer Scope from
payload text and does not decide that two scopes are semantically equivalent because their
names look similar.

For the initial schema, an Operation explicitly declares the exact source and destination
ScopeRefs it is designed to cross. A later richer Module-provided scope-relation mechanism
may extend exact matching without changing `ScopeRef`.

### 4.3 Security fact value objects

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

These are facts, not credentials.

Responsibility is structural:

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

No Agent output, Workflow, WorkPlan or prompt can manufacture authority by asserting that
a crossing is permitted.

### 4.4 Security evaluation boundary

Conceptually:

```text
SecurityDecision = SecurityAlgebra.evaluate(
    material,
    source_scope,
    agent,
    operation,
    destination,
    intended_use,
    current_kernel_policy,
)
```

The exact implementation is one deterministic Kernel component with pure, separately
tested predicates. It receives current immutable descriptor facts plus current Kernel
policy. It does not parse semantic payload text.

The initial predicate families are:

| Dimension/boundary | Deterministic relation |
| --- | --- |
| Reserved level | Ordinary Module/Agent facts containing `SYSTEM_RESERVED` are rejected as malformed/security-invalid. |
| Material sensitivity -> Agent | Every provided material sensitivity MUST be `<= agent.maximum_handled_sensitivity`. |
| Material sensitivity -> Operation | Every provided material sensitivity MUST be `<= operation.maximum_input_sensitivity`. |
| Material trust -> Operation | Every provided material trust MUST be `>= operation.minimum_input_trust`. |
| Agent trust | Agent trust MUST satisfy the current Kernel-policy minimum for the actual sensitivity, Operation risk, destination boundary and intended use. |
| Agent execution risk | Agent execution risk MUST be `<=` the current Kernel-policy maximum for the actual sensitivity, destination and intended use. |
| Operation risk | Operation risk MUST be `<=` the current Kernel-policy maximum for the destination boundary and intended use. |
| Source Scope | Every Scope carried by material sent to an Operation MUST be accepted by `operation.source_scopes` under exact matching in the initial model, plus any stricter current policy. |
| Destination Scope | Any produced/crossed destination Scope MUST be declared in `operation.destination_scopes` and allowed by current policy. |
| Execution boundary | The actual boundary MUST equal the declared Operation boundary and be permitted by current policy for the actual material. Remote is never inferred from availability alone. |
| Intended use | When an `IntendedUseRef` is supplied/required, current policy MUST permit that use for the material/Agent/Operation combination. |
| Reclassification | Lower sensitivity than source material requires an explicit Module-owned transformation Operation with `MAY_RECALCULATE`; the new material is evaluated again from its own facts. |

Policy functions may use one dimension to select the threshold for another, for example a
higher-sensitivity crossing may require a higher Agent-trust minimum. That remains a
lookup/relational rule, not arithmetic aggregation of trust, risk and sensitivity.

The initial implementation SHOULD expose these policy queries as explicit typed methods,
for example:

```text
minimum_agent_trust(material_sensitivity, operation_risk, boundary, intended_use)
maximum_agent_execution_risk(material_sensitivity, boundary, intended_use)
maximum_operation_risk(boundary, intended_use)
remote_boundary_permitted(material_facts, intended_use)
```

The complete future policy language is deliberately deferred.

### 4.5 Structured decision evidence

```text
SecurityDeficitDimension
    RESERVED_LEVEL
    SENSITIVITY
    TRUST
    RISK
    SCOPE
    EXECUTION_BOUNDARY
    INTENDED_USE
    CLASSIFICATION_TRANSFORM
    POLICY

SecurityDeficit
    dimension: SecurityDeficitDimension
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

`SecurityDecisionEvidence` is persisted Kernel evidence. `accepted=true` is **not** an
authorization state or reusable credential. Every later crossing is re-evaluated from the
current facts and current policy.

#### Responsibility

Explain one concrete Kernel security evaluation.

#### Identity

`SecurityDecisionRef` is installation-scoped and never reused.

#### Ownership

Kernel owns the evidence; Modules own the semantic facts supplied to the evaluator.

#### Persistence

Retained with the corresponding Operation/Runtime/WorkPlan evidence according to
security-aware retention policy.

#### Mutability

Immutable after evaluation.

#### Invariants

- `accepted=true` implies `deficits` is empty.
- A decision cannot be replayed as permission for another crossing.
- The decision references the actual ContextBundles supplied, not inaccessible upstream
  domain records.

#### Explicit non-responsibilities

- issuing permissions or tokens;
- changing Module classification;
- selecting semantic Scope based on payload text;
- becoming AgentTask lifecycle state.

### 4.6 Context minimization and recalculation

Security attaches to the material actually crossing a boundary.

```text
private source ContextBundle
        |
        | Module-owned minimization Operation
        v
OperationInvocationRecord
        |
        v
new ContextBundle
    derived_from = [source]
    derivation_operation = exact OperationInvocationRef
    security = Module-recalculated DataSecurityFacts for actual output
```

The original ContextBundle is never relabeled. Kernel verifies that the Operation
contract allows recalculation and then evaluates the derived bundle normally wherever it
is next used.

---

## 5. `ModuleManifest`

### Responsibility

Publish one Module's current MADRE-facing domain/discovery contract without exposing its
internal application model.

### Fields

```text
ModuleManifest
    module: ModuleRef                              1
    revision: PositiveInt                          1
    name: string                                   1
    description: string                            1
    visibility: DiscoveryPolicyRef                 1
    scopes: ScopeDescriptor[]                      0..N
    operations: OperationRef[]                     0..N
    skills: SkillRef[]                             0..N
    agents: AgentDefinitionRef[]                   0..N
    context_offers: ContextOfferDescriptor[]       0..N
```

```text
ScopeDescriptor
    ref: ScopeRef
    name: string
    description: string

ContextOfferDescriptor
    offer_id: OpaqueId
    purpose: string
    scope: ScopeRef
    payload_schema: SchemaRef | null
    visibility: DiscoveryPolicyRef
```

A context offer advertises what can be projected. Actual `DataSecurityFacts` are created
when the Module emits the concrete ContextBundle and therefore are not frozen in the
offer.

### Identity

`ModuleRef` is stable for the same installed semantic Module. Manifest `revision` is a
monotonic publication sequence and is not part of child definition identity.

### Ownership

The Module owns manifest content and Scope meaning. Kernel owns registration and
policy-filtered discovery.

### Persistence

Kernel persists the current accepted manifest and MAY retain historical manifest
revisions for inspection. Exact child definition revisions referenced by retained state
remain resolvable independently of the current manifest.

### Mutability

A published manifest revision is immutable; change publishes another revision.

### Invariants

- Zero Operations, Skills or Agents are valid.
- Every `ScopeDescriptor.ref.module` equals `module`.
- Manifest cannot assign itself CORE.
- Discovering a Module never implies Operation admissibility.
- Context offer metadata does not expose underlying domain records.

### Explicit non-responsibilities

- Module database schema/state;
- Agent internal architecture;
- Runtime Capability configuration;
- security decisions;
- WorkPlans/AgentTasks;
- CORE privilege.

---

## 6. `CoreRoleAssignment`

A separate representation is required because CORE privilege is Kernel policy, not a
Module self-description.

### Responsibility

Identify the Module currently assigned the replaceable CORE role.

### Fields

```text
CoreRoleAssignment
    module: ModuleRef                 1
    assigned_at: Instant              1
    policy_revision: PositiveInt      1
```

Administrative audit may independently record the installation-owner action that caused
the assignment; that identity model is not made part of Agent architecture.

### Identity

Singleton active installation configuration.

### Ownership

Kernel/installation owner policy.

### Persistence

Durable Kernel configuration with ordinary administrative audit history.

### Mutability

Changed only by explicit reassignment. Reassignment creates new administrative evidence;
it does not rewrite Module or Agent definitions.

### Invariants

- At most one active CORE assignment.
- The referenced Module must be registered and compatible with the CORE contract.
- CORE assignment does not bypass Scope, Context, Operation or Runtime security checks.
- Changing CORE affects future role-based resolution only.

### Explicit non-responsibilities

- owning user configuration globally;
- rewriting `AgentDefinitionRef` ownership;
- moving `AgentSkillInstance`s or Workflows between Modules;
- granting direct storage access to other Modules;
- bypassing Runtime.

---

## 7. `OperationDescriptor`

### Responsibility

Publish one bounded callable function owned by one Module, including the typed contract,
security facts and truthful effect/repetition semantics required to decide whether and
how it may execute.

### Fields

```text
OperationDescriptor
    ref: OperationRef                         1
    name: string                              1
    purpose: string                           1
    input_schema: SchemaRef                   1
    output_schema: SchemaRef                  1
    security: OperationSecurityFacts          1
    effect_semantics: EffectSemantics         1
    visibility: DiscoveryPolicyRef            1
    provenance: DefinitionProvenance<OperationRef> 1
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

EffectSemantics
    kind: EffectKind
    repeatability: Repeatability
    interrupted_outcome: InterruptedOutcome
```

### Identity

`OperationRef = (ModuleRef, operation_id, revision)`. Any incompatible contract,
security, boundary or effect-semantics change publishes a new revision.

### Ownership

`ref.module` owns and implements the Operation. Kernel only exposes/invokes it through
policy-filtered discovery plus current `SecurityAlgebra` evaluation.

### Persistence

Published revisions are immutable and retained while exact references remain in retained
Agents, Skills, Workflows, plans or evidence.

### Mutability

None after publication.

### Invariants

- Operation is the only canonical callable MADRE abstraction.
- External scripts/APIs/MCP tools/functions are adapter internals behind the Operation.
- Skill/Workflow/AgentTask references to an Operation are requirements or guidance, not
  authority.
- Unknown/non-repeatable external effects are never blindly retried after uncertain
  outcome evidence.
- A lower-sensitivity derived ContextBundle requires
  `security.classification_transform = MAY_RECALCULATE` and a fresh Kernel evaluation.

### Explicit non-responsibilities

- provider transport details exposed to the Agent;
- Runtime lifecycle;
- Capability identity;
- Agent security permission lists;
- Module database identifiers.

---

## 8. `SkillDefinition`

### Responsibility

Publish one reusable Module-owned ability package without assuming that a Skill is a
prompt bundle or Workflow container.

### Fields

```text
SkillDefinition
    ref: SkillRef                                  1
    name: string                                   1
    purpose: string                                1
    descriptive_resources: SkillResourceRef[]      0..N
    operation_requirements: OperationRequirement[] 0..N
    workflow_refs: WorkflowRef[]                   0..N
    context_expectations: ContextExpectation[]     0..N
    visibility: DiscoveryPolicyRef                 1
    provenance: DefinitionProvenance<SkillRef>     1
```

```text
RequirementStrength
    REQUIRED
    RELEVANT

OperationRequirement
    operation: OperationRef
    strength: RequirementStrength

ContextExpectation
    purpose: string
    schema: SchemaRef | null
    scopes: set[ScopeRef]
```

`OperationRequirement` never grants invocation authority. It says what the Skill expects
or can make use of.

### Identity

`SkillRef` pins one immutable revision owned by the publishing Module.

### Ownership

`ref.module` publishes/owns the reusable definition. An Agent's responsible Module owns
its separate learned `AgentSkillInstance`.

### Persistence

Published revisions are retained while referenced by any AgentSkillInstance, Workflow,
Agent/plan evidence or configured retention.

### Mutability

None after publication. Change publishes a new Skill revision. Derivation creates a new
Skill identity with provenance rather than modifying upstream history.

### Invariants

- `workflow_refs` may be empty.
- Referenced Workflow revisions are exact.
- Resource references are immutable/revision-pinned.
- Publishing Skill revision N+1 never changes an existing AgentSkillInstance sourced from
  revision N.

### Explicit non-responsibilities

- Agent-specific learned state;
- success scores;
- autonomous learning/promotion;
- Operation permission lists;
- Agent memory/session state.

---

## 9. `AgentSkillInstance`

### Responsibility

Represent one unique AgentDefinition-owned installation/learning of one exact
Module-published Skill revision.

### Fields

```text
AgentSkillInstance
    ref: AgentSkillInstanceRef                  1
    source_skill: SkillRef                      1
    adopted_workflows: WorkflowRef[]            0..N
    created_at: Instant                         1
    provenance: SkillInstanceProvenance         1
```

```text
SkillInstanceProvenance
    created_by: ProvenanceSubjectRef
    source_skill: SkillRef
```

`source_skill` is intentionally repeated in provenance only as creation lineage; both
values MUST agree.

### Identity

`AgentSkillInstanceRef` is scoped by the exact owning `AgentDefinitionRef`. The
`skill_instance_id` is unique within that definition revision.

### Ownership

The AgentDefinition's responsible Module manages the instance as Agent configuration.
The source Skill remains owned by its publishing Module.

### Persistence

Durable with the AgentDefinition configuration and retained while referenced by a task or
historical plan.

### Mutability

Immutable in the initial architecture.

Learning/installing a Skill into an immutable AgentDefinition is an atomic configuration
publication: allocate a new AgentDefinition revision (or new derived AgentDefinition
identity), create the new AgentSkillInstance owned by that exact revision, and publish the
AgentDefinition referencing it. Existing AgentDefinitions/instances do not change.

### Invariants

- `source_skill` is an exact immutable revision.
- Every `adopted_workflows` reference MUST be present in the source SkillDefinition's
  `workflow_refs` at creation in the initial model.
- A later source Skill revision never floats into this instance.
- AgentSkillInstance does not itself confer Operation admissibility.

### Explicit non-responsibilities

- autonomous adaptation/learning scores;
- generic configuration blobs;
- working memory;
- source Skill mutation;
- user Workflow ownership.

---

## 10. `WorkflowDefinition`

### Responsibility

Publish one first-class reusable explicit procedure while leaving recipe internals opaque
until real workflows justify a shared DSL.

### Fields

```text
WorkflowDefinition
    ref: WorkflowRef                                  1
    name: string                                      1
    purpose: string                                   1
    input_schema: SchemaRef | null                    0..1
    output_schema: SchemaRef | null                   0..1
    recipe: WorkflowRecipeRef                         1
    operation_requirements: OperationRequirement[]    0..N
    delegation_requirements: AgentRequirement[]       0..N
    visibility: DiscoveryPolicyRef                    1
    provenance: DefinitionProvenance<WorkflowRef>     1
```

### Identity

`WorkflowRef` is Module-scoped and pins an immutable revision. Workflow identity is
independent from Skill membership and Agent attachment.

### Ownership

The publishing Module owns the WorkflowDefinition. Direct attachment to an
AgentDefinition is configuration managed by that Agent's responsible Module. A user
creating a private Workflow for such an Agent normally causes that responsible Module to
publish/manage the private Workflow; this is not ownership by the CORE role as such.

### Persistence

Immutable revisions are retained while referenced by Skills, AgentSkillInstances,
AgentDefinitions, AgentTasks or retained plans.

### Mutability

None after publication. Derivation creates a new Workflow identity with `derived_from`.

### Invariants

- No mandatory parent Skill or Agent pointer.
- A Skill contributes a Workflow by reference.
- An AgentDefinition directly attaches a Workflow by reference.
- The effective Agent repertoire is direct Agent Workflows plus Workflows adopted through
  AgentSkillInstances.
- Operation requirements do not authorize the referenced Operations.

### Explicit non-responsibilities

- universal Workflow DSL;
- Runtime jobs;
- AgentTask lifecycle;
- learned execution scores;
- implicit Skill creation for ordinary user procedures.

---

## 11. `AgentDefinition`

### Responsibility

Expose MADRE's reusable public descriptor for one reasoning actor while binding opaquely
to the fundamentally Module-specific Agent manager implementation.

### Fields

```text
AgentDefinition
    ref: AgentDefinitionRef                         1
    name: string                                    1
    description: string                             1
    manager_definition_ref: ModuleAgentDefinitionRef 1
    skill_instances: AgentSkillInstanceRef[]        0..N
    direct_workflows: WorkflowRef[]                 0..N
    security: ActorSecurityFacts                    1
    resolution_descriptors: AgentResolutionDescriptor[] 0..N
    visibility: DiscoveryPolicyRef                  1
    provenance: DefinitionProvenance<AgentDefinitionRef> 1
```

```text
AgentResolutionDescriptor
    namespace_module: ModuleRef
    descriptor_id: OpaqueId
```

Resolution descriptors are semantic matching facts, not authority.

### Identity

`AgentDefinitionRef.module` is the responsible Agent-managing Module. Exact revision is
always preserved once persisted in a WorkPlan/AgentTask binding.

### Ownership

The responsible Module owns the Agent configuration and interprets
`manager_definition_ref`. CORE role assignment is irrelevant to that ownership.

### Persistence

Published revisions are immutable and retained while referenced by Agent instances,
AgentSkillInstances, AgentTasks or retained plans/evidence.

### Mutability

None after publication. Configuration change, including learning a Skill or adding a
direct Workflow, publishes another revision/new derived definition.

### Invariants

- `manager_definition_ref.module == ref.module`.
- Every `skill_instances[*].agent_definition == ref`.
- Several AgentInstances may instantiate the same exact definition concurrently.
- The definition exposes no universal prompt, conversation, memory or session layout.
- `security` is a fact declaration used by Kernel algebra, not a permission set.
- Agent ownership does not imply access to the responsible Module's domain persistence.

### Explicit non-responsibilities

The universal schema does **not** contain:

```text
base_prompt
base_instructions
conversation_history
working_memory
persona_memory
learning_memory
standard session
state/memory policy enum
operation allowlist/grant set
working_state payload
model/provider configuration
```

All such implementation choices, if used, remain behind `manager_definition_ref` in the
responsible Module.

---

## 12. `AgentInstance` and opaque Agent state

### Responsibility

Identify one concrete working actor instantiated from an exact AgentDefinition while
leaving all internal state semantics with its responsible Module.

### Fields

```text
AgentInstance
    ref: AgentInstanceRef                         1
    definition: AgentDefinitionRef                1
    manager_instance_ref: ModuleAgentInstanceRef  1
    state_refs: AgentStateRef[]                   0..N
    created_at: Instant                           1
    closed_at: Instant | null                     0..1
```

### Identity

`AgentInstanceRef` is installation-scoped and never reused. `definition` pins the exact
AgentDefinition revision instantiated.

### Ownership

Kernel coordinates creation/binding; the responsible Module behind
`definition.module` owns and interprets the actual Agent instance and every AgentStateRef.

### Persistence

Kernel persists the integration record while required by active or retained WorkPlans.
The responsible Module independently persists whatever manager instance/state it needs
for restart according to its own Agent semantics.

### Mutability

`definition` and `manager_instance_ref` do not change. `state_refs` may be explicitly
synchronized as the responsible Module creates/retires opaque state references;
`closed_at` may be set once.

The Kernel does not checkpoint arbitrary state content and does not prescribe a single
state object.

### Invariants

- `manager_instance_ref.module == definition.module`.
- Every `state_refs[*].module == definition.module`.
- `state_refs` cardinality is 0..N.
- Different AgentInstances do not share state merely because they share one definition.
  A responsible Module may intentionally reference shared Module state, but that is an
  explicit Module-owned design, not a MADRE default.
- If Agent state content must cross a MADRE boundary, the Module projects it into a new
  ContextBundle and normal security algebra applies.

### Explicit non-responsibilities

- universal Session entity;
- `working_state: JsonValue` or arbitrary map;
- memory/persona taxonomy;
- Agent-internal state machine semantics;
- Runtime lifecycle;
- implicit domain authority.

---

## 13. `AgentRequirement`

`AgentRequirement` is a typed value embedded in WorkPlans/AgentTasks/Workflow delegation.
It is not another Agent species and does not authorize anything.

```text
AgentRequirement
    preferred_definition: AgentDefinitionRef | null
    resolution_descriptors: AgentResolutionDescriptor[]
    required_skill_sources: SkillRef[]
    required_workflows: WorkflowRef[]
    security_requirement: ActorSecurityRequirement | null
```

```text
ActorSecurityRequirement
    minimum_trust: OrdinarySecurityLevel | null
    minimum_handled_sensitivity: OrdinarySecurityLevel | null
    maximum_execution_risk: OrdinarySecurityLevel | null
```

These fields narrow semantic candidate resolution. Actual Context/Operation crossings are
still evaluated from the resolved Agent's real `ActorSecurityFacts` and current policy.

The requirement is persisted even after exact Agent resolution. This preserves what the
objective asked for if the resolved AgentDefinition later disappears and permits a future
resolver to detect the missing binding without silently substituting another actor.

---

## 14. Typed schemas, payloads and `ContextBundle`

### 14.1 Schema-bound payload

Architectural semantic payload is never typed as `JsonValue`, `Any`, `dict[str, Any]` or
an equivalent unvalidated arbitrary map.

Conceptually:

```text
TypedPayload<S>
    schema: SchemaRef<S>
    value: S
```

A concrete implementation may persist it as:

```text
schema ref + canonical encoded bytes
```

where the Module/schema codec validates and decodes the bytes into the type identified by
`SchemaRef`. JSON is one possible encoding; it does not make arbitrary JSON a valid
semantic type.

A `SchemaRef` resolves to immutable Module-owned schema material. The concrete schema
language (JSON Schema, protobuf, typed JVM codec, CBOR schema, etc.) is not fixed here.

### 14.2 `ContextBundle`

#### Responsibility

Carry one immutable bounded semantic projection with explicit type, provenance and
security facts across Module/Agent/Operation/Runtime boundaries.

#### Fields

```text
ContextBundle
    ref: ContextBundleRef                           1
    owner_module: ModuleRef                         1
    purpose: string                                 1
    payload: TypedPayload<S> | ModuleResourceRef    1
    security: DataSecurityFacts                     1
    created_at: Instant                             1
    derived_from: ContextBundleRef[]                0..N
    derivation_operation: OperationInvocationRef | null 0..1
    provenance: ContextProvenance                   1
```

```text
ContextProducer = one of:
    ModuleProducer(module: ModuleRef)
    AgentProducer(instance: AgentInstanceRef)
    OperationProducer(invocation: OperationInvocationRef)

ContextProvenance
    producer: ContextProducer
    source_contexts: ContextBundleRef[]
    source_resources: ModuleResourceRef[]
```

#### Identity

`ContextBundleRef` is immutable and installation-scoped. Transforming/projection creates
a new bundle identity.

#### Ownership

`owner_module` is responsible for emitting/classifying the actual material. Kernel brokers
and evaluates crossings but never reinterprets domain payload to assign security facts.

Agent output is emitted/classified through its responsible Module's Agent manager; the
model/Agent cannot directly self-declare lower security facts.

#### Persistence

Persist while required by active/retained WorkPlans or evidence, subject to security-aware
retention. A plan may retain an expired/unavailable reference rather than silently copying
Module data into orchestration storage.

#### Mutability

None after creation.

#### Invariants

- `TypedPayload.schema` resolves exactly and validates the payload.
- `ModuleResourceRef` remains opaque to consumers; accessing its content requires a
  Module-defined projection/Operation boundary.
- A derived bundle is a new object; the source is never relabeled.
- If derived sensitivity is lower than any source bundle sensitivity,
  `derivation_operation` is required, the exact Operation must declare
  `MAY_RECALCULATE`, and the producer Module must classify the resulting actual material.
- Every later crossing re-evaluates the derived bundle's own facts under current policy.

#### Explicit non-responsibilities

- universal knowledge/truth type;
- Module database row identity exposed as authority;
- arbitrary JSON storage;
- Agent internal memory;
- Runtime work state;
- reusable security permission.

---

## 15. `WorkPlan`

### Responsibility

Persist semantic continuity for one objective across foreground interaction, delayed
reasoning, process interruption and restart, independently of Runtime work lifecycle.

### Fields

```text
WorkPlan
    ref: WorkPlanRef                              1
    objective: ContextBundleRef                   1
    origin: PlanOrigin                            1
    orchestrator_requirement: AgentRequirement | null 0..1
    orchestrator_definition: AgentDefinitionRef | null 0..1
    created_at: Instant                           1
    updated_at: Instant                           1
    completion: PlanCompletion | null             0..1
    termination: PlanTermination | null           0..1
    retention_until: Instant | null               0..1
    derived_from: WorkPlanRef | null              0..1
```

```text
PlanOrigin = one of:
    OwnerPlanOrigin(request_context: ContextBundleRef | null)
    ModulePlanOrigin(module: ModuleRef, source: ModuleResourceRef | null)
    AgentTaskPlanOrigin(task: AgentTaskRef)

PlanCompletion
    completed_at: Instant
    context_outputs: ContextBundleRef[]
    artifacts: ModuleArtifactRef[]

PlanTermination
    terminated_at: Instant
    reason_code: OpaqueId
    detail_context: ContextBundleRef | null
```

### Identity

`WorkPlanRef` is never reused. Replay/clone creates a new WorkPlan and records
`derived_from`; previous evidence is never rewritten.

### Ownership

Kernel owns durable semantic orchestration. Modules retain authority over domain material
and artifacts referenced from the plan.

### Persistence

Durable across Kernel/Runtime restart. Completed/terminated plans remain inspectable for
configured retention.

### Mutability

The plan accumulates orchestration facts and may set bindings/terminal facts. It is not an
immutable published definition. Active-plan version graphs are deferred.

AgentTasks are independent durable records addressed by `AgentTaskRef`; the WorkPlan does
not embed one mutable task graph blob.

### Invariants

- No `pending/running/succeeded/failed/blocked` semantic status enum.
- Open/terminal condition is derived from completion/termination facts.
- At most one of `completion` and `termination` is present.
- `orchestrator_definition`, once resolved, remains the exact historical binding even if
  it later stops resolving.
- `objective` is a classified ContextBundle, not an unclassified arbitrary semantic map.
- Runtime queue/work state is never embedded.

### Explicit non-responsibilities

- Module domain persistence;
- one-runtime-job planning;
- user-approval lifecycle;
- live graph revision history;
- security authorization state;
- Agent internal state.

---

## 16. `AgentTask`

### Responsibility

Persist one objective-specific semantic assignment inside a WorkPlan without assuming one
Workflow, one Operation or one Runtime job.

### Fields

```text
AgentTask
    ref: AgentTaskRef                             1
    parent_task: AgentTaskRef | null              0..1
    objective: ContextBundleRef                   1
    agent_requirement: AgentRequirement           1
    resolved_agent: AgentDefinitionRef | null     0..1
    agent_instance: AgentInstanceRef | null       0..1
    skill_instances: AgentSkillInstanceRef[]      0..N
    workflow_requirements: WorkflowRef[]          0..N
    selected_workflow: WorkflowRef | null         0..1
    context_refs: ContextBundleRef[]              0..N
    operation_requirements: OperationRequirement[] 0..N
    prerequisite_tasks: AgentTaskRef[]            0..N
    eligible_at: Instant | null                   0..1
    expected_outputs: OutputExpectation[]         0..N
    created_at: Instant                           1
    completion: TaskCompletion | null             0..1
    termination: TaskTermination | null           0..1
```

```text
OutputExpectation
    name: string
    schema: SchemaRef | null
    scope: ScopeRef | null

TaskCompletion
    completed_at: Instant
    context_outputs: ContextBundleRef[]
    artifacts: ModuleArtifactRef[]

TaskTermination
    terminated_at: Instant
    reason_code: OpaqueId
    detail_context: ContextBundleRef | null
```

### Identity

`AgentTaskRef.task_id` is unique within one WorkPlan. Delegation creates an ordinary child
AgentTask with `parent_task`; there is no team/supervisor ontology.

### Ownership

Kernel owns task orchestration. Responsible Modules own Agent internals; domain Modules
own Context/Operation semantics.

### Persistence

Durable with the WorkPlan and independent from AgentInstance/Runtime process lifetime.

### Mutability

Resolution and instance bindings may be populated once; completion or termination may be
set once. Readiness is always recomputed rather than persisted as a mutable lifecycle
state.

### Effective semantic Agent environment

For a resolved task, Kernel can inspect the semantic composition:

```text
exact AgentDefinition
    + selected AgentSkillInstances owned by that definition
    + direct Workflows from that AgentDefinition
    + adopted Workflows from selected AgentSkillInstances
    + task Workflow requirements/selection
    + task ContextBundles
    + task Operation requirements
```

This composition describes what the task needs and what the Agent can reason about. It is
**not** an authority calculation.

The actually callable Operation set at any concrete invocation is derived afresh from:

```text
policy-filtered visible Module/Operation contracts
    + actual ContextBundle DataSecurityFacts
    + ScopeRefs of the actual material
    + resolved Agent ActorSecurityFacts
    + OperationSecurityFacts
    + actual destination/execution boundary
    + intended use when applicable
    + current Kernel policy
    -> SecurityAlgebra decision
```

There is no `operation_grants`, Agent operation allowlist, planner capability token or
persisted authorization bit.

### Readiness

Initial readiness is derived from facts such as:

```text
no completion/termination
AND prerequisite AgentTasks have required completion evidence
AND eligible_at has arrived (if present)
AND exact/preferred Agent can be resolved or a permitted fallback exists
AND selected AgentSkillInstances resolve and belong to the resolved AgentDefinition
AND required/selected Workflows resolve in the Agent's effective repertoire
AND required ContextBundles resolve
AND required Operations are discoverable/available
AND current security checks permit the crossings required to begin the next action
```

A rejected security crossing is an explainable current readiness/execution fact, not a
`blocked` lifecycle state. A user decision may later become a typed prerequisite for a
specific objective without becoming a universal lifecycle model.

### Invariants

- No semantic `work_id` field.
- `operation_requirements` are descriptive needs only.
- `skill_instances` MUST belong to the resolved AgentDefinition when it is bound.
- `selected_workflow`, if present, MUST be in the effective repertoire (direct or adopted)
  for the resolved Agent configuration.
- One AgentTask may create 0..N child tasks, Operation invocation records and Runtime
  evidence links.
- At most one of completion/termination is present.
- Runtime failure evidence does not mechanically imply semantic task failure/completion.

### Explicit non-responsibilities

- Runtime lifecycle;
- Operation authorization/grants;
- universal human approval state;
- Agent internal memory/session;
- Module domain rows;
- `WorkPlanStep` semantics.

---

## 17. `OperationInvocationRecord`

### Responsibility

Persist truthful Kernel evidence for one requested Module Operation crossing, including
security evaluation, dispatch/outcome evidence and result provenance.

The record exists even when Kernel rejects the requested crossing, because rejection is
useful inspectable evidence; no provider call occurs in that case.

### Fields

```text
OperationInvocationRecord
    ref: OperationInvocationRef                 1
    task: AgentTaskRef                          1
    operation: OperationRef                     1
    requested_by: AgentInstanceRef              1
    requested_at: Instant                       1
    input_contexts: ContextBundleRef[]          0..N
    intended_use: IntendedUseRef | null         0..1
    security_decision: SecurityDecisionRef      1
    dispatched_at: Instant | null               0..1
    outcome: OperationOutcome | null            0..1
    produced_contexts: ContextBundleRef[]       0..N
    artifacts: ModuleArtifactRef[]              0..N
```

```text
OperationOutcome = one of:
    SecurityRejected(observed_at: Instant)
    OperationSuccess(completed_at: Instant)
    OperationFailure(completed_at: Instant, code: OpaqueId,
                     detail_context: ContextBundleRef | null)
    UnknownExternalEffect(observed_at: Instant,
                          detail_context: ContextBundleRef | null)
```

### Identity

`OperationInvocationRef` is installation-scoped and never reused.

### Ownership

Kernel owns the invocation/evidence record. Operation Module owns the function/effect and
classifies any produced ContextBundles.

### Persistence

Durable with WorkPlan/security/effect evidence according to retention policy.

### Mutability

Append/close only: create request, attach security decision, optionally set dispatch time,
then set one observed outcome and produced references. An unknown outcome is never
rewritten into success without separate new evidence.

### Invariants

- `SecurityRejected` implies `dispatched_at == null` and the referenced decision is
  rejected.
- Any dispatched Operation implies the referenced decision was accepted at dispatch time.
- Acceptance is not cached for a later retry; another attempt obtains another current
  SecurityDecision.
- A potentially non-repeatable/unknown external effect plus uncertain outcome prevents
  blind replay. Any later attempt must satisfy effect semantics, truthful Runtime/Kernel
  evidence and current security policy.
- Lower-sensitivity `produced_contexts` use this exact invocation as
  `derivation_operation` and require `MAY_RECALCULATE`.

### Explicit non-responsibilities

- being the callable Operation definition;
- granting future authority;
- Runtime scheduler state;
- universal Agent action taxonomy.

---

## 18. `RuntimeEvidenceLink`

### Responsibility

Associate one physical Runtime WorkRecord with the single semantic AgentTask execution it
materialized, without teaching Runtime about agentic semantics.

### Fields

```text
RuntimeEvidencePurpose
    AGENT_REASONING
    OPERATION_EXECUTION

RuntimeEvidenceLink
    task: AgentTaskRef                              1
    runtime_work: RuntimeWorkRef                    1
    operation_invocation: OperationInvocationRef | null 0..1
    purpose: RuntimeEvidencePurpose                 1
    created_at: Instant                             1
```

### Identity

`runtime_work` is unique across RuntimeEvidenceLinks in the initial architecture. The
link may therefore use `RuntimeWorkRef` as its natural identity.

### Ownership

Kernel owns the relation. Runtime owns the referenced WorkRecord and remains unaware of
the link.

### Persistence

Durable with semantic/Runtime evidence retention.

### Mutability

Append-only.

### Invariants

```text
one AgentTask -> 0..N RuntimeEvidenceLink
one Runtime WorkRecord -> 0..1 RuntimeEvidenceLink
```

- `OPERATION_EXECUTION` requires `operation_invocation`.
- `AGENT_REASONING` normally has `operation_invocation == null`.
- Reuse by another semantic task references the produced ContextBundle/artifact/evidence;
  it does not add a second task owner to the same physical WorkRecord.
- AgentTask semantic completion is never inferred solely from one WorkRecord succeeding.

### Explicit non-responsibilities

- Runtime retry/cancellation semantics;
- shared multi-task physical execution ontology;
- Agent/Workflow/Skill data inside WorkRecord;
- semantic result ownership.

---

## 19. Ownership and persistence matrix

| Schema/entity | Semantic authority | Persistence | Mutability |
| --- | --- | --- | --- |
| `ModuleManifest` | Module content; Kernel registration/discovery | durable current + optional history | new revision |
| `CoreRoleAssignment` | Kernel/installation owner policy | durable singleton + audit | explicit reassignment |
| `OperationDescriptor` | owning Module | durable while referenced | immutable revision |
| `SkillDefinition` | publishing Module | durable while referenced | immutable revision |
| `AgentSkillInstance` | Agent configuration managed by responsible Module | durable with owning AgentDefinition/history | immutable initial record |
| `WorkflowDefinition` | publishing Module; direct attachment managed by Agent's responsible Module | durable while referenced | immutable revision |
| `AgentDefinition` | responsible Agent-managing Module | durable while referenced | immutable revision |
| `AgentInstance` | responsible Module owns internals; Kernel owns integration binding | durable while active/retained | refs may synchronize; close once |
| `ContextBundle` | emitting/classifying Module; Kernel brokers | durable while referenced/allowed | immutable |
| `SecurityDecisionEvidence` | Kernel evidence | durable with crossing evidence | immutable |
| `WorkPlan` | Kernel semantic orchestration | durable + retention | accumulate facts / terminal close |
| `AgentTask` | Kernel semantic orchestration | durable with plan | bindings/evidence / terminal close |
| `OperationInvocationRecord` | Kernel evidence; Module owns Operation effect | durable with plan/evidence | append/close |
| `RuntimeEvidenceLink` | Kernel correlation | durable with evidence | append-only |
| `Runtime WorkRecord` | existing Runtime | existing Runtime store | existing Runtime lifecycle |
| `AgentStateRef` target | responsible Agent-managing Module | Module-defined | Module-defined |
| `ModuleArtifactRef` target | owning Module | Module-defined | Module-defined |
| `SchemaRef` / resource target | referenced Module | immutable/revisioned as referenced | new revision |

Reference/value objects are not independently authoritative merely because they are
persisted inside these records.

Physical table/process placement does not change semantic ownership.

### CORE and user configuration

CORE is only the current role assignment.

If the currently assigned CORE Module manages an AgentDefinition containing a user-created
Workflow or an AgentSkillInstance, that Module manages the configuration because it is the
Agent's responsible Module. It does **not** acquire global user-configuration ownership by
being CORE.

Changing `CoreRoleAssignment`:

- does not change existing `AgentDefinitionRef.module` values;
- does not move AgentSkillInstances;
- does not republish WorkflowDefinitions;
- does not rewrite WorkPlans/AgentTasks;
- only changes future role-based fallback/resolution behavior.

---

## 20. Immutability, revision and reference rules

### 20.1 Published definitions

`OperationDescriptor`, `SkillDefinition`, `WorkflowDefinition` and `AgentDefinition` are
immutable published revisions.

```text
change existing definition semantics -> new revision
customize/derive identity           -> new identity + derived_from exact upstream ref
```

References persisted in Agents/Tasks/Plans are exact and never float to a later advertised
revision.

### 20.2 AgentSkillInstance

An initial AgentSkillInstance is immutable and exact-source pinned. Installing/upgrading a
Skill is an Agent-configuration publication, not mutation of the source Skill and not a
floating pointer.

### 20.3 ContextBundle

Immutable. Projection/minimization/transformation always creates a new bundle with new
facts and provenance.

### 20.4 AgentInstance

The exact AgentDefinition binding never changes. Internal Module-managed states may evolve
behind AgentStateRefs. Kernel does not impose state revision semantics.

### 20.5 WorkPlan and AgentTask

These are durable orchestration records, not immutable definitions. They accumulate
bindings/evidence and terminal facts. Readiness is derived every time from current facts.
Replay/clone creates a new WorkPlan with provenance rather than rewriting history.

### 20.6 Missing exact references

A persisted exact reference that no longer resolves remains evidence of the missing
binding. Kernel must not silently substitute a later revision.

AgentTask retains `AgentRequirement` beside `resolved_agent`, so future substitution can
be proposed explicitly without erasing what actor was originally bound or what was
required.

---

## 21. Security crossing rules

### 21.1 Discovery first

Module, Agent, Skill and Operation existence is exposed only through a subject/context
query evaluated under `DiscoveryPolicyRef` plus current Kernel policy.

There is no globally readable registry projection. Discovery success is not invocation
authority.

### 21.2 Context -> Agent

Before a ContextBundle reaches an AgentInstance:

1. resolve the exact AgentDefinition and `ActorSecurityFacts`;
2. evaluate actual bundle `DataSecurityFacts` and ScopeRefs;
3. evaluate actual Agent execution destination/boundary under current policy;
4. require sensitivity/trust/risk/boundary/intended-use predicates to pass;
5. record evidence when inspection/audit policy requires it;
6. physically withhold the ContextBundle if rejected.

No Agent state can override this.

### 21.3 Context/Agent -> Operation

Before dispatching an Operation:

1. the Operation must be discoverable under current policy;
2. exact `OperationDescriptor` and actual input ContextBundles are resolved;
3. `SecurityAlgebra` evaluates material sensitivity/trust/scopes, Agent facts,
   Operation risk/input constraints, destination boundary, intended use and current
   policy;
4. rejection creates evidence but no provider call;
5. acceptance permits only this concrete dispatch attempt;
6. any later attempt re-evaluates current facts/policy.

OperationRequirement presence or absence does not decide authority. Requirements help
planning/resolution; the algebra controls the actual crossing.

### 21.4 Operation output and Scope transition

An Operation output may carry only ScopeRefs compatible with its declared
`destination_scopes` and current policy.

The owning Module classifies the actual output material. Kernel verifies the declared
contract and transformation relation; it does not derive classifications from text.

### 21.5 Sensitivity lowering

If output sensitivity is lower than any source ContextBundle used to derive it:

```text
Operation.security.classification_transform == MAY_RECALCULATE
AND derived ContextBundle is new/immutable
AND derived_from identifies source ContextBundles
AND derivation_operation identifies the exact invocation
AND current policy permits the transformation/crossing
```

The derived material is then treated normally according to its own recalculated facts.

### 21.6 Runtime-backed reasoning

Reasoning material crossing into a Runtime Capability is evaluated against the actual
Capability execution boundary supplied to `SecurityAlgebra` by current Kernel/Runtime
configuration. Runtime does not need to know WorkPlan or AgentTask semantics.

The Runtime WorkRecord remains physical evidence; `RuntimeEvidenceLink` preserves the
semantic correlation outside Runtime.

### 21.7 Unknown/non-repeatable effects

Security algebra decides whether an attempt is admissible before dispatch. Effect and
Runtime evidence determine whether repetition is safe afterward.

For `NON_REPEATABLE`/`UNKNOWN` effects or `MAY_BE_UNKNOWN` interruption evidence, Kernel
must not infer that another attempt is safe merely because the Agent still wants the
result. It surfaces truthful evidence and requires an explicitly safe future basis before
another physical invocation.

This needs no universal `waiting_for_human` or approval lifecycle state.

---

## 22. Validation against difficult architecture cases

### A — Agentless `CalcModule`

`CalcModule` publishes one Scope, a typed calculate Operation and zero Agents. A WorkPlan
creates an AgentTask with a general `AgentRequirement`; role-based resolution selects the
current CORE fallback AgentDefinition. The AgentInstance sees bounded task Context and the
Operation descriptor. When it requests calculate, Kernel evaluates actual Context, Agent,
Operation, Scope and local boundary facts through `SecurityAlgebra` and dispatches only if
accepted.

No `NoAgent`, Tool, Operation grant or second orchestration model exists.

### B — AAAAT Skill learning

AAAAT publishes `SkillDefinition(AAAAT, revision=3)`, exact Workflow references,
Operation requirements and bounded private Context offers, while exposing zero native
Agents.

Installing the Skill into a CORE-managed Agent configuration creates unique
`AgentSkillInstance X`, with `source_skill = AAAAT@3`, and a new exact AgentDefinition
configuration revision referencing X. X adopts exact Workflow revisions from Skill 3.

When AAAAT publishes Skill revision 4, X remains pinned to revision 3. The AAAAT Module
still owns the reusable Skill; the responsible Agent Module owns X. No permission flows
from the Skill reference.

### C — User Workflow

The Agent-managing Module publishes a private user WorkflowDefinition and the next
AgentDefinition revision attaches its exact `WorkflowRef` in `direct_workflows`.

No synthetic SkillDefinition is created. The Workflow has no exclusive parent pointer.

### D — Concurrent AgentDefinition

Planner AgentDefinition revision N can create Instance A/B/C concurrently. Each has a
different `AgentInstanceRef` and Module manager-instance reference. Their `state_refs` may
be empty, one ref or several refs according to the responsible Module.

MADRE imposes no Session/working-memory model and stores no universal state payload.

### E — Alternative native Agent implementation

A future Module can expose the same MADRE-facing AgentDefinition fields while its
`manager_definition_ref` points to a native state machine, symbolic system, multi-model
controller or another architecture unrelated to CORE.

Its AgentInstances expose opaque manager/state refs only. No CORE prompt, model session,
memory taxonomy or state-policy class is required. This proves MADRE standardizes the
Agent boundary rather than one implementation style.

### F — Long WorkPlan

WorkPlan and AgentTask are Kernel-durable semantic records. Eligibility, prerequisites,
exact Agent binding and Context references survive restart independently from Runtime.

The responsible Agent Module restores whatever opaque manager state it supports through
its own refs. Runtime independently recovers WorkRecords. `RuntimeEvidenceLink` reconnects
physical evidence to the semantic task without putting plan semantics into Runtime.

### G — Multi-runtime AgentTask

One AgentTask can produce:

```text
RuntimeEvidenceLink(reasoning #1)
OperationInvocationRecord(calculate)
RuntimeEvidenceLink(reasoning #2)
child AgentTask
RuntimeEvidenceLink(reasoning #3)
...
```

There is no task `work_id`; one task can correlate with any number of physical works and
Operation invocations.

### H — Privacy minimization

A private calendar ContextBundle has high sensitivity and Calendar-owned ScopeRefs. A
direct crossing to an AAAAT Agent fails the current sensitivity/trust/scope/boundary
predicates.

Calendar's Module-owned minimization Operation declares the appropriate source/destination
Scopes and `MAY_RECALCULATE`. If the invocation itself is admissible, Calendar produces a
new typed ContextBundle containing only the minimized material, recalculates its
DataSecurityFacts, and records `derived_from` plus the exact invocation.

Kernel evaluates that new bundle from its own facts. It never lowers the original bundle
or treats a previous SecurityDecision as a credential.

### I — Private discovery

An installed Module, AgentDefinition, SkillDefinition or Operation can refer to a
restricted `DiscoveryPolicyRef`. Kernel evaluates discovery before returning descriptor
metadata. An unauthorized subject therefore cannot infer existence from a globally
readable registry.

Discovery policy remains distinct from Operation admissibility.

### J — Unknown side effect

An Operation declares `EXTERNAL_EFFECT`, `UNKNOWN` or `NON_REPEATABLE`, and
`MAY_BE_UNKNOWN` as appropriate, together with normalized risk/boundary facts.

Kernel first decides whether attempting it is admissible. If dispatched and the physical
outcome later becomes uncertain, `OperationInvocationRecord`/Runtime evidence records
`UnknownExternalEffect`. Kernel will not blindly retry. The AgentTask can remain open,
terminate or choose another semantic path without introducing a universal human-approval
state.

### K — Replace CORE

`CoreRoleAssignment` changes from Module A to compatible Module B. Future fallback Agent
resolution uses B.

Existing AgentDefinitionRefs still point to their original responsible Modules;
AgentSkillInstances remain scoped to those exact Agent definitions; direct Workflow
ownership does not change; retained WorkPlans/AgentTasks preserve exact bindings. If an
old Module is later removed, its references become explicit missing bindings rather than
silently re-owned by B.

---

## 23. Explicit deferrals and genuine unresolved questions

The following are deliberately not solved by this schema:

1. **Complete final security policy language.** The normalized facts and deterministic
   predicates are fixed enough for the first vertical path; policy administration,
   revocation, richer trust evidence and additional normalized dimensions remain future
   work.
2. **Richer Module Scope relations.** Initial Kernel checking uses exact declared
   ScopeRefs. Hierarchies/semantic subset relations may later be exposed by a typed
   Module-owned relation contract if real domains require them.
3. **Persistent persona semantics.** AgentStateRefs provide a seam; MADRE does not define
   which states are persona, memory, conversation or learning.
4. **Cross-task AgentInstance reuse.** The schema permits opaque Agent manager behavior,
   but the first vertical implementation may instantiate one working actor for one task.
   No universal reuse semantics are defined.
5. **Agent equivalence/substitution.** Exact binding plus retained AgentRequirement makes
   future explicit substitution possible; similarity scoring is undefined.
6. **Autonomous Skill/Workflow learning/promotion.** AgentSkillInstance provides a legal
   future location for Agent-specific Skill evolution, but no scores/promotion machinery
   exist now.
7. **Workflow DSL.** Recipe material remains behind `WorkflowRecipeRef` until repeated
   real workflows justify shared structure.
8. **Universal artifact ontology.** Artifacts remain opaque Module-owned references.
9. **General prerequisite algebra.** Initial prerequisites are AgentTask dependencies,
   eligibility time and current resolvability/security facts. New typed gate kinds wait
   for real objectives.
10. **Cross-installation package identity/export.** Current semantic identities are
    installation/Module scoped. Portable signed packages may later need publisher/package
    identity.
11. **Supply-chain/code trust framework.** Signatures, source inspection, sandboxing and
    code provenance may contribute future trust facts but are not a second authorization
    system here.
12. **Cross-store Runtime submission reconciliation for side effects.** Before
    non-repeatable external Operations rely on Runtime materialization, Kernel linking and
    Runtime idempotent submission must be made crash-reconcilable. Runtime still must not
    learn AgentTask semantics.
13. **Operation revision retirement.** Retention/garbage-collection policy for definitions
    no longer referenced by retained evidence remains implementation policy.
14. **Concrete schema language.** `SchemaRef` intentionally does not choose JSON Schema,
    protobuf or one provider-specific format. The first implementation must select a
    concrete validator/codec without weakening typed contracts to arbitrary maps.

None of these deferrals requires another architectural entity before the first vertical
AgenticLoop.

---

## 24. Exact first implementation slice after schema approval

Do **not** build another horizontal registry-only milestone.

The first implementation should prove one thin but complete vertical AgenticLoop:

```text
Agentless CalcModule
        |
        | manifest + Scope + calculate Operation
        v
Kernel policy-filtered discovery
        |
        | AgentRequirement has no Module-native Agent candidate
        v
current CORE fallback AgentDefinition
        |
        v
responsible Module instantiates AgentInstance
        |
        v
durable WorkPlan / AgentTask
        |
        | ContextBundle objective/input
        v
Runtime-backed Agent reasoning #1
        |
        | RuntimeEvidenceLink
        v
Agent requests CalcModule.calculate
        |
        v
Kernel SecurityAlgebra.evaluate(
    actual input Context,
    Scope,
    Agent facts,
    Operation facts,
    local destination,
    intended use,
    current policy
)
        |
        +-- rejected -> SecurityDecisionEvidence + no dispatch
        |
        v accepted
CalcModule.calculate
        |
        v
OperationInvocationRecord
        + result ContextBundle (typed, Calc-owned, classified)
        |
        v
Runtime-backed Agent reasoning #2
        |
        | RuntimeEvidenceLink
        v
AgentTask completion
        |
        v
WorkPlan completion
```

The slice must establish, end-to-end:

- `ModuleManifest`, Module Scope and Module-owned `OperationDescriptor`;
- separate `CoreRoleAssignment` and fallback Agent resolution;
- `AgentDefinition` as public descriptor plus opaque Module manager binding;
- `AgentInstance` without a universal Session/state payload;
- durable `WorkPlan` and `AgentTask` from the start;
- typed `ContextBundle` inputs/results;
- normalized sensitivity/trust/risk facts and deterministic security deficits;
- real Runtime-backed reasoning using existing WorkSubmission/WorkRecord behavior;
- one requested Operation crossing evaluated by the Kernel algebra;
- `OperationInvocationRecord` and `SecurityDecisionEvidence`;
- result Context flowing back into the same AgentTask;
- a second Runtime reasoning pass and semantic completion;
- `RuntimeEvidenceLink` outside Runtime.

A second AAAAT-shaped fixture should then prove the same vertical composition with:

```text
Agentless AAAAT-like Module
+ SkillDefinition
+ AgentSkillInstance owned by a CORE-managed AgentDefinition revision
+ adopted Workflow revision where useful
+ private bounded Context
+ minimization Operation
```

That fixture is evidence for Skill ownership/pinning and privacy transformation, not a
request to reimplement AAAAT.

The first implementation must **not** introduce richer planners, persistent persona
semantics, autonomous learning loops, multi-Agent teams, a general workflow engine or a
universal Workflow DSL.

The required proof is deliberately small but vertical:

```text
Module
Operation
CORE fallback
AgentDefinition
AgentSkillInstance/Workflow composition when applicable
AgentInstance
Context
SecurityAlgebra
WorkPlan
AgentTask
Runtime
Operation result
continuation
completion
```

Only after this loop works should the architecture expand horizontally.