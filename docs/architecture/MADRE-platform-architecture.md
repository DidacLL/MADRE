# MADRE Platform Architecture

## Responsibility map

The target product layering is:

```text
Owner
  |
  +--> host product-management surfaces
  |      install / uninstall / configure / select CORE
  |      start / stop / diagnose / report health
  |
  `--> owner interaction surfaces
         |
         v
       ordinary installed Module assigned CORE
         | owns owner-interaction semantics and coordination
         |
         +--> caller-bound ModuleDirectory / ModuleInvoker
         |      to other installed Modules
         |
         +--> ordinary application I/O
         |
         `--> ReasoningService
                |
                v
              Kernel
                | live Module receiver mechanics
                | reasoning registration/selection/resources
                | immediate/durable execution, retry, cancellation,
                | persistence and delivery
                v
              independently installed ReasoningCapability

External/public caller
  |
  | host PublicModuleInvoker + Module-owned semantic transformation
  v
installed ordinary Module
```

The Security Algebra is cross-cutting behavior of values and contracts in this graph. It is not a runtime component.

The current console does not yet realize this complete product layering. `MadreMain` and `interaction.*` currently own presentation/command mechanics independently of `roles.core`. That is transitional executable behavior documented in `docs/implementation-baseline.md`, not a reason to redefine the target responsibilities above.

## Experimentation architecture and SDK lifecycle

MADRE is intentionally both an installed owner product and a software/inference experimentation environment. The architecture must make it inexpensive to try alternative semantic-agentic designs and heterogeneous inference mechanisms without turning each experiment into Kernel architecture.

Semantic experimentation belongs primarily above Kernel: Module behavior, Agent definitions, Skills, Workflows, context/request construction, semantic memory/stores, knowledge graphs, retrieval, coordination and application-specific interpretation are examples of techniques that experiments may use. They are not a mandatory architecture inventory and do not imply one Module or public abstraction per technique. Those experiments should consume the same stable Module/Material/Operation/ReasoningService contracts as deployed software.

The public SDK therefore has two lifecycle levels conceptually:

- **stable contracts**: small public types whose ownership/interoperability semantics are already demonstrated and that independent Modules may rely on;
- **experimental facilities**: higher-level authoring, testing, harness and semantic-engineering helpers that are intentionally allowed to evolve during 0.x while evidence is gathered.

Experimental facilities may depend on stable SDK contracts. Stable SDK, Kernel and the reasoning SPI must not depend on experimental facilities. Exact artifact names/package boundaries are implementation choices until materialized, but experimental code must have an explicit public lifecycle rather than silently becoming stable by being convenient.

Graduation is evidence-driven. A technique becomes stable only after repeated real use shows that it is broadly reusable, has a clear responsibility owner and does not leak one Module/provider's private semantics.

The same rule applies vertically. A technical primitive or inference computation becoming available does not create a semantic/application responsibility above it. It may remain a provider capability, a typed computation contract or a reusable library until a real semantic consumer proves that a higher-level abstraction is useful and correctly owned.

This experimentation model is not permission to create a universal Agent loop, workflow language, generic tool system, arbitrary metadata framework, speculative semantic database abstraction or synthetic Module whose only purpose is to demonstrate a technical primitive. The point is to make concrete experiments cheap and rigorous.

## Host product boundary

The host product owns mechanics required to operate MADRE as installed software rather than as a source checkout:

- installation and uninstallation;
- persistent product configuration;
- installed Module and reasoning-artifact lifecycle;
- CORE assignment/selection;
- application startup and shutdown;
- diagnostics and health;
- owner-facing management surfaces over those responsibilities.

These mechanics do not belong to CORE. Moving semantic interaction into CORE must not move installation authority, artifact management or host lifecycle into a Module.

The host may hold host-only ports such as `OwnerModuleInvoker` and `PublicModuleInvoker`; those ports are not supplied to Modules through `ModuleContext` and are not granted by CORE assignment.

