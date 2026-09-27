# MADRE — Lane C Owner Decision: DRE and Physical Inference Architecture

Status: **accepted current Owner decision and current Lane C convergence**.

This document preserves the ownership decision and the final engineering correction so future work cannot reconstruct Lane C from rejected implementation scaffolding.

## Why Lane C exists

Lane C makes physical inference reliable enough that semantic MADRE does not need to care about process, provider, model-runtime or scheduling mechanics. It must do that without turning the Owner's inference environment into a MADRE-owned worker/engine ontology.

Two historical extremes are rejected:

```text
Kernel owns engines/workers/model lifecycle
```

and:

```text
semantic MADRE completely selects exact provider/model/configuration
before Kernel, leaving DRE no meaningful physical choice
```

The accepted middle is:

```text
semantic MADRE derives a small physical inference requirement
        ↓
Kernel owns durable physical Work, capability truth and DRE
        ↓
open physical binding
        ↓
Owner-selected independent inference environment
```

## Fixed semantic/physical boundary

Semantic MADRE owns application and reasoning meaning. It can know the reasoning objective, relevant Material/context, desired reasoning effort, urgency/deadline, acceptable delay, constraints derived from actual semantic composition and explicit Owner preferences.

Semantic MADRE reduces those facts to a physical inference requirement. `ReasoningRequest` itself does not cross into Kernel.

Kernel owns the physical consequences:

- durable Work identity/lifecycle;
- configured `InferenceCapability` catalogue;
- current availability state;
- observed physical evidence/history;
- inference-aware scheduling;
- capability choice among admissible options;
- execution/cancellation/result lifecycle;
- truthful restart recovery.

Kernel does not own Module/Agent continuation and does not judge whether a physically valid answer solves the application problem.

## PhysicalInferenceWork

The first implemented physical request remains deliberately small:

```text
prepared input
requested physical effort
urgency
optional eligibility time
optional deadline
hard local/external execution boundary
```

A field belongs in the physical contract because an implemented DRE decision consumes it, not because a generic AI platform might want it.

No Module, Agent, Operation, Material, SPIRA, CORE, semantic Workflow/WorkPlan or semantic continuation is smuggled into Work.

## InferenceCapability truth

An `InferenceCapability` is a configured physical path through which MADRE can obtain intelligence. It is not a provider/model ontology and it does not make the underlying runtime MADRE-owned.

The architecture distinguishes:

```text
CONFIGURED / DECLARED
    identity + binding
    execution boundary
    supported effort
    Owner preference

CURRENT
    Unknown / Unavailable / Available
    observation time

HISTORICAL / OBSERVED
    attempt outcome
    successful latency evidence
    failure evidence
```

Configured expectation, current observation and historical evidence are separate truth domains.

### Unknown is not Unavailable

A configured capability without a custom probe remains `Unknown`; that is not a reason to make it unusable. DRE prefers known-available admissible capabilities. If no known-available option exists, an admissible configured `Unknown` capability may be tried. Actual execution can then produce current physical evidence.

A known-unavailable capability is not dispatched. It is re-observed automatically using Kernel-owned timing policy, so recovery does not depend on a caller manually refreshing it forever.

Startup probing is asynchronous and cannot hold Kernel availability hostage. A valid empty Kernel starts with zero capabilities and no configuration file.

## DRE

DRE remains a real Kernel concern. It is not flattened into a dumb executor.

Current DRE consumes:

- durable eligibility/deadlines;
- explicit effort admissibility;
- hard local/external boundary admissibility;
- current availability truth;
- Owner preference;
- persisted successful latency evidence for interactive choice when comparable evidence exists.

Effort and urgency ordering are explicit domain code, not enum ordinal semantics or SQL policy.

The scheduler is wake/deadline driven. It does not busy-poll. Waiting unavailable Work cannot hide later runnable Work behind a fixed head page.

## Binding execution

