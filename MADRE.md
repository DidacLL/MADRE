# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It is simultaneously an installed owner product and a public software/inference experimentation platform. Neither identity is secondary.

The installed product lets an Owner run, configure and evolve one coherent local system without needing to understand Java classpaths, Gradle, ServiceLoader, provider property namespaces or MADRE's internal runtime structure. The public platform lets independent developers build, test and package Modules and reasoning mechanisms against the same contracts used by shipped software.

MADRE is not a hosted AI platform, an assistant architecture, a universal Agent framework, a plugin marketplace model, a SaaS control plane, a service-container abstraction, or a taxonomy that promotes every reusable technical primitive into architecture. The Owner may deliberately install, replace, configure or remove Modules and reasoning mechanisms independently. MADRE does not promise to sandbox arbitrary software the Owner chooses to install; operating-system isolation and installation trust remain separate concerns.

## Why MADRE exists

MADRE exists because useful owner-facing agentic software cannot be assumed to emerge from one assistant architecture, one model, one inference runtime or one fixed orchestration pattern chosen in advance.

Useful behavior may require experimentation with Module boundaries, Agents, Skills, Workflows, semantic state, retrieval/context construction, memory/knowledge structures, coordination, immediate/durable reasoning, alternative models and runtimes, richer generation controls, embeddings, multimodal work and mechanism-specific optimization. These are experimentation areas, not mandatory architectural components.

MADRE therefore makes experimentation cheap while preserving explicit responsibility. The framework should make it easy to write ordinary local software around heterogeneous inference engines without collapsing application meaning into Kernel, provider configuration into the host, or information/security boundaries into convenience APIs.

A technical capability earns a stable higher-level abstraction only when concrete semantic software demonstrates ownership and reusable value proportional to the abstraction's scope. A new inference primitive does not automatically justify a new Module, Material vocabulary, Agent model or Kernel subsystem.

`model-agnostic` means Kernel does not depend on concrete model/provider/runtime implementations. It does not mean inference contracts must be feature-poor. Portable inference semantics may be rich typed computation contracts; model/runtime-specific tuning remains provider-owned; shared execution mechanics remain Kernel-owned.

## The Owner and learning curve

The ordinary final user wants an installed product that is useful without first learning MADRE's architecture. The Owner should be able to start the product, converse or otherwise use installed application capabilities, understand meaningful product state and change ordinary configuration with low ceremony.

Control is progressively disclosed:

- ordinary use should require little or no knowledge of Module/Operation/Material internals;
- a power user should be able to inspect installed Modules and reasoning providers, the Module assigned CORE, relevant exposed capabilities, meaningful configuration and public-disclosure boundaries;
- a developer should be able to work directly with Module, Agent, Operation, Material, reasoning and Security-Algebra contracts.

The Owner should be able to know which software is installed, which Module is assigned CORE, which Module Operations are exposed for ordinary composition, which reasoning mechanisms are installed/configured/enabled, and when information is crossing a meaningful external/public boundary. The Owner should be able to edit product/module/provider configuration and role assignment without being forced to edit internal runtime data structures or Module-private semantic state.

Low learning curve does not mean flattening responsibilities. MADRE hides ceremony where possible while keeping semantic ownership, value ownership, receiver boundaries and trust facts explicit.

## Product layering and ownership

The durable responsibility model is:

```text
Owner
  |
  +--> MADRE host product-management surfaces
  |      installation / uninstallation
  |      persistent product configuration
  |      Module and reasoning-artifact lifecycle
  |      CORE selection
  |      startup / shutdown
  |      diagnostics / health
  |
  `--> owner interaction surfaces
         semantically led by the ordinary Module assigned CORE
         |
         +--> other installed Modules through ordinary Module composition
         |
         `--> ReasoningService when Module logic needs reasoning
                |
                v
              narrow Kernel reasoning/runtime machinery
                |
                v
              independently installed ReasoningCapabilities
```

Ordinary files, HTTP, databases, search, MCP, devices and similar application I/O remain Module/application responsibilities unless a concrete shared Kernel responsibility is established.

Security Algebra is cross-cutting behavior of values and contracts in this system. It is not another runtime service.

### Host product

The host product owns installation/uninstallation, persistent product configuration, Module/reasoning artifact lifecycle, CORE selection, startup/shutdown, diagnostics/health and owner-facing presentation mechanics. These responsibilities must not be pushed into CORE merely because CORE is owner-facing.

