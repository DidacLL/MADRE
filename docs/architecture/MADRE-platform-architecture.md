# MADRE Platform Architecture

## Shape

```text
Module
  owns meaning, data, UI, Agents, Skills, Workflows, WorkPlans,
  Materials, transformations, result interpretation, and Operations
        |
        | declarative definitions and execution requests
        v
MADRE SDK
  typed reusable value objects and segregated behavior ports
        |
        v
Kernel
  physical execution lifecycle, resources, mechanism selection,
  reference-only durable work, recovery, and result delivery
        |
        v
Capability
  one physical inference or execution mechanism
```

The Security Algebra is used by objects at any layer but belongs to none of those
layers. A scope or relation composes itself from the participating values. There is no
security component beside the diagram.

## Module ownership

A Module owns every decision requiring semantic understanding:

- which Material matters and how it is classified;
- which scopes are reachable or exposed together;
- Agent behavior and private state;
- adoption and interpretation of Skills and Workflows;
- WorkPlan meaning;
- transformations that create new Material;
- interpretation of Capability output;
- whether to continue, request more physical computation, or invoke a bounded
  Operation;
- the implementation and domain consequences of its Operations.

A Module may expose no Agent, Skill, Workflow, or Operation. Those are available
building blocks, not mandatory framework layers.

## SDK boundary

The SDK contains immutable serializable definitions for Module, Agent, Skill,
Workflow, Operation, EffectProfile, Material, Capability, exact security scopes, and
execution requests.

Definitions contain no Python callable, provider client, runtime store, Kernel object,
or private Module state. Runtime behavior is attached through small interfaces. This
keeps the public model translatable to JSON or XML and permits a future tool to
assemble definitions without generating Python framework code.

The SDK does not define semantic loops, planning algorithms, memory policy, tool-use
conventions, an assistant archetype, or CORE behavior.

## Kernel boundary

Kernel may:

- select a Capability whose typed physical properties match a request;
- compose the request’s actual Material surface with the selected Capability surface;
- execute transient work;
- persist reference-only durable intent and lifecycle metadata;
- resolve Module-owned Material immediately before a durable attempt;
- coordinate scarce physical resources;
- deliver new ordinary output Material;
- report transient execution failures.

Kernel may not:

- inspect payload meaning to select semantic behavior;
- reinterpret or reclassify Material;
- turn model output into a command;
- decide a Module continuation;
- expose generic shell or network powers to a model;
- keep Security Algebra decisions, relations, operands, or denials;
- construct permission, authorization, trust, or identity systems;
- give the Module occupying CORE any distinct interface.

## Capability boundary

A Capability definition describes one physical mechanism, its typed execution
properties, and the exact security surface exposed by its use. The adapter implements
transport, protocol mapping, process invocation, model loading, or hardware access.

A physical mechanism does not become an Agent, Skill, Operation, controller, or
semantic authority. Its output is data. Provider authentication and client setup are
external environment mechanics supplied to the adapter, not MADRE concepts.

## Catalog

The catalog stores and lists declarative Module definitions. Catalog presence means
only that a definition is available. It does not route calls, evaluate security,
grant authority, score participants, or predict feasibility.

Cross-Module Agent or Operation invocation is not implemented by inventing a generic
broker. A future execution path must be justified by an exact MADRE behavior and use
the same public definitions and intrinsic algebra as every other path.

## CORE

CORE is an installation-level assignment of one ordinary Module to the default
interaction/fallback position. The assignment is outside the SDK and Kernel.

The selected Module keeps the same definition, behavior ports, security surface, and
execution path it has when not assigned CORE. No public type or runtime branch is
allowed to test for that assignment.
