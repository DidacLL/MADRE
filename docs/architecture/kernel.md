# MADRE Kernel — current physical inference architecture

Status: **accepted architecture and current Lane C implementation after final engineering convergence**.

This document describes the active capability-aware .NET Kernel under `kernel/`. Historical C++, loopback web-host and MAF-validation implementations remain Git-history evidence only.

## Purpose and boundary

Kernel exists so MADRE can physically realize reasoning needs over time against the Owner's available intelligence without turning those inference systems into MADRE-owned engines/workers.

> **Kernel owns durable physical inference scheduling, capability truth and execution. It does not own application semantics or the internal lifecycle of the Owner's inference systems.**

```text
SEMANTIC MADRE
    Module / Agent owns meaning and continuation
        ↓
    semantic reasoning need
        ↓
    small physical inference requirement
        ↓
madre-kernel-client / physical boundary

================ HARD BOUNDARY ================

KERNEL
    PhysicalInferenceWork
        ↓
    DRE + InferenceCapability truth/observations
        ↓
    selected physical binding
        ↓
    Owner-selected inference environment
```

The current physical request contains only implemented facts consumed by DRE: prepared input, requested effort, urgency, optional eligibility/deadline and hard local/external execution boundary.

Kernel remains ignorant of Module, MADRE Agent, Operation, Material meaning, ReasoningRequest semantics, SPIRA policy, CORE, MADRE Skill/Workflow/WorkPlan semantics and semantic continuation/persistence.

## Physical Work

One authoritative durable Work lifecycle holds the physical request and execution state required for DRE/recovery:

```text
identity / lifecycle state
prepared input + hard physical constraints
requested effort / urgency
eligibleAt / deadline
selected capability/binding when dispatched
attempt history
result / failure / release state
```

There is no current production strategy/checkpoint field or checkpointed Work state. The earlier MAF checkpoint experiment is not part of the product implementation.

SQLite is the current authoritative local persistence. Work interrupted while actively executing recovers as `UnknownCompletion`; uncertain execution is not silently duplicated.

Terminal release clears retained request/result payload while preserving Work identity, terminal state and attempt history.

## InferenceCapability truth

An `InferenceCapability` is a configured physical path through which MADRE can obtain intelligence. It is not a Module, semantic Agent, provider/model ontology, MADRE-owned engine/worker, `IChatClient`, or workflow object.

The current typed core separates:

```text
CONFIGURED / DECLARED
    capability identity
    binding identity/version
    execution boundary
    supported effort
    Owner preference

CURRENT
    Unknown / Unavailable / Available
    observed-at time

HISTORICAL / OBSERVED
    attempt outcomes
    successful latency evidence
    failure evidence
```

These are distinct truth domains. Configuration does not fabricate availability. Current state is re-observed. Historical attempt evidence remains historical even when a capability is later removed from configuration.

At startup the persisted configured catalogue is reconciled to the actual current configuration. Removed capability rows/current state therefore cannot survive as selectable stale configuration. Attempt rows are not deleted by that reconciliation.

### Unknown and Unavailable

`Unknown` means Kernel lacks current availability evidence. It does not mean the capability is unusable.

DRE selection rule:

1. filter by hard effort and execution-boundary admissibility;
2. prefer known-available candidates;
3. if none are known available, an admissible configured `Unknown` candidate may be tried;
4. if all admissible candidates are known unavailable, Work waits for re-observation.

Successful or otherwise physically informative execution updates current availability evidence. Known-unavailable capabilities are automatically re-observed on a Kernel-owned schedule. Recovery therefore does not rely on a caller manually invoking refresh forever.

Capability probes run asynchronously after Kernel startup. Slow/broken probes cannot prevent an otherwise valid empty or configured Kernel from becoming available.

## DRE scheduling

DRE remains the inference-aware physical scheduler rather than a generic timer plus dumb executor.

Current decisions consume:

- eligibility/deadlines;
- explicit effort admissibility;
- hard local/external admissibility;
- current capability availability;
- Owner preference;
- persisted successful latency evidence for interactive choice when candidates have comparable evidence.

Effort support and urgency priority are explicit domain functions. Enum ordinals do not carry domain semantics. SQL persists/query facts; it does not embed urgency policy.

The scheduler is wake/deadline driven. Submission, cancellation, attempt completion and capability observations wake it. The next future Work eligibility/deadline or capability re-observation supplies the next timed wake. There is no fixed busy-poll interval and no SQLite `LIMIT -1` sentinel leaking into scheduling.

Eligible Work is considered as a complete local set with explicit urgency/creation ordering, so unavailable head Work cannot starve later runnable Work.

Timing defaults are named technical Kernel policy and can be injected for tests through `IKernelClock` / `KernelTimingOptions`; they are not nontechnical Owner configuration knobs.

## Binding execution

`IInferenceBinding` is the open physical seam:

```text
ProbeAsync()
ExecuteAsync(InferenceExecutionRequest)
```

