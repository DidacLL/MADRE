# MADRE — Product and Owner Intent Corpus

This document preserves the product meaning of MADRE: why it exists, what its concepts mean, and how they fit together. It is not an implementation plan. Examples explain intent; they do not create mandatory architecture.

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

MADRE can help software minimise information, understand information journeys, expose consequences, choose safer execution, request Owner involvement, and recover from mistakes. Those facilities serve the Owner. They do not create an authority above the Owner.

The design target is therefore optimistic for developers and builders: clear semantic contracts, useful defaults, inspectability and diagnostics rather than a security prison around the machine owner.

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

An application may contain ordinary code, state, UI, deterministic Operations, Agents, Skills, Material, memory, delayed reasoning, external services, local models, remote models, or an existing opaque agentic environment.

A model is an inference mechanism. It is not the semantic identity of the application.

Model agnosticism therefore means that the application's meaning survives changes in the physical intelligence used to satisfy a reasoning need. Different ReasoningRequests may use different mechanisms, and a Module may also own internal intelligence that MADRE does not manage.

## Modules

A **Module is an independently installable MADRE application around a coherent domain or application responsibility**.

The domain is the reason the Module exists.

Examples include candidature/job-search management, the Owner's role-playing application, or a professional work environment. CORE is also an ordinary Module assigned a special installation role.

A Module owns the semantics of its application. Where relevant that includes:

- domain state and persistence;
- domain types and Material;
- bounded Operations;
- optional Agents;
- optional Skills;
- optional UI;
- integrations;
- knowledge or behaviour whose meaning belongs to that application; and
- arbitrary internal implementation, including opaque existing software.

Not every Module has an Agent. Not every Module has a UI. A Module may be small, or it may be a thin binding around a much larger existing system.

MADRE standardises the public surfaces needed for composition, not the internal architecture behind them.

## Not everything useful is a Module

Modules correspond to applications and domains. Reusable behaviour is different.

Research, calendar access, file access, search, generation, embeddings or similar facilities do not become Modules merely because they are useful. They can be SDK facilities, Operations supplied by a developer, operating-system facilities, external APIs, or implementation details of a Module.

The architecture should not manufacture application domains out of every noun or capability.

## Operations

An **Operation is bounded executable behaviour**.

A Module can expose Operations that belong to its application. The SDK can also provide reusable Operation facilities where they solve common MADRE development problems clearly and cheaply.

An Operation can be deterministic. It does not imply inference and does not require an Agent inside the providing Module.

An Operation's public semantic contract carries the SPIRA facts that belong specifically to that bounded behavior: the Privacy of accepted Material boundaries, the maximum Sensitivity promised for produced Material, and zero or more immutable `EffectProfile`s when the Operation has consequential execution variants. An `EffectProfile` belongs to its Operation and binds one exact Risk/Autonomy shape; it is not an authorization state.

When Agent A invokes an Operation exposed by Module B, the invocation does not automatically transfer Agent A's semantic continuation to a Module-B Agent. Module B may be agentless. The Operation executes its bounded behaviour and returns its result; Agent A continues its own semantic work.

Explicit Agent-to-Agent delegation is different: a delegated semantic objective is actually handed to another Agent.

## Agents

An **Agent is a semantic reasoning actor**.

It is not a model, prompt, inference endpoint, worker or generic name for any automation.

Agents are provided by Modules. The providing Module owns the Agent's definition and persistent behaviour, memory or learning that belongs to it.

An Agent may use:

- relevant domain context and Material;
- Skills;
- its reusable Workflows;
- Operations from its own Module;
- Operations exposed by other Modules or the SDK;
- ReasoningRequests when inference is genuinely needed; and
- explicit delegation to another Agent.

An Agent also has an Integrity value because it can be an actual causal participant in security-significant semantic work. That Integrity is an assurance fact about the actor, not a privilege or permission level.

The Module providing an Agent and the Module owning the current task domain do not have to be the same.

For example, a default research Agent provided by CORE can work with selected context from a Roleplay Module. Roleplay remains the application domain; CORE remains the Agent provider; research remains reusable behaviour.

## Skills, Workflows and WorkPlans

A **Skill** is reusable know-how available to an Agent.

A **Workflow** is reusable semantic behaviour in an Agent's repertoire. It is not a Kernel schedule, Runtime queue or universal DAG engine.

A **WorkPlan** is objective-specific semantic planning and may coordinate more than one Agent or reusable Workflow when the objective requires it.

These concepts exist to make useful behaviour reusable without turning each behaviour into another application domain or another central subsystem.

## Material and context

MADRE treats information as meaningful information, not merely anonymous payload.

