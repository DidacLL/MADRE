# MADRE Kernel — current physical inference architecture

Status: **accepted architecture and current Lane C implementation**.

This document describes the current Lane C physical inference architecture after the Owner correction preserved in `docs/product/lane-c-owner-decision.md`.

The active tree implements that architecture as the capability-aware .NET Kernel under `kernel/`. The superseded C++ LCR1–LCR3 concrete-invocation Kernel is deleted; its useful causal and behavioral evidence is available only in Git history.

For whole-system semantics and the semantic/physical boundary, see `docs/architecture/mid-level-architecture.md`. For SPIRA, see `docs/architecture/security-algebra.md`.

## Purpose

Kernel exists so MADRE can physically realize reasoning needs over time against the Owner's available intelligence without turning those inference systems into MADRE-owned engines/workers.

The governing invariant is:

> **Kernel owns durable physical inference scheduling, capability knowledge and execution. It does not own application semantics or the internal architecture/lifecycle of the Owner's inference systems.**

This rejects both historical extremes:

```text
REJECTED ORIGINAL DRIFT
Kernel owns engines/workers/model lifecycle

REJECTED OVER-CORRECTION
semantic side fully selects exact provider/model/configuration/invocation
before Kernel, leaving DRE unable to make meaningful physical choices
```

## Semantic / physical boundary

```text
SEMANTIC MADRE

Module / Agent owns application meaning and continuation
        ↓
Agent creates ReasoningRequest
        ↓
semantic MADRE derives physical inference requirement
    prepared input
    requested result characteristics
    desired reasoning depth/effort
    urgency
    acceptable delay/deadline
    relevant modality/context characteristics
    hard physical restrictions derived above
    explicit Owner execution preferences where relevant
        ↓
madre-kernel-client / physical boundary

================ HARD BOUNDARY ================

KERNEL

PhysicalInferenceWork
        ↓
DRE + InferenceCapability knowledge/state/observations
        ↓
physical strategy / capability / timing
        ↓
physical binding(s)
        ↓
Owner-selected independent inference environment
```

The current Lane C `/v1` request carries only the implemented physical facts consumed today: prepared input, requested physical effort, urgency, optional eligibility/deadline and the hard local/external execution boundary. The future semantic SDK/Runtime carrier above that boundary remains design work and must stay small.

The Kernel must not receive semantic objects merely because their information influenced the physical request.

It must remain ignorant of:

```text
Module semantics
MADRE Agent semantics
Operation semantics
Material meaning
ReasoningRequest semantics
SPIRA as semantic objects/policy
CORE semantics
MADRE Skill / Workflow / WorkPlan semantics
semantic continuation
semantic persistence
```

Kernel may receive physical restrictions derived from those semantics. It does not re-evaluate or reinterpret the semantic construction that produced them.

## PhysicalInferenceWork

`PhysicalInferenceWork` is the durable Kernel-side unit representing one physical inference obligation.

It carries only enough state for current DRE, recovery and result lifecycle:

```text
identity / lifecycle state
prepared physical input + hard constraints
requested effort / urgency
eligibleAt / deadline
strategy identity + version/state where relevant
attempt history / selected capability and binding
checkpoint reference where applicable
result state / release state
```

This is not a universal workflow object and not a semantic WorkPlan.

The authoritative Work state belongs to MADRE. Framework-specific state is subordinate.

## InferenceCapability

An `InferenceCapability` is a configured physical path through which MADRE can obtain intelligence.

It is not:

- a Module;
- a MADRE Agent;
- a provider/model ontology;
- an engine/worker owned by Kernel;
- an `IChatClient` alias;
- a MAF `AIAgent` alias;
- a MAF Workflow.

The same underlying model exposed with materially different configurations can be several capabilities. Conversely, several physical implementations may be interchangeable for a particular physical requirement.

Capability identity therefore belongs to the configured inference path as experienced by MADRE, not merely to a model/provider name.

### Configured / declared facts

Examples include:

```text
locality / external exposure boundary
input/output modalities
context characteristics / limits
declared reasoning/effort features
cost/price characteristics where meaningful
Owner configuration/preferences
execution binding
```

Only properties with a real DRE consumer should be first-class in the initial model.

The current typed core uses the configured execution boundary, supported effort, Owner preference and binding identity/version because the implemented DRE and recovery paths consume them.

### Current state

Examples include:

```text
reachable / unavailable
transient degradation
observable rate/capacity limits
observable pressure / queue state where exposed
```

Current state is not the same as configuration or historical observation.

In the current implementation, normal startup and explicit refresh obtain availability through the binding's physical probe. Configuration alone does not fabricate `Available`.

### Historical / observed evidence

