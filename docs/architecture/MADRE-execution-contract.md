# MADRE Execution Contract

Authority: `MADRE.md` defines product meaning. This document owns transient/durable execution, material lifecycle, inference requirements/mechanism selection, scheduling-facing behavior, results and recovery.

## 1. Execution classes

MADRE exposes two execution lifecycles.

### Transient inference

```text
Module -> minimal ephemeral material -> Kernel -> Capability -> transient result -> Module
```

Transient inference:

- creates no durable `WorkRecord`;
- may carry minimal input directly;
- has no restart/recovery guarantee;
- discards request/result bytes after handoff;
- may request low latency or other execution properties.

A Module/Agent may use this primitive for interactive response, validation, probing, background reasoning or another semantic purpose. Kernel does not assign that meaning.

### Durable work

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

A `WorkSubmission` is an explicit request for schedulable/recoverable physical computation. The caller has already decided semantically that the computation is useful.

The durable `WorkRecord` stores execution metadata, material identity/integrity binding, carried security identities/history and scheduling state. It never embeds or queues prompt/context/result content.

A `WorkAttempt` records physical truth: selected mechanism/model, relevant security/boundary identities, timing, outcome, failure classification, output digest/size and execution evidence.

## 2. Durable submission projection

A durable submission contains, in substance:

- originator identity for routing/correlation;
- carried SecurityIDs/SecurityObjects and security-relevant history required for the lifecycle so far;
- inference requirements/preferences;
- a verifiable `MaterialHandle`;
- eligibility, priority and execution constraints;
- opaque semantic correlation identifiers.

A `MaterialHandle` contains enough information to reacquire exactly the prepared material later:

```text
material reference
expected digest
material SecurityID / immutable security binding
opaque retrieval coordination value
```

The retrieval coordination value is not MADRE permission or an authentication credential. It only identifies the prepared material to its owning Module/resolver.

The durable projection contains no private payload.

## 3. Durable material ownership and just-in-time resolution

For every durable work item, immediately eligible or delayed:

> Kernel owns execution intent, not queued private material.

The Module retains actual prepared material.

Before requesting the payload, Kernel should establish all practical facts that do not require it, including eligibility, compatible mechanism candidates, role-relevant security evaluation and resource readiness.

Only when a concrete attempt is genuinely ready does Kernel resolve the `MaterialHandle` through the Module-facing resolver.

Kernel verifies at least:

```text
reference continuity
expected digest
security binding continuity
```

Verified bytes exist only transiently for that attempt and are discarded afterwards.

Unavailable material produces truthful material-unavailable failure. Mismatch produces integrity/security-continuity failure.

No durable prompt cache, prompt vault or queued private-material cache belongs in Kernel.

## 4. Restart, retry and external effects

Accepted work survives restart as execution intent and verification/security-history metadata.

```text
restore WorkRecord
    -> select when eligible/resources permit
    -> construct/evaluate the prospective transition
    -> resolve material when ready
    -> verify
    -> execute
```

Retries reacquire material rather than depending on stale Kernel-owned bytes.

Interrupted inference normally recomputes unless the selected mechanism exposes a concrete checkpoint/resume capability.

Externally effectful Operations require different handling: if dispatch may have happened but outcome is unknown, Kernel records that uncertainty and does not blindly repeat the effect.

A retry selecting a different Capability, endpoint, EffectProfile or material representation is a new prospective security transition and is evaluated using those newly bound facts.

## 5. Inference requirements and mechanism selection

A **Capability** is an available physical inference/execution mechanism with known properties.

Modules generally express what execution they need, while Kernel knows which mechanisms are currently installed/available.

Hard constraints must remain distinguishable from preferences/fallbacks.

Relevant dimensions include, when required by real use cases:

```text
modality / specialization
latency class
reasoning effort / quality target
cost policy
locality/privacy constraints
resource/availability constraints
preferred provider/model/mechanism
fallback permission/order
```

Kernel deterministically matches these against `CapabilityDescriptor` facts and current resource state. It does not inspect prompt semantics to choose a reasoning strategy.

A preferred provider/model may be a soft preference unless the caller marks it as a hard requirement.

Paid execution is an execution property and should be visible to the owning Module/UI. Conversational negotiation is not a Kernel requirement.

## 6. Mechanism adapters and provider ecosystems

A concrete Capability adapter may use:

```text
provider request/response schemas
API keys or OAuth/account sessions
vendor CLI
SDK
MCP
HTTP / IPC
local gateway/bridge
model loading/runtime management
backend/device/cache/session controls
user-installed automation over local software
```

One provider may therefore contribute several distinct mechanisms.

Provider credentials access the provider/mechanism; they do not grant MADRE work authority.

