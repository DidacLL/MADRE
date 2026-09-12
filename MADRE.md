# MADRE

MADRE is a local-first system through which independent Modules use shared physical
inference and execution mechanisms without surrendering ownership of meaning, data,
behavior, or effects.

This document is the product contract. The focused documents under
`docs/architecture/` refine it without changing its meaning.

## Responsibility model

A **Module** is an independently owned application or integration boundary. It owns
its data, private state, interaction, Agents, Skills, Workflows, WorkPlans, Materials,
semantic transformations, result interpretation, and Operations.

The **public SDK** supplies reusable, typed, language-neutral building blocks through
which Modules describe those concepts and request physical execution. Definitions are
declarative and serializable. Executable behavior is bound separately.

The **Kernel** owns mechanism selection, resource coordination, transient execution,
reference-only durable work, lifecycle recovery, and result delivery. It treats
Material payloads as opaque and never decides what model output means or what should
happen next.

A **Capability** is a physical inference or execution mechanism. It is not a Skill,
Agent, semantic ability, trusted party, or grant of authority.

## Public building blocks

- A **Material** is an ordinary Module-owned value with its own identity, content
  contract, payload, and applicable security facts.
- An **Agent** is a Module-owned intelligent actor. MADRE describes its public surface
  without prescribing its private reasoning, state, memory, or loop.
- A **Skill** is reusable behavior, knowledge, or instruction material.
- A **Workflow** is a reusable semantic recipe. MADRE does not impose a universal
  Workflow execution language.
- A **WorkPlan** is semantic planning state owned by its Module or Agent. Executable
  portions become ordinary execution requests.
- An **Operation** is bounded callable behavior deliberately exposed and implemented
  by a Module.
- An **EffectProfile** is one bounded execution variant of one Operation. It carries
  exactly that Operation/profile identity, Risk, and Autonomy.

Sensitivity can apply to any exact exposed or reachable scope, including Module,
Agent, Operation, Capability, and Material surfaces. A Module that manages S5 Material
has an effective S5 surface, while an isolated Material it creates through
tokenization, anonymization, or minimization may independently be S4, S3, or S2.

A composite Agent is as private as the meet of the Operations and other surfaces it
actually exposes. Exposing fewer or different Operations can therefore produce a
different effective Privacy. These values are structural results, not manually
duplicated descriptor declarations.

## Security Algebra

The Security Algebra is standalone. It is neither a Kernel subsystem nor an
observability, policy, authorization, or audit facility.

Its five ordered carriers are nominally distinct:

- **Sensitivity**: the confidentiality consequence of exposing an exact scope.
- **Privacy**: the confidentiality boundary of an exact observer scope.
- **Integrity**: bounded causal or effect-realization responsibility for an exact
  control/execution scope.
- **Risk**: the consequence magnitude of one bounded EffectProfile.
- **Autonomy**: the residual machine execution of that same EffectProfile.

There is no global score. Integrity does not mean truth, reliability, model quality,
provider reputation, or generic trust.

`Privacy.UNKNOWN` means Privacy applies but no stronger confidentiality boundary is
established. `None` means Privacy does not apply to that exact scope. Location and
provider never imply Privacy: remote is not UNKNOWN, local is not private, and a
provider name establishes no Privacy value.

Exact scopes compose into immutable surfaces:

- effective Sensitivity is the maximum applicable Sensitivity;
- effective Privacy is the minimum applicable Privacy;
- effective Integrity is the minimum applicable Integrity;
- a facet with no applicable member remains `None`.

The three relations are fixed:

```text
Disclosure:
    S_D = max Sensitivity of actual exposed source scopes
    P_D = min Privacy of actual observer scopes
    match iff S_D <= P_D

Control:
    D_C(R,A) = min(R,A)
    I_C = min Integrity of actual non-user controllers
          or I5 when there are none
    match iff min(R,A) <= I_C

Effect execution:
    I_E = min Integrity of actual effect-realizing executors
    executor set must be nonempty
    match iff R <= I_E
```

The values travel with the participating objects and compose when another exact scope
is added. An incompatible addition cannot construct the next object, so that request
cannot continue. The responsible Module may create different Material, expose a
different surface, choose another bounded action, or make another request.

There is no security evaluator, broker, admission service, decision record, rejected
candidate, history, evidence ledger, ancestry, freshness, lineage, completed-output
identity, retry authority, or reconstructed state. Runtime diagnostics may describe a
failed attempt, but diagnostics never become security operands.

There is no disclosure exception. User presence, approval, or an interaction session
cannot change Sensitivity or Privacy. Live user action is represented only by the
appropriate Autonomy value of the exact EffectProfile.

When a Module transforms, minimizes, selects, validates, summarizes, tokenizes,
anonymizes, or otherwise adapts information, the output is simply new Material with a
new identity and its own applicable security facts. Optional provenance is a separate
future concern and never participates in the algebra.

## Execution

The ordinary path is:

```text
Module behavior
    -> public SDK execution request
    -> Kernel selects one physical Capability
    -> the request and Capability surfaces compose
    -> Capability performs physical computation
    -> Kernel returns new ordinary Material
    -> requesting Module interprets it
    -> Module alone chooses any continuation or bounded Operation
```

Model output is never a Kernel command. A model cannot select its own Capability,
invoke Kernel, or realize an effect. Consequential behavior enters MADRE only through
an explicit bounded Module-owned Operation and its exact EffectProfile. There is no
CORE bypass.

Durable work stores execution intent, a Material handle, output specification,
mechanism/lifecycle metadata, and result digests. The Module retains the payload and
resolves it just in time. Security compositions, denials, model output, prompts,
private context, and Module state are not durable Kernel data.

## CORE

CORE is an installation role assigned to an ordinary Module. The Module acting as
CORE is exactly like every other Module at the SDK, Kernel, Capability, and Security
Algebra boundaries.

MADRE defines no CORE-specific Module class, turn, selection, delegation helper,
continuation contract, special service, security default, or privileged API. Changing
which installed Module occupies the role changes only installation/application
configuration.

## External mechanics

MADRE does not model credentials, API keys, authentication, authorization, RBAC,
permissions, access roles, clearances, or provider trust. Those are external
adapter/environment mechanics. An adapter may receive an already configured transport
or client, but those mechanics are not public MADRE values and never enter Security
Algebra composition.

## Implementation direction

The current Python implementation is a prototype of a language-neutral SDK. Its
public definitions must remain strongly typed, cohesive, immutable, serializable, and
independent of Python callables so a future Module can be assembled from declarative
JSON or XML plus separately bound implementations.

MADRE is not a universal agent framework, production RAG platform, credential vault,
policy engine, generic shell, or unrestricted Internet environment. New abstractions
must earn their place through a concrete MADRE responsibility.
