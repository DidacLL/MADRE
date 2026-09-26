# MADRE — Lane C Owner Decision: DRE and Physical Inference Architecture

Status: **accepted current Owner decision**.

This document preserves the product decision and the reasons behind it so later implementation work cannot recover the wrong Lane C architecture from historical code or from an intermediate correction.

It is not an implementation plan and does not make .NET, Microsoft.Extensions.AI or Microsoft Agent Framework part of MADRE's semantic identity. It records the accepted ownership model that implementations must serve.

Where an older repository document says that semantic MADRE must completely select an exact provider/model/configuration or a complete `ConcretePhysicalInvocation` candidate set before Kernel, this decision supersedes that statement. The concrete-invocation architecture was a useful corrective implementation after the earlier worker/engine drift, but it over-corrected the semantic/physical boundary and is no longer the target architecture.

## Why this correction exists

Lane C has exposed two opposite failure modes.

The first implementation made a possible physical realization into MADRE architecture:

```text
Kernel may use workers
        ↓
Kernel owns workers
        ↓
all engines become workers
        ↓
Kernel owns model/process lifecycle
        ↓
Kernel owns warmness and RAM/VRAM assumptions
        ↓
MADRE directly integrates llama.cpp
```

That was rejected because MADRE should use the Owner's inference environment rather than rematerialize it as MADRE-owned inference engines.

The corrective LCR1/LCR2 architecture then deliberately narrowed Kernel to durable execution of already-approved concrete process/HTTP invocations. That successfully removed workers, engine inventory, model lifecycle and llama.cpp ownership, but it moved physical inference selection so far above Kernel that Kernel could no longer perform the inference-aware scheduling that Delayed Reasoning Effort requires.

The accepted architecture keeps the ownership correction without amputating DRE.

## Fixed semantic/physical responsibility boundary

Semantic MADRE owns application and reasoning meaning.

A Module/Agent can know facts such as:

- what reasoning is required;
- what Material actually participates;
- the context available and its relevant characteristics;
- desired reasoning depth/effort;
- urgency and deadline;
- acceptable delay;
- required result characteristics;
- constraints derived from the actual SPIRA composition;
- explicit Owner requirements/preferences.

Semantic MADRE reduces those facts into a **physical inference requirement**. The semantic `ReasoningRequest` itself does not cross into Kernel.

Kernel receives physical inference Work and owns the physical inference consequences:

- durable Work identity and lifecycle;
- configured `InferenceCapability` knowledge;
- current capability state;
- physical observations/history;
- inference-aware scheduling;
- capability selection among physically/semantically admissible options expressed by the request constraints;
- physical effort and strategy selection;
- delay/defer/resume decisions;
- physically justified retry/escalation;
- execution and result lifecycle.

The naming of semantic preparation as part of "DRE" or as preparation for Kernel DRE is not an architectural question. The responsibility boundary above is the invariant. There is no semantic scheduler and no Kernel ownership of Module/Agent continuation.

## DRE is the inference-aware physical scheduler

Kernel DRE is not a generic timer service plus an unrelated policy layer.

The important scheduling decisions are inference-aware:

```text
run now or later?
which available capability is appropriate?
should the Kernel wait for a better capability?
how much physical reasoning effort should be spent?
one inference call or a richer physical strategy?
retry after which physical outcomes?
escalate to another admissible capability?
resume a checkpointed physical strategy now?
```

Durable mechanics such as `eligibleAt`, deadlines, leases, transactions and restart recovery exist underneath those decisions. They do not replace DRE.

There is no requirement for Kernel to schedule MADRE semantic Workflows, WorkPlans or Module application logic. A Module can simply have an outstanding reasoning request while its semantic continuation remains its own responsibility.

## PhysicalInferenceWork

The exact public contract remains design work, but its role is fixed.

Conceptually it carries only the physical consequences derived from semantic reasoning, for example:

```text
prepared inference input
requested result characteristics
reasoning effort/depth indication
urgency
acceptable delay / deadline
required modality/context characteristics
hard physical restrictions derived above
explicit Owner execution preferences where relevant
```

It must not carry Module semantics, Agent semantics, MADRE Workflow/WorkPlan semantics, Operation semantics, raw SPIRA as a Kernel policy object, or semantic continuation.

The first version must remain small: a field belongs in this contract only when an implemented DRE decision consumes it.

## InferenceCapability is a central Kernel concept

Kernel reasons about configured **InferenceCapabilities**, not provider brands, models, workers or framework classes.

