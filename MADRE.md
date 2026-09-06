# MADRE — Product Definition and Invariants

This file is the active product authority for MADRE.

The **Product invariants** below define MADRE independently of any particular implementation, class map, framework, provider, experiment, or previous design. Code and implementation plans may refine how these invariants are realized while preserving their meaning.

Historical documents, Git history, research reports, issues, pull requests and prior conversations may explain how a conclusion was reached. Current product meaning is established here; historical material gains current architectural relevance through an explicit update to this definition or through working behavior consistent with it.

## Identity

MADRE is the **Model-Agnostic Delayed Reasoning Effort Agentic System**: a local-first runtime/kernel that independent applications use to execute AI work under software control, immediately or at another time.

Its defining thesis is **Delayed Reasoning Effort (DRE)**. Reasoning is work whose execution effort can be controlled in time rather than being assumed to be one synchronous model call. Work may be accepted, made eligible now or later, persisted when durability is required, executed through configured capabilities, interrupted, recovered, inspected and returned to the originating application.

MADRE sits between independent applications and execution capabilities such as inference engines, model providers and tools. The application owns the domain. MADRE owns the runtime execution lifecycle. Capabilities perform bounded computation.

## Product invariants

These are semantic invariants. Their meaning must survive implementation changes.

### 1. A MADRE module is an independent application

`module` names the architectural role of an application that uses MADRE. Concrete process topology, integration mechanism and internal object model remain implementation choices for the application and runtime boundary.

An application owns the meaning of its domain. This includes, when relevant:

- authoritative domain data and its persistence;
- knowledge, memory, retrieval, provenance and truth semantics;
- learning, evaluation, adaptation and promotion of improved domain behavior;
- domain workflows and task decomposition;
- domain agents, prompts and interaction logic;
- UI, artifacts and domain-specific actions;
- selection of the domain material relevant to a piece of reasoning work;
- semantic privacy projection, minimization and domain classification.

An application may be a script, a desktop application, a service or a larger system such as AAAAT. These responsibilities remain application-owned across the integration boundary.

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

MADRE may persist the runtime state needed to perform those responsibilities. Application knowledge remains application-owned because its purpose is domain interpretation rather than runtime execution.

### 3. Capabilities are replaceable and non-authoritative

Inference engines, model providers and tools are execution capabilities. They may be local libraries, local processes, services, remote endpoints or other replaceable implementations.

A capability may compute, generate, retrieve or transform material within the execution boundary granted to it. Provider-specific concepts stay inside the capability integration unless the product itself acquires a corresponding responsibility.

Generated model or tool output is **data**. Software assigns any permission, trusted status, workflow consequence, mutation or other authority through the application and runtime responsibilities defined here.

### 4. Delayed Reasoning Effort is runtime semantics

Immediate and delayed reasoning are scheduling cases of the same product concept: reasoning effort is runtime work whose eligibility and lifecycle are controlled by software.

An application may submit work that is eligible immediately or at another time. MADRE preserves the execution intent required to run accepted work, executes it when eligible under the applicable runtime constraints, records the runtime outcome when durability or inspection requires it, and makes the result available to the application.

The invariant required by DRE is software control over when reasoning work becomes eligible and how its runtime lifecycle is handled. Additional planning, coordination or decomposition mechanisms arise from demonstrated application or runtime requirements.

### 5. Applications own workflow meaning; MADRE owns submitted-work lifecycle

An application decides what a domain task means, how a domain workflow is decomposed, what evidence is relevant and what should happen with a result.

MADRE may schedule several submitted jobs that belong to one application workflow. Its responsibility is the runtime lifecycle of those submitted jobs; the application retains the semantics connecting them.

A model may propose a plan, action, classification or next step when an application requests that computation. The proposal remains generated material until ordinary software accepts and uses it through the application's logic and the applicable runtime execution boundary.

### 6. Context meaning belongs to the application; execution constraints are enforced at use

The application selects the domain material submitted for reasoning and owns the semantic interpretation of that material. MADRE execution relies on the execution-relevant material and constraints supplied through the runtime boundary, while domain interpretation remains with the application.

The application may provide the execution-relevant constraints required for safe handling of submitted material. MADRE enforces constraints where they become operational: for example, when choosing a permitted capability, transferring material across a local/remote boundary, invoking an action, or consuming runtime resources.

Represent boundary information where it can change active execution or transfer. This keeps the model proportional to the behavior that actually depends on the boundary.

### 7. Persistence ownership follows purpose

