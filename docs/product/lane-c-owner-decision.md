# MADRE — Lane C Owner Decision: DRE and Physical Inference Architecture

Status: **accepted current Owner decision; implementation closure requires separate Owner/orchestrator audit and evidence**.

This document preserves the product decision and the reasons behind it so later implementation work cannot recover the wrong Lane C architecture from historical code or an intermediate correction.

It is not an implementation plan and does not make .NET, Microsoft.Extensions.AI or Microsoft Agent Framework part of MADRE's semantic identity. It records the accepted ownership model that implementations must serve.

Where an older repository document says semantic MADRE must completely select an exact provider/model/configuration or complete `ConcretePhysicalInvocation` candidate set before Kernel, this decision supersedes that statement. That concrete-invocation architecture was a useful correction after earlier worker/engine drift, but it over-corrected the semantic/physical boundary and is no longer the target architecture.

The later closure audit also corrected the opposite over-compression: Kernel Work cannot discard the Owner's exact physical choice or the semantically derived set of physically admissible capabilities, and capability configuration cannot expose only an ambiguous local/external permission. The final boundary preserves both directions without sending semantic concepts into Kernel.

## Why this correction exists

Lane C exposed three related failure modes.

The first implementation made one possible physical realization into MADRE architecture:

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

The corrective LCR1/LCR2 architecture then deliberately narrowed Kernel to durable execution of already-approved concrete process/HTTP invocations. That successfully removed workers, engine inventory, model lifecycle and llama.cpp ownership, but moved physical inference selection so far above Kernel that Kernel could no longer perform the inference-aware scheduling Delayed Reasoning Effort requires.

A later capability-aware Kernel restored meaningful physical DRE, but its first public request compressed semantic/Owner physical restrictions too far: it could express only effort, urgency, timing and a coarse local/external allowance. That meant semantic MADRE could neither preserve an exact Owner-selected physical target nor pass a derived eligible physical set while still allowing Kernel to choose within that set. At the same time, the capability side reused the same local/external enum as though a request permission were a factual execution-path description.

The accepted architecture keeps the ownership correction and meaningful DRE **while preserving the information journey and physical admissibility established above Kernel**.

## Fixed semantic/physical responsibility boundary

Semantic MADRE owns application and reasoning meaning. A Module/Agent can know facts such as:

- what reasoning is required;
- what Material actually participates;
- context and its relevant characteristics;
- desired reasoning depth/effort;
- urgency and deadline;
- acceptable delay;
- requested result characteristics;
- constraints derived from actual SPIRA composition;
- explicit Owner requirements/preferences;
- the actual information journey and which configured physical capabilities are admissible for this reasoning need.

Semantic MADRE reduces those facts into a **physical inference requirement**. The semantic `ReasoningRequest` itself does not cross into Kernel.

The downward physical requirement may contain an opaque set of eligible physical capability identities. Kernel does not know why another capability was excluded. A singleton eligible set preserves an exact Owner/semantic physical choice. A larger set preserves meaningful Kernel DRE within the physical space semantic MADRE has established as valid. If no capability-id restriction is needed, Kernel may choose among all capabilities satisfying the other physical constraints.

Kernel receives physical inference Work and owns physical consequences:

- durable Work identity/lifecycle;
- configured `InferenceCapability` knowledge;
- current capability state;
- physical observations/history;
- inference-aware scheduling;
- capability selection **inside the Work's physical admissibility restrictions**;
- physical effort/strategy choice where implemented;
- delay/defer/re-observation decisions;
- physically justified retry/escalation where a real physical strategy defines it;
- execution/cancellation/result/recovery lifecycle.

There is no semantic global scheduler and no Kernel ownership of Module/Agent continuation.

The reverse direction is also explicit: configured capabilities expose factual physical execution-path information upward so semantic MADRE and Owner tooling can understand where information will physically go. Kernel stores/exposes these facts; it does not turn them into SPIRA or application policy.

## DRE is the inference-aware physical scheduler

