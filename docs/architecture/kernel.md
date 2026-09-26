# MADRE Kernel — current physical architecture

This document describes the durable physical inference-execution subsystem implemented by Lane C after the LCR1 ownership correction, LCR2 external-inference boundary completion and LCR3 physical-substrate closure.

For whole-system semantics and the semantic/physical boundary, see `docs/architecture/mid-level-architecture.md`. For SPIRA, see `docs/architecture/security-algebra.md`.

## Purpose

The Kernel exists so semantic MADRE does not have to keep a process alive merely to carry already-decided physical inference Work to completion.

The invariant remains:

> **Kernel does not own the intelligence environment. Kernel owns durable physical execution of inference choices already made above it.**

Inference-target configuration, provider/model/service/executable meaning, semantic reasoning intent and the complete set of acceptable physical destinations belong above Kernel.

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

The Kernel must not know Module, Agent, Operation semantics, Material, ReasoningRequest, SPIRA, CORE, Skill, Workflow, WorkPlan semantics, semantic continuation, semantic persistence, the Owner inference-target catalogue, or semantic effort/capability requirements.

If Kernel code needs one of those concepts, the semantic/physical boundary has drifted.

## Protocol v4 physical Work

Protocol v4 makes each concrete candidate complete with respect to its own request bytes.

A `WorkRequest` contains:

```text
candidates
urgency / eligibility
deadline / attempt timeout
retry policy
```

There is no Work-global inference payload.

The Java physical algebra is deliberately small:

```text
ConcretePhysicalInvocation
    ├── ProcessInvocation
    └── HttpInvocation
```

These are physical mechanisms, not provider/model types and not a registry of inference engines.

Each candidate has a stable invocation ID, optional opaque target identity and its own bounded request payload. The v4 submission frame carries candidate payload slices as one bounded transport frame; Kernel immediately materializes each slice into that candidate's own retained payload file. The complete submitted candidate request material is bounded to 1 MiB per Work submission, and each retained result body is also bounded to 1 MiB.

The supplied candidate list is the complete acceptable set. One candidate is exact. Multiple candidates mean the semantic side has already approved every member as a physical realization of the same selected inference choice. Kernel may choose only among that set for factual physical dispatchability. It does not compare reasoning quality, model capability, semantic effort, cost preference, provider meaning or application/domain semantics, and it does not synthesize a URI, executable, provider, model or replacement target.

## ProcessInvocation

`ProcessInvocation` represents one fresh operating-system process attempt:

```text
ProcessInvocation
    invocation ID
    executable
    arguments
    candidate-specific stdin bytes
    optional opaque target identity
```

Kernel may skip a supplied process candidate when its executable is physically unavailable. When selected, Kernel starts a fresh process, writes that candidate's exact stdin, captures bounded stdout/stderr, observes exit status, and enforces cancellation, deadline and attempt timeout.

There are no warm workers, model residency, model loading, provider hosts or process pools in this executor.

## HttpInvocation

`HttpInvocation` is a generic concrete HTTP(S) POST attempt:

```text
HttpInvocation
    invocation ID
    exact http/https URI
    bounded candidate-specific body bytes
    explicit request headers
    optional opaque target identity
```

Kernel does not understand the body schema. Provider-specific JSON remains opaque bytes supplied by the semantic-to-physical implementation above Kernel.

The native transport is **libcurl**. Production Kernel code:

- accepts only `http` and `https` target schemes;
- performs POST;
- retains a bounded response body;
- uses normal TLS certificate and hostname verification;
- does not follow redirects;
- enforces deadline / attempt timeout;
- supports local cancellation;
- records factual HTTP status when a response was observed.

Kernel is not a generic browser, REST framework or provider adapter system.

## HTTP headers and late-bound credentials

The public physical API makes persistence intent explicit.

`LiteralHttpHeader` contains a header value that is deliberately durable Work data. It is suitable for non-secret facts such as `Content-Type`. Kernel does not infer secrecy from a header name.

`EnvironmentHttpHeader` contains only durable binding metadata:

```text
header name
environment-variable name
non-secret prefix
non-secret suffix
```

The environment-variable value is resolved immediately before the physical HTTP attempt. The resolved value is used only in ephemeral memory to construct the outgoing header. It is not written to SQLite, candidate payload files, result files, ordinary Kernel logs, `WorkInspection` or technical failure strings.

