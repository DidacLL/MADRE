# MADRE Platform Architecture

## Responsibility map

```text
Owner / local surface
  |
  v
installed ordinary Module
  | owns application/domain behavior, Material, meaning, state, Agents,
  | Workflows, Operations, transformation and continuation
  | may optionally be assigned the CORE role
  v
live Module registry / public Module invocation boundary
  | discovery, exact executable binding validation and PUBLIC reachability
  | no privilege from CORE, bundling, process or class-loader placement
  |
  +--> ordinary Module Java/application behavior
  |
  `--> physical work only where the current execution contract genuinely applies
         v
       Kernel
         | capability selection, resources, scheduling, durable work and delivery
         v
       physical adapter/mechanism
```

The Security Algebra is behavior of values carried by these objects. It is not a
runtime component in the diagram.

## Module boundary

A Module is the semantic and application/domain boundary. It owns its domain state and
persistence, Material, Agents, Skills, Workflows, public and private Operations,
application I/O, semantic transformations, continuation, artifacts, and presentation
that belong to that domain.

An Operation is a bounded callable Module/application function. It declares the
Material it can accept, the receiving Privacy that applies, the Material it may
produce, and its EffectProfiles when it has consequential variants. Module behavior
uses ordinary Java and SDK types. A Module does not become a Kernel extension merely
because one Operation performs file, network, database, device, or other application
I/O.

`ModuleDefinition` is the canonical declarative surface. `ModuleInstance` binds that
definition to the exact executable `OperationBinding` for every declared Operation.
Registration validates that the binding set exactly matches the declared identities
and canonical contracts before the Module becomes reachable.

## Installation and discovery

MADRE installs executable Modules as ordinary JVM JARs. A JAR exposes a
`ModuleProvider` using the standard Java service-provider mechanism. The application
loads providers from the configured Module directory with JDK path/class-loader APIs,
constructs each Module with ordinary SDK services, and registers its `ModuleInstance`.
This mechanism is cross-platform Java installation/discovery; it is not a marketplace,
container architecture, or separate plugin framework.

Shipped Modules are packaged into the distribution's Module directory but are not
concrete compile-time dependencies of `madre-app`. Independently built Module JARs use
the same provider and registration route. Placement in the distribution, a particular
class loader, or the MADRE process never creates authority.

The live registry is in-memory and is rebuilt by installed Module providers at boot.
Its directory API exposes reachable public definitions. Its invocation API resolves
one installed Module and exact canonical `PUBLIC` Operation and calls it without the
caller constructing or naming the concrete implementation class.

## External/public Material boundary

A public Operation may create internal Material that is not itself safe to expose.
Every executable `PUBLIC` binding therefore owns a semantic public-result transformer.
The transformer runs after the internal Operation result is validated and before the
result leaves the public Module invocation boundary.

The external result must be new declared Material with a new identity, must still
satisfy the declared output contract, and must be minimized enough to reach
`Privacy.PUBLIC`. Raw internal Material identity cannot be returned as the public
result. This enforces a mandatory semantic boundary without introducing a generic
policy evaluator: the Module decides the transformation; the runtime prevents bypass.

## Kernel boundary

Kernel owns mechanisms shared across physical execution:

- the in-memory executable Module registry and public invocation port;
- the current installation registry of physical Capability manifests and adapter
  bindings;
- optional lookup of the configured CORE Module identity;
- selection of applicable physical mechanisms;
- immediate and durable physical work;
- timing, priority, timeout, cancellation, resource coordination, and retry after
  physical failure;
- opaque physical result delivery and ordinary runtime logging.

Kernel does not own Module state, Material semantics, semantic workflows, artifact
meaning, or ordinary application code. It does not create Module Material.

The runtime may boot with zero physical Capabilities/connectors. An Operation that
actually submits physical work for which no applicable mechanism is installed fails or
waits according to the ordinary execution contract when invoked; the absence of a
reasoning mechanism is not a platform boot failure.

## Capability boundary and current recovery status

The current code still has a generic `Capability<C,R>` SPI whose manifest describes a
physical command/result contract, receiving Privacy, physical Integrity where
applicable, availability, physical properties, and required resources. Adapters
receive physical commands, not Material, Module, Agent, Workflow, Skill, or Operation
objects.

Current physical contracts include text inference and web search, with llama.cpp,
OpenAI-compatible and SearXNG adapters. Availability remains an observed physical fact
and Kernel selects only explicitly available mechanisms.

This generic shape is not the desired final architecture. It is known recovery debt.
The correction to reasoning-only `ReasoningCapability`, the placement of
SearXNG/WebSearch, and removal of the universal physical-action Kernel path from
ordinary Module behavior are intentionally deferred. No new Module execution in this
slice is built on generic Capability dispatch, and no new Memory, Knowledge,
Communication, or WebSearch abstraction is introduced.

## CORE installation role

`roles.core`, when present, contains one ordinary installed `ModuleId`. CORE is only a
role lookup. MADRE also supports no CORE assignment at all, and a configured but absent
CORE does not prevent platform boot.

CORE does not alter invocation authority, Operation visibility, Security Algebra,
physical selection, scheduling, or public-result rules. There is no CORE subtype,
privileged registration path, special class-loader treatment, or boot-time requirement
for Operations with particular names. Any shipped owner-interaction behavior remains
ordinary Module behavior.

## Storage

Module persistence stays inside each Module. Kernel SQLite persists only physical work
that must survive restart: opaque queued input, scheduling/attempt state, and pending
physical output until delivery. Runtime cleanup removes payload bytes according to
delivery and retention. The live Module registry is rebuilt at application boot.

## Provider environment

MADRE configuration describes installed physical connector endpoints and physical
behavior. Account sessions, credentials, authentication flows, authorization,
permissions, and provider roles remain outside the MADRE domain model and do not alter
Capability Privacy or Integrity.
