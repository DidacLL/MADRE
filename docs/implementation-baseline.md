# Implementation Baseline

This document records executable truth on the active Java 21 branch. Durable product meaning remains in `MADRE.md`; architecture responsibility rules remain in `docs/architecture/*`.

## Product/runtime baseline

MADRE is implemented as both an owner-installed local application and a public Module/reasoning experimentation platform. Windows and Linux packages contain the application, shipped artifacts and a bundled Java runtime. Mutable configuration/data/state live in owner-writable per-user locations; Kernel durable reasoning state and Module-owned state are separate resources.

The product can boot with no configured reasoning mechanism. Module installation and reasoning-provider installation are separate domains. CORE remains an optional ordinary Module role with no registration, invocation, Security Algebra or reasoning privilege.

The shipped first-run owner product selects the ordinary owner-interaction Module through `roles.core`. `interaction.*` now configures only the console/presentation entry Operations and Material details for that selected Module; it no longer carries a second Module identity. This remains 0.x presentation wiring rather than a universal UI or CORE protocol.

Ordinary console conversation enters exact executable Operation bindings explicitly marked as owner-interaction entry points. Those Operations are `PRIVATE`: owner interaction is no longer equated with generic Module-to-Module `PUBLIC` exposure. The host interaction port is not supplied through `ModuleContext`, CORE assignment does not create it, and a generic PRIVATE Operation does not become host-callable. Generic expert/debug owner invocation remains available separately and remains `PUBLIC`-only for compatibility.

The console host also polls the selected interaction Module's configured bounded collection Operation while the interaction surface is active. The Module still owns durable-work association, result interpretation, acknowledgement and the decision that a visible follow-up is useful; the host owns only presentation mechanics. `/updates` remains an explicit compatibility/debug command but is not required for the normal owner journey.

## Code-first Module SDK

The stable Java SDK has an explicit execution-side authoring model:

```text
Module
Agent                     optional
StatefulAgent<S>           optional Agent specialization
MaterialType<T>
Operation<I,O>
OperationBinding<I,O>
OperationCall<I,O>
```

A Java `Module` is the executable source of truth. It declares identity/version/purpose, Java Material bindings, optional foreign public Material references, optional Agents/Skills, and executable Operation bindings. `Module.definition()` derives the portable contract and `Module.instance()` derives the validated runtime assembly.

A Java `Agent` is an optional Module-owned actor. It defines no universal loop, planner, memory, prompt or execution context. If no stronger causal-integrity claim has been established, `Agent.integrity()` defaults to `Integrity.I1`; authors may explicitly declare stronger Integrity when justified.

`StatefulAgent<S>` is an optional OOP authoring base for an Agent that owns typed private state. It serializes reads/transitions and can commit one transition through a Module-owned persistence function before publishing the new in-memory state. It is not a portable memory schema, generic persistence service or second execution model: concrete state meaning remains Module/Agent-owned, and MADRE-arbitrated executions still occur through Operations and `OperationCall` values.

An `Operation<I,O>` is a generic bounded executable unit of Module logic. Its implementation may be pure computation, file/database/network access, a script/process call, reasoning-backed behavior, or any other Module-owned Java code. `OperationCall` is the current typed MADRE arbitration boundary: it validates accepted Material/Privacy and, when the Operation declares consequential variants, the selected EffectProfile against actual non-user causal Integrity. Output type/owner/maximum Sensitivity is validated before the result escapes the bounded call.

The current `OperationVisibility.PUBLIC/PRIVATE` is an installed-runtime exposure marker. It is not Material confidentiality, external publication, owner visibility or reasoning locality. A PRIVATE Operation may request local or remote reasoning; a PUBLIC Operation may request none.

`OperationBinding.ownerInteractionOperation(...)` is the smallest current executable host-entry distinction recovered from the real owner journey. It requires a PRIVATE `OperationDefinition` and marks only that exact executable binding as eligible for the selected owner-interaction surface. It is not encoded in the portable definition because this checkpoint has not established a language-neutral universal presentation taxonomy; it does not change Security Algebra or make unrelated PRIVATE Operations reachable.