The owner of persisted state is determined by **why the state exists**, rather than by which process happens to write the bytes.

- State that exists because the domain must remember, interpret, retrieve, learn from or govern it belongs to the application.
- State that exists because runtime work must be scheduled, executed, recovered, inspected or controlled belongs to MADRE.
- State that exists as an implementation cache or internal detail of an inference/tool capability belongs to that capability unless a stronger product requirement assigns it elsewhere.

This keeps runtime persistence scoped to runtime execution while domain persistence remains scoped to domain meaning.

### 8. Local-first and model-agnostic are architectural properties

MADRE treats local execution as a first-class path. A useful local path exists independently of any particular remote provider, hosted control plane or provider-specific agent platform.

Remote inference or remote tools may also be supported. Crossing that boundary is an explicit execution decision subject to the relevant transfer constraints and is represented accordingly in runtime behavior and evidence.

Model-agnostic means MADRE product semantics remain stable when a compatible capability implementation is replaced. Provider and framework APIs may shape their adapters while the ownership model above remains stable.

### 9. Product concepts are not automatically software classes

Names in this document express responsibilities and semantic roles. The implementation chooses the simplest mapping of those responsibilities to classes, functions, services, tables or other structures.

Architecture is introduced from demonstrated behavior. A new abstraction earns existence when a working path needs a distinct responsibility that becomes clearer or more reliable through that abstraction.

Implementation may rename, merge or split internal types while preserving MADRE's product invariants.

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
   Reconsider the boundary and separate implementation-specific behavior from stable product responsibility.

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

MADRE should remain a small, inspectable control plane. It should reuse existing inference engines, databases, libraries and operating-system facilities through suitable boundaries whenever they provide the required behavior cleanly.

The shortest coherent execution path is preferred. Additional frameworks, services and layers earn their place when demonstrated behavior becomes simpler, clearer, safer or more reliable because of them.

Domain knowledge, learning, workflow semantics and user-facing application behavior remain application responsibilities. MADRE concentrates on reusable runtime execution semantics.

## Current realization strategy

This section records the present implementation order. It is **not a product invariant**. It may change as code and empirical evidence improve while the invariants above remain true.

Determine the next implementation step from the code that actually exists.

### 1. Real inference path

Build the smallest real path from an application through MADRE to a configured local model and back.

A Python library call and a thin CLI are sufficient as the first access surfaces. Start with one OpenAI-compatible local HTTP adapter and explicit endpoint, model and timeout configuration. The path must return actual generated output or a useful transport/protocol error. Supporting frameworks or broader provider structure should be introduced when the working path demonstrates a responsibility that benefits from them.

The first path proves that MADRE owns the invocation boundary while the inference server remains replaceable. Controlled HTTP fixtures can verify failure behavior. Acceptance of real inference requires either a successful real local-model invocation or an accurate report that the executor environment lacks such an endpoint while leaving the real path runnable.

### 2. Durable delayed reasoning

Extend the same real inference request so an application can submit work for later and receive an identifier immediately.

Use a simple local persistence mechanism such as SQLite unless implementation evidence shows a better need. Persist only runtime information required to know what work is pending, when it may run, which configured capability it requires, what attempt occurred and what result or error exists.

Provide a simple foreground worker or run-due command before considering a daemon or service. Pending work must survive restart. Interrupted execution must have an explicit recoverable outcome, and delivery guarantees should accurately reflect the semantics that the underlying capability can support.

After this stage, MADRE is already useful: an application can run real inference immediately or schedule reasoning, restart the runtime and later retrieve the result.

### 3. Integrate one real application

Connect an independent application such as AAAAT or another current MADRE consumer while preserving its ownership of domain state.

The application selects its own context, submits reasoning work and consumes the result. Its knowledge, learning, workflow, UI and domain semantics remain application concerns. This stage is the architectural proof that MADRE is a reusable runtime serving an independent application.

### 4. Add runtime controls from real use cases

Add capability/action permissions, remote-inference transfer rules, cancellation, richer priorities, budgets, resource scheduling, multiple workers or additional provider adapters when an actual application path requires them.

Each addition should extend an existing working path and be justified by the behavior it makes possible or materially improves.

## Development rule

The repository is the implementation evidence. Current state should be recoverable from the active product definition, current code and repository history.

When continuing development, inspect the current code, identify the earliest incomplete realization stage above, and implement one cohesive behavior that moves that stage toward a working end-to-end path. Routine branch, commit, pull-request and merge operations are part of that task.
