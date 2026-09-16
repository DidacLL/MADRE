# MADRE Platform Architecture

## Responsibility map

MADRE is one owner-installed local product and one public experimentation platform. Its target layering is:

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
         | owns semantic owner interaction and coordination
         |
         +--> caller-bound ModuleDirectory / ModuleInvoker
         |      to exposed interfaces of other installed Modules
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

External/public receiver
  |
  | explicit host boundary + Module-owned transformation
  v
installed ordinary Module
```

Security Algebra is cross-cutting behavior of values and contracts in this graph. It is not a runtime component or an access-control lattice.

## Product and experimentation architecture

MADRE must support useful owner deployment and rapid semantic/inference experimentation without turning each experiment into permanent architecture.

Semantic experiments belong primarily in Module-owned software: Agent behavior, Skills, Workflows, context construction, memory/knowledge structures, retrieval, coordination, application state and interpretation. Inference experiments belong in typed reasoning computation contracts plus independently installed provider/adapters and provider-specific tuning.

These are experiment areas, not a mandatory component inventory. Stable architecture is recovered from demonstrated ownership and reuse. External applications and integration exercises are evidence about SDK friction; they do not become MADRE dependencies or authority by default.

## Host product boundary

The host owns mechanics required to operate MADRE as installed software:

- installation and uninstallation;
- persistent product configuration;
- Module and reasoning-artifact lifecycle;
- CORE assignment;
- startup and shutdown;
- diagnostics and health;
- generic owner/debug entry;
- external/public receiver entry;
- owner-facing presentation mechanics.

These mechanics do not belong to CORE. Moving semantic owner interaction into the Module assigned CORE must not transfer product-management authority into that Module.

The host currently exposes three semantically distinct invocation responsibilities:

- `OwnerModuleInvoker`: expert/debug entry to an exact installed Operation, returning contract-valid Module Material unchanged;
- `PublicModuleInvoker`: actual external/public disclosure, requiring a Module-owned public-disclosure transformation;
- ordinary owner interaction: host presentation resolves the Agent supplied by the Module assigned `roles.core`, transports owner text and Agent-approved messages, while the Agent may use the restricted `OwnerInteractionInvoker` to execute its own exact owner-interaction bindings.

`OwnerModuleInvoker`, `PublicModuleInvoker` and `OwnerInteractionInvoker` are host/runtime ports and are absent from `ModuleContext`. The owner-interaction Agent receives only the already CORE-restricted interaction invoker for its own semantic execution; this does not create authority over another Module.

## Module boundary

A Module is the independently installable semantic/application boundary. It exists only when coherent application/domain software owns meaning, state, interpretation, bounded behavior and domain semantics.

A Module may be agentless. Reusable infrastructure such as search clients, embedding primitives, databases, storage libraries, transports, model APIs or MCP does not become a Module merely because it is technically reusable.

A Module owns domain/application state and persistence, Material values it creates, optional Agents/Skills/Workflows, Operations and implementation, semantic transformations and interpretations, continuation decisions, domain integrations/application I/O, domain-specific UX where appropriate, and its cross-Module exposed interface.

For Java authors, executable `Module` is the source of truth. `Module.definition()` projects the portable `ModuleDefinition`; `Module.instance()` projects the validated runtime assembly.

## Agent boundary

An Agent is an optional Module-owned semantic actor. It owns semantic intent, interpretation, Agent-specific state and continuation appropriate to its Module. There is no universal Agent loop, planner, memory system, prompt framework or tool loop.

An agentless Module is valid. If the Owner reaches its capability through normal product interaction, an Agent in another Module—normally the Agent in the Module assigned CORE—owns intent interpretation, invokes the exposed Module capability, interprets the result and decides continuation.

The optional `OwnerInteractionAgent` execution-side specialization represents exactly that default owner semantic surface for a Module capable of serving the CORE role. It is not portable Module metadata, does not apply to every Agent or Module, and grants no privilege. The host transports owner text and presents `OwnerMessage` values; the Agent owns which of its internal Operations, reasoning paths and continuation behavior produce those messages.

Kernel does not acquire semantic agency merely because it routes or executes calls.

## Operation boundary

An Operation is one bounded callable execution of Module logic through MADRE. Its contract captures the facts needed to arbitrate that execution: accepted Material receiver Privacy, produced Material maximum Sensitivity and any consequential EffectProfiles.

PUBLIC/PRIVATE is not intrinsic Operation ontology. `OperationDefinition` carries no visibility field.

The following concerns are orthogonal:

```text
bounded execution                 Operation
cross-Module discoverability      Module exposedOperations
expert/debug entry                host OwnerModuleInvoker
ordinary owner interaction        selected CORE Agent semantic surface
internal CORE interaction calls   restricted OwnerInteractionInvoker + bindings
actual public disclosure          host PublicModuleInvoker + publicDisclosure binding
reasoning eligibility             Material Sensitivity vs mechanism receiving Privacy
transport                         implementation/provider concern
```

Do not reintroduce a replacement visibility/security lattice merely to encode these distinct concerns in one field.

## Module installation and discovery

Modules are JVM JARs exposing `ModuleProvider` through Java service-provider discovery. Each provider declares its canonical `ModuleId` before materialization, receives a `ModuleContext` bound to that identity plus immutable Module configuration, and creates one executable `Module` with the same identity.

Shipped and independently built Modules use the same route. Bundling, class-loader placement, process placement and CORE assignment grant no authority.

The live registry is rebuilt at boot. Caller-bound `ModuleDirectory` and `ModuleInvoker` facades capture installed caller identity structurally; Module code cannot provide another caller identity or arbitrary receiver Privacy.

## Module configuration boundary

The host owns generic Module configuration persistence and rendering. Providers own key vocabulary, defaults, validation, parsing and typed settings. Provider metadata/configuration is separate from Module execution semantics and separate from reasoning-provider configuration.

`roles.core` is the one current Module identity selecting default owner interaction. Ordinary owner conversation does not require host configuration of CORE-private Operation names, Material types, reasoning modes or delayed-work collection payloads. Such implementation details remain Module-owned and may still be available through explicit expert/debug boundaries where useful.

## Cross-Module exposure

The target Module owns its ordinary composition interface through `Module.exposedOperations()` / `ModuleDefinition.exposedOperations()`.

Only those Operations are discoverable/invocable from another Module through `ModuleDirectory` / `ModuleInvoker`. Exposure is a Module boundary fact, not an Operation security/visibility level and not a Security-Algebra carrier.

Reachability still applies the target Operation's accepted-Material Privacy to the offered information. The caller identity is runtime-bound.

The caller-bound invoker executes the ordinary Operation binding and does not run a public-disclosure transformer.

## Module-to-Module Material receiver

Cross-Module invocation represents one installed application calling another while remaining a Module receiver. It is not owner-local host authority and not external/public disclosure.

The receiver Privacy is fixed at `Privacy.MODULE`. Before result delivery, the runtime requires the calling Module to structurally declare the nominal Material contract it receives through `foreignMaterialReferences()` and requires result Sensitivity to reach `Privacy.MODULE`.

Concrete Material ownership and nominal type ownership are independent. A callee may create and return callee-owned Material conforming to a nominal contract defined by the caller or another Module. When the receiver checks succeed, the exact callee value crosses unchanged with its concrete owner, nominal type and Sensitivity intact.

The caller may then create new caller-owned Material representing its own interpretation. No CORE privilege participates in this path.

## Generic owner/debug boundary

Generic owner/debug invocation is a host-only expert path. It can resolve an exact installed Operation regardless of whether the Module exposes that Operation to other Modules.

The host still constructs a canonical `OperationCall`, so accepted Material Privacy, EffectProfile selection, causal Integrity and output validation remain in force.

`OwnerModuleInvoker` returns the Module-created Material at its Module-created Sensitivity. It neither runs a public transformer nor silently lowers Sensitivity.

This is a debugging/product-mechanics boundary, not the normal owner semantic interaction contract.

## Owner interaction boundary

Ordinary owner interaction is a host presentation boundary into the semantic Agent supplied by the installed Module selected through `roles.core`.

The host chooses no CORE-private Operation, Material protocol, reasoning mode, continuation mode or background collection protocol for an ordinary turn. It transports owner text plus current presentation Sensitivity to the selected `OwnerInteractionAgent` and presents only Agent-approved `OwnerMessage` values.

The Agent may execute bounded Module behavior through exact `OperationBinding.ownerInteractionOperation(...)` bindings using the host/runtime `OwnerInteractionInvoker`. That invoker additionally requires that the target Operation belong to the currently installed Module selected by `roles.core`; it therefore remains a narrow execution boundary rather than a generic privilege. The Agent—not the host—constructs and interprets those internal calls.

The port is absent from `ModuleContext`; CORE code receives no hidden authority to invoke another Module's interaction entries or internal Operations. Every internal entry still executes a canonical `OperationCall`, and no Security Algebra value is inferred from owner presentation or CORE selection.

The shipped owner-interaction Module preserves bounded conversation state, foreground reasoning, durable background reasoning, pending-work association, result interpretation, acknowledgement and useful follow-up behavior. Its Agent now owns selection of the foreground/continuation behavior and interpretation of delayed results. Host polling is only a physical presentation mechanism used to ask the Agent for approved follow-ups; the host does not know the Module's private collection Operation, Material names or result protocol.

Current Operation names, private Material types, console commands and polling cadence remain 0.x implementation evidence, not owner API or universal CORE protocol.

## External/public Material boundary

`PublicModuleInvoker.invokePublic` is the actual external/public receiver path. It can cross only through an executable binding created with `OperationBinding.publicDisclosure(...)`.

The Module-owned transformer runs after internal output validation and must create new declared Material with a new identity. The transformed result is revalidated and must have Sensitivity capable of reaching `Privacy.PUBLIC`.

Explicit transformation/minimization at actual public disclosure is durable architecture. Whether the same underlying Operation is in `exposedOperations` for other Modules is independent.

`Privacy.PUBLIC` means an actual confidentiality receiver boundary; it does not mean Module exposure.

## Material ownership

Material has two relevant nominal identities with distinct owners:

- concrete value ownership: `MaterialId.moduleId`;
- nominal contract ownership: `MaterialTypeId.moduleId`.

The Module creating a value owns that value even when it conforms to a foreign nominal contract. This permits generic composition without pretending that using another Module's type transfers ownership of the concrete result.

A Module must explicitly declare foreign Material contracts it structurally uses through `foreignMaterialReferences()`. That declaration is not an S1/public-disclosure classification.

## CORE role

`roles.core` identifies the ordinary installed Module expected to provide MADRE's default owner-interaction/coordinator role.

CORE is not a privileged runtime class. Assignment changes no Module definition, Security Algebra value, cross-Module exposure, generic owner/debug authority, external disclosure, reasoning installation/selection, scheduling, class-loader treatment or host installation authority. There is no privileged `CoreModule` subtype.

For the ordinary owner path, the selected CORE's Agent owns interpretation of the turn, conversation state/context use, its own reasoning and continuation choices, interpretation of completed delayed reasoning, the semantic decision that a delayed result is worth presenting, and conversational continuation after that result. The host owns presentation/product mechanics only.

Target CORE behavior also includes coordination with installed Modules through their ordinary exposed interfaces. The structural qualification for future CORE Modules remains intentionally narrow and unfrozen beyond the optional semantic owner-interaction surface demonstrated by the shipped CORE. Do not derive a universal assistant protocol from the current console or shipped Operation names.

## Reasoning architecture

MADRE separates three layers of inference variability:

1. portable request/result semantics belong to nominal typed reasoning computation contracts when multiple mechanisms can implement them;
2. mechanism/model/runtime tuning belongs to independently installed providers/adapters;
3. shared execution mechanics belong to Kernel: selection, information reach, availability/resources, immediate/durable scheduling, retry, cancellation, persistence and delivery.

Current computation families include text inference, text generation and text embeddings. Their existence does not create corresponding Module-domain concepts.

Reasoning eligibility is independent from Module exposure and host entry. The actual carried Material Sensitivity must be able to reach the selected mechanism's explicit receiving Privacy.

Kernel durable work is opaque physical reasoning state. Module owns semantic association, interpretation and continuation.

## Reasoning-mechanism installation/configuration

Reasoning mechanisms are independently installable and distinct from Modules. Providers expose provider-owned descriptors/configurators and materialize enabled `ReasoningCapability` values.

The host owns generic discovery, lifecycle and configuration persistence. Provider-specific parsing, validation, defaults, raw property names and model/runtime controls remain inside adapters. Zero configured/materialized mechanisms is valid.

Privacy is an explicit mechanism fact and is never inferred from endpoint, transport, provider identity, model, locality or shipped status.

## Owner product progression

The final Owner should not need to learn this architecture for ordinary use. The product progressively discloses control:

- ordinary owner interaction is plain semantic interaction with the selected CORE Agent;
- management surfaces expose installed Module/provider state and configuration;
- expert/debug surfaces can invoke exact Operations and inspect details;
- developer-facing SDK contracts expose full architectural precision.

The product should make boundaries understandable when they matter, especially installed software, CORE assignment, Module exposure, reasoning mechanism selection/configuration and actual external/public disclosure.

## Evolution rule

Prefer clear ownership and one coherent runtime over flattening responsibilities for local convenience.

Examples, fixtures, current UI shapes, integration experiments and provider implementations are evidence. They are not definitions unless current authority adopts their responsibility.

Do not pre-build a universal Agent framework, planner/tool system, workflow language, memory/RAG system, semantic-database abstraction, Module taxonomy, plugin marketplace abstraction or replacement visibility/access-control lattice.
