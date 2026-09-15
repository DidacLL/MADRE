# MADRE Agent Harness

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It has two equally important product identities: an owner-installed application and a public development/experimentation platform for independently built Modules and reasoning mechanisms.

Optimize for a coherent usable product, fast experimentation and durable public contracts. Do not grow architecture for its own sake.

## Authority

Use this order when resolving conflict:

1. the Owner's current instruction;
2. `MADRE.md` for durable product meaning and Owner reasoning;
3. `docs/architecture/*` for focused responsibility boundaries;
4. `docs/implementation-baseline.md` for executable truth;
5. current code/tests/history as implementation evidence.

`docs/master-development-plan.md` is historical/completed planning evidence, not an immutable roadmap. Familiar AI-platform patterns and external systems are evidence only unless the Owner accepts them.

Before changing architecture or public SDK contracts, re-check live repository truth and reconcile the task against the actual branch head.

## Mandatory understanding gate for orchestration

Before proposing a new architectural/product slice, an orchestrator must be able to answer these questions from current authority and live code:

1. What is MADRE, and why are owner product and public experimentation platform equally important?
2. What creates a Module boundary, and which technical primitives do not create one?
3. What belongs to Module/Agent/Operation, to Kernel, to reasoning provider/adapter, and to the host product?
4. What does Security Algebra govern, and which values must never be inferred from locality/provider/model/process/CORE status?
5. What is CORE, and what privilege does it receive?
6. Why are Java executable objects separated from portable Module descriptions, and where does Java payload typing live?
7. How should unknown or unproven generated/experimental code be represented?
8. What evidence is required before adding a stable SDK abstraction?

If an answer conflicts with `MADRE.md`, focused architecture or live executable truth, resolve that conflict before implementation.

## Owner reasoning

The Owner does not expect one assistant architecture chosen in advance to produce the final UX. MADRE must make experimentation cheap on two coupled sides.

Semantic/application experiments may involve Agents, Skills, Workflows, context/request construction, semantic memory/knowledge structures, retrieval, coordination, Module composition and different immediate/durable reasoning patterns.

Inference experiments may involve heterogeneous origins/runtimes/models, richer portable computation contracts, embeddings, multimodal work and provider/model/runtime tuning, especially for local low-resource models.

These are experiment areas, not architecture decomposition, roadmap or mandatory Module inventory. A technical capability earns a higher-level stable abstraction only when real semantic software demonstrates ownership, reuse and repeated value.

`model-agnostic` means Kernel is independent of concrete model/runtime implementations. It does not require inference contracts to be feature-poor. Portable inference semantics belong to typed computation contracts; mechanism/model/runtime-specific tuning belongs to providers/adapters; shared reasoning scheduling/selection/resources/durability belongs to Kernel.

## Semantic ownership

A Module boundary exists only when coherent application/domain behavior owns meaning, state, interpretation, bounded Operations and domain semantics.

Technical facilities such as search, embeddings, files, databases, transports, model APIs, storage, MCP or devices may be ordinary libraries/mechanisms used by a Module. Technical reuse does not create a Module.

Do not build one Module per inference capability. Do not turn a new inference primitive into a Material/Agent/Workflow/stable semantic SDK concept unless a real semantic consumer proves the abstraction.

Correct direction:

```text
owner/domain software
  -> semantic Module/SDK objects
  -> ReasoningService
  -> Kernel reasoning runtime
  -> heterogeneous reasoning mechanisms
```

Not:

```text
new inference feature
  -> new Module
  -> new product architecture
```

## Product responsibilities

The host product owns installation/uninstallation, persistent product configuration, Module/reasoning artifact lifecycle, CORE selection, startup/shutdown, diagnostics/health and owner-facing product mechanics.

A Module owns meaning, domain state/persistence, Material, transformations, optional Agents, Skills, Workflows, interpretation, continuation, domain integrations/UX and bounded Operations. Ordinary application I/O stays in application/Module code unless a concrete shared Kernel responsibility is established.

An Agent is an optional Module-owned intelligent actor. There is no universal Agent loop, planner, memory model, prompt framework or tool loop.

An Operation is one bounded Module behavior. It may request reasoning or perform ordinary application effects, but semantic interpretation and continuation remain Module-owned.