A capability is a physical path through which MADRE can obtain intelligence. The same underlying model exposed with materially different configurations can be several capabilities. Different provider implementations can also be equivalent for a particular physical requirement.

Capability information must preserve provenance instead of mutating one supposedly authoritative property record.

At minimum the architecture distinguishes:

```text
InferenceCapability
    identity
    execution binding

CONFIGURED / DECLARED
    locality / external boundary
    modalities
    context characteristics
    declared inference/reasoning features
    price/cost characteristics where meaningful
    Owner configuration and preferences

CURRENT
    reachability / availability
    transient degradation or limits
    observable pressure where meaningful

HISTORICAL / OBSERVED
    latency
    throughput
    failures / error rates
    availability history
    malformed/incomplete-result rates
    observed costs
    benchmark/evaluation evidence with provenance where supplied
```

A provider claim, an Owner declaration and a MADRE observation are different facts. For example, "provider declares 1M context" is not the same as "MADRE has successfully observed 780k".

Only properties with an actual DRE consumer should become first-class in the first version. The architecture must leave room for additional typed/traceable observations without freezing an exhaustive ontology.

## Kernel observation is physical evidence

Kernel can become better informed by its own physical inference history. This is part of the value of DRE, not incidental telemetry.

Kernel may observe and use facts such as latency, availability, failure rate, throughput, observed cost and structurally valid/invalid outcomes. It may also retain external benchmark/evaluation evidence with provenance when a DRE strategy explicitly understands that evidence.

Kernel does not silently become a semantic answer judge.

A physically successful result is not retried merely because Kernel believes the answer is weak or does not solve the user's application problem. That judgment belongs to the Agent/Module consuming the result.

The retry/continuation distinction is:

```text
executor retry
    transient transport/process/executor failure

DRE retry / escalation
    physical inference result is unusable/incomplete
    or an explicitly physical strategy condition requires continuation

semantic retry
    valid physical result does not satisfy the application reasoning need
    -> Agent/Module logic
```

## Physical multi-stage reasoning is legitimate

DRE may select richer physical inference strategies when they are useful, for example:

```text
capability A
    ↓
physical/structural validator
    ↓
capability B
```

or fan-out/comparison/synthesis across several admissible inference capabilities.

This does not create a MADRE semantic Workflow. A physical inference strategy remains below the semantic boundary.

Kernel must not use physical workflow machinery to silently acquire application effects such as changing Module state, sending application email, committing project state or executing another Module's semantic Operation. Meaning and ownership determine the boundary, not whether a technology happens to be callable from a framework.

Opaque external inference systems are valid. Kernel need only model what can honestly be known at their boundary.

## Open-source physical construction surface

The leading implementation candidature is a cross-platform .NET Kernel using open-source infrastructure where it removes generic physical AI engineering.

### Microsoft.Extensions.AI

MEAI is a strong low-level interoperability candidate for inference clients and related generic AI transport abstractions.

`InferenceCapability` is not `IChatClient`. MEAI is an implementation mechanism behind a capability binding.

### Microsoft Agent Framework

MAF is retained primarily as an **optimistic physical construction toolbox**:

- provider/runtime integrations;
- physical workflow graphs;
- fan-out/fan-in and executor sequencing;
- checkpoint/resume machinery;
- custom physical implementations;
- other reusable physical AI infrastructure where useful.

MAF does not define MADRE semantics.

```text
MADRE Agent        != MAF AIAgent
MADRE Workflow     != MAF Workflow
InferenceCapability != IChatClient
InferenceCapability != MAF AIAgent
InferenceCapability != MAF Workflow
DRE                 != MAF
```

A MAF Workflow may execute a physical strategy selected by DRE. DRE remains the MADRE inference-aware scheduler and strategy selector.

MAF is optional per strategy: simple inference must not be forced through a graph merely because a graph engine exists.

## Open physical bindings

MADRE must not need to predict every future inference environment.

The physical construction surface should support common integrations and preserve an intentionally open binding seam. Useful realizations can include:

```text
MEAI/provider integration
OpenAI-compatible endpoint
HTTP/protocol binding
process/script binding
A2A/remote intelligence
Owner-provided .NET/service/executable wrapper
future mechanism
```

Hot loading, a connector marketplace, automatic NuGet discovery and an elaborate defensive plugin sandbox are not first-version requirements.

The practical requirement is:

> Adding an unusual Owner-controlled inference environment should normally require configuration or installing/writing a binding, not modifying Kernel architecture or provider-specific core source.

The Owner is trusted and may deliberately configure bindings that use local executables, credentials, private networks or external endpoints.

## First-party parity rule

