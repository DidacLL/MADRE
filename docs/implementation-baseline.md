# Implementation Baseline

This document records executable truth on the active Java 21 branch. Durable product meaning remains in `MADRE.md`; architecture responsibility rules remain in `docs/architecture/*`.

## Product/runtime baseline

MADRE is implemented as both an owner-installed local application and a public Module/reasoning experimentation platform. Windows and Linux packages contain the application, shipped artifacts, a bundled Java runtime and the version-matched public development contracts used to build independent extensions for that same product. Mutable configuration/data/state live in owner-writable per-user locations; Kernel durable reasoning state and Module-owned state are separate resources.

The product can boot with no configured reasoning mechanism. Module installation and reasoning-provider installation are separate domains. CORE remains an optional ordinary Module role with no registration, Module-composition, Security-Algebra or reasoning privilege.

The shipped first-run owner product selects the ordinary owner-interaction Module through `roles.core`. First-run configuration no longer carries `interaction.*` Operation/Material/background-protocol keys for normal conversation.

Ordinary console text is transported to the `OwnerInteractionAgent` discovered in the Module assigned CORE. The host supplies presentation Sensitivity and presents only Agent-approved `OwnerMessage` values. It does not choose `fast-lane`, `standard-prompt`, `collect-background`, private Material types, target Module Operations or a delayed-work payload/protocol.

The Agent may use the host/runtime `OwnerInteractionInvoker` to execute exact `OperationBinding.ownerInteractionOperation(...)` bindings in its own selected CORE Module. That lower-level port remains absent from `ModuleContext` and remains restricted to the selected CORE. Cross-Module coordination is separate: every installed Module, including one assigned CORE, receives the same caller-bound `ModuleDirectory` and `ModuleInvoker` through `ModuleContext`.

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

`OwnerInteractionAgent` is another optional execution-side specialization. It is discovered from the Module's existing Agent collection when that Module is selected as CORE. It receives owner text plus presentation Sensitivity and returns semantic `OwnerMessage` values; it may also produce delayed approved messages through `followUps(...)`. It is not serialized into `ModuleDefinition`, does not apply to every Module or Agent and grants no special Module-composition authority.

An `Operation<I,O>` is a generic bounded executable unit of Module logic. Its implementation may be pure computation, file/database/network access, a script/process call, reasoning-backed behavior, or any other Module-owned Java code. `OperationCall` is the current typed MADRE arbitration boundary: it validates accepted Material/Privacy and, when the Operation declares consequential variants, the selected EffectProfile against actual non-user causal Integrity. Output type/value-owner/maximum Sensitivity is validated before the result escapes the bounded call.

`OperationDefinition` has no PUBLIC/PRIVATE visibility field. Cross-Module exposure is a Module-interface fact represented by `Module.exposedOperations()` / `ModuleDefinition.exposedOperations()`. Ordinary exposed Module behavior is therefore Module-exposed behavior, not "PUBLIC behavior". External/public disclosure and bounded owner-interaction execution are executable receiver/product bindings, not Operation ontology.

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

`ModuleDefinitionJsonCodec` serializes/deserializes format version 3. Module exposure is represented by the top-level `exposedOperations` set and structural compile-time foreign nominal Material contracts by `foreignMaterialReferences`; Operation objects themselves contain no visibility marker.

`ModuleInstance` is not the ordinary authoring model. It is the validated runtime/adaptor assembly containing one portable `ModuleDefinition`, exact Java Material bindings, exact executable Operation bindings, and optionally one discovered execution-side `OwnerInteractionAgent`.

A concrete Material value and its nominal type have independent ownership. `MaterialId.moduleId` identifies the Module that created/owns the value. `MaterialTypeId.moduleId` identifies the Module that defines the nominal contract. Compile-time foreign contracts remain explicit through `foreignMaterialReferences()`. R4 adds a distinct runtime case for an exact target-owned nominal contract published by the exact selected target Operation; this does not transfer contract ownership or authorize unrelated third-party contracts.

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

- Module-to-Module: caller-bound `ModuleDirectory` / `ModuleInvoker`, targets Operations explicitly present in the target Module's `exposedOperations`, and does not run a public result transformer;
- generic owner/debug: host-only `OwnerModuleInvoker`, resolves any exact installed Operation contract and returns canonical valid Module Material unchanged;
- ordinary owner interaction: host presentation resolves the optional `OwnerInteractionAgent` in the installed Module assigned `roles.core`, transports owner text/approved messages and does not select the Module's private semantic protocol;
- selected-CORE bounded interaction execution: host/runtime `OwnerInteractionInvoker`, usable by that Agent to enter only exact bindings created as owner-interaction entries in the installed Module assigned `roles.core`;
- external/public disclosure: host-only `PublicModuleInvoker`, targets only a binding with a Module-owned public-disclosure transformer and requires the transformed result to reach `Privacy.PUBLIC`.