`InferenceExecutionRequest` contains execution-relevant physical input only. Scheduler metadata such as urgency, eligibility and deadline is not passed to bindings.

The provided shell-free process binding, MEAI interoperability and Owner/custom bindings all use the same ordinary seam. Common execution responsibility is centralized in `BindingExecutor`: exception conversion, successful-result validity and output-size enforcement happen once.

Process-specific mechanics remain inside the process binding: launch, bounded stdout/stderr, exit status and confirmed process-tree cancellation.

## Physical failure vocabulary

Physical failures use `PhysicalFailureKind` plus separate optional technical detail. Current kinds cover:

```text
NoAdmissibleCapability
DeadlineExpired
Cancelled
LaunchFailed
ProcessExited
PayloadLimitExceeded
IoFailure
InvalidBindingResult
CompletionUnknown
```

Persistence stores the named kind and detail separately. Scheduling does not depend on scattered magic strings.

## Local IPC

Kernel and the Java 21 client use a small versioned local protocol over Unix-domain sockets on supported Windows/Linux systems.

There is no TCP listener, web server, path/status vocabulary, configurable port or web-host dependency.

Each message is one bounded frame:

```text
4-byte big-endian length
UTF-8 JSON envelope
```

The envelope carries protocol version, request correlation id, operation and payload; responses carry success payload or a small IPC error code/detail.

`KernelProtocol` owns version and payload/frame limits in .NET. The Java package has one corresponding `KernelProtocol` source. Live acceptance asks the host for `ProtocolInfo` and proves the Java/.NET values match rather than relying on unexplained duplicate literals.

Each client operation uses an independent local socket connection. The server accepts clients concurrently; a stalled or disappearing connection is bounded by its own idle timeout and cannot block unrelated callers or own Kernel lifetime.

The default socket lives in the current owner's local application-data directory. Linux restricts the containing directory and socket to owner access. A technical `--ipc-path` override exists for testing/embedding; there is no port setting.

## Durability and restart truth

SQLite owns the durable physical lifecycle. On startup:

- schema is opened;
- configured capability catalogue/current state is reconciled to configuration;
- current capability state starts `Unknown` until observed again;
- attempts left `Running` by process loss become `UnknownCompletion` with technical restart detail;
- corresponding Work becomes `UnknownCompletion`.

Kernel does not infer that an interrupted physical attempt failed and does not replay it automatically.

## Cancellation

Queued Work cancels without creating an attempt. Running Work records a cancellation request and signals the active binding execution.

The process binding reports confirmed cancellation only after physically terminating and observing its process tree. Other uncertain cancellation paths become `UnknownCompletion` rather than claiming certainty Kernel does not have.

## MEAI and future physical strategies

Microsoft.Extensions.AI remains useful generic inference interoperability behind bindings. It does not define `InferenceCapability` or DRE.

There is no current production Microsoft Agent Framework dependency or workflow/checkpoint strategy. The previous two-stage `High + Background` experiment proved checkpoint machinery but encoded no meaningful MADRE product strategy, so it and its durable scaffolding were deleted.

A future actual physical strategy may use MAF, another workflow mechanism or direct code if a concrete product need earns it. No strategy registry/checkpoint fields are retained speculatively.

## Open physical binding and first-party parity

An unusual Owner-controlled inference system should normally be usable through configuration or an `IInferenceBinding`, not provider-specific edits to Kernel architecture.

Do not add connector marketplaces, hot-loading frameworks, generic plugin systems or security prisons without evidence. MADRE-provided integrations use the same class of physical seam available to advanced Owners. Removing one adapter must leave Kernel architecture coherent.

## Current acceptance

Normal CI runs the complete Java/.NET Lane C suite on Linux and Windows. It proves:

- zero-capability/no-config startup;
- no port/web/TCP control-plane implementation;
- bounded versioned local-socket protocol and Java/.NET parity;
- real Java 21 ↔ .NET local-socket submit/inspect/result/cancel/release behavior;
- stalled/disappearing clients do not block other callers;
- caller process disappearance does not destroy durable Work;
- bounded payloads/concurrency;
- eligibility, urgency and deadlines;
- cancellation and retained-result release;
- restart recovery with `UnknownCompletion` and no implicit duplication;
- current configured-capability reconciliation with historical attempts retained;
- optional-probe `Unknown` capability execution and evidence update;
- automatic recovery of known-unavailable capabilities without manual refresh;
- Owner preference / observed-latency DRE behavior where applicable;
- process binding and Owner/custom binding openness;
- no fixed-head starvation;
- contamination checks rejecting semantic leakage, web/port residue, MAF/checkpoint residue and production packaging of Java test helpers.

## Historical evidence

Historical native C++, worker/runtime, llama.cpp, protocol-v4, loopback web-host and MAF validation implementations are not current architecture. Their useful behaviors survive only when independently justified by MADRE and re-proven in the current tree.