### Kernel

Kernel is deliberately narrow. It owns live executable Module registration/receiver mechanics and shared reasoning-runtime responsibilities: reasoning-mechanism registration and deterministic selection, resources, immediate/durable execution, retry, cancellation, persistence, result delivery and ordinary runtime logging.

Kernel may resolve which ordinary installed Module is assigned CORE. That does not make CORE privileged.

### Module

A Module is an independently installable semantic/application boundary. It exists because one coherent application/domain owns meaning, state, interpretation, bounded behavior and domain semantics. A Module may be agentless.

A Module owns domain/application state and persistence, Material it creates, optional Agents, Skills and Workflows, bounded Operations, domain transformations, semantic interpretation, continuation, application integrations and domain-specific UX.

Reusable technical primitives such as search clients, embeddings, databases, transports, storage, model APIs or MCP are not Modules merely because multiple applications may use them.

### Agent

An Agent is an optional Module-owned semantic actor. It owns semantic intent, interpretation, agent-specific state and continuation when the Module's behavior requires agency. There is no universal Agent loop, planner, prompt framework, tool loop or memory model.

An agentless Module remains fully valid. When an ordinary Owner wants to use capabilities of an agentless Module semantically, an Agent in another Module—normally the Agent in the ordinary Module assigned CORE—interprets intent, invokes the exposed capability, interprets the result and owns continuation. Agency must not be invented inside Kernel or an agentless callee merely for convenience.

### Skill and Workflow

A Skill is reusable Module-provided ability, knowledge or instruction. A Workflow is reusable Agent-owned semantic behavior. In the current minimal model a Workflow is an ordered sequence of Operations. It is not a Kernel scheduler language.

### Operation

An Operation is one bounded callable execution of Module logic through MADRE. It exists so that one execution can participate in MADRE's typed, modular, trust and Security-Algebra arbitration while the Module retains ownership of implementation and meaning.

PUBLIC/PRIVATE is not intrinsic Operation ontology. `OperationDefinition` contains only bounded execution facts: identity/purpose, accepted Material receiver Privacy, produced Material maximum Sensitivity and optional EffectProfiles.

Cross-Module exposure, generic owner/debug entry, owner interaction, external/public disclosure, presentation, reasoning use/locality and transport are separate concerns. None determines whether something is an Operation.

### EffectProfile

An EffectProfile describes one consequential execution variant of an Operation. It carries that variant's Risk and Autonomy. Reasoning computation is not itself a consequential external effect and does not justify an EffectProfile merely because reasoning contributed to a decision.

### Material and nominal contracts

A Material is a typed semantic value with nominal identity, payload and Sensitivity.

Concrete value ownership and nominal type ownership are distinct:

- `MaterialId.moduleId` identifies the Module that created/owns the concrete Material value;
- `MaterialTypeId.moduleId` identifies the Module that defines the nominal Material contract.

A Module may create a concrete value conforming to a foreign nominal contract when that contract is explicitly declared through `foreignMaterialReferences()`. The value remains owned by its creator. A callee may therefore return a callee-owned value conforming to a nominal contract defined by its caller or another Module.

Adapting or minimizing information creates new Material with new identity and explicit Sensitivity. The source remains unchanged.

### ReasoningCapability

A ReasoningCapability is one executable model/mechanism implementation of a reasoning contract. It exposes only the facts Kernel needs for reasoning selection/execution, such as reasoning contract, receiving Privacy, location/latency, resources and availability. It knows nothing about Modules, Agents, Workflows, Operations, Material, external actions or semantic continuation.

A reasoning adapter is an independently installable JVM artifact that provides one or more configured ReasoningCapability instances through the public reasoning-adapter SPI. Installation does not imply mechanism enablement.

## Module exposure and receiver boundaries

MADRE deliberately keeps four concerns distinct even where implementation mechanics can be shared.

### Module-to-Module exposure

Ordinary installed application composition uses caller-bound `ModuleDirectory` and `ModuleInvoker` supplied through `ModuleContext`. Runtime binds canonical caller identity; Module code cannot provide another caller identity or arbitrary receiver Privacy.

