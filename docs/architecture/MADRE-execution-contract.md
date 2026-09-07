# MADRE Execution Contract

This document defines the durable execution boundary beneath Module semantics.

## 1. Work lifecycle

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

A `WorkSubmission` is an explicit request for computation. The caller has already made the semantic decision that the computation is useful.

The durable `WorkRecord` stores only execution metadata. It never embeds prompt/context/result content.

An attempt records physical truth: selected capability, start/end, failure classification, output digest/size and other execution evidence.

## 2. Submission projection

The public submission contains:

- originator identity;
- requester security envelope;
- capability/model requirement;
- material reference plus material envelope;
- transient material for immediate work **or** expected digest for delayed work;
- eligibility, priority and execution constraints;
- opaque semantic correlation metadata.

The durable projection (`WorkSpec`) contains the material reference/digest/envelope but not the transient payload.

Correlation values locate Module-owned semantics such as a conversation turn or WorkPlan task. Kernel does not interpret them unless a field has explicit execution meaning.

## 3. Immediate material

For immediate execution, the caller may submit actual material transiently. MADRE verifies that its digest matches the immutable material envelope, writes only the durable projection, and holds content only in transient memory while it remains needed.

If the process ends before execution, durable work survives but content does not. Recovery therefore uses the same originator material-resolution path as delayed work.

## 4. Delayed material and restart

For delayed work, the Module retains source material. MADRE persists:

```text
originator
opaque material reference
expected digest
immutable material envelope
capability/model requirements
eligible_at / scheduling metadata
```

When the work becomes eligible, Kernel asks the originator for the material, recomputes its digest, checks the original reference/envelope and only then attempts execution.

Unavailable material produces truthful `material_unavailable` failure. Mismatched material produces `material_integrity` failure. MADRE never solves restart recovery by copying private content into durable runtime storage.

## 5. Capability selection

A `CapabilityRequest` describes execution properties such as capability class, modality, model and optional exact capability identity.

Kernel selection is deterministic over registered capability descriptors and runtime/policy evidence. It is not semantic prompt routing.

The runtime-level adapter protocol is conceptually:

```text
execute(JSON-like payload, ExecutionConstraints) -> JSON-like result
```

A provider-specific adapter may use OpenAI chat messages, embeddings, speech frames or another protocol internally. The work model is not tied to one chat dialect.

## 6. Security admission

Before physical execution Kernel evaluates:

```text
requester envelope
material envelope
capability requirements
capability/destination envelope
actual execution boundary
current policy
```

The resulting decision and deficits are evidence. They are not reusable permission tokens.

## 7. Scheduling/resources

Kernel retains global deterministic scheduling state. The current reference implementation preserves:

- delayed eligibility;
- originator round-robin fairness;
- priority within an originator;
- FIFO tie breaking;
- one heavyweight local capability slot;
- durable queue sequencing;
- cancellation and retry transitions.

Future scheduling groups/dependencies may be added as explicit execution metadata without introducing semantic WorkPlan ownership into Kernel.

## 8. Result lifecycle

Successful physical output is transient:

```text
Capability -> transient result -> originator consumes -> bytes discarded
```

Durable evidence contains output digest, size, production time, attempt reference and delivery state.

Delivery states distinguish:

- awaiting consumption;
- consumed;
- lost before consumption.

A restart turns any unconsumed transient result into `lost`; it does not preserve result bytes. Recomputing a lost result is a new retry/recomputation decision by the originator under applicable idempotency/effect semantics.

## 9. Cancellation/retry/recovery

Cancellation records whether accepted work was prevented or a request arrived while an attempt was running.

Interrupted physical execution is recorded as failure with unknown provider outcome. Retrying such work requires explicit `allow_unknown_outcome` intent.

At startup, any work still marked running is failed as interrupted. This preserves physical truth rather than inventing exactly-once guarantees.

Operations with external side effects require their own effect/repeatability semantics and must not be blindly retried.

## 10. Persistence invariant

The runtime database may contain work/capability/registry/security metadata, digests, delivery state and failure/evidence strings. It must contain no prompt/context/output payload columns and no content serialization hidden in correlation fields.

Privacy claims must be validated against actual storage, not only against object models.
