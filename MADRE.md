# MADRE

MADRE is personal, owner-sovereign, local-first software for modular agentic applications. Its defining runtime capability is model-agnostic reasoning that may continue durably after a useful foreground result has already been returned.

MADRE has two equally important identities:

1. an end-user product the Owner installs, configures, launches and uses without needing to understand Gradle, Java classpaths, ServiceLoader or MADRE's internal property namespaces;
2. an application-development platform through which an unrelated developer can consume stable public artifacts, build/test/package an independent Module, install it into MADRE and expose domain behavior without depending on application or Kernel implementation classes.

MADRE is not a hosted AI platform and does not attempt to protect the owner from software the owner deliberately installs. The owner may install, replace, configure or remove Modules and reasoning mechanisms independently.

## Product layering

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
         `--> ReasoningService when semantic behavior needs reasoning
                |
                v
              narrow Kernel reasoning/runtime machinery
                |
                v
              independently installed ReasoningCapabilities
```

Ordinary files, HTTP, databases, search, MCP, devices and similar application I/O remain Module/application responsibilities unless a concrete shared Kernel responsibility is established.

Security Algebra is cross-cutting behavior of values and contracts in this system. It is not another runtime service.

## Host platforms

Windows and Linux are first-class hosts for the same MADRE application, Kernel, SDK, Module installation mechanism, reasoning-mechanism installation mechanism and persistence model. Windows is not a compatibility port of a Unix implementation, and hosted Linux CI is not a product architecture dependency.

A concrete reasoning transport may be platform-specific. That difference remains inside its adapter and does not justify separate Kernel, SDK, Module, Workflow or Security Algebra architectures.

## Building blocks

A **Module** is an owner-installed application or integration. It owns meaning, domain state, persistence, Material, transformations, Agents, Skills, Workflows, interpretation, continuation, domain-specific integrations, domain-specific user experience and bounded Operations.

An **Agent** is a Module-owned intelligent actor. It has no universal loop or assistant behavior. Its available behavior comes from learned Skills, Agent-owned Workflows and Operations its Module exposes.

A **Skill** is reusable Module-provided ability, knowledge or instruction.

A **Workflow** is reusable Agent-owned semantic behavior. In the current minimal model it is an ordered sequence of Operations. It is not a Kernel scheduler language.

An **Operation** is one explicitly bounded piece of Module behavior. It accepts declared Material, may transform it, may request reasoning, may perform ordinary application I/O, interprets results, creates new Material and decides whether its Module continues.

An **EffectProfile** represents one bounded consequential execution variant of an Operation. It carries that variant's Risk and Autonomy. Reasoning computation is not itself a consequential external effect and does not justify an EffectProfile by itself.

A **Material** is a typed Module-owned value with nominal identity, content type, payload and Sensitivity. Adapting information creates new Material with a new identity and explicit Sensitivity; the source remains unchanged.

A **ReasoningCapability** is one executable model/mechanism implementation of a reasoning contract. It exposes only the reasoning-contract, receiving-Privacy, location/latency, resource and availability facts Kernel needs. It knows nothing about Modules, Agents, Workflows, Operations, Material, external actions or semantic continuation.

A **reasoning adapter** is an independently installable JVM artifact that provides one or more configured `ReasoningCapability` instances through the public reasoning-adapter SPI. Installation does not imply mechanism enablement.

**Kernel** is deliberately narrow. It owns live executable Module registration and receiver mechanics plus shared reasoning-runtime responsibilities: reasoning-mechanism registration and deterministic selection, resources, immediate/durable execution, retry, cancellation, persistence, result delivery and ordinary runtime logging. Kernel may resolve which ordinary installed Module is assigned CORE; that resolution does not make CORE privileged.

The **MADRE host product** owns product-management mechanics that should not be pushed into CORE: installation/uninstallation, persistent configuration, artifact lifecycle, CORE selection, startup/shutdown, diagnostics and health.

The **SDK** supplies the strongly typed Module construction model and responsibility-specific ports, including executable provider/registration contracts, Module composition, host invocation boundaries and Module-facing reasoning. Domain objects are Java objects; JSON is only a boundary representation.

Modules and reasoning mechanisms are distinct installation concepts. CORE designation has no relation to reasoning installation or selection privilege.

## CORE

CORE is the installation role identifying MADRE's default owner-interaction/coordinator Module. It is semantically meaningful but non-privileged.

A Module assigned CORE is still an ordinary Module. CORE assignment changes no Security Algebra value, Operation visibility, Module/owner-local/external-PUBLIC invocation authority, reasoning selection, scheduling, class-loader treatment, installation authority or host invocation authority. There is no privileged `CoreModule` subtype.

The target CORE responsibility is to lead ordinary agentic owner interaction semantics: foreground conversation, reasoning choices, useful delayed semantic follow-up, and coordination/routing to installed Modules through the same public Module behavior available to other ordinary Modules. It may own interaction state and semantics appropriate to that role. It does not own product installation, global lifecycle or host administration.

