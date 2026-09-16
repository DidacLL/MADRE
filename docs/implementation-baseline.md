# Implementation Baseline

This document records executable truth on the active Java 21 branch. Durable product meaning remains in `MADRE.md`; architecture responsibility rules remain in `docs/architecture/*`.

## Product/runtime baseline

MADRE is implemented as both an owner-installed local application and a public Module/reasoning experimentation platform. Windows and Linux packages contain the application, shipped artifacts and a bundled Java runtime. Mutable configuration/data/state live in owner-writable per-user locations; Kernel durable reasoning state and Module-owned state are separate resources.

The product can boot with no configured reasoning mechanism. Module installation and reasoning-provider installation are separate domains. CORE remains an optional ordinary Module role with no registration, Module-composition, Security-Algebra or reasoning privilege.

The shipped first-run owner product selects the ordinary owner-interaction Module through `roles.core`. First-run configuration no longer carries `interaction.*` Operation/Material/background-protocol keys for normal conversation.

Ordinary console text is transported to the `OwnerInteractionAgent` discovered in the Module assigned CORE. The host supplies presentation Sensitivity and presents only Agent-approved `OwnerMessage` values. It does not choose `fast-lane`, `standard-prompt`, `collect-background`, private Material types or a delayed-work payload/protocol.

The Agent may use the host/runtime `OwnerInteractionInvoker` to execute exact `OperationBinding.ownerInteractionOperation(...)` bindings in its own selected CORE Module. That lower-level port remains absent from `ModuleContext`, remains restricted to the selected CORE, and therefore grants no generic Module authority.

While the interaction surface is active, host presentation may poll the Agent's `followUps(...)` method as a physical delivery mechanism. The Agent itself invokes and interprets the Module's private collection behavior, owns durable-work association/acknowledgement and decides whether a visible follow-up is useful. There is no ordinary `/updates` workflow or host-parsed collection result protocol.

## Code-first Module SDK

The stable Java SDK has an explicit execution-side authoring model:

```text
Module
Agent                     optional
StatefulAgent<S>           optional Agent specialization
OwnerInteractionAgent      optional owner-semantic Agent specialization
OwnerMessage               Agent-approved owner-visible semantic message
MaterialType<T>
Operation<I,O>
OperationBinding<I,O>
OperationCall<I,O>
```

A Java `Module` is the executable source of truth. It declares identity/version/purpose, Java Material bindings, optional foreign Material references, optional Agents/Skills, executable Operation bindings, and the exact Operation identities it exposes to other installed Modules. `Module.definition()` derives the portable contract and `Module.instance()` derives the validated runtime assembly.

A Java `Agent` is an optional Module-owned actor. It defines no universal loop, planner, memory, prompt or execution context. If no stronger causal-integrity claim has been established, `Agent.integrity()` defaults to `Integrity.I1`; authors may explicitly declare stronger Integrity when justified.

`StatefulAgent<S>` is an optional OOP authoring base for an Agent that owns typed private state. It serializes reads/transitions and can commit one transition through a Module-owned persistence function before publishing the new in-memory state. It is not a portable memory schema, generic persistence service or second execution model: concrete state meaning remains Module/Agent-owned, and MADRE-arbitrated executions still occur through Operations and `OperationCall` values.

`OwnerInteractionAgent` is another optional execution-side specialization. It is discovered from the Module's existing Agent collection when that Module is selected as CORE. It receives owner text plus presentation Sensitivity and returns semantic `OwnerMessage` values; it may also produce delayed approved messages through `followUps(...)`. It is not serialized into `ModuleDefinition`, does not apply to every Module or Agent and grants no authority beyond the already CORE-restricted interaction invoker supplied for execution.

An `Operation<I,O>` is a generic bounded executable unit of Module logic. Its implementation may be pure computation, file/database/network access, a script/process call, reasoning-backed behavior, or any other Module-owned Java code. `OperationCall` is the current typed MADRE arbitration boundary: it validates accepted Material/Privacy and, when the Operation declares consequential variants, the selected EffectProfile against actual non-user causal Integrity. Output type/value-owner/maximum Sensitivity is validated before the result escapes the bounded call.

`OperationDefinition` has no PUBLIC/PRIVATE visibility field. Cross-Module exposure is a Module-interface fact represented by `Module.exposedOperations()` / `ModuleDefinition.exposedOperations()`. External/public disclosure and bounded owner-interaction execution are executable receiver/product bindings, not Operation ontology.

