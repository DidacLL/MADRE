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

An **EffectProfile** represents one bounded consequential execution variant of an Operation. It carries that variant's Risk and Autonomy. Reasoning computation is not itself a consequential external effect and does not justify an EffectProfile by itself.

A **Material** is a typed Module-owned value with nominal identity, content type, payload and Sensitivity. Adapting information creates new Material with a new identity and explicit Sensitivity; the source remains unchanged.

A **ReasoningCapability** is one executable reasoning mechanism. It exposes only reasoning-contract, receiving-Privacy, placement/latency, resource and availability facts needed by Kernel. It accepts a nominal `ReasoningComputation<R>` and returns its result. It knows nothing about Modules, Agents, Workflows, Operations, Material or semantic continuation.

A **reasoning adapter** is an independently installable JVM artifact that provides one or more configured `ReasoningCapability` instances through the public reasoning-adapter SPI. An adapter may materialize zero, one or many mechanism instances. Artifact installation does not imply mechanism enablement.

**Kernel** owns the shared runtime pieces that genuinely need central coordination: live executable Module registration and host invocation boundaries, optional CORE-role lookup, reasoning-mechanism selection, reasoning resources, immediate/durable reasoning, reasoning retry/cancellation/result delivery and ordinary runtime logging.

The **SDK** supplies the strongly typed construction model and responsibility-specific ports, including the public executable Module provider/configuration boundary. Domain objects are programmed as Java objects; JSON is only a boundary representation.

Modules and reasoning mechanisms are distinct installation concepts. A Module owns application/domain semantics. A ReasoningCapability realizes one reasoning computation mechanism. They use separate installation directories, identities and service-provider contracts. CORE designation has no relation to reasoning installation or selection privilege.

Search, files, databases, devices and other application I/O do not become Kernel capabilities merely because they are external. SearXNG is presently a reusable Java client, not a Kernel reasoning mechanism and not a shipped standalone WebSearch Module.

## Module installation configuration

Executable Modules are discovered as ordinary JVM JARs exposing `ModuleProvider`. Each provider declares its canonical `ModuleId` before materialization and receives an immutable `ModuleProviderConfiguration` scoped to that same identity when `create` is called.

The application owns only generic extraction, scoping and delivery. Owner properties use:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The brackets delimit the identity structurally. Valid `ModuleId` values cannot contain `[` or `]`, while dots, dashes and underscores are valid identity characters, so configuration is matched against the exact canonical identity rather than split on punctuation or inferred from provider class names, JAR names, discovery order, shipped status or CORE assignment.

`madre-app` has no table or parser for Module-specific keys. The provider/Module artifact owns interpretation, validation, defaults and conversion to typed settings. Omitted configuration is delivered as an empty configuration for that exact Module. An explicit malformed Module setting is a startup error rather than a fallback. Configuration for an identity that is not installed is also rejected rather than silently ignored.

Provider identity is part of the installation invariant. Duplicate providers for one canonical `ModuleId` are rejected before materialization; a provider that materializes a different Module identity is rejected before registration. All providers are materialized and validated before any Module is registered, and a later registration failure rolls back earlier registrations. A failed startup therefore does not leave an accidentally reachable partial Module installation.

Module configuration is independent of `roles.core` and `interaction.*`. CORE creates no configuration privilege, and the application presentation binding neither supplies nor overrides Module-owned settings.

The shipped owner-interaction Module uses this same public boundary for its existing reasoning limits, timeouts, retry behavior and reasoning preferences. Omitting its `modules.config[...]` entries preserves `OwnerInteractionSettings.defaults()`.

## Reasoning installation

Reasoning adapters are ordinary JVM JARs discovered from the configured reasoning installation directory with Java class-loading and service-provider APIs. Installed distributions use a sibling `reasoning/` directory by default; `reasoning.directory` may explicitly override it.

`madre-app` knows only this generic installation boundary and the read-only `reasoning.*` owner configuration namespace. It has no concrete llama.cpp, OpenAI-compatible or future-provider factory/configuration branch. Provider-specific parsing belongs to each discovered adapter.

The shipped llama.cpp AF_UNIX adapter, explicit llama.cpp loopback-HTTP compatibility adapter and OpenAI-compatible adapter are packaged into the same reasoning installation directory and discovered through the same provider mechanism as an independently supplied adapter. Bundled placement grants no privilege or selection preference.

A provider must explicitly materialize configured mechanisms. Disabled or unconfigured instances materialize nothing. Privacy is explicit owner/provider configuration and is never inferred from locality, endpoint or transport. Invalid enabled configuration is a startup error rather than a semantic fallback.

MADRE can boot when the reasoning directory does not exist, is empty, contains adapters with no enabled mechanisms, or no reasoning mechanism is configured at all. Modules that do not request reasoning remain fully usable in that state.

