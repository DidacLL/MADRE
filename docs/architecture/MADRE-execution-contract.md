# MADRE Execution Contract

This document defines the execution boundary beneath Module semantics.

## 1. Two execution classes

MADRE exposes two logically distinct inference paths because latency-sensitive interaction and durable work have different ownership/recovery requirements.

### Transient interactive inference

```text
Module -> minimal ephemeral input -> Kernel inference mechanism -> transient result -> Module
```

This path is:

- optimized for low interaction latency;
- never queued as durable work;
- allowed to carry minimal input directly;
- not restart/recovery durable;
- discarded after the invocation/result handoff.

It exists to support natural interaction on constrained machines. Interactive Modules may use it as the fast lane of the dual-lane interaction pattern described in `MADRE.md`.

### Durable work

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

A `WorkSubmission` is an explicit request for recoverable/schedulable computation. The caller has already made the semantic decision that the computation is useful.

The durable `WorkRecord` stores execution metadata and carried security state. It never embeds or queues prompt/context/result content.

A WorkAttempt records physical truth: selected inference mechanism/model/boundary, start/end, failure classification, output digest/size and execution evidence.

## 2. Durable submission projection

A durable submission contains, in substance:

- originator identity for routing/correlation;
- the current carried `SecurityContext`;
- inference requirements/preferences;
- a verifiable material handle;
- eligibility, priority and execution constraints;
- opaque semantic correlation identifiers.

The material handle contains the information necessary to retrieve and verify the prepared Module-owned material later:

```text
material reference
expected digest
immutable material SecurityEnvelope
opaque retrieval coordination value / claim
```

The retrieval value is not a MADRE authorization token, trust grant, role, identity credential or replacement for `SecurityAlgebra`. It is opaque coordination data supplied back to the owning Module so that the correct prepared material can be resolved rather than an ambiguous/stale reference.

The originator identity is likewise not an authorization credential. MADRE does not look up registered Module trust to decide whether the submission is permitted.

At admission, the material envelope is appended to the supplied security context and the algebra is evaluated. The resulting context is persisted in `WorkSpec` and becomes the security history carried by that work lifecycle.

The durable projection contains no payload.

## 3. Durable material ownership and just-in-time resolution

For all durable work, whether eligible immediately or much later:

> MADRE never owns queued or merely accepted prompt/context/material content.

The Module retains the actual prepared material. Kernel should establish all execution facts that do not require the payload before requesting it, including eligibility, compatible inference mechanisms, relevant boundary algebra and executable resource availability where practical.

Only when the work is genuinely about to execute does Kernel ask the originator to resolve the material handle.

Kernel then verifies at least:

```text
reference continuity
expected digest
exact immutable material envelope / integrity
```

Only verified material enters transient Kernel memory. It is discarded after execution/failure/cancellation of that concrete attempt.

Unavailable material produces truthful `material_unavailable` failure. Mismatched material produces `material_integrity` failure.

There is no durable prompt cache, temporary encrypted prompt vault or queued in-memory material cache in Kernel.

## 4. Restart and retry

Accepted work survives restart as execution intent and verification metadata, not as private content.

After restart:

```text
WorkRecord restored
    -> wait until selected for execution
    -> resolve material again from originator
    -> verify
    -> execute
```

A retry likewise resolves material again rather than reusing stale Kernel-owned bytes.

The carried security context is **not reconstructed from registry state**. A registry update cannot raise or lower the authority of previously accepted work.

If a new boundary is actually crossed later—for example an inference mechanism is selected—its current boundary envelope is appended at that point and the algebra is evaluated again.

Work that was physically running when the process/machine stopped becomes `interrupted`. Kernel does not pretend to resume model computation unless a concrete mechanism later provides explicit checkpoint/resume semantics.

Operations/external effects retain conservative unknown-effect behavior and must not be blindly repeated.

## 5. Inference requirements and mechanism selection

The current code calls physical execution implementations **Capabilities**. In this contract they should be understood as available physical inference/execution mechanisms with known properties.

