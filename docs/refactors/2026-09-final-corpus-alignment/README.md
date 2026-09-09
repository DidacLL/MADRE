# Final Corpus Alignment Refactor

Status: **temporary, non-authoritative migration context**.

Product authority is `MADRE.md` plus the focused architecture documents. This folder exists only to help the next implementation run reconcile the current branch to that corpus without reloading historical discussions.

Delete or archive this folder after the refactor is complete and `docs/implementation-baseline.md` no longer needs it.

## Goal

Bring the current working Kernel/runtime foundation into structural agreement with the frozen MADRE corpus while preserving useful existing behavior.

Do not use this refactor to redesign Module semantics, implement CORE, create a full SDK, or invent the final Security Algebra formula.

## Current structural deltas

### Durable material

Current runtime still supports pushed/cached material for durable work.

Target:

```text
all durable work
    -> MaterialHandle only while queued/accepted
    -> Module resolver called JIT when attempt is ready
    -> digest + SecurityID/binding verified
    -> transient bytes discarded after attempt
```

Preserve delayed work, retry/recovery and privacy-tested persistence.

### Transient inference

Target a generic non-durable inference primitive:

```text
Module -> ephemeral input -> Kernel -> Capability -> transient result -> Module
```

It has no `WorkRecord` or recovery lifecycle.

Do not implement CORE fast-response semantics inside Kernel. CORE/SDK will compose this primitive later.

### Mechanism requirements

Refine Module requests enough to separate hard requirements from preferences/fallbacks across the concrete execution dimensions in `MADRE-execution-contract.md`.

Preserve the existing OpenAI-compatible adapter as one useful Capability; do not treat it as provider architecture.

### Security schema seam

Current implementation has universal `SecurityEnvelope` objects and `SecurityContext` reduction.

Target architecture is bound `SecurityID`/`SecurityObject` semantics with subject-kind-specific values and carried composition.

This refactor may establish/rename the schema and propagation seam, but **must not invent the final algebra formula**.

The existing formula remains implementation behavior until the dedicated algebra design task replaces it with a tested formula.

Avoid introducing ACLs, roles, grants, clearances, bearer authorization, sensitivity ceilings or mirrored requirement/property matrices.

### SDK-ready public boundary

Leave Kernel contracts clean enough that the next stage can build the SDK without importing persistence, scheduler, HTTP framework or provider internals.

Keep optional Module responsibilities segregated: Agent endpoint, Operation endpoint, material resolver, work/result access and descriptor publication should not force one giant Module interface.

## Preservation requirements

The refactor must preserve unless directly incompatible:

- delayed eligibility;
- work/attempt durability and restart recovery;
- cancellation/retry behavior;
- originator fairness / priority / FIFO behavior;
- scarce-local-resource admission;
- public descriptor discovery/brokering;
- uncertain external Operation effect handling;
- transient result delivery/loss evidence;
- provider adapter support;
- SQLite privacy invariants;
- generated-output reuse by Modules;
- explicit bounded Operations/mechanisms for external/system effects.

## Validation

Use the locked repository suite plus targeted new tests for each changed contract.

Inspect persisted SQLite bytes/columns after material/security-schema changes to ensure private payload content remains absent.

The refactor is complete only when `docs/implementation-baseline.md` completion criteria are satisfied and documentation matches executable truth.
