# MADRE — Product and Owner Intent Corpus

This document preserves the product meaning of MADRE: why it exists, what its concepts mean, and how those concepts fit together. It is not an implementation plan. Examples explain intent; they do not create mandatory architecture.

Current Owner instructions override this document when they are more specific. Historical code, tests, issues and familiar AI-platform patterns are evidence only.

## North Star

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

Its central technical thesis is that application capability does not have to equal the capability of one synchronous frontier-model call. Many ordinary user tasks can be solved by combining:

- persistent domain knowledge;
- deterministic bounded Operations;
- reusable Agents and Skills;
- accumulated results and learning;
- decomposition into smaller reasoning needs;
- Delayed Reasoning Effort (DRE);
- local/open inference where sufficient; and
- selective stronger external inference where it is actually useful or explicitly desired.

MADRE therefore asks a different question from a provider-owned assistant harness. Instead of asking which single model can solve the whole problem now, MADRE asks what software, knowledge, Operations, reasoning and time are actually needed to obtain the result.

This is why DRE is in the product name. Provider independence, privacy and data ownership are important consequences of keeping the application, its domain state and much of its reasoning inside the Owner's environment rather than continuously exporting whole contexts to a third-party harness.

MADRE does not seek independence by banning providers. External intelligence remains a useful resource. The difference is that the Owner's environment uses providers; the provider does not have to be the environment.

## Owner sovereignty

MADRE has an Owner. The Owner is not an adversary.

The Owner may inspect, edit, replace, remove or bypass any Module, Agent state, Material store, CORE assignment, inference configuration, Runtime state, Kernel data, installed artifact, generated software or source file they own.

MADRE can help software minimise information, understand information journeys, expose consequences, choose better execution, request Owner involvement and recover from mistakes. Those facilities serve the Owner. They do not create an authority above the Owner.

The design target is therefore optimistic for developers and builders: clear semantic contracts, useful defaults, inspectability and diagnostics rather than security machinery directed against the machine owner.

## Product surface and learning curve

The ordinary user wants useful software, not an AI infrastructure platform.

The intended progression is:

```text
use
→ inspect
→ configure
→ understand/develop
→ modify
→ replace
→ experiment
```

The depth is optional. The simple surface is mandatory.

The Owner should not need to understand model hosting, workers, VRAM, scheduling, IPC or provider SDKs to use a Module. Those mechanisms remain inspectable where useful, but they should not become ordinary product ceremony.

## AI-native software

MADRE is for AI-native applications, not merely model calls.

An application may contain ordinary code, state, UI, deterministic Operations, Agents, Skills, Material, memory, delayed reasoning, external services, local models, remote models or an existing opaque agentic environment.

A model is an inference mechanism. It is not the semantic identity of the application.

Model agnosticism means that application meaning survives changes in the physical intelligence used to satisfy a reasoning need. Different ReasoningRequests may use different mechanisms, and a Module may also own internal intelligence that MADRE does not manage.

## Modules

A **Module is an independently installable MADRE application around a coherent domain or application responsibility**.

The domain is the reason the Module exists.

Examples include candidature/job-search management, the Owner's role-playing application or a professional work environment. CORE is also an ordinary Module assigned a special installation role.

A Module owns the semantics of its application. Where relevant that includes:

- domain state and persistence;
- domain types and Material;
- domain-specific Operations;
- optional Agents;
- optional Skills;
- optional UI;
- integrations;
- bounded semantic strategies that require domain knowledge; and
- arbitrary internal implementation, including opaque existing software.

Not every Module has an Agent. Not every Module has a UI. A Module may be small, or it may be a thin binding around a much larger existing system.

MADRE standardises the public surfaces needed for composition, not the internal architecture behind them.

## Not everything useful is a Module

Modules correspond to applications and domains. Reusable behaviour is different.

Research, calendar access, file access, search, generation, embeddings or similar facilities do not become Modules merely because they are useful. They can be SDK facilities, Operations supplied by a developer, operating-system facilities, external APIs or implementation details of a Module.

The important question is what the application actually needs to do, not what architectural category MADRE can invent for a useful noun.

## Operations

