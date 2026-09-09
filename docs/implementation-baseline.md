# Implementation Baseline

This document describes current repository state only. It is not product authority.

Product meaning comes from `MADRE.md`; detailed architecture comes from `docs/architecture/`. Design rationale lives under `docs/design-memory/`.

## Current development stage

**The Kernel foundation and the first modular SDK + shipped default CORE foundation are implemented.**

The SDK/CORE stage proves the public architecture with two independent consumers: the shipped CORE Module and an Agentless reference Module used by integration tests. The final Security Algebra formula remains a separate focused architecture/research task.

## Implemented repository truth

### Kernel foundation

The `madre` package continues to provide:

- bound `SecurityID` / `SecurityObject` state with subject-kind-specific normalized values and immutable integrity binding;
- carried `SecurityContext` composition persisted with durable work;
- the explicitly named compatibility evaluator behind the replaceable `SecurityEvaluator` seam;
- durable Module manifests plus Agent/Skill/Workflow/Operation descriptors;
- security-filtered discovery and explicit owning-Module + exported-identity Agent/Operation brokering;
- broker propagation of the evaluated carried `SecurityContext` into the invoked Agent/Operation endpoint so downstream work can continue the same lifecycle state;
- `WorkSubmission -> WorkRecord -> WorkAttempt` durable execution;
- reference-only `MaterialHandle` work with JIT Module-owned material resolution and continuity verification;
- restart/retry reacquisition, delayed eligibility, cancellation and result evidence;
- generic non-durable transient inference;
- deterministic requirement/preference/fallback Capability selection;
- one OpenAI-compatible HTTP Capability adapter;
- SQLite persistence without prompt/context/output/provider-error payload fields.

The SDK stage exposed two narrow public-brokering deficiencies in the Kernel-facing protocols: target lookup had relied on the exported Agent/Operation id without using its owning Module, and the already-evaluated carried security context stopped at the broker instead of reaching the invoked endpoint. Those seams now route by owning Module plus exported identity and forward the carried context. They correct routing/security continuity without adding semantic target choice, authorization machinery or other Module meaning to Kernel.

### Modular SDK

The `madre_sdk` package now materializes the public Module semantic boundary without importing Kernel runtime/storage/service/provider internals.

It provides:

- `Module` manifest/endpoint composition over public registration protocols;
- a minimal `Agent` with purpose, instructions, contracts, security and execution behavior, plus `from_instructions(...)`;
- portable `Skill` and `Workflow` value structures with public descriptors and no universal executor;
- a lightweight `WorkPlan` projection protocol whose semantic state remains Module-owned;
- bounded `Operation` behavior over the existing public effect/repeatability/security contract;
- `Artifact` and `ContextBundle` construction, derivation and generated-output reuse;
- new security binding for each derived material representation while preserving independent Sensitivity/IntendedUse values unless the Module explicitly changes them;
- `MaterialRepository` as an optional Module-owned resolver helper producing valid transient material and durable `MaterialHandle`s;
- small clients for transient inference, durable submission/result access, discovery, Agent brokering and Operation brokering;
- continuation of an incoming carried `SecurityContext` through nested inference, durable submission and explicit brokering;
- typed security-object construction/context helpers;
- `CoreSelection` + `CoreDelegate` as ordinary Module configuration/fallback helpers whose selected Module identity participates in routing.

The SDK does not define Agent sessions, mandatory memory/state, a generic Planner, a Workflow engine, Skill instances, Task ontology or a generic credential/shell/Internet framework.

### Shipped default CORE

The `madre_core` package implements the default CORE-capable Module entirely through `madre_sdk`.

Its current behavior demonstrates:

- ordinary Module registration and Agent endpoint publication;
- a default interaction Agent using transient `model.inference.chat` for its immediate natural-response path, preferring interactive/standard latency rather than requiring a special Kernel lane;
- optional CORE-private continuation decisions that can explicitly delegate to another visible Agent or submit ordinary durable follow-up inference;
- preservation of inbound carried security facts through CORE transient inference, delegation and durable continuation;
- no `[[MADRE_REASONING:...]]` marker protocol and no Kernel fast lane;
- strongest currently expressible actor trust/isolation values for the shipped Module and interaction Agent;
- independently high/max Sensitivity on CORE-owned interaction/context/output material;
- Module-owned material resolution for durable continuation;
- ordinary configured replacement through `CoreSelection`, with no Kernel awareness of the literal shipped CORE module identity.

### Reference Module proof

The integration suite includes an Agentless reference Module implemented with the SDK. It demonstrates registration, Artifact/ContextBundle preparation, transient inference, durable work with Module-owned JIT material, generated-output reuse and fallback delegation to configured CORE.

A second test CORE-capable Module is selected through SDK configuration without Kernel modification.

## Architecture boundary evidence

Tests enforce that:

- `madre_sdk` imports only `madre.contracts`, `madre.interfaces`, `madre.registry` and `madre.security` from the Kernel namespace;
- `madre_core` imports no `madre.*` package directly and reaches MADRE only through `madre_sdk`;
- `madre` imports neither `madre_sdk` nor `madre_core`, keeping CORE/Agent/Workflow/WorkPlan semantics outside Kernel;
- brokered material/participant SecurityIDs remain present in CORE's downstream transient and durable security contexts;
- CORE selection uses both the configured Module and interaction-Agent identity;
- CORE contains no legacy fast/deeper marker protocol.

## Structurally outside Kernel

Kernel still does not own Agent private reasoning/state/memory, semantic WorkPlans, Workflow/Skill execution, conversation state, interaction strategy, semantic fallback routing, prompt construction, user profiles, generated-result meaning, domain mutations, generic unrestricted shell/Internet authority or the final Security Algebra formula.

## Validation authority

The locked validation suite remains `.github/workflows/ci.yml`:

```text
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
wheel reinstall + isolated madre/madre_sdk/madre_core imports
```

The implementation PR is the final CI authority for this stage.

## Next substantive stage

With the SDK/public Module boundary and shipped CORE foundation materialized, MADRE can move to the next product/research stage without extending Kernel by inertia. Candidate work now includes focused final Security Algebra design/validation, richer real CORE product behavior/UI, concrete third-party/domain Module integration and additional inference mechanisms when demanded by real consumers.
