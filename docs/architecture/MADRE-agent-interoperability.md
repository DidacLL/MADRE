# MADRE Module SDK and Interoperability

## Public object model

The SDK is a public Java 21 library under the Owner's namespace. It contains immutable
objects with nominal identities and constructor-enforced invariants.

The core Module model is:

- `ModuleDefinition`: identity, version, declared Material types, Agents, Module-provided
  Skills, Operations, and public references;
- `ModuleInstance`: one executable installation of the canonical definition together
  with the exact executable binding for every declared Operation;
- `AgentDefinition`: identity, purpose, applicable Integrity, learned Skills, owned
  Workflows, and Operations that form its repertoire;
- `SkillDefinition`: reusable Module-provided ability, knowledge, or instruction;
- `WorkflowDefinition`: Agent-owned semantic behavior. The current minimal form is an
  ordered sequence of Operations triggered as one behavior;
- `OperationDefinition`: one bounded callable Module behavior, its accepted Material
  kinds and Privacy, produced Material kinds and Sensitivity promises, and applicable
  EffectProfiles;
- `OperationBinding`: the exact executable implementation of one canonical Operation
  declaration, including the public-result transformation when the declaration is
  `PUBLIC`;
- `EffectProfile`: one Operation variant with Risk and Autonomy;
- `MaterialType<T>` and `Material<T>`: the Module-owned semantic value model.

Module, Agent, Operation, Skill, Workflow, EffectProfile, Material, and MaterialType
have nominal identity types where real identity is needed. A Workflow identity is
scoped to its owning Agent. Algebraic application points do not acquire separate
identities.

A Module definition rejects unknown or conflicting references. An executable Module
instance additionally rejects missing, undeclared, foreign, or non-canonical Operation
bindings before registration. Skills and Operations remain Module-owned declarations;
Workflows remain declared inside their owning Agent.

## Executable Module boundary

A Module is a complete application/domain boundary implemented with ordinary Java.
An Operation is one bounded callable function of that application. Definitions contain
no executable class names, import paths, scripts, prompts, provider payloads, or
arbitrary metadata.

Runtime behavior is bound through responsibility-specific SDK interfaces:

- `Operation<I,O>` implements one bounded declared Operation;
- `ModuleInstance` associates one `ModuleDefinition` with all executable bindings;
- `ModuleProvider` materializes one installable executable Module from ordinary SDK
  services supplied in `ModuleContext`;
- `ModuleRegistration` registers executable Module behavior, not metadata alone;
- `ModuleDirectory` exposes currently reachable public definitions;
- `ModuleInvoker` invokes an exact installed `PUBLIC` Operation from an
  `OperationCall` without exposing the concrete Module implementation class;
- `ExecutionService` remains the Module-facing port for physical work that actually
  belongs in the current Kernel execution path.

There is no universal `receive` method, Agent loop, assistant turn, planner, policy
engine, or Kernel workflow engine. A concrete Module implements only its own behavior.
Ordinary Module Java code is not required to route application I/O through generic
Kernel Capability dispatch merely because it performs I/O.

## Module installation and discovery

An installable Module artifact is an ordinary JVM JAR containing a Java service
provider for `ModuleProvider`. MADRE discovers JARs from the configured installation
Module directory using JDK class-loading and `ServiceLoader` APIs. This is Module
installation/discovery, not a marketplace, container, or separate extension
architecture.

Shipped Modules may be placed in the distribution's Module directory, but they use the
same provider, registration, discovery, validation, and invocation contracts as an
independently installed Module. Bundling, class-loader placement, process placement,
or CORE assignment confers no authority.

The independent verification Module is built in a separate Gradle build against only
the published `madre-sdk` artifact. The application does not compile against that
fixture and does not add it as a normal dependency. Runtime verification installs its
JAR into a built MADRE distribution, discovers it, and invokes its public Operation.

## PUBLIC Operation boundary

`ModuleInvoker.invokePublic` resolves the installed Module and exact canonical
Operation binding. A missing Module, undeclared binding, forged/non-canonical
Operation declaration, or private Operation is rejected.

A `PUBLIC` binding must provide a Module-owned `PublicResultTransformer`. The Operation
first produces ordinary internal Module Material. Before that result crosses MADRE's
external/public invocation boundary, the transformer must create new declared
Material with a new nominal identity and semantics minimized enough to reach
`Privacy.PUBLIC`. The transformed Material must still satisfy the Operation's declared
output type, owner, and maximum Sensitivity. Returning the raw internal Material or a
new result that remains too sensitive is rejected.

This is not a generic policy evaluator. Semantic transformation remains Module
behavior; the runtime merely enforces that the public boundary cannot be bypassed.
Internal Module calls may continue to use internal Material without pretending that
such Material has crossed the public boundary.

## Structural algebra in definitions

An Operation directly declares the Material kinds it accepts and the Privacy applying
to that input. It directly declares the Material kinds it promises to produce and
their maximum Sensitivity. This information is part of the Operation contract, not an
autonomous input/output surface object.

An Agent derives its effective Privacy from its exposed Operations. A Module derives
its effective Sensitivity for an exact state from its reachable Material and declared
outputs. Directory results include only definitions whose exact current values
compose.

A Workflow adds no independent security decision or combined algebraic state. Each
Operation invocation composes from the actual Material entering that Operation and,
when it creates physical work, the resulting `WorkRequest` carries the values derived
from that exact bounded call.

## CORE assignment

CORE is an optional installation role containing an ordinary `ModuleId`. If configured
and the Module is installed, the live registry can resolve that identity. If no CORE is
configured or the configured Module is absent, MADRE still boots.

CORE assignment changes no Module definition, visibility, public-invocation authority,
Security Algebra value, Operation contract, scheduling privilege, or class hierarchy.
There is no required Operation name merely to qualify platform boot.

## Codecs

Explicit versioned codecs map definitions to JSON boundary representations. Domain
classes do not inherit from codec or HTTP framework classes and expose no
`Map<String, Object>` extension bag. Executable behavior is never serialized.

Definition codec version 2 nests Workflow declarations inside their owning Agent and
preserves Operation order. Development has no installed compatibility contract that
requires version-1 Module definitions to remain readable. A future XML codec can map
the same declarative object model without serializing executable bindings.

## Intentionally unrecovered areas

This executable Module slice does not broaden the existing generic physical
`Capability` architecture. That architecture remains recovery debt: it is expected to
narrow to reasoning-only `ReasoningCapability`; SearXNG/WebSearch placement remains to
be corrected; and the universal physical-action Kernel dispatch path remains to be
removed from ordinary Module/application behavior. These debts do not alter the
executable Module installation and public invocation contracts described here.
