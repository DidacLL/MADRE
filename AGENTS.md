# MADRE Agent Harness

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. It has two equally important product identities: an owner-installed application that must be usable without understanding its implementation machinery, and a public development platform on which unrelated developers can build independently installable Modules and reasoning mechanisms.

Optimize for one coherent usable product and durable public contracts, not architecture demonstrations or framework growth.

## Authority

Use the current Owner request as the task goal and `MADRE.md` as the durable product model. Read every focused document in `docs/architecture/`, then `docs/implementation-baseline.md`, before implementation.

The authority hierarchy is:

1. the Owner's current instruction;
2. `MADRE.md` for durable product meaning and responsibility;
3. `docs/architecture/*` for focused architectural boundaries;
4. `docs/implementation-baseline.md` for what actually executes now;
5. code, tests and history as implementation evidence.

`docs/master-development-plan.md` is the historical/completed foundation-plan record, not an immutable roadmap. Historical code, tests, deleted documents and familiar software patterns are evidence only. Recover Owner intent rather than preserving a sequencing decision because it is detailed or tested.

Third-party libraries, protocols, providers, models and runtimes may constrain an adapter implementation, but they do not define MADRE architecture. Research on external systems is evidence only unless the Owner explicitly accepts a direction.

## Product and responsibility boundary

- The MADRE host product owns product-management mechanics: installation and uninstallation, persistent product configuration, Module and reasoning-artifact lifecycle, CORE selection, startup/shutdown, diagnostics, health and the owner-facing mechanics needed to operate the installed product.
- A Module owns meaning, domain state, persistence, Material, transformations, Agents, Skills, Agent-owned Workflows, interpretation, continuation, domain-specific integrations, domain-specific UX and bounded Operations.
- An Operation is bounded Module/application behavior. Ordinary file, network, database, device, search, MCP or similar I/O remains ordinary Module/application behavior unless a concrete shared Kernel responsibility is established.
- Kernel stays narrow. It owns the live executable Module registry and receiver mechanics plus the shared reasoning runtime: reasoning-mechanism registration/selection, resources, immediate/durable reasoning, retry, cancellation, persistence, delivery and runtime logging.
- Installed Module composition uses caller-bound `ModuleDirectory`/`ModuleInvoker` facades. Runtime assembly binds the canonical caller identity; Module code does not supply a caller identity or arbitrary receiving Privacy. Contract-valid foreign results are receivable only for canonically referenced foreign Material types that can reach fixed `Privacy.MODULE`.
- External/PUBLIC disclosure is a separate host boundary. `PublicResultTransformer` remains mandatory there and is not run merely because one installed Module invoked another.
- `OwnerModuleInvoker` and the external/PUBLIC host invoker remain outside `ModuleContext`. PRIVATE Operations remain Module-internal.
- `ReasoningCapability` is intentionally narrow. It accepts only nominal `ReasoningComputation<R>` values and returns reasoning results. It knows no Module, Agent, Operation, Workflow, Material, external action or semantic continuation.
- Reasoning adapters are ordinary owner-installable JVM artifacts discovered independently from Modules through the public reasoning-adapter SPI. `madre-app` must not know concrete reasoning-provider types or parse provider-specific configuration.
- Module installation and reasoning-mechanism installation are distinct. They use separate directories, service-provider contracts and identities.
- Search is not a Kernel capability. SearXNG is an ordinary reusable client for domain Modules or applications that legitimately need web search.
- Provider account/session mechanics remain outside MADRE unless a concrete product requirement later assigns them to an appropriate host or Module boundary.

Kernel may persist opaque bytes originating from a reasoning request. That never transfers Material ownership or semantic meaning to Kernel.

## CORE

CORE is the installation role identifying MADRE's default owner-interaction/coordinator Module. That meaning is useful, but it confers no privilege.

A CORE Module remains an ordinary installed Module. CORE assignment must not change Security Algebra, Operation visibility, Module/owner-local/external-PUBLIC invocation authority, reasoning installation or selection, scheduling, class-loader treatment, installation authority or host invocation authority. Do not introduce a privileged `CoreModule` subtype.

The target owner experience is semantically led by CORE: foreground conversation, reasoning choices, useful delayed semantic follow-up, and ordinary coordination/routing to installed Modules. The host product still owns product-management mechanics. CORE does not install artifacts, own global configuration, manage process lifecycle, become Kernel, or receive host-only invocation ports through `ModuleContext`.

Do not freeze speculative CORE Operation names, a universal surface interface or a UI framework. Recover the smallest public structural contract from actual owner-interaction behavior when implementation pressure requires it. The present `roles.core` lookup, `interaction.*` binding and `MadreMain` console split are executable transitional behavior, not the final product contract.

## Installation and configuration

Module providers declare their canonical `ModuleId` before materialization and receive immutable owner configuration scoped to that exact identity through the public Module SDK. `madre-app` may extract/deliver the current generic `modules.config[<ModuleId>].*` namespace, but Module-specific keys, parsing, validation and typed settings remain inside the installed Module artifact.

Reasoning-provider configuration follows the same ownership principle through the reasoning installation boundary.

