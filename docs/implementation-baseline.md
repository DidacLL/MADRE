# Implementation Baseline

This file describes current repository truth, not future product semantics. Product authority is `MADRE.md` and `docs/architecture/`.

## Current architecture-aligned foundation

The `madre` package implements one coherent reference platform slice:

- immutable normalized security envelopes;
- carried `SecurityContext` state accumulated through the request/work lifecycle;
- conservative deterministic sensitivity/trust/risk composition without registry grants, ACLs or external authorization tables;
- durable Module manifests and public Agent/Skill/Workflow/Operation descriptors used for discovery/routing only;
- boundary-filtered discovery over caller-carried context plus candidate descriptor facts;
- explicit Agent/Operation broker routing into Module-owned endpoints with independently evaluated return material;
- append-only broker dispatch/effect evidence, including unknown Operation effect after uncertain dispatch failure;
- `WorkSubmission -> WorkRecord -> WorkAttempt` execution with carried security state persisted in WorkSpec;
- immediate transient material plus delayed originator-owned material references;
- digest/envelope verification when material is supplied for execution;
- delayed execution continuing from persisted carried security state rather than refreshing registry authority;
- Capability registry with deterministic compatibility, optional `local_only`, security-algebra evaluation and physical adapter selection;
- provider-specific OpenAI-compatible chat handling isolated under the Capability adapter boundary, including optional provider API-key configuration;
- delayed eligibility, originator fairness, priority/FIFO ordering and one heavyweight local execution slot;
- cancellation, retry and interrupted-attempt recovery;
- transient result delivery with durable digest/size/delivery evidence only;
- startup truth that unconsumed transient result bytes are lost after restart;
- SQLite persistence for work metadata, public descriptors, broker evidence and structured security-decision evidence.

## Security invariant implemented

No Module or work is authorized because it is registered, accepted, named, authenticated by a MADRE bearer token, or previously permitted.

Registration means existence/discovery/routing only.

A work submission carries its current SecurityContext. MADRE appends the material envelope at admission and persists the resulting context. Each later physical boundary appends its own envelope and is evaluated from that carried state. Registry mutation does not rewrite existing WorkRecord security history.

The current minimal reference relation derives highest carried sensitivity, lowest carried trust and highest carried risk; trust must cover both sensitivity and risk. Scope/domain facts are accumulated but their semantic classification remains Module-owned.

Current Kernel trust is boundary/provenance trust only. The implementation does not perform prompt-injection detection, semantic truth scoring, hallucination detection or generic AI-content safety analysis.

## Privacy invariant implemented

The runtime schema has no input/result content columns and no durable provider-error message column. The deterministic suite writes sentinel prompt/output/provider-error strings, executes work, consumes the result, closes storage and scans the actual SQLite files to prove those strings were never durably stored.

Delayed work persists only the originator material reference, expected digest, material envelope and carried security context. After restart the originator provider must supply the material again.

## Known Kernel completion gaps

The current implementation predates two now-canonical refinements and must be converged before the Kernel stage is considered complete:

1. **Durable material ownership:** `ImmediateMaterial` can still be pushed into `WorkSubmission` and cached in `WorkRuntime._materials`. Canonical architecture now requires all durable accepted/queued work to be reference-only, with material acquired just-in-time from its Module for the concrete execution attempt. Immediate eligibility must not imply Kernel ownership of queued input.

2. **Transient fast inference:** there is not yet a first-class non-durable latency-sensitive inference path. Canonical interactive behavior distinguishes this transient route from durable work so minimal user-interaction input can be executed directly without queue/recovery semantics.

The current `CapabilityRequest` is also intentionally minimal (`kind`, `modality`, optional model/exact capability). The canonical selection model now requires a clearer distinction between Module execution requirements/preferences and the installed inference-mechanism inventory, including future dimensions such as latency class, effort/quality, cost policy, specialization, provider/model preferences and fallback semantics. Do not mistake the current OpenAI-compatible adapter or current request fields for the final provider/inference architecture.

## Deliberately absent

The Kernel does not implement or persist:

- Agent memory/state or Agent implementation classes;
- semantic WorkPlans or AgentTasks;
- a universal Workflow language/executor;
- Skill adoption internals;
- semantic routing over prompt contents;
- a CORE planner/Agent implementation;
- application history/private context storage;
- durable model output content;
- Module authentication/authorization infrastructure for the local single-owner installation;
- ACL/role/token/allowlist permission systems;
- compatibility shims for the discarded semantic-Kernel architecture.

The reference scheduler is not presented as a complete parallel/resource optimization engine. It preserves the already useful deterministic scheduling/recovery behavior while leaving richer resource concurrency for evidence-driven development.

## Capability/provider boundary

Capabilities remain first-class MADRE physical inference/execution mechanisms. Local models are the primary product focus, while permitted remote/provider mechanisms remain supported through replaceable adapters.

One provider may expose many distinct mechanisms: API, vendor CLI/session, MCP path, local gateway, SDK or another user-installed software adapter. Their authentication, cost, latency and model properties can differ and must not be flattened into one provider abstraction.

The concrete OpenAI-compatible HTTP adapter owns its provider request/response shape and optional API-key environment variable. It is one implemented mechanism, not a canonical example that future provider support must copy.

The local HTTP MADRE service remains as a transport surface but has no separate bearer-token authorization framework.

## SDK boundary direction

The public contracts are intended to become the basis of a modular MADRE SDK. CORE should be the first substantial consumer of that same public Module-facing boundary rather than calling privileged Kernel internals.

The SDK must not absorb Agent memory/state, WorkPlans, Workflow execution or domain semantics; those remain Module-owned.

## Validation owner

CI remains the repository authority for locked pytest/Ruff/mypy/build/wheel-import validation. The current implemented suite contains 16 deterministic tests and performs direct SQLite content inspection.
