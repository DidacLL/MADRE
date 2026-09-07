# MADRE agentic schema authority

Status: **Owner-directed clean-slate schema authority**

This document records the architectural corrections established after review of
`docs/architecture/MADRE-agentic-schema.md` at commit
`d2d95c39b60ef6cdae4a6075763ae3e8fe98d823`.

It exists so the next schema pass does not regress into ACL-style authorization,
standard AI-agent session/state assumptions, CORE-owned user configuration, or a
Runtime-shaped semantic model.

`MADRE.md` remains the proposed product contract and
`docs/architecture/MADRE-agentic-architecture.md` remains the long-form architecture.
For schema work, **this document overrides conflicting choices in the first delegated
schema proposal**. The next schema author should derive a coherent schema from all three
sources rather than incrementally patching the previous field tables.

The previous schema remains useful design history. In particular, its immutable
revisioned definitions, Module-owned Operations, AgentDefinition/AgentInstance split,
WorkPlan/AgentTask separation from Runtime and provenance-preserving Context derivation
remain valuable. The corrections below are architectural, not cosmetic.

---

## 1. Primary semantic boundaries

```text
Module
  owns domain semantics, domain boundary and exposed Operations
  may publish Skills
  may manage Agents

CORE
  is a replaceable privileged Module role

AgentDefinition
  is the MADRE-visible definition of a reusable reasoning actor managed by a Module

AgentInstance
  is one concrete instantiation of that definition
  may have zero or more Module-managed state references

SkillDefinition
  is a Module-published reusable ability package

AgentSkillInstance
  is one Agent-owned pinned instantiation of a SkillDefinition

WorkflowDefinition
  is a reusable explicit recipe
  may be contributed by a Skill or attached directly to an Agent

ContextBundle
  is a bounded typed semantic projection carrying provenance and security facts

Operation
  is a Module-owned bounded callable function

WorkPlan / AgentTask
  are durable MADRE semantic orchestration state

Runtime WorkRecord
  is durable physical execution evidence
```

There is no canonical `Tool`, `Routine`, `WorkPlanStep`, Agent subclass hierarchy,
knowledge/learning ontology or universal Agent-session model.

---

## 2. Security is algebraic admissibility, not grants

MADRE security is not based on planners or Agents issuing authorization tokens.

Do **not** model authority as:

```text
AgentTask.operation_grants
permission tokens
capability tokens produced by a planner
"authorized = true" persisted on semantic work
```

Such values could become stale, be confused with policy, or incorrectly make WorkPlan
state the source of security authority.

Instead, every relevant crossing is admitted or rejected deterministically from the
security facts carried by the participating data, Module/domain boundary, Agent,
Operation and execution destination.

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

The result is derived at the crossing. It is not an authorization credential.

The Kernel may record the decision and deficits as evidence, but a prior accepted result
does not bypass re-evaluation when facts or policy change.

### 2.1 Normalized level vocabulary

The initial MADRE security algebra uses one common normalized ordinal vocabulary for
comparable security dimensions:

```text
SecurityLevel
  SYSTEM_RESERVED = 0
  LEVEL_1 = 1
  LEVEL_2 = 2
  LEVEL_3 = 3
  LEVEL_4 = 4
  LEVEL_5 = 5
```

`0` is reserved for Kernel/system semantics and is not an ordinary user/Module-selectable
security level. It is **not** merely "lower than LEVEL_1"; it is a reserved sentinel
outside the ordinary 1..5 scale.

Each dimension defines its own monotonic interpretation. Initially the architecture must
support at least the concepts of:

```text
sensitivity  # higher ordinary level means more sensitive material
trust        # higher ordinary level means more trusted material/actor
risk         # higher ordinary level means greater operation/execution risk
```

Do not add, average or collapse dimensions into one scalar score. They share a normalized
vocabulary so relations can be implemented and inspected consistently, but admission is
a conjunction of dimension-specific relations.

The schema must leave room for additional normalized dimensions such as intended use,
autonomy or safety if concrete security behavior later requires them.

### 2.2 Scope/domain boundary

Scope is not another numeric score.

```text
ScopeRef
  module: ModuleRef
  scope_id: opaque identifier owned by that Module
```

A Module owns the meaning and internal relationship of its scopes.

The Kernel does not infer domain semantics from payload text. It evaluates only the
normalized facts and scope relations supplied through the Module contract and current
system policy.