The current string configuration is executable truth, not the final owner configurator contract. A generic owner-facing configurator must not hard-code settings belonging to independently installed Modules or reasoning providers. When that configurator is implemented, recover the minimum provider-owned typed metadata it actually requires. Do not invent a general schema framework in advance.

## Security Algebra

Sensitivity, Privacy, Integrity, Risk and Autonomy are distinct ordered public types. Each owns only its intrinsic immutable composition.

- Sensitivity combines by maximum.
- Privacy combines by minimum.
- Integrity combines by minimum.
- Information can reach a receiver only while accumulated Sensitivity is no greater than accumulated Privacy.
- One EffectProfile uses only its own Risk and Autonomy.
- Its non-user causal demand is `min(Risk, Autonomy)`, supported by the minimum Integrity of actual non-user causal participants, or I5 when there are none.
- Values from different EffectProfiles never combine.

`Risk` is Module-domain consequence metadata. It is not propagated into `ReasoningRequest`, and `ReasoningCapability` manifests do not carry action-realizer Integrity. A reasoning mechanism does not become the physical realizer of a Module's external effect merely because a Module used inference while deciding what to do.

Attach a value directly to the real object or contract where it applies. Do not add a universal optional-value container, generic security object, surface identity, policy decision, evidence/history system or checking service.

`Privacy.UNKNOWN` is explicit P2 for an applicable third-party boundary outside owner control. It is not missing information and is never inferred from locality or provider identity. `Privacy.MODULE` is the structural P4 receiver contract for declared foreign Material crossing between installed Modules; it is not caller-chosen per invocation.

Security Algebra is a cross-cutting value model, not another runtime service.

## Public engineering model

The implementation is Java 21. Use nominal types, immutable records/final classes, generics, sealed hierarchies only for genuinely closed domains, and small responsibility-specific interfaces. JSON is a codec boundary, never the domain programming model.

Windows and Linux are first-class hosts for the same application, Kernel, SDK, Modules, reasoning-adapter installation, persistence and ordinary execution path. Windows is not a compatibility port of a Unix design.

Module developers may depend on published SDK and relevant computation-contract artifacts, but not on `madre-app` or Kernel runtime implementation classes merely to receive installation configuration, discover/invoke compatible installed Modules or execute bounded Module behavior. Reasoning-adapter developers may depend on the public reasoning SPI and relevant computation contracts, but not on application/Kernel implementation merely to register a mechanism.

The existing independent SDK/reasoning fixtures prove strong contract isolation. They do not by themselves make MADRE a community-ready development platform. Treat stable consumable publication, documentation, tooling/testkit, packaging conventions and a complete independent developer journey as a product gate to be proven explicitly.

Module-to-Module invocation is proven infrastructure and must remain available. Do not remove it because its generalization preceded real CORE use. Equally, do not keep expanding its public shape without concrete product demand before 1.0.

Do not add containers, VM layers, orchestration systems, hosted services or provider accounts to the mandatory build/CI/product path without a concrete Owner-accepted requirement. Prefer the JDK, checked-in Gradle wrapper, native host execution and the smallest real dependency required by the behavior under test.

Do not restore the removed Python packages class-for-class. In particular, do not restore Material-aware capabilities, a generic `Capability<C,R>` Kernel SPI, universal physical-action dispatch, autonomous input/output surfaces, a universal Module/Agent loop, arbitrary property bags or test-only product Modules.

Tests establish mathematical invariants, public contracts, failure mechanics and real integrations. A fixture may replace an external reasoning mechanism for deterministic tests, but it cannot stand in for claimed product behavior.

## Product gates and sequencing

After the current product-contract recovery, prioritize concrete product proof rather than new abstractions:

1. an owner-deployable Windows/Linux product that does not require the owner to understand JDK, Gradle, classpaths, ServiceLoader or internal property namespaces;
2. a meaningful CORE-led owner interaction experience, including natural delayed semantic follow-up rather than a diagnostic collection command as the primary UX;
3. generic configuration contracts proven by the actual owner configurator, with provider-owned typed metadata introduced only to satisfy demonstrated configurator needs;
4. a genuinely public SDK/tooling/testkit/developer journey in which an unrelated developer can consume stable artifacts, build/test/package an independent Module, install it and expose domain behavior without application/Kernel implementation dependencies;
5. only then broader community integration surfaces such as UI extraction, Skills/MCP standard libraries, audio/multimodal support or similar facilities when concrete use demonstrates the need.

Do not convert these gates into speculative API design. A later gate may be reordered only when live dependency analysis shows that doing so is necessary to complete an earlier product outcome.

## Delivery discipline

Current executable truth belongs in `docs/implementation-baseline.md`; durable product meaning belongs in `MADRE.md`; focused architecture belongs in `docs/architecture/`; runnable current instructions belong in `README.md`.

For each coherent change:

1. inspect current public contracts and the affected acceptance journey;
2. identify the owner-visible or developer-visible outcome before inventing abstractions;
3. implement complete behavior across its real boundaries;
4. exercise behavior proportionally, including real execution for real-execution claims;
5. review the changed surface for responsibility leakage and platform/framework assumptions;
6. commit and push a coherent green result;
7. never merge without explicit Owner instruction.

MADRE has no installed-base compatibility obligation for discarded prototypes. Replace incompatible development artifacts directly and preserve unrelated Owner changes.