# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

MADRE's thesis is that useful application capability does not have to equal one synchronous frontier-model call. Domain-aware applications can combine ordinary software, reusable Operations, Agents, accumulated knowledge, local/open inference and **Delayed Reasoning Effort**, then use stronger external providers only where they are actually useful.

The Owner keeps the application and software environment. Models/providers become resources inside it.

## Product shape

MADRE applications are independently installable **Modules** built against the public SDK.

Modules own their application/domain semantics and may expose bounded Operations, optional Agents/Skills and meaningful Material/context. Internal implementation remains theirs; existing software can be bound without being rewritten into one universal MADRE architecture.

An **Agent** is the semantic actor. An **Operation** is a bounded action. An agentless Module can expose Operations that another Agent invokes; cross-Module Operation use does not itself transfer semantic continuation to another Agent.

The public SDK is part of the product, not a thin transport adapter. It is intended to be simple and explicit enough for human developers and eventually AI-assisted builders to generate ordinary owner-local Modules without hidden first-party hooks.

MADRE Runtime supplies installation mechanics and shared semantic execution facilities. The Kernel is the durable physical inference substrate: it schedules physical reasoning against Owner-configured inference capabilities while remaining ignorant of application/domain semantics and external inference-runtime internals.

## DRE

Delayed Reasoning Effort allows useful reasoning to outlive the latency budget of the immediate interaction.

An application can answer what it already knows, decompose work, request bounded reasoning later, use idle local compute, and escalate only the reasoning that still benefits from stronger inference.

Semantic MADRE knows the application-side facts—what reasoning is needed, context characteristics, urgency, acceptable delay, actual SPIRA composition, Owner choices and the factual execution paths exposed by configured inference capabilities. It derives a small physical inference requirement without sending those semantic concepts into Kernel.

That derivation can preserve an opaque physical admissibility set. If the Owner explicitly selected one configured physical capability, the derived set can contain exactly that capability. If several physical capabilities are valid, Kernel DRE retains meaningful choice only inside that set.

Kernel combines the physical request with configured `InferenceCapability` facts, current state and accumulated physical observations to decide timing and capability within the admissible physical space.

## InferenceCapability and information journey

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

Capability knowledge keeps configured/declared facts, current state and historical observations distinct.

Configured capability truth now includes a factual physical execution path:

```text
ExecutionLocation: Local / External
destination
optional route/intermediary description
optional retention/history description
+ provenance
```

Those are facts about the configured physical path, not SPIRA values and not permissions. They exist so semantic MADRE/Owner tooling can understand the information journey above Kernel.

The Work-side `ExecutionBoundary` is a different concept: `LocalOnly` / `ExternalAllowed` describes what one Work item is allowed to do. `ExternalAllowed` does not claim that a capability itself is external.

`Unknown` and `Unavailable` are also different physical truths. DRE prefers known-available admissible capabilities. If none are known available, an admissible configured `Unknown` capability may be tried; actual execution can then provide evidence. Known-unavailable capabilities wait and are re-observed automatically under demand.

Kernel does not judge application-level answer quality. A physically valid result that fails the user's semantic objective is handled by the consuming Agent/Module.

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

A `ReasoningRequest` is semantic and is created by an Agent. It can involve context, Material, provenance, relevant SPIRA facts, Owner instruction and the reasoning objective, but the semantic `ReasoningRequest` itself does not cross into Kernel.

The current physical Kernel request carries only:

```text
prepared input
requested effort
urgency
optional eligibility/deadline
request-side LocalOnly / ExternalAllowed constraint
optional eligible capability IDs
```

A singleton eligible set is the exact-selection case. A larger set lets Kernel DRE choose only among those physically eligible capabilities. Kernel never needs to know the semantic reason for exclusion.

## Current Lane C implementation

Lane C is one current implementation under [`kernel/`](kernel/): the capability-aware .NET physical Kernel plus the Java 21 [`madre-kernel-client`](madre-kernel-client/).

The repaired physical closure baseline is:

```text
77a862941e9c05d15652317616069a296af0f397
```

GitHub Actions run `36604046199` passed preserved acceptance plus deterministic regression and qualification on both Ubuntu and Windows.

The earlier physical closure point was superseded because its public request could not preserve exact/eligible physical selection and its capability surface reused an ambiguous local/external concept instead of exposing factual execution paths. The rest of the capability-aware physical architecture did not require redesign.

MADRE owns below the hard boundary:

```text
PhysicalInferenceWork
physical admissibility / exact physical restrictions
InferenceCapability factual path + configured/current/observed truth
DRE
physical attempts/results/recovery
one authoritative durable Work lifecycle
```

Current implementation properties include:

- SQLite-authoritative Work/attempt/result lifecycle with pre-release schema version 2;
- one physical process owner per database, including Linux path aliases/symlinks;
- durable exact/eligible physical capability restrictions;
- factual capability location/destination/route/retention exposure with provenance;
- DRE constrained to that physical admissible space;
- asynchronous capability observation and demand-driven unavailable recovery;
- binding/version-scoped successful latency evidence;
- wake/deadline-driven scheduling with urgency preserved across slot-release races;
- typed physical failures;
- shell-free UTF-8 process binding, MEAI interoperability and open `IInferenceBinding` extensibility;
- bounded version-2 Unix-domain-socket IPC on Windows/Linux;
- strict IPC/config/CLI parsing;
- bounded physical concurrency, cancellation, release and truthful `UnknownCompletion` recovery.

There is no TCP/loopback web control plane, provider-specific Kernel ontology, model-worker lifecycle, compatibility migration layer or speculative production MAF/checkpoint strategy.

The semantic SDK/Module layer and MADRE Runtime remain outside Lane C. They must consume this physical boundary rather than reopen it for convenience.

## Kernel verification

Kernel verification is deliberately manual/on-demand rather than a permanent PR/push tax. The active workflow is:

[` .github/workflows/kernel-verification.yml`](.github/workflows/kernel-verification.yml)

It exposes regression, qualification, stress and soak suites on Linux/Windows. Lane A/B work does not run the Kernel arsenal merely because it shares the repository.

The repaired boundary is part of preserved acceptance and proves exact selection, multi-capability eligibility, no widening to unknown/incompatible capabilities, factual execution-path exposure, Java round-trip and custom capability-path validation.

## Documentation

Start here:

- [`NORTH_STAR.md`](NORTH_STAR.md) — short mandatory anti-drift recovery checkpoint;
- [`docs/product/lane-c-owner-decision.md`](docs/product/lane-c-owner-decision.md) — accepted Lane C/DRE ownership and final semantic→physical boundary decision;
- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — detailed product reasoning, subject to later Owner corrections;
- [`MADRE.md`](MADRE.md) — detailed repository-level product/semantic overview;
- [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) — operational SPIRA semantics;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — current closed physical Kernel architecture;
- [`docs/architecture/kernel-handoff.md`](docs/architecture/kernel-handoff.md) — Lane A/B backlog and Kernel anti-drift handoff.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention or historical generated code. Do not delete the carrier or boundary that gives an accepted concept meaning. Do not reopen the physical Kernel because a semantic implementation would be easier against a different architecture.