MADRE-provided inference conveniences must use the same class of physical construction surface available to advanced Owners.

There must be no privileged architectural path such as:

```text
if llama.cpp -> special Kernel ontology
if Ollama    -> hidden privileged lifecycle
if first-party adapter -> secret internal API
```

A provided adapter may exist, including local-runtime convenience tooling, but removing that adapter must leave Kernel architecture coherent.

This is the physical equivalent of first-party Modules using the same public MADRE SDK as Owner-created Modules.

## Durability

MADRE keeps one authoritative `PhysicalInferenceWork` lifecycle in its own durable state, initially SQLite unless a real need justifies replacement.

Conceptually:

```text
PhysicalInferenceWork
    id / state
    eligibleAt / deadline / urgency
    physical requirement / constraints
    strategy identity + version/state
    attempts / selected capabilities
    checkpoint reference where applicable
    lease/recovery state
    result state
```

Framework checkpoint state is subordinate execution state. A MAF checkpoint does not independently decide whether MADRE Work exists, is cancelled, is terminal or should resume.

`strategyType`/`strategyVersion` or equivalent durable identity must make incompatible continuation detectable rather than silently restoring old state into a changed implementation.

## Generic scheduler/workflow platforms

Quartz, Wolverine, Elsa, Temporal and similar systems are not currently part of the accepted Kernel architecture.

They either solve only the generic wake/delivery part while DRE still owns the meaningful inference-aware decision, or they introduce a broader durable workflow architecture and additional state authority than the single-owner local Kernel currently needs.

This is not a permanent ban. They may be reconsidered if concrete requirements make them simpler than the small MADRE-owned durable substrate.

## .NET candidature

.NET is the leading implementation candidate because the corrected Kernel workload is now primarily:

- asynchronous inference integration;
- structured configuration;
- capability knowledge and observations;
- networking;
- durable Work;
- inference-aware scheduling;
- physical workflow execution/checkpointing;
- Owner-extensible physical bindings.

The old native-worker motivations—model lifecycle, warm workers, RAM/VRAM ownership and in-Kernel native inference—have been rejected.

C++ is therefore not retained merely because the current reference implementation exists.

## What survives from current Lane C

The existing C++ LCR1–LCR3 implementation remains valuable evidence/test material for physical behavior, including:

- independent Kernel lifetime;
- durable physical Work identity;
- caller-disappearance semantics;
- SQLite/local persistence;
- eligibility/deadlines;
- cancellation;
- attempt history;
- conservative `UNKNOWN_COMPLETION` after interrupted active attempts;
- result acknowledgement/release lifecycle;
- local IPC and Windows/Linux behavioral requirements;
- generic process/HTTP execution as useful low-level escape hatches.

Those behaviors survive because MADRE needs them, not because the current C++ abstractions are authoritative.

The following must not survive by implementation inertia:

- `WorkerPool` as universal inference abstraction;
- engine/worker inventory as Kernel ontology;
- warm-worker lifecycle;
- model load/unload ownership;
- Kernel-owned provider/model processes;
- mandatory resource reservations for external engines;
- privileged llama.cpp integration;
- the exact-invocation-before-Kernel rule.

## Current implementation versus target architecture

The active corrective branch still contains the C++ concrete-invocation Kernel. It is now a **reference implementation/test oracle**, not the target Lane C architecture.

Do not continue hardening it merely because it exists unless a fix is required to preserve evidence needed by the replacement.

The target architecture must first validate the new center:

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
    observations/evidence
        ↓
physical binding/strategy
        ↓
inference execution
        ↓
physical observations persisted
        ↓
future DRE may use that evidence
```

A later validation should exercise one genuinely multi-stage physical MAF workflow with checkpoint/restart/resume while MADRE Work remains authoritative.

## Anti-drift test

A Lane C design is drifting if it does any of the following:

- makes a provider/model/runtime into MADRE semantic identity;
- converts every inference engine into a MADRE-owned worker;
- resolves all meaningful capability/effort choice before Kernel so DRE cannot schedule inference;
- sends Module/Agent/Workflow/Operation semantics or raw SPIRA policy into Kernel;
- lets Kernel judge application-level answer quality;
- turns MAF/MEAI classes into MADRE ontology;
- makes first-party adapters more privileged than Owner adapters;
- introduces a generic scheduler/workflow platform without removing more complexity than it adds;
- freezes speculative capability properties with no DRE consumer;
- treats the current C++ implementation as authority over the accepted product decision.

The target remains: **MADRE owns the physical concepts that make DRE useful while reusing generic inference infrastructure and leaving the Owner's actual intelligence environment independent.**
