# MADRE Kernel — current physical architecture

This document is the current public Kernel architecture. It describes the durable physical inference-execution subsystem implemented by Lane C after the LCR1 ownership correction.

For whole-system semantics and the semantic/physical boundary, see `docs/architecture/mid-level-architecture.md`. For SPIRA, see `docs/architecture/security-algebra.md`.

## Purpose

The Kernel exists so semantic MADRE does not have to keep a process alive merely to carry already-decided physical inference Work to completion.

The invariant is:

> **Kernel does not own the intelligence environment. Kernel owns durable physical execution of inference choices already made above it.**

Inference-target configuration, provider/model/service/executable meaning, semantic reasoning intent and the choice of acceptable physical destinations belong above Kernel.

## Hard boundary

```text
SEMANTIC MADRE

Agent creates ReasoningRequest
        ↓
Runtime executes configured Module-owned reasoning→physical Operation
        ↓
semantic side resolves acceptable inference choices
        ↓
one or more already-approved ConcretePhysicalInvocation candidates
        ↓
madre-kernel-client

================ HARD BOUNDARY ================

native Kernel
        ↓
physical executor for the supplied invocation variant
```

The Kernel must not know:

```text
Module
Agent
Operation semantics
Material
ReasoningRequest
SPIRA / Sensitivity / Privacy / Integrity / Risk / Autonomy
CORE
Skill
Workflow
WorkPlan semantics
semantic continuation
semantic persistence
Owner inference-target catalogue
semantic effort/capability requirements
```

If Kernel code needs one of those concepts, the semantic/physical boundary has drifted.

## Concrete physical invocation candidates

A physical `WorkRequest` contains one or more already-approved concrete physical invocation candidates.

Each candidate is complete enough for its physical executor to attempt and carries an opaque stable candidate identity. Optional target identity may be retained and reported as descriptive physical information, but Kernel does not interpret it to discover or substitute another provider/model/destination.

The supplied candidate list is the complete acceptable set.

- one candidate means the physical inference choice is exact;
- multiple candidates allow only physical routing among those supplied candidates;
- Kernel may never widen the set or synthesize another destination.

Routing is deliberately small. LCR1 uses deterministic supplied order and may skip a `ProcessInvocation` whose executable is physically unavailable. That is a physical executability fact, not semantic model selection.

## Physical client

`madre-kernel-client` is deliberately small and physical. It does not depend on `madre-sdk`.

The current contract supports:

- submit durable Work containing concrete invocation candidates;
- inspect physical status and the selected supplied candidate;
- collect a retained result;
- cancel Work;
- acknowledge a retained successful result.

The client does **not** expose an inference-engine inventory and does not ask Kernel to interpret `Effort`, required model capabilities, eligible engine IDs or exact engine/model search constraints.

## ProcessInvocation — first executor variant

LCR1 implements one concrete invocation variant:

```text
ProcessInvocation
    stable candidate identity
    executable
    arguments
    bounded stdin payload
    optional opaque target identity
```

A `ProcessInvocation` represents **one physical attempt**.

Kernel launches a fresh operating-system process for that attempt, supplies bounded stdin, captures bounded stdout as the result, captures bounded stderr for technical failure evidence, observes exit status and enforces attempt timeout/cancellation.

It does not create warm workers, model residency, process reuse, model loading or a long-lived provider host.

The process executor is a physical mechanism. It is not a Module, provider, model, inference-engine ontology or generic claim that all future inference is a process. LCR2 may add another real invocation variant, such as HTTP(S), behind the same narrow executor seam.

Having a process executor also does not turn arbitrary Module Operations into Kernel Work. Kernel Work remains the shared physical inference responsibility produced by the semantic reasoning-to-physical bridge.

## Kernel responsibilities

Kernel owns:

- durable physical Work identity/lifecycle;
- eligible execution time and urgency;
- deadline and per-attempt timeout;
- bounded local physical concurrency;
- physical attempts;
- deterministic routing only among supplied candidates;
- technical execution and failure evidence;
- cancellation mechanics;
- retry mechanics under explicit retry-safety semantics;
- durable result retention;
- restart recovery.

Kernel does not own:

- inference-target discovery/configuration;
- provider/model catalogue or matching;
- semantic effort/capability interpretation;
- model/runtime warmness;
- generic RAM/VRAM declarations for engines;
- provider/runtime implementations.

## Retry and unknown completion

Retry count alone is not enough to justify repeating a physical invocation.

LCR1 distinguishes:

```text
NEVER
    no automatic retry

DEFINITE_FAILURES
    retry may occur after a definitely observed technical failure
    but not after loss of completion certainty

INCLUDING_UNKNOWN_COMPLETION
    retry may also occur after Kernel restart interrupted observation
    because the submitting semantic side explicitly declared that repetition safe/idempotent
```

If Kernel restarts while an attempt was `RUNNING`, it cannot infer that the physical effect definitely failed. The attempt is recorded as `UNKNOWN_COMPLETION`.

Unless retry-after-unknown was explicitly declared safe and another attempt remains, Work becomes terminal `UNKNOWN_COMPLETION` rather than being silently translated into `FAILED` or blindly repeated.

This invariant is mandatory:

> **Kernel must never translate “I lost certainty about an attempt” into “that attempt definitely failed and is safe to repeat.”**

## Persistence split

Kernel persists only technical lifecycle.

Semantic persistence belongs above the boundary and may include the reasoning request, context, origin/correlation and continuation.

Kernel persistence contains only physical Work identity, supplied concrete candidates, scheduling state, attempts, retry/cancel state, selected supplied candidate, technical failure facts and terminal physical result.

Kernel remains correct if Runtime and all Module processes disappear while physical Work is queued or executing.

## Local IPC

The native Kernel keeps an independent lifetime and uses local-only IPC:

- Unix-domain sockets on Unix-like systems;
- local Windows named pipes on Windows.

LCR1 introduces no localhost HTTP/TCP requirement.

## Replaceability and experimentation

The Kernel does not use the semantic SDK, but its real physical responsibilities should remain bounded enough for Owner experiments.

The stable seam is the concrete physical invocation plus its executor. Do not generalize this into a provider SPI, adapter marketplace, generic RPC framework or universal job system before another concrete executor requires it.

A future physical-routing experiment may reason over physical facts Kernel actually owns, but it may still select only from the already-approved candidate set supplied from above.

## Current implementation

LCR1 preserves the valuable Lane C substrate:

- native C++ Kernel executable;
- independent lifetime;
- versioned framing;
- Unix-domain socket / Windows named-pipe IPC;
- SQLite durable Work and attempt lifecycle;
- file-backed bounded input/result retention;
- scheduling, deadlines, timeout, cancellation and restart recovery;
- Java `madre-kernel-client` physical boundary.

The rejected Lane C engine/worker ownership model, llama.cpp worker, model downloads and inference-runtime-specific Kernel build integration are not part of the current architecture.

The semantic SDK/Module and Runtime layers described by the whole-system architecture are still not implemented by LCR1.