An **Operation is bounded executable behaviour**.

A Module can expose Operations belonging to its domain. The SDK can also provide generally useful Operations where they solve common MADRE development needs.

An Operation can be deterministic. It does not imply inference and does not require an Agent inside the Module that provides it.

The semantic actor and the action remain different concepts:

```text
Agent     → carries semantic agency / continuation
Operation → performs one bounded action
```

An Operation execution occurs in an acting Agent's semantic continuation. If Agent A invokes an Operation exposed by Module B, Agent A remains the actor unless there is an explicit Agent-to-Agent delegation. Module B may be completely agentless.

This is important because cross-Module reuse should not create duplicate Agents merely to call bounded behaviour.

An Operation also carries the semantic contract facts that belong to that behaviour. In SPIRA terms, that can include:

- receiving Privacy for Material it accepts;
- maximum Sensitivity promised for Material it produces; and
- Risk for the concrete Operation/effect.

Autonomy does **not** belong to the Operation. It belongs to the actual acting Agent continuation.

## Agents

An **Agent is a semantic reasoning actor**.

It is not a model, prompt, inference endpoint, Operation, worker or generic name for automation.

Agents are provided by Modules. The providing Module owns the Agent's definition and persistent behaviour, memory or learning that belongs to it.

An Agent may:

- work with domain context and Material;
- use Skills;
- use reusable Workflows;
- invoke Operations from its own Module;
- invoke Operations exposed by other Modules or the SDK;
- create ReasoningRequests when reasoning is genuinely needed; and
- explicitly delegate semantic continuation to another Agent.

An Agent contributes Integrity to semantic work in which it actually participates. Its current continuation also contributes the current Autonomy state.

The Module providing an Agent and the Module owning the current task domain do not have to be the same.

For example, a default research Agent provided by CORE can work with selected context from a Roleplay Module. Roleplay remains the application domain; CORE remains the Agent provider; research remains reusable behaviour.

## Skills, Workflows and WorkPlans

A **Skill** is reusable know-how available to an Agent.

A **Workflow** is reusable semantic behaviour in an Agent's repertoire. It is not a Kernel schedule, Runtime queue or universal DAG engine.

A **WorkPlan** is objective-specific semantic planning and may coordinate more than one Agent or reusable Workflow when the objective requires it.

These concepts make behaviour reusable without turning each behaviour into another application domain or central subsystem.

## Material and context

MADRE software works with meaningful information.

Material is not intended to become a universal object ontology imposed on every application. Domain types should carry the complexity that belongs to their domain.

Concrete Material/context contributes Sensitivity while it participates in semantic composition.

Material can be selected, transformed, minimised, derived, passed to another Agent or incorporated into a ReasoningRequest.

A transformation creates a new representation. It does not retroactively mutate the semantic properties of its source.

For example, professional software may need only `available 16:00–17:30`, not the private events from which that availability was derived. No Calendar Module is implied; the source could be an SDK Operation, OS facility, provider API or application-specific implementation.

## Security Algebra — SPIRA

MADRE's Security Algebra is **SPIRA**:

- Sensitivity;
- Privacy;
- Integrity;
- Risk;
- Autonomy.

It is not a conventional permission system, central policy evaluator, authorization service or authority above the Owner.

It is the **intrinsic compositional structure of the actual semantic constituents participating in the current construction**.

The detailed engineering authority is `docs/architecture/security-algebra.md`. The essential product semantics are preserved here because SPIRA is one of MADRE's recurring drift points.

### Sensitivity

```text
0 SYSTEM_RESERVED
1 TRIVIAL
2 SHARED
3 PROFILING
4 SENSITIVE
5 SECRET
```

Sensitivity describes actual information. Material/context representations contribute it.

Higher values represent information requiring greater protection from exposure.

Sensitivity can be re-evaluated at Module boundaries when a Module has additional domain facts or applies bounded strategies relevant to its own domain. A transformation or minimisation produces a new representation; it does not relabel the source.

Actual participating Sensitivities accumulate by maximum:

```text
S_scope = max(S_i)
```

### Privacy

```text
0 SYSTEM_RESERVED
1 PUBLIC
2 UNKNOWN
3 LOCAL
4 MODULE
5 ISOLATED
```

