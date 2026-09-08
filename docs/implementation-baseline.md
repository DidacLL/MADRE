# Implementation Baseline

This document describes current repository state only. It is not product authority.

Product meaning comes from `MADRE.md`; detailed architecture comes from the focused documents under `docs/architecture/`.

## Current development stage

The active stage is **Kernel foundation convergence**.

The goal of this stage is to align the already-working runtime foundation with the final public execution/material/mechanism contracts required before modular SDK and CORE development begins.

When an agent is asked simply to continue, this stage remains the default target until the completion criteria below are satisfied or the Owner explicitly changes direction.

## Implemented repository truth

The current `madre` package implements:

- immutable normalized `SecurityEnvelope` facts and carried `SecurityContext` lifecycle state;
- conservative deterministic sensitivity/trust/risk composition without registry grants, ACLs or external authorization tables;
- durable Module manifests plus public Agent/Skill/Workflow/Operation descriptors used for discovery/routing only;
- boundary-filtered discovery from caller-carried security state;
- explicit Agent/Operation broker routing into Module-owned endpoints with independently evaluated return material;
- broker dispatch/effect evidence, including unknown Operation effect after uncertain dispatch failure;
- `WorkSubmission -> WorkRecord -> WorkAttempt` durable execution records;
- immediate pushed transient material plus delayed originator-owned material references;
- material digest/envelope continuity verification;
- delayed execution/retry continuing from persisted carried security rather than mutable registry authority;
- Capability registry with deterministic compatibility, optional `local_only`, boundary-algebra evaluation and physical adapter selection;
- one OpenAI-compatible HTTP Capability adapter with optional provider API-key environment configuration;
- delayed eligibility, originator fairness, priority/FIFO ordering and one heavyweight local execution slot;
- cancellation, retry and interrupted-attempt recovery;
- transient result delivery with durable digest/size/delivery evidence;
- truthful loss of unconsumed transient results after restart;
- SQLite persistence for work metadata, public descriptors, broker evidence and structured security-decision evidence;
- direct SQLite byte-inspection tests proving private input/output/provider-error sentinel content is not durably persisted.

Current Kernel `trust` is boundary/provenance trust only. No prompt-injection, truth, hallucination or generic AI-content scoring is implemented.

## Active convergence gaps

These are implementation gaps against current canonical architecture, not optional roadmap ideas.

### 1. Durable material ownership

Current code still permits `ImmediateMaterial` to be pushed into `WorkSubmission` and cached in `WorkRuntime._materials`.

Canonical execution now requires **all durable accepted/queued work to be reference-only**, with Module-owned material acquired just-in-time for the concrete execution attempt. Immediate eligibility must not imply Kernel ownership of queued input.

Restart and retry must reacquire material from the Module.

### 2. Transient fast inference

There is not yet a first-class non-durable latency-sensitive inference path.

The canonical dual-lane interaction model requires a transient route that accepts minimal ephemeral input directly, performs real inference, creates no `WorkRecord`, carries no recovery promise and discards input/result after handoff.

Kernel provides the execution primitive only; Module/Agent code owns acknowledgement, semantic routing, delegation/escalation and user-facing behavior.

### 3. Inference requirements versus installed mechanisms

Current `CapabilityRequest` is intentionally minimal: kind, modality and optional model/exact capability identity.

The canonical model distinguishes Module execution requirements/preferences from Kernel's installed inference-mechanism inventory. The public request/descriptor contracts still need to converge enough to express hard constraints versus preferences/fallbacks across concrete needs such as latency class, effort/quality, cost policy, specialization/modality, locality/privacy and provider/model preference.

Do not mistake the current OpenAI-compatible adapter, `local_only`, or current request fields for the complete inference architecture.

### 4. SDK-ready public Module boundary

The repository already has public descriptors and runtime/broker protocols, but the completed Kernel stage must leave them sufficiently segregated that a modular SDK/CORE consumer does not import Kernel persistence, scheduler, FastAPI or provider internals.

Material resolution, optional Agent endpoint and optional Operation endpoint responsibilities should remain independently consumable.

## Stage completion criteria

The Kernel foundation stage is complete only when all are demonstrated:

1. accepted/queued durable work contains no private material payload in Kernel memory or SQLite;
2. durable material is acquired just-in-time only when execution is genuinely ready;
3. restart and retry resolve Module-owned material again and verify reference/digest/envelope continuity;
4. transient interactive inference works without durable work state;
5. mechanism selection cleanly distinguishes Module requirements/preferences from installed physical mechanisms and preserves deterministic fallback/cost behavior;
6. carried security/registry-independence invariants remain intact and no new authorization framework appears;
7. existing broker, scheduling, cancellation/recovery, transient-result and provider-adapter behavior is not accidentally destroyed;
8. a realistic reference Module path can consume the public boundary end-to-end without privileged Kernel internals;
9. locked pytest/Ruff/mypy/build/wheel-import validation is green;
10. canonical docs and this baseline agree with repository truth.

Once these criteria are met, stop expanding Kernel by inertia and move to the modular SDK + CORE stage unless the Owner directs otherwise.

## Structurally outside Kernel

The following are intentionally not Kernel responsibilities:

- Agent memory/state or Agent implementation classes;
- semantic WorkPlans / Agent task meaning;
- universal Workflow language/execution;
- Skill adoption internals;
- semantic routing over prompt contents;
- application history/private context storage;
- durable model-output content;
- domain learning/adaptation;
- domain-specific result interpretation or mutations;
- local per-Module ACL/role/token/allowlist authorization infrastructure.

These are exclusions, not missing Kernel features.

## Explicitly deferred stages

After Kernel foundation convergence:

- build the modular MADRE SDK over the stable public Module boundary;
- implement the default CORE Module as the first substantial SDK consumer;
- use CORE/real Modules to expose remaining public-contract deficiencies;
- add broader provider/inference mechanisms as concrete integrations are needed;
- evolve richer resource optimization only from real workload evidence;
- consider deterministic AI-specific security signals only when a concrete design exists.

Deferred does not mean rejected; it means it is not part of the current Kernel completion stage.

## Current mechanism/provider surface

The concrete OpenAI-compatible HTTP adapter owns its provider request/response schema and optional API-key environment variable. It is one implemented physical mechanism, not a canonical template for provider ecosystems.

The current local HTTP MADRE service remains a transport surface and has no separate bearer-token authorization framework.

## Validation state

CI is the repository authority for the locked validation suite.

At the documentation-refactor starting head, the implemented suite contained 16 deterministic tests and the branch had passing locked pytest, Ruff, strict mypy, package build/wheel reinstall and isolated import validation. Documentation-only revisions after that point must not be mistaken for newly implemented runtime behavior; current CI results should be checked when claiming branch validation.
