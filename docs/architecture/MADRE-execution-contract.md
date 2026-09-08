# MADRE Execution Contract

Authority: `MADRE.md` defines product meaning. This document owns detailed execution semantics: transient inference, durable work, material lifecycle, inference requirements/mechanism selection, scheduling-facing behavior, results and recovery.

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
- discarded after invocation/result handoff.

It exists to support the dual-lane interaction model in `MADRE.md` without forcing durable-work round trips onto the fast lane.

### Durable work

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

A `WorkSubmission` is an explicit request for schedulable/recoverable computation. The caller has already made the semantic decision that the computation is useful.

The durable `WorkRecord` stores execution metadata and carried security state. It never embeds or queues prompt/context/result content.

A `WorkAttempt` records physical truth: selected mechanism/model/boundary, start/end, failure classification, output digest/size and execution evidence.

## 2. Durable submission projection

A durable submission contains, in substance:

- originator identity for routing/correlation;
- the current carried `SecurityContext`;
- inference requirements/preferences;
- a verifiable material handle;
- eligibility, priority and execution constraints;
- opaque semantic correlation identifiers.

The material handle contains:

```text
material reference
expected digest
immutable material SecurityEnvelope
opaque retrieval coordination value / claim
```

The retrieval value is coordination data, not MADRE authority. It is not an authentication credential, role, ACL, trust grant or replacement for `SecurityAlgebra`. It lets the owning Module resolve the particular prepared material associated with the work rather than an ambiguous/stale reference.

The originator identity is likewise routing/correlation data, not an authorization credential.

At admission, the material envelope contributes to the supplied carried security context. The resulting context is persisted in `WorkSpec` as lifecycle security history. Detailed admissibility semantics belong to `MADRE-security-algebra.md`.

The durable projection contains no payload.

## 3. Durable material ownership and just-in-time resolution

For all durable work, whether eligible immediately or much later:

> MADRE never owns queued or merely accepted prompt/context/material content.

The Module retains the actual prepared material. Kernel should establish execution facts that do not require the payload before requesting it, including eligibility, compatible mechanism candidates, candidate boundary admissibility and resource availability where practical.

Only when work is genuinely about to execute does Kernel ask the originator to resolve the material handle.

Kernel verifies at least:

```text
reference continuity
expected digest
exact immutable material envelope / integrity
```

Only verified material enters transient Kernel memory. It is discarded after that concrete execution attempt completes or fails.

Unavailable material produces truthful `material_unavailable` failure. Mismatched material produces `material_integrity` failure.

There is no durable prompt cache, encrypted prompt vault or queued in-memory material cache in Kernel.

## 4. Restart, retry and uncertain effects

Accepted work survives restart as execution intent and verification metadata, not private content.

```text
WorkRecord restored
    -> wait until selected for execution
    -> resolve material again from originator
    -> verify
    -> execute
```

A retry likewise resolves material again rather than reusing stale Kernel-owned bytes.

Work that was physically running when the process/machine stopped becomes `interrupted`. Kernel does not pretend to resume model computation unless a concrete mechanism later provides explicit checkpoint/resume semantics.

Inference can generally be recomputed through an explicit retry. Operations or other externally visible side effects are different: if dispatch outcome is uncertain, effect is recorded conservatively and must not be blindly repeated.

## 5. Inference requirements and mechanism selection

The current code calls physical execution implementations **Capabilities**. In this contract they are available physical inference/execution mechanisms with declared properties.

Modules generally request execution properties rather than enumerate installed mechanisms. Hard constraints must remain distinguishable from preferences/fallbacks.

Relevant dimensions include, as concrete needs emerge:

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

Kernel deterministically matches requirements/preferences against registered mechanism descriptors and current resource/availability state. It does not inspect prompt meaning to choose a reasoning strategy.

A preferred provider/model is not automatically an exact hard requirement. A user preference such as "use Claude" may permit fallback to the closest compatible admissible mechanism when the request allows it.

Paid execution is an execution property with application-visible evidence. Cost should normally be surfaced visually by the Module/UI rather than forcing awkward conversational permission text.

## 6. Mechanism adapters and provider ecosystems

Provider/backend integration belongs to Capability adapters or external provider software.

A concrete mechanism may use:

```text
provider request/response schemas
API keys
OAuth/browser/account login
vendor CLI sessions
MCP client/server paths
HTTP / SDK / local IPC
local gateways
backend-specific validation
model loading
backend/device/cache behavior
user-installed automation bridges over local software
```

One provider may therefore contribute many mechanisms. They must not be flattened when cost, latency, authentication, modality, model inventory or operational behavior differs.

The current OpenAI-compatible HTTP adapter is one implemented mechanism only. Its API-key option is not a statement that API keys or HTTP are MADRE's preferred/universal provider architecture.

The generic Kernel layer needs bounded mechanism descriptors, execution properties, security/boundary facts and a callable adapter interface.

## 7. Security at execution boundaries

Execution uses the carried deterministic security algebra defined in `MADRE-security-algebra.md`.

For a mechanism candidate, conceptually:

```text
persisted carried SecurityContext
    + candidate mechanism SecurityEnvelope
    -> SecurityAlgebra
```

Material resolved from a Module must remain consistent with the material envelope already carried by the work.

Provider login/API-key/session mechanics only access that external mechanism. They do not grant MADRE authority.

Kernel does not perform semantic truth, prompt-injection, hallucination or generic AI-safety scoring as part of current execution admission.

## 8. Scheduling and resources

Kernel owns global deterministic scheduling/resource state.

The architecture requires support for:

- latency-sensitive interactive inference;
- immediately eligible and delayed durable work;
- application/originator fairness and priority;
- budgets/deadlines as execution metadata where implemented;
- GPU/CPU/RAM admission;
- model residency/device coordination where implemented;
- cancellation and retry;
- restart recovery.

MADRE targets ordinary personal machines where VRAM and RAM may already be heavily constrained by the OS and other applications. Long background work must not make interactive Modules feel blocked.

The current reference scheduler preserves delayed eligibility, originator round-robin fairness, priority/FIFO ordering, one heavyweight local execution slot and durable queue sequencing. It is not a complete resource optimizer, and the fast lane requirement does not prescribe a particular preemption algorithm.

## 9. Result lifecycle

Successful physical output is transient:

```text
inference mechanism -> transient result -> originator consumes -> bytes discarded
```

Durable-work evidence may contain output digest, size, production time, attempt reference and delivery state.

A restart turns an unconsumed transient durable-work result into `lost`; it does not preserve result bytes.

Transient interactive inference has no durable result/recovery promise at all.

## 10. Cancellation and failure evidence

Cancellation records whether accepted durable work was prevented or a request arrived while an attempt was running.

Provider/mechanism exception text is not persisted merely for convenience; durable failure evidence stores stable adapter/runtime failure codes.

## 11. Persistence invariant

The runtime database may contain work/mechanism/registry/security metadata, opaque references/retrieval coordination values, digests, delivery state and evidence codes. It must contain no prompt/context/output payload columns.

Privacy claims are validated against actual storage, not only object models.
