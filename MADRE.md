# MADRE — Current Product Definition

MADRE is the **Model-Agnostic Delayed Reasoning Effort Agentic System**: a local-first runtime/kernel that independent applications use to execute AI work now or later under software control.

Its defining thesis is **Delayed Reasoning Effort**. Reasoning is not assumed to be one synchronous model call. It is work that may be accepted, scheduled, prioritized, persisted, executed with different capabilities, interrupted, resumed, inspected, and returned to the originating application.

MADRE sits between applications and execution capabilities such as local or remote inference providers and tools. It is not the domain application and it is not the inference engine.

## System boundary

### Applications / modules

A MADRE module is an **independent application**. It owns its domain and remains independently responsible for:

- domain data and persistence;
- knowledge, memory, retrieval, provenance and truth semantics;
- learning, evaluation, adaptation and promotion of improved behavior;
- domain workflows and task decomposition;
- domain agents, prompts and interaction logic;
- UI, artifacts and domain-specific actions;
- deciding what domain context is relevant to a piece of work.

MADRE must not require an application to move those responsibilities into the runtime. An application may be as small as a script or as rich as AAAAT; the integration boundary stays narrow.

### MADRE runtime / kernel

MADRE owns the execution concerns that should not have to be reimplemented independently by every application:

- accepting and identifying runtime work;
- immediate and delayed scheduling;
- priority, timing, budget and execution constraints when required;
- durable runtime work state and recovery after process interruption;
- selecting and invoking configured inference/tool capabilities;
- managing replaceable capability bindings without making a provider authoritative;
- enforcing execution and transfer boundaries at the point where a capability is used;
- runtime-level cancellation, retries and failure handling when those behaviors are implemented;
- resource allocation and concurrency when real workloads require them;
- trace/inspection of work lifecycle, attempts, capability use and results;
- returning results and runtime evidence to the originating application.

MADRE may persist the runtime state needed to do those jobs. That persistence is not the application's knowledge base.

### Models, providers and tools

Inference engines, model providers and tools are **capabilities**. They may be local processes, libraries, services or remote endpoints. They are replaceable and bounded by the runtime contract.

Generated output is data. It does not grant itself permission, change policy, authorize an action, create trusted knowledge, or redefine the runtime.

## Delayed Reasoning Effort

Immediate and delayed reasoning are two scheduling cases of the same runtime concept.

An application can submit work that is eligible now, or submit work that should run later. MADRE acknowledges accepted work, preserves the execution intent, runs it when eligible and resources permit, records the outcome, and makes the result available to the application.

The application owns the meaning of the task and any domain workflow around it. MADRE owns the lifecycle of the runtime work submitted to it. If an application decomposes a workflow into several reasoning jobs, MADRE schedules those jobs; it does not become the owner of the application's workflow semantics.

A model may be used to propose a plan or next action when an application asks for that capability, but the proposal remains generated material until software accepts it through the application's own logic and the runtime's execution boundaries.

## Context and authority

Applications select and govern their domain context. MADRE receives only the material an application chooses to submit for a runtime job, together with the execution constraints needed to handle it safely.

MADRE enforces boundaries that become relevant during execution: for example whether material may leave the local machine, which capability may receive it, which action may run, and which runtime resources may be used. Domain classification and knowledge semantics remain with the application.

Boundaries should be represented where they affect active execution or transfer. They are not a reason to attach duplicated policy state to every passive request, plan, workflow or record.

The authority order is simple: the user/application defines intent; MADRE controls runtime execution; capabilities produce results. A capability never acquires authority merely by producing text or structured output.

## Architectural posture

MADRE should remain a small, inspectable control plane. It should use existing inference engines, databases, libraries and operating-system facilities behind replaceable interfaces rather than recreating them without evidence of need.

Abstractions are introduced by demonstrated behavior, not by completing a conceptual class map in advance. A class, service, registry, policy layer or framework earns existence only when the current working path needs a distinct responsibility that cannot be expressed more simply.

Local-first means private work should be able to remain local and local capabilities should be first-class. It does not mean MADRE can never use remote inference; remote execution is an explicit stronger boundary with deliberate transfer and authorization.

## Implementation sequence

This is an order of realization, not a progress document. Determine the next step from the code that actually exists.

### 1. Real inference path

Build the smallest real path from an application through MADRE to a configured local model and back.

A Python library call and a thin CLI are sufficient as the first access surfaces. Start with one OpenAI-compatible local HTTP adapter and explicit endpoint, model and timeout configuration. The path must return actual generated output or a useful transport/protocol error. No agent framework or provider registry is required.

The first path proves that MADRE owns the invocation boundary while the inference server remains replaceable. A controlled HTTP fixture may verify failure cases, but a fake-only path is not the acceptance result. If a real local model is unavailable in the executor environment, report that limitation instead of pretending the fixture is equivalent.

### 2. Durable delayed reasoning

Extend the same real inference request so an application can submit work for later and receive an identifier immediately.

Use a simple local persistence mechanism such as SQLite unless implementation evidence shows a better need. Persist the minimum runtime information required to know what is pending, when it may run, which configured capability it requires, what attempt occurred and what result/error exists.

Provide a simple foreground worker/run-due command before considering a daemon or service. Pending work must survive restart. Interrupted execution must have an explicit recoverable outcome; do not claim exactly-once inference where the underlying capability cannot provide it.

After this stage, MADRE is already useful: an application can run real inference immediately or schedule reasoning, restart the runtime and later retrieve the result.

### 3. Integrate one real application

Connect an independent application such as AAAAT or another current MADRE consumer without moving its domain state into MADRE.

The application selects its own context, submits reasoning work and consumes the result. Its knowledge, learning, workflow and UI remain application concerns. This stage is the architectural proof that MADRE is a reusable runtime rather than a hidden monolithic application.

### 4. Add runtime controls only when demanded by a real use case

Add capability/action permissions, remote-inference transfer rules, cancellation, richer priorities, budgets, resource scheduling, multiple workers or additional provider adapters when an actual application path requires them.

Each addition should extend an existing working path. Do not create generalized infrastructure merely because it may be useful later.

## Development rule

The repository is the implementation evidence. There is no mutable project-state document.

When continuing development, inspect the current code, identify the earliest incomplete implementation stage above, and implement one cohesive behavior that moves that stage toward a working end-to-end path. Routine branch, commit, pull-request and merge operations are part of that task, not a separate workflow for the developer to coordinate.