Privacy describes the containment/exposure offered by the actual receiving boundary.

The information contributes Sensitivity; the actual receiver contributes Privacy. They are deliberately different facets.

An Operation's accepted Material boundary is one natural public place to declare the Privacy of that receiver.

Actual receiving boundaries accumulate by minimum:

```text
P_path = min(P_i)
```

`UNKNOWN` is an ordinary value, not missing information.

### Integrity

```text
0 SYSTEM_RESERVED
1 NOT_DECLARED
2 DECLARED
3 TRUSTED
4 ACCEPTED
5 VALIDATED
```

Integrity describes assurance carried by actual semantic participants/provenance relevant to the current composition.

- **NOT_DECLARED** — the relevant integrity cannot be traced or meaningfully claimed;
- **DECLARED** — it comes from an explicit manifested declaration, but that declaration is not independently traceable;
- **TRUSTED** — the origin or behaviour is reasonably trusted through established provenance, common use or other evidence even though it is not fully analysable;
- **ACCEPTED** — stronger assurance exists because of effective boundaries, known origin, observable behaviour or direct Owner acceptance;
- **VALIDATED** — the relevant integrity property can be deterministically verified again when needed.

An actual Agent contributes Integrity when it participates. Other provenance-bearing subjects contribute only when they actually participate in the current causal construction.

Actual participating Integrity values accumulate by minimum:

```text
I_scope = min(I_i)
```

Integrity is assurance, not privilege. Being local, first-party, shipped with MADRE or assigned CORE does not automatically imply a high value.

### Risk

```text
0 SYSTEM_RESERVED
1 READ
2 WRITE
3 DELETE
4 EXECUTE
5 POTENTIALLY_HARMFUL
```

Risk belongs to the **concrete Operation/effect actually being selected**.

It is not an abstract property of an entire Module and it does not accumulate from unused Operations. A Module can expose Operations with very different Risks; only the one actually participating matters.

### Autonomy

```text
0 SYSTEM_RESERVED
1 LIVE_INTERACTION
2 ASK_ALWAYS
3 ASK_ONCE
4 ACKNOWLEDGE
5 AUTONOMOUS
```

Autonomy describes the **actual Agent continuation state**: how independently the Agent is proceeding relative to Owner interaction.

It is not a permission attached to an Operation and is not a permanent Module property.

The same Operation can therefore participate under different Autonomy values. Owner interaction can change the real continuation state without changing the Operation's Risk.

### How the compound exists

There is no mandatory `CompoundSecurity`, `EffectProfile`, `Authorization`, `PolicyDecision` or evaluator object.

The compound is a derived semantic property of the actual current constituents.

Depending on the current construction, those constituents can include:

- actual Material/context;
- the actual acting Agent;
- the actual receiving/executed Operation;
- actual provenance-bearing participants;
- the Agent's actual continuation state.

Things that merely could participate do not contaminate the compound. Unused Operations do not. Possible model outputs do not. Every installed capability does not. Unrelated branches do not globally reduce each other.

The Agent does not own or manage a mutable Compound. The Agent decides what to try; SPIRA follows from the actual semantic pieces that attempt composes.

### Where facets meet

SPIRA does not become one global score. Different facets meet only where the current semantic construction makes their relation relevant.

For actual information entering an actual receiving path:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)
```

For the actual Operation/effect under the actual acting Agent continuation and actual non-user causal participants:

```text
min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)
```

Where actual effect realizers have semantically relevant Integrity:

```text
R_actual_operation
    <= min(I_actual_effect_realizers)