## Module boundary

A Module is the semantic and application/domain boundary. It owns domain state and persistence, Material, Agents, Skills, Workflows, public/private Operations, application I/O, semantic transformations, continuation, artifacts and domain-specific presentation/UX.

A Module boundary therefore requires coherent semantic/domain ownership. Reusable infrastructure such as search clients, embedding primitives, databases, storage libraries, transports or model APIs is not a Module merely because multiple applications may use it. Such facilities may be libraries or lower-layer capabilities until an actual application/domain exists that owns meaning around them.

An Operation is bounded callable Module behavior. It declares accepted Material and receiving Privacy, produced Material and maximum Sensitivity, and any EffectProfiles required for consequential variants.

An EffectProfile represents actual consequential behavior of one exact Operation variant. Reasoning computation is not itself an action realizer and does not create Risk merely because an Operation requested reasoning.

A Module does not become a Kernel extension merely because one Operation performs file, HTTP, database, device, search, MCP or other application I/O.

Semantic memory, semantic databases, knowledge graphs, retrieval/context construction and request optimization likewise do not become Kernel responsibilities merely because they contribute to agentic quality. They belong in Modules that have real semantic ownership or in reusable SDK/application libraries unless a concrete shared-runtime requirement establishes otherwise. Their usefulness alone does not justify inventing a dedicated Module.

`ModuleDefinition` is the canonical declarative surface. `ModuleInstance` binds it to exact executable `OperationBinding` values. Registration validates declared/executable identities and contracts before reachability.

`ModuleDefinition.publicMaterialReferences` records foreign public Material type identities a Module structurally references. This is an opt-in to receiving those types through the fixed `Privacy.MODULE` receiver; it does not imply that every value of the type is S1.

## Module installation and discovery

Modules are ordinary JVM JARs exposing `ModuleProvider` through Java's service-provider mechanism. Each provider declares its canonical `ModuleId` before materialization, receives a `ModuleContext` bound to that identity plus immutable `ModuleProviderConfiguration`, and creates one `ModuleInstance` for the same canonical identity.

Shipped and independently built Modules use the same route. Bundling, class-loader placement, process placement and CORE assignment grant no authority.

The live Module registry is rebuilt at boot. Caller-bound `ModuleDirectory` and `ModuleInvoker` facades capture the installed caller identity structurally; Module code cannot supply another caller identity or arbitrary receiver Privacy.

The proven in-process Module-to-Module path is retained infrastructure. Further generalization or transport extraction is demand-driven and should be validated by real product use before 1.0 rather than expanded because an abstraction could exist.

## Module configuration boundary

The current executable owner/developer property format is:

```text
modules.config[<canonical ModuleId>].<module-owned-key>=<value>
```

`madre-app` currently extracts only this generic namespace, matches exact canonical identity and supplies an immutable string map. Each provider owns its key vocabulary, validation, parsing, typed settings and defaults. Configuration targeting an uninstalled identity or malformed enabled configuration fails startup rather than silently falling back.

This is a sound ownership boundary but not yet a generic owner Module configurator contract. An owner-facing Module configurator cannot hard-code keys owned by independently installed Modules. The eventual configurator therefore needs the minimum provider-owned typed metadata required to render/validate real Module configuration. The exact public metadata API is intentionally not designed here; it must be recovered from the first Module configurator implementation rather than from a speculative schema framework or by generalizing the reasoning-provider descriptor contract prematurely.

`roles.core` and current `interaction.*` are separate installation facts today. Neither changes Module configuration delivery.

## Module-to-Module Material boundary

Module-to-Module invocation represents one installed application calling another while remaining a Module receiver. It is neither owner-local host authority nor external/public disclosure.

The caller-bound directory exposes only exact target Operations declared `PUBLIC` whose accepted-Material Privacy can receive the offered information. PRIVATE Operations are never discoverable.

The caller-bound invoker resolves the canonical target binding and executes ordinary `OperationBinding.invoke`, which validates the Module-created internal result. It does not run `PublicResultTransformer`.