Kernel can accumulate factual physical evidence such as:

```text
latency distributions
throughput
failure/error rate
availability history
malformed/incomplete-result rate
observed cost
successful/failed context sizes where meaningful
benchmark/evaluation evidence with provenance where supplied
```

Provenance must be preserved.

```text
provider declares 1M context
Owner says capability is especially useful
MADRE has successfully observed 780k
```

are three different facts.

Do not collapse configured expectation, current state and historical observation into one mutable "truth" field.

The current implementation persists successful latency and success/failure observation counts because the first DRE rules consume that evidence. It does not freeze a speculative exhaustive capability ontology.

## DRE — inference-aware physical scheduler

DRE is the intelligence that decides what physical inference should happen next for eligible Work.

Its questions include:

```text
run now or later?
which admissible capability is currently appropriate?
should current availability/observations justify waiting?
how much physical reasoning effort should be spent?
one inference attempt or a richer physical strategy?
should another capability be used after a physically invalid/unusable outcome?
should several capabilities run in parallel or sequence?
should a checkpointed physical strategy resume now?
```

This is why Kernel capability knowledge and observations are first-class. DRE can learn from the Owner's actual inference environment without owning those engines.

`eligibleAt`, deadlines, SQLite transactions and restart recovery are persistence/admission mechanics underneath DRE. A generic timer service does not replace the inference-aware scheduler.

The current scheduler deliberately remains small: it scans the currently eligible local Work set in urgency/creation order and asks DRE about each Work. Waiting Work whose admissible capability is currently unavailable remains queued, but cannot hide later runnable Work behind a fixed head page.

There is no Kernel scheduler for Module semantic Workflows or WorkPlans.

## Physical observation is not semantic judgment

Kernel may judge physical validity/observability, not application success.

Examples of physically actionable outcomes include:

```text
transport/executor failure
timeout / interruption
capability unavailable
malformed/corrupt/truncated response
required structural output missing
required modality missing
physical validator defined by the strategy failed
```

A physically valid result is not retried merely because Kernel believes:

```text
the answer is weak
the reasoning is unconvincing
the user's actual problem was not solved
```

Those are semantic judgments for the Agent/Module consuming the result.

The retry layers are deliberately distinct:

```text
executor retry
    transient physical/executor failure

DRE retry / escalation
    physically unusable/incomplete result
    or another explicit physical strategy condition

semantic retry
    physically valid result does not satisfy application reasoning need
    -> Agent/Module logic
```

External benchmark/evaluation evidence may be stored and used with provenance when a DRE strategy explicitly knows how to interpret it. Kernel does not infer universal semantic truth from arbitrary model output.

## Physical multi-stage strategies

DRE may choose a simple strategy:

```text
invoke capability A
```

or a richer physical strategy:

```text
        capability A
       /            \
input                 synthesis
       \            /
        capability B
```

or:

```text
inference A
    ↓
physical/structural validator
    ↓
inference B
```

These are physical inference strategies, not MADRE semantic Workflows.

Physical workflow machinery must not silently acquire application effects such as:

```text
modify Module/domain state
send application email
change project state
commit code on behalf of a Module
execute another Module's semantic Operation
```

Meaning and ownership determine the boundary. Technology does not.

Opaque external intelligence is acceptable. Kernel only models what can honestly be known at the capability boundary.

## Physical construction surface

Kernel reuses generic open-source inference infrastructure where useful rather than rebuilding another AI platform.

The current implementation is cross-platform .NET.

### Microsoft.Extensions.AI

MEAI is useful as generic inference interoperability where appropriate.

It may back a capability binding, but:

```text
InferenceCapability != IChatClient
```

MADRE retains capability identity, configuration, observations and DRE semantics.

### Microsoft Agent Framework

MAF is useful as an optimistic physical construction toolbox where concrete strategies need its machinery:

- provider/runtime integrations;
- physical workflow graphs;
- fan-out/fan-in;
- executor sequencing;
- checkpoint/resume;
- custom physical implementations;
- protocol/agent-runtime integration where useful.

It does not define MADRE ontology:

```text
MADRE Agent         != MAF AIAgent
MADRE Workflow      != MAF Workflow
InferenceCapability != MAF AIAgent
InferenceCapability != MAF Workflow
DRE                  != MAF
```

DRE selects timing, capabilities and physical strategy. The current richer strategy uses a MAF Workflow for one concrete two-stage physical graph with a durable checkpoint between stages.

Simple inference bypasses MAF completely.

Framework-specific execution semantics do not become universal DRE semantics. A future physical strategy that does not map cleanly to MAF remains free to execute another way through the same MADRE physical ownership boundary.