```

These are composition relations, not an authorization API.

A compatible current construction can continue. An incompatible exact construction cannot continue as though it were compatible.

The semantic actor can change the actual construction—another Operation, another receiving path, new minimized Material, different Agent continuation, Owner interaction, delegation or stopping the path—and composition is then derived again.

MADRE does not silently weaken Sensitivity, pretend a boundary has greater Privacy, raise Integrity, lower Risk or alter Autonomy to obtain a desired answer.

## SPIRA across Module and Agent boundaries

When context crosses into another Module or Agent, the source side carries the actual context and the semantic facts already known about it.

The receiving Module can contribute its own domain knowledge and bounded strategies. This can produce a different derived sensitivity or new Material where the actual domain semantics justify it.

The target does not simply inherit one eternally global label, and it does not discard what the source already knew.

This is why SPIRA belongs to semantic MADRE rather than the physical Kernel.

## ReasoningRequest

A **ReasoningRequest** represents a semantic need for reasoning. It is created by an Agent.

It may involve:

- relevant context;
- Material;
- provenance;
- Owner instruction;
- SPIRA facts;
- the actual reasoning objective.

The ReasoningRequest remains semantic.

The responsible Agent/Module semantic process determines what reasoning is needed, what information is relevant, what inference choices are acceptable and what current composition is valid.

The corpus does **not** require a `ReasoningRequest` to become a generic five-field SPIRA carrier. Risk remains with the actual Operation; Autonomy remains with the actual Agent continuation; other facets remain with the actual semantic facts that contribute them.

The ReasoningRequest ends on the semantic side. It is not sent into the Kernel as a semantic object.

Only the selected physical invocation crosses the semantic/physical boundary.

## ReasoningRequest and the semantic-to-physical bridge

Agents do not manually construct Kernel Work.

The public SDK defines a bounded special Operation/function that receives:

```text
ReasoningRequest
+
execution preferences/declarations
```

and translates that semantic reasoning need into the physical Work requirements understood by the Kernel.

The exact preference object and implementation details are intentionally not fixed here. It may express things such as desired effort, timing or other technical preferences without exposing Kernel mechanics to Agent code.

This conversion is executed by Runtime, but—as with other executable MADRE behaviour—the implementation belongs to a Module. The shipped default CORE Module provides the ordinary implementation needed for a basic installation.

Runtime keeps the installation-level selection of the implementation. The Owner may replace, wrap or extend it; for example, an Owner could insert a logger/learning experiment that observes the reasoning-to-Work journey and delegates to the default implementation.

The implementation can inspect the semantic request and factual engine descriptions as needed, but it outputs physical constraints. Module, Agent, Material and SPIRA semantics do not cross into Kernel Work.

## Delayed Reasoning Effort

DRE is a central product thesis, not background-job decoration.

A user interaction need not force every useful thought into one blocking response. An Agent may answer what is known now and create bounded reasoning that can happen later: research, verification, critique, synthesis, waiting, alternative reasoning, stronger inference or use of idle local compute.

Latency can therefore substitute for some amount of instantaneous model power.

The semantic reason for delayed work remains above the Kernel. The Kernel understands only the physical scheduling and execution facts needed to carry out Work later.

This creates a deliberate persistence split:

- the semantic side preserves what gives the work meaning: relevant context, semantic request, origin/correlation, continuation and interpretation;
- Kernel preserves the technical lifecycle: opaque Work identity, physical requirements, eligibility/scheduling, attempts, resources, retry/cancel state and terminal technical outcome.

## Runtime

MADRE Runtime is the installed semantic environment around the public SDK and Kernel.

Its responsibilities include:

- Module installation/configuration and live availability;
- CORE role assignment;
- Module discovery;
- lifecycle/activation and addressing;
- cross-Module Operation routing and Agent delegation transport;
- execution of installation-required functions such as the configured reasoning-to-physical Operation;
- semantic ReasoningRequest persistence/correlation and continuation support;
- correlation of Kernel Work with semantic requests;
- inspection, diagnostics, backup/recovery and Owner-facing configuration where appropriate.

Runtime provides mechanics. It does **not** acquire Module domain meaning or become the semantic evaluator of SPIRA simply because it routes or executes calls.

The actual Agent/Module semantic context owns the meaning of the current work and reacts to the algebraic composition it encounters.

## CORE

CORE is an ordinary Module assigned the CORE role.

It provides useful defaults for an ordinary installation: general Owner interaction, default/meta behaviour, default Agents, reusable generic behaviour and the shipped implementation of required Module-owned Runtime Operations such as the ReasoningRequest-to-Work translation.

CORE does not own other Modules or their Agents. CORE is not Runtime, Kernel or a special SPIRA authority.

Because required Runtime Operations are Module-owned and selected through Runtime configuration, the Owner can replace or decorate them without redefining MADRE.

## Kernel

The Kernel owns physical inference Work only.

It handles durable Work, scheduling/eligibility, physical engine matching, scarce-resource management, worker supervision, retry/cancellation and terminal technical results.

The Kernel must remain blind to Module, Agent, Operation semantics, Material, ReasoningRequest, SPIRA, CORE, Workflow, WorkPlan and semantic continuation.

A Module may contain its own private intelligence and never use the shared Kernel for that internal work. The Kernel is shared MADRE inference infrastructure, not a universal interceptor of every AI computation.

Factual engine descriptors can be inspected above the boundary so semantic software can choose acceptable physical constraints. Kernel reports and executes physical facts; it does not convert them into Privacy, Integrity or other semantic values.

### Kernel extensibility and experimentation

The Kernel does not use the semantic SDK, but the same MADRE philosophy applies at the physical layer: solid defaults, modular internals, replaceable behaviour and no unnecessary closed doors.

Physical control-flow responsibilities should have explicit enough boundaries that the Owner can replace or interpose experiments without rewriting the whole Kernel. A future experiment could, for example, add learning-assisted physical routing that reasons over factual Kernel state.

Such an experiment remains physical Kernel behaviour. It must not import semantic MADRE concepts merely because it uses inference internally.

MADRE should be modifiable at every layer without flattening those layers into each other.

## SDK as a generation target

The SDK is a primary product because MADRE is intended not only to run software but to make creation of owner-native AI software cheap.

The public abstractions should be simple and explicit enough that a wide range of capable AI development systems can generate a Module or binding directly against them.

The intended future journey is:

```text
Owner need
  -> AI-assisted builder/development system
  -> public MADRE SDK + domain/application information
  -> ordinary Module implementation
  -> build/test/install locally
  -> normal installed software