`OperationBinding.operation(...)` binds ordinary bounded execution. `OperationBinding.publicDisclosure(...)` adds an explicit Module-owned transformation used only when information crosses the external/public receiver boundary. `OperationBinding.ownerInteractionOperation(...)` marks an exact Operation as eligible for the restricted selected-CORE interaction invoker used internally by an owner-interaction Agent. These binding facts do not change Security Algebra or cross-Module exposure and do not define the ordinary owner UI/API.

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

`ModuleDefinitionJsonCodec` serializes/deserializes format version 3. Module exposure is represented by the top-level `exposedOperations` set and structural foreign nominal Material contracts by `foreignMaterialReferences`; Operation objects themselves contain no visibility marker.

`ModuleInstance` is not the ordinary authoring model. It is the validated runtime/adaptor assembly containing one portable `ModuleDefinition`, exact Java Material bindings, exact executable Operation bindings, and optionally one discovered execution-side `OwnerInteractionAgent`.

A concrete Material value and its nominal type have independent ownership. `MaterialId.moduleId` identifies the Module that created/owns the value. `MaterialTypeId.moduleId` identifies the Module that defines the nominal contract. A Module may create a value using an explicitly referenced foreign nominal contract. This is not an ownership transfer of the contract and it does not bypass receiver Privacy.

## Provider and owner configuration

`ModuleProvider` exposes canonical Module identity, provider-owned configuration metadata/validation and `create(...)`, which returns the executable `Module`, not a separately assembled `ModuleInstance`. Host/testkit code projects and validates the runtime assembly.

The stable owner-facing configuration field kinds remain exactly `TEXT`, `INTEGER` and `CHOICE`. Provider code owns key meaning, parsing/defaults and validation. Configuration discovery/validation does not materialize the Module.

Raw compatibility persistence remains:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The host supports `modules list`, `inspect`, `configure`, local-file `install --replace`, and `uninstall [--purge-configuration]` without giving Module code artifact-management authority.

`roles.core` remains the installation-level selection for default owner semantic interaction. There is no replacement universal semantic configuration mini-protocol for private Operations, Material types or background collection.

## Security and invocation baseline

The public Security Algebra remains unchanged. It governs MADRE-mediated values and composition; it is not an OS sandbox or general permission system for arbitrary installed Java code.

Current receiver/entry paths are intentionally distinct:

- Module-to-Module: caller-bound `ModuleDirectory` / `ModuleInvoker`, targets Operations explicitly present in the target Module's `exposedOperations`, uses fixed `Privacy.MODULE`, and does not run a public result transformer;
- generic owner/debug: host-only `OwnerModuleInvoker`, resolves any exact installed Operation contract and returns canonical valid Module Material unchanged;
- ordinary owner interaction: host presentation resolves the optional `OwnerInteractionAgent` in the installed Module assigned `roles.core`, transports owner text/approved messages and does not select the Module's private semantic protocol;
- selected-CORE bounded interaction execution: host/runtime `OwnerInteractionInvoker`, usable by that Agent to enter only exact bindings created as owner-interaction entries in the installed Module assigned `roles.core`;
- external/public disclosure: host-only `PublicModuleInvoker`, targets only a binding with a Module-owned public-disclosure transformer and requires the transformed result to reach `Privacy.PUBLIC`.

The durable principles are caller-bound Module composition, explicit receiver Privacy, explicit public transformation, Agent-owned owner semantics and Module encapsulation. Module exposure does not imply external publication; owner interaction does not imply Module exposure; CORE designation does not grant generic authority.

A Module-to-Module result crosses unchanged only when the caller structurally declares the nominal Material contract in `foreignMaterialReferences` and its Sensitivity can reach `Privacy.MODULE`. The concrete value remains owned by the Module that produced it even when the nominal type is defined by the caller or another Module. The caller may then create new caller-owned interpretation Material.

Material adaptation/minimization is Module behavior. Runtime does not lower Sensitivity automatically. `Privacy.PUBLIC` is reserved for an actual public receiver boundary and is not a synonym for Module exposure.

## Reasoning baseline

`ReasoningRequest` is structurally derived from a valid bounded `OperationCall`; originating Module and carried Sensitivity come from that call. Module code supplies a nominal `ReasoningComputation<R>` plus ordinary execution controls, not a concrete mechanism or Operation Risk.

Reasoning eligibility is independent from Module exposure and host entry. Any Operation can request reasoning; Kernel selects only a compatible mechanism whose explicit receiving Privacy can receive the actual carried Sensitivity. If information must be reduced before crossing a weaker boundary, the Module must explicitly derive new appropriately classified Material.

Kernel remains responsible for compatible reasoning-mechanism selection, resource coordination, immediate/durable execution, retry/cancellation and opaque durable persistence. Modules remain responsible for application/domain meaning, association, interpretation and continuation.

