# MADRE

MADRE is personal software for one owner. It provides a modular local environment for owner-installed applications that may use local or external reasoning while keeping information reach explicit and structural.

MADRE is not a hosted AI platform and does not attempt to protect the owner from software the owner deliberately installs. The owner can install, replace, configure or remove Modules and reasoning mechanisms independently.

## Host platforms

Windows and Linux are first-class hosts for the same MADRE application, Kernel, SDK, Module installation mechanism, reasoning-mechanism installation mechanism and persistence model. Windows is not a compatibility port of a Unix implementation, and hosted Linux CI is not a product architecture dependency.

A concrete reasoning transport may be platform-specific. That difference remains inside its adapter and does not justify separate Kernel, SDK, Module, Workflow or Security Algebra architectures.

## Building blocks

A **Module** is an owner-installed application or integration. It owns meaning, domain state, persistence, Material, transformations, Agents, Skills, interpretation, continuation, user experience and bounded Operations.

An **Agent** is a Module-owned intelligent actor. It has no universal loop or assistant behavior. Its available behavior comes from learned Skills, Agent-owned Workflows and Operations its Module exposes.

A **Skill** is reusable Module-provided ability, knowledge or instruction.

A **Workflow** is reusable Agent-owned semantic behavior. In the current minimal model it is an ordered sequence of Operations. It is not a Kernel scheduler language.

An **Operation** is one explicitly bounded piece of Module behavior. It accepts declared Material, may transform it, may request reasoning, may perform ordinary application I/O, interprets results, creates new Material and decides whether its Module continues.

An **EffectProfile** represents one bounded consequential execution variant of an Operation. It carries that variant's Risk and Autonomy.

A **Material** is a typed Module-owned value with nominal identity, content type, payload and Sensitivity. Adapting information creates new Material with a new identity and explicit Sensitivity; the source remains unchanged.

A **ReasoningCapability** is one executable reasoning mechanism. It exposes only reasoning-contract, receiving-Privacy, placement/latency, resource and availability facts needed by Kernel. It accepts a nominal `ReasoningComputation<R>` and returns its result. It knows nothing about Modules, Agents, Workflows, Operations, Material or semantic continuation.

A **reasoning adapter** is an independently installable JVM artifact that provides one or more configured `ReasoningCapability` instances through the public reasoning-adapter SPI. An adapter may materialize zero, one or many mechanism instances. Artifact installation does not imply mechanism enablement.

**Kernel** owns the shared runtime pieces that genuinely need central coordination: live executable Module registration/public invocation, optional CORE-role lookup, reasoning-mechanism selection, reasoning resources, immediate/durable reasoning, reasoning retry/cancellation/result delivery and ordinary runtime logging.

The **SDK** supplies the strongly typed construction model and Module-facing ports. Domain objects are programmed as Java objects; JSON is only a boundary representation.

Modules and reasoning mechanisms are distinct installation concepts. A Module owns application/domain semantics. A ReasoningCapability realizes one reasoning computation mechanism. They use separate installation directories, identities and service-provider contracts. CORE designation has no relation to reasoning installation or selection privilege.

Search, files, databases, devices and other application I/O do not become Kernel capabilities merely because they are external. SearXNG is presently a reusable Java client, not a Kernel reasoning mechanism and not a shipped standalone WebSearch Module.

## Reasoning installation

Reasoning adapters are ordinary JVM JARs discovered from the configured reasoning installation directory with Java class-loading and service-provider APIs. Installed distributions use a sibling `reasoning/` directory by default; `reasoning.directory` may explicitly override it.

`madre-app` knows only this generic installation boundary and the read-only `reasoning.*` owner configuration namespace. It has no concrete llama.cpp, OpenAI-compatible or future-provider factory/configuration branch. Provider-specific parsing belongs to each discovered adapter.

The shipped llama.cpp AF_UNIX adapter, explicit llama.cpp loopback-HTTP compatibility adapter and OpenAI-compatible adapter are packaged into the same reasoning installation directory and discovered through the same provider mechanism as an independently supplied adapter. Bundled placement grants no privilege or selection preference.

A provider must explicitly materialize configured mechanisms. Disabled or unconfigured instances materialize nothing. Privacy is explicit owner/provider configuration and is never inferred from locality, endpoint or transport. Invalid enabled configuration is a startup error rather than a semantic fallback.

MADRE can boot when the reasoning directory does not exist, is empty, contains adapters with no enabled mechanisms, or no reasoning mechanism is configured at all. Modules that do not request reasoning remain fully usable in that state.

## CORE

CORE is an optional installation role containing one ordinary installed `ModuleId`.

