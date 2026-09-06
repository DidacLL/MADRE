# MADRE

This file is the canonical product definition for **MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System**.

MADRE is a local-first execution runtime for independent applications that use AI capabilities. Its distinctive responsibility is to make **reasoning effort itself manageable runtime work**: work can execute immediately or become durable work whose eligibility, execution, recovery and evidence remain under software control.

The product boundary is intentionally simple:

```text
Application  →  MADRE runtime  →  Capability
   meaning        execution         computation
```

The application owns the domain. MADRE owns the runtime lifecycle of submitted work. Capabilities perform bounded computation and return results.

## System roles

### Application / module

A MADRE module is an independent application using the runtime. It may be a script, desktop application, service or larger local system.

The application owns its domain semantics and the state whose purpose is domain work, including as applicable:

- authoritative domain data and persistence;
- knowledge, memory, retrieval, provenance and truth semantics;
- learning, evaluation and adaptation of domain behavior;
- domain workflows and task decomposition;
- domain agents, prompts and interaction logic;
- user interface, artifacts and domain operations;
- selection of material relevant to a reasoning task;
- semantic privacy projection, minimization and classification;
- interpretation and acceptance of generated results.

The application decides what its work means. Integration with MADRE preserves that ownership.

### MADRE runtime

MADRE provides reusable execution semantics for AI work submitted by applications. Its responsibilities include, as the working system requires them:

- accepting and identifying work;
- immediate and delayed scheduling;
- eligibility time, priority, budget and execution constraints;
- durable work state;
- execution and recovery after interruption;
- invocation of configured inference and tool capabilities;
- active execution and transfer-boundary enforcement;
- cancellation, retry and failure handling;
- resource allocation and concurrency;
- runtime trace and inspection;
- returning results and runtime evidence to the originating application.

MADRE state exists to execute, recover and inspect runtime work. Application state exists to represent and operate the domain.

### Capability

A capability is a replaceable implementation that performs bounded computation for MADRE. Inference engines, model providers, tools and external services are capability examples.

Capabilities may be local libraries, local processes, services or remote endpoints. Their provider-specific mechanics belong to their integration boundary. MADRE presents the stable runtime semantics required by applications.

Capability output is generated data. Software in the application and runtime assigns any subsequent permission, trusted status, mutation or workflow consequence.

## Product requirements

### R1 — Application autonomy

An application can use MADRE without transferring ownership of its domain model, persistence, knowledge, workflows, UI or domain policy into the runtime.

The integration surface carries the material and execution intent required for a piece of work while leaving domain interpretation with the application.

### R2 — Runtime work as a first-class concept

AI execution is represented as runtime work with an identifiable lifecycle. MADRE can know which application submitted the work, whether it is eligible to execute, which capability is used, what execution outcome occurred and what runtime evidence belongs to that work.

The concrete API and internal types are implementation decisions. The lifecycle semantics are product behavior.

### R3 — Delayed Reasoning Effort

Reasoning effort can be controlled in time.

The same runtime concept supports work eligible immediately and work eligible later. Durable delayed work preserves enough execution intent to remain actionable across process interruption and restart.

An application may decompose a larger domain workflow into several reasoning jobs. MADRE schedules and executes those submitted jobs while the application retains the semantics connecting them.

DRE is therefore a scheduling and execution property. Planning and decomposition mechanisms are introduced where an application or runtime behavior demonstrates their value.

### R4 — Replaceable capabilities

MADRE can invoke different compatible capability implementations while preserving the application's runtime contract.

A provider, model, inference runtime, transport or framework may shape its adapter and may become a stable implementation dependency when it is the appropriate engineering choice. MADRE product semantics remain defined by the responsibilities in this document rather than by accidental properties of one provider.

### R5 — Explicit authority

Authority follows software ownership:

1. the user and application establish domain intent;
2. the application selects domain material and requested operations;
3. MADRE controls runtime execution according to the submitted intent and execution constraints;
4. capabilities compute and return results;
5. the application decides the domain consequence of those results.

A generated proposal can therefore participate in planning, classification or action selection while remaining generated material until software gives it an effect.

### R6 — Application-governed context

The application determines which domain material is relevant for a reasoning operation and performs domain-specific projection or minimization before submission when needed.