The durable principles are caller-bound Module composition, explicit receiver Privacy, explicit public transformation, Agent-owned owner semantics and Module encapsulation. Module exposure does not imply external publication; owner interaction does not imply Module exposure; CORE designation does not grant generic authority.

`ModuleDirectory.reachable(ReachabilityQuery)` remains exact discovery for a caller-known nominal input type. R4 adds `ModuleDirectory.reachableOperations(Sensitivity)` for an earlier-compiled caller that cannot know a later target's nominal types. It returns exact target-exposed Operation contracts whose accepted receiver can carry the supplied Sensitivity plus only the target-owned portable `MaterialTypeDefinition`s referenced by those Operations. Kernel performs no semantic ranking.

For invocation, caller identity remains runtime-bound and `ModuleInvoker` remains the only ordinary cross-Module execution port. An earlier-compiled caller may create caller-owned Material conforming exactly to a target-owned input definition returned for the exact selected target Operation. Runtime validates that portable definition against the target's installed canonical definition and Java execution binding. This target-scoped allowance does not authorize unrelated foreign contracts or mutate the caller's portable definition.

The result receiver remains fixed at `Privacy.MODULE`. A target-owned result may cross without a static caller reference only when its nominal definition matches the selected target's installed canonical definition and its Sensitivity can reach the Module receiver. The exact target-created Material crosses unchanged: concrete owner, `MaterialId`, nominal `MaterialTypeId`, payload and Sensitivity are not relabelled. Other foreign nominal types continue to require the caller's static structural reference. The caller may then deliberately create new caller-owned interpretation Material.

Material adaptation/minimization is Module behavior. Runtime does not lower Sensitivity automatically. `Privacy.PUBLIC` is reserved for an actual public receiver boundary and is not a synonym for Module exposure.

## Reasoning baseline

`ReasoningRequest` is structurally derived from a valid bounded `OperationCall`; originating Module and carried Sensitivity come from that call. Module code supplies a nominal `ReasoningComputation<R>` plus ordinary execution controls, not a concrete mechanism or Operation Risk.

Reasoning eligibility is independent from Module exposure and host entry. Any Operation can request reasoning; Kernel selects only a compatible mechanism whose explicit receiving Privacy can receive the actual carried Sensitivity. If information must be reduced before crossing a weaker boundary, the Module must explicitly derive new appropriately classified Material.

Kernel remains responsible for compatible reasoning-mechanism selection, resource coordination, immediate/durable execution, retry/cancellation and opaque durable persistence. Modules remain responsible for application/domain meaning, association, interpretation and continuation.

The reasoning SPI remains intentionally independent of R4 Module composition. `ReasoningCapabilityManifest` contains genuine pre-execution mechanism-selection facts: nominal reasoning contract, receiving Privacy, location, expected latency and resources. Those facts are not a duplicate Module-definition graph.

Stable computation-contract artifacts remain `madre-text-inference`, `madre-text-generation` and `madre-embeddings`. Inference families do not imply corresponding Modules or higher-level SDK abstractions.

## Testkit and independent developer proof

`madre-sdk-testkit` materializes the provider's executable `Module`, validates the projected runtime assembly, and directly invokes exact bounded Operations. `ProgrammableReasoningService` remains generic over arbitrary `ReasoningComputation<R>` and exposes deterministic immediate/controlled durable behavior.

The testkit deliberately does not emulate Kernel mechanism selection, resource scheduling, retry timing, receiver-boundary enforcement or SQLite durability.

The packaged owner MADRE carries its compatible public development artifacts in `developer/repository` inside the jpackage application payload, together with the SDK developer guide. The packaged repository contains only the public BOM, Algebra, stable SDK, testkit, experimental authoring artifact, reasoning SPI and stable computation-contract artifacts; it does not publish or expose `madre-app`, Kernel implementation or concrete adapters as development dependencies.

`verification/sdk-consumer` remains a separate Gradle project depending only on that public MADRE surface. R4 makes the fixture a small agentless durable workspace application while preserving its existing independent SDK/reasoning/public-boundary checks. The application owns its own note state, exposes meaningful save/count behavior, keeps destructive reset behavior unexposed and exposes a deliberately too-sensitive read used to prove the Module receiver boundary.