Concrete Material carries its Sensitivity. Material may be selected, derived, transformed or minimised before it crosses a semantic boundary. Domain types are allowed to express domain complexity; MADRE does not require one universal object ontology for all Module state.

A context crossing a boundary carries only what is relevant to that interaction together with the semantic facts needed for correct composition.

A transformed or minimised representation is new Material with its own Sensitivity. It does not retroactively relabel its source.

For example, professional software may need only `available 16:00–17:30`, not the private events from which that availability was derived. No Calendar Module is implied by this example; the source could be an SDK Operation, OS facility, provider API or application-specific implementation.

## Security Algebra — SPIRA

MADRE's Security Algebra is **SPIRA**:

- Sensitivity;
- Privacy;
- Integrity;
- Risk;
- Autonomy.

SPIRA is not a generic security label or a central authorization service. Its values live on the semantic contracts where each value actually means something, and comparisons happen only when a concrete information/effect construction requires them.

This distinction is part of the product, not an implementation detail. Flattening SPIRA into a five-field context or reducing it to a generic `safe/unsafe` check destroys the information MADRE uses to compose software intelligently.

The detailed engineering authority for this model is `docs/architecture/security-algebra.md`.

### Sensitivity — concrete Material

Sensitivity describes the disclosure consequence of a concrete Material representation:

```text
0 SYSTEM_RESERVED
1 TRIVIAL
2 SHARED
3 PROFILING
4 SENSITIVE
5 SECRET
```

Concrete Material is the direct carrier. Actual participating Material combines by maximum:

```text
S_scope = max(S_i)
```

A genuine transformation may create new Material with a different Sensitivity. The source remains unchanged.

### Privacy — receiving Operation boundary

Privacy describes the effective confidentiality of the concrete receiving boundary:

```text
0 SYSTEM_RESERVED
1 PUBLIC
2 UNKNOWN
3 LOCAL
4 MODULE
5 ISOLATED
```

An Operation declares Privacy per accepted Material type. When a path includes several actual receiving boundaries, they combine by minimum:

```text
P_path = min(P_i)
```

`UNKNOWN` is a real ordinary value, not absence or a hidden default.

The information-reach comparison is local to the Material actually being disclosed and the path actually receiving it:

```text
max(S_disclosed) <= min(P_actual_path)
```

A receiver that was merely considered but did not receive the Material does not contaminate another route.

### Integrity — actual causal participants and realizers

Integrity describes the assurance of a subject that actually participates in security-significant causal control or effect realization:

```text
0 SYSTEM_RESERVED
1 NOT_DECLARED
2 DECLARED
3 TRUSTED
4 ACCEPTED
5 VALIDATED
```

Meaning:

- **NOT_DECLARED** — the relevant integrity cannot be traced or meaningfully claimed;
- **DECLARED** — it comes from an explicit manifested declaration, but that declaration is not independently traceable;
- **TRUSTED** — the origin or behaviour is reasonably trusted through established provenance, common use or other evidence even though it is not fully analysable;
- **ACCEPTED** — stronger assurance exists because of effective boundaries, known origin, observable behaviour, or direct Owner acceptance;
- **VALIDATED** — the relevant integrity property can be deterministically verified again when needed.

Agents are direct named Integrity carriers in the semantic SDK because they can be causal actors. Other causal participants or effect realizers contribute Integrity only when they really participate in the concrete construction.

Actual participating Integrity values combine by minimum:

```text
I_scope = min(I_i)
```

These values describe assurance, not privilege. Being local, first-party, shipped with MADRE or assigned CORE does not automatically imply high Integrity.

### Risk and Autonomy — selected EffectProfile

Risk and Autonomy do not float independently through a global context. They are paired on an immutable **EffectProfile owned by one Operation**.

Risk:

```text
0 SYSTEM_RESERVED
1 READ
2 WRITE
3 DELETE
4 EXECUTE
5 POTENTIALLY_HARMFUL
```

Autonomy:

```text
0 SYSTEM_RESERVED
1 LIVE_INTERACTION
2 ASK_ALWAYS
3 ASK_ONCE
4 ACKNOWLEDGE
5 AUTONOMOUS
```

An Operation can declare zero or more EffectProfiles. A consequential invocation selects one exact profile belonging to that Operation. A non-consequential Operation does not get dummy Risk/Autonomy values.

This allows the same Operation to have distinct real execution shapes—for example a direct Owner-controlled deletion and an autonomous cleanup—without inventing `approved=true`, ACLs or another permission system.

### Three separate comparisons

SPIRA does not produce one global score. The established comparisons answer three different questions.

**Disclosure:** can this actual Material be exposed through this actual receiving path?