MADRE receives the selected material together with execution-relevant constraints. Domain vocabulary and privacy semantics stay with the application; the runtime needs only the information required to execute the work correctly.

This allows different applications to use different domain models without requiring MADRE to understand their internal records.

### R7 — Execution and transfer boundaries

Execution-relevant constraints are applied where they affect actual capability use.

MADRE can decide whether a configured capability is eligible for a work item, whether submitted material may cross the required transfer boundary, whether an operation may execute and which runtime resources are available.

Boundary representation follows active execution needs. The working design carries each constraint at the narrowest place where it can change runtime behavior.

### R8 — Domain operations remain application-owned

Applications expose the domain operations they choose to make available to AI-assisted execution. MADRE may invoke such operations through an explicit runtime capability boundary.

A durable domain mutation is performed by application-owned software. Generated output can supply proposed inputs or decisions while the application operation remains the authority that changes domain state.

### R9 — Persistence follows purpose

State ownership is determined by why the state exists:

| Purpose | Owner |
| --- | --- |
| Represent, remember, retrieve, interpret or learn domain information | Application |
| Schedule, execute, recover, control or inspect runtime work | MADRE |
| Implement the internal operation or cache of an inference/tool integration | Capability |

This rule remains valid regardless of which process or storage engine physically writes the bytes.

### R10 — Durable execution has explicit recovery semantics

When work is durable, restart and interruption have defined runtime outcomes. MADRE records enough state to determine what remains eligible, what attempt occurred and what result or failure is available.

Delivery and retry guarantees match what the invoked capability can actually support. Runtime evidence describes the behavior that occurred rather than promising stronger semantics than the execution boundary can provide.

### R11 — Inspectability

Runtime work can be inspected through evidence proportional to its execution semantics. Evidence can identify lifecycle transitions, attempts, capability use, timing, results and failures as required by the working path.

Inspection serves debugging, recovery, application integration and research reproducibility. It records execution facts rather than replacing application knowledge.

### R12 — Local-first execution

Local execution is a first-class useful path. An application can use MADRE with locally available capabilities while keeping private work on the local machine when its boundary requires that.

Remote capabilities can coexist with local ones. Remote use is represented as a real transfer boundary so the runtime can apply the constraints supplied for that work and record the execution accurately.

### R13 — Evolvable implementation

Concrete technologies are legitimate implementation architecture. Languages, databases, libraries, transports, frameworks and provider integrations can be selected, depended on and optimized around when they provide the clearest working solution.

Product responsibilities remain stable while implementation structures may be added, merged, renamed or replaced as evidence from the running system develops.

A new architectural element earns its place by making an observed responsibility simpler, clearer, safer, more reliable or measurably more capable.

## Ownership test

A future design decision can be located by asking why it exists:

- If it exists to understand, remember, decide, present or mutate the domain, it is application responsibility.
- If it exists to schedule, execute, constrain, recover or inspect submitted AI work, it is MADRE responsibility.
- If it exists to perform a particular computation, it belongs to a capability implementation or its adapter.
- If it is produced by a capability, it is data until application/runtime software assigns an effect.

When a responsibility spans these roles, define the narrow integration boundary between the owners rather than moving the whole concern into one side.

## Reference interaction

```text
independent application
    │
    │ selected domain material
    │ requested computation
    │ execution-relevant constraints
    ▼
MADRE runtime
    │
    │ scheduled bounded invocation
    ▼
capability
    │
    │ generated result / failure
    ▼
MADRE runtime
    │
    │ result + runtime evidence
    ▼
originating application
    │
    ▼
domain interpretation / operation
```

## Product acceptance path

MADRE becomes useful through progressively stronger end-to-end evidence:

1. **Real execution:** an application can invoke a real configured local inference capability through MADRE and receive its result or an accurate runtime error.
2. **Delayed execution:** the same kind of work can be accepted for later execution, survive runtime restart and expose its eventual result or recoverable failure.
3. **Independent application integration:** a real application can select its own context, submit work and consume results while retaining its domain state and semantics.
4. **Boundary growth from use:** additional capabilities, execution controls, resource policies and orchestration patterns extend those working paths as concrete applications or experiments require them.

These are behavioral acceptance goals. The implementation is free to choose the simplest suitable technologies and structures that realize them.
