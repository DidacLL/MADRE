# MADRE Module SDK and Interoperability

## Public object model

The SDK is a public Java 21 library under the Owner's namespace. It contains immutable
objects with nominal identities and constructor-enforced invariants.

The core Module model is:

- `ModuleDefinition`: identity, version, declared Material types, Agents, Skills,
  Workflows, Operations, and public references;
- `AgentDefinition`: identity, purpose, applicable Integrity, and references to the
  Skills, Workflows, and Operations that form its repertoire;
- `SkillDefinition`: reusable Module-owned ability, knowledge, or instruction;
- `WorkflowDefinition`: reusable Module-owned semantic behavior;
- `OperationDefinition`: one bounded callable Module behavior, its accepted Material
  kinds and Privacy, produced Material kinds and Sensitivity promises, and applicable
  EffectProfiles;
- `EffectProfile`: one Operation variant with Risk and Autonomy;
- `MaterialType<T>` and `Material<T>`: the Module-owned semantic value model.

Module, Agent, Operation, Skill, Workflow, EffectProfile, Material, and MaterialType
have nominal identity types where real identity is needed. Algebraic application
points do not acquire separate identities.

A Module definition rejects unknown or conflicting references. The same Skill,
Workflow, or Operation may be referenced wherever its Module intentionally reuses it.
Serialization layout does not create an ownership rule.

## Behavior binding

Definitions contain no executable class names, import paths, scripts, prompts,
provider payloads, or arbitrary metadata.

Runtime behavior is bound through responsibility-specific Java interfaces:

- an Operation implementation receives an already-composed typed Operation call;
- the Module-facing execution service accepts a typed physical work request;
- the Module directory exposes currently reachable public definitions.

There is no universal `receive` method, Agent loop, assistant turn, planner, or
workflow engine. A concrete Module implements only its own behavior.

The shipped CORE-capable Module is the first real reference implementation. No
separate demonstration Module or test-only substitute is treated as product proof.

## Structural algebra in definitions

An Operation directly declares the Material kinds it accepts and the Privacy applying
to that input. It directly declares the Material kinds it promises to produce and
their maximum Sensitivity. This information is part of the Operation contract, not an
autonomous input/output surface object.

An Agent derives its effective Privacy from its exposed Operations. A Module derives
its effective Sensitivity for an exact state from its reachable Material and declared
outputs. Directory results include only definitions whose exact current values
compose.

The SDK provides immutable collection and builder behavior where it protects these
invariants, but it does not create a separate class for every field or every
mathematical intermediate.

## Module registry

A running Module registers its definition in Kernel's live registry. Restart empties
that registry; Modules register again.

A Module directory query supplies the caller's accumulated applicable values and
returns only currently reachable:

- Module identities and descriptive information;
- Agents and their reachable Operation references;
- public Operations and their declared contracts.

The query does not expose Capability identities. It does not persist a result or
change either Module.

Cross-Module execution is intentionally not generalized by the current foundation.
The first concrete Module that needs it will define the smallest transport needed by
its actual bounded Operation. Directory discovery does not silently create an
invocation engine.

## CORE assignment

Installation configuration maps the CORE role to one ordinary Module identity. A
candidate is eligible when its ordinary public definition supplies the role's
required behavior. The shipped owner-interaction Module is the default candidate and
provides an interaction Agent exposing standard-prompt and fast-lane Operations.

The mapping does not alter the Module definition. Module authors do not inherit from a
CORE class and no CORE behavior appears in Kernel's physical execution path.

## Codecs

Explicit versioned codecs map definitions to JSON boundary representations. Domain
classes do not inherit from codec or HTTP framework classes and expose no
`Map<String, Object>` extension bag.

A future XML codec maps the same object model. Executable behavior is never
serialized.
