# MADRE Kernel — current physical inference architecture

Status: **current accepted physical architecture and implementation; Lane C closure remains an Owner/orchestrator audit decision**.

This document describes the active capability-aware .NET Kernel under `kernel/`. Historical C++, loopback web-host and MAF-validation implementations remain Git-history evidence only.

## Purpose and boundary

Kernel exists so MADRE can physically realize reasoning needs over time against the Owner's available intelligence without turning those inference systems into MADRE-owned engines/workers.

> **Kernel owns durable physical inference scheduling, capability truth and execution. It does not own application semantics or the internal lifecycle of the Owner's inference systems.**

```text
SEMANTIC MADRE
    Module / Agent owns meaning and continuation
        ↓
    small derived physical inference requirement
        ↓
madre-kernel-client / physical boundary
================ HARD BOUNDARY ================
KERNEL
    PhysicalInferenceWork
        ↓
    DRE + InferenceCapability truth/evidence
        ↓
    selected physical binding
        ↓
    Owner-selected inference environment
```

The current physical request contains only prepared input, requested effort, urgency, optional eligibility/deadline and hard local/external execution boundary. Kernel remains ignorant of Module, Agent, Operation, Material, ReasoningRequest, SPIRA, CORE, semantic Workflow/WorkPlan and semantic continuation/persistence.

## Process ownership and local endpoint

Exactly one Kernel process owns a given SQLite database at a time, independent of IPC path. The process acquires a database-scoped lifetime lease before touching the IPC endpoint or starting scheduling.

A second Kernel targeting the same database fails whether it requests the same socket or another socket. It cannot unlink the first Kernel's endpoint and cannot run another scheduler over the same authoritative database.

Endpoint preparation is separately conservative. A proven-live local socket is never deleted. A stale filesystem socket is removed only after a connection attempt establishes that it is not live. Endpoint cleanup remains process-local and does not define database ownership.

## Durable Work and schema truth

SQLite is authoritative for current Lane C physical Work, attempt history, configured/current capability truth and retained results.

The current schema has an explicit `PRAGMA user_version` identity. A fresh database is created as the current schema. An existing unversioned database with pre-release tables, or a database with another schema version, fails early with a clear incompatibility error. Lane C does not add migrations or compatibility machinery for pre-release physical schemas.

One durable Work lifecycle holds:

```text
identity / lifecycle state
prepared input + hard physical constraints
requested effort / urgency
eligibleAt / deadline
selected capability/binding when dispatched
attempt history
result / failure / release state
```

Active attempts interrupted by process loss recover as `UnknownCompletion`; uncertain execution is never silently duplicated. Terminal release clears retained request/result payload while preserving Work identity, terminal state and attempt history.

## InferenceCapability truth

An `InferenceCapability` is a configured physical path through which MADRE can obtain intelligence. It is not a provider/model ontology or MADRE-owned worker.

The typed core separates:

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
    durable physical attempts
    successful latency evidence for the current capability/binding/version