The exact public behavior that qualifies a Module for CORE is intentionally not frozen here. The next implementation that makes CORE drive real owner interaction must recover the smallest structural contract from the shipped interaction behavior rather than inventing role-specific APIs, exact Operation names or a generic UI/surface framework in advance.

The current implementation is transitional: `roles.core` resolves an optional installed identity, while `interaction.*` independently selects the Module used by the current console. That independence is executable truth today, not the intended final product relationship. Removing the split must not introduce CORE privilege.

## Module installation and configuration

Executable Modules are discovered as JVM JARs exposing `ModuleProvider`. Each provider declares its canonical `ModuleId` before materialization and receives an immutable `ModuleProviderConfiguration` scoped to that same identity.

The current developer configuration format is:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

`madre-app` currently owns only generic extraction, exact-identity scoping and delivery. Provider class name, JAR name, discovery order, shipped status and CORE assignment never participate in association. Each Module provider owns supported-key validation, parsing, typed settings and defaults.

This string map is current executable behavior, not the final owner configurator contract. A generic configurator cannot safely hard-code independently installed Module settings. When an owner-facing Module configurator is implemented, the installed provider must supply the minimum provider-owned typed metadata that the configurator actually needs. The exact public metadata API is deliberately deferred until that real configurator proves the requirement; this is not permission to invent a general schema framework now.

Provider identity remains an installation invariant. Duplicate identities, materialized identity mismatches and invalid executable bindings fail startup before a partially reachable installation is exposed.

## Reasoning installation and configuration

Reasoning adapters are independently installable JVM JARs discovered from the reasoning installation directories through the public reasoning SPI. Packaged installations scan both shipped and owner-writable reasoning roots; explicit `reasoning.directory` retains exact single-directory semantics for development and tests.

The owner-facing reasoning configurator is implemented. Each installed provider declares a stable provider-owned `ReasoningProviderId`, a `ReasoningProviderDescriptor` containing only the owner-facing display/help information and minimal `TEXT`, `INTEGER` and `CHOICE` fields demonstrated necessary by the current configurator, and a `ReasoningProviderConfigurator` for repeatable named instances. The host renders this metadata generically, exposes provider/list/inspect/configure/enable/disable/remove commands, and owns transactional persistence. Provider-specific parsing, validation, defaults and raw-property mapping remain inside the provider artifact.

`madre-app` has no concrete llama.cpp, OpenAI-compatible or future-provider configuration branch. Provider discovery is independent from mechanism materialization: an installed provider can be visible and configurable while materializing zero mechanisms. Disabled or unconfigured instances register nothing. Privacy is explicit and is never inferred from endpoint, transport or locality.

The raw `reasoning.*` string representation remains executable for compatibility and advanced developer use rather than being the ordinary owner UX. This implemented reasoning-specific configurator does not imply a universal settings framework and does not complete generic Module configuration.

MADRE can boot with no reasoning directory, an empty one, or installed providers that materialize no mechanisms. Modules that do not request reasoning remain usable.

Host configuration mechanics remain outside Kernel and outside CORE. `ReasoningCapability` remains mechanism-only and contains no owner configuration UI or product-management responsibility.

## Module, owner-local and external/PUBLIC invocation

MADRE distinguishes three receiver boundaries over exact installed Operations declared `PUBLIC`.

**Module-to-Module invocation** is ordinary installed application composition. `ModuleContext` supplies a caller-bound `ModuleDirectory` and `ModuleInvoker`; Module code supplies neither caller identity nor receiver Privacy. Only target `PUBLIC` Operations whose accepted Material can receive the offered information are reachable. Before result delivery, the runtime requires the caller to canonically reference the foreign Material type and requires the returned Sensitivity to reach fixed `Privacy.MODULE`. When valid, the exact callee Material crosses unchanged. `PublicResultTransformer` is not run on this receiver path.

This is proven infrastructure and remains part of MADRE. Its future public shape should be extended only when real product use demonstrates need before 1.0.

**Owner-local invocation** is a host/application boundary over an exact installed externally callable Operation. `OwnerModuleInvoker.invokeOwner` executes the canonical call and returns the Module-created Material at the Sensitivity created by that Module. It does not apply `PublicResultTransformer` merely because the owner sees the result locally.

**External/PUBLIC invocation** is the actual disclosure boundary. `PublicModuleInvoker.invokePublic` executes the same bounded Operation and then requires the Module-owned semantic public transformation to create new declared Material whose Sensitivity can reach `Privacy.PUBLIC`. Mandatory semantic transformation therefore applies to actual external/PUBLIC disclosure, not to every call of a public Operation.

PRIVATE Operations remain Module-internal. Host invocation ports are not supplied through `ModuleContext` and CORE receives no special access to them.

