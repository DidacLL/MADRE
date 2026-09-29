# MADRE Kernel — current physical inference architecture

Status: **closed and qualified current physical architecture and implementation after the semantic→physical boundary repair**.

This document defines the active capability-aware .NET Kernel under `kernel/`. Historical C++, worker/runtime, loopback web-host, protocol-v4 and MAF-validation implementations remain Git-history evidence only.

Lane C closure is an Owner/orchestrator architecture decision supported by implementation evidence; tests do not define the architecture. The earlier closure at `95ddf2250c27d28e90e215391223164965b68729` was superseded because the public boundary could not preserve exact/eligible physical selection or expose enough factual execution-path information. Those omissions are repaired in the current baseline.

## Closure baseline

Current executable Kernel closure baseline:

```text
77a862941e9c05d15652317616069a296af0f397
```

Deterministic cross-platform requalification:

```text
GitHub Actions run 36604046199
seed 12648430
scale medium

Ubuntu  regression     PASS
Ubuntu  qualification  PASS
Windows regression     PASS
Windows qualification  PASS
```

The repaired boundary was also exercised by the preserved Lane C acceptance suite on both operating systems. Stress/soak remain manual qualification tools; they were not rerun merely because this bounded contract/schema correction did not change the underlying load/restart architecture.

`docs/architecture/kernel-handoff.md` records what Lane A/B and anti-drift agents may rely on. `docs/product/lane-c-owner-decision.md` records the Owner reasoning that this implementation must serve.

## Purpose and hard boundary

Kernel exists so MADRE can physically realize reasoning needs over time against the Owner's available intelligence without turning those inference systems into MADRE-owned engines/workers.

> **Kernel owns durable physical inference scheduling, capability truth and execution. It does not own application semantics or the internal lifecycle of the Owner's inference systems.**

```text
SEMANTIC MADRE
    Module / Agent owns meaning and continuation
        ↓
    ReasoningRequest + SPIRA + Owner choice
        ↓
    semantic derivation of physical admissibility
        ↓
madre-kernel-client / physical request
================ HARD BOUNDARY ================
KERNEL
    PhysicalInferenceWork
        ↓
    admissible physical capability space
        ↓
    DRE + InferenceCapability truth/evidence
        ↓
    selected physical binding
        ↓
    Owner-selected inference environment
```

Kernel remains ignorant of Module, Agent, Operation, Material, ReasoningRequest, SPIRA, CORE, semantic Workflow/WorkPlan and semantic continuation/persistence.

The boundary flows in **both directions**:

- downward: semantic MADRE sends only physical consequences/restrictions;
- upward: Kernel exposes factual configured execution-path information and physical observations so semantic MADRE/Owner tooling can understand the information journey.

## PhysicalInferenceWork request

The current physical request is deliberately small:

```text
prepared input
requested effort
urgency
optional eligibleAt
optional deadline
request-side allowed exposure: LocalOnly / ExternalAllowed
optional eligibleCapabilityIds
```

`ExecutionBoundary` is request-side only. It means what this Work is permitted to do:

- `LocalOnly` — the selected capability must factually execute locally;
- `ExternalAllowed` — either local or external execution is admissible.

It is **not** a factual capability property.

`EligibleCapabilityIds` is an opaque physical restriction derived above Kernel:

- absent: DRE may consider every configured capability satisfying the other physical constraints;
- one id: exact physical selection is preserved;
- several ids: DRE chooses only within that semantically/Owner-established eligible set.

Kernel does not know why a capability was excluded and does not receive SPIRA or provider/model semantics. Unknown or otherwise inadmissible ids simply yield no admissible capability; Kernel does not silently broaden the set.

The eligibility set is durable Work metadata and remains inspectable. Release clears retained input/result payload while preserving identity/history and physical metadata.

## Process ownership and local endpoint

Exactly one Kernel process owns a given SQLite database at a time, independent of IPC path. The process acquires a database-scoped lifetime lease before touching the IPC endpoint or starting scheduling.

Database ownership is based on physical path identity. On Linux filesystem aliases/symlinks are resolved so the same authoritative database cannot obtain multiple Kernel owners through different path spellings.

A second Kernel targeting the same database fails whether it requests the same socket or another socket. Endpoint preparation is separately conservative: a proven-live local socket is never deleted; a stale filesystem socket is removed only after a connection attempt establishes that it is not live.

## Durable Work and schema truth

SQLite is authoritative for physical Work, physical admissibility metadata, attempt history, configured/current capability truth and retained results.

The current pre-release schema is version `2`. A fresh database is created as the current schema. An unversioned database with existing tables or another schema version fails early. No migration/compatibility layer is retained for superseded pre-release physical schemas.