Kernel DRE is not a generic timer service plus an unrelated policy layer. Its meaningful questions include:

```text
run now or later?
which capability inside the admissible physical set is currently appropriate?
should known unavailability justify waiting/re-observation?
how much physical reasoning effort should be spent?
which current physical observations should affect choice?
one inference or a richer physical strategy when a real strategy exists?
```

Durable mechanics such as eligibility, deadlines, transactions and restart recovery exist underneath those decisions. They do not replace DRE.

There is no requirement for Kernel to schedule MADRE semantic Workflows, WorkPlans or Module application logic. A Module can have outstanding physical reasoning while semantic continuation remains its own responsibility.

## PhysicalInferenceWork

The first implemented physical request remains deliberately small:

```text
prepared input
requested physical effort
urgency
optional eligibility time
optional deadline
request-side allowed exposure: LocalOnly / ExternalAllowed
optional eligible physical capability IDs
```

The request-side exposure value is a **constraint on this Work**, not a factual description of any capability. `ExternalAllowed` means the Work may leave the local environment; it does not say that a selected capability actually does so.

The optional eligible capability set is deliberately opaque. It carries only physical identity. A singleton set is the exact-selection case; a larger set lets DRE choose within the semantically/Owner-established physical space.

A field belongs in the physical contract because an implemented DRE decision or the preservation of an actual Owner/semantic physical restriction consumes it, not because a generic AI platform might want it.

No Module, Agent, Operation, Material, SPIRA, CORE, semantic Workflow/WorkPlan or semantic continuation is smuggled into Work.

## InferenceCapability is a central Kernel concept

Kernel reasons about configured **InferenceCapabilities**, not provider brands, models, workers or framework classes.

A capability is a physical path through which MADRE can obtain intelligence. The same underlying model exposed with materially different configurations can be several capabilities. Different provider implementations may also be equivalent for a physical requirement.

Capability information preserves provenance rather than mutating one supposedly authoritative property record:

```text
InferenceCapability
    identity
    execution binding + version

CONFIGURED / DECLARED
    factual execution location
    factual destination
    optional factual route/intermediary description
    optional factual retention/history description
    supported physical effort/characteristics currently modeled
    Owner configuration/preferences

CURRENT
    Unknown / Unavailable / Available
    observation time

HISTORICAL / OBSERVED
    attempt identity/outcome
    latency scoped to the physical capability/binding/version that produced it
    other evidence only when an actual DRE consumer exists
```

`ExecutionLocation` is a factual capability property (`Local` or `External`). Destination identifies where the configured path actually sends/executes information. Route and retention/history descriptions are physical facts exposed for semantic/Owner inspection; Kernel need not interpret their provider-specific meaning.

A provider claim, an Owner declaration and a MADRE observation are different facts. Provenance must remain visible rather than collapsing them into one mutable truth.

The capability-side factual execution path and the Work-side allowed exposure are intentionally different contracts flowing in opposite directions. Do not reuse an `ExternalAllowed`-style permission as though it were factual capability location.

### Unknown is not Unavailable

A configured capability without a custom probe remains `Unknown`; absence of probe evidence is not unavailability. DRE prefers known-available admissible capabilities. If none are known available, an admissible configured `Unknown` capability may be tried. Actual execution can then provide current physical evidence.

A known-unavailable capability is not dispatched. Re-observation is automatic **when relevant pending Work creates demand**, rather than periodically probing every configured capability forever while Kernel is idle. Explicit refresh and useful startup observation remain valid technical actions.

A probe that exceeds its technical timeout produces `Unknown`, not fabricated `Unavailable`.

Startup observation is asynchronous and cannot hold Kernel availability hostage. A valid empty Kernel starts with zero capabilities and no configuration file.

## Kernel observation is physical evidence

Kernel can become better informed from physical inference history. This is part of DRE rather than incidental telemetry.

Current latency evidence is useful only when it corresponds to the current physical capability identity: capability id **and** binding identity/version. Reusing latency measured through an old binding/version after configuration changes is false provenance and is not permitted.