After resolution and prefix/suffix construction, the final header value is rejected if it contains CR or LF before it is handed to libcurl. The rejection reports only a constant technical failure and never echoes the resolved value.

A missing environment binding is a physical dispatchability fact. Kernel may skip that supplied candidate and choose another supplied dispatchable candidate. It may not invent another destination.

This mechanism is deliberately only a small late-binding facility. Kernel does not implement secret storage or secret management.

## Candidate containment and physical routing

Candidate routing remains intentionally small and deterministic.

For each queued Work, Kernel checks the supplied candidates in order and may use only physical facts it owns, currently including:

- process executable availability;
- HTTP late-bound environment bindings being available;
- supported concrete invocation kind.

The selected attempt records the exact supplied invocation ID, kind and optional opaque target identity.

No provider/model catalogue, capability interpretation, model substitution, URI mutation or target discovery exists below the boundary.

## Honest HTTP completion certainty

HTTP creates cases where local disconnection does not prove the remote outcome. LCR2 therefore treats certainty conservatively.

### Definitely failed before meaningful remote submission

Examples include DNS/connect/TLS failure before libcurl reports request bytes as sent, or timeout/cancellation before submission. These are definite physical outcomes.

A definite failure may be retried only if the submitted retry policy permits definite-failure retries and attempt/deadline budget remains.

### Definite HTTP response observed

A received HTTP status is a factual remote observation.

- HTTP 2xx with a complete bounded response body is `SUCCEEDED`.
- A received non-2xx status is a definite technical `FAILED` outcome with the status exposed for inspection.
- Kernel does not interpret provider business semantics in the response body.

### Completion unknown

If request bytes may have reached the remote target but Kernel loses the response, completion is unknown.

Examples include transport loss after request transmission, attempt timeout or deadline expiration after transmission, and Owner cancellation after transmission. These become `UNKNOWN_COMPLETION`. Closing the local connection is not represented as confirmed remote cancellation or failure.

If Kernel disappears while any attempt is active, restart recovery also records `UNKNOWN_COMPLETION`.

## Retry and cancellation semantics

The current built-in physical retry policy is:

```text
NEVER
    no automatic retry

DEFINITE_FAILURES
    retry may occur after a definite technical failure
    never after UNKNOWN_COMPLETION

INCLUDING_UNKNOWN_COMPLETION
    retry may also occur after UNKNOWN_COMPLETION
    because the submitting side explicitly declared repetition acceptable
```

All retries remain bounded by `maxAttempts`, retry delay and Work deadline. Retry delay is scheduling eligibility for the next physical attempt, not a semantic inference judgment.

Owner-requested cancellation is stronger than retry permission. If cancellation is requested while an HTTP request may already have reached the target, Work becomes terminal `UNKNOWN_COMPLETION` and is not automatically retried even under `INCLUDING_UNKNOWN_COMPLETION`. Deadline exhaustion never creates another attempt.

This policy is a useful current physical strategy, not an immutable inference semantic. A future execution strategy above or around the physical substrate may replace or extend it without requiring provider/model logic in the Kernel.

## Scheduler semantics after LCR3 audit

The current scheduler has no worker inventory, warm-model ownership or per-engine resource reservation. Its admission model is intentionally smaller:

- one configured global bound on simultaneously active physical attempts;
- only Work whose `eligible_at` and `next_attempt_at` are due is considered;
- expired queued deadlines are failed without dispatch;
- among eligible queued Work, urgency orders `INTERACTIVE`, `NORMAL`, then `BACKGROUND`, with creation order as the deterministic tie-breaker;
- candidate selection is then restricted to the submitted physical alternatives and factual dispatchability;
- cancellation removes queued Work or requests termination of running Work.

Therefore the historical resource-blocked head-of-line defect from the removed worker/resource architecture is not present in this scheduler. Future-eligible or retry-delayed Work is excluded by the queue predicate and cannot occupy the selected queue head. A candidate set with no currently supported/available physical realization fails as `NO_DISPATCHABLE_CANDIDATE`; there is no hidden resource-wait state that can indefinitely block later Work.

LCR3 adds deterministic acceptance for global capacity, urgency, future eligibility, queued deadline expiry and retry-delay bypass rather than inventing a replacement resource scheduler.

## Physical inspection

`WorkInspection` exposes small factual physical information:

- Work state;
- attempt count;
- whether retained payload ownership has been released;
- latest/current attempt number;
- concrete invocation ID;
- invocation kind (`PROCESS` or `HTTP`);
- optional opaque target identity;
- attempt state;
- start/end time where known;
- process exit code where relevant;
- HTTP response status where relevant;
- technical failure or uncertainty fact.

It does not expose provider/model semantics or resolved credential values.

The store retains full attempt rows internally; the public client exposes the bounded latest/current attempt view rather than introducing pagination/history infrastructure.

## Durable payload retention and terminal release

Physical Work must survive Runtime/Module process disappearance, so Kernel may durably retain request bytes while the Work requires recovery. This includes provider-shaped HTTP request bodies containing prompt/context bytes after semantic-to-physical translation. Kernel does not semantically understand or classify those bytes.

Late-bound credential values are different: they are not part of durable Work and are never persisted by Kernel.

Every terminal Work state supports `release`:

```text
SUCCEEDED
FAILED
CANCELLED
UNKNOWN_COMPLETION
```

`release` is the explicit end of retained physical payload ownership. Kernel performs ordinary deletion of every candidate request payload and any retained result body while keeping minimal Work/attempt lifecycle metadata for truthful inspection. Release is idempotent: repeating it on terminal Work succeeds, and an already-missing candidate/result file is treated as already released content rather than making the Work unreleasable. After release, `result()` does not return prior result content.

No secure-erasure guarantee is claimed.

## Persistence split

Semantic persistence remains above the Kernel boundary and may contain reasoning context, semantic request, origin/correlation and continuation.

Kernel persists only physical lifecycle:

- Work identity and timing/retry facts;
- the complete submitted concrete candidate set;
- candidate-specific retained request payload-file paths;
- durable non-secret HTTP header facts and late-binding references;
- attempts and factual physical outcomes;
- cancellation state;
- technical failure/uncertainty facts;
- retained result path;
- terminal release state.

Kernel remains correct if Runtime and all Module processes disappear while physical Work is queued or executing.

## Local IPC and compatibility

The native Kernel keeps an independent lifetime and uses local-only IPC:

- Unix-domain sockets on Unix-like systems;
- local Windows named pipes on Windows.

Connection servicing is isolated per accepted local client. The accept loop continues while another client is reading an incomplete bounded frame, so one stalled local caller cannot freeze unrelated control-plane callers. This is deliberately a small concurrency boundary around the existing local framed protocol, not a generic server framework.

LCR3 retains Kernel protocol **v4** and SQLite schema/user version **4** because the public wire and durable schema shapes did not need to change. Development protocol-v3 databases remain rejected rather than migrated or adapted.

## Typed physical extension seam

The native physical algebra remains:

```text
ConcretePhysicalInvocationSpec =
    variant<ProcessInvocationSpec, HttpInvocationSpec>
```

A future real physical mechanism has a direct typed path: add its invocation representation, executor, bounded persistence/serialization support and dispatch integration. Scheduler, Work lifecycle and semantic MADRE continue to operate over physical Work and do not need conceptual redesign.

There is intentionally no dynamic executor registry, plugin loading, executor marketplace or arbitrary stringly-typed invocation map. Process and HTTP are the working built-ins required now, not a claim that all future inference ecosystems reduce to those two mechanisms.

## Historical Issue #63 disposition

Issue #63 was written against an earlier Lane C implementation that owned an engine inventory, warm workers and resource reservations. LCR1 removed that model rather than repairing it.

Still applicable to the current physical substrate:

- factual physical inspection at the Java boundary — implemented in LCR2;
- explicit terminal payload ownership/release — implemented in LCR2 and hardened/idempotent in LCR3;
- isolation of stalled local IPC clients — implemented in LCR3;
- the general requirement that queued physical Work not head-of-line block unrelated eligible Work — re-audited against the current scheduler and covered by current scheduling acceptance.

Obsolete because their owning architecture no longer exists:

- warm worker RAM/VRAM ownership accounting;
- warm-worker idle termination/resource release;
- CPU-vs-resident-model resource leasing;
- engine selection before resource executability;
- warm-engine preference;
- per-engine capacity/resource-blocked dispatch states.

Those findings must not be used to resurrect an engine catalogue, MADRE-managed worker ontology or resource-reservation subsystem unless a future concrete physical mechanism creates a new current need.