A crossing to a different domain/scope is not made valid by an Agent deciding to do it.
It must be representable by the declared Module/Operation boundary and pass the security
algebra.

### 2.3 Source of facts versus enforcement

The responsibility split is:

```text
Module
  classifies/projects its domain material
  declares the security facts of its exposed Operations
  owns Scope semantics
  performs domain-aware minimization/anonymization/projection

Agent manager Module
  declares/manages the Agent's security properties

Kernel
  does not reinterpret domain payload
  mechanically evaluates the normalized algebra
  enforces whether the crossing/invocation can physically occur
```

Thus Module code creates semantic/security facts; Kernel verifies their relations and
enforces the boundary.

No prompt, model output, Workflow instruction or WorkPlan field can override the Kernel
calculus.

### 2.4 Context minimization and derived security

Security is evaluated against the **actual material provided**, not the inaccessible
source record from which it originated.

If high-sensitivity context is minimized or anonymized, that operation produces a new
ContextBundle with its own recalculated security facts and provenance:

```text
source ContextBundle
      |
      | Module-owned authorized projection/minimization
      v
derived ContextBundle
  derived_from = source
  derivation = exact OperationInvocationRef
  security = recalculated facts for the derived material
```

The original bundle is never relabeled in place.

### 2.5 Unknown-effect Operations

Operation security facts include effect/risk semantics. Unknown or non-repeatable
external effects are therefore handled by the same deterministic model plus truthful
execution evidence.

The security algebra decides whether the Operation may be attempted. Runtime/Kernel
evidence decides what is known to have happened. An uncertain external effect must not
be blindly repeated merely because the Agent wants the result.

Future code/source inspection, sandboxing, signatures or open-source provenance may add
trust evidence for Operations, but they are additional evidence into this model rather
than a second authorization system. The user remains the ultimate installation owner.

---

## 3. Minimum security schema

The next concrete schema should model security through typed facts rather than ACLs.
A suitable minimum shape is:

```text
DataSecurityFacts
  sensitivity: SecurityLevel
  trust: SecurityLevel
  scopes: set[ScopeRef]

ActorSecurityFacts
  trust: SecurityLevel
  maximum_handled_sensitivity: SecurityLevel
  execution_risk: SecurityLevel

OperationSecurityFacts
  risk: SecurityLevel
  minimum_input_trust: SecurityLevel
  maximum_input_sensitivity: SecurityLevel
  source_scopes: set[ScopeRef]
  destination_scopes: set[ScopeRef]
  boundary: ExecutionBoundary
  classification_transform: NONE | MAY_RECALCULATE

ExecutionBoundary
  LOCAL_TRUSTED
  LOCAL_ISOLATED
  REMOTE
```

The exact predicate table belongs to a separately tested deterministic
`SecurityAlgebra`; the entity schema must not hard-code a simplistic arithmetic formula.

The evaluator should return structured deficits rather than only a Boolean:

```text
SecurityDecision
  accepted: bool
  deficits: SecurityDeficit[]
```

`SecurityDecision` is execution evidence, not authority and not a semantic WorkPlan
lifecycle state.

---

## 4. Module and Operation schema

A Module is the integration/domain boundary seen by MADRE.

```text
ModuleRef
  module_id: installation-scoped opaque identifier

ModuleManifest
  module: ModuleRef
  revision: positive integer
  name: string
  description: string
  visibility: discovery policy
  scopes: ScopeDescriptor[]
  operations: OperationRef[]
  skills: SkillRef[]
  agents: AgentDefinitionRef[]
  context_offers: ContextOfferDescriptor[]
```

A Module may expose zero Agents, zero Skills and zero Operations.

CORE assignment remains Kernel configuration and is not self-declared by the manifest.

An Operation is always Module-owned:

```text
OperationRef
  module: ModuleRef
  operation_id: opaque identifier
  revision: positive integer

OperationDescriptor
  ref: OperationRef
  name: string
  purpose: string
  input_schema: SchemaRef
  output_schema: SchemaRef
  security: OperationSecurityFacts
  effect_semantics: EffectSemantics
  visibility: discovery policy
  provenance: DefinitionProvenance
```

`EffectSemantics` must be sufficient to distinguish safe repeatability/idempotency from
unknown or non-repeatable effects.

The Operation implementation may internally be a script, MCP call, HTTP API, native
function, database-backed application operation or something else. That provider detail
is not part of Agent semantics.

There is no independent MADRE Tool entity.

---

