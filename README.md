# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

MADRE's thesis is that useful application capability does not have to equal one synchronous frontier-model call. Domain-aware applications can combine ordinary software, reusable Operations, Agents, accumulated knowledge, local/open inference and **Delayed Reasoning Effort**, then use stronger external providers only where they are actually useful.

The Owner keeps the application and software environment. Models/providers become resources inside it.

## Product shape

MADRE applications are independently installable **Modules** built against the public SDK.

Modules own their application/domain semantics and may expose bounded Operations, optional Agents/Skills and meaningful Material/context. Internal implementation remains theirs; existing software can be bound without being rewritten into one universal MADRE architecture.

An **Agent** is the semantic actor. An **Operation** is a bounded action. An agentless Module can expose Operations that another Agent invokes; cross-Module Operation use does not itself transfer the semantic continuation to another Agent.

The public SDK is part of the product, not a thin transport adapter. It is intended to be simple and explicit enough for human developers and eventually AI-assisted builders to generate ordinary owner-local Modules without hidden first-party hooks.

MADRE Runtime supplies installation mechanics and shared semantic execution facilities. The Kernel is the durable physical inference substrate: it schedules physical reasoning against Owner-configured inference capabilities while remaining ignorant of application/domain semantics and external inference-runtime internals.

## DRE

Delayed Reasoning Effort allows useful reasoning to outlive the latency budget of the immediate interaction.

An application can answer what it already knows, decompose work, request bounded reasoning later, use idle local compute, and escalate only the reasoning that still benefits from stronger inference.

Semantic MADRE knows the application-side facts—what reasoning is needed, context characteristics, urgency, acceptable delay, required result characteristics and constraints derived from the actual semantic/SPIRA construction—and reduces them to a small physical inference requirement.

Kernel DRE combines that requirement with configured `InferenceCapability` facts, current capability state and accumulated physical observations to decide timing and capability within the implemented physical policy.

The goal is not to pretend small local models equal frontier models. The goal is to make **domain-aware software + time + bounded reasoning + selective physical inference** more capable than one isolated inference call suggests.

## InferenceCapability

Kernel reasons about physical capabilities rather than owning inference engines.

A capability may represent, for example:

```text
local model / fast configuration
same model / deeper configuration
same model on another machine
commercial endpoint
Owner inference router
opaque coding/research intelligence
```

Capability knowledge keeps configured/declared facts, current state and historical observations distinct. Physical observations can improve later DRE decisions without making Kernel the owner of the underlying inference runtime.

`Unknown` and `Unavailable` are different physical truths. DRE prefers known-available admissible capabilities. If none are known available, an otherwise admissible configured `Unknown` capability may be tried; actual execution can then provide evidence. Known-unavailable capabilities wait and are re-observed automatically.

Kernel does not silently judge application-level answer quality. A physically valid result that fails the user's semantic objective is handled by the consuming Agent/Module, not retried merely because Kernel dislikes the answer.

## SPIRA

MADRE's Security Algebra is intrinsic semantic composition, not a central authorization/policy service.

The direct facets are:

```text
Material/context representation       -> Sensitivity
actual receiving/exposure boundary    -> Privacy
actual Agent/provenance participant   -> Integrity
actual selected Operation/effect      -> Risk
current acting Agent continuation     -> Autonomy
```

There is no mandatory `EffectProfile` and no Agent-owned `Compound` manager in the current architecture. The compound exists because actual constituents compose. Semantic MADRE changes actual constituents when a construction does not compose; it does not rewrite algebra values.

See [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) for the operational model.

## Reasoning and the Kernel boundary

A `ReasoningRequest` is semantic and is created by an Agent. It can involve context, Material, provenance, relevant SPIRA facts and the reasoning objective, but the semantic `ReasoningRequest` itself does not cross into Kernel.

Semantic MADRE derives only the physical inference facts needed below the boundary. The current Lane C contract carries prepared physical input, requested physical effort, urgency, eligibility/deadline and the hard local/external execution restriction consumed by the implemented DRE.

Kernel performs DRE against configured `InferenceCapability` facts, current physical state and observed physical evidence. It does not receive Module/Agent/Material/SPIRA semantics, own model loading/warmness, or rematerialize external inference systems as MADRE workers.

## Current Lane C implementation