The reasoning SPI remains intentionally unchanged by R2. `ReasoningCapabilityManifest` contains genuine pre-execution mechanism-selection facts: nominal reasoning contract, receiving Privacy, location, expected latency and resources. Those facts are not a duplicate Module-definition graph.

Stable computation-contract artifacts remain `madre-text-inference`, `madre-text-generation` and `madre-embeddings`. Inference families do not imply corresponding Modules or higher-level SDK abstractions.

## Testkit and independent developer proof

`madre-sdk-testkit` materializes the provider's executable `Module`, validates the projected runtime assembly, and directly invokes exact bounded Operations. `ProgrammableReasoningService` remains generic over arbitrary `ReasoningComputation<R>` and exposes deterministic immediate/controlled durable behavior.

The testkit deliberately does not emulate Kernel mechanism selection, resource scheduling, retry timing, receiver-boundary enforcement or SQLite durability.

`verification/sdk-consumer` remains a separate Gradle project depending only on published public MADRE artifacts. Its Module implements the same code-first stable `Module` contract used by shipped code. Its tests exercise deterministic Module behavior, heterogeneous reasoning contracts and projection from executable Module objects to the portable `ModuleDefinition`.

`verification/module-interoperability` independently proves Module-boundary exposure, caller-bound discovery/invocation, `Privacy.MODULE` result reachability, foreign nominal-contract references and unchanged callee-owned Material crossing between Modules.

`verification/installed-owner-acceptance.ps1` proves an independently built Module and independently built reasoning provider can be installed/replaced/configured while ordinary owner interaction remains plain text through the selected CORE rather than an implementation command.

External application integrations may be used as temporary experiments to expose SDK friction. They are not product dependencies or architecture authority merely because they were useful evidence.

## Owner-interaction implementation baseline

`madre-module-owner-interaction` is an ordinary shipped code-first Module whose interaction actor is a concrete stateful `OwnerInteractionAgent`. The stateful abstraction and owner-interaction specialization grant no privilege and do not replace MADRE Operations.

Its existing bounded implementation still contains:

- `standard-prompt`: bounded conversational inference behavior;
- `fast-lane`: bounded conversational behavior using the same persisted context for immediate foreground inference plus independently durable background reasoning while preserving the Module-owned pending association;
- `collect-background`: bounded delayed-result interpretation/acknowledgement behavior that can produce an optional useful follow-up.

These names are private implementation facts for normal conversation. The host does not select among them, construct their Material protocol or parse their result sentinels. The `OwnerInteractionAgent` makes those semantic choices and executes them through the declared `OperationBinding` / `OperationCall` contracts.

Conversation state is Module/Agent-owned, persisted separately from Kernel durable reasoning state, and bounded by provider-owned settings. Pending durable association is Module-owned and independently persisted; Kernel owns only the physical durable reasoning lifecycle/result. On restart each side recovers its own state and the Agent can collect and interpret recovered terminal work through ordinary Module logic.

When historical/contextual values participate in a reasoning payload, the Module first constructs the actual contextual Material at the combined maximum Sensitivity and derives the reasoning request from a bounded call over that Material. A raw Sensitivity override is not part of the SDK.

For an ordinary owner turn, the Agent currently chooses the fast foreground path and may continue useful analysis durably. Host presentation polls `followUps(...)` as a physical delivery mechanism; the Agent invokes the private collection Operation and returns only approved `OwnerMessage` values. Normal owner output does not expose private Operation/Material names, collection payloads or sentinel protocol.

The cross-platform packaged acceptance proves foreground response, persisted multi-turn context, durable continuation without an owner updates command, graceful stop/restart while work is relevant, Module-state and Kernel-work recovery, natural Agent-approved follow-up after restart, and absence of the private interaction protocol from normal product output.

## Installed owner deployment and artifact lifecycle

The native app image contains the shipped owner-interaction Module plus llama.cpp and OpenAI-compatible reasoning adapters. First launch creates owner-writable configuration/data/state roots and can run with zero configured mechanisms.

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

`OwnerInteractionAgent` is intentionally narrow. It is not a universal presentation taxonomy, remote API taxonomy or claim that every Module should expose owner interaction. Likewise, the owner-interaction Operation marker remains a lower-level selected-CORE execution boundary, and Module exposure remains only the explicit installed Module interface for ordinary composition rather than a replacement access-control lattice.

`StatefulAgent<S>` remains typed OOP state ownership for a concrete Agent, while state shape, persistence and use remain application-owned. Further abstractions require real consumers rather than extrapolation from owner interaction alone.

These absences are deliberate until concrete experiments demonstrate an ownership-correct reusable contract. The SDK strategy is to make ordinary modular software cheap to author while keeping stable concepts few, explicit and composable.
