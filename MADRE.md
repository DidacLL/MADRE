# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first governed execution layer between AI-capable applications and the computation they use.

MADRE makes intelligence execution manageable runtime work. Applications and their Modules decide what work means; MADRE decides how permitted work is admitted, scheduled, executed, recovered and evidenced across scarce local or explicitly permitted remote capabilities.

The product thesis is:

> Useful intelligence on ordinary personal computers improves when reasoning work can be scheduled, delayed, decomposed by its semantic owner, routed across replaceable models/capabilities, constrained by deterministic security relations, and recovered without forcing every application to implement its own inference runtime.

## 1. Responsibility model

```text
Module / application
    owns user/domain semantics
    owns interaction surfaces
    owns Agents, Skills, Workflows and WorkPlans
    owns domain state and domain mutations
    selects/minimizes material for execution
    interprets generated results
            |
            | explicit executable work
            v
MADRE Runtime
    accepts durable work
    evaluates execution-boundary algebra
    schedules immediate and delayed work
    arbitrates scarce resources globally
    selects permitted model/capability execution paths
    records attempts, outcomes and runtime evidence
    recovers accepted work after interruption
            |
            v
Capability / execution backend
    performs bounded computation
```

The deterministic core of MADRE Runtime is referred to as the **Kernel**. Kernel responsibility is execution control: work, models/capabilities, scheduling, security evaluation, lifecycle and evidence.

## 2. Module

A **Module** is the application/domain integration boundary that uses MADRE.

A Module may be a desktop application, service, script, library, editor integration, local domain system, compatibility adapter or first-party MADRE component. Its internal architecture is its own.

A Module may own, as useful to its product:

- domain data and persistence;
- interaction surfaces and UI state;
- domain semantics and truth/evaluation rules;
- Agents and their state;
- Skills and Workflows;
- semantic planning and WorkPlans;
- context selection, projection and minimization;
- artifacts;
- domain Operations and mutations;
- learning, memory and adaptation.

MADRE sees only the bounded integration surfaces required for execution.

### 2.1 Module integration surface

A native Module may expose some combination of:

```text
Module descriptor
    identity
    human/semantic routing descriptors
    boundary metadata
    optional exposed Operations
    optional context/projection surfaces
    optional local UI projections

MADRE work client
    submit work
    inspect/cancel/retry work
    retrieve results/evidence

optional governed invocation surface
    receive explicit Operation invocations
    return bounded results/evidence
```

Module descriptors support discovery by Module-owned intelligence. They are not a semantic router inside Kernel.

## 3. Interaction surfaces

Users interact with **Module-owned surfaces**.

Examples include a domain application's UI, CLI, editor integration, voice interface or the standard first-party CORE Module UI.

The surface, its assigned Agent, or ordinary Module logic interprets the interaction before MADRE receives executable work.

A simple path may be:

```text
User
  -> Module UI
  -> Module interaction logic
  -> MADRE immediate inference work
  -> result
  -> Module UI
```

The submitted inference input may contain the user's text unchanged when the Module decides that is the correct bounded input. MADRE receives it as execution input, not as an unowned user request requiring semantic routing.

A richer surface may instead decide to use a Workflow, create a WorkPlan, request another Module, invoke a domain Operation, submit delayed reasoning, or combine several of these behaviors.

## 4. Agent

An **Agent** is a Module-local abstraction for reasoning behavior.

An Agent may be implemented as:

- a named/persistent configuration;
- an ephemeral object;
- a stateless function around one model call;
- a state machine;
- a planner/reasoner architecture;
- several collaborating internal actors;
- interaction logic integrated directly into the Module.

MADRE does not require an Agent registry or a universal Agent persistence model. A Module may expose no separate Agent configuration at all while still using agentic reasoning internally.

Agent identity, prompts, state, memory, Skill repertoire, Workflow repertoire and learning semantics remain Module concerns.

An Agent is above models: it decides why computation is useful and how results are interpreted. Models/capabilities are physical resources MADRE may use to execute the requested computation.

## 5. Skills and Workflows

**Skills** and **Workflows** are part of a Module's Agent architecture.