Durable attempt history remains the source evidence. Aggregate fields without an actual DRE consumer are not kept as public capability state merely because they are easy to count.

Kernel does not become a semantic answer judge. A physically successful result is not retried merely because Kernel believes the answer is weak or unsuitable for the user's application objective.

## Physical multi-stage reasoning remains legitimate, not preinstalled

DRE may eventually select richer physical inference strategies—multi-capability inference, structural validation, fan-out/comparison/synthesis—when a real product strategy requires them. That does not create a MADRE semantic Workflow.

Physical workflow machinery must not silently acquire application effects such as changing Module state, sending application email, committing domain/project state or invoking another Module's semantic Operation.

The previous `High + Background -> MAF two-stage` behavior is rejected as arbitrary validation strategy rather than MADRE product strategy. Its production workflow/checkpoint implementation, package dependency, state fields and dedicated validation suite are deleted. Git history is sufficient evidence that checkpoint machinery was explored.

This is not a permanent ban on MAF. MAF or another mechanism may implement a future actual physical strategy when a concrete product need earns that machinery. No checkpoint or strategy scaffolding is retained speculatively.

## Open physical bindings and first-party parity

MADRE must not need to predict every future inference environment. The physical construction surface preserves an intentionally open binding seam.

Useful realizations may include generic provider interoperability, OpenAI-compatible endpoints, protocol bindings, process/script bindings, A2A/remote intelligence and Owner-provided wrappers when concrete needs exist.

Hot loading, a connector marketplace, automatic package discovery and an elaborate defensive plugin sandbox are not first-version requirements.

The practical requirement is:

> Adding an unusual Owner-controlled inference environment should normally require configuration or a binding, not modifying Kernel architecture or provider-specific core source.

MADRE-provided inference conveniences use the same class of physical construction surface available to advanced Owners. There is no privileged first-party Kernel ontology or hidden lifecycle.

MEAI remains useful generic interoperability behind bindings.

## Durable truth and single ownership

MADRE keeps one authoritative `PhysicalInferenceWork` lifecycle in SQLite for current Lane C.

One process-lifetime Kernel owner exists per database, regardless of IPC path. A second Kernel must fail before it can unlink or replace a live endpoint or run another scheduler against the same authoritative database. Physical filesystem aliases must not create independent owners of the same database.

The local socket endpoint is independently protected: a live endpoint is never deleted. A stale filesystem socket may be removed only after it is established not to be live.

SQLite carries an explicit current schema identity. Databases produced by incompatible pre-release layouts fail early and clearly. There is no migration/compatibility machinery because this project has no requirement to preserve those pre-release physical schemas.

On startup current configured capability catalogue is reconciled to actual configuration. Removed capabilities disappear from selectable current configuration/state; historical attempts remain historical.

An interrupted active attempt recovers as `UnknownCompletion`. MADRE does not silently duplicate uncertain physical Work or rewrite uncertainty into definite failure. Terminal release preserves Work identity/history and physical metadata while clearing retained input/result payload.

## Scheduler shape and memory truth

DRE must preserve complete-set/no-head-starvation behavior without materialising every physical payload merely to schedule.

Eligible scheduling candidates contain scheduling metadata and physical admissibility only. Prepared input—potentially up to the physical payload bound—is loaded only after Work is actually claimed for execution.

Urgency/effort ordering belongs in explicit domain code, not enum ordinals or SQL policy. SQLite query mechanics do not own DRE policy. The scheduler is wake/deadline driven rather than fixed busy polling.

Capacity released by an attempt during a scheduler pass must be reconsidered against a fresh urgency ordering rather than reused later in a stale candidate traversal.

## Background failure truth

Kernel-owned background tasks are part of authoritative physical execution, not best-effort telemetry. Scheduler failures, capability-observation persistence failures and attempt completion/persistence failures are supervised.