## Current local interaction implementation

The present local text console is a replaceable `madre-app` adapter implemented by `MadreMain`. It owns the current read/evaluate command loop and maps ordinary text or commands through a resolved `LocalInteractionBinding`.

`interaction.*` currently chooses one installed Module plus the Operations and Material types used by that console. Ordinary text and `/standard` invoke configured behavior through the owner-local host boundary. `/updates` invokes a configured Module-specific collection Operation. The application does not interpret Kernel reasoning bytes; semantic interpretation remains inside the Module.

The shipped owner-interaction Module already demonstrates the important semantic split: it can produce an immediate result, submit durable reasoning, persist its own pending semantic association, later interpret completed reasoning, and decide whether a useful owner-visible follow-up exists. Kernel persists only opaque reasoning-runtime state.

What the current implementation does not yet provide is the target owner interaction product. The console still owns the interaction loop, `interaction.*` is independent of CORE, and delayed follow-up is primarily surfaced by the explicit `/updates` command rather than naturally re-entering owner interaction. Those are transitional implementation facts, not architectural responsibilities to preserve indefinitely.

The existing bounded Operations (`standard-prompt`, `fast-lane`, `collect-background`) are evidence from which the next product slice may recover the minimum real CORE interaction contract. Their exact names are not frozen as universal CORE APIs.

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

For one EffectProfile the non-user causal demand is `min(Risk, Autonomy)` and must be supported by the minimum Integrity of actual non-user causal participants, or I5 when there are none. Values from different EffectProfiles do not combine.

Risk is not propagated into reasoning requests. Reasoning mechanisms do not become action realizers merely because reasoning contributed to a Module decision. `Privacy.MODULE` is the fixed structural receiver for declared foreign Material between installed Modules. There is no OWNER Privacy shortcut.

## Reasoning execution and persistence

A Module creates a nominal `ReasoningComputation<R>` from valid bounded behavior and submits a `ReasoningRequest` through `ReasoningService`. The request derives originating Module and carried Sensitivity and contains only reasoning execution controls such as mode, priority, timing, timeout, cancellation, retry and typed selection preferences.

It contains no Material identity/type, Agent, Workflow, Operation identity, concrete reasoning mechanism, semantic continuation or future output Material. It also contains no Operation Risk.

Kernel selects compatible reachable mechanisms, coordinates resources, executes immediate or durable work and persists durable runtime state in SQLite. Persisted reasoning input/output remains opaque to Kernel semantics. After restart, compatible reasoning mechanisms can resume queued work; the originating Module remains responsible for interpretation and any continuation.

A Module owns semantic/domain persistence. The shipped owner-interaction Module's pending state and Kernel's durable work store are intentionally separate persistence domains.

## Public development platform status

The current SDK/reasoning SPI are a strong public-contract foundation. Independent Gradle builds already compile Modules and reasoning adapters against published MADRE artifacts only; installed-distribution acceptance proves discovery, Module-to-Module composition, owner-local/PUBLIC behavior and independent reasoning execution on Windows and Linux.

That evidence is necessary but not sufficient for a community developer product. MADRE is not considered developer-ready merely because isolated fixtures compile. A public-platform release must prove stable consumable publication, documentation, packaging conventions, test tooling/testkit and an end-to-end unrelated-developer journey that does not require repository-internal knowledge.

Optional future standard libraries for ordinary application I/O, MCP, UI, audio, testing or similar developer ergonomics may belong above Kernel when concrete use demonstrates value. Their existence would not make those concerns Kernel responsibilities.

## Product gates

Native Windows/Linux owner deployment and generic reasoning-provider configuration are implemented foundations. The next product outcomes are ordered by owner/developer value rather than architecture novelty:

1. **Meaningful CORE-led interaction.** CORE should lead the ordinary owner conversation and deliver useful delayed semantic follow-up naturally while remaining an ordinary non-privileged Module.
2. **Generic owner Module configuration.** A real Module configurator should drive the minimum Module-provider-owned typed metadata contract needed for independently installed Module settings. Do not reuse the reasoning configurator as a universal schema, hard-code Module settings, or design a schema framework first.
3. **Genuinely public developer journey.** Stable artifacts, documentation, tooling/testkit and packaging/install proof must make an unrelated Module developer successful without depending on `madre-app` or Kernel implementation.
4. **Broader integration surfaces only from demonstrated demand.** UI extraction, Skills/MCP standard libraries, audio/multimodal support and similar community facilities follow concrete use rather than speculative architecture.

These are product gates, not a new immutable master plan. Live dependency analysis may change implementation order, but it must not use sequencing as an excuse to introduce unrelated frameworks, protocols or Modules.

Research on QVAC, AAAAT, OpenWhispr and other external systems remains evidence only unless the Owner explicitly accepts a product direction.