A Module may define portable formats, versions, user customization, imported Skill packages, reusable Workflows or no explicit Skill/Workflow system.

When an Agent selects a Skill or Workflow, that selection has semantic meaning inside the Module. Executable portions are submitted to MADRE as ordinary work.

MADRE scheduling does not depend on understanding Skill or Workflow semantics.

## 6. WorkPlan

A **WorkPlan** is semantic planning state owned by the Module/Agent that created it.

A WorkPlan may describe an objective, decomposition, dependencies, verification strategy, chosen Workflows, expected outputs, application coordination and interpretation of intermediate results.

The semantic owner decides:

- whether a plan is necessary;
- which steps/tasks exist;
- why dependencies exist;
- when new work should be added;
- how results revise the plan;
- when the objective is complete.

Executable portions of a WorkPlan become MADRE `WorkSubmission`s.

```text
Module-owned WorkPlan
    step/task A -> WorkSubmission A
    step/task B -> WorkSubmission B
    step/task C -> WorkSubmission C
```

MADRE may receive opaque correlation/group metadata linking these work items. It interprets only scheduling fields that have execution meaning.

If a scheduling policy needs to know that several work items form one attention/fairness group, the Module may submit an explicit scheduling-group relation. The rationale behind the group remains opaque.

A Module that requires restart-safe semantic planning persists the WorkPlan in its own state. MADRE independently preserves every accepted durable work item and its execution evidence.

## 7. Runtime work

`WorkSubmission`, `WorkRecord` and `WorkAttempt` are MADRE's durable execution foundation.

Immediate and delayed work converge on the same lifecycle.

A work submission carries only information needed to execute correctly, such as:

- originating Module/application for accounting, return routing and correlation;
- execution input;
- capability/model requirements;
- eligibility time;
- scheduling priority/budget/resource constraints;
- execution-boundary factors;
- cancellation/retry/idempotency semantics where applicable;
- opaque semantic correlation metadata.

Once accepted, work becomes durable runtime state.

MADRE owns:

- acceptance and idempotent submission behavior;
- immediate/delayed eligibility;
- global scheduling and fairness;
- scarce-resource admission;
- capability/model execution choice;
- attempts;
- cancellation and retry;
- interruption/restart recovery;
- physical result/failure truth;
- runtime evidence.

The originating Module owns the meaning of the result.

## 8. Delayed Reasoning Effort

**Delayed Reasoning Effort (DRE)** is the ability to control intelligence work in time while preserving reliable execution.

A Module may:

- answer immediately and schedule follow-up reasoning;
- schedule work without producing an immediate answer;
- continue interacting while background work runs;
- submit several independent or correlated jobs;
- request later verification, synthesis or evidence processing;
- cancel or retry eligible work according to truthful execution semantics.

The Module/Agent decides why delayed work is valuable. MADRE provides the durable timing, execution and recovery behavior.

Delayed work is therefore an execution property rather than a universal semantic progression such as “fast then deeper.”

## 9. Models and capabilities

A **Capability** is a replaceable bounded computation provider used by MADRE Runtime.

Important capability classes include model inference, embeddings, speech, image processing and other specialized computation. A capability may be implemented by a local library, local process, sidecar, service or explicitly permitted remote provider.

A Module submits semantic execution requirements; MADRE combines them with runtime evidence.

Examples of Module-provided requirements:

```text
local-only
interactive latency preferred
high reasoning quality required
specific modality
minimum context capacity
exact model required
background execution acceptable
```

Examples of Runtime evidence:

```text
model residency
RAM / VRAM pressure
load/swap cost
context capacity
recent latency / throughput
failure history
other eligible work
resource admission state
```

MADRE makes the final physical execution-path decision among permitted candidates. An exact requested target either executes if admissible/available under policy or returns a truthful rejection/failure.

Provider-specific request/response dialects remain at capability adapters. The MADRE architecture is not defined by a universal chat-message protocol.

Capability output is generated material. Its semantic value, truth status and domain consequence are assigned by Module-owned software.

## 10. Operations

An **Operation** is bounded functionality a Module intentionally exposes for governed invocation by another Module/CORE behavior.