Kernel is deliberately narrow. It owns live executable Module registry/receiver mechanics plus shared reasoning registration/selection, resources, immediate/durable execution, retry, cancellation, opaque persistence and result delivery.

Reasoning providers/adapters are independently installed mechanism implementations. They own concrete provider/model/runtime configuration and tuning. `madre-app` must not know provider-specific fields.

Search is not a Kernel capability. Similar reusable technical primitives remain outside Kernel absent an explicit shared-runtime responsibility.

## Code-first public SDK

Java executable objects are the authoring source of truth:

```text
Module
Agent                     optional
MaterialType<T>
Operation<I,O>
OperationBinding<I,O>
OperationCall<I,O>
```

Portable descriptions are derived:

```text
ModuleDefinition
AgentDefinition
MaterialTypeDefinition
OperationDefinition
SkillDefinition
WorkflowDefinition
EffectProfile
```

`OperationDefinition` is language-neutral and non-generic. Java payload typing lives on executable Operation/Call/Binding objects. `MaterialTypeDefinition` contains nominal identity/content type; Java class and codec belong to `MaterialType<T>`.

`ModuleInstance` is validated runtime/adaptor assembly, not the ordinary Module authoring model. `ModuleProvider.create(...)` returns the executable `Module`.

An unproven Java Agent defaults to `Integrity.I1`; generated or experimental code must not invent stronger assurance. Lack of proof should reduce trust/composability, not make arbitrary local software impossible.

`madre-sdk-experimental` is an explicit 0.x incubation boundary. It currently contains no public authoring helper after removal of the obsolete definition-first builder. Stable SDK, Kernel and reasoning SPI must not depend on experimental facilities.

Add authoring conveniences only when repeated real Module code demonstrates generic, ownership-correct friction. LLM-friendly means few orthogonal concepts and strong local invariants, not a dynamic metadata bag or universal framework.

## Module composition

Installed Module composition uses caller-bound `ModuleDirectory` / `ModuleInvoker`. Runtime binds canonical caller identity; Module code cannot provide caller identity or arbitrary receiving Privacy.

PRIVATE Operations remain Module-internal. Module-to-Module calls target installed `PUBLIC` Operations and use fixed `Privacy.MODULE`.

A foreign result crosses unchanged only when the caller declares its type in `publicMaterialReferences` and its Sensitivity can reach `Privacy.MODULE`. The caller may then create new caller-owned interpretation Material.

External/PUBLIC disclosure is a different host boundary requiring `PublicResultTransformer`. Owner-local invocation is another host boundary returning valid Module Material unchanged. Neither host-only port is present in `ModuleContext`.

CORE assignment changes none of these boundaries.

## CORE

CORE identifies MADRE's default owner-interaction/coordinator Module. It is semantically meaningful and non-privileged.

CORE assignment must not alter Security Algebra, Operation visibility, Module/owner-local/external-PUBLIC authority, reasoning installation/selection, scheduling, classloader treatment, installation authority or host ports. Do not create a privileged `CoreModule` subtype.

The target owner experience is semantically led by CORE: foreground conversation, reasoning choices, useful delayed semantic follow-up and ordinary coordination/routing to installed Modules. The host still owns product management.

The current `roles.core`, `interaction.*` and `MadreMain` split is transitional executable behavior. Do not freeze current Operation names or console commands into a universal CORE API.

## Installation and configuration

Module providers declare canonical `ModuleId`, optional generic owner-facing configuration metadata, validation/canonicalization, and materialize an executable `Module` from `ModuleContext` plus identity-scoped `ModuleProviderConfiguration`.

Generic Module owner configuration is implemented. The host supports provider discovery, `modules list`, `inspect`, `configure`, local-file install/replace and uninstall/purge without materializing Modules for configuration/lifecycle commands. Stable field kinds are currently only demonstrated `TEXT`, `INTEGER`, `CHOICE`.

Reasoning-provider configuration is separately implemented through provider-owned descriptors/configurators for repeatable named instances. Zero configured/materialized mechanisms remains valid.

Do not generalize these proven domain-specific contracts into one universal settings/property framework without demonstrated need.

