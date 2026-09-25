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

MADRE's Security Algebra is a concrete composition model, not a central authorization/policy service.

Its values live on the contracts where they mean something:

```text
Material                          -> Sensitivity
Operation accepted Material type -> Privacy
Operation produced Material type -> maximum output Sensitivity
Agent / actual causal participant -> Integrity
actual effect realizer           -> Integrity when it participates
selected Operation EffectProfile -> Risk + Autonomy
```

Integrity levels are:

`NOT_DECLARED → DECLARED → TRUSTED → ACCEPTED → VALIDATED`

An Operation owns its immutable `EffectProfile`s. A consequential invocation selects one exact profile; that profile supplies the Risk/Autonomy shape of that execution. Non-consequential Operations do not receive dummy effect values.

SPIRA keeps three comparisons distinct:

```text
information reach:
max(S_actual_material) <= min(P_actual_receiving_path)

machine control of a consequential effect:
min(R_selected_profile, A_selected_profile) <= min(I_actual_non_user_controllers)

effect realization:
R_selected_profile <= min(I_actual_effect_realizers)
```

A `ReasoningRequest` accumulates only the actual Sensitivity, Privacy and Integrity constituents of its reasoning computation. It does not automatically inherit a surrounding effect's Risk/Autonomy.

There is no Agent-owned `Compound` manager. The algebraic compound exists because actual constituents compose; Agents react to the result.

See [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) for the complete operational contract.

## Modularity

MADRE is designed to remain replaceable at every real boundary without turning every boundary into a speculative plugin framework.

Examples:

- Modules can be replaced or generated against the public SDK;
- Runtime-required semantic Operations have selectable Module-owned implementations;
- the shipped CORE Module provides default implementations but does not monopolize them;
- the ReasoningRequest→physical Work translation can be wrapped/replaced for Owner experiments;
- Kernel stays independent of the semantic SDK but its physical routing/resource journey remains adaptable enough for experiments such as learning-assisted routing;
- engine/worker implementations remain replaceable behind physical Kernel contracts.

The hard semantic/physical boundary remains hard even when implementations on either side are changed.

## Current repository state

`main` represents the current MADRE direction rather than the discarded historical implementation.

### Implemented

The accepted Lane C/native Kernel foundation is present:

- C++ native Kernel;
- durable physical Work and restart recovery;
- scheduling and worker supervision;
- physical resource accounting;
- local IPC and Windows/Linux hardening;
- small Java `madre-kernel-client`;
- real native llama.cpp worker path.

### Not yet implemented

The semantic SDK/Module layer and MADRE Runtime described by the accepted architecture are still missing from the active tree. Their architecture is documented; their implementation should be built cleanly rather than restored wholesale from discarded historical code.

Historical semantic code remains evidence for recovering settled contracts. In particular, the current SPIRA architecture deliberately retains the previously established `EffectProfile`, Operation-bound Privacy/output Sensitivity and concrete ReasoningRequest composition model without restoring obsolete implementation topology.

## Documentation

Start here:

- [`docs/product/owner-intent-corpus.md`](docs/product/owner-intent-corpus.md) — authoritative product reasoning and meanings;
- [`MADRE.md`](MADRE.md) — concise cross-repository product definition and authority order;
- [`docs/architecture/security-algebra.md`](docs/architecture/security-algebra.md) — operational SPIRA carriers, `EffectProfile`s and comparison points;
- [`docs/architecture/mid-level-architecture.md`](docs/architecture/mid-level-architecture.md) — accepted whole-system architecture and interaction diagrams;
- [`docs/architecture/kernel.md`](docs/architecture/kernel.md) — current physical Kernel architecture;
- [`docs/architecture/lane-c-native-kernel.md`](docs/architecture/lane-c-native-kernel.md) — pointer to current Kernel architecture and archived original Lane C contract.

## Development principle

MADRE is a one-Owner research/product project developed heavily with AI assistance.

The target is:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not infer missing MADRE semantics from industry convention, historical generated code or the fact that another AI platform uses a particular abstraction.

Do not simplify established contracts by removing the ownership/application points that make them operational. MADRE aims for small explicit concepts, not vague abstractions.