# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first platform that lets independent applications share heterogeneous intelligence resources while keeping semantic ownership in those applications.

Modules decide what intelligence work is useful, what information it means, and what consequences should follow. MADRE provides deterministic infrastructure for interoperability, security composition, durable execution, resource coordination, inference-mechanism selection, recovery, brokering, result delivery and execution evidence.

## Authority map

This file is the canonical product contract.

Detailed architecture is owned by:

- `docs/architecture/MADRE-platform-architecture.md` — topology and responsibility placement;
- `docs/architecture/MADRE-execution-contract.md` — transient and durable execution, material lifecycle, mechanism selection, scheduling-facing semantics, results and recovery;
- `docs/architecture/MADRE-agent-interoperability.md` — Module/Agent/Skill/Workflow/Operation contracts, CORE contract, discovery, brokering and SDK;
- `docs/architecture/MADRE-security-algebra.md` — exact security scopes, relation-local normal forms, direct-user crossings, derivation and evidence.

`docs/implementation-baseline.md` describes current repository state only. `docs/design-memory/` preserves non-normative product rationale, examples, constraints, ecosystem observations and open research directions.

## Responsibility model

```text
Module
    owns meaning, data, interaction and agentic behavior
        |
        | public contracts / execution requests
        v
MADRE SDK + interoperability
    stable typed boundary for Modules and tooling
        |
        v
Kernel
    deterministic shared execution, resources, security composition,
    durable lifecycle, mechanism selection, routing and evidence
        |
        v
Inference mechanism / Capability adapter
    concrete physical computation and provider/software integration
```

A **Module** is an independently owned application or integration boundary. It may be a desktop application, service, script, editor integration, adapter, first-party MADRE Module or another bounded software system.

A Module owns, as applicable:

```text
domain records and persistence
UI / interaction state
private context and history
Agents and Agent state/memory
Skills
Workflows
WorkPlans
artifacts
Operations and domain mutations
domain-specific context selection/minimization
knowledge, learning and adaptation
result interpretation
```

A Module may expose no Agents at all.

Kernel owns deterministic shared execution. If a Kernel decision requires understanding the semantic meaning of private Module content, that decision belongs elsewhere.

A **Capability** is an available physical inference/execution mechanism with known properties. It is not a semantic Skill or a claim that an Agent can perform a user task.

## Core semantic entities

An **Agent** is a Module-owned intelligent actor. MADRE defines enough public/SDK structure for Agents to interoperate without prescribing one private reasoning implementation. At the simplest end an Agent may be instructions plus execution behavior; state, memory, delegation and richer orchestration are optional Module-owned concerns.

A **Skill** is reusable Agent behavior, knowledge or instruction material that can be adopted or translated into an Agent implementation.

A **Workflow** is reusable semantic behavior or a recipe used by an Agent/Module. MADRE does not require one universal Workflow execution language.

A **WorkPlan** is semantic planning state owned by the Module/Agent that created it. Executable portions are projected into ordinary MADRE work; Kernel does not own or interpret the semantic plan.

An **Operation** is bounded callable behavior intentionally exported and implemented by a Module. An Operation may expose immutable **EffectProfiles** describing security-relevant execution shapes of that same bounded effect. Operations are how Module/system/external effects are exposed to intelligent actors without granting a generic shell or unrestricted Internet environment.

An **Artifact** is Module-owned material. A **ContextBundle** is an artifact-like bounded collection of material prepared for a concrete use/boundary.

## Product invariants

### 1. Module semantics remain outside Kernel

Modules decide what data matters, what requests mean, what Agents/Skills/Workflows are useful, how results are interpreted, and what domain mutation occurs.

Generated output is ordinary Module-owned material once delivered. A Module may use it as later input, evidence, memory/state, WorkPlan material, a persisted artifact or the basis for a later bounded Operation. Kernel does not impose a universal semantic status on generated content.

### 2. Interaction belongs to the interaction-owning Module

MADRE supports multiple interaction surfaces: Module-owned UI, CLI, provider/application surfaces, automation, or the shipped CORE Module's general UI/UX.

When CORE owns the interaction surface, its interaction Agent may implement a low-latency fast-response strategy and continue/delegate/schedule additional reasoning as Module-owned behavior. MADRE-native Modules may deliberately delegate governed input to that CORE interaction surface, and Modules/adapters without their own UI may use CORE as the default interaction experience.

Kernel does not own a semantic "fast lane". It exposes execution primitives such as transient inference and durable work with latency, resource and scheduling properties; Agents/Modules decide how to compose them into user experience.

### 3. Durable work owns execution intent, never queued private material

Durable execution is:

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

All accepted/queued durable work is reference-only from Kernel's perspective. The Module retains actual prompt/context/material. Kernel persists only execution metadata plus a verifiable material handle/security binding, resolves material just-in-time for a concrete attempt, verifies it, uses it transiently and discards it.

Restart and retry recover execution intent and reacquire material from the Module.

### 4. Kernel treats payload bytes as transient opaque material

Kernel may persist public descriptors, execution/mechanism metadata, work/attempt lifecycle state, scheduling/resource evidence, material/output digests, security facts/decisions, failure/delivery evidence and opaque coordination/correlation values.

Kernel does not durably persist prompts, conversation/private/retrieved context, private documents, Agent state, semantic WorkPlans, model answers/generated content, application history or domain records.

Kernel does not derive semantic policy, intent, truth or routing meaning from prompt/output bytes.

Modules, including the shipped CORE Module, may persist their own private material and results according to their semantics. Shipping those Modules with MADRE does not transfer that persistence responsibility into Kernel.

### 5. Modules request execution properties; Kernel selects physical mechanisms

Modules normally describe what execution they need rather than enumerate installed provider/model inventory.

