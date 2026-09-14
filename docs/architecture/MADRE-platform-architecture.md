# MADRE Platform Architecture

## Responsibility map

```text
Owner / local surface
  |
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
       ReasoningCapability adapter
```

The Security Algebra is behavior of values carried by these objects. It is not a runtime component.

## Module boundary

A Module is the semantic and application/domain boundary. It owns domain state and persistence, Material, Agents, Skills, Workflows, public/private Operations, application I/O, semantic transformations, continuation, artifacts and presentation that belong to that domain.

An Operation is bounded callable Module/application behavior. It declares the Material it accepts, receiving Privacy, Material it may produce, maximum output Sensitivity and any EffectProfiles for consequential variants. Module behavior uses ordinary Java and SDK types.

A Module does not become a Kernel extension merely because one Operation performs file, network, database, device, search or other application I/O.

`ModuleDefinition` is the canonical declarative surface. `ModuleInstance` binds it to exact executable `OperationBinding` values. Registration validates that declared and executable identities/contracts match exactly before the Module becomes reachable.

## Installation and discovery

Modules are ordinary JVM JARs exposing `ModuleProvider` through Java's service-provider mechanism. The application discovers configured Module JARs with JDK path/class-loader APIs, constructs each Module from `ModuleContext`, and registers its `ModuleInstance`.

Shipped Modules use the same route as independently built Modules. Bundling, class-loader placement, process placement or CORE assignment grants no authority.

The registry is in-memory and rebuilt at boot. `ModuleDirectory` exposes reachable public definitions; `ModuleInvoker` invokes one exact installed `PUBLIC` Operation without callers depending on the concrete implementation class.

## External/public Material boundary

Every executable `PUBLIC` binding owns a `PublicResultTransformer`. The transformer runs after internal Operation output validation and before Material leaves the public invocation boundary.

The external result must be new declared Material with a new identity, remain inside the Operation output contract and have Sensitivity able to reach `Privacy.PUBLIC`. Raw internal Material cannot be returned through this path.

Semantic minimization belongs to Module behavior; runtime validation prevents bypass. This is not a generic policy evaluator.

## Kernel boundary

Kernel owns only shared runtime responsibilities that presently require central coordination:

- live executable Module registry and public invocation port;
- optional resolution of the configured CORE Module identity;
- registry of installed reasoning mechanisms;
- deterministic selection of compatible reasoning mechanisms;
- shared reasoning resources;
- immediate and durable reasoning execution;
- timeout, cancellation and bounded reasoning retry;
- opaque reasoning-result delivery and ordinary runtime logging.

Kernel does not own Module state, Material semantics, semantic workflows, artifact meaning, ordinary application I/O, search, model interpretation or continuation. It does not create Module Material.

The runtime may boot with zero reasoning mechanisms. A Module Operation that later requests unavailable reasoning fails or waits according to the reasoning execution contract; mechanism absence is not a platform boot failure.

## ReasoningCapability boundary

The Kernel SPI is deliberately reasoning-specific:

```text
ReasoningCapability<R,C extends ReasoningComputation<R>>
```

The manifest describes only exact reasoning contract, receiving Privacy, location, expected latency and resource claims. Availability is observed physical/runtime state.

Selection uses the exact computation contract, `Sensitivity <= Privacy`, availability, resources, typed location/latency preferences and deterministic ordering.

Operation Risk is not carried into reasoning work. Reasoning manifests do not carry action-realizer Integrity. A reasoning mechanism supplies computation; it is not automatically the physical realizer of a Module Operation's external effect.

Current adapters provide typed text inference through llama.cpp AF_UNIX, explicit llama.cpp loopback HTTP compatibility, and OpenAI-compatible HTTP.

There is no generic Kernel `Capability<C,R>` action-dispatch path.

## Search placement

Search is ordinary application/domain I/O. `madre-web-search` supplies reusable typed web-search values and `madre-adapter-searxng` supplies an ordinary SearXNG Java client.

The SearXNG client does not depend on Kernel and does not implement `ReasoningCapability`. The former standalone WebSearch Module is not part of the shipped application.

A future domain Module that genuinely owns research/search behavior may depend on the search library/client and compose search semantically inside that Module. That does not require a Kernel search abstraction.

## CORE installation role

`roles.core`, when present, contains one ordinary installed `ModuleId`. CORE is only role lookup. MADRE also supports no CORE assignment, and a configured-but-absent CORE does not prevent boot.

CORE does not alter invocation authority, Operation visibility, Security Algebra, reasoning selection, scheduling or public-result rules. There is no CORE subtype or privileged registration path.

## Storage

Module persistence remains inside each Module.

Kernel SQLite persists only durable reasoning work that must survive restart: opaque serialized computation input, delivery Module identity, scheduling/attempt/cancellation/retry state and pending opaque output until collection/acknowledgement/retention cleanup.

The live Module and reasoning registries are rebuilt at application boot.

## Provider environment

MADRE configuration may describe installed reasoning endpoints and explicit receiving Privacy/location/resource facts. Provider accounts, credentials, authentication flows, authorization, permissions, roles and sessions remain outside MADRE's domain model and do not become Security Algebra values.