MADRE ships a default CORE-capable owner-interaction Module, but CORE designation changes no type, algebraic value, visibility, invocation authority, class-loader treatment or scheduling privilege. MADRE can also boot with no CORE assignment or with a configured CORE Module that is not installed.

The shipped owner-interaction Module owns ordinary interaction behavior, including bounded standard-prompt and fast-lane Operations. Standard prompt requests one reasoning computation and interprets the result as new Material. Fast lane combines low-latency foreground reasoning with durable background reasoning; the same Module later interprets the background result and decides whether any semantic continuation is useful.

Fast lane is Module behavior, not a Kernel lane, model command or privileged scheduler path.

## Security Algebra

MADRE has five different ordered carriers:

- **Sensitivity**: confidentiality consequence of exposing information.
- **Privacy**: confidentiality boundary of a receiver.
- **Integrity**: bounded responsibility of a non-user causal participant.
- **Risk**: consequence of one Operation execution variant.
- **Autonomy**: how much of that same variant remains machine-executed.

All use ranks 1 through 5 for ordinary values, but equal ranks across carriers are never interchangeable. Rank 0 is `SYSTEM_RESERVED` and is rejected by ordinary public objects where an ordinary value is required.

Intrinsic composition is:

```text
Sensitivity: maximum
Privacy:     minimum
Integrity:   minimum
```

Information reach for one attempted connection is:

```text
S = maximum Sensitivity of the information being carried
P = minimum Privacy of the receiving boundary

reachable iff S <= P
```

No decision object, permission, evidence record or history is created by composition.

For one EffectProfile:

```text
D = min(Risk, Autonomy)
D <= minimum Integrity of actual non-user causal participants
     or I5 when no such participant exists
```

Only the exact EffectProfile participates. `Risk` is not propagated into reasoning requests. A reasoning mechanism supplies computation, not the external effect represented by an Operation's Risk, so reasoning-mechanism selection does not use Risk or action-realizer Integrity.

`Privacy.UNKNOWN` is explicit P2 for an applicable third-party boundary outside stronger owner-controlled boundaries. It is not missing data and is never inferred from endpoint, provider, process placement or locality.

## Reasoning execution

A Module constructs a nominal reasoning computation from a valid bounded `OperationCall` and submits a `ReasoningRequest` through `ReasoningService`. The request derives the originating Module and carried Sensitivity from that call and contains ordinary reasoning controls such as immediate/durable mode, priority, eligibility, timeout, cancellation, retry and typed location/latency preferences.

It contains no Material identity/type, Agent, Workflow, Operation identity, concrete reasoning-mechanism identity, semantic continuation or future output Material. It also contains no Operation Risk.

Kernel selects only among registered reasoning mechanisms that implement the computation contract. Selection composes the request's carried Sensitivity with each mechanism's explicit receiving Privacy, then applies availability, resource capacity, typed location/latency preferences and deterministic ordering.

A non-composable or unavailable reasoning mechanism is simply unreachable for that request. Immediate reasoning fails if nothing can run; durable reasoning follows its ordinary timing/retry lifecycle.

Kernel invokes the selected mechanism with only the reasoning computation and execution mechanics. The result is not Material. The originating Module interprets it and may create new Material, update state, present UI, invoke another bounded Operation or stop.

## Live Module reachability

Executable Modules are installed as ordinary JVM JARs that provide `ModuleProvider` using Java's service-provider mechanism. A `ModuleProvider` creates a `ModuleInstance`: one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation.

The live registry is rebuilt at boot. Public callers discover declarations and invoke exact installed `PUBLIC` Operations through `ModuleInvoker` without depending on the concrete Module implementation class.

A public Operation binding must transform internal result Material before it crosses the external/public boundary. The result must be new declared Material with a new identity and a Sensitivity that can reach `Privacy.PUBLIC`. Semantic minimization remains Module behavior; runtime validation prevents bypass.

Registry membership and CORE assignment confer no privilege.

## Persistence and external mechanics

A Module owns semantic/domain persistence.

Kernel SQLite stores only durable reasoning-runtime state: opaque serialized reasoning input, originating Module identity for delivery, stable reasoning-contract identity, scheduling/attempt/cancellation state and opaque result bytes until collection/acknowledgement/retention cleanup. Persisted work does not depend on a concrete adapter implementation class name; after restart it becomes executable when a compatible computation contract/mechanism is registered again.

Kernel does not treat queued bytes as Module knowledge.

Provider accounts, credentials, authentication, authorization, permissions, roles and externally prepared sessions remain outside the MADRE domain model.
