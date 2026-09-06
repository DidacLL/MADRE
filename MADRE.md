# MADRE

This file is the canonical product definition for **MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System**.

MADRE provides a coherent, privacy-aware and user-controlled way to use the permitted intelligence capabilities available to a user, including local models, tools, application-provided intelligence and authorized external services.

Its runtime makes **reasoning effort itself manageable runtime work**: work can execute immediately or become durable work whose eligibility, execution, recovery and evidence remain under software control. Local/private execution is a first-class high-trust, offline-capable execution tier, not MADRE's entire product boundary.

The product supports two complementary paths that converge on the same runtime:

```text
Application ───────────────→ MADRE runtime → Capability

User → CORE ───────────────→ MADRE runtime → Capability
          ↕
      Applications
```

Applications may use MADRE directly without CORE. CORE, when implemented, uses the ordinary MADRE runtime rather than bypassing execution control.

The ownership invariant is:

```text
Applications own domains.
CORE owns default generic/system intelligence.
MADRE Runtime owns execution.
Capabilities perform computation.
```

## System roles

### Application

An application is an independent domain system using MADRE directly or collaborating with CORE. It may be a script, desktop application, service or larger local system.

The application owns its domain semantics and the state whose purpose is domain work, including as applicable:

- authoritative domain data and persistence;
- knowledge, memory, retrieval, provenance and truth semantics;
- learning, evaluation and adaptation of domain behavior;
- domain workflows and task decomposition;
- domain-specific agents, prompts and interaction logic;
- user interface, artifacts and domain operations;
- selection of material relevant to a reasoning task;
- semantic privacy projection, minimization and classification;
- interpretation and acceptance of generated results.

The application decides what its work means, what domain information may be disclosed and which domain mutations may occur. Integration with MADRE preserves that ownership.

### CORE

CORE is MADRE's future first-party generic/system intelligence layer. As real product needs establish them, CORE may provide reusable behaviors such as interaction, planning and cross-domain coordination that should not have to be reimplemented by every application.

CORE does not own application domains. It can reason over only the context and actions an application chooses to expose, and application-owned software remains authoritative over domain state and mutations.

CORE uses the same MADRE runtime execution plane as applications. Its higher trust does not grant an alternate execution path or unrestricted access to application data.

Current CORE interaction surfaces used to exercise this path are experimental product-development probes, not a stable MADRE user-experience contract. Their prompts, commands, response staging and presentation may change as concrete CORE behavior develops; provisional chat UX must not define or constrain runtime, Agent or application architecture.

### MADRE runtime

MADRE provides reusable execution semantics for permitted intelligence work submitted by applications or CORE. Its responsibilities include, as the working system requires them:

- accepting and identifying work;
- immediate and delayed scheduling;
- eligibility time, priority, budget and execution constraints;
- durable work state;
- execution and recovery after interruption;
- invocation of configured inference, tool and other capability implementations;
- active execution and transfer-boundary enforcement;
- cancellation, retry and failure handling;
- resource allocation, scarce-resource admission and concurrency;
- final permitted execution-path/capability selection as concrete product needs establish it;
- runtime trace and inspection;
- returning results and runtime evidence to the originating application or CORE behavior.

MADRE state exists to execute, recover and inspect runtime work. Application state exists to represent and operate the domain. CORE state, when introduced, exists to support generic/system intelligence rather than to absorb domain ownership.

### Capability

A capability is a replaceable implementation that performs bounded computation for MADRE. Inference engines, deterministic tools, application-provided intelligence, specialized services and authorized external intelligence are capability examples.

Capabilities may be local libraries, local processes, services or remote endpoints. Their provider-specific mechanics belong to their integration boundary. MADRE presents the stable runtime semantics required by applications and CORE.

MADRE should reuse mature external implementations below this boundary when they remove implementation burden without taking away a responsibility MADRE needs to own.

Capability output is generated data. Software in the application, CORE and runtime assigns any subsequent permission, trusted status, mutation or workflow consequence.

## Product requirements

### R1 — Application autonomy

An application can use MADRE without transferring ownership of its domain model, persistence, knowledge, workflows, UI or domain policy into the runtime or CORE.

