# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It has two equally important identities: an owner-installed product and a public software/inference experimentation platform.

The active implementation is Java 21. Windows and Linux use the same application, Kernel, SDK, Module lifecycle and reasoning-mechanism architecture.

MADRE is not one frozen assistant architecture, a hosted AI platform, a plugin marketplace, or a universal Agent framework. Its purpose is to make ordinary local application/domain software composable with heterogeneous reasoning mechanisms while preserving explicit ownership, typed semantic values and Security Algebra.

Start with:

- [MADRE.md](MADRE.md) — durable product meaning and Owner reasoning;
- [SDK developer guide](docs/sdk-development.md) — current independent Module developer journey;
- [Platform architecture](docs/architecture/MADRE-platform-architecture.md) — responsibility boundaries;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) — composition rules;
- [Module SDK and interoperability](docs/architecture/MADRE-agent-interoperability.md) — public Module execution model;
- [Reasoning execution contract](docs/architecture/MADRE-execution-contract.md) — reasoning SPI and Kernel path;
- [Implementation baseline](docs/implementation-baseline.md) — executable truth.

## Architectural shape

```text
Owner
  |
  +--> Host product mechanics
  |
  `--> Module assigned CORE for ordinary semantic interaction
           |
           +--> exposed interfaces of other installed Modules
           |
           `--> ReasoningService
                    |
                    v
                  Kernel
                    |
                    v
          heterogeneous reasoning mechanisms
```

A Module exists because it owns a coherent application/domain. It may be agentless. Technical reuse alone does not create a Module: search clients, files, databases, HTTP, MCP, ML libraries, vector stores, devices and application APIs can remain ordinary implementation facilities inside the software that owns their semantics.

An Agent is an optional Module-owned semantic actor. Semantic intent, interpretation and continuation remain Agent responsibilities when agency is present; Kernel does not manufacture agency for an agentless Module.

An Operation is one bounded callable execution of Module logic under MADRE arbitration. It may calculate, perform application I/O, invoke reasoning, call other Modules, or combine ordinary Java facilities. PUBLIC/PRIVATE is not Operation ontology.

Kernel stays narrow. It owns live Module receiver mechanics plus shared reasoning selection/resources/immediate-durable execution/retry/cancellation/opaque persistence. It does not own application workflows, conversation state, search or semantic continuation.

CORE is an ordinary Module role for default owner interaction/coordination. It has no Security Algebra, Module-composition, reasoning, installation or host privilege.

For ordinary owner conversation, the host presents input/output while the selected CORE Agent owns the semantic interaction: turn interpretation, conversation context, reasoning/continuation choices and useful delayed follow-up.

## Code-first Module SDK

Java Module authors implement executable `Module` objects. MADRE projects portable contracts from those objects:

```text
Java execution side             Portable description side
-------------------             -------------------------
Module                          ModuleDefinition
Agent                           AgentDefinition
StatefulAgent<S>                (execution-side specialization only)
OwnerInteractionAgent           (execution-side specialization only)
OwnerMessage                    (execution-side semantic message)
MaterialType<T>                 MaterialTypeDefinition
Operation<I,O>                  OperationDefinition
OperationBinding<I,O>           SkillDefinition
OperationCall<I,O>              WorkflowDefinition
                                EffectProfile
```

`OperationDefinition` is intentionally language-neutral and non-generic. It contains bounded execution facts only: accepted Material receiver Privacy, produced Material maximum Sensitivity and optional EffectProfiles. Cross-Module exposure belongs to `Module.exposedOperations()` / `ModuleDefinition.exposedOperations()`.

Executable binding choices are independent from exposure:

```text
OperationBinding.operation(...)                 ordinary bounded execution
OperationBinding.ownerInteractionOperation(...) selected-CORE bounded interaction entry
OperationBinding.publicDisclosure(...)          explicit external/public transformation
```

A single concern must not be overloaded to mean Module exposure, ordinary owner conversation and actual public disclosure. `ownerInteractionOperation(...)` is a lower-level execution entry that a selected CORE Agent may use; it is not the owner-facing conversational API.

A concrete Material value and its nominal contract also have distinct ownership. `MaterialId.moduleId` owns the value; `MaterialTypeId.moduleId` owns the nominal type. A Module may create a value conforming to an explicitly referenced foreign nominal contract without transferring concrete value ownership.

An Agent whose causal Integrity has not been established defaults conservatively to `Integrity.I1`. `StatefulAgent<S>` supplies typed state ergonomics but does not create another execution model; arbitrated work remains Operation-bound.

See [docs/sdk-development.md](docs/sdk-development.md) for executable examples.

## Receiver boundaries

MADRE keeps four concerns distinct.

Module-to-Module composition uses caller-bound `ModuleDirectory` / `ModuleInvoker`. Only Operations in the target Module's `exposedOperations` are discoverable/invocable. The result receiver is fixed `Privacy.MODULE`; contract-valid callee Material crosses unchanged when the caller structurally understands the nominal Material type and Sensitivity can reach that receiver.

Generic owner/debug invocation is host-only and may enter an exact installed Operation without implying Module exposure or public disclosure. It returns valid Module Material unchanged.

Ordinary owner interaction is a host presentation path into the `OwnerInteractionAgent` supplied by the installed Module assigned `roles.core`. The host sends ordinary owner text and presents Agent-approved `OwnerMessage` values. The Agent may internally use the host/runtime `OwnerInteractionInvoker`, which can enter only exact `ownerInteractionOperation(...)` bindings in the selected CORE Module. The port is not supplied through `ModuleContext`, so CORE receives no authority over another Module's internal behavior.

