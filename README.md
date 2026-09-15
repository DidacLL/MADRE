# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It has two equally important identities:

1. an owner-installed application; and
2. a public software and inference experimentation platform.

The active implementation is Java 21. Windows and Linux use the same application, Kernel, SDK, Module lifecycle and reasoning-mechanism architecture.

MADRE is not one frozen assistant architecture and it is not a hosted AI platform. Its purpose is to make it practical to combine ordinary local software with heterogeneous reasoning/model machinery while preserving explicit Module ownership and composable trust/security contracts.

Start with:

- [MADRE.md](MADRE.md) — durable product meaning, Owner reasoning and development posture;
- [SDK developer guide](docs/sdk-development.md) — current independent Module developer journey;
- [Platform architecture](docs/architecture/MADRE-platform-architecture.md) — responsibility boundaries;
- [Security Algebra](docs/architecture/MADRE-security-algebra.md) — composition rules;
- [Module SDK and interoperability](docs/architecture/MADRE-agent-interoperability.md) — public Module execution model;
- [Reasoning execution contract](docs/architecture/MADRE-execution-contract.md) — reasoning SPI and Kernel path;
- [Implementation baseline](docs/implementation-baseline.md) — executable truth.

## Architectural shape

The intended flow is:

```text
owner / domain software
        |
        v
      Modules
  Agents / Operations
        |
        v
  ReasoningService
        |
        v
      Kernel
        |
        v
heterogeneous reasoning mechanisms
```

A Module exists because it owns a coherent application/domain. Technical reuse alone does not create a Module. Search clients, files, databases, HTTP, MCP, ML libraries, vector stores, devices and application APIs can be ordinary implementation facilities inside a Module when that is where their ownership belongs.

An Operation is simply one bounded callable execution of Module logic placed under MADRE arbitration. It may calculate a value, read or write a file, call a public API, query a database, run a script/process, invoke reasoning, or compose ordinary Java code. The developer declares the Material/trust/security facts that apply; MADRE validates the bounded call. Operation exposure, reasoning locality and transport are separate concerns.

Kernel stays narrow. It owns shared reasoning execution concerns such as compatible mechanism selection, resources, immediate/durable execution, retry/cancellation and opaque durable persistence. It does not own Agent/application workflows, memory, tools, search or domain continuation.

Reasoning providers/adapters own concrete mechanism/model/runtime configuration. Adding a new inference family does not imply a new Module or Operation kind.

CORE is an optional ordinary Module role for owner interaction/coordination. It has no special invocation, Security Algebra, reasoning or installation privilege.

## Code-first Module SDK

Java Module authors implement ordinary executable `Module` objects. Optional `Agent` objects live inside the Module when the domain genuinely has intelligent actors. Agents that genuinely own typed state may optionally extend `StatefulAgent<S>`; this does not create another execution model.

MADRE derives portable descriptions from executable objects rather than requiring a parallel hand-maintained definition graph:

```text
Java execution side             Portable description side
-------------------             -------------------------
Module                          ModuleDefinition
Agent                           AgentDefinition
StatefulAgent<S>                (execution-side specialization only)
MaterialType<T>                 MaterialTypeDefinition
Operation<I,O>                  OperationDefinition
OperationBinding<I,O>           SkillDefinition
OperationCall<I,O>              WorkflowDefinition
                                EffectProfile
```

`OperationDefinition` is intentionally language-neutral and non-generic. Java payload typing belongs to the executable `Operation<I,O>`, `OperationCall<I,O>` and `OperationBinding<I,O>` path. Likewise, portable `MaterialTypeDefinition` contains nominal identity plus content type, while Java `MaterialType<T>` owns `Class<T>` and its codec.

`Module.instance()` produces the validated runtime assembly. Ordinary Module authors do not need to understand Kernel registries or construct `ModuleInstance` manually.

An Agent whose causal Integrity has not been established defaults conservatively to `Integrity.I1`; generated or experimental code is not required to invent stronger assurance. A Module may contain zero Agents.

`StatefulAgent<S>` supplies serialized typed state reads/transitions and optional commit-before-publish persistence. The concrete Module still owns the state model and persistence format, and all MADRE-arbitrated bounded execution remains Operation-bound.

The stable SDK deliberately remains small. `madre-sdk-experimental` remains an explicit incubation artifact for higher-level facilities whose ownership or semantics are not yet ready to stabilize. The previous definition-first builder was removed after code-first authoring made it redundant.

See [docs/sdk-development.md](docs/sdk-development.md) for examples.

## Security Algebra

MADRE's public algebra uses ordered carriers for Privacy, Sensitivity, Integrity, Risk and Autonomy. Those values attach only where the corresponding responsibility exists.

Important current receiver paths remain distinct:

- Module-to-Module invocation currently targets `PUBLIC` Operations, uses fixed `Privacy.MODULE`, and preserves valid callee Material unchanged;
- owner-local invocation currently reuses a host-only path to a `PUBLIC` Operation and returns valid Module Material without public minimization;
- external/PUBLIC invocation currently reuses a `PUBLIC` binding and requires explicit Module-owned transformation to new PUBLIC-capable Material;
- `PRIVATE` Operations remain unavailable to those generic installed invocation paths.

The shared `PUBLIC` marker across those current paths is 0.x exposure wiring, not the definition of an Operation and not a confidentiality label.

Runtime validates declared contracts but does not invent sanitization or automatically lower Sensitivity.

Security Algebra governs MADRE-mediated information/effect composition. It is not an operating-system sandbox for arbitrary installed Java code. The Owner explicitly chooses local software to install.

## Reasoning

Modules request reasoning through `ReasoningService` using typed `ReasoningComputation<R>` contracts. They do not select concrete provider implementations.