The integration surface carries the material and execution intent required for a piece of work while leaving domain interpretation with the application.

### R2 — Runtime work as a first-class concept

Intelligence execution is represented as runtime work with an identifiable lifecycle. MADRE can know which application or CORE behavior originated the work, whether it is eligible to execute, which capability is used, what execution outcome occurred and what runtime evidence belongs to that work.

The concrete API and internal types are implementation decisions. The lifecycle semantics are product behavior.

### R3 — Delayed Reasoning Effort

Reasoning effort can be controlled in time.

The same runtime concept supports work eligible immediately and work eligible later. Durable delayed work preserves enough execution intent to remain actionable across process interruption and restart.

An application or CORE behavior may decompose a larger semantic objective into several reasoning jobs. MADRE schedules and executes those submitted jobs while the semantic owner retains the meaning connecting them.

DRE is therefore a scheduling and execution property. Planning and decomposition mechanisms are introduced where an application or CORE behavior demonstrates their value.

On consumer hardware, MADRE seeks useful intelligence through scheduling, delayed reasoning, decomposition, context minimization, selective stronger reasoning, deterministic tools and accumulated execution evidence. Efficient local inference is an important part of that strategy, not the full reason MADRE exists.

### R4 — Replaceable capabilities

MADRE can invoke different compatible capability implementations while preserving the originating application's or CORE behavior's runtime contract.

A provider, model, inference runtime, tool, transport or framework may shape its adapter and may become a stable implementation dependency when it is the appropriate engineering choice. MADRE product semantics remain defined by the responsibilities in this document rather than by accidental properties of one provider or by assuming every task maps directly to an LLM model.

### R5 — Explicit authority

Authority follows software ownership:

1. the user and applications establish intent and domain authority;
2. applications select what domain material and operations they expose;
3. CORE may provide generic/system reasoning or coordination within those exposed boundaries;
4. MADRE controls runtime execution according to the submitted intent, authorization and execution constraints;
5. capabilities compute and return results;
6. application-owned software decides any domain consequence or mutation.

A generated proposal can therefore participate in planning, classification or action selection while remaining generated material until software gives it an effect.

### R6 — Application-governed context

The source application determines which domain material is relevant for a reasoning operation and performs domain-specific projection or minimization before that material crosses the application boundary when needed.

CORE may further minimize task context before a less-trusted execution boundary. Its high trust does not imply unrestricted access to application state.

MADRE receives selected material together with execution-relevant constraints. Domain vocabulary and privacy semantics stay with the application; the runtime needs only the information required to execute the work correctly.

This allows different applications to use different domain models without requiring MADRE or CORE to understand their internal records.

### R7 — Execution and transfer boundaries

Execution-relevant constraints are applied where they affect actual capability use.

MADRE can decide whether a configured capability is eligible for a work item, whether submitted material may cross the required transfer boundary, whether an operation may execute and which runtime resources are available.

Availability of a capability, provider account or external service does not imply authorization to send arbitrary data to it. Application disclosure, user authorization, task need and execution-boundary constraints must permit the transfer and execution.

MADRE should prefer the safest permitted execution path that satisfies the task while keeping the user in control of data transfer and external execution.

Boundary representation follows active execution needs. The working design carries each constraint at the narrowest place where it can change runtime behavior.

### R8 — Domain operations remain application-owned

Applications expose the domain operations they choose to make available to AI-assisted execution. MADRE or CORE may request such operations through an explicit application-owned boundary.

A durable domain mutation is performed by application-owned software. Generated output can supply proposed inputs or decisions while the application operation remains the authority that changes domain state.

### R9 — Persistence follows purpose

State ownership is determined by why the state exists:

| Purpose | Owner |
| --- | --- |
| Represent, remember, retrieve, interpret or learn domain information | Application |
| Provide generic interaction, planning or cross-domain coordination | CORE |
| Schedule, execute, recover, control or inspect runtime work | MADRE Runtime |
| Implement the internal operation or cache of an inference/tool integration | Capability |

This rule remains valid regardless of which process or storage engine physically writes the bytes.

