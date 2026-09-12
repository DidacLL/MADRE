# MADRE Module and Agent Interoperability

## Objective

The public SDK provides the building blocks needed for independent Modules to
describe compatible surfaces without forcing one implementation language, reasoning
loop, orchestration framework, or provider convention.

Interoperability is descriptive. A definition does not become authorization,
execution routing, or a security decision.

## Definition and implementation

Every public entity has a declarative definition distinct from executable behavior.

A definition is:

- immutable and strongly typed;
- explicit about identity, revision, contracts, contained/exposed members, and
  applicable security scopes;
- serializable without Python callables, import paths, runtime clients, or opaque
  extension dictionaries;
- suitable for a future equivalent JSON or XML representation.

An implementation is private Module or adapter code bound through a segregated
interface. Implementations may evolve without changing unrelated definitions.

## ModuleDefinition

A Module definition owns:

- its identity and description;
- exact Module security facts when applicable;
- the Material/security scopes it manages or can expose;
- public Agents, Skills, Workflows, and Operations;
- optional discovery terms.

Its effective SecuritySurface is calculated from those members. It is not an
independently declared summary.

A Module may omit every optional entity. The SDK does not manufacture a default
Agent, assistant, planner, Workflow, or Operation.

## AgentDefinition

An Agent definition owns purpose, instructions when applicable, typed input/output
Material contracts, and the Skills, Workflows, and Operations it exposes.

Its surface is the structural composition of those exact exposed members and any
Agent-specific scope facts. Consequently:

- Sensitivity may apply directly to an Agent or arrive from a reachable sub-scope;
- Privacy is the minimum applicable Privacy among its exposed surfaces;
- removing an exposed Operation can legitimately change the Agent’s effective
  Privacy;
- no registry or evaluator calculates a separate Agent security value.

Agent behavior is a Module-owned implementation. State, memory, delegation, learning,
and model prompting remain private unless a later exact interoperability need
justifies a typed public contract.

## SkillDefinition and WorkflowDefinition

A Skill describes reusable behavior, knowledge, or instruction material, its purpose,
resources, and optional input/output contracts.

A Workflow describes a reusable semantic recipe and typed input/output contracts.

Neither is automatically executable by Kernel. MADRE does not require a universal
Workflow language or translate third-party agent conventions into Kernel semantics.

## OperationDefinition and EffectProfile

An Operation is one explicitly bounded Module-owned callable effect. Its definition
contains purpose, typed input/output contracts, effect description, repeatability,
exact security scope, and one or more EffectProfiles.

Each EffectProfile belongs to exactly one Operation and contains only:

- its own identity;
- the exact Operation identity;
- Risk;
- Autonomy.

Callers cannot replace those values. Controllers and executors are facts of one
actual invocation and are composed into Control and EffectExecution at that time.
They are not catalog metadata or a generic permission system.

## Material

`Material[T]` contains exact identity, a `MaterialContract`, typed payload, and one
security scope bound to the same identity. Its digest and durable handle are mechanical
properties of that Material.

All adaptations and generated outputs are new Material. There is no Artifact versus
ContextBundle security ontology, derivation method, validation status, generated
freshness, or ancestry contract.

## Behavior ports

The current SDK exposes only the minimum executable seams needed by real behavior:

- `ExecutionService.execute(ExecutionRequest) -> Material`;
- `ModuleBehavior.receive(Material, ExecutionService) -> Material`;
- optional Agent and Operation behavior protocols;
- `ModuleRuntime`, which binds one Module definition to its private behavior and
  granted execution service.

These interfaces do not prescribe what a Module should think, how an Agent should
loop, or whether a continuation exists.

The shipped interaction behavior uses only these ordinary ports. It submits Material
for physical execution, receives new Material, and calls its own injected interpreter.
Being selected as CORE does not alter that behavior.

## Catalog

The catalog registers and enumerates Module definitions and their contained public
entities. It does not:

- attach executable endpoints;
- select semantic targets;
- filter by security;
- route Agent or Operation calls;
- accumulate participants;
- record decisions;
- grant access.

A future cross-Module invocation mechanism must start from a concrete product
behavior. It cannot be inferred from generic multi-agent platform patterns.

## OOP boundary

Public classes own their invariants and derived values. Repositories, selectors,
adapters, and behaviors depend on narrow protocols. Serialization is a representation
of the domain objects, not the domain model itself.

Convenience functions, stringly typed identifiers, duplicated aggregate fields,
generic property bags, and type-name dispatch are not substitutes for domain objects.
Python is the current prototype language; its dynamic features do not weaken the
public contract.