The target Module owns its cross-Module interface through `Module.exposedOperations()` / `ModuleDefinition.exposedOperations()`. Only Operations in that set are discoverable/invocable through ordinary Module composition. Exposure is not an Operation visibility level and is not an Algebra carrier.

Accepted-Material Privacy still governs whether the offered information can reach a particular Operation. Returned Material must be structurally understood by the caller—owned locally or declared in `foreignMaterialReferences`—and its Sensitivity must be able to reach fixed `Privacy.MODULE`.

When valid, the exact callee-created Material crosses unchanged. The callee remains the concrete value owner even if the Material's nominal type contract is defined by the caller or another Module.

`PublicResultTransformer` is not run on this path.

### Generic owner/debug entry

Generic host/debug entry is a separate host-only concern. It resolves an exact installed Operation and executes the same bounded `OperationCall` contract. It returns contract-valid Module Material unchanged.

This path is not Module exposure, ordinary owner conversation or external/public disclosure. It exists for expert/debug use and should not define target product semantics.

### Owner interaction

Owner interaction is another distinct host/product entry concern. The product uses `roles.core` as the single Module identity for ordinary owner interaction. The selected Module is ordinary and non-privileged.

An owning Module may create an exact `OperationBinding.ownerInteractionOperation(...)`; the host-only `OwnerInteractionInvoker` may enter only such a binding in the installed Module currently assigned CORE. The port is not supplied through `ModuleContext`, so CORE gains no authority to invoke another Module's internal Operations.

Every owner-interaction entry still executes a canonical `OperationCall`, preserving accepted-Material Privacy, EffectProfile selection, causal Integrity and output validation.

The current `interaction.*` settings bind replaceable host presentation mechanics to exact Operations/Material types on the selected CORE Module. They are not a second Module-selection identity and are not a universal UI protocol.

### External/public disclosure

Actual external/public disclosure is a distinct receiver boundary. `OperationBinding.publicDisclosure(...)` binds an exact Operation with a Module-owned `PublicResultTransformer` used only for this boundary.

The transformer must create new declared Material with a new identity. The transformed Material must satisfy the Operation's output contract and its Sensitivity must be able to reach `Privacy.PUBLIC`.

This is the durable meaning of `Privacy.PUBLIC`: information is actually crossing a public receiver boundary. It is not Module exposure, owner visibility, reasoning locality or an access-control label.

Whether an Operation is exposed to other installed Modules is independent from whether it has an external/public disclosure binding.

## CORE

CORE is the installation role identifying MADRE's default owner-interaction/coordinator Module. It is semantically meaningful but non-privileged.

A Module assigned CORE is still an ordinary Module. CORE assignment changes no Security Algebra value, Module exposure, generic owner/debug authority, external/public disclosure, Module-composition authority, reasoning selection, scheduling, class-loader treatment, installation authority or host authority. There is no privileged `CoreModule` subtype.

The target CORE responsibility is to lead ordinary agentic owner interaction: foreground conversation, reasoning choices, useful delayed follow-up, and coordination/routing to installed Modules through ordinary Module composition. It may own interaction state and semantics appropriate to that role. It does not own product installation, global lifecycle or host administration.

CORE is also a reference experiment for MADRE's SDK and inference surfaces. Improving its UX should exercise real Module/Agent/Operation engineering and heterogeneous inference capabilities. Stable interaction abstractions should be recovered from repeated successful experiments rather than frozen directly from the current console or current Operation names.

## Current owner-interaction implementation

The present local text console is a replaceable `madre-app` adapter. The shipped owner-interaction Module is an ordinary Module containing a stateful interaction Agent.

That Agent owns bounded persisted conversation state, contextual Material construction, foreground/background reasoning choices, pending durable-work association, interpretation, acknowledgement and the decision whether a useful owner-visible follow-up exists. Kernel persists only opaque reasoning-runtime state.

The current bounded Operations are `standard-prompt`, `fast-lane` and `collect-background`. They are exact owner-interaction entries, not cross-Module exposed capabilities merely because the Module is CORE. Their names and text representation remain 0.x implementation evidence rather than universal CORE contracts.

While the owner interaction surface is active, host presentation polls the Module's bounded collection Operation. If the Module decides completed work produces a useful follow-up, the host presents it. After restart, Module-owned conversation/pending state and Kernel durable work recover independently and rejoin through ordinary Module logic. `/updates` remains only a compatibility/debug command.

## Security Algebra

