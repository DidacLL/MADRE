# Implementation Baseline

This file describes current repository truth, not future product semantics. Product authority is `MADRE.md` and `docs/architecture/`.

## Current architecture-aligned foundation

The `madre` package now implements one coherent platform slice:

- immutable normalized security envelopes and deterministic sensitivity/trust/risk/scope relations;
- durable Module manifests and public Agent/Skill/Workflow/Operation descriptors;
- boundary-filtered discovery;
- explicit Agent/Operation broker routing into Module-owned endpoints;
- architecture-neutral `WorkSubmission -> WorkRecord -> WorkAttempt` execution;
- immediate transient material plus delayed originator-owned material references;
- digest/envelope verification when material is supplied for execution;
- capability registry and deterministic physical adapter selection;
- provider-specific OpenAI-compatible chat handling isolated inside one Capability adapter;
- delayed eligibility, originator fairness, priority/FIFO ordering and one heavyweight local execution slot;
- cancellation, retry and interrupted-attempt recovery;
- transient result delivery with durable digest/size/delivery evidence only;
- startup truth that unconsumed transient result bytes are lost after restart;
- SQLite persistence for work metadata, public descriptors and security-decision evidence.

## Privacy invariant implemented

The runtime schema has no input/result content columns. The deterministic test suite writes sentinel prompt/output strings, executes work, consumes the result, closes storage and scans the actual SQLite files to prove those strings were never durably stored.

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

## Reference capability

The only concrete provider adapter currently present is OpenAI-compatible chat over HTTP. This is an optional Capability adapter, not the runtime work model. Tests also execute non-chat structured payloads through the same work lifecycle.

## Validation owner

CI remains the repository authority for locked pytest/Ruff/mypy/build/wheel-import validation. Local architecture tests additionally perform direct SQLite content inspection.