Modules generally request execution properties rather than knowing the installed mechanism inventory. The request model should be able to express hard constraints separately from preferences/fallbacks. Relevant dimensions include, as concrete needs emerge:

```text
modality / specialization
latency class (interactive / normal / background)
reasoning effort / quality target
cost policy (forbid paid / prefer free / paid allowed)
locality/privacy constraints
resource/availability constraints
preferred provider/model/mechanism
fallback permission/ordering
```

Kernel deterministically matches those requirements/preferences against registered mechanism descriptors and current resource/availability facts. It does not inspect prompt meaning to choose a reasoning strategy.

A preferred provider/model is not automatically an exact hard requirement. For example a user preference to use Claude may allow fallback to the closest compatible available mechanism if the Module/request explicitly permits fallback.

Paid execution is an execution property with application-visible evidence. User-facing cost information should normally be surfaced by the Module/UI rather than forced into awkward conversational permission text.

## 6. Mechanism adapters and provider ecosystems

Provider/backend integration belongs to Capability adapters or external provider software.

An adapter may manage:

```text
provider request/response schemas
API keys
OAuth/browser/account login
vendor CLI sessions
MCP client/server paths
HTTP / SDK / local IPC details
backend-specific validation
model loading
backend/device/cache behavior
user-installed automation bridges over local software
```

One provider may therefore contribute many different mechanisms. They must not be flattened into one provider abstraction when their cost, latency, authentication, modalities or operational behavior differ.

The current OpenAI-compatible HTTP adapter is one reference implementation only. Its API-key option is not a statement that API keys are MADRE's preferred or universal provider connection mechanism.

The generic Kernel layer needs only bounded mechanism descriptors, execution properties, boundary/security facts and a callable adapter interface.

## 7. Security admission

Kernel security is deterministic boundary/provenance algebra over explicit carried facts.

Current `trust` does not mean semantic truth, resistance to prompt injection, hallucination probability, answer quality or generic AI safety.

For a durable execution mechanism candidate:

```text
persisted carried SecurityContext
    + candidate mechanism SecurityEnvelope
    -> SecurityAlgebra
```

For the material crossing itself, the previously carried material envelope must remain consistent with the material resolved from the Module.

Provider login/API-key/session mechanics do not grant Kernel authorization. They are only adapter/provider access mechanisms.

## 8. Scheduling/resources

Kernel retains global deterministic scheduling/resource state.

The architecture requires support for:

- latency-sensitive interactive inference;
- immediately eligible and delayed durable work;
- application/originator fairness and priority;
- budgets/deadlines as execution metadata where implemented;
- GPU/CPU/RAM admission;
- model residency/device coordination where implemented;
- cancellation and retry;
- restart recovery.

The current reference scheduler preserves delayed eligibility, originator round-robin fairness, priority/FIFO ordering, one heavyweight local execution slot and durable queue sequencing. It is not presented as a complete resource optimizer.

The fast transient lane should receive latency-sensitive treatment appropriate to the resource-constrained target machines, but this requirement does not itself prescribe a particular preemption algorithm.

## 9. Result lifecycle

Successful physical output is transient:

```text
inference mechanism -> transient result -> originator consumes -> bytes discarded
```

Durable evidence contains output digest, size, production time, attempt reference and delivery state.

A restart turns any unconsumed transient durable-work result into `lost`; it does not preserve result bytes.

Transient interactive inference has no durable result/recovery promise at all.

## 10. Cancellation/recovery evidence

Cancellation records whether accepted durable work was prevented or a request arrived while an attempt was running.

Interrupted physical execution is recorded as failure with unknown backend outcome. Retrying such work requires explicit policy where the outcome may matter.

Provider exception messages are not persisted; durable failure evidence stores adapter/runtime failure codes.

## 11. Persistence invariant

The runtime database may contain work/inference-mechanism/registry/security metadata, references, retrieval coordination values, digests, delivery state and evidence codes. It must contain no prompt/context/output payload columns.

Privacy claims are validated against actual storage, not only object models.