One durable Work lifecycle contains:

```text
identity / lifecycle state
prepared input
requested effort / urgency
eligibleAt / deadline
request-side allowed exposure
optional eligible physical capability IDs
selected capability/binding when dispatched
attempt history
result / failure / release state
```

Active attempts interrupted by process loss recover as `UnknownCompletion`; uncertain physical execution is never silently duplicated.

## InferenceCapability factual truth

An `InferenceCapability` is a configured physical path through which MADRE can obtain intelligence. It is not a provider/model ontology or a MADRE-owned worker.

The typed core separates configured facts, current state and historical evidence:

```text
CONFIGURED / DECLARED
    capability identity
    binding identity/version
    factual ExecutionPath
        ExecutionLocation: Local / External
        destination
        optional route/intermediary description
        optional data-retention/history description
    supported effort
    Owner preference

CURRENT
    Unknown / Unavailable / Available
    observed-at time

HISTORICAL / OBSERVED
    durable physical attempts
    successful latency evidence for current capability/binding/version
```

Execution-path facts carry provenance. Owner declarations and provider/runtime claims are not silently collapsed. Route and retention descriptions remain deliberately physical/opaque; Kernel exposes them but does not interpret them as SPIRA or provider policy.

Host-configured process capabilities require explicit execution location and destination. Optional route/retention values cannot be blank. Programmatically/custom-constructed capabilities are validated against the same factual contract so alternate construction paths cannot bypass information-journey truth.

Removed configured capabilities disappear from the current selectable catalogue/state while historical attempts remain historical. Latency evidence is scoped to `(capability_id, binding_id, binding_version)` so a replacement binding/version does not inherit stale evidence.

## Unknown and Unavailable

`Unknown` means Kernel lacks current availability evidence; it does not mean unusable.

For a Work item, DRE filters in this order conceptually:

1. opaque eligible capability ids, if supplied;
2. requested effort compatibility;
3. request-side allowed exposure against factual capability `ExecutionLocation`;
4. current availability preference.

Known-available candidates are preferred. If none are known available, an admissible `Unknown` candidate may be tried. If all admissible candidates are known unavailable, Work waits for re-observation. If no configured capability survives physical admissibility, Work fails `NoAdmissibleCapability` without an attempt.

Startup observation is asynchronous. Probe timeout becomes `Unknown`, not fabricated `Unavailable`. Known-unavailable capabilities are automatically re-observed only while relevant pending Work creates demand; Kernel does not probe every capability forever while idle.

## DRE scheduling

DRE remains the inference-aware physical scheduler rather than a generic timer plus executor.

Current decisions consume:

- eligibility/deadlines;
- opaque eligible capability restriction;
- explicit effort admissibility;
- request-side allowed exposure plus factual capability location;
- current capability availability;
- Owner preference;
- binding/version-scoped successful latency evidence for interactive choice when comparable evidence exists.

Normal/background selection is Owner-preference-first. Interactive selection uses successful latency only when all candidate evidence is comparable, then Owner preference and deterministic identity tie-breaks.

The scheduler is wake/deadline driven. Submission, cancellation, attempt completion and capability observations wake it. Future eligibility/deadlines and demanded unavailable-capability re-observation provide timed wakes. There is no fixed busy-poll interval.

Eligible Work is considered as a complete metadata set with urgency/creation ordering so waiting unavailable Work cannot hide a later runnable item. Candidate queries do not load `prepared_input`; payload is loaded transactionally only after claim.

A scheduler pass snapshots dispatch capacity. A slot released by a fast completion is reconsidered by a fresh scheduler pass instead of being reused later against stale urgency ordering.

## Background supervision and health truth

Scheduler, capability observation and attempt execution/persistence are Kernel-owned background work and are supervised.

Normal physical probe failure may produce `Unknown`. By contrast, failure to maintain authoritative Kernel state—scheduler persistence/query failure, capability-state persistence failure, or attempt completion persistence failure—is fatal.

A fatal infrastructure failure signals Kernel fatal completion, cancels host lifetime and causes the host to fail/terminate. It must not continue serving healthy after authoritative machinery has died. If attempt completion could not be durably recorded before exit, restart recovery converts the remaining durable `Running` Work/attempt to `UnknownCompletion` rather than replaying it.

## Binding execution

`IInferenceBinding` is the open physical execution seam:

```text
ProbeAsync()
ExecuteAsync(InferenceExecutionRequest)
```

Bindings receive execution-relevant prepared input only; scheduling metadata, eligible sets and semantic data do not cross into the binding execution request.

The provided process binding, MEAI interoperability and Owner/custom bindings use the same seam. Common execution responsibility is centralized in `BindingExecutor`: exception conversion, success-result validity and output-size enforcement happen once.

