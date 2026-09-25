# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

MADRE's thesis is that useful application capability does not have to equal one synchronous frontier-model call. Domain-aware applications can combine ordinary software, reusable Operations, Agents, accumulated knowledge, local/open inference and **Delayed Reasoning Effort**, then use stronger external providers only where they are actually useful.

The Owner keeps the application and software environment. Models/providers become resources inside it.

## Product shape

MADRE applications are independently installable **Modules** built against the public SDK.

Modules own their application/domain semantics and may expose bounded Operations, optional Agents/Skills and meaningful Material/context. Internal implementation remains theirs; existing software can be bound without being rewritten into one universal MADRE architecture.

An **Agent** is the semantic actor. An **Operation** is a bounded action. An agentless Module can expose Operations that another Agent invokes; cross-Module Operation use does not itself transfer the semantic continuation to another Agent.

The public SDK is part of the product, not a thin transport adapter. It is intended to be simple and explicit enough for human developers and eventually AI-assisted builders to generate ordinary owner-local Modules without hidden first-party hooks.

MADRE Runtime supplies installation mechanics and shared semantic execution facilities. The native Kernel is deliberately narrower: it owns durable **physical inference Work only**.

## DRE

Delayed Reasoning Effort allows useful reasoning to outlive the latency budget of the immediate interaction.

An application can answer what it already knows, decompose work, schedule bounded verification/research/critique later, use idle local compute, and escalate only the reasoning that still benefits from stronger inference.

The goal is not to pretend small local models equal frontier models. The goal is to make **domain-aware software + time + bounded reasoning + selective routing** more capable than one isolated inference call suggests.

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

The SDK defines a bounded required function:

```text
ReasoningRequest + execution preferences/declarations
    -> physical Work requirements
```

Runtime executes the configured implementation. The implementation belongs to a Module; shipped CORE provides the default; the Owner can wrap or replace it.

Kernel receives only physical Work. It does not receive Module/Agent/Material/SPIRA semantics and does not derive Privacy or Integrity from physical engine/provider/locality facts.

## Modularity

MADRE is intended to remain replaceable at every real boundary without turning every boundary into a speculative plugin framework.

- Modules can be replaced or generated against the public SDK.
- Runtime-required semantic Operations have selectable Module-owned implementations.
- CORE provides defaults without monopolizing them.
- the ReasoningRequest-to-Work translation can be wrapped/replaced for Owner experiments.
- Kernel stays independent of the semantic SDK while physical routing/resource journeys remain adaptable.
- engine/worker implementations are replaceable behind physical Kernel contracts.

The hard semantic/physical boundary remains hard even when implementations on either side change.

## Current repository state

### Implemented

Current `main` contains the accepted Lane C/native Kernel foundation:

- C++ native Kernel;
- durable physical Work and restart recovery;
- scheduling and worker supervision;
- physical resource accounting;
- local IPC and Windows/Linux hardening;
- small Java `madre-kernel-client`;
- real native llama.cpp worker path.

### Not yet implemented

The semantic SDK/Module layer and MADRE Runtime described by the accepted architecture are still missing from the active tree. Their architecture is documented; their implementation must be built cleanly rather than restored wholesale from discarded historical code.

## Documentation

Start here:

- [`NORTH_STAR.md`](NORTH_STAR.md) — short mandatory anti-drift recovery checkpoint; it does not replace the richer documents below;
- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — authoritative detailed product reasoning and causal context;
- [`MADRE.md`](MADRE.md) — detailed repository-level product/semantic overview and authority order;
- [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) — operational SPIRA semantics;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture and diagrams;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — current physical Kernel architecture.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention or historical generated code. Do not simplify an established concept by deleting the concrete carrier, causal relation or boundary that gives it meaning.