```text
max(S_disclosed) <= min(P_actual_path)
```

**Control:** is the machine causal chain strong enough for the consequence and residual machine control of the selected EffectProfile?

```text
D_C(e) = min(Risk(e), Autonomy(e))
D_C(e) <= min(Integrity_actual_non_user_controllers)
```

When there is no non-user controller, rank 5 is the algebraic neutral element; this does not invent a phantom validated controller.

**Effect execution:** is the actual realization path strong enough to faithfully realize the selected consequence?

```text
Risk(e) <= min(Integrity_actual_effect_realizers)
```

Direct Owner interaction can reduce machine-control demand through the selected profile's Autonomy, but it does not make an unreliable destructive/executable realization safe.

Only actual participants count. A stronger later participant cannot wash a weaker participant that still causally determines the effect.

### OperationCall binds facts; it is not the policy engine

A concrete Operation invocation binds:

```text
Operation contract
+ actual input Material
+ one exact declared EffectProfile when consequential
```

The call verifies that the input type is accepted and the selected profile really belongs to the Operation. Produced Material must stay within the Operation's declared output type and maximum Sensitivity.

Cross-dimensional SPIRA comparison belongs to the actual semantic composition point, not hidden inside every value class or Operation constructor.

### ReasoningRequest carries only the reasoning constituents

A `ReasoningRequest` represents semantic inference intent before physical Kernel Work exists.

For the actual reasoning construction it accumulates:

- Sensitivity from the input Material and any additional Material actually included;
- Privacy from the accepted Operation boundary and any additional receiving boundaries actually included;
- Integrity from the acting Agent and any additional causal participants actually included.

Same-dimension composition remains `max(S)`, `min(P)`, `min(I)`.

Risk and Autonomy do **not** automatically propagate into the ReasoningRequest merely because the surrounding Operation has an EffectProfile. Inference does not itself realize that external effect. If reasoning output later actually controls a consequential Operation, the later effect composition includes the relevant derived Material/causal participants and the selected EffectProfile at that point.

### Intrinsic composition, not an Agent-owned Compound

There is no Agent-owned or Runtime-owned mutable `Compound` manager.

The compound is simply the algebraic structure inherent in the actual constituents that are composing at that moment. The Agent encounters the resulting compatible/incompatible structure and reacts to it.

It may choose another Operation or destination, derive/minimise different Material, select another genuinely available EffectProfile, ask the Owner where that changes the real interaction shape, delegate, or stop the path. It does not rewrite values to make the same construction pass.

SPIRA itself never secretly invokes inference. If semantic reasoning is needed to establish a new application fact, an Agent performs ordinary reasoning and the resulting semantic object then composes normally.

## ReasoningRequest and the semantic-to-physical bridge

A **ReasoningRequest** represents a semantic need for inference. It remains on the semantic side of MADRE.

Agents do not manually construct Kernel Work.

The public SDK defines a bounded Runtime function/Operation that receives:

```text
ReasoningRequest
+
execution preferences/declarations
```

and translates that semantic reasoning need into the physical Work requirements understood by the Kernel.

The exact preference object and implementation details are intentionally not fixed here. It may express things such as desired effort, timing or other technical preferences without exposing Kernel mechanics to Agent code.

This conversion is executed by Runtime, but—like other executable MADRE behaviour—the implementation belongs to a Module. The shipped default CORE Module provides the ordinary implementation needed for a basic MADRE installation.

The Runtime keeps an installation-level executor/implementation selection for this required semantic function. The Owner may replace, wrap or extend that selection. For example, an Owner could insert a logger/learning experiment that observes the reasoning-to-Work journey and then delegates to the shipped default implementation.

The configured implementation sees the semantic reasoning facts necessary to make semantic choices before crossing the boundary. It must translate those choices into physical Kernel requirements rather than sending SPIRA, Material or Agent semantics into Kernel Work.

This extension seam does not own the ReasoningRequest, SPIRA or Kernel semantics. It is a bounded translation/interception point.

## Delayed Reasoning Effort

DRE is a central product thesis, not background-job decoration.

A user interaction need not force every useful thought into one blocking response. An Agent may answer what is known now and create bounded reasoning that can happen later: research, verification, critique, synthesis, waiting, alternative reasoning, stronger inference, or use of idle local compute.

Latency can therefore substitute for some amount of instantaneous model power.

The semantic reason for delayed work remains above the Kernel. The Kernel only understands the physical scheduling and execution facts needed to carry out physical Work later.

This creates a deliberate persistence split:

Semantic side persists what gives the work meaning: relevant context, semantic request, origin/correlation, continuation and interpretation.

