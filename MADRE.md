# MADRE — Product Definition and Invariants

This file is the active product authority for MADRE.

The **Product invariants** below define MADRE independently of any particular implementation, class map, framework, provider, experiment, or previous design. Code and implementation plans may refine how these invariants are realized, but they do not redefine them implicitly.

Historical documents, deleted files, Git history, research reports, issues, pull requests and prior conversations may explain how a conclusion was reached. They are not additional product authority. A concept from those sources is part of current MADRE only when it is represented by the active definition here or is required by working behavior that is consistent with it.

## Identity

MADRE is the **Model-Agnostic Delayed Reasoning Effort Agentic System**: a local-first runtime/kernel that independent applications use to execute AI work under software control, immediately or at another time.

Its defining thesis is **Delayed Reasoning Effort (DRE)**. Reasoning is work whose execution effort can be controlled in time rather than being assumed to be one synchronous model call. Work may be accepted, made eligible now or later, persisted when durability is required, executed through configured capabilities, interrupted, recovered, inspected and returned to the originating application.

MADRE sits between independent applications and execution capabilities such as inference engines, model providers and tools. The application owns the domain. MADRE owns the runtime execution lifecycle. Capabilities perform bounded computation.

## Product invariants

These are semantic invariants. Their meaning must survive implementation changes.

### 1. A MADRE module is an independent application

`module` is the architectural role of an application that uses MADRE. It does not imply inheritance from a MADRE base class, containment inside the runtime, a particular process topology, or a mandatory internal object model.

An application owns the meaning of its domain. This includes, when relevant:

- authoritative domain data and its persistence;
- knowledge, memory, retrieval, provenance and truth semantics;
- learning, evaluation, adaptation and promotion of improved domain behavior;
- domain workflows and task decomposition;
- domain agents, prompts and interaction logic;
- UI, artifacts and domain-specific actions;
- selection of the domain material relevant to a piece of reasoning work;
- semantic privacy projection, minimization and domain classification.

An application may be a script, a desktop application, a service or a larger system such as AAAAT. MADRE does not require these responsibilities to be relocated into the runtime.

### 2. MADRE owns execution, not domain meaning

MADRE owns the reusable runtime concerns required to execute submitted AI work under software control. Depending on demonstrated needs, these include:

- accepting and identifying runtime work;
- immediate and delayed scheduling;
- timing, priority, budget and execution constraints;
- durable runtime work state and recovery after interruption;
- invoking configured inference and tool capabilities;
- replaceable capability bindings;
- enforcement of execution and transfer constraints when a capability is actually used;
- runtime cancellation, retry and failure handling;
- resource allocation and concurrency when real workloads require them;
- trace and inspection of work lifecycle, attempts, capability use and results;
- returning results and runtime evidence to the originating application.

MADRE may persist the runtime state needed to perform those responsibilities. That state does not become application knowledge merely because MADRE stores it.

### 3. Capabilities are replaceable and non-authoritative

Inference engines, model providers and tools are execution capabilities. They may be local libraries, local processes, services, remote endpoints or other replaceable implementations.

A capability may compute, generate, retrieve or transform material only within the execution boundary granted to it. Provider-specific concepts do not become MADRE product semantics merely because an adapter exposes them.

Generated model or tool output is **data**. By being generated, it does not acquire permission, policy authority, trusted knowledge status, workflow ownership, mutation authority or the ability to redefine MADRE behavior.

### 4. Delayed Reasoning Effort is runtime semantics

Immediate and delayed reasoning are scheduling cases of the same product concept: reasoning effort is runtime work whose eligibility and lifecycle are controlled by software.

An application may submit work that is eligible immediately or at another time. MADRE preserves the execution intent required to run accepted work, executes it when eligible under the applicable runtime constraints, records the runtime outcome when durability or inspection requires it, and makes the result available to the application.

DRE does not require a cognitive ontology, planner hierarchy, workflow language or multi-agent architecture. Such mechanisms may be used by an application or introduced experimentally when demonstrated behavior requires them; they are not implied by DRE itself.

### 5. Applications own workflow meaning; MADRE owns submitted-work lifecycle

An application decides what a domain task means, how a domain workflow is decomposed, what evidence is relevant and what should happen with a result.

MADRE may schedule several submitted jobs that belong to one application workflow, but scheduling those jobs does not transfer ownership of the workflow semantics to MADRE.

A model may propose a plan, action, classification or next step when an application requests that computation. The proposal remains generated material until ordinary software accepts and uses it through the application's logic and the applicable runtime execution boundary.

### 6. Context meaning belongs to the application; execution constraints are enforced at use

The application selects the domain material submitted for reasoning and owns the semantic interpretation of that material. MADRE must not need to understand what a candidature, experiment, thesis fact, CV field or other domain record means in order to execute work correctly.

The application may provide the execution-relevant constraints required for safe handling of submitted material. MADRE enforces constraints where they become operational: for example, when choosing a permitted capability, transferring material across a local/remote boundary, invoking an action, or consuming runtime resources.

Boundary information belongs where it changes active execution or transfer. Its existence is not a reason to duplicate equivalent policy state across every passive request, plan, workflow or record.

### 7. Persistence ownership follows purpose

The owner of persisted state is determined by **why the state exists**, not by which process happens to write the bytes.