Portable descriptions are separate from Java execution mechanics:

```text
ModuleDefinition
AgentDefinition
MaterialTypeDefinition
OperationDefinition
SkillDefinition
WorkflowDefinition
EffectProfile
```

`OperationDefinition` is non-generic and language-neutral. Java payload typing remains on `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>`. `MaterialTypeDefinition` contains nominal `MaterialTypeId` plus content type; Java `Class<T>` and `MaterialCodec<T>` remain on `MaterialType<T>`.

`ModuleDefinitionJsonCodec` serializes/deserializes format version 2 without a Java Material resolver. The old `MaterialTypeResolver` is removed.

`ModuleInstance` is not the ordinary authoring model. It is the validated runtime/adaptor assembly containing one portable `ModuleDefinition`, exact Java Material bindings and exact executable Operation bindings. It validates those relationships at construction, so registration receives a valid assembly rather than discovering malformed binding graphs later.

`Material` rejects `SYSTEM_RESERVED`, Java payload/type mismatch, and Material identity/type ownership mismatch at construction.

## Provider and owner configuration

`ModuleProvider` exposes:

```java
ModuleId moduleId();
default ModuleConfigurationDescriptor configurationDescriptor();
default ModuleProviderConfiguration validateConfiguration(
        ModuleProviderConfiguration configuration);
Module create(ModuleContext context,
        ModuleProviderConfiguration configuration);
```

`create(...)` returns the executable `Module`, not a separately assembled `ModuleInstance`. Host/testkit code projects and validates the runtime assembly.

The stable owner-facing configuration field kinds remain exactly `TEXT`, `INTEGER` and `CHOICE`. Provider code owns key meaning, parsing/defaults and validation. Configuration discovery/validation does not materialize the Module.

Raw compatibility persistence remains:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The host supports `modules list`, `inspect`, `configure`, local-file `install --replace`, and `uninstall [--purge-configuration]` without giving Module code artifact-management authority.

## Security and invocation baseline

The public Security Algebra remains unchanged. It governs MADRE-mediated values and composition; it is not an OS sandbox or general permission system for arbitrary installed Java code.

Current receiver/exposure paths are intentionally distinct:

- Module-to-Module: caller-bound `ModuleInvoker`, targets installed `PUBLIC` Operations, fixed `Privacy.MODULE`, no public result transformer;
- generic owner/debug: host-only `OwnerModuleInvoker`, targets a `PUBLIC` Operation and returns canonical valid Module Material unchanged;
- selected owner interaction: host-only `OwnerInteractionInvoker`, targets only exact PRIVATE bindings explicitly created as owner-interaction entries and executes the same `OperationCall` validation path;
- external/PUBLIC: host-only `PublicModuleInvoker`, targets a `PUBLIC` binding and requires Module-owned transformation to new PUBLIC-capable Material;
- ordinary `PRIVATE`: unavailable to those generic installed invocation paths.

The durable principles are caller-bound Module composition, explicit receiver Privacy, explicit public transformation, explicit product entry ownership and Module encapsulation. Owner interaction does not imply PUBLIC Module composition and CORE designation does not grant generic PRIVATE authority.

A Module-to-Module result crosses unchanged only when the caller canonically references the foreign Material type and its Sensitivity can reach `Privacy.MODULE`. The caller may then create new caller-owned interpretation Material.

Material adaptation/minimization is Module behavior. Runtime does not lower Sensitivity automatically.

## Reasoning baseline

`ReasoningRequest` is structurally derived from a valid bounded `OperationCall`; originating Module and carried Sensitivity come from that call. Module code supplies a nominal `ReasoningComputation<R>` plus ordinary execution controls, not a concrete mechanism or Operation Risk.

Reasoning eligibility is independent from Operation exposure. A PRIVATE or PUBLIC Operation can request reasoning; Kernel selects only a compatible mechanism whose explicit receiving Privacy can receive the actual carried Sensitivity. If information must be reduced before crossing a weaker boundary, the Module must explicitly derive new appropriately classified Material.