## Open physical binding seam

MADRE must be able to use inference ecosystems that do not have a first-party MEAI/MAF integration.

Useful physical realization paths may include:

```text
MEAI/provider integration
OpenAI-compatible endpoint
generic HTTP/protocol binding
process/script binding
A2A/remote intelligence
Owner-provided .NET binding
Owner-provided service/executable wrapper
future typed mechanism
```

The current ordinary binding API is `IInferenceBinding`: binding identity/version, a truthful availability probe and physical execution of the current request. Normal Owner JSON configuration instantiates the provided shell-free process/executable binding; MEAI and Owner/custom bindings use the same seam where useful.

The product requirement is openness, not a marketplace:

> An unusual Owner-controlled inference environment should normally be usable through configuration or an installed/custom binding rather than provider-specific changes to Kernel architecture.

Hot-loading, automatic NuGet discovery, a connector marketplace and a defensive plugin sandbox are not first-version requirements.

The Owner is trusted and may deliberately configure bindings that invoke local executables, use credentials, connect to private networks or call external endpoints.

## First-party parity

MADRE-provided inference conveniences must use the same class of physical construction surface available to advanced Owners.

There must be no architectural special cases such as:

```text
if llama.cpp -> privileged Kernel path
if Ollama    -> hidden Kernel lifecycle
if first-party adapter -> secret internal API
```

A provided local-runtime convenience is legitimate if removing it tomorrow leaves Kernel architecture intact.

This is the physical equivalent of first-party Modules using the same public SDK as independent Modules.

## Durable authority and checkpointing

MADRE keeps one authoritative `PhysicalInferenceWork` lifecycle in its own durable state.

SQLite is the current authoritative persistence for the single-owner local Kernel.

For the concrete MAF strategy, the relation is:

```text
MADRE PhysicalInferenceWork
    authoritative identity/lifecycle/eligibility/cancel/terminal state
        ↓
strategy type + version
checkpoint reference
        ↓
MAF checkpoint
    subordinate execution state for that strategy
```

A framework checkpoint cannot independently decide whether a MADRE Work exists, is cancelled, terminal or should resume.

The current Work state persists strategy and binding identity/version so incompatible continuation is rejected rather than silently restoring old state into changed code.

When a terminal Work is explicitly released, its retained SQLite input/result are cleared and any subordinate per-Work MAF checkpoint directory is removed. Work identity, terminal state and attempt history remain inspectable. Release remains harmless when no checkpoint exists and idempotent when repeated. Active or checkpointed resumable Work is not releasable.

## Scheduling persistence

The current implementation does not use Quartz, Wolverine, Elsa, Temporal or another generic durable scheduler/workflow platform.

DRE still has to make the meaningful inference-aware decisions even if one of those frameworks could store wakeups or messages. Introducing another durable state model is justified only if concrete implementation evidence shows it removes more complexity than it creates.

A small SQLite-backed Work store and Kernel wake/admission loop are sufficient for current Lane C. This is not an attempt to implement a generic scheduler; it is the minimum durable substrate beneath MADRE's own inference-aware DRE scheduler.

## Honest completion and restart recovery

The current implementation preserves the truthfulness learned from the historical C++ work.

If Kernel loses certainty about an active attempt or external operation, it does not rewrite uncertainty into definite failure merely to simplify recovery.

The current state model uses `UnknownCompletion`:

```text
definite observed failure != lost certainty about completion
```

An interrupted uncertain physical attempt is not implicitly repeated.

Cancellation likewise cannot claim confirmed remote cancellation when Kernel only stopped observing locally. The provided local process binding reports confirmed cancellation only when it has physically terminated its process tree; uncertain bindings can report unknown completion instead.

## Result/payload lifecycle

Physical inference Work may need to retain prepared request material and physical results while Runtime/Module processes are absent.

Current Lane C retains those bounded payloads durably until explicit terminal release. Release clears retained SQLite request/result payload and, for completed checkpointed MAF Work, deletes subordinate per-Work checkpoint state while preserving Work identity/history.

No secure-erasure guarantee is implied merely by ordinary file/database deletion.

Secrets/credentials should remain late-bound or otherwise deliberately handled rather than accidentally becoming durable Work payload when a binding can avoid it.

## Local control plane

Kernel has an independent lifetime from Runtime/Modules.

The current physical control plane is loopback HTTP bound to `127.0.0.1`, versioned under `/v1`.

It preserves the required behavior:

- local-only control-plane operation for the default installation;
- one stalled local caller does not freeze unrelated clients;
- bounded request/result payload handling;
- independent client and Kernel lifetimes;
- Windows and Linux support;
- a versioned physical contract used by the current Java `madre-kernel-client`.