`IInferenceBinding` is the open physical seam. A binding receives only execution-relevant physical data (`InferenceExecutionRequest`), not eligibility, urgency, deadlines or other scheduler metadata.

Process binding, MEAI interoperability and Owner/custom bindings use the same Kernel execution responsibility. Common result validation, exception-to-outcome conversion and payload enforcement are centralized rather than duplicated across execution paths.

Failures use a typed physical vocabulary with optional technical detail. Kernel can therefore distinguish concepts such as deadline expiry, no admissible capability, launch failure, process exit, payload overflow, I/O failure, cancellation and unknown completion without scattering magic strings through scheduling/persistence.

## Durable truth

SQLite remains authoritative for current Lane C physical Work, attempt history, capability catalogue/state and retained results.

On startup the configured capability catalogue is reconciled to the actual current configuration. Removed capabilities disappear from selectable configuration/current state. Historical attempts are not deleted merely because their capability is no longer configured.

An interrupted active attempt recovers as `UnknownCompletion`. MADRE does not silently duplicate uncertain physical work or rewrite uncertainty into definite failure.

Terminal release preserves Work identity/history while clearing retained input/result payload.

## Local IPC decision

Lane C uses a small versioned local IPC contract over Unix-domain sockets on supported Windows/Linux targets. There is no TCP listener, web path/status vocabulary, configurable port or web-host dependency.

The protocol uses bounded length-prefixed UTF-8 JSON frames. One source owns version/limits on each side and cross-language acceptance proves parity. Connections are independent and short-lived; a stalled or disappearing caller cannot own Kernel lifetime or block unrelated callers.

The default endpoint lives in the current owner's local application-data area. Linux endpoint placement/permissions are owner-only. Tests/embedding may supply a technical socket-path override without creating a user-facing port setting.

## MAF decision

The previous `High + Background -> MAF two-stage` behavior is rejected as arbitrary validation strategy rather than MADRE product strategy.

The production MAF workflow/checkpoint implementation, package dependency, Work/checkpoint fields/state/methods, MAF-only acceptance and authority claims are deleted. Git history is sufficient evidence that MAF checkpointing was validated.

This is not a permanent ban on MAF. A future real physical strategy may use MAF when an actual product need makes its graph/checkpoint machinery useful. No scaffolding is retained in anticipation of that possibility.

MEAI remains useful generic interoperability behind the same open binding seam.

## What survives from historical Lane C

Behavior survives when MADRE needs it, not because an old implementation shape deserves preservation:

- independent Kernel lifetime;
- durable SQLite Work;
- capability-aware DRE;
- bounded payloads and bounded physical concurrency;
- concurrent local clients and caller disappearance;
- eligibility/deadlines;
- cancellation;
- retained results and explicit release;
- attempt history;
- restart recovery with `UnknownCompletion`;
- process binding;
- open binding extensibility;
- Java 21 client operation;
- Windows/Linux support.

The native C++ worker/engine/model lifecycle, llama.cpp privilege, protocol-v4 shapes, loopback web control plane and validation MAF strategy are historical evidence only.

## Anti-drift test

Lane C is drifting if it:

- rematerializes provider/model/runtime internals as MADRE-owned engine/worker ontology;
- resolves all meaningful physical choice above Kernel;
- sends semantic MADRE objects into Kernel;
- treats `Unknown` as `Unavailable` merely because a custom probe is absent;
- makes startup depend on inference configuration/probe success;
- requires manual refresh for capability recovery;
- hides policy in SQL, enum ordinals or magic polling intervals;
- reintroduces TCP/web infrastructure because it is familiar;
- retains validation-only strategy/checkpoint machinery without a product need;
- privileges first-party integrations over Owner/custom bindings;
- starts SDK/Runtime work while correcting Lane C.

The target remains: **a small, truthful, durable, capability-aware physical inference Kernel that belongs to MADRE without trying to own the Owner's intelligence environment.**