Process-specific mechanics remain shell-free, explicitly UTF-8, bounded, and use confirmed process-tree cancellation where physical certainty is available.

## Physical failure vocabulary

Failures use `PhysicalFailureKind` plus optional technical detail. Current kinds cover no-admissible-capability, deadline expiry, cancellation, launch failure, process exit, payload overflow, I/O failure, invalid binding result and unknown completion. Scheduling does not depend on magic failure strings.

## Local IPC

Kernel and the Java 21 client use protocol version `2` over Unix-domain sockets on supported Windows/Linux systems.

There is no TCP listener, configurable port or web host. Each message is one bounded frame:

```text
4-byte big-endian length
UTF-8 JSON envelope
```

Each client operation uses an independent short-lived local socket. The server has a listen backlog, a hard active-client bound and per-client idle timeout. Java calls also have a bounded technical lifetime.

IPC parsing is strict. Submit requires the physical fields needed by the contract; numeric enum encodings and unknown parsed properties are rejected. When an eligible capability list is present it cannot be empty, contain blank ids or contain duplicates.

`Capabilities` exposes factual `CapabilityExecutionPath` data to Java/semantic-side consumers; Java acceptance proves cross-language parity for location, destination, route/retention facts and exact/eligible submission.

## Restart, cancellation and release

On startup:

- current schema identity is verified;
- configured capability catalogue/state is reconciled;
- current capability state begins `Unknown` until observed;
- attempts left `Running` become `UnknownCompletion`;
- corresponding Work becomes `UnknownCompletion`.

Queued Work cancels without an attempt. Running Work records a cancellation request and signals active binding execution. The process binding reports confirmed cancellation only after physical process-tree termination/observation; uncertainty becomes `UnknownCompletion` rather than invented certainty.

Terminal release preserves identity/history and physical metadata while deleting retained request/result payload.

## Framework containment and openness

Microsoft.Extensions.AI remains useful generic inference interoperability behind bindings. It does not define `InferenceCapability`, physical admissibility or DRE.

There is no current production Microsoft Agent Framework dependency or workflow/checkpoint strategy. A future actual physical strategy may use MAF, another workflow mechanism or direct code if a concrete product need earns it. No strategy/checkpoint scaffolding is retained speculatively.

An unusual Owner-controlled inference system should normally be usable through configuration or an `IInferenceBinding`, not provider-specific edits to Kernel architecture. Do not add connector marketplaces, generic hot-loaders, provider ontologies or defensive plugin prisons without evidence.

## Verification after closure

Kernel verification is manual/on-demand, not a permanent PR/push tax. The active workflow is:

```text
.github/workflows/kernel-verification.yml
```

It exposes `regression`, `qualification`, `stress` and `soak` on Ubuntu/Windows. Lane A/B changes do not trigger the Kernel arsenal merely because they share the repository.

The current repaired baseline adds direct acceptance for:

- unrestricted DRE still choosing normally;
- singleton eligible set preserving exact capability choice;
- multi-capability eligible set allowing DRE selection only inside it;
- unknown/incompatible eligible sets producing `NoAdmissibleCapability` with no attempt;
- `LocalOnly` excluding factually external capabilities;
- durable inspection of Work admissibility;
- capability snapshot exposure of factual location/destination/route/retention with provenance;
- Java submit/inspect/capability round-trip for the same boundary;
- custom/programmatic capability construction obeying factual execution-path validation.

These sit inside preserved Lane C acceptance rather than a temporary side suite.

A green verifier run is evidence, not authority. A failing assertion must be classified against current Owner intent before it is allowed to redefine the product.

## Anti-drift boundary

Lane C is drifting if later work:

- rematerializes provider/model/runtime internals as MADRE-owned engine/worker ontology;
- resolves all meaningful physical choice above Kernel;
- discards an exact Owner physical selection or semantic eligible physical set;
- lets DRE widen that eligible set silently;
- conflates request permission (`ExternalAllowed`) with factual capability location;
- hides destination/route/retention facts needed above Kernel to reason about the Owner's information journey;
- sends Module, Agent, Material, SPIRA, ReasoningRequest, CORE or semantic continuation into Kernel;
- makes Kernel judge answer quality;
- adds speculative physical strategy/checkpoint/plugin machinery;
- reintroduces routine Kernel CI for unrelated semantic work.

Historical native C++, worker/runtime, llama.cpp, protocol-v4, loopback web-host and validation-only MAF shapes are not active alternatives.

Lane C is closed at the repaired executable baseline above. Future agents must read `docs/architecture/kernel-handoff.md` before reopening it and require a concrete physical defect, a real new physical requirement, or an explicit Owner decision rather than a preference for another architecture.