Operations remain Module-owned because the Module owns the domain and any durable mutation.

An exposed Operation has a typed boundary describing at least what is required for safe invocation:

- identity/routing reference;
- typed input/output contract;
- execution-boundary factors;
- effect/repeatability semantics;
- scope transition information.

Semantic intelligence chooses whether an Operation is useful. A MADRE-mediated invocation then performs deterministic admission, dispatch and evidence recording.

Private functions used entirely inside a Module remain ordinary Module implementation and require no MADRE Operation representation.

Generated model output cannot itself mutate domain state. Module-owned software converts a proposed action into an explicit Operation invocation when appropriate.

## 11. Context and material

A Module owns its source data and determines the actual material needed for a piece of intelligence work.

Before material crosses a Module boundary, the Module can project/minimize it according to its domain semantics and intended use.

```text
Module private state
    -> domain-aware projection/minimization
    -> bounded execution material
    -> MADRE work
    -> selected capability
```

MADRE retains only the material or references required to execute/recover accepted work.

When a transformation produces materially different content, the derived material receives its own current boundary factors and provenance. The source material remains unchanged.

Local artifact generation, local observation and external transfer are distinct boundaries. A Module may create sensitive local artifacts while requiring a stronger boundary relation for sending/uploading/exporting those artifacts externally.

## 12. Security algebra

MADRE security is deterministic **algebraic admissibility** over the current execution-boundary values involved in an attempted crossing.

The normalized level vocabulary is:

```text
SecurityLevel
    SYSTEM_RESERVED = 0
    LEVEL_1 = 1
    LEVEL_2 = 2
    LEVEL_3 = 3
    LEVEL_4 = 4
    LEVEL_5 = 5
```

Ordinary values use levels 1..5. Level `0` is reserved for system/kernel representation.

The initial independent dimensions are:

```text
sensitivity    higher -> more sensitive material
trust          higher -> more trusted material/actor/destination
risk           higher -> more execution/action risk
```

These dimensions share a normalized range for simple deterministic comparison. They are not added, averaged or collapsed into one score.

Module/domain scope is a separate non-numeric relation.

For an attempted execution, the crossing carries the current boundary values required for evaluation, conceptually including:

```text
MaterialBoundary
    sensitivity
    trust
    scopes

ActorBoundary
    trust
    maximum_handled_sensitivity
    execution_risk

OperationBoundary (when present)
    risk
    minimum_input_trust
    maximum_input_sensitivity
    source/destination scopes
    execution boundary
    effect/classification-transform properties

DestinationBoundary
    trust
    execution_risk
    local / isolated / remote boundary

CurrentPolicy
    dimension-specific thresholds/relations
```

The Kernel performs a pure deterministic relation over these current values.

At minimum, admissibility can require relations such as:

- material sensitivity fits the actor handling ceiling;
- material trust meets the attempted operation/capability input floor;
- material sensitivity fits the target input ceiling;
- actor risk and operation risk fit policy for the actual material and destination;
- source/destination scopes are compatible with the explicit boundary relation;
- the actual execution boundary matches the permitted boundary;
- a sensitivity reduction corresponds to an actual transformation producing new material.

Policy may use one dimension to select a threshold in another dimension. That is still a relation, not arithmetic aggregation.

The evaluation is repeated for every governed crossing. A previous accepted decision is evidence of that earlier evaluation only.

References and IDs are used for routing/correlation/evidence. Security admissibility comes from the current boundary values, never from possession of a reference.

## 13. Cross-Module collaboration

Cross-Module semantic decisions are made by Module-owned intelligence.

A Module/Agent may inspect permitted Module descriptors and decide to:

- request bounded context from another Module;
- invoke an exposed Operation;
- send a semantic request to another Module-owned surface/agent contract;
- keep semantic responsibility locally while using another Module's Operations;
- combine independently minimized projections from several domains.

MADRE provides registration, governed transport/execution and runtime evidence. It executes an explicit destination chosen by the semantic owner.

Domain authority remains with each participating Module.

## 14. CORE

**CORE** is the standard first-party general intelligence Module commonly configured as the installation's default Module/surface.