Before result delivery the runtime requires:

```text
returned foreign Material type is canonically referenced by caller
and returned Sensitivity <= Privacy.MODULE
```

If valid, the exact callee Material crosses unchanged with the same identity, owner, type and Sensitivity. The caller may interpret it and create new caller-owned Material. Undeclared foreign types or S5 values are rejected before caller exposure.

No caller identity or receiver Privacy is supplied per call. No CORE privilege is involved.

## Owner-local Material boundary

Owner-local invocation is a host/application receiver for the owner using their installation. It resolves an exact installed `PUBLIC` Operation and executes a canonical `OperationCall`, preserving accepted-Material Privacy, exact EffectProfile selection, causal Integrity composition and output validation.

`OwnerModuleInvoker.invokeOwner` returns the Material created by the Module at the Sensitivity created by that Module. It does not apply `PublicResultTransformer` or lower Sensitivity because execution is local.

PRIVATE Operations remain Module-internal. Locality, class-loader placement, bundled status and CORE assignment create no additional authority.

## External/PUBLIC Material boundary

`PublicModuleInvoker.invokePublic` is the host external/public disclosure path. Every executable `PUBLIC` binding owns a `PublicResultTransformer`; the transformer runs after internal output validation and before Material crosses this receiver boundary.

The external result must be new declared Material with a new identity and Sensitivity able to reach `Privacy.PUBLIC`. Raw sensitive internal Material cannot cross.

Semantic minimization belongs to Module behavior; runtime validation prevents bypass. Mandatory transformation is therefore required for actual external/PUBLIC disclosure, not for Module-to-Module or owner-local calls.

## CORE installation role

`roles.core`, when configured, identifies the ordinary installed Module expected to provide MADRE's default owner-interaction/coordinator semantics.

CORE is not merely an authorization flag and is not a privileged runtime class. Assignment changes no Module definition, Security Algebra value, Operation visibility, invocation authority, configuration authority, reasoning installation/selection, scheduling, class-loader treatment or host installation authority. There is no `CoreModule` subtype and neither host invocation port becomes a CORE port.

Target CORE behavior is semantic: lead foreground owner conversation, make ordinary reasoning choices, turn completed durable reasoning into useful delayed semantic follow-up, and coordinate/routinely compose with other installed Modules. Those actions must use ordinary public Module behavior and the same Kernel reasoning port available to other Modules.

CORE is also a primary experimentation consumer and owner-UX benchmark. The exact structural qualification for CORE should be stabilized only after interaction experiments demonstrate the smallest reusable contract worth freezing. The shipped owner-interaction Module and current console are evidence, not a universal assistant protocol. This document deliberately does not freeze exact CORE Operation names, a surface interface or a generic UI API.

The current runtime only resolves the configured identity and tolerates absence/unresolved assignment. Current console binding remains separately configured through `interaction.*`. That is a transitional implementation detail, not the target semantic definition of CORE.

## Current application adapter and interaction transition

`madre-app` currently has no compile-time dependency on the concrete owner-interaction Module. It discovers installed declarations/codecs generically and can exercise owner-local and external/PUBLIC host boundaries.

`MadreMain` is a replaceable developer/local console. It currently owns the foreground read/evaluate loop, generic invocation commands, `/standard`, `/updates` and session Sensitivity selection. `LocalInteractionBinding` resolves `interaction.*` against exact installed Module declarations.

The shipped example maps ordinary text to the owner-interaction Module's fast path. `/updates` invokes the Module-specific collection Operation; the application does not inspect Kernel reasoning output or perform semantic continuation. The Module itself owns foreground/background reasoning choices, its pending semantic state, interpretation, acknowledgement and any visible follow-up Material.

