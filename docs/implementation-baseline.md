# Implementation Baseline

This document records executable truth on the active Java 21 branch. Durable product meaning remains in `MADRE.md`; architecture responsibility rules remain in `docs/architecture/*`.

The last code-bearing checkpoint for this baseline is `2fa763c358edaf11949f2975b29e617714b069de`, which passed Fast validation run `35028121656` with the root Gradle `check`.

## Product/runtime baseline

MADRE is implemented as both an owner-installed local application and a public Module/reasoning experimentation platform. Windows and Linux packages contain the application, shipped artifacts and a bundled Java runtime. Mutable configuration/data/state live in owner-writable per-user locations; Kernel durable reasoning state and Module semantic state are separate resources.

The product can boot with no configured reasoning mechanism. Module installation and reasoning-provider installation are separate domains. CORE remains an optional ordinary Module role with no registration, invocation, Security Algebra or reasoning privilege.

The current console remains transitional. `interaction.*` selects the local presentation Module/Operations independently from `roles.core`; ordinary text and `/standard` use the owner-local receiver boundary, `/updates` invokes the configured Module-specific collection Operation, and generic owner/PUBLIC invocation remains available. This is not the final owner interaction contract.

## Code-first Module SDK

The stable Java SDK now has an explicit execution-side authoring model:

```text
Module
Agent                     optional
MaterialType<T>
Operation<I,O>
OperationBinding<I,O>
OperationCall<I,O>
```

A Java `Module` is the executable source of truth. It declares identity/version/purpose, Java Material bindings, optional foreign public Material references, optional Agents/Skills, and executable Operation bindings. `Module.definition()` derives the portable contract and `Module.instance()` derives the validated runtime assembly.

A Java `Agent` is an optional Module-owned semantic actor. It defines no universal loop, planner, memory, prompt or execution context. If no stronger causal-integrity claim has been established, `Agent.integrity()` defaults to `Integrity.I1`; authors may explicitly declare stronger Integrity when justified.

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

`Material` rejects `SYSTEM_RESERVED`, Java payload/type mismatch, and Material identity/type ownership mismatch at construction. Operation input/effect composition remains in `OperationCall`; produced type/owner/maximum Sensitivity remains validated through `OperationCall.acceptOutput` after Module behavior executes.

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

The stable owner-facing configuration field kinds remain exactly `TEXT`, `INTEGER` and `CHOICE`. Provider code owns key meaning, parsing/defaults and semantic validation. Configuration discovery/validation does not materialize the Module.

Raw compatibility persistence remains:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

The host supports `modules list`, `inspect`, `configure`, local-file `install --replace`, and `uninstall [--purge-configuration]` without giving Module code artifact-management authority.

## Security and invocation baseline

The public Security Algebra remains unchanged. It governs MADRE-mediated values and composition; it is not an OS sandbox or general permission system for arbitrary installed Java code.

Receiver boundaries remain distinct:

- Module-to-Module: caller-bound `ModuleInvoker`, installed `PUBLIC` Operation, fixed `Privacy.MODULE`, no public result transformer;
- owner-local: host-only `OwnerModuleInvoker`, canonical valid Module Material returned unchanged;
- external/PUBLIC: host-only `PublicModuleInvoker`, mandatory Module-owned semantic transformation to new PUBLIC-capable Material;
- `PRIVATE`: Module-internal.

A Module-to-Module result crosses unchanged only when the caller canonically references the foreign Material type and its Sensitivity can reach `Privacy.MODULE`. The caller may then create a new caller-owned interpretation Material.

Material adaptation or public minimization is semantic Module behavior. Runtime does not lower Sensitivity automatically.

## Reasoning baseline

`ReasoningRequest` is still structurally derived from a valid bounded `OperationCall`; originating Module and carried Sensitivity come from that call. Module code supplies a nominal `ReasoningComputation<R>` plus ordinary execution controls, not a concrete mechanism or Operation Risk.

Kernel remains responsible for compatible reasoning-mechanism selection, resource coordination, immediate/durable execution, retry/cancellation and opaque durable persistence. Modules remain responsible for semantic meaning, association, interpretation and continuation.

The reasoning SPI remains intentionally unchanged by the code-first Module cleanup. `ReasoningCapabilityManifest` contains genuine pre-execution mechanism-selection facts: nominal reasoning contract, receiving Privacy, location, expected latency and resources. Those facts are not a duplicate Module-definition graph.