Execution requirements/preferences may include:

```text
modality / specialization
latency class
reasoning effort / quality
cost policy
locality/privacy
resource/availability constraints
preferred provider/model/mechanism
fallback permission/order
```

Kernel deterministically matches these against installed mechanisms and current resource state.

Local inference is the primary product focus, while admissible remote/provider mechanisms remain first-class options.

One provider may expose several distinct mechanisms: API, account-authenticated CLI/session, SDK, MCP path, gateway, local bridge or user-installed adapter. Provider-specific login, credentials, protocol schemas and software mechanics remain adapter concerns.

### 6. Security is typed, scoped and relation-local algebra

SecurityObjects bind exact immutable scopes/projections and revisions, rather than
one global tuple per software object. Sensitivity, Privacy, Integrity, Risk and
Autonomy are five nominal ordered facet types with integer wire ranks 1..5. They are
not interchangeable and there is no global score or injected policy meaning.

Sensitivity covers all sensitive state reachable/exposable through a scope, including
Module/Agent surfaces. Privacy is PUBLIC=1, UNKNOWN=2, LOCAL_PRIVATE=3,
MODULE_PRIVATE=4, SECRET=5. Privacy is an explicit observer-boundary fact: remote does
not imply UNKNOWN and local does not imply private. Integrity is warranted security-significant
causal/effect-realization responsibility under the exact bounded contract, not truth,
intelligence, reliability or general trust. Ordinary generated material needs no
Integrity claim. Risk and residual machine-control Autonomy stay paired on the same
immutable Operation EffectProfile.

The focused Security Algebra composes three feasible immutable normal forms:

- disclosure: max source Sensitivity <= min actual observer Privacy, or one exact live DirectUserAction;
- control: min(profile Risk, profile Autonomy) <= min actual non-user controller Integrity (5 if none);
- execution: profile Risk <= min actual executor Integrity, with nonempty executors;
- derivation: immutable source/procedure/result bindings and scope-local propagation.

Direct action changes neither Sensitivity nor Privacy and is unavailable to scheduled
or background work. Ordinary derivation preserves the
Sensitivity lower bound; selection uses retained members; semantic transforms have
an explicit Module-owned procedure; validation binds actual validators. SecurityEvidence
is immutable audit/provenance state, never active numeric state. Work
retry/scheduler/idempotency state is not a security operand.
Kernel checks mechanical bindings and declared closure under the honest-contract
model without inferring private semantics. ACLs, roles, tokens, provider reputation,
LLM truthfulness and hostile native-code sandboxing are outside this algebra.

### 7. CORE is a replaceable Module with a required capability/security contract

MADRE ships with a default **CORE-capable Module**. The user may configure another compatible Module as CORE.

CORE is not a second Kernel and receives no security bypass, resource exemption or private semantic Kernel API. It uses the same public SDK/interoperability boundary as other Modules.

A CORE-capable Module must provide the generic functionality required by the installation's default/general interaction and fallback role, and must carry sufficiently strong Privacy and Integrity characteristics for the sensitive material and control paths it is expected to handle under the same Security Algebra as every other Module.

CORE-owned data may itself have the highest Sensitivity values. Participant assurance and material sensitivity are independent dimensions.

Typical shipped CORE behavior may include:

```text
default/general UI and interaction Agent
fallback intelligence for Agentless or UI-less Modules
generic delegation/escalation
module-independent intelligent assistance such as configuration/install support
system/user profile and interaction continuity owned by CORE
```

Cross-domain material still moves through ordinary algebraic boundaries. CORE minimizes/anonymizes/transforms material when necessary before sending it toward a lower-Privacy or higher-consequence crossing.

### 8. Public interoperability does not make Kernel an Agent framework

Modules may publish Agent, Skill, Workflow and Operation descriptors. Intelligent participants choose semantic targets; Kernel provides discovery facts, deterministic boundary evaluation, routing and execution evidence.

MADRE-provided AI surfaces do not grant unrestricted shell or Internet access. External/system effects remain specific bounded Operations or mechanisms.

Unknown external Operation effects must not be blindly retried when dispatch outcome is uncertain.

### 9. The SDK is a first-class architectural boundary

The SDK materializes the smallest stable object-oriented contract needed to build Modules without depending on Kernel internals.

It should provide typed, modular interfaces/value objects for the public MADRE concepts and reusable helpers for common patterns while keeping Module-private semantics optional and extensible.

The SDK must be flexible enough that Agents, Skills and Workflows from fast-changing external ecosystems or informal/natural-language definitions can be translated into MADRE-compatible Modules/contracts without requiring those ecosystems to adopt MADRE internally.

Current Python implementation choices are not product architecture. Public contracts should remain language-neutral and suitable for a future more strongly typed implementation.

### 10. Architecture is minimal but concrete

MADRE defines the smallest coherent and human-readable object model required by its real boundaries, execution lifecycle and SDK.

Do not create universal classes merely because a concept can be named. Equally, do not leave shared cross-boundary concepts undefined and then let implementation convention define them accidentally.

### 11. Greenfield development may replace superseded implementation structure

MADRE has no production compatibility obligation during current development. Generated local state, schemas or implementation structures that no longer realize this contract may be replaced rather than wrapped.

## Responsibility test

Place a concept by asking what decision requires it:

- understand meaning, plan, remember, present, learn, choose semantic behavior or mutate a domain → **Module**;
- provide reusable typed public integration → **SDK/interoperability**;
- compose exact security relations, execute transient inference, admit/schedule durable work, allocate scarce resources, select a physical mechanism, recover execution, route an explicit target, deliver results or record evidence → **Kernel**;
- load/talk to a model/provider or expose mechanism-native optimization → **Capability adapter/external mechanism software**.