CORE may own its own:

- interaction UI;
- generic Agents;
- Skills and Workflows;
- semantic planning/WorkPlans;
- cross-domain coordination logic;
- context reduction logic;
- conversation or long-lived state.

CORE submits computation through the same MADRE Runtime boundary as every other Module.

The Module registry may expose which registered Module is configured as the default. Interaction/integration surfaces use that registration when they require the installation's generic Module. Default status is a routing convention at the Module/surface layer, not an alternate runtime authority path.

CORE can receive richer context than a remote capability when the current boundary algebra permits it, while every later transfer/execution is independently evaluated.

## 15. Persistence ownership

Persistence follows why state exists:

| Purpose | Owner |
| --- | --- |
| Domain data, semantics, truth, artifacts and mutations | Module |
| UI/surface state and conversation semantics | Module |
| Agents, Skills, Workflows, memory and learning | Module |
| WorkPlans and semantic decomposition | Module |
| Accepted executable work and scheduling state | MADRE Runtime |
| Work attempts, cancellation/retry/recovery state | MADRE Runtime |
| Security-decision and execution evidence | MADRE Runtime |
| Runtime model/capability telemetry used for selection | MADRE Runtime |
| Provider/model implementation state and caches | Capability/backend |

Physical storage location does not change semantic ownership.

## 16. Observability and result delivery

MADRE exposes execution provenance rather than hidden semantic reasoning.

Runtime evidence can include:

- originating Module/application;
- opaque semantic correlation;
- submission and eligibility times;
- scheduling/admission decisions;
- selected capability/model;
- boundary-algebra decision evidence;
- attempts;
- cancellation/retry/recovery evidence;
- timing/resource metrics;
- result/failure references.

The Module decides how that evidence and generated result are presented to users and how they affect its domain state.

## 17. Integration depth

MADRE supports different application integration depths while preserving one runtime execution path.

```text
Compatibility client
    -> standard inference-compatible facade
    -> MADRE work

Native Module
    -> richer WorkSubmission with timing/resource/boundary semantics
    -> MADRE work

Module-to-Module collaboration
    -> explicit Module-owned semantic decision
    -> governed Operation/context/work boundary
    -> MADRE execution where applicable
```

A richer integration adds execution metadata; it does not transfer domain ownership into MADRE.

## 18. Architectural tests

A proposed core concept belongs in MADRE when its meaning changes at least one of:

```text
admission
security/boundary evaluation
scheduling
resource allocation
capability/model selection
physical execution
retry/cancellation/recovery
runtime evidence
```

A concept whose meaning is primarily about understanding, planning, remembering, presenting, learning or mutating a domain belongs to the Module that owns those semantics.

When a concern spans both sides, the architecture defines a narrow boundary representation carrying only the execution-relevant projection.

## 19. Durable invariants

1. Users interact through Module-owned surfaces.
2. Modules decide semantic intent before submitting executable work.
3. Agents are Module-local abstractions above model execution.
4. Skills and Workflows remain inside Module Agent architectures.
5. WorkPlans are semantically owned by their creating Module/Agent.
6. MADRE's durable primitive is executable work, not semantic planning state.
7. Immediate and delayed work share one runtime lifecycle.
8. MADRE owns global resource admission and final permitted physical model/capability selection.
9. Domain source data is projected/minimized before crossing its owning Module boundary when needed.
10. Security is recomputed from current boundary values at every governed crossing.
11. References are correlation/routing identities rather than execution authority.
12. Module Operations remain authoritative for domain mutation.
13. Capability/model output is generated data until Module-owned software gives it semantic consequence.
14. CORE is an ordinary first-party Module using the ordinary MADRE execution plane.
15. Runtime evidence records execution truth without absorbing application semantics.
16. Consumer-hardware scarcity is a primary scheduling/resource design input.
17. New MADRE abstractions are justified by executable runtime behavior rather than by completeness of an Agent framework.

The detailed responsibility model and concrete execution-boundary contract are maintained in:

- `docs/architecture/MADRE-execution-architecture.md`
- `docs/architecture/MADRE-execution-contract.md`