This proves the correct semantic/runtime separation but not the completed owner product. The target remains for CORE to lead ordinary owner interaction semantics while host product surfaces retain host/product-management mechanics and presentation/adaptation. However, the exact interaction contract should emerge from SDK/inference experiments rather than from mechanically standardizing the present console wiring. Natural delayed follow-up remains an important UX benchmark, not a command name to freeze.

Do not implement that target by giving CORE privileged host ports or by turning Kernel into a conversation/router service.

## Heterogeneous inference boundary

MADRE's `model-agnostic` property is a separation-of-responsibility rule, not a requirement for impoverished inference contracts.

The architecture distinguishes three layers of inference variability:

1. **portable request/result semantics** belong to nominal typed reasoning computation-contract artifacts when multiple mechanisms can implement the same semantics;
2. **mechanism/model/runtime tuning** belongs to independently installed provider/adapter configuration and implementation;
3. **shared execution mechanics** belong to Kernel: compatible selection, information reach, availability/resources, immediate/durable scheduling, retry, cancellation, persistence and result delivery.

The current public computation families include preserved text inference (`madre.text-inference.v1`), richer text generation (`madre.text-generation.v2`) and text embeddings (`madre.text-embedding.v1`). They demonstrate that richer and materially different inference families can coexist without adding model-specific branches to Kernel. They remain inference-layer contracts; their existence does not require Module-domain concepts that mirror their vocabulary.

Low-resource local inference is a first-order product concern. A llama.cpp provider, for example, may need controls for engine/model execution that have no meaning for an OpenAI-compatible endpoint or another runtime. Such controls remain provider-owned. Conversely, request parameters demonstrated to be portable across multiple text-generation implementations belong in the common text-generation computation contract rather than being duplicated as provider-private knobs.

Future computation families such as multimodal work should be introduced only when concrete inference experiments establish their portable semantics. New inference capabilities do not establish a roadmap for semantic/application architecture.

This boundary lets MADRE expose enough control to optimize SLMs and heterogeneous engines while preserving a narrow model-independent Kernel.

## Reasoning-mechanism installation, configuration and discovery

Reasoning mechanisms are independently installable and architecturally distinct from Modules. Their artifacts live in separate shipped/owner reasoning installation roots and use the public reasoning service-provider contract.

The public reasoning SPI contains the typed reasoning execution contract plus the small provider-owned installation/configuration boundary used by the owner product. An adapter depends on that SPI plus the computation contracts it implements, not on `madre-app` or Kernel registries/stores/schedulers.

Each provider declares a stable `ReasoningProviderId`, a `ReasoningProviderDescriptor` with only owner-facing display/help information and the minimal `TEXT`, `INTEGER` and `CHOICE` field metadata demonstrated by the current configurator, a `ReasoningProviderConfigurator` for repeatable named instances, and `materialize(...)` for enabled mechanisms. Provider discovery is independent from mechanism materialization, so an installed provider with zero configured or enabled instances remains discoverable/configurable while registering no `ReasoningCapability` values.

`madre-app` owns generic discovery, rendering of provider descriptors, the provider/list/inspect/configure/enable/disable/remove host commands, and transactional persistence of provider-produced configuration updates. Provider-specific parsing, validation, defaults, raw property names and representation remain inside each adapter. There are no concrete llama.cpp/OpenAI-compatible configuration branches in application production code.

The raw `reasoning.*` string representation remains executable for compatibility and advanced developer use; it is not the ordinary owner configuration surface. The implemented reasoning-provider descriptor/configurator is intentionally reasoning-specific and must not be treated as the unfinished generic Module configurator or expanded into a universal settings framework without demonstrated need.

Provider configuration is expected to grow only when real engine/model experiments demand more expressiveness. The current field kinds are an executable baseline, not a claim that all future runtime/model controls fit `TEXT`/`INTEGER`/`CHOICE` forever.

These host configuration mechanics remain outside Kernel and outside CORE. Kernel receives only successfully materialized reasoning mechanisms; `ReasoningCapability` remains mechanism-only and contains no configuration UI/product-management responsibility.