Stable provider-independent computation families currently include:

- `madre-text-inference`;
- `madre-text-generation`;
- `madre-embeddings`.

Concrete OpenAI-compatible and llama.cpp adapters implement compatible mechanisms without moving provider/model knowledge into Kernel.

`ReasoningRequest` derives originating Module and carried Sensitivity from a valid bounded `OperationCall`. If application code combines multiple inputs before inference, it must construct the actual contextual Material with the combined Sensitivity and derive reasoning from that bounded call; there is no raw carried-Sensitivity escape hatch.

A PRIVATE or PUBLIC Operation may request reasoning. Mechanism eligibility depends on the actual Material Sensitivity and the mechanism's explicit receiving Privacy, not on Operation visibility or locality assumptions.

Durable reasoning stores opaque physical work in Kernel. Modules retain association and decide how completed results are interpreted or continued.

## Owner installation

JDK 21 `jpackage` produces Windows MSI and Linux DEB packages containing MADRE, shipped artifacts and a bundled Java runtime. Normal product state is owner-writable and separate from the installation image.

Typical owner roots are:

```text
Windows
  config: %APPDATA%\MADRE\madre.properties
  data:   %LOCALAPPDATA%\MADRE
  state:  %LOCALAPPDATA%\MADRE\state

Linux
  config: ${XDG_CONFIG_HOME:-~/.config}/madre/madre.properties
  data:   ${XDG_DATA_HOME:-~/.local/share}/madre
  state:  ${XDG_STATE_HOME:-~/.local/state}/madre
```

The Kernel durable database lives under state as `kernel-work.sqlite`; Module-owned state is rooted separately under `<state>/module-state`.

A fresh installation does not invent reasoning provider endpoints, models, credentials or Privacy values. Zero configured reasoning mechanisms is a valid state.

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

Managed installation currently accepts one ordinary local JAR exposing one canonical `ModuleProvider`. Shipped artifacts are protected. MADRE-installed owner artifacts occupy deterministic managed slots. A manually copied JAR remains discoverable as `manual` compatibility input but is never silently adopted, overwritten or deleted by managed lifecycle commands.

Module configuration is provider-owned and persisted under the exact canonical identity:

```properties
modules.config[<ModuleId>].<module-owned-key>=<value>
```

Module-owned state is distinct from artifact bytes and is not deleted by uninstall.

## Reasoning-provider lifecycle

Reasoning adapters are installed/configured separately from Modules:

```text
madre reasoning install <jar>
madre reasoning install <replacement.jar> --replace
madre reasoning providers
madre reasoning configure <provider-id> <instance> [--set <field>=<value>]...
madre reasoning list
madre reasoning inspect <provider-id>/<instance>
madre reasoning enable <provider-id>/<instance>
madre reasoning disable <provider-id>/<instance>
madre reasoning remove <provider-id>/<instance>
madre reasoning uninstall <provider-id>
madre reasoning uninstall <provider-id> --purge-configuration
```

Installing a provider makes a provider type discoverable; it does not invent or enable a mechanism instance. Provider-owned configuration remains provider-owned.

## Current owner interaction

The shipped `io.github.didacll.madre.owner-interaction` artifact is an ordinary Module and an important reference consumer. Its interaction actor is a concrete `StatefulAgent<OwnerConversationState>` and currently demonstrates:

- bounded persisted multi-turn owner conversation state;
- contextual Material whose Sensitivity is the maximum of all participating current/historical values;
- immediate foreground reasoning;
- independently durable background reasoning;
- Module-owned pending WorkId association and restart recovery;
- explicit Module interpretation of completed reasoning;
- optional visible follow-up;
- explicit external/PUBLIC minimization;
- MADRE-arbitrated execution through declared `OperationBinding` / `OperationCall` contracts rather than an `Agent.execute(...)` bypass.

Its Operations are currently marked `PUBLIC` because the transitional console host path can only enter generic installed `PUBLIC` Operations. That does not establish that a future local owner conversation must be public Module-composition behavior.

This is not yet the target owner interaction product. The current console still owns the presentation loop, `interaction.*` is independent from `roles.core`, and delayed follow-up is primarily pulled through `/updates` rather than naturally re-entering owner interaction.

Those limitations are experiment opportunities, not reasons to move behavior into Kernel.

## Developer verification

Source development uses JDK 21 and the checked-in Gradle wrapper:

```text
./gradlew --no-daemon --build-cache check
./gradlew --no-daemon publish
```

The repository contains independent verification projects for Module SDK consumption, Module-to-Module interoperability and reasoning-provider SPI consumption. The external SDK consumer builds only against published public MADRE artifacts and uses the same code-first `Module` surface as shipped code.

Automatic pull-request validation is intentionally the lightweight Ubuntu root `check`. Cross-platform SDK/native/reasoning installed-product journeys are explicit manual verification workflows used for relevant boundary changes and release checkpoints rather than continuous development scheduling.

## Experimentation posture

MADRE should make it cheap for a developer—or generated code—to build many different Modules and Operations over ordinary software and heterogeneous inference/ML machinery without understanding Kernel internals.

That flexibility does not mean a universal agent framework. Stable SDK surface should remain small but may include narrow optional OOP abstractions when a real substantial consumer proves a generic programming responsibility, focused tests preserve the invariants, and the abstraction does not impose one domain on unrelated code. It need not describe every Agent to be useful SDK.

Do not infer architecture from fashionable mechanisms. Embeddings, RAG, memory, semantic stores, planners, tool systems, macros, application wrappers and multimodal pipelines can all be legitimate experiments without becoming mandatory Kernel services or one universal Module taxonomy.