```

Configured expectation, current observation and historical evidence are distinct. Removed configured capabilities disappear from selectable current catalogue/state while historical attempt rows remain historical.

Latency evidence is provenance-scoped to `(capability_id, binding_id, binding_version)`. Reconfiguring the same capability id to another binding/version does not reuse stale latency in DRE. Success/failure aggregate counters with no current DRE consumer are not exposed as capability state.

### Unknown and Unavailable

`Unknown` means Kernel lacks current availability evidence. It does not mean unusable.

Selection rule:

1. filter by hard effort and execution-boundary admissibility;
2. prefer known-available candidates;
3. if none are known available, an admissible configured `Unknown` may be tried;
4. if all admissible candidates are known unavailable, Work waits for re-observation.

Startup observation is asynchronous. A probe exceeding its technical timeout becomes `Unknown`, not `Unavailable`.

Known-unavailable capabilities are automatically re-observed only while relevant pending Work creates demand. Kernel does not periodically probe every capability forever while idle. Explicit refresh remains available, and successful/physically informative execution may update current state.

## DRE scheduling

DRE remains the inference-aware physical scheduler rather than a generic timer plus dumb executor.

Current decisions consume:

- eligibility/deadlines;
- explicit effort admissibility;
- hard local/external admissibility;
- current capability availability;
- Owner preference;
- binding/version-scoped successful latency evidence for interactive choice when comparable evidence exists.

Effort support and urgency priority are explicit domain functions. Enum ordinals do not carry policy. SQL persists/query facts; it does not embed urgency policy.

The scheduler is wake/deadline driven. Submission, cancellation, attempt completion and capability observations wake it. Future eligibility/deadlines and demanded unavailable-capability re-observation provide timed wakes. There is no fixed busy-poll interval.

Eligible Work is considered as a complete metadata set with explicit urgency/creation ordering, so waiting unavailable Work cannot starve a later runnable item. The candidate query does **not** load `prepared_input`; payload is loaded transactionally only after Work is actually claimed for an attempt.

Timing defaults are named technical Kernel policy and injectable for tests. They are not nontechnical Owner knobs.

## Background supervision and health truth

Scheduler, capability observation and attempt execution/persistence are Kernel-owned background work and are supervised.

Normal physical probe failure may produce `Unknown`; that is not a fatal infrastructure event. By contrast, failure to maintain authoritative Kernel state—scheduler persistence/query failure, capability-state persistence failure, or attempt completion persistence failure—is fatal.

A fatal infrastructure failure signals Kernel fatal completion, cancels host lifetime and causes the host process to fail/terminate. It must not continue serving `Health = ok` after the authoritative scheduler/state machinery has died.

If an attempt completion could not be durably recorded before the fatal process exit, restart recovery converts the still-`Running` durable attempt/Work to `UnknownCompletion` rather than leaving it forever running or replaying it.

## Binding execution

`IInferenceBinding` is the open physical seam:

```text
ProbeAsync()
ExecuteAsync(InferenceExecutionRequest)
```

`InferenceExecutionRequest` contains execution-relevant prepared input only. Scheduler metadata does not cross into bindings.

The provided process binding, MEAI interoperability and Owner/custom bindings use the same ordinary seam. Common execution responsibility is centralized in `BindingExecutor`: exception conversion, successful-result validity and output-size enforcement happen once.

Process-specific mechanics stay in the process binding: shell-free launch, explicitly UTF-8 stdin/stdout/stderr, bounded output, exit status and confirmed process-tree cancellation.

## Physical failure vocabulary

Failures use `PhysicalFailureKind` plus optional technical detail. Current kinds cover no-admissible-capability, deadline expiry, cancellation, launch failure, process exit, payload overflow, I/O failure, invalid binding result and unknown completion. Persistence stores kind and detail separately; scheduling does not depend on magic failure strings.

## Local IPC

Kernel and the Java 21 client use a small versioned local protocol over Unix-domain sockets on supported Windows/Linux systems.

There is no TCP listener, web server, path/status vocabulary, configurable port or web-host dependency.

Each message is one bounded frame:

```text
4-byte big-endian length
UTF-8 JSON envelope
```

`KernelProtocol` owns version/payload/frame limits in .NET. The Java package has one corresponding `KernelProtocol`; live `ProtocolInfo` acceptance proves parity.

Each client operation uses an independent short-lived local socket. The server has both a listen backlog and a hard bound on active client handlers, so stalled connections cannot create unbounded task exposure. Per-client idle timeout bounds server-side stalls.

The Java client uses typed `KernelIpcOperation` and `KernelIpcErrorCode` vocabulary and wraps each blocking local call in a bounded technical call lifetime. A bogus/stalled socket peer therefore fails rather than blocking a caller forever.

## Strict boundary parsing

Protocol/configuration/CLI boundaries do not silently acquire defaults for required facts.

IPC requires protocol version, request id and operation. Submit requires prepared input, effort, urgency and execution boundary. String enums are required; numeric enum encodings are rejected. Unknown parsed JSON properties are rejected.

Capability configuration requires explicit capability id, binding id/version, executable, execution boundary, supported effort and Owner preference. Unknown properties, numeric enums and malformed required values fail loading.

CLI accepts only the documented flags, exactly once, with explicit values. Unknown flags, duplicate flags, missing values and malformed integer values fail before host startup.

The normal Kernel host default for physical concurrency has one owner (`KernelHostDefaults`), rather than being duplicated in `KernelEngine` and configuration types.

## Restart, cancellation and release

On startup:

- current schema identity is verified;
- current configured capability catalogue/state is reconciled;
- current capability state begins `Unknown` until observed again;
- attempts left `Running` by process loss become `UnknownCompletion` with technical restart detail;
- corresponding Work becomes `UnknownCompletion`.

Queued Work cancels without an attempt. Running Work records a cancellation request and signals active binding execution. The process binding reports confirmed cancellation only after physically terminating/observing its process tree. Uncertain cancellation becomes `UnknownCompletion` rather than invented certainty.

Terminal release preserves identity/history while deleting retained request/result payload.

## Framework containment and openness

Microsoft.Extensions.AI remains useful generic inference interoperability behind bindings. It does not define `InferenceCapability` or DRE.

There is no current production Microsoft Agent Framework dependency or workflow/checkpoint strategy. A future actual physical strategy may use MAF, another workflow mechanism or direct code if a concrete product need earns it. No strategy registry/checkpoint fields are retained speculatively.

An unusual Owner-controlled inference system should normally be usable through configuration or an `IInferenceBinding`, not provider-specific edits to Kernel architecture. Do not add connector marketplaces, hot-loading frameworks, generic plugin systems or security prisons without evidence. Provided integrations use the same class of seam available to advanced Owners.

## Current acceptance

Normal CI runs the complete Java/.NET Lane C suite on Linux and Windows. It proves, among other retained behavior:

- zero-capability/no-config startup;
- same-database single-process ownership for same and different socket paths;
- non-destructive live endpoint handling and stale filesystem-socket recovery where applicable;
- explicit schema identity and incompatible pre-release rejection;
- bounded versioned local-socket protocol and Java/.NET parity;
- strict IPC/config/CLI validation;
- bounded active client handlers and Java stalled-peer timeout;
- real Java 21 ↔ .NET submit/inspect/result/cancel/release;
- explicit UTF-8 non-ASCII process round-trip;
- stalled/disappearing clients do not own Kernel lifetime;
- eligibility, urgency, deadlines, cancellation and release;
- restart `UnknownCompletion` with no implicit duplication;
- configured-capability reconciliation with history retained;
- optional-probe `Unknown` execution and evidence update;
- idle capabilities are not periodically reprobed;
- unavailable capabilities recover under pending demand;
- probe timeout remains `Unknown`;
- binding/version-scoped latency evidence;
- process/custom binding openness;
- no fixed-head starvation and deterministic eligibility-boundary wake behavior;
- forced scheduler/probe/attempt persistence failures fail the host instead of creating zombie health.

## Historical evidence

Historical native C++, worker/runtime, llama.cpp, protocol-v4, loopback web-host and MAF validation implementations are not current architecture. Useful behaviors survive only when independently justified by MADRE and re-proven in the current tree.

This implementation status is evidence for Owner/orchestrator audit. The implementation does not declare Lane C closed.