- If state exists because the domain must remember, interpret, retrieve, learn from or govern it, the application owns it.
- If state exists because runtime work must be scheduled, executed, recovered, inspected or controlled, MADRE owns it.
- If state is an implementation cache or internal detail of an inference/tool capability, that capability owns it unless a stronger product requirement says otherwise.

This rule prevents runtime persistence from silently becoming a generic memory or knowledge subsystem.

### 8. Local-first and model-agnostic are architectural properties

MADRE must treat local execution as a first-class path and must not require one remote provider to define the product. A useful local path must be possible without making a cloud account, provider-specific agent platform or hosted control plane the architectural authority.

Remote inference or remote tools may be supported. Crossing that boundary is an explicit execution decision subject to the relevant transfer constraints; it is not equivalent to local execution merely because both implement the same capability interface.

Model-agnostic means MADRE product semantics remain stable when a compatible capability implementation is replaced. Provider/framework APIs may influence adapters, never the ownership model above.

### 9. Product concepts are not automatically software classes

Names in this document describe responsibilities and semantic roles. They do not require one class, interface, service, registry or database table per noun.

Architecture is introduced from demonstrated behavior. A new abstraction earns existence only when a working path requires a distinct responsibility that cannot be expressed more simply.

Implementation may rename, merge or split internal types without changing MADRE as long as the product invariants remain true.

## Ownership test for future design decisions

Before assigning a new responsibility or introducing a new core abstraction, classify it using these questions:

1. **Does it exist because the domain needs to know, interpret, remember, decide or present something?**  
   It belongs to the application.

2. **Does it exist because submitted AI work must be scheduled, executed, constrained, recovered or inspected?**  
   It belongs to MADRE.

3. **Does it exist because a model, inference runtime, tool or external service performs a particular computation?**  
   It belongs behind a replaceable capability boundary.

4. **Is it produced by a capability?**  
   It is data until application/runtime software assigns a consequence to it.

5. **Would replacing the provider, model, agent library or transport change the supposed product meaning?**  
   If yes, implementation detail is leaking into product architecture and the boundary should be reconsidered.

When a proposed design cannot be classified cleanly by these tests, resolve the ownership question before materializing new architecture.

## System interaction

The narrow conceptual path is:

```text
independent application
    │
    │ selected domain material + execution intent
    ▼
MADRE runtime
    │
    │ bounded capability invocation
    ▼
inference / tool capability
    │
    │ generated result
    ▼
MADRE runtime
    │
    │ result + runtime evidence
    ▼
originating application
```

The exact API and internal data structures are implementation decisions. The invariant is the ownership and authority direction shown above.

## Architectural posture

MADRE should remain a small, inspectable control plane. It should use existing inference engines, databases, libraries and operating-system facilities behind replaceable boundaries rather than recreate commodity infrastructure without evidence of need.

The shortest coherent execution path is preferred. Additional frameworks, services and layers are justified only by behavior that the current path cannot provide cleanly.

The runtime does not become a knowledge platform, learning platform, domain workflow engine or monolithic application merely because those concerns may participate in AI-assisted systems. Independent applications can implement or reuse those capabilities according to their own domains.

## Current realization strategy

This section records the present implementation order. It is **not a product invariant**. It may change as code and empirical evidence improve without requiring a redefinition of MADRE, provided the invariants above remain true.

Determine the next implementation step from the code that actually exists.

### 1. Real inference path

Build the smallest real path from an application through MADRE to a configured local model and back.

A Python library call and a thin CLI are sufficient as the first access surfaces. Start with one OpenAI-compatible local HTTP adapter and explicit endpoint, model and timeout configuration. The path must return actual generated output or a useful transport/protocol error. No agent framework or provider registry is required.

The first path proves that MADRE owns the invocation boundary while the inference server remains replaceable. A controlled HTTP fixture may verify failure cases, but a fake-only path is not the acceptance result. If a real local model is unavailable in the executor environment, report that limitation instead of treating the fixture as equivalent.

### 2. Durable delayed reasoning

Extend the same real inference request so an application can submit work for later and receive an identifier immediately.

Use a simple local persistence mechanism such as SQLite unless implementation evidence shows a better need. Persist only runtime information required to know what work is pending, when it may run, which configured capability it requires, what attempt occurred and what result or error exists.

Provide a simple foreground worker or run-due command before considering a daemon or service. Pending work must survive restart. Interrupted execution must have an explicit recoverable outcome; do not claim exactly-once inference where the underlying capability cannot provide it.

After this stage, MADRE is already useful: an application can run real inference immediately or schedule reasoning, restart the runtime and later retrieve the result.

### 3. Integrate one real application

Connect an independent application such as AAAAT or another current MADRE consumer without moving its domain state into MADRE.

The application selects its own context, submits reasoning work and consumes the result. Its knowledge, learning, workflow, UI and domain semantics remain application concerns. This stage is the architectural proof that MADRE is a reusable runtime rather than a hidden monolithic application.

### 4. Add runtime controls only when demanded by a real use case

Add capability/action permissions, remote-inference transfer rules, cancellation, richer priorities, budgets, resource scheduling, multiple workers or additional provider adapters when an actual application path requires them.

Each addition should extend an existing working path. Do not create generalized infrastructure merely because it could be useful later.

## Development rule

The repository is the implementation evidence. There is no mutable project-state document.

When continuing development, inspect the current code, identify the earliest incomplete realization stage above, and implement one cohesive behavior that moves that stage toward a working end-to-end path. Routine branch, commit, pull-request and merge operations are part of that task, not a separate workflow for the developer to coordinate.
