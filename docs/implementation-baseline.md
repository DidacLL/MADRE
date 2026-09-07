# Implementation Baseline

This file describes current repository truth, not future product semantics. Product authority is `MADRE.md` and `docs/architecture/`.

## Current architecture-aligned foundation

The `madre` package implements one coherent reference platform slice:

- immutable normalized security envelopes and deterministic sensitivity/trust/risk/scope relations;
- current requester trust/security resolved from registered Module boundaries rather than caller-minted requester envelopes;
- durable Module manifests and public owner-qualified Agent/Skill/Workflow/Operation descriptors;
- boundary-filtered discovery;
- explicit Agent/Operation broker routing into Module-owned endpoints with independently governed return material;
- append-only broker dispatch/effect evidence, including unknown Operation effect after uncertain dispatch failure;
- architecture-neutral `WorkSubmission -> WorkRecord -> WorkAttempt` execution;
- immediate transient material plus delayed originator-owned material references;
- digest/envelope/provenance verification when material is supplied for execution;
- delayed execution re-resolves the current originator Module security boundary;
- capability registry with deterministic constraint- and security-aware physical adapter selection;
- `local_only` enforcement and loopback enforcement for the reference local HTTP capability;
- provider-specific OpenAI-compatible chat handling isolated inside one Capability adapter;
- delayed eligibility, originator fairness, priority/FIFO ordering and one heavyweight local execution slot;
- cancellation, retry and interrupted-attempt recovery;
- transient result delivery with durable digest/size/delivery evidence only;
- startup truth that unconsumed transient result bytes are lost after restart;
- SQLite persistence for work metadata, public descriptors, broker evidence and structured security-decision evidence.

## Privacy invariant implemented

The runtime schema has no input/result content columns and no durable provider-error message column. The deterministic test suite writes sentinel prompt/output/provider-error strings, executes work, consumes the result, closes storage and scans the actual SQLite files to prove those strings were never durably stored.

Correlation, reference and provenance metadata use bounded identifier-shaped representations to prevent "opaque metadata" from silently carrying prompt/content prose.

Delayed work persists only the originator material reference, expected digest and envelope. After restart the originator provider must supply the material again.

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
- compatibility shims for the discarded semantic-Kernel architecture.

The reference scheduler is also not presented as a complete parallel/resource optimization engine. It preserves the already useful deterministic scheduling/recovery behavior while leaving richer resource concurrency for evidence-driven development.

## Reference capability and transport

The only concrete provider adapter currently present is OpenAI-compatible chat over HTTP. This is an optional Capability adapter, not the runtime work model. Tests also execute non-chat structured payloads through the same lifecycle and verify that an inadmissible first candidate is skipped in favor of a later permitted capability.

The local HTTP service is a compatibility/administration surface using one installation-admin bearer credential. It is not a complete per-Module authentication system. Registration/trust assignment is therefore an installation-controlled action; future transports must bind authenticated callers to registered Module identities without making security envelopes bearer credentials.

## Validation owner

CI remains the repository authority for locked pytest/Ruff/mypy/build/wheel-import validation. The self-review suite currently contains 16 deterministic tests and also performs direct SQLite content inspection.
