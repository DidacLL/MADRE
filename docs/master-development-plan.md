# MADRE Master Development Plan

## Status: completed foundation-plan record

This document records the foundation plan that produced the current Java implementation on PR #47. It is historical evidence, not MADRE's current immutable roadmap and not a queue to replay.

The Owner's current instructions, `MADRE.md`, the focused documents under `docs/architecture/`, and `docs/implementation-baseline.md` supersede historical sequencing and intermediate design choices recorded by this plan.

The foundation work was intentionally architecture-heavy and established several capabilities before the owner product exercised them fully. Preserve proven implementation where it remains correct, but do not infer that the next task is to continue generalizing those abstractions.

The Owner subsequently clarified an important sequencing rationale: acceptable owner UX is expected to require extensive experimentation in both semantic agentic engineering and heterogeneous inference/model tuning. Consequently, a solid SDK/test/harness experimentation substrate precedes freezing more of the present CORE/console interaction shape. This clarification is durable in `MADRE.md`; it must not be reinterpreted from the older sequence recorded below.

## Historical objective

The foundation plan aimed to create one Java 21 MADRE environment that could prove the essential runtime and public-development boundaries together:

```text
owner interaction
    -> ordinary installed Module behavior
    -> bounded reasoning request
    -> Kernel reasoning selection / durable execution
    -> real or deterministic reasoning mechanism
    -> Module-owned interpretation
    -> owner-local or external/public result
```

It also required independently implemented Modules to compile against public MADRE artifacts rather than application/Kernel implementation classes.

Those foundation goals are now substantially implemented. The authoritative list of what executes today belongs in `docs/implementation-baseline.md`.

## Completed foundation slices

### 1. Public algebra, SDK and executable Module model

The foundation established the nominal Security Algebra, typed Material/Module/Agent/Skill/Workflow/Operation model, exact executable Operation bindings, codecs and installation-provider contracts.

The active implementation now includes caller-bound installed Module composition, generic owner/debug host invocation, selected owner interaction and external/public host disclosure. Actual external/public disclosure requires Module-owned semantic transformation; these receiver/product boundaries preserve distinct semantics.

Independent verification builds prove that Module developers can compile against published SDK artifacts without taking `madre-app` or Kernel implementation dependencies.

### 2. Narrow reasoning Kernel and durable runtime

The foundation established the public `ReasoningService`/reasoning SPI split, deterministic mechanism selection, resources, immediate and durable execution, timeout/cancellation/retry, SQLite recovery and opaque result delivery.

The generic Kernel `Capability<C,R>` / generic action-dispatch direction was removed. The active reasoning SPI is intentionally limited to model/mechanism reasoning execution. Material meaning, semantic continuation and ordinary application I/O remain outside Kernel.

### 3. Independently installed Modules and reasoning mechanisms

The foundation established JAR/ServiceLoader installation for executable Modules and reasoning adapters, generic identity-scoped Module configuration, provider-owned reasoning configuration, the shipped owner-interaction Module, and independent reasoning/module acceptance on Windows and Linux.

The current tree also proves Module-to-Module receiver semantics, owner/debug versus external/public behavior, independently installed reasoning execution, and durable restart in which Kernel recovers opaque work while the originating Module recovers and interprets its semantic state.

### 4. Proven integrations retained as evidence

PR #47 has exercised real llama.cpp inference over the native AF_UNIX adapter. The current tree also retains ordinary reusable search values and a SearXNG client outside Kernel.

Integrations and external application exercises are evidence for implemented boundaries and SDK friction. They do not make an external provider, application, search system or transport part of MADRE's defining architecture unless current authority adopts that responsibility.

## Historical decisions that are not future sequencing authority

Several foundation-era decisions should not be treated as the next roadmap simply because they appear in older plan text or commits:

- CORE was initially exercised mainly as optional Module identity resolution. Current authority gives CORE meaningful default owner-interaction/coordinator semantics while preserving its non-privileged ordinary-Module status.
- `interaction.*` and the `MadreMain` console are replaceable product wiring rather than a universal UI protocol.
- Module configuration remains provider-owned and domain-specific; reasoning-provider configuration has its own proven descriptor/configurator contract. Do not generalize these into one universal settings schema without evidence.
- Native Windows/Linux owner packaging with bundled Java and zero-argument first-run bootstrap is implemented foundation behavior rather than a future deployment gate.
- Independent SDK fixtures prove contract isolation, not a complete experimentation/developer product.
- `model-agnostic` never meant that inference contracts must remain minimal. Portable request semantics may evolve in typed computation contracts; engine/model/runtime tuning remains provider-owned; shared execution mechanics remain Kernel-owned.
- Module-to-Module invocation is valid proven infrastructure. Its exposure semantics are now Module-owned through `exposedOperations`, while foreign nominal Material contracts are declared through `foreignMaterialReferences`.
- Historical references to generic physical `Capability`, `ExecutionService`, `WorkRequest`, Kernel search, or Operation PUBLIC/PRIVATE visibility are superseded by the active architecture and must not be restored by replaying old slices.

## Current product gates

The current product direction is summarized here only so this historical record cannot be mistaken for an active roadmap. Durable meaning and Owner reasoning are in `MADRE.md` and `AGENTS.md`.

Native owner deployment and generic reasoning-provider configuration are implemented foundations. Current outcome-oriented gates are:

1. solid SDK experimentation/developer foundation: consumable/aligned artifacts, documentation, testkit/harness support, packaging/authoring ergonomics, explicit stable-versus-experimental lifecycle and a complete independent developer journey;
2. heterogeneous inference experimentation through typed common computation contracts and provider-owned low-resource/model/runtime tuning, adding new computation families only from concrete experiments;
3. iterative CORE/owner UX engineering using those facilities, with only proven interaction abstractions graduating to stable contracts;
4. generic Module configuration contracts proven by actual Module configurator needs and provider-owned typed metadata;
5. broader reusable integration surfaces such as semantic stores/knowledge graphs, UI extraction, Skills/MCP libraries, audio or multimodal UX only as concrete experiments demonstrate value.

These are outcome gates, not class/API prescriptions. SDK-first means making experimentation cheap and rigorous, not implementing every possible agentic subsystem.

## Preserved architectural invariants

Future work must preserve the foundation already proven:

- Kernel owns only live Module receiver mechanics and shared reasoning runtime responsibilities.
- Modules own semantic/application behavior, state, persistence, Material, workflows, interpretation, continuation, domain integration and domain UX.
- ReasoningCapabilities realize model/mechanism reasoning only.
- portable inference request/result semantics belong to typed computation contracts; provider/model/runtime tuning belongs to adapters/providers; shared execution mechanics belong to Kernel.
- ordinary application I/O does not become a Kernel capability by default.
- Module-to-Module exposure, owner/debug entry, owner interaction and external/public disclosure are distinct concerns.
- mandatory semantic result transformation applies to actual external/public disclosure.
- CORE remains an ordinary non-privileged Module role.
- host product-management responsibility remains outside CORE.
- Security Algebra remains cross-cutting value semantics rather than a service.

## Excluded from this record as roadmap commitments

Research and exploratory material about external applications, voice, MCP, marketplace/update services and other systems remains evidence only unless the Owner explicitly accepts a product direction.

This historical plan therefore does not authorize native installer redesign, desktop UI, new generic configuration-schema design, UI/surface frameworks, Skill runtime redesign, MCP runtime, external-process Module transport, public marketplace/update services or adjacent work by itself.

## Authority reminder

For a new implementation run:

- use `MADRE.md` for product meaning and Owner reasoning;
- use `docs/architecture/*` for active architectural boundaries;
- use `docs/implementation-baseline.md` for current executable truth;
- use `README.md` for the current runnable developer path;
- use this file only to understand the completed foundation and why the current system has the shape it has.