Kernel remains responsible for compatible reasoning-mechanism selection, resource coordination, immediate/durable execution, retry/cancellation and opaque durable persistence. Modules remain responsible for application/domain meaning, association, interpretation and continuation.

The reasoning SPI remains intentionally unchanged by the owner-interaction correction. `ReasoningCapabilityManifest` contains genuine pre-execution mechanism-selection facts: nominal reasoning contract, receiving Privacy, location, expected latency and resources. Those facts are not a duplicate Module-definition graph.

Stable computation-contract artifacts remain `madre-text-inference`, `madre-text-generation` and `madre-embeddings`. Inference families do not imply corresponding Modules or higher-level SDK abstractions.

## Testkit and experimentation baseline

`madre-sdk-testkit` materializes the provider's executable `Module`, validates the projected runtime assembly, and directly invokes exact bounded Operations. `ProgrammableReasoningService` remains generic over arbitrary `ReasoningComputation<R>` and exposes deterministic immediate/controlled durable behavior.

The testkit deliberately does not emulate Kernel mechanism selection, resource scheduling, retry timing, receiver-boundary enforcement or SQLite durability.

`madre-sdk` includes the small stable `StatefulAgent<S>` authoring convenience because the shipped owner-interaction experiment demonstrated a concrete OOP need for typed Agent-owned state without weakening Operation-bound arbitration. `madre-sdk-experimental` remains an explicit 0.x incubation artifact and currently exposes no additional public authoring helper. The former `ModuleDefinitionBuilder` remains removed because it preserved the definition-first duplicate graph that code-first authoring eliminates. Stable SDK/runtime artifacts do not depend on the experimental artifact.

## Independent developer proof

`verification/sdk-consumer` remains a separate Gradle project that depends only on published public MADRE artifacts. Its Module implements the same code-first stable `Module` contract used by shipped code. Its tests exercise deterministic Module behavior through `madre-sdk-testkit`, generic text inference, stable text-generation and embedding computations, an arbitrary non-text `ReasoningComputation<Integer>`, and projection from executable Module objects to the portable `ModuleDefinition`.

`verification/module-interoperability` likewise implements its independent caller/callee as code-first Modules and uses portable non-generic `OperationDefinition` values for discovery/composition.

The repository contains manual cross-platform acceptance workflows for the independent SDK, installed owner lifecycle, reasoning configuration and native packaging. The native package workflow now also contains a sustained owner-conversation acceptance using the shipped OpenAI-compatible provider against a deterministic loopback HTTP fixture. It exercises the real provider/configurator, Kernel mechanism selection, owner-interaction Operations, persisted conversation state, Kernel durable work, Module pending association and restart presentation without requiring internet credentials.

## Owner-interaction implementation baseline

`madre-module-owner-interaction` is an ordinary shipped code-first Module whose interaction actor is a concrete `StatefulAgent<OwnerConversationState>`. The stateful abstraction does not grant privilege and does not replace MADRE Operations.

Its bounded behavior is:

- `standard-prompt`: PRIVATE explicit owner-interaction entry; `WRITE + LIVE_INTERACTION`; constructs bounded conversation-context Material, performs immediate text inference, returns Module-owned answer Material and commits the completed exchange to Agent-owned state;
- `fast-lane`: PRIVATE explicit owner-interaction entry; uses the same bounded conversation context for immediate foreground inference plus independently durable background reasoning, while preserving the Module-owned pending association;
- `collect-background`: PRIVATE explicit owner-interaction entry; interprets terminal durable reasoning, creates optional visible follow-up, acknowledges Kernel work and removes pending Module state.

All three bounded executions are reached through their declared `OperationBinding` and `OperationCall` contracts. There is no `Agent.execute(...)` path or shortcut around Operation arbitration. They are not generic Module-composition APIs and cannot be reached through `PublicModuleInvoker`, `OwnerModuleInvoker` or a caller-bound `ModuleInvoker` merely because the Module is selected as CORE.