## Owner-local and PUBLIC invocation

MADRE distinguishes the owner using their local installation from an external/public receiver.

**Owner-local invocation** is a host/application boundary over an exact installed externally callable Operation. `OwnerModuleInvoker.invokeOwner` accepts a real canonical `OperationCall`, so input reach, exact EffectProfile selection where applicable, causal Integrity composition and output-contract validation remain structural. It executes the Module-owned Operation and returns the declared Material at the Sensitivity created by that Module. It does not apply `PublicResultTransformer`, does not lower Sensitivity, and derives no Privacy from localhost, process placement, class-loader placement, installation origin or CORE assignment.

**PUBLIC invocation** is the external/public receiver boundary. `ModuleInvoker.invokePublic` executes the exact installed `PUBLIC` Operation and then requires the Module-owned `PublicResultTransformer` to produce new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`. Raw sensitive internal Material cannot cross this path.

Owner-local invocation does not create a third Operation visibility. Both host receiver boundaries address Operations declared `PUBLIC`; `PRIVATE` remains Module-internal.

The owner-local port is not supplied through `ModuleContext`. Installed Modules receive only a facade implementing the public cross-Module `ModuleInvoker`, plus the ordinary public directory and reasoning port. The concrete live registry is not leaked through those context objects. Therefore installing two Modules in the same process does not give either one the ability to invoke the other's owner-local boundary.

## Local interaction presentation

The local text console is a replaceable `madre-app` presentation adapter, not a universal interaction SDK, assistant loop, conversation protocol, router or Kernel abstraction.

An installation may configure the console against one ordinary installed Module through `interaction.*`. The application resolves those nominal identities only after normal Module discovery and validates the exact canonical declarations it will rely on: the Module and configured Operations exist; configured Operations are `PUBLIC`; configured input Material types are Module-owned and accepted by those Operations; console input and every declared possible output are text; generic EffectProfile selection is unambiguous; and configured ordinary Sensitivities can reach the corresponding receiving boundaries. Malformed or structurally incompatible interaction configuration is a startup error. With no `interaction.*` configuration the generic console remains valid.

The current shipped example uses:

```text
interaction.module=io.github.didacll.madre.owner-interaction
interaction.default-operation=fast-lane
interaction.standard-operation=standard-prompt
interaction.prompt-material-type=owner-prompt
interaction.default-sensitivity=S5
interaction.updates-operation=collect-background
interaction.updates-material-type=background-collection-request
interaction.updates-payload=collect
interaction.updates-sensitivity=S1
```

These names are installation policy in the example configuration. `madre-app` Java code does not import the concrete owner-interaction implementation or hard-code its Module, Material, Operation or EffectProfile identities. A different structurally compatible ordinary installed Module can satisfy the same presentation binding without recompiling the application.

When configured, ordinary non-command console text constructs the same exact canonical `OperationCall` used by generic owner invocation and invokes the configured default Operation through the owner-local boundary. `/standard <text>` similarly invokes the configured standard Operation owner-locally. `/updates` invokes only the configured Module-specific collection Operation and renders the returned Module Material; the application does not inspect Kernel reasoning output or own delayed semantic interpretation. No background polling, callback, event bus or destructive availability probe is introduced.

The prompt Sensitivity is explicit installation/session state rather than inferred classification. `interaction.default-sensitivity` supplies the initial ordinary value and `/sensitivity S1..S5` can change it for the current console session. `SYSTEM_RESERVED` is unavailable for owner input. Owner-local output is displayed together with the Sensitivity actually created by the Module; rendering an S5 answer locally does not make it S1 or PUBLIC.

Ordinary text never routes to PUBLIC invocation. `/invoke-public` remains the explicit external/public boundary and the legacy `/invoke` alias remains PUBLIC. `/invoke-owner` and `/modules` remain generic diagnostic/application paths for any installed Module.

The presentation binding is independent of CORE. `interaction.module` does not derive authority from `roles.core`, and CORE does not grant presentation authority. The same binding is valid with CORE absent, assigned to the interaction Module, assigned to another installed Module or unresolved.

## CORE

CORE is an optional installation role containing one ordinary installed `ModuleId`.

MADRE ships a default CORE-capable owner-interaction Module, but CORE designation changes no type, algebraic value, visibility, invocation authority, class-loader treatment, reasoning selection, scheduling privilege or local-presentation authority. MADRE can also boot with no CORE assignment or with a configured CORE Module that is not installed.

The shipped owner-interaction Module owns ordinary interaction behavior. Its current bounded Operations are:

- `standard-prompt`: immediate reasoning and response-Material creation. It has no EffectProfile because that behavior has no consequential external/domain effect merely from performing reasoning.
- `fast-lane`: low-latency foreground reasoning plus durable background reasoning and Module-owned pending-state persistence. Its exact `durable-background-write` EffectProfile is `Risk.WRITE` / `Autonomy.AUTONOMOUS`; the write/persistence consequence, not inference, justifies the profile.
- `collect-background`: Module-specific interpretation of completed durable reasoning, acknowledgement of the Kernel work and cleanup of completed pending semantic state. Its exact `acknowledge-completed-background` profile is `Risk.DELETE` / `Autonomy.LIVE_INTERACTION`.

Fast lane is Module behavior, not a Kernel lane, model command or privileged scheduler path. The collection Operation is Module-specific and does not generalize semantic continuation into Kernel callbacks or a scheduler language. The convenient console reaches these behaviors only through the configured generic owner-local invocation machinery.

## Security Algebra

MADRE has five different ordered carriers:

- **Sensitivity**: confidentiality consequence of exposing information.
- **Privacy**: confidentiality boundary of a receiver.
- **Integrity**: bounded responsibility of a non-user causal participant.
- **Risk**: consequence of one Operation execution variant.
- **Autonomy**: how much of that same variant remains machine-executed.

The exact values are:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

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
     or I5 when there are none
```