`verification/reasoning-consumer` likewise accepts an explicit public-repository location, builds against the public reasoning SPI/computation contracts and rejects MADRE runtime/product implementation dependencies.

Cross-platform extended SDK acceptance first builds the release candidate owner image, copies only its packaged `developer` surface and the independent fixture sources into a source-free temporary workspace, deletes the checkout-local publication repository, then builds/checks the independent Module and reasoning provider from the copied product surface. The resulting artifacts are installed/replaced/configured and executed against that same already-built packaged owner MADRE through the ordinary lifecycle.

`verification/module-interoperability` continues to prove the established compile-time Module-boundary path. Kernel tests additionally prove R4 target-scoped dynamic input/output contracts, unchanged target Material delivery, forged-contract rejection and S5 result rejection at `Privacy.MODULE`.

`verification/installed-owner-acceptance.ps1` now also proves the complete R4 product path before the independent reasoning provider is installed: natural owner text reaches the existing shipped CORE Agent, CORE structurally discovers and invokes the independently built agentless workspace application, the target's Module-owned state changes and remains readable after artifact replacement, a target-created S5 result does not cross the Module receiver, and an unexposed destructive Operation cannot be reached through ordinary composition. Owner-visible output contains no target Module/Operation/Material identifiers.

External application integrations may be used as temporary experiments to expose SDK friction. They are not product dependencies or architecture authority merely because they were useful evidence.

## Owner-interaction implementation baseline

`madre-module-owner-interaction` is an ordinary shipped code-first Module whose interaction actor remains one concrete stateful `OwnerInteractionAgent`. The stateful abstraction and owner-interaction specialization grant no privilege and do not replace MADRE Operations.

Its existing bounded implementation still contains:

- `standard-prompt`: bounded conversational inference behavior;
- `fast-lane`: bounded conversational behavior using the same persisted context for immediate foreground inference plus independently durable background reasoning while preserving the Module-owned pending association;
- `collect-background`: bounded delayed-result interpretation/acknowledgement behavior that can produce an optional useful follow-up.

These names are private implementation facts for normal conversation. The host does not select among them, construct their Material protocol or parse their result sentinels. The `OwnerInteractionAgent` makes those semantic choices and executes them through the declared `OperationBinding` / `OperationCall` contracts.

Conversation state is Module/Agent-owned, persisted separately from Kernel durable reasoning state, and bounded by provider-owned settings. Pending durable association is Module-owned and independently persisted; Kernel owns only the physical durable reasoning lifecycle/result. On restart each side recovers its own state and the Agent can collect and interpret recovered terminal work through ordinary Module logic.

When historical/contextual values participate in a reasoning payload, the Module first constructs the actual contextual Material at the combined maximum Sensitivity and derives the reasoning request from a bounded call over that Material. A raw Sensitivity override is not part of the SDK.

For an ordinary owner turn, explicit owner-knowledge intent remains handled first. The Agent may then use its ordinary caller-bound Module directory to look for an unambiguous later-installed textual Operation it can honestly instantiate. The shipped R4 matcher is intentionally narrow: exactly one target-owned UTF-8 text input, exactly one target-owned UTF-8 text output, at most one EffectProfile, input Privacy compatible with the carried owner Sensitivity, and conservative description/action-term matching with ambiguity declined rather than guessed. If no such target is selected, the existing fast foreground/durable reasoning path remains unchanged.

When a target is selected, the Agent constructs caller-owned input using the exact target-owned nominal definition, supplies its actual `Integrity` for a consequential target call, and invokes the exact target Operation through its ordinary `ModuleInvoker`. The runtime returns the exact target-created foreign Material only when the target contract is canonical and the result can reach `Privacy.MODULE`. The Agent then deliberately creates a new CORE-owned `IMMEDIATE_ANSWER` Material for owner presentation, conservatively combining the owner prompt and foreign result Sensitivity. Module invocation failure or unsafe result delivery is converted into a generic owner-facing failure message rather than protocol identifiers.

Host presentation polls `followUps(...)` as a physical delivery mechanism; the Agent invokes the private collection Operation and returns only approved `OwnerMessage` values. Normal owner output does not expose private Operation/Material names, collection payloads or sentinel protocol.

### Current R3 CORE knowledge experiment

