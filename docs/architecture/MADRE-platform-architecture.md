# MADRE Platform Architecture

## Responsibility map

```text
Owner / local surface
  | owner-local host invocation
  v
installed ordinary Module
  | owns application/domain behavior, Material, meaning, state, Agents,
  | Skills, Workflows, Operations, application I/O and continuation
  | may optionally be assigned the CORE role
  |
  +--> ordinary Java/application I/O (files, search, databases, devices, network...)
  |
  `--> ReasoningService when bounded behavior needs reasoning
         v
       Kernel reasoning runtime
         | compatible reasoning selection, resources, scheduling,
         | durable reasoning, retry/cancellation and delivery
         v
       installed ReasoningCapability

External/public caller
  |
  | PUBLIC invocation + Module-owned semantic transformation
  v
installed ordinary Module
```

The Security Algebra is behavior of values carried by these objects. It is not a runtime component.

## Module boundary

A Module is the semantic and application/domain boundary. It owns domain state and persistence, Material, Agents, Skills, Workflows, public/private Operations, application I/O, semantic transformations, continuation, artifacts and presentation that belong to that domain.

An Operation is bounded callable Module/application behavior. It declares the Material it accepts, receiving Privacy, Material it may produce, maximum output Sensitivity and any EffectProfiles for consequential variants. Module behavior uses ordinary Java and SDK types.

An EffectProfile represents actual consequential behavior of one exact Operation variant. Reasoning computation itself is not an action realizer and does not create Risk merely because the Operation requested reasoning.

A Module does not become a Kernel extension merely because one Operation performs file, network, database, device, search or other application I/O.

`ModuleDefinition` is the canonical declarative surface. `ModuleInstance` binds it to exact executable `OperationBinding` values. Registration validates that declared and executable identities/contracts match exactly before the Module becomes reachable.

## Module installation and discovery

Modules are ordinary JVM JARs exposing `ModuleProvider` through Java's service-provider mechanism. The application discovers configured Module JARs with JDK path/class-loader APIs, constructs each Module from `ModuleContext`, and registers its `ModuleInstance`.

Shipped Modules use the same route as independently built Modules. Bundling, class-loader placement, process placement or CORE assignment grants no authority.

The Module registry is in-memory and rebuilt at boot. `ModuleDirectory` exposes reachable public definitions. `ModuleInvoker` invokes one exact installed `PUBLIC` Operation across the external/public boundary. `OwnerModuleInvoker` is a separate host/application port for owner-local invocation of an exact installed externally callable Operation.

`OwnerModuleInvoker` is not exposed in `ModuleContext`. Application assembly gives Modules separate facade objects for `ModuleDirectory` and `ModuleInvoker`, not the concrete live registry. Thus a Module cannot downcast a context port to obtain owner-local invocation simply because it is installed in the same process.

## Owner-local Material boundary

Owner-local invocation represents the owner interacting with an installed Module inside their MADRE installation. It is a receiver boundary, not a new Privacy carrier, Operation visibility or privilege assigned to Modules.

The owner-local route resolves the canonical installed Module and exact `PUBLIC` Operation binding, accepts a real `OperationCall`, and therefore preserves accepted-Material Privacy, exact EffectProfile selection, causal Integrity composition and output-contract validation.

`OperationBinding.invoke` validates the Module-created result against the exact Operation output contract. `OwnerModuleInvoker.invokeOwner` returns that Material directly, at the Sensitivity created by the Module. It does not apply `PublicResultTransformer` and does not lower Sensitivity merely because the owner receives the result locally.

PRIVATE Operations remain Module-internal. Owner-local invocation is not authority to call arbitrary implementation details.

No Privacy is inferred from localhost, same process, class-loader placement, CORE assignment, bundled placement or machine locality.

## External/public Material boundary

Every executable `PUBLIC` binding owns a `PublicResultTransformer`. The transformer runs after internal Operation output validation and before Material leaves the public invocation boundary.

The external result must be new declared Material with a new identity, remain inside the Operation output contract and have Sensitivity able to reach `Privacy.PUBLIC`. Raw internal Material cannot be returned through this path.

Semantic minimization belongs to Module behavior; runtime validation prevents bypass. This is not a generic policy evaluator.

Owner-local and PUBLIC invocation are genuinely distinct receiver boundaries. Owner-local execution does not invoke the public transformer and then attempt to recover sensitive information.

## Generic application adapter

`madre-app` has no compile-time dependency on the concrete owner-interaction Module. It discovers declarations and codecs through installed Module/runtime contracts only.

The console/non-interactive adapter can invoke both receiver boundaries generically. Input is decoded with the exact canonical installed `MaterialType` codec and a canonical `OperationCall` is constructed. For no-effect Operations it uses `withoutEffect`; for a consequential Operation it selects the exact declared EffectProfile, using `<operation>@<effect-profile>` only when disambiguation is necessary. The host supplies only actual non-user causal participants and never asks the user to fabricate Integrity values.

The console is a replaceable local adapter rather than a workflow/policy/chat framework.

## Reasoning-mechanism installation and discovery

Reasoning mechanisms are independently installable and architecturally distinct from Modules. Their artifacts live in a separate reasoning installation directory and use a separate service-provider contract.

The public `madre-reasoning-spi` artifact contains the typed reasoning execution contract and the minimal installation materialization boundary. A reasoning adapter depends on that SPI plus whatever published computation-contract artifacts it implements. It does not depend on `madre-app` or Kernel runtime implementation classes such as registries, SQLite stores or schedulers.

A reasoning adapter JAR exposes `ReasoningMechanismProvider` through Java's service-provider mechanism. Installed distributions discover JARs from their sibling `reasoning/` directory by default; `reasoning.directory` explicitly overrides it. Missing and empty directories are valid.

`madre-app` owns only generic discovery, read-only delivery of the `reasoning.*` owner configuration namespace and registration of materialized mechanisms. It contains no concrete adapter imports, provider factory table, provider-type switch or provider-specific property parser.

Each provider owns parsing and validation of its configuration. One provider may materialize zero, one or many `ReasoningMechanism` values. Disabled or unconfigured mechanisms produce no registration. Invalid enabled configuration fails startup clearly. Privacy remains explicit provider/owner configuration and is never inferred from endpoint, transport or locality.

The shipped llama.cpp AF_UNIX, explicit llama.cpp loopback-HTTP compatibility and OpenAI-compatible adapters are copied into the same `reasoning/` directory and discovered through this exact path. Bundled placement grants no privilege and does not enable an instance.

Startup materialization and registration are transactional at the application-assembly level: a provider/materialization/registration failure closes already-created registrations and loaders so no half-registered reasoning installation remains reachable.

## Kernel boundary

Kernel owns only shared runtime responsibilities that presently require central coordination:

- live executable Module registry and the host owner-local/public invocation mechanics;
- optional resolution of the configured CORE Module identity;
- registry of installed reasoning mechanisms;
- deterministic selection of compatible reasoning mechanisms;
- shared reasoning resources;
- immediate and durable reasoning execution;
- timeout, cancellation and bounded reasoning retry;
- opaque reasoning-result delivery and ordinary runtime logging.

Kernel does not own Module state, Material semantics, semantic workflows, artifact meaning, ordinary application I/O, search, model interpretation or continuation. It does not create Module Material.

The runtime may boot with zero reasoning mechanisms. A Module Operation that later requests unavailable reasoning fails or waits according to the reasoning execution contract; mechanism absence is not a platform boot failure. A Module that uses no reasoning remains executable with an absent/empty reasoning installation directory.

## ReasoningCapability boundary

The public reasoning SPI is deliberately reasoning-specific:

```text
ReasoningCapability<R,C extends ReasoningComputation<R>>
```

The manifest describes only exact reasoning contract, receiving Privacy, location, expected latency and resource claims. Availability is observed physical/runtime state.

Selection uses the exact computation contract, `Sensitivity <= Privacy`, availability, resources, typed location/latency preferences and deterministic ordering.

Operation Risk is not carried into reasoning work. Reasoning manifests do not carry action-realizer Integrity. A reasoning mechanism supplies computation; it is not automatically the physical realizer of a Module Operation's external effect.

There is no generic Kernel `Capability<C,R>` action-dispatch path.

## Shipped owner-interaction behavior

The shipped owner-interaction Module is ordinary installed behavior. It may be CORE, but its invocation and reasoning behavior must be identical when CORE is absent.

`standard-prompt` performs immediate reasoning and creates response Material only, so it is a no-effect Operation.

`fast-lane` creates durable Kernel reasoning work and persists Module-owned pending semantic state that continues beyond the foreground response. Its `durable-background-write` EffectProfile is `WRITE/AUTONOMOUS`.

`collect-background` is the Module-specific bounded path for interpreting terminal durable reasoning, creating Module Material, acknowledging the Kernel work and deleting completed pending semantic state. Its `acknowledge-completed-background` EffectProfile is `DELETE/LIVE_INTERACTION`.

The durable Kernel bytes remain opaque runtime state. The Module-owned pending file carries only semantic association needed to interpret eventual results. On restart those two persistence domains are rebuilt independently and compose through the existing `ReasoningService` work lifecycle.

## Search placement

Search is ordinary application/domain I/O. `madre-web-search` supplies reusable typed web-search values and `madre-adapter-searxng` supplies an ordinary SearXNG Java client.

The SearXNG client does not depend on Kernel and does not implement `ReasoningCapability`. The former standalone WebSearch Module is not part of the shipped application.

A future domain Module that genuinely owns research/search behavior may depend on the search library/client and compose search semantically inside that Module. That does not require a Kernel search abstraction.

## CORE installation role

`roles.core`, when present, contains one ordinary installed `ModuleId`. CORE is only role lookup. MADRE also supports no CORE assignment, and a configured-but-absent CORE does not prevent boot.

CORE does not alter invocation authority, Operation visibility, Security Algebra, reasoning installation, reasoning selection, scheduling or public-result rules. There is no CORE subtype or privileged registration path. The owner-local port is not a CORE port and is not exposed to a CORE Module.

## Storage

Module persistence remains inside each Module.

Kernel SQLite persists only durable reasoning work that must survive restart: opaque serialized computation input, delivery Module identity, stable reasoning-contract identity, scheduling/attempt/cancellation/retry state and pending opaque output until collection/acknowledgement/retention cleanup.

Persisted durable work does not name a concrete adapter implementation class. The live Module and reasoning registries are rebuilt at application boot; queued work becomes executable again when its compatible reasoning contract/mechanism is registered.

The shipped owner-interaction Module separately reloads its pending semantic state after restart, interprets completed reasoning itself, acknowledges the durable work and removes completed pending state. Kernel never learns the semantic meaning of that work.

## Provider environment

MADRE configuration may describe provider-owned reasoning instances and explicit receiving Privacy/location/resource facts. Provider accounts, credentials, authentication flows, authorization, permissions, roles and sessions remain outside MADRE's domain model and do not become Security Algebra values.
