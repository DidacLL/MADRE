# MADRE Kernel — current physical architecture

This document describes the durable physical inference-execution subsystem implemented by Lane C after the LCR1 ownership correction and LCR2 external-inference boundary completion.

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

The supplied candidate list is the complete acceptable set. One candidate is exact. Multiple candidates permit only physical routing among those supplied candidates. Kernel does not synthesize a URI, executable, provider, model or replacement target.

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

A missing environment binding is a physical dispatchability fact. Kernel may skip that supplied candidate and choose another supplied dispatchable candidate. It may not invent another destination.

LCR2 does not introduce a Kernel secret manager or Runtime/CORE secret configuration. Supplying the Kernel process environment belongs to later host/runtime installation mechanics.

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

The physical outcomes are distinguished as follows:

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

Examples include:

- transport loss after request transmission;
- attempt timeout after transmission;
- deadline expiration after transmission;
- Owner cancellation after transmission.

These become `UNKNOWN_COMPLETION`. Closing the local connection is not represented as confirmed remote cancellation or failure.

If Kernel disappears while any attempt is active, restart recovery also records `UNKNOWN_COMPLETION`, as in LCR1.

## Retry and cancellation semantics

The retry safety values remain:

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

All retries remain bounded by `maxAttempts`, retry delay and Work deadline.

Owner-requested cancellation is stronger than retry permission. If cancellation is requested while an HTTP request may already have reached the target, Work becomes terminal `UNKNOWN_COMPLETION` and is not automatically retried even under `INCLUDING_UNKNOWN_COMPLETION`.

Deadline exhaustion never creates another attempt.

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

The store retains full attempt rows internally; the public LCR2 client exposes the bounded latest/current attempt view rather than introducing pagination/history infrastructure.

## Durable payload retention and terminal release

Physical Work must survive Runtime/Module process disappearance, so Kernel may durably retain request bytes while the Work requires recovery.

This includes provider-shaped HTTP request bodies containing prompt/context bytes after semantic-to-physical translation. Kernel does not semantically understand or classify those bytes.

Late-bound credential values are different: they are not part of durable Work and are never persisted by Kernel.

Every terminal Work state supports `release`:

```text
SUCCEEDED
FAILED
CANCELLED
UNKNOWN_COMPLETION
```

`release` means the client is finished with retained physical content. Kernel then performs ordinary deletion of candidate request payload files and any retained result body while keeping minimal Work/attempt lifecycle metadata for truthful inspection.

LCR2 does not claim secure disk erasure.

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

LCR2 uses Kernel protocol **v4**. Development protocol v3 databases are rejected rather than migrated or adapted.

## Replaceability without provider framework drift

The real executor seam is now a direct C++ `std::variant` dispatch between `ProcessInvocationSpec` and `HttpInvocationSpec`.

There is no dynamic executor registry, provider SPI, provider adapter marketplace, engine registry, dependency-injection framework or generic plugin system.

`ProcessInvocation` and `HttpInvocation` are the concrete mechanisms currently required. They are not declared to be the final universal executor taxonomy.