There is no current UDS/named-pipe/protocol-v4 compatibility layer. Those belonged to the deleted historical native implementation.

## Historical C++ implementation evidence

The deleted LCR1–LCR3 C++ physical substrate established useful behavioral evidence including:

- independent Kernel lifetime;
- SQLite durable Work;
- eligibility/deadlines;
- caller disappearance;
- cancellation;
- attempt history;
- conservative `UNKNOWN_COMPLETION` restart semantics;
- bounded payload/result handling;
- explicit terminal payload/result release;
- local-only Windows/Linux behavior.

Those behaviors were revalidated against the current implementation where they remain applicable. The historical source and protocol shapes are available in Git history only.

The following are **not** current authority merely because historical code implemented them:

- exact `ConcretePhysicalInvocation` selection before Kernel;
- candidate routing constrained to a semantic-side preselected set as the final DRE architecture;
- C++ as required implementation language;
- process/HTTP as exhaustive inference ontology;
- protocol-v4, UDS or named-pipe shapes as current public architecture.

The even older worker/engine architecture remains rejected:

- `WorkerPool` as universal inference abstraction;
- engine inventory/matching as Kernel ontology;
- warm workers/model residency;
- Kernel-owned provider/model process lifecycle;
- model loading/unloading;
- generic external-engine RAM/VRAM reservation;
- privileged llama.cpp worker architecture.

## Current Lane C implementation

```text
MADRE Kernel — .NET under kernel/

MADRE-owned
    PhysicalInferenceWork
    InferenceCapability
    configured/current/observed capability truth
    DRE
    physical attempts/results/recovery
    authoritative SQLite durable state

Reusable physical infrastructure
    Microsoft.Extensions.AI where useful
    selective Microsoft Agent Framework workflow/checkpointing
    process/executable binding
    Owner/custom IInferenceBinding implementations

Control boundary
    local loopback HTTP /v1
    Java madre-kernel-client

External
    Owner-selected inference systems
```

The first-version DRE currently enforces eligibility/deadlines and hard local/external and effort admissibility, requires current availability, uses Owner preference as the normal stable choice, uses persisted successful latency evidence for `Interactive` choice when candidates have comparable evidence, runs simple inference directly and selects the checkpointed two-stage MAF strategy for `High + Background` Work.

No provider/model brand receives special routing or lifecycle ownership.

## Acceptance evidence

The replacement validation has happened and is part of normal CI on Linux and Windows.

Current acceptance proves:

1. configured/current/observed capability facts remain distinct and current availability comes from physical evidence;
2. DRE respects hard restrictions, Owner preference and persisted latency evidence;
3. eligible/deadline/urgency behavior, bounded concurrency and concurrent clients;
4. queued and running cancellation truth plus explicit release;
5. the external Java client submits, inspects, cancels, collects and releases physical Work across an independent process boundary;
6. queued Work survives Kernel restart and interrupted active Work recovers as `UnknownCompletion` without implicit duplicate inference;
7. Owner/custom bindings use the same ordinary capability/DRE path;
8. the concrete two-stage MAF strategy checkpoints, survives hard Kernel death and resumes without replaying stage A;
9. checkpoint deadline/cancellation authority and strategy/binding incompatibility rejection;
10. simple inference bypasses MAF;
11. more than 64 earlier waiting Works cannot starve a later runnable Work;
12. explicit release of completed checkpointed Work removes subordinate MAF filesystem state while preserving Work identity/history;
13. contamination/destructive-convergence checks reject native/reference implementation return, semantic SDK leakage, worker/engine ontology, protocol-v4 fossils and validation-only production hooks.

## Anti-drift checks

A Kernel design is drifting if it:

- converts provider/model/runtime implementations into MADRE-owned workers/engines;
- requires all meaningful provider/model/capability choice to happen before Kernel;
- imports Module/Agent/Workflow/Operation semantics into physical Work;
- treats raw SPIRA as a Kernel policy engine;
- judges application-level answer quality as a hidden semantic Agent;
- equates `InferenceCapability` with a MEAI/MAF class;
- lets MAF define DRE rather than execute selected physical strategies;
- privileges provided adapters over Owner adapters;
- creates a connector marketplace/plugin framework before a real need;
- adds capability fields with no DRE consumer;
- introduces a generic scheduler/workflow platform without simplifying the actual DRE state problem;
- treats deleted C++/protocol-v4 historical implementation details as product authority over later Owner intent.

The target is narrow in ownership, not narrow in useful physical inference capability:

> **MADRE owns DRE, durable physical Work, capability knowledge and observations; external systems own their inference internals; reusable libraries provide generic physical machinery.**
