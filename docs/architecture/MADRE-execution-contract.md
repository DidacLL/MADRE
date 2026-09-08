# MADRE Execution Contract

This document defines the durable execution boundary beneath Module semantics.

## 1. Work lifecycle

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

A `WorkSubmission` is an explicit request for computation. The caller has already made the semantic decision that the computation is useful.

The durable `WorkRecord` stores execution metadata and carried security state. It never embeds prompt/context/result content.

A WorkAttempt records physical truth: selected capability/model/boundary, start/end, failure classification, output digest/size and execution evidence.

## 2. Submission projection

The public submission contains:

- originator identity for routing/correlation;
- the current carried `SecurityContext`;
- capability/model requirements;
- material reference plus immutable material envelope;
- transient material for immediate work **or** expected digest for delayed work;
- eligibility, priority and execution constraints;
- opaque semantic correlation identifiers.

The originator identity is not an authorization credential. MADRE does not look up registered Module trust to decide whether the submission is permitted.

At admission, the material envelope is appended to the supplied security context and the algebra is evaluated. The resulting context is persisted in `WorkSpec` and becomes the security history carried by that work lifecycle.

The durable projection contains no transient payload.

## 3. Immediate material

For immediate execution the caller may submit actual material transiently. MADRE verifies its digest/envelope binding, appends that envelope to the carried context, writes only the durable execution projection, and holds content only while needed.

Transient material is discarded on terminal cancellation, admission failure, execution completion or execution failure.

## 4. Delayed material and restart

For delayed work, the Module retains source material. MADRE persists:

```text
originator
opaque material reference
expected digest
immutable material envelope
carried SecurityContext
capability/model requirements
eligible_at / scheduling metadata
```

When work becomes eligible, MADRE asks the originator for the material, recomputes its digest, and verifies reference/envelope continuity.

The carried security context is **not reconstructed from registry state**. A registry update cannot raise or lower the authority of previously accepted work.

If a new boundary is actually crossed later—for example a capability is selected—its current boundary envelope is appended at that point and the algebra is evaluated again.

Unavailable material produces truthful `material_unavailable` failure. Mismatched material produces `material_integrity` failure.

## 5. Capability selection

A `CapabilityRequest` describes physical execution requirements such as capability class, modality, model and optional exact capability identity.

Kernel selection is deterministic over registered capability availability, explicit execution constraints and current resource state. It is not semantic prompt routing.

For each compatible candidate, its capability boundary/security envelope is appended to the WorkRecord's carried security context and evaluated. A rejected candidate grants or denies nothing beyond that candidate crossing; selection may continue to another compatible candidate.

`local_only` remains an explicit execution constraint available to Modules. It is not a default policy and does not create an authentication mechanism.

The current Python reference execution payload is JSON-like because the implemented adapters currently use that representation. This is an implementation surface, not a universal MADRE architecture restriction. Binary/streaming representations should be introduced only when a concrete capability requires them.

## 6. Capability adapters and providers

Provider/backend integration belongs to Capability adapters.

An adapter may manage:

```text
provider request/response schemas
OpenAI-compatible contracts
provider API keys or login flows
HTTP / SDK details
backend-specific validation
model loading
backend/device/cache behavior
```

None of those provider details define Kernel work semantics or MADRE authorization.

The generic Capability layer only needs the bounded execution descriptor, physical execution boundary, model/modality compatibility, resource facts and callable adapter interface.

## 7. Scheduling/resources

Kernel retains global deterministic scheduling state. The current reference implementation preserves:

- delayed eligibility;
- originator round-robin fairness;
- priority within an originator;
- FIFO tie breaking;
- one heavyweight local execution slot;
- durable queue sequencing;
- cancellation and retry transitions.

The execution loop remains deliberately simple and does not claim a complete resource optimizer.

## 8. Result lifecycle

Successful physical output is transient:

```text
Capability -> transient result -> originator consumes -> bytes discarded
```

Durable evidence contains output digest, size, production time, attempt reference and delivery state.

A restart turns any unconsumed transient result into `lost`; it does not preserve result bytes.

## 9. Cancellation/retry/recovery

Cancellation records whether accepted work was prevented or a request arrived while an attempt was running.

Interrupted physical execution is recorded as failure with unknown provider outcome. Retrying such work requires explicit `allow_unknown_outcome` intent.

At startup, work still marked running is failed as interrupted. Operations with external side effects preserve uncertain effects rather than being blindly retried.

Provider exception messages are not persisted; durable failure evidence stores adapter/runtime failure codes.

## 10. Persistence invariant

The runtime database may contain work/capability/registry/security metadata, references, digests, delivery state and evidence codes. It must contain no prompt/context/output payload columns.

Privacy claims are validated against actual storage, not only object models.