The shipped CORE currently persists explicit owner-controlled semantic knowledge in CORE-private Module state. Its private experimental categories are `OWNER_FACT` (minimum S3), `INTERACTION_PREFERENCE` (minimum S2), `ENVIRONMENT_FACT` (minimum S3) and `HIGHLY_SENSITIVE` (minimum S5). These names, category defaults, keyword parser and selection cues are executable evidence from this CORE experiment only; they are not public SDK concepts, a generic memory taxonomy or a Security-Algebra ontology.

When selected owner/environment knowledge participates in reasoning, CORE creates the actual contextual Material and combines its Sensitivity with conversation/current-turn Material before constructing the bounded `OperationCall`. Kernel reasoning selection then applies the unchanged Security Algebra to that carried Sensitivity. Highly sensitive source Material remains S5. For ordinary reasoning use, CORE may derive a new S2 opaque-reference Material that states only that a highly sensitive owner value exists; the raw source value is omitted. Explicit owner-visible raw resolution remains a direct CORE knowledge read and does not call a reasoning mechanism.

The experiment deliberately does not provide credential-vault semantics. Credential-like password/passphrase/PIN/private-key/recovery/API-key/token/account-identifier store or reveal phrases are intercepted as non-storing removal requests, which also clears matching legacy experimental entries. R3's S5 mediation proof instead uses an ordinary private note. MADRE still has no credential vault or secret-manager subsystem.

The shipped CORE Agent declares `Integrity.I2`, not I5. I2 remains tied to concrete assurance evidence for this exact implementation and is not inferred from CORE assignment, locality, shipped status or built-in identity. R4 does not make target Modules depend on that value: the independent workspace's write profile is `Risk.WRITE` plus `Autonomy.LIVE_INTERACTION`, whose existing Algebra demand is I1, and the target implementation contains no CORE identity or trust-level special case. The actual CORE Agent I2 is simply the causal participant supplied by the caller.

The cross-platform packaged acceptance proves foreground response, persisted multi-turn context, durable continuation without an owner updates command, graceful stop/restart while work is relevant, Module-state and Kernel-work recovery, natural Agent-approved follow-up after restart, R3 classified-knowledge reasoning selection, opaque S5 mediation, credential non-storage, R4 source-independent Module composition and absence of private interaction/target protocol from normal product output.

## Installed owner deployment and artifact lifecycle

The native app image contains the shipped owner-interaction Module plus llama.cpp and OpenAI-compatible reasoning adapters. It also carries the version-matched public development repository and developer guide under the packaged `developer` directory. First launch creates owner-writable configuration/data/state roots and can run with zero configured mechanisms.

Managed local Module/reasoning JAR lifecycle remains unchanged:

- shipped artifacts are immutable;
- MADRE-installed owner artifacts occupy deterministic managed slots;
- directly copied JARs remain discoverable as `manual` but are never adopted/replaced/deleted by managed lifecycle;
- external explicit discovery overrides remain development inputs, not mutation roots;
- replacement validates staged bytes before touching the active artifact and closes discovery classloaders before mutation;
- Module-owned state and Kernel durable reasoning work are not deleted by artifact uninstall;
- Module and reasoning configuration purge remain domain-specific and exact-identity scoped.

## Current intentional limitations

There is no public remote artifact repository/catalog, marketplace, update feed, dependency bundle protocol, signature/PKI trust model, credential vault, sandbox, external-process Module transport, universal Agent loop, generic planner/tool framework, workflow scheduler, universal memory abstraction, RAG abstraction, semantic-database abstraction or arbitrary metadata/property framework. The packaged version-matched Maven repository is a local development distribution surface, not a registry, catalog or update service.

`OwnerInteractionAgent` is intentionally narrow. It is not a universal presentation taxonomy, remote API taxonomy or claim that every Module should expose owner interaction. Likewise, the owner-interaction Operation marker remains a lower-level selected-CORE execution boundary, and Module exposure remains only the explicit installed Module interface for ordinary composition rather than a replacement access-control lattice.

R4 structural discovery is not a universal Tool/Capability/Action registry and does not guarantee that a caller can understand every exposed Operation. It publishes portable bounded contracts only. The shipped CORE currently understands one conservative textual subset and declines unsupported or ambiguous contracts. More capable semantic coordination requires future evidence rather than expanding Kernel or inventing a generic planner.

`StatefulAgent<S>` remains typed OOP state ownership for a concrete Agent, while state shape, persistence and use remain application-owned. Further abstractions require real consumers rather than extrapolation from owner interaction alone.

These absences are deliberate until concrete experiments demonstrate an ownership-correct reusable contract. The SDK strategy is to make ordinary modular software cheap to author while keeping stable concepts few, explicit and composable.
