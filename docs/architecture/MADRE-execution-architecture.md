# MADRE execution architecture

This document defines the detailed responsibility and interaction architecture behind the canonical product contract in `MADRE.md`.

Its purpose is to keep semantic intelligence, domain ownership and runtime execution separated while still allowing sophisticated agentic applications to share one governed model/capability runtime.

## 1. Architectural thesis

MADRE is the execution bridge between AI-capable Modules and bounded computation.

```text
Module / application semantics
    |
    | executable work + execution constraints
    v
MADRE Kernel / Runtime
    |
    | admitted physical execution
    v
Capabilities / models / bounded services
```

The semantic owner chooses **what work should exist**.

MADRE chooses **how accepted work executes**.

That boundary is the primary architecture rule.

## 2. Responsibility layers

### 2.1 Module layer

A Module owns everything whose purpose is to understand, represent, present, plan or mutate its domain.

Typical Module responsibilities include:

```text
interaction surfaces
semantic routing
Agent architecture
Skills / Workflows
WorkPlans
conversation semantics
knowledge / memory / learning
context selection
context minimization
artifact lifecycle
domain Operations
domain persistence
result interpretation
```

A Module may implement these responsibilities with any internal technology.

### 2.2 MADRE execution layer

MADRE owns everything whose purpose is to execute intelligence work reliably and globally across available resources.

```text
WorkSubmission acceptance
WorkRecord lifecycle
WorkAttempt truth
immediate/delayed eligibility
application/module fairness
priority/budget/resource constraints
global scarce-resource admission
model/capability selection
execution-boundary algebra
cancellation/retry
interruption/restart recovery
runtime evidence
```

### 2.3 Capability layer

Capabilities perform bounded physical computation.

They may expose model inference, embeddings, audio, image, deterministic compute or another specialized execution primitive.

Provider-specific lifecycle, API dialect, caches, model residency implementation and device-specific mechanics remain behind this layer.

## 3. No direct human-to-Kernel interaction

The execution Kernel is not a user-facing semantic endpoint.

Every user interaction belongs to a Module-owned surface.

```text
User
  -> Module surface
  -> Module semantic logic / Agent
  -> explicit MADRE execution request
```

A compatibility API can make MADRE look like a conventional inference endpoint to an application. That application is still the semantic owner of the prompt and response.

The important property is that MADRE receives a request that has already been classified by the caller as executable intelligence work.

The input can be raw user text, structured model context, embeddings input, an image, audio or another typed payload. Its content does not make Kernel responsible for understanding it.

## 4. Module surfaces and foreground behavior

A Module can expose several human/application surfaces:

```text
conversation UI
dashboard
CLI
editor integration
voice interface
automation interface
background service
```

The Module determines which Agent or ordinary code is responsible for each surface.

For a simple surface:

```text
surface event
    -> lightweight Module logic
    -> one MADRE inference request
    -> render result
```

For a sophisticated surface:

```text
surface event
    -> interaction Agent
    -> semantic routing / Workflow / WorkPlan
    -> one or more MADRE executions
    -> interpret results
    -> update UI/domain state
```

The distinction is internal to the Module.

## 5. Agent architecture boundary

Agent is a semantic abstraction owned by a Module.

The architecture intentionally permits different Module implementations:

```text
Module A
    one stateless interaction Agent

Module B
    persistent Agent with memory and Skills

Module C
    planner + specialized Agents

Module D
    ordinary application logic that directly uses MADRE commands
```

MADRE's contract remains the same in every case.

An implementation should therefore avoid making any of these Agent-internal concepts prerequisites of `WorkSubmission`:

```text
Agent definition identity
Agent instance identity
Agent session
Skill instance
Workflow selection
Agent memory reference
prompt template identity
planner state
```

A Module may put such identifiers into opaque correlation/diagnostic metadata if useful, but the runtime does not assign execution semantics to them unless an explicit scheduling requirement later justifies one.

## 6. Semantic routing

Semantic routing belongs to Module-owned intelligence.

A Module may discover registered Module descriptors and use them to decide that another domain should participate.

Example:

```text
Module A Agent
    examines permitted descriptors
    decides Module B is relevant
    requests a B-owned projection / Operation / semantic endpoint
```

MADRE can provide deterministic registry lookup and transport, but it does not infer destination from natural language.

A destination Module may:

- return bounded context;
- execute a Module-owned Operation;
- accept a semantic request through its own application contract;
- submit its own MADRE work while processing that request.

The domain meaning of the collaboration stays above MADRE.