MADRE has five distinct ordered carriers:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity combine by minimum. Information can reach a receiver iff accumulated Sensitivity is no greater than accumulated Privacy.

For one EffectProfile the non-user causal demand is `min(Risk, Autonomy)` and must be supported by the minimum Integrity of actual non-user causal participants, or I5 when none exist. Values from different EffectProfiles do not combine.

Material carries Sensitivity. Accepted Operation input contracts carry receiving Privacy. Agent carries/derives Integrity. EffectProfile carries Risk and Autonomy. Reasoning mechanisms declare receiving Privacy.

Module exposure, owner/debug entry, owner-interaction entry, public-disclosure binding and CORE assignment are not Algebra carriers. They do not lower Material Sensitivity or grant a reasoning mechanism permission to receive information.

Security Algebra never infers values from localhost, provider identity, model name, endpoint, process/classloader placement, shipped/bundled status, CORE designation or Module exposure.

`Privacy.MODULE` is the structural receiver used for ordinary cross-Module Material delivery. `Privacy.PUBLIC` is the actual public receiver boundary. Neither is an access-control role.

## Reasoning execution and persistence

A Module creates a nominal `ReasoningComputation<R>` from a valid bounded `OperationCall` and submits a `ReasoningRequest` through `ReasoningService`. The request derives originating Module and carried Sensitivity and contains reasoning execution controls such as mode, priority, timing, timeout, cancellation, retry and typed selection preferences.

It contains no Agent, Workflow, concrete reasoning mechanism, semantic continuation or future output Material. It also contains no Operation Risk.

A reasoning computation contract is a typed description of one portable family of inference work and its result. Current public families include preserved text inference (`madre.text-inference.v1`), richer text generation (`madre.text-generation.v2`) and text embeddings (`madre.text-embedding.v1`). These are inference contracts, not Module-domain concepts.

Any Operation may request reasoning. Reasoning eligibility depends on actual carried Sensitivity, computation compatibility and the mechanism's explicit receiving Privacy/availability/resources. Module exposure and host entry selection are irrelevant to reasoning eligibility.

If a weaker receiver is desired, the Module must deliberately create new minimized/derived Material and issue work from that new context. Kernel never silently lowers Sensitivity.

Kernel durable work is opaque physical reasoning state. Module owns semantic association, interpretation and continuation. Durable work may outlive the foreground request and survive restart without turning Kernel into a conversation or workflow engine.

## Installation and configuration

Executable Modules are discovered as JVM JARs exposing `ModuleProvider`. Each provider declares canonical `ModuleId` before materialization and receives immutable identity-scoped owner configuration.

Installed providers expose provider-owned configuration metadata and validation. The host renders this metadata generically through Module lifecycle/configuration commands without hard-coding domain keys or materializing Modules merely to inspect configuration.

Reasoning-provider configuration is separate and provider-owned. Zero configured/materialized mechanisms remains valid.

Managed artifact lifecycle distinguishes shipped, lifecycle-managed owner, manually placed and development override sources. Manual files remain discoverable but are not silently adopted/replaced/deleted. Shipped artifacts are protected. Module semantic state and Kernel durable reasoning work are not artifact bytes and are not deleted merely because an artifact is uninstalled.

Windows and Linux are first-class hosts for the same MADRE application, Kernel, SDK, Module installation mechanism, reasoning-mechanism installation mechanism and persistence model. A concrete reasoning transport may be platform-specific; that difference remains inside its adapter.

## Development scope

MADRE development covers the installed product, the stable public Module/Agent/Operation/Material/Security-Algebra contracts, the narrow Kernel/runtime machinery, owner interaction/default CORE experience, packaging/lifecycle, and the SDK/test tooling needed for independently developed software to target the same runtime.

External applications used to test the SDK are evidence about developer friction; they are not automatically MADRE Modules, product dependencies or architecture authority. Examples, tests, UI shapes, current console commands, integration experiments, model strategies, retrieval techniques and provider adapters become durable architecture only when current authority explicitly adopts the underlying responsibility.

Do not pre-build a universal Agent framework, planner/tool system, workflow language, universal memory/RAG system, semantic-database abstraction, Module taxonomy, visibility/access-control lattice or SaaS control plane.

MADRE should remain one coherent runtime with clear ownership, progressive disclosure for the Owner and low-friction code-first experimentation for developers.