Kernel persists only the technical lifecycle: opaque Work identity, technical requirements, eligibility/scheduling, engine attempts, resources, retry/cancel state and terminal technical outcome.

## Runtime

MADRE Runtime is the installed semantic environment around the public SDK and Kernel.

Its responsibilities include:

- Module installation/configuration and live availability;
- CORE role assignment;
- Module discovery and composition;
- lifecycle/activation and addressing;
- cross-Module Operation invocation and Agent delegation;
- deterministic SPIRA composition/comparison at Runtime-coordinated semantic boundaries using the facts owned by the participating contracts;
- execution of installation-required semantic functions such as the configured reasoning-to-physical Operation;
- semantic ReasoningRequest persistence/correlation and continuation support;
- correlation of Kernel Work with semantic requests;
- inspection, diagnostics, backup/recovery and Owner-facing configuration where appropriate.

Runtime coordination does not make Runtime the owner of Module domains, Agent semantics or SPIRA values.

## CORE

CORE is an ordinary Module assigned the CORE role.

It provides useful defaults for an ordinary installation: general Owner interaction, default/meta behaviour, default Agents and the shipped implementation of required Module-owned Runtime Operations such as the ReasoningRequest-to-Work translation.

CORE does not own other Modules or their Agents. CORE is not Runtime, Kernel or a special SPIRA authority.

Because the implementation of these required Runtime Operations is Module-owned and selected through Runtime configuration, the Owner can replace or decorate them without redefining MADRE.

## Kernel

The Kernel owns physical inference Work only.

It handles durable Work, scheduling/eligibility, physical engine matching, scarce-resource management, worker supervision, retry/cancellation and terminal technical results.

The Kernel must remain blind to Module, Agent, Operation semantics, Material, ReasoningRequest, EffectProfile, SPIRA, CORE, Workflow and semantic continuation.

A Module may contain its own private intelligence and never use the shared Kernel for that internal work. The Kernel is shared MADRE inference infrastructure, not a universal interceptor of every AI computation.

### Kernel extensibility and experimentation

The Kernel is deliberately not built against the semantic SDK, but the same MADRE philosophy applies at the physical layer: **solid defaults, modular internals, replaceable behaviour, no unnecessary closed doors**.

Physical control-flow responsibilities should have explicit enough boundaries that an Owner can replace or interpose experiments without rewriting the entire Kernel. A future experiment might, for example, add learning-assisted physical routing that observes factual Kernel state and uses inference internally before delegating to or replacing the normal routing decision.

Such an experiment remains physical Kernel behaviour. It must not import semantic MADRE concepts into the Kernel merely because it uses inference internally.

The architecture therefore distinguishes two things:

- a hard semantic/physical boundary that must remain hard; and
- replaceable implementation journeys on either side of that boundary.

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

MADRE's modularity is not a demand to turn every concern into another Module.

It means each real responsibility has a clear boundary and can be replaced where useful without acquiring unrelated meaning.

- Modules are replaceable applications/domains.
- Agents, Operations, Skills and Workflows compose without flattening domains.
- required Runtime semantic functions are bounded and their Module-owned implementations are selectable/replaceable;
- the reasoning-to-physical bridge can be wrapped or replaced;
- the physical Kernel remains independently replaceable and internally extensible without importing semantic SDK concepts;
- engine/worker implementations are replaceable behind the Kernel's physical contracts.

The Owner may experiment at any layer.

The design preference is not maximum pluggability machinery. It is **small explicit seams where a real journey may reasonably need to change**.

## Development scope

MADRE is a one-Owner research/product project developed heavily with AI assistance.

The engineering target is:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Do not add enterprise decomposition, compatibility machinery without users, plugin frameworks for hypothetical ecosystems, universal policy systems or abstractions copied from conventional AI platforms merely because they are familiar.

New structure earns its place because an actual MADRE responsibility requires it.

## Recovery point

MADRE's core bet is that much current dependence on frontier-cloud AI is architectural rather than inevitable.

A domain-aware application can retain its own knowledge and state. Deterministic Operations can perform ordinary software work. Agents can compose bounded reasoning. DRE can spend time instead of only model power. ReasoningRequests can be translated through a replaceable Runtime seam into physical Work. The Kernel can execute that Work without knowing semantic MADRE. SPIRA preserves concrete information reach, causal control and effect realization through the contracts that actually own those values rather than through a central security authority. Strong external inference remains available when useful.

The result is intended to let owner-controlled, locally installed AI-native software do far more useful work than an isolated local model suggests, while preserving the freedom to use frontier providers selectively.

The Owner keeps the software environment. The models and providers become resources inside it.