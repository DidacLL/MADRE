# MADRE Master Development Plan

## Status: completed foundation-plan record

This document records the foundation plan that produced the current Java implementation on PR #47. It is historical evidence, not MADRE's current immutable roadmap and not a queue to replay.

The Owner's current instructions, `MADRE.md`, the focused documents under `docs/architecture/`, and `docs/implementation-baseline.md` supersede historical sequencing and intermediate design choices recorded by this plan.

In particular, the foundation work was intentionally architecture-heavy and established several capabilities before the owner product exercised them fully. Preserve proven implementation where it remains correct, but do not infer that the next task is to continue generalizing those abstractions.

## Historical objective

The foundation plan aimed to create one Java 21 MADRE environment that could prove the essential runtime and public-development boundaries together:

```text
owner interaction
    -> ordinary installed Module behavior
    -> bounded reasoning request
    -> Kernel reasoning selection / durable execution
    -> real or deterministic reasoning mechanism
    -> Module-owned interpretation
    -> owner-local or external/PUBLIC result
```

It also required independently implemented Modules to compile against public MADRE artifacts rather than application/Kernel implementation classes.

Those foundation goals are now substantially implemented. The authoritative list of what executes today belongs in `docs/implementation-baseline.md`.

## Completed foundation slices

### 1. Public algebra, SDK and executable Module model

The foundation established the nominal Security Algebra, typed Material/Module/Agent/Skill/Workflow/Operation model, exact executable Operation bindings, codecs and installation-provider contracts.

The active implementation now includes caller-bound installed Module composition, owner-local host invocation and external/PUBLIC host invocation. External/PUBLIC disclosure requires Module-owned semantic transformation; Module-to-Module and owner-local receivers preserve their distinct semantics.

Independent verification builds prove that Module developers can compile against published SDK artifacts without taking `madre-app` or Kernel implementation dependencies.

### 2. Narrow reasoning Kernel and durable runtime

The foundation established the public `ReasoningService`/reasoning SPI split, deterministic mechanism selection, resources, immediate and durable execution, timeout/cancellation/retry, SQLite recovery and opaque result delivery.

The generic Kernel `Capability<C,R>` / generic action-dispatch direction was removed. The active reasoning SPI is intentionally limited to model/mechanism reasoning execution. Material meaning, semantic continuation and ordinary application I/O remain outside Kernel.

### 3. Independently installed Modules and reasoning mechanisms

The foundation established JAR/ServiceLoader installation for executable Modules and reasoning adapters, generic identity-scoped Module configuration, provider-owned reasoning configuration, the shipped owner-interaction Module, and independent reasoning/module acceptance on Windows and Linux.

The current tree also proves Module-to-Module receiver semantics, sensitive owner-local versus external/PUBLIC behavior, independently installed reasoning execution, and durable restart in which Kernel recovers opaque work while the originating Module recovers and interprets its semantic state.

### 4. Proven integrations retained as evidence

PR #47 has exercised real llama.cpp inference over the native AF_UNIX adapter. The current tree also retains ordinary reusable search values and a SearXNG client outside Kernel.

These integrations are evidence for the implemented boundaries. They do not make any external provider, search system or transport part of MADRE's defining architecture.

## Historical decisions that are not future sequencing authority

Several foundation-era decisions should not be treated as the next roadmap simply because they appear in older plan text or commits:

- CORE was initially exercised mainly as optional Module identity resolution. The durable product definition now gives CORE meaningful default owner-interaction/coordinator semantics while preserving its non-privileged ordinary-Module status.
- `interaction.*` and the `MadreMain` console currently provide presentation wiring independently of CORE. This is transitional executable truth, not the desired final owner-product responsibility split.
- Module configuration remains generic string delivery with provider-owned parsing; its owner-facing generic configurator is still unfinished and must recover only the minimum Module-provider-owned typed metadata demonstrated by real owner behavior. Reasoning-provider configuration has since advanced beyond the original plan: the real owner configurator now uses a narrow provider-owned descriptor/configurator contract with stable provider identity and `TEXT`/`INTEGER`/`CHOICE` metadata. That implemented reasoning contract must not be replayed as future work or generalized into a universal settings schema.
- Native Windows/Linux owner packaging with bundled Java and zero-argument first-run bootstrap is now implemented foundation behavior rather than a future deployment gate.
- Independent SDK fixtures prove contract isolation, not a complete community developer product.
- Module-to-Module invocation is valid proven infrastructure even though its generalization arrived before CORE exercised it. Preserve it; extend its public shape only from real use.
- Historical references to generic physical `Capability`, `ExecutionService`, `WorkRequest` or Kernel search are superseded by the active reasoning-specific architecture and must not be restored by replaying old slices.

## Current product gates

The current product direction is summarized here only so this historical record cannot be mistaken for an active roadmap. The durable statement is in `MADRE.md` and `AGENTS.md`.

Native owner deployment and generic reasoning-provider configuration are implemented foundations. The remaining near-term product gates are:

1. meaningful CORE-led owner interaction with natural delayed semantic follow-up;
2. generic Module configuration contracts proven by the actual Module configurator and provider-owned typed metadata it truly needs;
3. genuinely public SDK/tooling/testkit/developer journey;
4. broader integration surfaces such as UI extraction, Skills/MCP libraries or multimodal support only after concrete use demonstrates need.

These are outcome gates, not class/API prescriptions. Live dependency analysis may change implementation order when necessary, but it must not use that freedom to introduce unrelated frameworks or integrations.

## Preserved architectural invariants

Future work must preserve the foundation that has already been proven:

- Kernel owns only live Module receiver mechanics and shared reasoning runtime responsibilities.
- Modules own semantic/application behavior, state, persistence, Material, workflows, interpretation, continuation, domain integration and domain UX.
- ReasoningCapabilities realize model/mechanism reasoning only.
- ordinary application I/O does not become a Kernel capability by default.
- Module-to-Module, owner-local and external/PUBLIC are distinct receiver boundaries.
- mandatory semantic result transformation applies to actual external/PUBLIC disclosure.
- CORE remains an ordinary non-privileged Module role.
- host product-management responsibility remains outside CORE.
- Security Algebra remains cross-cutting value semantics rather than a service.

## Excluded from this record as roadmap commitments

Research and exploratory material about QVAC, AAAAT, OpenWhispr, voice, MCP, marketplace/update services and other external systems remains evidence only unless the Owner explicitly accepts a product direction.

This historical plan therefore does not authorize native installer redesign, desktop UI, new generic configuration-schema design, UI/surface frameworks, Skill runtime redesign, MCP runtime, external-process Module transport, public marketplace/update services or other adjacent work by itself.

## Authority reminder

For a new implementation run:

- use `MADRE.md` for product meaning;
- use `docs/architecture/*` for active architectural boundaries;
- use `docs/implementation-baseline.md` for current executable truth;
- use `README.md` for the current runnable developer path;
- use this file only to understand the completed foundation and why the current system has the shape it has.