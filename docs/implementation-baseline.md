# Implementation Baseline

This document describes current repository state only. It is not product authority.

Product meaning comes from `MADRE.md`; detailed architecture comes from `docs/architecture/`. Design rationale lives under `docs/design-memory/`.

Temporary instructions for reconciling the current implementation to the frozen corpus live under `docs/refactors/2026-09-final-corpus-alignment/`.

## Current development stage

The active stage is **Kernel foundation convergence**.

The goal is to align the working runtime foundation with the public execution, material, mechanism, security-object and SDK-facing contracts required before implementing the modular SDK and default CORE Module.

When an agent is asked simply to continue, this stage remains the default target until the completion criteria below are satisfied or the Owner changes direction.

## Implemented repository truth

The current `madre` package implements:

- immutable normalized `SecurityEnvelope` objects and carried `SecurityContext` state;
- a current `max(sensitivity) / min(trust) / max(risk)` reduction plus `trust >= sensitivity` and `trust >= risk` admission checks;
- durable Module manifests plus public Agent/Skill/Workflow/Operation descriptors;
- boundary-filtered discovery;
- explicit Agent/Operation broker routing with return-boundary evaluation;
- broker dispatch/effect evidence, including unknown Operation effect after uncertain dispatch failure;
- `WorkSubmission -> WorkRecord -> WorkAttempt` durable execution records;
- immediate pushed material plus delayed originator-owned material references;
- material digest/security-envelope continuity verification;
- Capability registry with deterministic compatibility and physical adapter selection;
- one OpenAI-compatible HTTP Capability adapter;
- delayed eligibility, originator fairness, priority/FIFO ordering and one heavyweight local execution slot;
- cancellation, retry and interrupted-attempt recovery;
- transient result delivery with digest/size/delivery evidence;
- truthful loss of unconsumed transient results after restart;
- SQLite persistence and privacy tests ensuring private input/output/provider-error sentinel content is not durably stored.

The implementation has no semantic prompt/output inspection and no generic AI shell/Internet surface.

## Active convergence gaps

### 1. Durable material ownership

Current code still permits pushed material for durable work and caches some work material in runtime memory.

Canonical execution requires every accepted/queued durable work item to be reference-only. Material must remain Module-owned, resolve through `MaterialHandle` just in time for a concrete attempt, be verified, used transiently and discarded. Restart/retry must reacquire it.

### 2. Generic transient inference

The runtime does not yet expose the final first-class non-durable transient inference primitive.

The primitive must accept minimal ephemeral material, execute through normal mechanism selection/security/resource handling, create no `WorkRecord`, provide no recovery guarantee and discard bytes after handoff.

It is a generic execution primitive. CORE or another Module may use it for fast interaction, but Kernel does not implement semantic fast-lane behavior.

### 3. Inference requirements versus installed mechanisms

Current `CapabilityRequest` is intentionally minimal.

The public contract must distinguish hard constraints from preferences/fallbacks and support the concrete dimensions required by MADRE, including modality/specialization, latency, effort/quality, cost policy, locality/privacy, resource constraints and optional provider/model/mechanism preference.

Mechanism-native features remain adapter extensions unless they become genuine generic requirements.

### 4. SDK-ready public boundary

Public descriptors/runtime protocols exist, but the final Kernel stage must leave a small, interface-segregated boundary suitable for a modular SDK.

A Module should be able to consume only the surfaces it needs: registration/descriptors, transient inference, durable work/material resolution, discovery/brokering, optional Agent/Operation endpoints, result access and SecurityObject propagation.

Module code must not require Kernel persistence, scheduler, transport-framework or provider internals.

### 5. SecurityObject schema and algebra

Current runtime represents every participant with a universal `SecurityEnvelope` shape and evaluates the current reduction formula.

Canonical architecture now requires object-bound `SecurityID` / `SecurityObject` semantics where each subject kind carries only the normalized dimensions relevant to it.

The exact final algebra formula remains a separate focused design task. Do not invent it during unrelated refactoring.

Kernel foundation should establish a clean schema/binding/carried-composition seam so the final formula can be implemented without introducing ACL/role/grant machinery or rewriting the rest of the runtime again.

The final formula must be validated against representative MADRE cases before becoming implementation authority.

## Stage completion criteria

Kernel foundation is complete only when all are demonstrated:

1. accepted/queued durable work contains no private payload in Kernel memory or SQLite;
2. durable material resolves just in time and restart/retry reacquire/verify it;
3. generic transient inference works without durable work state;
4. mechanism selection separates Module requirements/preferences from installed physical mechanisms;
5. public Module contracts are suitable for an interface-segregated SDK without Kernel internals;
6. security state is structurally ready for bound per-object SecurityIDs/SecurityObjects and carried composition without adding independent authorization machinery;
7. the current unapproved formula is not represented as canonical product meaning;
8. existing useful broker, scheduling, cancellation/recovery, result and provider-adapter behavior remains working;
9. generated results can be consumed/reused by Modules without Kernel semantic interpretation;
10. external/system effects remain bounded by explicit Operations/mechanisms;
11. locked pytest/Ruff/mypy/build/wheel-import validation is green;
12. canonical docs and this baseline agree with repository truth.

After these criteria are green, development should move to the modular SDK and default CORE Module unless the Owner directs otherwise.

## Structurally outside Kernel

Kernel does not own:

- Agent private reasoning/state/memory;
- semantic WorkPlans;
- Workflow execution semantics;
- Skill adoption internals;
- interaction/fast-response strategy;
- semantic routing over private prompt contents;
- generated-result meaning;
- application history/private context;
- durable model-output content;
- domain learning/adaptation;
- domain result interpretation/mutations;
- generic unrestricted AI shell/Internet authority;
- a local ACL/role/token/allowlist authorization system.

## Next stage: SDK + CORE

After Kernel convergence:

- materialize the public MADRE SDK over the stable boundary;
- use typed/OOP-friendly contracts while remaining language-neutral;
- implement the shipped CORE Module as the first substantial SDK consumer;
- implement CORE interaction/fallback behavior as Module/Agent behavior using ordinary transient/durable primitives;
- use real Module/CORE integration to refine public contracts;
- add broader provider/mechanism integrations from concrete need;
- pursue richer resource optimization from measured workloads;
- design the final Security Algebra formula as a focused research/architecture task.

## Validation state

CI remains the validation authority for the branch.

Documentation/corpus changes do not imply runtime implementation of the gaps above. Check current CI before claiming branch validation.