### R10 — Durable execution has explicit recovery semantics

When work is durable, restart and interruption have defined runtime outcomes. MADRE records enough state to determine what remains eligible, what attempt occurred and what result or failure is available.

Delivery and retry guarantees match what the invoked capability can actually support. Runtime evidence describes the behavior that occurred rather than promising stronger semantics than the execution boundary can provide.

### R11 — Inspectability

Runtime work can be inspected through evidence proportional to its execution semantics. Evidence can identify lifecycle transitions, attempts, capability use, timing, results and failures as required by the working path.

Inspection serves debugging, recovery, application integration and research reproducibility. It records execution facts rather than replacing application knowledge.

### R12 — Local-first execution

Local/private execution is a first-class execution and trust tier. A useful MADRE installation can operate with locally available capabilities without requiring a cloud account or remote-service credential for its core runtime path.

An application can keep private work on the local machine when its boundary requires that. Local execution is especially valuable for high-trust and offline-capable work and for avoiding unnecessary data disclosure.

Prefer local and free capabilities. For paid cloud services, provider-managed authentication through supported clients or hosts is the preferred connection approach, with authentication mechanics at the capability boundary. Prefer keeping provider-account credentials with that client or host over collecting them in MADRE. Direct API-key integration remains available when explicitly chosen; provider credentials are not a prerequisite for the core runtime.

Authorized external capabilities can coexist with local ones. Remote use is represented as a real transfer boundary so the runtime can apply the constraints supplied for that work and record the execution accurately.

### R13 — Evolvable implementation

Concrete technologies are legitimate implementation architecture. Languages, databases, libraries, transports, frameworks and provider integrations can be selected, depended on and optimized around when they provide the clearest working solution.

Product responsibilities remain stable while implementation structures may be added, merged, renamed or replaced as evidence from the running system develops.

A new architectural element earns its place by making an observed responsibility simpler, clearer, safer, more reliable or measurably more capable.

## Ownership test

A future design decision can be located by asking why it exists:

- If it exists to understand, remember, decide, present or mutate a domain, it is application responsibility.
- If it exists to provide default generic interaction, planning or cross-domain coordination, it is CORE responsibility.
- If it exists to schedule, execute, constrain, recover, select a permitted execution path or inspect runtime work, it is MADRE Runtime responsibility.
- If it exists to perform a particular computation or provider-specific operation, it belongs to a capability implementation or its adapter.
- If it is produced by a capability, it is data until application/CORE/runtime software assigns an effect.

When a responsibility spans these roles, define the narrow integration boundary between the owners rather than moving the whole concern into one side.

## Reference interaction

Applications can invoke the runtime directly:

```text
Application
    │ selected domain material + execution intent
    ▼
MADRE runtime
    │ scheduled permitted invocation
    ▼
Capability
    │ generated result / failure
    ▼
MADRE runtime
    │ result + runtime evidence
    ▼
Application
    │
    ▼
domain interpretation / operation
```

CORE can also originate generic/system work and coordinate with applications without taking over their domains:

```text
User
  │
  ▼
CORE ↔ Applications
  │       bounded context/actions
  │
  ▼
MADRE runtime
  │
  ▼
Capability
```

Both paths use the same runtime execution authority.

## Product acceptance path

MADRE becomes useful through progressively stronger end-to-end evidence:

1. **Real execution:** an application can invoke a real configured local inference capability through MADRE and receive its result or an accurate runtime error.
2. **Delayed execution and resource authority:** the same kind of work can be accepted for later execution, survive runtime restart and share global scarce-resource admission with immediate work.
3. **First-party intelligence:** CORE can operate as the first real first-party MADRE application through the ordinary runtime boundary, initially with only the smallest useful behavior needed to exercise that path without freezing the eventual user experience.
4. **Independent application integration:** other applications can select their own context, submit work directly or expose bounded context/actions to CORE while retaining domain state and semantics.
5. **Boundary growth from use:** additional capabilities, permitted execution-path selection, execution controls and orchestration patterns extend those working paths as concrete applications or experiments require them.

These are behavioral acceptance goals. The implementation is free to choose the simplest suitable technologies and structures that realize them.