```

The builder is not required at runtime. AI helps create the software; then the software exists.

A future BuilderModule or automated building product may be deferred. The architectural requirement exists now: independent/generated Modules must not need hidden first-party hooks, Runtime internals, Kernel internals or undocumented conventions.

## Modularity at every level

MADRE modularity does not mean turning every useful capability or extension point into another Module.

It means each real responsibility has a clear boundary and can be replaced where useful without acquiring unrelated meaning.

- Modules are replaceable applications/domains.
- Agents, Operations, Skills and Workflows compose without flattening domains.
- required Runtime semantic functions are bounded and their Module-owned implementations are selectable/replaceable;
- the reasoning-to-physical bridge can be wrapped or replaced;
- the physical Kernel remains independently replaceable and internally adaptable without importing semantic SDK concepts;
- engine/worker implementations are replaceable behind Kernel's physical contracts.

The Owner may experiment at any layer.

The design preference is not maximum pluggability machinery. It is **small explicit seams where a real journey may reasonably need to change**.

## Development scope

MADRE is a one-Owner research/product project developed heavily with AI assistance.

The engineering target is:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not add enterprise decomposition, compatibility machinery without users, plugin frameworks for hypothetical ecosystems, universal policy systems or abstractions copied from conventional AI platforms merely because they are familiar.

Do not simplify established concepts by erasing the concrete carrier, relation or boundary that gives them meaning.

New structure earns its place because an actual MADRE responsibility requires it.

## Recovery point

MADRE's core bet is that much current dependence on frontier-cloud AI is architectural rather than inevitable.

A domain-aware application can retain its own knowledge and state. Deterministic Operations can perform ordinary software work. Agents can compose bounded reasoning. DRE can spend time instead of only model power. ReasoningRequests can be translated through a replaceable Runtime seam into physical Work. The Kernel can execute that Work without knowing semantic MADRE. SPIRA preserves the actual information, receiving boundary, provenance, consequence and autonomy composition without becoming a central security authority. Strong external inference remains available when useful.

The result is intended to let owner-controlled, locally installed AI-native software do far more useful work than an isolated local model suggests while preserving the freedom to use frontier providers selectively.

The Owner keeps the software environment. The models and providers become resources inside it.