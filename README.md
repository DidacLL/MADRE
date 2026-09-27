# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

MADRE's thesis is that useful application capability does not have to equal one synchronous frontier-model call. Domain-aware applications can combine ordinary software, reusable Operations, Agents, accumulated knowledge, local/open inference and **Delayed Reasoning Effort**, then use stronger external providers only where they are actually useful.

The Owner keeps the application and software environment. Models/providers become resources inside it.

## Product shape

MADRE applications are independently installable **Modules** built against the public SDK.

Modules own their application/domain semantics and may expose bounded Operations, optional Agents/Skills and meaningful Material/context. Internal implementation remains theirs; existing software can be bound without being rewritten into one universal MADRE architecture.

An **Agent** is the semantic actor. An **Operation** is a bounded action. An agentless Module can expose Operations that another Agent invokes; cross-Module Operation use does not itself transfer the semantic continuation to another Agent.

The public SDK is part of the product, not a thin transport adapter. It is intended to be simple and explicit enough for human developers and eventually AI-assisted builders to generate ordinary owner-local Modules without hidden first-party hooks.

MADRE Runtime supplies installation mechanics and shared semantic execution facilities. The Kernel is the durable physical inference control plane: it schedules physical reasoning against Owner-configured inference capabilities while remaining ignorant of application/domain semantics and external inference-runtime internals.

## DRE

Delayed Reasoning Effort allows useful reasoning to outlive the latency budget of the immediate interaction.

An application can answer what it already knows, decompose work, request bounded reasoning later, use idle local compute, and escalate only the reasoning that still benefits from stronger inference.

Semantic MADRE knows the application-side facts—what reasoning is needed, context characteristics, urgency, acceptable delay, required result characteristics and constraints derived from the actual semantic/SPIRA construction—and reduces them to a small physical inference requirement.

Kernel DRE combines that requirement with configured `InferenceCapability` facts, current capability state and accumulated physical observations to decide timing, capability, physical effort and strategy.

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

Capability knowledge keeps configured/declared facts, current state and historical observations distinct. Physical observations such as latency, availability, throughput, failure rate or observed cost can improve later DRE decisions without making Kernel the owner of the underlying inference runtime.

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

Lane C is now a single current implementation under [`kernel/`](kernel/): the capability-aware .NET physical Kernel. The previous native C++ Kernel, workers, model/runtime integration, protocol-v4 client, native acceptance suites and validation-era `kernel-dotnet` side tree are not present in the active tree. Git history is the historical record.

MADRE owns below the semantic/physical boundary:

```text
PhysicalInferenceWork
InferenceCapability
configured/current/observed capability truth
DRE
physical attempts/results/recovery
one authoritative durable Work lifecycle
```

The production control boundary is loopback HTTP on `127.0.0.1`, versioned under `/v1`. It is intentionally local-only and small: an independent Kernel process, concurrent bounded clients, bounded payloads, restartable lifetime and the same behavior on Windows and Linux. There is no service discovery, remote-control plane, TLS/PKI platform or tenancy layer.

Normal Kernel startup loads explicit Owner configuration. The first provided universal binding is a shell-free process/executable binding; a configured probe supplies actual physical evidence for current availability. Configuration alone never turns a capability into `Available`. `IInferenceBinding` is the ordinary seam for the provided process binding, optional MEAI use and advanced Owner/custom bindings; there is no dynamic plugin marketplace or privileged first-party route.

The implemented first-version DRE is deliberately small:

- durable eligibility and deadline admission;
- hard local/external admissibility;
- requested physical effort compatibility;
- current `Available` state only;
- Owner preference as the stable normal selection rule;
- persisted successful latency evidence for later `Interactive` choices when candidates have comparable evidence;
- one-shot physical inference normally;
- the concrete checkpointed two-stage strategy for `High + Background` Work.

SQLite is authoritative for Work, configured/current capability facts, attempts and retained physical result state. Active attempts whose completion becomes unknowable across hard Kernel death recover as `UnknownCompletion` and are never implicitly duplicated. Terminal input/result retention has explicit release semantics.

Microsoft Agent Framework is used only by the one concrete multi-stage physical strategy. Its checkpoint is subordinate execution state. Before continuation, MADRE Work authority still checks existence/state, cancellation, deadline, strategy identity/version, selected capability/binding identity/version and current physical admissibility. Simple inference bypasses MAF.

The Java [`madre-kernel-client`](madre-kernel-client/) speaks only the current `/v1` physical boundary and does not depend on `madre-sdk`.

## Repository acceptance

The active CI builds and tests only the current architecture on Linux and Windows. It builds the .NET Kernel and Java physical client, runs current lifecycle/capability/DRE/client acceptance, runs the MAF checkpoint/hard-restart suite, and runs contamination/destructive-convergence checks that reject semantic MADRE concepts, rejected worker/engine ontology, protocol-v4 fossils, native Kernel source and validation-only production hooks.

The semantic SDK/Module layer and MADRE Runtime remain outside this Lane C implementation and are not reconstructed here.

## Documentation

Start here:

- [`NORTH_STAR.md`](NORTH_STAR.md) — short mandatory anti-drift recovery checkpoint;
- [`docs/product/lane-c-owner-decision.md`](docs/product/lane-c-owner-decision.md) — accepted Lane C/DRE ownership decision and causal reasoning;
- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — authoritative detailed product reasoning;
- [`MADRE.md`](MADRE.md) — detailed repository-level product/semantic overview and authority order;
- [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) — operational SPIRA semantics;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — current physical Kernel/DRE architecture and implementation.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention or historical generated code. Do not simplify an established concept by deleting the concrete carrier, causal relation or boundary that gives it meaning. Do not preserve a superseded implementation boundary after the Owner has established that it prevents the product's DRE behavior.
