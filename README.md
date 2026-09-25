# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

MADRE's thesis is that useful application capability does not have to equal one synchronous frontier-model call. Domain-aware applications can combine ordinary software, reusable Operations, Agents, accumulated knowledge, local/open inference and **Delayed Reasoning Effort**, then use stronger external providers only where they are actually useful.

The Owner keeps the application and software environment. Models/providers become resources inside it.

## Product shape

MADRE applications are independently installable **Modules** built against the public SDK.

Modules own their application/domain semantics and may expose bounded Operations, optional Agents/Skills and meaningful Material/context. Internal implementation remains theirs; an existing software or agentic environment can be bound without being rewritten into one universal MADRE architecture.

The public SDK is intentionally part of the product, not a thin adapter. It is intended to be simple and explicit enough for human developers and, eventually, AI-assisted builders to generate ordinary owner-local Modules without hidden first-party hooks.

MADRE Runtime coordinates installed Modules and shared semantic reasoning facilities.

The native Kernel is deliberately narrower: it owns durable **physical inference Work only** and remains blind to Module/Agent/Material/SPIRA semantics.

## DRE

Delayed Reasoning Effort allows useful reasoning to outlive the latency budget of the immediate interaction.

An application can answer what it already knows, decompose work, schedule bounded verification/research/critique later, use idle local compute, and escalate only the reasoning that still benefits from stronger inference.

The goal is not to pretend small local models equal frontier models. The goal is to make **domain-aware software + time + bounded reasoning + selective routing** more capable than one isolated inference call suggests.

## SPIRA

MADRE's Security Algebra is intrinsic semantic composition across five separate facets:

- Sensitivity;
- Privacy;
- Integrity;
- Risk;
- Autonomy.

Integrity levels are:

`NOT_DECLARED → DECLARED → TRUSTED → ACCEPTED → VALIDATED`

SPIRA is not a central authorization/policy service. A compound arises inherently from the actual semantic constituents that are composing. Agents react to that structure; they do not own or manage it.

## Modularity

MADRE is designed to remain replaceable at every real boundary without turning every boundary into a speculative plugin framework.

Examples:

- Modules can be replaced or generated against the public SDK;
- Runtime-required semantic Operations have selectable Module-owned implementations;
- the shipped CORE Module provides default implementations but does not monopolize them;
- the ReasoningRequest→physical Work translation can be wrapped/replaced for Owner experiments;
- Kernel stays independent of the semantic SDK but its physical routing/resource journey must remain adaptable enough for experiments such as learning-assisted routing;
- engine/worker implementations remain replaceable behind physical Kernel contracts.

The hard semantic/physical boundary remains hard even when implementations on either side are changed.

## Current repository state

`main` now represents the current MADRE direction rather than the discarded historical implementation.

### Implemented

The accepted Lane C/native Kernel foundation is present:

- C++ native Kernel;
- durable physical Work and restart recovery;
- scheduling and worker supervision;
- physical resource accounting;
- local IPC and Windows/Linux hardening;
- small Java `madre-kernel-client`;
- real native llama.cpp worker path.

The integrated Kernel baseline descends from accepted head `fc7ce5c75bc84de5b277aaa91e796a97a54242fd`, which passed the full Lane C validation on Linux and Windows.

### Not yet implemented

The semantic SDK/Module layer and MADRE Runtime described by the accepted architecture are still missing from the active tree. Their architecture is documented; their implementation should be built cleanly rather than restored from discarded historical code.

## Documentation

Start here:

- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — authoritative product reasoning and meanings;
- [`MADRE.md`](MADRE.md) — concise cross-repository product definition and authority order;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture and interaction diagrams;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — current physical Kernel architecture;
- [`docs/architecture/lane-c-native-kernel.md`](docs/architecture/lane-c-native-kernel.md) — detailed historical Lane C implementation contract/evidence.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

The target is:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention, historical generated code or the fact that another AI platform uses a particular abstraction.