If Kernel can no longer maintain authoritative physical truth, the host must become observably failed and terminate/fail rather than continue answering `Health = ok` as a zombie. A failed detached attempt must not leave durable Work silently `Running` forever; host failure plus restart recovery yields truthful `UnknownCompletion` when completion became unknowable.

Physical probe failure itself is not automatically fatal—an inference mechanism may legitimately be unavailable or unknown. Infrastructure failure while recording authoritative state is different and is fatal.

## Strict local IPC boundary

Lane C uses a small versioned local IPC contract over Unix-domain sockets on supported Windows/Linux targets. There is no TCP listener, web path/status vocabulary, configurable port or web-host dependency.

The protocol uses bounded length-prefixed UTF-8 JSON frames. One source owns protocol limits/defaults on each side and cross-language acceptance proves parity.

Connections are independent and short-lived. Active server client handlers are bounded, so frame bounds also imply bounded process exposure under stalled clients. The Java 21 client has a bounded technical call failure path against a stalled/bogus peer rather than blocking forever.

Protocol operation/error vocabulary is typed on the Java side rather than scattered string literals.

Malformed IPC does not silently acquire defaults: required operation/submit fields must be present, numeric enum encodings are rejected, and unknown properties are rejected where the contract is parsed. Physical eligible capability sets reject empty/blank/duplicate values when explicitly supplied.

Host configuration likewise rejects unknown properties, numeric enum encodings and missing required capability facts. A configured capability must provide truthful physical execution location and destination; optional route/retention facts cannot be empty. Programmatic/custom capability construction obeys the same physical contract. CLI parsing rejects unknown flags, duplicate flags, missing values and malformed values. Technical defaults have one intentional owner rather than being copied across layers.

## Process binding

The process binding remains shell-free and uses explicitly UTF-8 stdin/stdout/stderr. Non-ASCII physical input/result round-trip is a cross-platform acceptance requirement.

`IInferenceBinding` remains the open physical seam. Bindings receive only execution-relevant physical input rather than urgency, eligibility, deadlines or unrelated scheduling metadata. Common result validation, exception conversion and payload enforcement live in one binding execution responsibility.

## What survives from historical Lane C

Behavior survives when MADRE needs it, not because an old implementation shape deserves preservation:

- independent Kernel lifetime;
- one authoritative durable physical Work lifecycle;
- semantic/Owner-derived physical admissibility and exact-selection preservation;
- capability-aware DRE inside that admissible physical space;
- inspectable factual capability execution paths;
- bounded payloads and physical concurrency;
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
- discards an exact Owner physical choice or semantically derived eligible physical set before Work;
- lets DRE select a capability outside the Work's opaque physical admissibility;
- conflates request-side allowed exposure with factual capability execution location/destination;
- hides the configured physical destination/route/retention facts needed above Kernel to resolve the Owner's information journey;
- sends semantic MADRE objects into Kernel;
- treats `Unknown` as `Unavailable` because a custom probe is absent or times out;
- probes every capability forever without current physical demand;
- reuses physical observations from a replaced binding/version as though they described the current capability;
- makes startup depend on inference configuration/probe success;
- requires manual refresh forever for unavailable-capability recovery;
- hides policy in SQL, enum ordinals or magic polling intervals;
- materialises physical payloads merely to rank scheduling candidates;
- permits multiple Kernel schedulers to own one database;
- reports healthy after authoritative background infrastructure has faulted;
- accepts malformed protocol/configuration by silently defaulting required facts;
- reintroduces TCP/web infrastructure because it is familiar;
- retains validation-only strategy/checkpoint machinery without product need;
- privileges first-party integrations over Owner/custom bindings;
- starts SDK/Runtime work while correcting Lane C.

The target remains: **a small, truthful, durable, capability-aware physical inference Kernel that preserves the Owner's physical admissibility and information-journey intent without trying to own the Owner's intelligence environment.**

Implementation evidence is submitted for Owner/orchestrator audit. This product-decision document does not declare Lane C closed by itself.
