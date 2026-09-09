# Implementation Baseline

This document describes current repository state only. It is not product authority.

Product meaning comes from `MADRE.md`; detailed architecture comes from `docs/architecture/`. Design rationale lives under `docs/design-memory/`.

## Current development stage

**Kernel foundation convergence is complete.**

The next substantive stage is the modular SDK plus the shipped default CORE Module. The final Security Algebra formula remains a separate focused architecture/research task and is not part of the completed Kernel convergence.

## Implemented repository truth

The current `madre` package implements:

- bound `SecurityID` / `SecurityObject` state with subject-kind-specific normalized values and immutable integrity binding;
- carried `SecurityContext` composition persisted with durable work;
- an explicitly named compatibility evaluator preserving the previous max/min implementation behavior behind a replaceable `SecurityEvaluator` seam rather than presenting that formula as canonical architecture;
- durable Module manifests plus public Agent/Skill/Workflow/Operation descriptors;
- boundary-filtered discovery from carried security facts without making registry state an independent authorization source;
- explicit interface-segregated Agent and Operation broker endpoints with return-boundary evaluation;
- broker dispatch/effect evidence, including unknown Operation effect after uncertain dispatch failure;
- `WorkSubmission -> WorkRecord -> WorkAttempt` durable execution records;
- reference-only `MaterialHandle` durable work with no pushed/cached durable payload path;
- just-in-time Module-owned material resolution after eligibility, compatible Capability selection, security evaluation and scarce-resource readiness;
- material reference, digest and bound-SecurityObject continuity verification;
- restart/retry material reacquisition from the Module resolver;
- generic non-durable transient inference with no `WorkRecord`, queue entry or recovery promise;
- `InferenceRequirement` separation of hard constraints, ordered preferences and fallback permission across specialization/modality, latency, effort/quality, cost, locality, resources and provider/model/mechanism identity;
- Capability descriptors and deterministic selection that keep provider identity distinct from mechanism identity;
- one OpenAI-compatible HTTP Capability adapter whose transport/authentication schema remains adapter-private;
- delayed eligibility, originator fairness, priority/FIFO ordering and one heavyweight local execution slot shared by transient and durable inference;
- cancellation, retry and interrupted-attempt recovery;
- transient durable-result delivery with digest/size/delivery evidence and truthful loss after restart;
- SQLite persistence with no prompt/context/output/provider-error payload fields;
- interface-segregated Module-facing protocols for registration, transient inference, durable work, material resolution, inspection/result access, discovery and optional Agent/Operation endpoints.

The implementation has no semantic prompt/output inspection and no generic AI shell/Internet surface.

## Kernel foundation completion criteria

The Kernel foundation now demonstrates:

1. accepted/queued durable work contains no private payload in Kernel accepted-work state or SQLite;
2. durable material resolves just in time and restart/retry reacquire and verify it;
3. generic transient inference works without durable work state;
4. mechanism selection separates Module hard requirements, soft preferences/fallbacks and installed physical mechanisms;
5. public Module contracts are interface-segregated and do not require persistence, scheduler, FastAPI or provider-adapter internals;
6. security state uses bound per-object SecurityIDs/SecurityObjects with carried composition and no independent ACL/role/grant/token authorization machinery;
7. the current compatibility formula is isolated as implementation behavior and not represented as canonical product meaning;
8. broker, scheduling, cancellation/recovery, result and provider-adapter behavior remains working;
9. generated results can be consumed/reused by Modules without Kernel semantic interpretation;
10. external/system effects remain bounded by explicit Operations/mechanisms;
11. persistence/schema tests inspect SQLite columns and raw database bytes for private-material absence;
12. canonical architecture docs and this baseline agree with executable responsibility placement.

Locked pytest/Ruff/mypy/build/wheel validation is defined by `.github/workflows/ci.yml` and remains the validation authority for each implementation commit/PR.

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

Development can now move to:

- materializing the public MADRE SDK over the stable interface-segregated boundary;
- typed/OOP-friendly contracts while remaining language-neutral;
- implementing the shipped CORE Module as the first substantial SDK consumer;
- implementing CORE interaction/fallback behavior as Module/Agent behavior using ordinary transient/durable primitives;
- using real Module/CORE integration to refine public contracts without moving semantics into Kernel;
- adding broader provider/mechanism integrations from concrete need;
- pursuing richer resource optimization from measured workloads;
- designing and validating the final Security Algebra formula as a focused research/architecture task.

## Validation state

Focused convergence tests cover the required positive and negative paths, including JIT resolver timing behind the heavyweight-local slot, restart/retry reacquisition, material continuity failures, transient non-durability, hard/preferred/fallback selection, security binding tamper detection, durable carried-security recovery, registry independence, broker return evaluation, unknown Operation effects and generated-result reuse.

CI is the final locked-suite authority for the implementation PR.