## 5. SkillDefinition and AgentSkillInstance

The Module publishes the reusable Skill definition. The Agent owns its particular
learned/installed instantiation.

```text
SkillRef
  module: ModuleRef
  skill_id: opaque identifier
  revision: positive integer

SkillDefinition
  ref: SkillRef
  name: string
  purpose: string
  descriptive_resources: SkillResourceRef[]
  operation_requirements: OperationRequirement[]
  workflow_refs: WorkflowRef[]
  context_expectations: ContextExpectation[]
  visibility: discovery policy
  provenance: DefinitionProvenance
```

A Skill may have zero Workflows.

The schema must not assume that a Skill is only a prompt bundle or only a Workflow
container. `SkillResourceRef` deliberately allows the publishing Module/adapter to expose
instructions, harness material, examples or other standardized skill resources without
forcing one universal AI-provider format into MADRE.

When an Agent learns/installs a Skill, create a unique pinned Agent-owned instantiation:

```text
AgentSkillInstanceRef
  agent_definition: AgentDefinitionRef
  skill_instance_id: opaque identifier

AgentSkillInstance
  ref: AgentSkillInstanceRef
  source_skill: SkillRef             # exact immutable upstream revision
  adopted_workflows: WorkflowRef[]   # exact revisions visible in this instantiation
  created_at: Instant
  provenance: DefinitionProvenance
```

This record is managed as part of the responsible Module's Agent configuration.

It solves three requirements:

1. the publishing Module remains owner of the upstream Skill;
2. an Agent receives a unique, pinned instantiation that does not float when the Module
   publishes a later Skill revision;
3. future per-Agent skill adaptation has a natural location without making the original
   Module Skill mutable.

The initial schema does not need to define autonomous learning fields, success scores,
mutable skill memory or arbitrary configuration blobs.

If a user later creates an entirely new Skill, it is published by some Module the user
controls. That is different from adding a custom Workflow to an Agent.

---

## 6. WorkflowDefinition

A user-created reusable procedure is normally a Workflow, not an invented user Skill.

```text
WorkflowRef
  module: ModuleRef
  workflow_id: opaque identifier
  revision: positive integer

WorkflowDefinition
  ref: WorkflowRef
  name: string
  purpose: string
  input_schema: SchemaRef | null
  output_schema: SchemaRef | null
  recipe: WorkflowRecipeRef
  operation_requirements: OperationRequirement[]
  delegation_requirements: AgentRequirement[]
  provenance: DefinitionProvenance
  visibility: discovery policy
```

`WorkflowRecipeRef` is an explicit typed reference to the recipe material managed by the
publishing/Agent-managing Module. Do not introduce a universal Workflow DSL until real
workflows demonstrate one.

WorkflowDefinition remains first-class and has no exclusive parent pointer.

A Skill may contribute WorkflowRefs.
An AgentDefinition may directly attach WorkflowRefs, including private/user-created
ones.

The Agent's effective repertoire is the union of its direct Workflows and the Workflows
adopted through its AgentSkillInstances.

---

## 7. AgentDefinition: public contract, not universal internal Agent class

A MADRE-native Module must remain free to implement Agent management in fundamentally
different ways.

Therefore the universal AgentDefinition must **not** encode assumptions such as:

```text
base_prompt: string
conversation_history: JsonValue
working_memory: JsonValue
session: standard industry session object
single memory/state policy enum
```

The Kernel-facing definition is a public descriptor and an opaque binding to the
responsible Module's Agent manager:

```text
AgentDefinitionRef
  module: ModuleRef
  agent_id: opaque identifier
  revision: positive integer

AgentDefinition
  ref: AgentDefinitionRef
  name: string
  description: string
  manager_definition_ref: ModuleAgentDefinitionRef
  skill_instances: AgentSkillInstanceRef[]
  direct_workflows: WorkflowRef[]
  security: ActorSecurityFacts
  resolution_descriptors: AgentResolutionDescriptor[]
  visibility: discovery policy
  provenance: DefinitionProvenance
```

`ModuleAgentDefinitionRef` is opaque to Kernel. The responsible Module decides whether
it points to prompts, a state machine, a model configuration, a complex native Agent
system or another implementation.

This is the key extensibility rule:

> MADRE standardizes the contract around an Agent, not the internal architecture of every
> Agent implementation.

---

## 8. AgentInstance and state

AgentInstance is one concrete working actor:

```text
AgentInstanceRef
  instance_id: installation-scoped opaque identifier

AgentInstance
  ref: AgentInstanceRef
  definition: AgentDefinitionRef
  manager_instance_ref: ModuleAgentInstanceRef
  state_refs: AgentStateRef[]
  created_at: Instant
  closed_at: Instant | null
```

There is deliberately no `working_state: JsonValue` and no universal Session entity.

```text
AgentStateRef
  module: ModuleRef
  state_id: opaque identifier owned by the responsible Module
```

`state_refs` cardinality is `0..N`.

Examples are intentionally **not** canonical categories. One Module may use no state,
another may maintain several distinct states, and a future persistent/persona Agent may
have different state organization from a temporary planner. Kernel does not inspect or
merge these states.

If state content must cross a MADRE boundary, the responsible Module projects the
necessary material into a ContextBundle. That projected context is then governed by the
same security algebra as every other payload.

This avoids binding MADRE to current industry assumptions about conversation sessions,
working memory, learning memory or persona memory.

---

## 9. ContextBundle and typed payloads

Do not use `JsonValue` as an architectural escape hatch.

Runtime/domain payloads must be associated with an explicit schema or opaque Module
resource contract.

```text
SchemaRef
  module: ModuleRef
  schema_id: opaque identifier
  revision: positive integer

TypedPayload
  schema: SchemaRef
  content: schema-validated value

ContextBundleRef
  bundle_id: installation-scoped opaque identifier

ContextBundle
  ref: ContextBundleRef
  owner_module: ModuleRef
  purpose: string
  payload: TypedPayload | ModuleResourceRef
  security: DataSecurityFacts
  created_at: Instant
  derived_from: ContextBundleRef[]
  derivation_operation: OperationInvocationRef | null
  provenance: ContextProvenance
```

The implementation may serialize a typed payload as JSON, CBOR or another format, but
internal APIs must not treat arbitrary `dict[str, Any]` / `JsonValue` as a valid semantic
contract merely because serialization is convenient.

A transformed/minimized bundle is always a new immutable bundle with recalculated
security facts.

---

## 10. WorkPlan and AgentTask

The first delegated schema's separation from Runtime remains correct.

```text
WorkPlan
  plan_id
  objective
  origin/provenance
  orchestrator requirement/binding
  retention/provenance metadata
  terminal completion or termination facts
```

AgentTasks are independently durable records under the plan:

```text
AgentTask
  task_ref
  parent_task: AgentTaskRef | null
  objective
  agent_requirement
  resolved_agent: AgentDefinitionRef | null
  agent_instance: AgentInstanceRef | null
  skill_instances: AgentSkillInstanceRef[]
  workflow_requirements: WorkflowRef[]
  selected_workflow: WorkflowRef | null
  context_refs: ContextBundleRef[]
  operation_requirements: OperationRequirement[]
  prerequisite_tasks: AgentTaskRef[]
  eligible_at: Instant | null
  expected_outputs
  completion | termination
```

`operation_requirements` express task needs/hints. They are **not authority**.

The effective callable Operation set is derived from the actual Module/Agent/Context
configuration and current security algebra. A planner cannot grant itself a forbidden
Operation by writing a reference into AgentTask.

Readiness remains derived rather than represented by `blocked` or a universal lifecycle
enum.

One AgentTask may perform zero, one or many Operation invocations, Runtime works and child
AgentTasks.

---

## 11. Operation evidence and Runtime linkage

Keep Operation execution evidence separate from semantic task state:

```text
OperationInvocationRecord
  invocation_ref
  task: AgentTaskRef
  operation: OperationRef
  requested_by: AgentInstanceRef
  requested_at
  input_contexts: ContextBundleRef[]
  security_decision: SecurityDecisionRef
  outcome
  produced_contexts: ContextBundleRef[]
  artifacts
```

The exact Runtime bridge is an internal Kernel evidence relation, not a user-facing
semantic entity:

```text
RuntimeEvidenceLink
  task: AgentTaskRef
  runtime_work: RuntimeWorkRef
  operation_invocation: OperationInvocationRef | null
  purpose: string | null
```

For the initial architecture:

```text
one AgentTask -> 0..N RuntimeEvidenceLink
one Runtime WorkRecord -> at most one AgentTask execution link
```

If a result is later reused by another task, reuse the resulting ContextBundle/artifact
or other evidence explicitly rather than claiming that one physical execution belonged
to several semantic assignments.