Managed artifact lifecycle distinguishes `shipped`, lifecycle-managed `owner`, manually placed `manual`, and development override sources. Manual files remain discoverable but are not silently adopted/replaced/deleted. Shipped artifacts are protected. Module semantic state and Kernel durable reasoning work are not artifact bytes and are not deleted by uninstall.

## Security Algebra

Keep the five ordered carriers distinct:

```text
Privacy      SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, SECRET
Sensitivity  SYSTEM_RESERVED, S1, S2, S3, S4, S5
Integrity    SYSTEM_RESERVED, I1, I2, I3, I4, I5
Risk         SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy     SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Sensitivity combines by maximum; Privacy and Integrity combine by minimum. Information reaches a receiver only when accumulated Sensitivity is no greater than receiver Privacy.

One EffectProfile uses only its own Risk and Autonomy. Its non-user causal demand is `min(Risk, Autonomy)`, supported by minimum actual non-user causal Integrity, or I5 when there are none. Different EffectProfiles do not combine.

Material carries Sensitivity. Accepted Operation input contracts carry receiving Privacy. Agent carries/derives Integrity. EffectProfile carries Risk+Autonomy. Reasoning mechanism declares receiving Privacy.

Never infer algebra values from provider identity, endpoint, model, localhost, process/classloader placement, shipped status, Module bundling or CORE assignment.

Security Algebra governs MADRE-mediated semantic composition. It is not a general OS sandbox or a promise that arbitrary software the Owner installs is safe.

## Reasoning execution

`ReasoningCapability` is mechanism-only. It knows no Module, Agent, Operation, Workflow, Material, external action or semantic continuation.

A Module creates a typed `ReasoningComputation<R>` and `ReasoningRequest` from a valid bounded `OperationCall`. The request derives originating Module and carried Sensitivity and carries reasoning computation plus execution controls only.

If semantic context contains multiple source values, the Module must construct the actual contextual Material at combined Sensitivity and derive reasoning from a call over it. Do not add a raw Sensitivity override.

Kernel durable work remains opaque physical reasoning state. Module owns semantic association, interpretation and continuation. Do not add generic Kernel callbacks, continuation routers or scheduler languages.

## Verification economics

Verification is proportional to engineering risk. The standing PR signal is one lightweight Ubuntu root `check`. Expensive Windows/Linux SDK, reasoning configuration and native package journeys are explicit/manual acceptance tools used for relevant boundary changes, release checkpoints or Owner request.

Use focused tests during implementation and root `check` at coherent cross-project checkpoints. A relevant failing check is real evidence and must be understood before dependent work continues. Do not turn hosted CI into the development scheduler.

Cross-platform evidence remains important because Windows and Linux are first-class hosts, but do not reflexively dispatch every expensive workflow after every commit.

## Active product posture

Native owner deployment, Module/reasoning configuration, local artifact lifecycle, public SDK/testkit/experimental boundaries, independent developer fixtures and heterogeneous reasoning contracts are implemented substrate.

Choose new work from concrete owner/developer/semantic-programming friction. Current high-value evidence areas include owner interaction, real Module composition, developer authoring ergonomics and public artifact distribution—but no one item is automatically next merely because it is listed.

The shipped owner-interaction Module remains a useful reference consumer and is still transitional/single-turn in foreground reasoning. Its code-first refactor did not implement generic memory or conversation semantics.

Promote only proven reusable abstractions. Do not pre-build a universal Agent framework, memory/RAG system, planner/tool API, workflow language, semantic database abstraction or Module taxonomy.

## Delivery discipline

For each coherent change:

1. re-check live repository truth;
2. read current Owner request, `MADRE.md`, focused architecture and implementation baseline;
3. state the owner/developer-visible outcome before inventing abstraction;
4. implement behavior across its real boundaries;
5. test proportionally, including real execution for real-execution claims;
6. review for responsibility leakage and accidental framework assumptions;
7. update the appropriate authoritative documentation when executable truth changed;
8. commit/push coherent work;
9. investigate relevant failures;
10. never merge or enable auto-merge without explicit Owner instruction.

MADRE has no installed-base compatibility obligation for discarded prototypes. Preserve unrelated Owner changes, but replace obsolete 0.x development scaffolding when it conflicts with the recovered architecture.
