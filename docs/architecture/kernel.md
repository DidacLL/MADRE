# MADRE Kernel — target physical inference architecture

Status: **accepted target architecture; replacement implementation not yet complete**.

This document describes the target Lane C physical inference architecture after the Owner correction preserved in `docs/product/lane-c-owner-decision.md`.

The active branch still contains the C++ LCR1–LCR3 concrete-invocation Kernel. That code is retained as a reference implementation/test oracle for useful physical behavior, but its exact-invocation-before-Kernel boundary is no longer the target architecture.

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

The exact request type remains design work. The first version must stay small: each field must have an implemented DRE consumer.

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

Conceptually it needs only enough state for DRE, recovery and result lifecycle:

```text
identity / lifecycle state
physical inference requirement + hard constraints
urgency
eligibleAt / acceptable delay / deadline
strategy identity + version/state where relevant
attempt history / selected capabilities
checkpoint reference where applicable
lease/recovery state
result state
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

### Current state

Examples include:

```text
reachable / unavailable
transient degradation
observable rate/capacity limits
observable pressure / queue state where exposed
```

Current state is not the same as configuration or historical observation.

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

`eligibleAt`, deadlines, leases, SQLite transactions and restart recovery are persistence/admission mechanics underneath DRE. A generic timer service does not replace the inference-aware scheduler.

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

Kernel should reuse generic open-source inference infrastructure rather than rebuilding another AI platform.

The leading implementation candidature is a cross-platform .NET Kernel.

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

DRE selects timing, capabilities and physical strategy. A MAF Workflow may execute the chosen graph.

Simple inference must not be forced through a MAF Workflow merely because MAF exists.

Framework-specific execution semantics must not become universal DRE semantics. If a future physical strategy does not map cleanly to MAF, Kernel remains free to execute it another way.

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

The exact common binding API/protocol remains design work.

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

SQLite is the leading initial persistence choice for the single-owner local Kernel.

If a physical strategy uses MAF checkpointing, the relation is:

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

Persist enough strategy/binding version identity to detect incompatible continuation after upgrades rather than silently restoring old state into changed code.

## Scheduling persistence

The current target does not assume Quartz, Wolverine, Elsa, Temporal or another generic durable scheduler/workflow platform.

DRE still has to make the meaningful inference-aware decisions even if one of those frameworks stores wakeups or messages. Introducing another durable state model is justified only if concrete implementation evidence shows it removes more complexity than it creates.

A small SQLite-backed Work store, atomic leasing and a Kernel wake/admission loop are currently sufficient assumptions.

This is not an attempt to implement a generic scheduler. It is the minimum durable substrate beneath MADRE's own inference-aware DRE scheduler.

## Honest completion and restart recovery

The replacement must preserve the truthfulness learned from the C++ reference implementation.

If Kernel loses certainty about an active attempt or external operation, it must not rewrite uncertainty into definite failure merely to simplify recovery.

The exact state model may evolve, but the `UNKNOWN_COMPLETION` lesson survives:

```text
definite observed failure != lost certainty about completion
```

Automatic repetition after uncertain completion must depend on the physical strategy/binding's explicit semantics rather than an optimistic global default.

Cancellation likewise cannot claim confirmed remote cancellation when Kernel only stopped observing locally.

## Result/payload lifecycle

Physical inference Work may need to retain prepared request material and physical results while Runtime/Module processes are absent.

The replacement must preserve a bounded, inspectable lifecycle for retained physical data and explicit release/acknowledgement semantics where needed.

No secure-erasure guarantee is implied merely by ordinary file/database deletion.

Secrets/credentials should remain late-bound or otherwise deliberately handled rather than accidentally becoming durable Work payload when a binding can avoid it.

## Local control plane

Kernel has an independent lifetime from Runtime/Modules.

The exact replacement transport remains design work. The C++ reference proves useful requirements:

- local-only control-plane operation for the default installation;
- one stalled local caller must not freeze unrelated clients;
- bounded framing/requests;
- Windows and Linux support;
- versioned physical contract where persistence/transport compatibility matters.

Do not preserve UDS/named-pipe/C++ details merely because the reference implementation uses them if the replacement can satisfy the same behavior more simply.

## Current C++ reference implementation

The active correction branch currently contains the completed LCR1–LCR3 C++ physical substrate.

Useful behavior established there includes:

- independent Kernel lifetime;
- SQLite durable Work;
- eligibility/deadlines;
- caller disappearance;
- cancellation;
- attempt history;
- conservative `UNKNOWN_COMPLETION` restart semantics;
- candidate-specific `ProcessInvocation` and generic `HttpInvocation` execution;
- late-bound HTTP credential references and bounded payload/result handling;
- explicit terminal payload/result release;
- isolated local IPC clients;
- Linux/Windows CI evidence.

Those are behavioral evidence/acceptance cases for replacement where still applicable.

The following are **not** target authority merely because the reference code implements them:

- exact `ConcretePhysicalInvocation` selection before Kernel;
- candidate routing constrained to a semantic-side preselected set as the final DRE architecture;
- C++ as required implementation language;
- process/HTTP as exhaustive inference ontology;
- protocol/schema shapes as future public architecture.

The even older worker/engine architecture remains rejected:

- `WorkerPool` as universal inference abstraction;
- engine inventory/matching as Kernel ontology;
- warm workers/model residency;
- Kernel-owned provider/model process lifecycle;
- model loading/unloading;
- generic external-engine RAM/VRAM reservation;
- privileged llama.cpp worker architecture.

## Leading replacement implementation

The current leading candidate is:

```text
MADRE Kernel — .NET

MADRE-owned
    PhysicalInferenceWork
    InferenceCapability
    configured/current/observed capability knowledge
    DRE
    physical attempts/results/recovery
    authoritative SQLite durable state

Reusable physical infrastructure
    Microsoft.Extensions.AI
    selective Microsoft Agent Framework workflow/checkpointing
    generic HTTP/protocol/process bindings
    Owner/custom bindings

External
    Owner-selected inference systems
```

.NET is favored because the corrected Kernel workload is primarily async integration, configuration, networking, durable state, observation, scheduling and optional physical workflow execution rather than native model lifecycle.

## Replacement validation sequence

Do not replace the entire Kernel in one framework-driven rewrite.

The first bounded vertical validation must prove:

```text
physical inference requirement
        ↓
PhysicalInferenceWork
        ↓
DRE
        ↓
InferenceCapability catalogue
    configured facts
    current state
    observations
        ↓
choose physical capability/action
        ↓
MEAI or generic/custom binding
        ↓
execute
        ↓
record latency/result/failure
        ↓
persist observation
        ↓
future DRE can consume that evidence
```

It should demonstrate at least:

1. one MEAI-backed capability;
2. one generic low-level capability path such as HTTP/process;
3. one Owner/custom unusual capability through the same capability/DRE path;
4. declared/current/observed facts remaining distinct;
5. durable Work surviving caller disappearance/restart;
6. truthful physical outcome and observation recording;
7. no provider/model semantic special case in DRE.

A second validation should demonstrate one genuinely multi-stage physical MAF strategy with checkpoint, Kernel restart and resume while MADRE Work remains authoritative.

Only after those prove the architecture should the reference C++ implementation be removed/replaced.

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
- treats C++/protocol-v4/reference tests as product authority over later Owner intent.

The target is narrow in ownership, not narrow in useful physical inference capability:

> **MADRE owns DRE, durable physical Work, capability knowledge and observations; external systems own their inference internals; reusable libraries provide generic physical machinery.**