Runtime remains unaware of AgentTask, Workflow, Skill, Agent state and Module domain
meaning.

---

## 12. Ownership summary

| Concern | Authority |
| --- | --- |
| Domain semantics and persistence | Module |
| Scope meaning | Module |
| Module Operation definition/implementation | Module |
| SkillDefinition | publishing Module |
| learned AgentSkillInstance | Agent configuration managed by responsible Module |
| direct Agent Workflows | Agent configuration managed by responsible Module |
| Agent internal definition/state model | responsible Module |
| Agent resolution/instantiation coordination | Kernel |
| Context crossing security enforcement | Kernel using Module/Agent/Operation facts |
| WorkPlan / AgentTask durable orchestration | Kernel |
| physical durable work | Runtime |
| provider-specific computation | Capability |

CORE is simply the currently assigned privileged Module role. CORE assignment does not
own user definitions by implication.

A user-created Workflow attached to a CORE-managed Agent is owned because CORE currently
manages that Agent, not because user configuration is globally owned by the CORE role.
If CORE is replaced, role reassignment must not silently rewrite historical ownership or
references.

---

## 13. Required invariants

```text
Module is the domain/security integration boundary.

CORE is a replaceable Module role.

Operations are Module-owned.

There is no canonical Tool entity.

Security is deterministic algebraic admissibility, not ACL/grant tokens in WorkPlans.

Security facts are normalized; dimensions are compared independently, never summed.

0 is reserved for system semantics; ordinary normalized security values are 1..5.

Scope is Module-owned semantic structure, not another numeric score.

Module creates semantic/security facts; Kernel mechanically enforces crossings.

Context minimization creates new derived material with recalculated facts and provenance.

SkillDefinition is Module-owned.

An Agent learns/instantiates a Skill as a unique pinned AgentSkillInstance.

A Skill may provide zero Workflows.

User-created procedures are direct Workflows unless the user intentionally authors a
new Skill.

WorkflowDefinition has no exclusive Skill/Agent parent.

AgentDefinition is a public MADRE contract plus opaque Module-managed implementation.

AgentInstance may reference 0..N opaque Module-managed states.

MADRE defines no universal Session/working-memory/persona-memory schema.

Do not use JsonValue/dict[str, Any] as a semantic schema escape hatch.

WorkPlan and AgentTask remain durable semantic orchestration above Runtime.

AgentTask operation requirements are not authority.

Readiness is derived; `blocked` is not a canonical semantic lifecycle state.

One AgentTask may produce many Operation invocations and Runtime works.

Runtime remains blind to Module/Agent/Skill/Workflow/WorkPlan semantics.
```

---

## 14. Explicitly deferred

Do not solve these while producing the next schema unless a current invariant genuinely
requires a seam:

- autonomous learning/promotion of Skills or Workflows;
- persistent persona semantics;
- universal Agent session taxonomy;
- universal memory taxonomy;
- exact multi-state organization for Agents;
- Agent similarity/substitution algorithm;
- live WorkPlan version graph;
- universal Workflow DSL;
- universal artifact ontology;
- cross-installation package identity;
- code-scanning/supply-chain trust framework;
- complete final security policy language;
- richer normalized security dimensions beyond those required by the first working
  vertical path.

The objective is not to predict every future Agent system. It is to preserve the seams
that let Modules implement different Agent systems without replacing the Kernel contract.

---

## 15. Required first implementation direction after schema approval

Do not spend the first implementation phase building a registry/security substrate that
never executes an AgenticLoop.

The first implementation should be a thin vertical proof using the approved schema:

```text
Agentless CalcModule
  exposes calculate Operation
        |
        v
CORE fallback AgentDefinition
        |
        v
AgentInstance
        |
        v
one durable WorkPlan / AgentTask
        |
        v
Runtime-backed reasoning
        |
        v
Agent chooses/requests calculate
        |
        v
Kernel security algebra evaluates Operation + context + Agent + scope
        |
        v
CalcModule.calculate
        |
        v
Operation evidence + result ContextBundle
        |
        v
Agent continues reasoning through Runtime
        |
        v
AgentTask completion / WorkPlan completion
```

A second fixture should be AAAAT-shaped: Agentless Module, SkillDefinition, bounded
private Context and Module Operations, consumed through a CORE fallback Agent. It exists
to prove Module/Skill/Agent ownership separation and security/minimization boundaries,
not to reimplement AAAAT.

The implementation must remain small, but it must close the loop end-to-end.
