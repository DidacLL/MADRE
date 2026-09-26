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

Integrity levels are:

```text
NOT_DECLARED -> DECLARED -> TRUSTED -> ACCEPTED -> VALIDATED
```

The same-facet reductions are `max(Sensitivity)`, `min(Privacy)` and `min(Integrity)`. Risk comes from the concrete Operation/effect actually being used. Autonomy comes from the current Agent continuation.

There is **no mandatory `EffectProfile`** and no Agent-owned `Compound` manager in the current architecture. The compound exists because actual constituents compose.

Where the current construction makes the relations relevant:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)

min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)

R_actual_operation
    <= min(I_actual_effect_realizers)
```

These are composition relations, not permission decisions. The Agent changes actual constituents when a construction does not compose; it does not rewrite algebra values.

See [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) for the operational model.

## Reasoning and the Kernel boundary

A `ReasoningRequest` is semantic and is created by an Agent. It can involve context, Material, provenance, relevant SPIRA facts and the reasoning objective, but MADRE does not require it to become one generic SPIRA tuple.

`ReasoningRequest` itself does not cross into Kernel. Semantic MADRE derives only the physical inference facts needed below the boundary, conceptually such as:

```text
prepared inference input
requested result characteristics
reasoning depth / effort indication
urgency
acceptable delay / deadline
modality/context characteristics
hard physical restrictions
```

Kernel then performs DRE against its configured `InferenceCapability` catalogue/state/observations.

This supersedes the intermediate Lane C rule that provider/model/configuration must be fully resolved to exact `ConcretePhysicalInvocation` candidates before Kernel. That correction successfully removed Kernel-owned inference engines, but it also prevented meaningful capability-aware DRE scheduling.

Kernel still does **not** receive Module/Agent/Material/SPIRA semantics, own model loading/warmness, or rematerialize external inference systems as MADRE workers.

## Physical construction surface

The leading target Kernel implementation is cross-platform .NET.

MADRE owns:

```text
PhysicalInferenceWork
InferenceCapability
configured/current/observed capability knowledge
DRE
physical attempts/results/recovery
one authoritative durable Work lifecycle
```

Generic open-source infrastructure may provide the machinery beneath those concepts:

- Microsoft.Extensions.AI for common inference interoperability where useful;
- Microsoft Agent Framework for physical workflow graphs/checkpointing where a concrete DRE strategy needs it;
- generic HTTP/protocol/process bindings;
- Owner/custom bindings for unusual inference environments.

Framework types do not become MADRE concepts:

```text
MADRE Agent         != MAF AIAgent
MADRE Workflow      != MAF Workflow
InferenceCapability != IChatClient / MAF AIAgent / MAF Workflow
DRE                  != MAF
```

MADRE-provided inference adapters must use the same class of physical construction surface available to advanced Owners. No provider/runtime receives a privileged Kernel path merely because MADRE ships a convenience integration for it.

## Modularity

MADRE is intended to remain replaceable at every real boundary without turning every boundary into a speculative plugin framework.

- Modules can be replaced or generated against the public SDK.
- CORE provides defaults without monopolizing semantic ownership.
- the semantic→physical inference requirement contract remains small and replaceable;
- Kernel owns MADRE's physical DRE/capability concepts while external engines own their internals;
- MEAI/MAF are implementation infrastructure, not public semantic API;
- provided and Owner-built physical adapters use the same class of extension surface;
- new capability fields or physical mechanisms should be justified by actual DRE consumers/needs rather than hypothetical completeness.

## Current repository state

### Implemented reference behavior

The active Lane C correction branch contains the completed C++ LCR1–LCR3 physical reference implementation:

- native C++ Kernel;
- SQLite durable physical Work and restart recovery;
- generic candidate-specific `ProcessInvocation` and `HttpInvocation` execution;
- deadlines, cancellation and bounded retry semantics;
- truthful `UNKNOWN_COMPLETION` after interrupted active attempts;
- explicit terminal payload/result release;
- isolated local Unix-domain socket / Windows named-pipe clients;
- Java physical client at protocol v4;
- Linux/Windows CI evidence.

No provider/model runtime is built into that Kernel and no llama.cpp/model download is required for validation.

The C++ implementation is now a **reference implementation/test oracle**, not the target Lane C architecture, because its boundary still assumes inference choices have already become concrete invocations before Kernel.

### Accepted target, not yet implemented

The target is the capability-aware Kernel described above: semantic MADRE supplies a physical inference requirement; Kernel DRE schedules it against configured capability facts/state/observations and executes through open physical bindings.

The semantic SDK/Module layer and MADRE Runtime are also accepted architecture but are still missing from the active tree. Their implementation must be built cleanly rather than restored wholesale from discarded historical code.

## Documentation

Start here:

- [`NORTH_STAR.md`](NORTH_STAR.md) — short mandatory anti-drift recovery checkpoint;
- [`docs/product/lane-c-owner-decision.md`](docs/product/lane-c-owner-decision.md) — accepted current Lane C/DRE decision and the causal reasoning behind it;
- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — authoritative detailed product reasoning;
- [`MADRE.md`](MADRE.md) — detailed repository-level product/semantic overview and authority order;
- [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) — operational SPIRA semantics;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture and diagrams;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — target physical Kernel/DRE architecture and replacement validation path.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention or historical generated code. Do not simplify an established concept by deleting the concrete carrier, causal relation or boundary that gives it meaning. Do not preserve a corrective implementation boundary after the Owner has established that it prevents the product's DRE behavior.