Actual external/public disclosure uses `publicDisclosure(...)`. The Module-owned transformer must create new declared Material with a new identity whose Sensitivity can reach `Privacy.PUBLIC`. This is the durable meaning of a public receiver boundary.

## Security Algebra

MADRE's public algebra uses distinct ordered carriers for Privacy, Sensitivity, Integrity, Risk and Autonomy.

```text
Privacy      PUBLIC ... SECRET
Sensitivity  S1 ... S5
Integrity    I1 ... I5
Risk         READ ... POTENTIALLY_HARMFUL
Autonomy     LIVE_INTERACTION ... AUTONOMOUS
```

The exact ranks and `SYSTEM_RESERVED` elements are defined in the architecture document. Sensitivity combines by maximum; Privacy and Integrity combine by minimum; information can reach a receiver when accumulated Sensitivity is no greater than receiver Privacy.

Module exposure, generic owner/debug entry, owner-interaction binding, public-disclosure binding and CORE assignment are not Algebra carriers. `Privacy.PUBLIC` means an actual public receiver, not an exposed Module interface.

Security Algebra governs MADRE-mediated semantic composition. It is not an operating-system sandbox for arbitrary software deliberately installed by the Owner.

## Reasoning

Modules request reasoning through `ReasoningService` using typed `ReasoningComputation<R>` contracts. They do not select concrete provider implementations.

Stable provider-independent families currently include text inference, text generation and text embeddings. Concrete OpenAI-compatible and llama.cpp adapters implement compatible mechanisms without moving provider/model knowledge into Kernel.

`ReasoningRequest` derives originating Module and carried Sensitivity from a bounded `OperationCall`. If application code combines several semantic values into inference context, it first constructs actual contextual Material at the combined Sensitivity. There is no raw Sensitivity override.

Any Operation may request reasoning. Eligibility depends on actual carried Sensitivity and mechanism receiving Privacy plus computation/availability/resource compatibility—not on Module exposure, host entry, CORE status, provider identity, model name or locality assumptions.

Durable reasoning stores opaque physical work in Kernel. Modules retain semantic association and decide how completed results are interpreted or continued.

## Owner installation

JDK 21 `jpackage` produces Windows MSI and Linux DEB packages containing MADRE, shipped artifacts and a bundled Java runtime. Mutable product state is owner-writable and separate from the installation image.

A fresh installation does not invent reasoning endpoints, models, credentials or Privacy values. Zero configured reasoning mechanisms is valid.

The shipped first-run configuration assigns `io.github.didacll.madre.owner-interaction` to `roles.core`. Ordinary conversation requires no host configuration of CORE-private Operation names, Material types, reasoning modes or background collection protocol.

Reasoning providers are configured generically through provider-owned metadata, for example:

```text
madre reasoning providers
madre reasoning configure openai-compatible personal \
  --set capability-id=personal-text \
  --set endpoint=http://127.0.0.1:8080/v1/ \
  --set model=my-model \
  --set privacy=SECRET \
  --set location=LOCAL
```

`privacy` is explicit; loopback/locality does not infer it.

## Local Module lifecycle

Owner-selected Module JARs use a generic local lifecycle:

```text
madre modules install <jar>
madre modules install <replacement.jar> --replace
madre modules list
madre modules inspect <module-id>
madre modules configure <module-id> [--set <field>=<value>]...
madre modules uninstall <module-id>
madre modules uninstall <module-id> --purge-configuration
```

Shipped artifacts are protected. MADRE-installed owner artifacts occupy managed slots. Manually placed JARs remain discoverable but are not silently adopted/replaced/deleted. Module semantic state is distinct from artifact bytes.

Reasoning adapters have a separate `madre reasoning ...` lifecycle; installing a provider does not enable a mechanism or assign a Module role.

## Current owner interaction

The shipped owner-interaction Module is an ordinary reference consumer. Its stateful `OwnerInteractionAgent` demonstrates bounded persisted multi-turn conversation state, contextual Material with combined Sensitivity, immediate foreground reasoning, independently durable background reasoning, Module-owned pending WorkId association, restart recovery, Module interpretation of completed reasoning and optional visible follow-up.

Launch the packaged application and type an ordinary request. The Agent selects its own private conversational execution path. A second ordinary turn reuses persisted context. If useful durable continuation is started, the host may poll the Agent physically, but only the Agent invokes/interprets its private collection protocol and decides whether a follow-up is worth presenting. That follow-up can recover naturally after restart.

The private Operations and Material protocol are not cross-Module exposed merely because the Module is CORE, and normal product output does not expose their names or collection sentinels. Expert `/invoke-owner` and external `/invoke-public` remain separate explicit host boundaries.

## Development

Source development uses JDK 21 and the checked-in Gradle wrapper:

```text
./gradlew --no-daemon --build-cache check
./gradlew --no-daemon publish
```

Independent verification projects exercise SDK consumption, Module-to-Module interoperability and reasoning-provider SPI consumption against published public artifacts. Installed-owner acceptance proves independently built Module/reasoning artifacts continue to work with plain owner conversation. Native owner acceptance proves foreground, multi-turn, durable continuation and restart recovery across Windows and Linux packages.

Stable architecture should continue to be recovered from substantial owner/developer experiments. Do not pre-build a universal Agent loop, planner/tool framework, memory/RAG system, semantic database, workflow scheduler, Module taxonomy or replacement visibility/access-control lattice.