Conversation state is Module/Agent-owned, persisted separately from Kernel durable reasoning state, and bounded by the provider-owned `conversation-history-exchanges` setting. Pending durable association is Module-owned and independently persisted; Kernel owns only the physical durable reasoning lifecycle/result. On restart each side recovers its own state and the Module can collect and interpret a recovered terminal result through its ordinary collection Operation.

The reasoning-sensitivity invariant is implemented rather than deferred: when historical/contextual values participate in a reasoning payload, the Module first constructs the actual contextual Material at the combined maximum Sensitivity and derives the reasoning request from a bounded call over that Material. A raw Sensitivity override is not part of the SDK. Native acceptance configures both PUBLIC- and SECRET-receiving OpenAI-compatible mechanisms and verifies that a later S1 turn which carries prior S5 conversation state remains S5 and therefore cannot select the PUBLIC receiver even when it has higher preference.

Foreground `fast-lane` returns from immediate reasoning while durable work continues independently. While the owner interaction surface remains active, host presentation polls the Module's bounded collection Operation. The Module returns only its interpreted update representation and determines whether a follow-up is useful; the host does not inspect Kernel reasoning results. The explicit `/updates` command remains for diagnostics/compatibility.

## Installed owner deployment baseline

The native app image contains the shipped owner-interaction Module plus llama.cpp and OpenAI-compatible reasoning adapters. First launch creates owner-writable configuration/data/state roots and can run with zero configured mechanisms.

The ordinary supported experiment path is:

1. start MADRE or run `madre doctor` to initialize/inspect the installation;
2. run `madre reasoning providers` to inspect provider-owned configuration metadata;
3. configure an installed provider instance through `madre reasoning configure ... --set ...`;
4. start the console and converse normally with the Module selected by `roles.core`;
5. allow fast-lane durable reasoning to continue while the foreground remains responsive;
6. restart MADRE without deleting the owner state roots; Module conversation/pending state and Kernel durable reasoning state recover independently;
7. useful recovered follow-up is surfaced by the active owner interaction surface without requiring `/updates`.

A real manual mechanism may use the shipped OpenAI-compatible or llama.cpp route. Privacy remains explicit provider configuration and is not inferred from loopback, remote endpoint, model name or adapter identity.

## Local artifact lifecycle baseline

Managed local Module/reasoning JAR lifecycle remains unchanged:

- shipped artifacts are immutable;
- MADRE-installed owner artifacts occupy deterministic managed slots;
- directly copied JARs remain discoverable as `manual` but are never adopted/replaced/deleted by managed lifecycle;
- external explicit discovery overrides remain development inputs, not mutation roots;
- replacement validates staged bytes before touching the active artifact and closes discovery classloaders before mutation;
- Module-owned state and Kernel durable reasoning work are not deleted by artifact uninstall;
- Module and reasoning configuration purge remain domain-specific and exact-identity scoped.

## Current intentional limitations

There is no public remote artifact repository/catalog, marketplace, update feed, dependency bundle protocol, signature/PKI trust model, credential vault, sandbox, external-process Module transport, universal Agent loop, generic planner/tool framework, workflow scheduler, universal memory abstraction, RAG abstraction, semantic-database abstraction or arbitrary metadata/property framework.

The owner-interaction entry marker is intentionally narrow. It is not a universal presentation/exposure enum, remote API taxonomy or claim that every Module should expose owner interaction. The current console still uses configured exact Operation/material names and a text update representation; richer product surfaces should evolve only from additional real consumers.

`StatefulAgent<S>` remains typed OOP state ownership for a concrete Agent, while state shape, persistence and use remain application-owned. Further abstractions require real consumers rather than extrapolation from owner interaction alone.

These absences are deliberate until concrete experiments demonstrate an ownership-correct reusable contract. The SDK strategy is to make ordinary modular software cheap to author while keeping stable concepts few, explicit and composable.