Only the exact EffectProfile participates. A host caller never manufactures arbitrary Integrity values to make a call pass; it supplies only actual non-user causal participants. When there are none, the existing algebra uses I5.

`Risk` is not propagated into reasoning requests. A reasoning mechanism supplies computation, not the external effect represented by an Operation's Risk, so reasoning-mechanism selection does not use Risk or action-realizer Integrity.

`Privacy.UNKNOWN` is explicit P2 for an applicable third-party boundary outside stronger owner-controlled boundaries. It is not missing data and is never inferred from endpoint, provider, process placement or locality. There is no OWNER Privacy value or trusted-user Integrity shortcut.

## Reasoning execution

A Module constructs a nominal reasoning computation from a valid bounded `OperationCall` and submits a `ReasoningRequest` through `ReasoningService`. The request derives the originating Module and carried Sensitivity from that call and contains ordinary reasoning controls such as immediate/durable mode, priority, eligibility, timeout, cancellation, retry and typed location/latency preferences.

It contains no Material identity/type, Agent, Workflow, Operation identity, concrete reasoning-mechanism identity, semantic continuation or future output Material. It also contains no Operation Risk.

Kernel selects only among registered reasoning mechanisms that implement the computation contract. Selection composes the request's carried Sensitivity with each mechanism's explicit receiving Privacy, then applies availability, resource capacity, typed location/latency preferences and deterministic ordering.

A non-composable or unavailable reasoning mechanism is simply unreachable for that request. Immediate reasoning fails if nothing can run; durable reasoning follows its ordinary timing/retry lifecycle.

Kernel invokes the selected mechanism with only the reasoning computation and execution mechanics. The result is not Material. The originating Module interprets it and may create new Material, update state, present UI, invoke another bounded Operation or stop.

## Live Module reachability

Executable Modules are installed as ordinary JVM JARs that provide `ModuleProvider` using Java's service-provider mechanism. A provider first declares the canonical `ModuleId` by which installation configuration is scoped, then creates a `ModuleInstance` from `ModuleContext` plus immutable `ModuleProviderConfiguration`: one canonical `ModuleDefinition` plus exact executable bindings for every declared Operation.

The live registry is rebuilt at boot. Public cross-Module callers discover declarations and invoke exact installed `PUBLIC` Operations through `ModuleInvoker` without depending on the concrete Module implementation class. The host/application can additionally use `OwnerModuleInvoker` for owner-local invocation; this port is deliberately not Module-facing.

`OperationBinding.invoke` validates Module-created output against the declared type, owner and maximum Sensitivity before any receiver-specific boundary is applied. `invokePublic` preserves that internal validation and then performs and validates the mandatory semantic public transformation.

Registry membership, Module configuration and CORE assignment confer no privilege.

## Persistence and external mechanics

A Module owns semantic/domain persistence.

Kernel SQLite stores only durable reasoning-runtime state: opaque serialized reasoning input, originating Module identity for delivery, stable reasoning-contract identity, scheduling/attempt/cancellation state and opaque result bytes until collection/acknowledgement/retention cleanup. Persisted work does not depend on a concrete adapter implementation class name; after restart it becomes executable when a compatible computation contract/mechanism is registered again.

The owner-interaction Module separately persists only the semantic association it needs for outstanding fast-lane work. On restart, Kernel recovers the opaque reasoning work while the Module reloads its own pending state. The Module later interprets the completed result, creates Module Material, acknowledges the work and cleans up its own state. The console's `/updates` command only invokes that configured bounded Module Operation; it never performs interpretation itself.

Kernel does not treat queued bytes as Module knowledge.

Provider accounts, credentials, authentication, authorization, permissions, roles and externally prepared sessions remain outside the MADRE domain model.