Lane C is a single current implementation under [`kernel/`](kernel/): the capability-aware .NET physical Kernel. The physical baseline is closed and qualified; superseded native/worker/model-lifecycle implementations and validation side trees are not active alternatives, and Git history is the historical record.

MADRE owns below the semantic/physical boundary:

```text
PhysicalInferenceWork
InferenceCapability
configured/current/observed capability truth
DRE
physical attempts/results/recovery
one authoritative durable Work lifecycle
```

The Kernel host uses a small versioned local IPC contract over Unix-domain sockets on supported Windows/Linux targets. Messages are bounded length-prefixed UTF-8 JSON frames. There is no TCP listener, configurable port or web-server dependency. The Java 21 [`madre-kernel-client`](madre-kernel-client/) uses this same local protocol and does not depend on `madre-sdk`.

Kernel startup is valid with zero configured capabilities and no configuration file. Capability probing is asynchronous, so slow or broken inference systems do not hold Kernel availability hostage. The configured catalogue is reconciled at restart: removed capabilities cease to be selectable while historical physical attempts remain historical evidence.

The implemented DRE is deliberately small:

- durable eligibility and deadline admission;
- hard local/external admissibility;
- requested physical-effort compatibility;
- explicit `Available` / `Unknown` / `Unavailable` handling;
- automatic capability re-observation;
- Owner preference as the stable normal selection rule;
- persisted successful latency evidence for `Interactive` choices when candidates have comparable evidence.

Scheduling is wake/deadline driven rather than busy-polled. Urgency and effort ordering live in explicit domain code rather than enum ordinals or SQL policy. Physical failures use a named typed vocabulary with separate technical detail.

SQLite is authoritative for Work, configured/current capability state, attempts and retained physical results. Active attempts whose completion becomes unknowable across hard Kernel death recover as `UnknownCompletion` and are never implicitly duplicated. Terminal input/result retention has explicit release semantics.

`IInferenceBinding` is the ordinary physical seam for the provided shell-free process binding, MEAI interoperability and advanced Owner/custom bindings. Binding execution receives only execution-relevant physical data, and common result validation/failure conversion is centralized.

The earlier validation-only multi-stage checkpoint strategy is not part of the current product implementation. No production checkpoint state, workflow dependency or validation-only strategy registry remains in the active tree.

## Kernel verification

The closed physical baseline was fully requalified on Linux and Windows at executable head `95ddf2250c27d28e90e215391223164965b68729`. GitHub Actions run `36493627377` passed regression, qualification, stress and soak on both operating systems.

Kernel verification is now deliberately manual/on-demand rather than a permanent PR/push tax. The active `.github/workflows/kernel-verification.yml` exposes the four suites with deterministic seed/scale controls.

The retained verification covers zero-capability/no-config startup, bounded local IPC, real Java↔.NET operation, concurrent/stalled/disappearing callers, capability truth and automatic recovery, DRE admissibility/Owner preference/observed latency, durable eligibility/deadlines, cancellation, retained results/release, restart `UnknownCompletion`, configuration reconciliation, process/custom binding openness, scheduler races, persistence failure truth, hostile process behavior, randomized/model stress, restart chaos and sustained load.

The semantic SDK/Module layer and MADRE Runtime remain outside this Lane C implementation and are not reconstructed here.

## Documentation

Start here:

- [`NORTH_STAR.md`](NORTH_STAR.md) — short mandatory anti-drift recovery checkpoint;
- [`docs/product/lane-c-owner-decision.md`](docs/product/lane-c-owner-decision.md) — accepted Lane C/DRE ownership decision and final engineering correction;
- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — authoritative detailed product reasoning, subject to later Owner corrections;
- [`MADRE.md`](MADRE.md) — detailed repository-level product/semantic overview and authority order;
- [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) — operational SPIRA semantics;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — current closed physical Kernel/DRE architecture and implementation;
- [`docs/architecture/kernel-handoff.md`](docs/architecture/kernel-handoff.md) — Lane C closure evidence, next-lane integration backlog and anti-drift handoff.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention or historical generated code. Do not simplify an established concept by deleting the concrete carrier, causal relation or boundary that gives it meaning. Do not preserve a superseded implementation boundary after the Owner has established that it prevents the product's DRE behavior.
