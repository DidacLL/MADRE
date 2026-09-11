# MADRE Execution Contract

Authority: `MADRE.md` defines product meaning. This document owns transient/durable
execution, material lifecycle, physical mechanism selection, scheduling, results,
recovery, and execution-facing security composition.

## Transient and durable execution

Transient inference executes immediately and returns transient generated material.
Durable execution records:

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

Durable work is reference-only. A `MaterialHandle` contains exact reference, digest,
SecurityObject, SecurityEvidence, and optional opaque coordination. Kernel resolves
the actual material from its owning Module just in time for an accepted attempt,
checks exact continuity, uses it transiently, and discards it.

`WorkSubmission` deliberately has no direct-user interaction. Scheduling, delayed
eligibility, restart, and retry cannot replay an immediate exceptional crossing.

## Material lifecycle

The Module owns prompt/context/material bytes and their retention. Kernel storage may
contain public execution intent, reference/binding metadata, SecurityObjects, accepted
relation normal forms, derivations, compact decisions, digests, and lifecycle evidence.
It never contains prompt/context/output payload columns.

Resolution happens only after a concrete Capability route has passed prospective
disclosure composition and the attempt owns its resource slot. An unavailable source
fails as `material_unavailable`; reference, digest, SecurityObject, or evidence mismatch
fails as `material_integrity`.

## Capability model and selection

A Capability is one physical inference/execution mechanism. Provider, model,
mechanism, and execution boundary are distinct facts. One provider may expose API,
CLI/session, SDK, MCP, gateway, or local bridge mechanisms with different contracts.

Hard requirements filter capabilities. Preferences deterministically rank compatible
candidates. Fallback controls whether unlisted choices remain eligible. Heavy local
mechanisms share the resource admission lane.

Every actual Capability observation boundary has explicit Privacy. The
OpenAI-compatible adapter normalizes omitted Privacy to `UNKNOWN`; an explicitly
declared P1..P5 value is valid regardless of `execution_boundary`. Location affects
transport/resource compatibility, never Privacy valuation.

For each candidate, Kernel composes a prospective `DisclosureNormalForm` directly
from the exact material scope and candidate observer. A rejected candidate receives
no bytes, is retained only as denied decision evidence, and is not merged into carried
evidence. Only the selected accepted relation is archived with the attempt.

## Transient inference

An immediate request carries originator, exact material, SecurityEvidence, inference
requirements, constraints, and optionally the active direct-user interaction obtained
from its `InvocationContext`. Kernel allocates a fresh crossing identity, composes the
candidate disclosure, executes one accepted Capability, and returns generated payload,
digest/size, physical mechanism facts, source/producer identities, and accepted
evidence.

Generated output has no automatic Integrity. A requesting Module must create ordinary
material and may later create a separately validated Integrity-bearing projection.

## Durable lifecycle

Accepted work retains immutable evidence and intent. Scheduling supports immediate
and delayed eligibility, priority/FIFO within an originator, originator fairness,
cancellation, restart recovery, and explicit retry.

Capability disclosure is evaluated before private material resolution. When the
selected route is ready, the accepted disclosure relation identity is attached to the
attempt. Retry reuses the same work intent and does not derive security from retry
count, prior success, Work identity, or accumulated participants. Selecting a
different candidate composes that candidate independently.

An interrupted running attempt has unknown physical outcome and becomes a stable
`interrupted` failure. Retry requires explicit `allow_unknown_outcome`; this is
lifecycle policy, not a security facet.

## Brokered Agent and Operation execution

Broker preserves explicit active Module/Agent/Operation identity, exact publication
and endpoint attachment, client lifetime binding, and actual-recipient directionality.

Agent input and output each compose only their own actual disclosure. Operation input
resolves the selected immutable profile from `OperationUse`, then composes:

- disclosure from material to actual target/additional observers;
- control from that profile and actual non-user controllers;
- effect execution from that profile and actual executors.

All must be accepted before dispatch. Additional semantic controllers and external
observers are typed SecurityObjects. Broker itself adds protocol-known observers and
executors. Return paths create a fresh disclosure to captured requester recipients.

If Operation dispatch raises after an external effect may have occurred, Broker
reports `UnknownOperationEffect` and does not retry.

## Direct-user execution

Only an immediate invocation can carry `DirectUserInteraction`. Kernel constructs the
exact `DirectUserAction`; callers cannot submit stored action evidence as authority.
The action binds source revision/digest, observer route, current execution/crossing,
and exact Operation/EffectProfile when present. It covers one otherwise-inadmissible
disclosure without modifying Sensitivity or Privacy.

Changed input, route, profile, effect, or execution creates a different crossing and
requires a new live action. Durable work and background continuation cannot carry or
replay it.

## Results and evidence

Successful output is transient:

```text
Capability -> transient result -> originator consumes -> bytes discarded
```

Durable result evidence contains digest, size, production time, delivery state, and
source/producer SecurityIDs. It has no output Integrity. Restart marks unconsumed
transient output truthfully lost.

SQLite stores `security_evidence_json`, canonical typed `security_relation` rows,
separate denied `security_decision` records, derivations, and accepted attempt
disclosure relation identities. The schema fingerprint recreates incompatible
development databases; there is no migration compatibility for superseded formats.

The fixed evidence audit may reconstruct stored relations and detect identity or
derivation conflicts. Ordinary runtime admission never rescans unrelated stored
evidence to calculate a decision.
