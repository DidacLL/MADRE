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

## Privacy invariant implemented

The runtime schema has no input/result content columns and no durable provider-error message column. The deterministic suite writes sentinel prompt/output/provider-error strings, executes work, consumes the result, closes storage and scans the actual SQLite files to prove those strings were never durably stored.

Delayed work persists only the originator material reference, expected digest, material envelope and carried security context. After restart the originator provider must supply the material again.

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

Capabilities remain first-class MADRE execution backends. Local models are the primary product focus, while permitted remote providers remain supported through replaceable adapters.

The concrete OpenAI-compatible HTTP adapter owns its provider request/response shape and optional API-key environment variable. Those details do not leak into Kernel security algebra or generic work contracts.

The local HTTP MADRE service remains as a transport surface but has no separate bearer-token authorization framework.

## Validation owner

CI remains the repository authority for locked pytest/Ruff/mypy/build/wheel-import validation. The suite contains 16 deterministic tests and performs direct SQLite content inspection.