Stable computation-contract artifacts remain `madre-text-inference`, `madre-text-generation` and `madre-embeddings`. Inference families do not imply corresponding Modules or semantic SDK abstractions.

## Testkit and experimentation baseline

`madre-sdk-testkit` materializes the provider's executable `Module`, validates the projected runtime assembly, and directly invokes exact bounded Operations. `ProgrammableReasoningService` remains generic over arbitrary `ReasoningComputation<R>` and exposes deterministic immediate/controlled durable behavior.

The testkit deliberately does not emulate Kernel mechanism selection, resource scheduling, retry timing, receiver-boundary enforcement or SQLite durability.

`madre-sdk-experimental` remains an explicit 0.x incubation artifact but currently exposes no public authoring helper. The former `ModuleDefinitionBuilder` was removed because it preserved the definition-first duplicate graph that code-first authoring eliminates. Stable SDK/runtime artifacts do not depend on the experimental artifact.

## Independent developer proof

`verification/sdk-consumer` remains a separate Gradle project that depends only on published public MADRE artifacts. Its Module now implements the same code-first stable `Module` contract used by shipped code. Its tests exercise:

- deterministic Module behavior through `madre-sdk-testkit`;
- generic text inference;
- stable text-generation and embedding computations;
- an arbitrary non-text `ReasoningComputation<Integer>`;
- projection from executable Module objects to the portable `ModuleDefinition`.

The external consumer no longer depends on `madre-sdk-experimental`.

`verification/module-interoperability` likewise implements its independent caller/callee as code-first Modules and uses portable non-generic `OperationDefinition` values for discovery/composition.

The repository still contains manual cross-platform acceptance workflows for the independent SDK, installed owner lifecycle, reasoning configuration and native packaging. They are explicit expensive verification tools rather than continuous development gates. The current code-first SDK cleanup is proven by root Fast validation; a fresh Windows/Linux extended SDK acceptance remains a relevant explicit release/checkpoint verification because this slice changes the public SDK boundary.

## Owner-interaction implementation baseline

`madre-module-owner-interaction` is an ordinary shipped code-first Module with one code-first Agent. Its current behavior is still the pre-conversation experiment:

- `standard-prompt`: immediate text inference from the current owner prompt;
- `fast-lane`: immediate foreground inference plus independently durable background reasoning and Module-owned pending association;
- `collect-background`: Module-owned interpretation, optional visible follow-up, acknowledgement and pending-state removal;
- explicit external/PUBLIC minimization to new S1 Material.

This cleanup did not add multi-turn conversation history, generic memory, RAG, embeddings, routing or natural asynchronous presentation. The current console still requires explicit `/updates` for delayed follow-up presentation.

A future multi-turn experiment must preserve the existing reasoning-sensitivity invariant: if historical/contextual information participates in a reasoning payload, the Module must first construct the actual contextual Material at the combined maximum Sensitivity and derive the reasoning request from a bounded call over that Material. A raw Sensitivity override is not part of the SDK.

## Local artifact lifecycle baseline

Managed local Module/reasoning JAR lifecycle remains unchanged by this SDK refactor:

- shipped artifacts are immutable;
- MADRE-installed owner artifacts occupy deterministic managed slots;
- directly copied JARs remain discoverable as `manual` but are never adopted/replaced/deleted by managed lifecycle;
- external explicit discovery overrides remain development inputs, not mutation roots;
- replacement validates staged bytes before touching the active artifact and closes discovery classloaders before mutation;
- Module semantic state and Kernel durable reasoning work are not deleted by artifact uninstall;
- Module and reasoning configuration purge remain domain-specific and exact-identity scoped.

## Current intentional limitations

There is no public remote artifact repository/catalog, marketplace, update feed, dependency bundle protocol, signature/PKI trust model, credential vault, sandbox, external-process Module transport, universal Agent loop, generic planner/tool framework, workflow scheduler, memory abstraction, RAG abstraction, semantic-database abstraction or arbitrary metadata/property framework.

These absences are deliberate until concrete experiments demonstrate an ownership-correct reusable contract. The SDK strategy is to make ordinary semantic software cheap to author while keeping stable concepts few, explicit and composable.