Privacy remains explicit and is never inferred from endpoint, transport or locality. Bundled adapter placement grants no privilege. Zero materialized mechanisms remains a valid boot state.

## Kernel boundary

Kernel owns only shared runtime responsibilities that require central coordination:

- live executable Module registry and caller-bound Module/host receiver mechanics;
- resolution of the configured CORE Module identity;
- registry and deterministic selection of installed reasoning mechanisms;
- shared reasoning resources;
- immediate and durable reasoning execution;
- timeout, cancellation and bounded retry;
- durable reasoning persistence and opaque result delivery;
- ordinary runtime logging.

Kernel does not own Module/domain state, Material semantics, semantic workflows, artifact meaning, ordinary application I/O, search, semantic stores, knowledge graphs, model interpretation, model/runtime tuning, continuation, owner conversation semantics, product installation or host product-management UX.

The runtime may boot with zero reasoning mechanisms. A Module that uses no reasoning remains executable. An Operation requesting unavailable reasoning fails or waits according to the reasoning contract rather than making mechanism presence a boot invariant.

## ReasoningCapability boundary

The public reasoning SPI is reasoning-specific:

```text
ReasoningCapability<R,C extends ReasoningComputation<R>>
```

The manifest describes the exact reasoning contract, receiving Privacy, location, expected latency and resource claims. Availability is runtime state. Selection uses nominal contract matching, the capability's local deterministic compatibility for the exact computation value, information reach, availability, resources, typed preferences and deterministic ordering.

That value-level compatibility remains generic. Kernel does not understand why a mechanism rejects a computation. For example, embedding-space compatibility is enforced by embedding mechanisms without making Kernel aware of embeddings.

Operation Risk is not carried into reasoning. Reasoning mechanisms do not own Module/Agent/Material/Workflow semantics, external action or semantic continuation. There is no generic Kernel action capability path.

## Shipped owner-interaction evidence

The shipped owner-interaction Module is ordinary installed behavior and may be assigned CORE. Its invocation/reasoning behavior does not depend on CORE privilege.

Its current bounded behavior proves:

- `standard-prompt`: immediate reasoning followed by Module-created response Material;
- `fast-lane`: immediate foreground reasoning plus durable background reasoning and Module-owned pending-state persistence;
- `collect-background`: Module interpretation of terminal reasoning, optional visible follow-up, acknowledgement of Kernel work and pending-state cleanup.

The exact names above are implementation and experimentation evidence, not universal CORE API names.

Kernel SQLite stores opaque durable reasoning state. The Module stores only the semantic association it needs to interpret eventual results. On restart these persistence domains recover independently and rejoin through `ReasoningService`.

## Search and ordinary application I/O

Search is ordinary application/domain I/O. `madre-web-search` supplies reusable typed values and `madre-adapter-searxng` supplies an ordinary client with no Kernel dependency.

A future domain Module may use search, files, databases, semantic databases, knowledge graphs, HTTP, MCP, embeddings or devices directly or through optional SDK/application libraries when those facilities support the Module's actual domain semantics. Such libraries may improve developer ergonomics without becoming Modules or Kernel services merely because they are reusable.

## Storage

Module persistence remains inside each Module.

Kernel SQLite persists only durable reasoning work that must survive restart: opaque serialized computation input, delivery Module identity, stable reasoning-contract identity, scheduling/attempt/cancellation/retry state and pending opaque output until collection/acknowledgement/retention cleanup.

Persisted durable work does not name a concrete adapter implementation class. Live Module and reasoning registries are rebuilt at boot; queued work becomes executable again when a compatible reasoning contract/mechanism is registered.

Kernel never acquires semantic ownership of queued bytes.

## Provider environment

Provider accounts, credentials, authentication flows, permissions and externally prepared sessions remain outside the current MADRE domain model. Configuration may describe provider-owned instances, but provider-specific meaning stays with the installed artifact. Add broader account/session product responsibility only when a concrete owner product requirement establishes the correct boundary.