Mechanism-native optimization such as model residency, KV-cache/session reuse or vectorized backend state belongs behind the adapter boundary. The SDK may expose controlled optional extension APIs for such mechanisms without making them universal Kernel request fields.

## 7. Security at execution boundaries

Every participating security-relevant object contributes its bound `SecurityID`/`SecurityObject` as defined in `MADRE-security-algebra.md`.

Security evaluation is transition-local rather than a global reduction over the entire carried object history.

For a candidate inference mechanism, the prospective transition identifies the concrete material disclosure edge(s) to the selected Capability and any other actual recipients on that path. The confidentiality predicate evaluates the material's Sensitivity against the minimum Privacy of that actual path.

For an effectful Operation, the prospective transition identifies the selected bound EffectProfile, actual residual controllers and effect executors. Control compares `control_risk` with controller Assurance; realization compares `effect_risk` with executor/profile Assurance. Autonomy orders feasible real profiles and does not enter either predicate.

Conceptually:

```text
immutable carried security history
    + prospective material/participant relationships
    + candidate Capability or EffectProfile
    -> SecurityTransition
    -> SecurityAlgebra
```

A candidate mechanism that is rejected before receiving material is not recorded as having received that material merely because it was considered.

Kernel handles bound security facts, transition structure supplied by public execution contracts, and material/binding integrity. It does not infer semantic truth, material classification or causal roles by reading prompt/output bytes.

## 8. Scheduling and resources

Kernel owns global deterministic scheduling/resource state.

The architecture requires support for:

- transient inference with latency requirements;
- immediately eligible and delayed durable work;
- originator/application fairness and priority;
- budgets/deadlines as execution metadata where implemented;
- GPU/CPU/RAM admission;
- model residency/device coordination where useful;
- cancellation and retry;
- restart recovery.

MADRE targets ordinary personal machines where inference resources may be scarce and already shared with the OS and other applications. Resource coordination must therefore prevent long background work from unnecessarily degrading interactive workloads.

The exact scheduling/optimization algorithm should evolve from measured workloads rather than from speculative scheduler features.

## 9. Result lifecycle

Successful physical output is transient at the Kernel boundary:

```text
Capability -> transient result -> originator consumes -> bytes discarded
```

Durable-work evidence may retain output digest, size, production time, attempt reference and delivery state.

A restart turns an unconsumed transient durable result into truthful result loss; Kernel does not gain hidden content durability.

Once delivered, output is Module-owned `Artifact` material and may be used according to Module semantics. When that result participates in a later governed transition it has its own material SecurityObject and explicit transition role.

## 10. Cancellation and failure evidence

Cancellation records whether accepted durable work was prevented or whether cancellation arrived during a running attempt.

Durable failure evidence should use stable runtime/adapter codes rather than persist arbitrary provider exception text containing private material.

## 11. Persistence invariant

Runtime persistence may contain public registry/mechanism metadata, work/attempt state, SecurityIDs/SecurityObjects and security-relevant transition/derivation evidence, opaque references/coordination values, digests, delivery state and evidence codes.

It must not contain prompt/context/output payload columns.


## V2 security execution contracts

Physical Capability descriptors bind computation Assurance separately from their explicit disclosure-boundary contracts. The route evaluates boundary PrivacyCapacity against the concrete material Sensitivity. `local`, `isolated` and `remote` are execution metadata, not implicit numeric valuations. Rejected mechanisms remain prospective evidence; only the selected path becomes realized history.

Generated results carry source/producer IDs and output Assurance constrained by the actual computation basis. Material transformations may establish a different resulting contract only through their bound execution path. Kernel verifies representation and completion continuity; Module implementations own semantic classification.

V2 immutable objects, transformations and accepted transitions survive restart/retry without being revalued from current registry metadata. Incompatible V1 storage is rejected; no migration or silent deletion occurs. Profile feasibility is inspection, not execution or an accepted crossing.


### Bound transformation and role interpretation

The current transform behavior contract is bounded material-to-material execution. It receives the concrete source, not general nested SDK services. Agents and Operations compose transformations through execution-bound clients, then pass completed representations as sources. A transform cannot advertise nested execution whose resulting dependencies its output contract cannot carry.

EffectProfile Assurance describes preservation of the immutable profile implementation's bound security-significant behavior. It participates as realization Assurance for that profile's own effect, and as controller Assurance only when that executing profile actually selects a downstream effect. Ownership does not supply either role or a replacement value.

Attachment disclosure boundaries denote the destination containment/exposure domain entered by delivery. Input enters the target domain; return enters the requester domain. They are not inferred bidirectional physical links. Any additional actual serial exposure requires its explicit boundary contract; handling bytes inside a domain does not create another operand.