## 7. CORE placement

CORE is a first-party general intelligence Module.

Its status as the configured default means the installation can expose CORE as the general surface when no domain-specific surface is being used.

Conceptually:

```text
ModuleRegistry
    registered_modules
    default_module_ref
```

The default reference is registry/configuration information for surfaces and integration logic.

Typical CORE internals may include:

```text
default interaction Agent
Planner
Reasoner
Verifier
Coordinator
Skills
Workflows
WorkPlans
conversation state
cross-domain context reduction
```

These are CORE architecture rather than Kernel architecture.

CORE calls the same MADRE work boundary as every other Module.

## 8. WorkPlan projection into runtime work

A semantic WorkPlan can be arbitrarily rich inside its owning Module.

Example:

```text
WorkPlan P
    objective
    Task A: investigate
    Task B: compare after A
    Task C: verify independently
    Task D: produce final synthesis
```

MADRE does not need those semantic labels.

The owner projects executable portions:

```text
P/A -> WorkSubmission 101
P/C -> WorkSubmission 102
later P/B -> WorkSubmission 103
later P/D -> WorkSubmission 104
```

The submissions can contain correlation metadata such as:

```text
semantic_owner_ref = opaque
plan_ref = opaque
task_ref = opaque
purpose = opaque diagnostic text/value
```

These values let the owner reconstruct results and let inspection tools display useful provenance without making MADRE a semantic planner.

### 8.1 Scheduling-group projection

If physical scheduling needs additional structure, the semantic owner may provide an explicit execution projection.

For example:

```text
SchedulingGroup
    group_ref
    relative_share
    member_index / expected_member_count (optional)
```

MADRE may then know that work is “1 of 4” for fairness/resource purposes while remaining unaware of what those four items mean.

Such fields should be introduced only when the scheduler actually uses them.

### 8.2 Dependencies

A semantic owner can initially enforce dependencies by submitting downstream work only when prerequisites are semantically satisfied.

If runtime restart/performance requirements later justify durable dependency scheduling, MADRE may gain the smallest execution dependency primitive necessary, for example “work B becomes eligible after work A succeeds.”

That relation remains execution structure rather than WorkPlan semantics.

## 9. DRE flow

A representative DRE interaction is:

```text
User
  -> Module surface
  -> Agent determines immediate response is useful
  -> MADRE immediate WorkSubmission
  -> Module returns response to user

Agent/Module also determines follow-up work is useful
  -> Module-owned WorkPlan / task state
  -> MADRE delayed WorkSubmission(s)

User continues interacting

MADRE later executes eligible work
  -> durable result/evidence
  -> Module retrieves/receives result
  -> Agent interprets result
  -> Module may update its plan and surface
```

MADRE needs no interpretation of “deeper,” “research,” “verification” or “reflection.” These are semantic labels belonging to the Module.

## 10. Runtime work lifecycle

The runtime foundation is:

```text
WorkSubmission
    -> WorkRecord
        -> WorkAttempt 1
        -> WorkAttempt 2 if explicitly retried
        -> ...
```

Accepted work is durable before physical execution.

Immediate and delayed work share the same durable path.

The Runtime Scheduler derives eligibility from execution facts such as:

```text
accepted and not terminal
eligible_at reached
not cancelled
required capability available or discoverable
resource admission possible
retry semantics permit attempt
```

The scheduler does not derive semantic readiness from prompt content or Agent reasoning.

## 11. Global resource authority

Consumer hardware scarcity is a primary design condition.

MADRE is the shared authority for heavyweight execution across Modules.

A Module can express requirements and priority, but it cannot independently monopolize shared model/GPU resources outside the runtime.

Runtime scheduling can account for:

```text
origin Module/application
priority
eligibility time
resource class
runtime duration evidence
model residency
load/swap cost
fairness cursor/group
retry freshness
cancellation state
```

The exact scheduler policy can evolve while preserving the ownership boundary.

## 12. Model/capability decision

Semantic owners describe what computation they need.

Example request classes:

```text
interactive text generation
high-quality background reasoning
embedding computation
speech transcription
image understanding
exact configured model
local-only execution
remote-permitted execution
```

MADRE resolves those needs against current capabilities and current physical evidence.

The decision is deterministic/runtime-oriented. It may consider availability, model metadata, resource pressure and policy.

The Module interprets the returned output.

## 13. Generic capability input

MADRE should retain a generic typed execution envelope even if one current capability uses a chat-completions protocol.

Conceptually:

```text
TypedPayload
    schema_ref
    encoding
    content

CapabilityRequest
    capability_requirement
    input_payloads
    output_requirement
```

A chat adapter may define a chat-message schema.

An embeddings adapter may define an embedding-input schema.

A speech adapter may define an audio/transcription schema.

Provider-specific request objects remain adapter implementation details.

## 14. Module Operations

A public Module Operation exists when a Module intentionally exposes bounded domain behavior to other MADRE-aware components.

Example shape:

```text
OperationDescriptor
    operation_ref
    purpose/routing metadata
    input_schema_ref
    output_schema_ref
    boundary profile
    effect semantics
```

An Agent/Module explicitly chooses the Operation.

MADRE evaluates the current crossing and invokes the destination Module through its registered Operation adapter.

Operation output returns to the semantic owner as generated/operation result material.

The destination Module alone applies domain mutations.

### 14.1 Effect truth

Operation execution evidence must distinguish enough physical truth to support safe recovery, such as:

```text
no external effect
repeatable
idempotent
non-repeatable
unknown repeatability
interruption may leave unknown external effect
```

A runtime retry policy cannot blindly repeat work whose effect may already have occurred.

## 15. Context ownership and projection

A Module's internal database or state is never implicitly execution context.

The Module determines a bounded projection for a specific use.

Conceptually:

```text
DomainState
    -> ModuleProjection(purpose)
    -> TypedPayload + MaterialBoundary
```

A projection can be richer for a trusted local Module-to-CORE exchange than for a later remote capability call.

This supports staged disclosure:

```text
private Module state
    -> bounded local projection
    -> CORE Agent
    -> further reduction
    -> selected model capability
```

Each actual crossing is evaluated independently.

## 16. Material derivation

A minimization/anonymization transformation produces new material rather than changing the classification of the old material.

```text
source material
    -> explicit transformation
    -> derived material
        derived payload
        current boundary factors
        provenance to source/transformation
```

The transformation is semantic Module behavior because the Module understands what information was removed or changed.

MADRE only evaluates the resulting execution boundary.

## 17. Security-algebra architecture

The security algebra is a Kernel function over normalized boundary factors.

It is intentionally semantically shallow.

### 17.1 Factors

For an execution crossing, relevant values can include:

```text
MaterialBoundary
    sensitivity
    trust
    scope set

ActorBoundary
    trust
    maximum_handled_sensitivity
    execution_risk

OperationBoundary
    operation risk
    minimum input trust
    maximum input sensitivity
    source scopes
    destination scopes
    execution boundary
    classification-transform behavior

DestinationBoundary
    trust
    execution risk
    execution boundary

PolicyBoundary
    current dimension-specific rules
```

No full Agent, Module, WorkPlan or domain object is required for the algebra.

### 17.2 Dimension semantics

```text
sensitivity: higher means more sensitive
trust:       higher means more trusted
risk:        higher means more risky
```

All use ordinary levels `1..5`, with `0` reserved for Kernel/system representation.

Each dimension is evaluated through its own relation.

Example family of predicates:

```text
material.sensitivity <= actor.maximum_handled_sensitivity
material.trust >= operation.minimum_input_trust
material.sensitivity <= operation.maximum_input_sensitivity
actor.execution_risk <= policy.actor_risk_limit(material, destination)
operation.risk <= policy.operation_risk_limit(material, destination)
scope_transition_allowed(material.scopes, operation, destination)
actual_boundary_compatible(operation/destination/policy)
```

This is not arithmetic addition even when several dimensions jointly influence a threshold.

### 17.3 Evaluation timing

Evaluation occurs immediately before the relevant governed crossing/dispatch using the actual current destination and actual material being sent.

A later crossing is evaluated again.

Runtime evidence records the values/policy revision required to explain the decision.

### 17.4 Identifiers

References identify records/endpoints for routing and evidence.

The algebra does not treat a reference, handle or token as permission.

A routing lookup and a security decision are separate operations.

## 18. Cross-Module security

A Module-to-Module collaboration has at least two distinct decisions:

```text
semantic decision:
    Module A decides Module B is useful

execution/security decision:
    current boundary factors permit the actual data/action crossing
```

The first belongs to Module intelligence.

The second belongs to MADRE's deterministic execution boundary when the crossing is MADRE-mediated.

The destination Module remains authoritative for its state and exposed Operations.

## 19. Result semantics

Every capability result is initially generated execution output.

Possible Module interpretations include:

```text
display as conversational response
store as draft
feed into another Agent step
validate deterministically
use as candidate Operation input
reject
request independent verification
update a Module-owned WorkPlan
```

Those decisions stay with the Module.

MADRE records only the physical execution outcome and returned payload/evidence required by its contract.

## 20. Persistence boundaries

### Module persistence

Examples:

```text
domain records
conversation state
Agent state
Skill installation
Workflow definitions
WorkPlans
semantic task status
artifacts
knowledge / learning state
```

### MADRE persistence

Examples:

```text
accepted WorkRecords
WorkAttempts
eligibility/scheduling data
idempotency keys
retry/cancellation evidence
capability/model execution selection
runtime metrics
security decision evidence
result/failure payloads or references
```

### Capability persistence

Examples:

```text
model files
provider caches
KV cache / backend state
backend-specific diagnostics
```

## 21. Recovery

Runtime recovery reconstructs physical execution truth.

After restart MADRE can determine which accepted work:

```text
has not yet run
is currently eligible
was interrupted
failed
completed
was cancelled
may be retried under its execution semantics
```

Semantic recovery belongs to the Module.

When a Module restarts it can use its own persisted WorkPlan/correlation state plus MADRE WorkRecords/results to decide what semantic work should happen next.

## 22. Compatibility clients

A conventional AI application may use an OpenAI-compatible or other standard inference facade.

The facade translates that request into an ordinary WorkSubmission.

The compatibility client still owns:

```text
prompt construction
conversation semantics
Agent behavior
result interpretation
```

The facade exposes only the subset of MADRE functionality expressible through that protocol.

Native clients can additionally express timing, scheduling, model/capability requirements and rich boundary factors.

## 23. Local-first and remote execution

Local execution is the preferred high-trust/private tier.

Remote execution is another destination boundary, not a separate semantic path.

A remote candidate may be considered only when the current work's boundary factors and policy permit the transfer.

The runtime should record that remote execution actually occurred.

Model/provider authentication belongs at the capability adapter/host integration boundary.

## 24. Architecture evolution rule

When evaluating a proposed MADRE type or field, ask:

```text
Does Kernel/Runtime need to understand this value to change
admission, scheduling, resource allocation, model/capability selection,
execution, recovery or evidence?
```

If yes, define the smallest execution projection required.

If its purpose is instead to understand a domain, implement Agent behavior, represent a WorkPlan, choose a Workflow, keep memory, learn, present UI or perform domain mutation, keep it in the owning Module.

This rule permits rich native applications without converting MADRE into their common semantic framework.

## 25. Reference flows

### 25.1 One-shot inference from a Module surface

```text
User
 -> Module UI
 -> Module interaction logic
 -> WorkSubmission(input=raw prompt, immediate)
 -> MADRE algebra/scheduler/model selection
 -> Capability
 -> Work result
 -> Module interprets
 -> UI
```

### 25.2 Delayed follow-up reasoning

```text
Module Agent
 -> immediate WorkSubmission
 -> returns current response
 -> Module WorkPlan schedules additional semantic work
 -> delayed WorkSubmission
 -> MADRE executes later
 -> Module receives result
 -> Agent updates Module-owned plan/UI
```

### 25.3 Cross-domain collaboration

```text
Module A Agent
 -> discovers permitted Module B descriptor
 -> requests B projection or explicit B Operation
 -> MADRE evaluates actual crossing if mediated
 -> Module B returns bounded result
 -> Module A Agent interprets it
```

### 25.4 Physical multi-model selection

```text
Module work requirement
       +
current Runtime model/resource evidence
       +
current boundary algebra
       -> MADRE selects eligible physical capability
       -> execution
```

## 26. Review checklist

A change is aligned when all applicable statements remain true:

- User intent is interpreted on a Module-owned surface.
- Semantic routing is performed by Module-owned intelligence.
- Agent/Skill/Workflow/WorkPlan state stays Module-owned.
- Executable work converges on `WorkSubmission` / `WorkRecord` / `WorkAttempt`.
- Immediate and delayed work use the same physical lifecycle.
- Global resource admission remains centralized in MADRE.
- Final physical model/capability selection remains MADRE-controlled within submitted requirements/policy.
- Domain context is projected before crossing the owning Module boundary.
- Security evaluation uses normalized current boundary factors rather than semantic entity authority.
- References are correlation/routing identities.
- Operations remain authoritative in the Module that owns the affected domain.
- Runtime output is generated material until Module logic assigns semantic consequence.
- Persistence follows responsibility.
