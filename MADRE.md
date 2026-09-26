# MADRE

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

This file is the **detailed repository-level product and semantic overview**. It intentionally preserves the reasoning connections among Modules, Agents, Operations, SPIRA, DRE, Runtime, CORE, Kernel and the SDK generation target.

Read `NORTH_STAR.md` first as the short mandatory recovery checkpoint before substantial MADRE work. Read `docs/product/lane-c-owner-decision.md` for the current accepted DRE/physical-inference correction and its rationale, `docs/product/owner-intent-corpus.md` for the deeper product reasoning, `docs/architecture/security-algebra.md` for the operational SPIRA model, `docs/architecture/mid-level-architecture.md` for the accepted whole-system design, and `docs/architecture/kernel.md` for the target physical Kernel architecture.

`NORTH_STAR.md` does **not** replace the detail in the richer authorities. Its purpose is to force recovery of the governing product answers before an agent derives architecture from code or convention.

Current Owner instructions override repository documentation when more specific. Historical code/tests/PRs/commits and familiar AI-platform patterns are evidence only.

## Product thesis

MADRE is built around the idea that useful application capability does not have to equal one synchronous frontier-model invocation.

Domain knowledge, deterministic bounded Operations, reusable Agents/Skills, accumulated state, decomposition and **Delayed Reasoning Effort** can make local/open intelligence sufficient for a large part of ordinary user work. Stronger external inference remains available selectively when it adds real value or the Owner wants it.

The application and reasoning environment stays with the Owner. Models/providers are resources used by that environment rather than the place where the environment must live.

## Owner sovereignty

The Owner owns the complete installation and is never MADRE's adversary.

MADRE may provide minimisation, diagnostics, recovery and useful defaults, and its semantic objects participate in the intrinsic SPIRA relations described below. Those mechanisms serve the Owner; they do not create an authority above the Owner.

The Owner may inspect, modify, replace or experiment with Modules, Agent state, Runtime behaviour, CORE assignment, inference configuration, Kernel state/implementation, inference bindings, generated software and source code.

## Modules, Agents and Operations

The public SDK is the construction surface for shipped, independent and eventually AI-generated MADRE software.

A **Module** is an independently installable application/domain semantic boundary. It owns its domain state, persistence, types, integrations, optional UI, optional Agents and domain-specific behaviour.

An **Agent** is a semantic reasoning actor.

An **Operation** is bounded executable behaviour.

The actor/action distinction is intentional. An agentless Module can expose Operations; another Agent can invoke them. Invoking another Module's Operation does not transfer semantic continuation to a target Agent unless an explicit Agent-to-Agent delegation occurs.

A **Skill** is reusable know-how. A **Workflow** is reusable Agent behaviour. A **WorkPlan** is objective-specific semantic planning that may coordinate multiple Agents/Workflows.

Reusable behaviour such as research, file access, search, calendar access or inference does not become a Module merely because it is useful.

## SPIRA

MADRE's Security Algebra is the intrinsic composition of the actual semantic facts participating now. It is not a permission/authorization service, policy engine or mutable Agent-owned security context.

The five facets and their direct meanings are:

```text
Material/context representation       -> Sensitivity
actual receiving/exposure boundary    -> Privacy
actual Agent/provenance participant   -> Integrity
actual selected Operation/effect      -> Risk
current acting Agent continuation     -> Autonomy
```

Values are:

```text
Sensitivity: SYSTEM_RESERVED, TRIVIAL, SHARED, PROFILING, SENSITIVE, SECRET
Privacy:    SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, ISOLATED
Integrity:  SYSTEM_RESERVED, NOT_DECLARED, DECLARED, TRUSTED, ACCEPTED, VALIDATED
Risk:       SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy:   SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Same-facet reductions are:

```text
Sensitivity = max(actual participating Sensitivities)
Privacy     = min(actual participating Privacies)
Integrity   = min(actual participating Integrities)
```

Risk is the Risk of the concrete Operation/effect actually selected. Autonomy is the state of the actual Agent continuation. They are deliberately separate.

There is **no mandatory `EffectProfile`** in the current architecture and no Agent-owned `Compound` object. Historical implementations that paired Risk/Autonomy in `EffectProfile` are evidence from an earlier design, not current authority.

Where the actual semantic construction makes the relations relevant:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)

min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)

R_actual_operation
    <= min(I_actual_effect_realizers)
```

These describe intrinsic compatibility of the current construction, not permission from a central authority. If the exact construction is incompatible, semantic software changes actual constituents and derives again.

The Agent decides what to try; the algebra follows from what actually participates.

Detailed authority: `docs/architecture/security-algebra.md`.

## ReasoningRequest and the physical inference boundary

A `ReasoningRequest` is a semantic need for reasoning created by an Agent. It can involve context, Material, provenance, Owner instruction, relevant SPIRA facts and the reasoning objective.

It is **not** required to become a generic SPIRA tuple. The facets remain on the actual semantic facts where they belong.

Agents do not construct Kernel Work directly, and `ReasoningRequest` does not cross into Kernel as a semantic object.

Semantic MADRE derives a small physical inference requirement from what it can actually know about the reasoning need, for example:

```text
prepared inference input
requested result characteristics
desired reasoning depth / effort
context characteristics
urgency
acceptable delay / deadline
required modality/context characteristics
hard physical restrictions derived from semantic composition
explicit Owner execution preferences where relevant
```

The exact SDK carrier and Runtime journey remain design work. The first version must stay small: a field belongs in the contract because an implemented DRE decision consumes it, not because a generic AI platform might want it.

The semantic side does not need to resolve an exact provider/model/configuration before Kernel. That was an intermediate corrective Lane C architecture which successfully removed Kernel-owned inference engines but also prevented Kernel DRE from doing meaningful capability-aware physical scheduling.

Runtime supplies installation mechanics—Module discovery/lifecycle/addressing/routing, persistence/correlation, configured execution and diagnostics. Runtime does **not** become the semantic owner/evaluator of SPIRA merely because it transports or executes calls.

## CORE

CORE is an ordinary Module assigned the CORE installation role.

It can provide ordinary Owner interaction, default/meta behaviour, default Agents and reusable generic behaviour.

CORE is not Runtime, Kernel, owner of other Modules or a special SPIRA authority.

Kernel inference-capability knowledge and physical DRE scheduling do not become CORE ownership merely because a previous architecture used a Module-owned reasoning-to-physical executor.

## Kernel

Kernel is MADRE's shared durable physical inference control plane.

It owns the common physical concepts needed to make DRE useful across independently installed applications:

```text
PhysicalInferenceWork
InferenceCapability
configured/declared capability facts
current capability state
provenance-preserving physical observations/evidence
DRE inference-aware scheduling / physical strategy
physical attempts, results and recovery
```

Kernel owns Work identity/lifecycle, eligibility/urgency, deadlines, bounded physical concurrency, cancellation, physically justified retry/escalation, durable result retention and restart recovery.

### InferenceCapability

An `InferenceCapability` represents a physical path through which MADRE can obtain intelligence. It is not a provider/model ontology and it does not make the external inference runtime MADRE-owned.

The same underlying model exposed with materially different configurations can be several capabilities. Different providers/runtimes can also be interchangeable for a given physical requirement.

Capability knowledge preserves provenance rather than flattening every fact into one mutable truth:

```text
CONFIGURED / DECLARED
    locality / external boundary
    modalities
    context characteristics
    declared inference/reasoning features
    cost characteristics where meaningful
    Owner configuration/preferences
    execution binding

CURRENT
    reachability / availability
    transient degradation or limits
    observable pressure where meaningful

HISTORICAL / OBSERVED
    latency
    throughput
    failures / error rates
    availability history
    malformed/incomplete-result rates
    observed costs
    benchmark/evaluation evidence with provenance where supplied
```

Only properties with an actual DRE consumer need first-class representation in the first version.

### DRE and physical observations

Kernel DRE is the inference-aware physical scheduler. It can decide when to run, which admissible capability to use, how much physical effort to spend, whether to defer, whether a richer physical inference strategy is worthwhile, whether physically justified retry/escalation is appropriate and whether a checkpointed physical strategy should resume.

Kernel may accumulate factual evidence such as latency, availability, throughput, failure/error rates and observed cost and use that evidence in future DRE decisions. Explicit benchmark/evaluation evidence may also be retained with provenance when a DRE strategy understands it.

Kernel does not become an application semantic evaluator. A physically valid inference result is not retried merely because Kernel believes the answer is weak or does not solve the user's actual task. Semantic dissatisfaction belongs to the consuming Agent/Module.

A useful distinction is:

```text
executor retry
    transient physical/executor failure

DRE retry / escalation
    physical inference result is unusable/incomplete
    or an explicitly physical strategy condition requires continuation

semantic retry
    physically valid result does not satisfy the application need
    -> Agent/Module logic
```

### Semantic blindness remains real

Kernel must remain blind to Module, MADRE Agent, Operation, Material meaning, ReasoningRequest semantics, SPIRA as semantic objects/policy, CORE semantics, MADRE Skill/Workflow/WorkPlan semantics and semantic continuation/persistence.

It also does not own model loading/warmness, provider/runtime internal process lifecycle, generic engine RAM/VRAM accounting or external inference-engine implementation.

A Module may privately use another AI/inference environment without using the shared Kernel. Kernel is not a universal interceptor.

### Physical strategy and binding openness

A DRE strategy may legitimately invoke several inference capabilities, perform physical/structural validation, fan out, compare or synthesize physical outputs. That is physical inference orchestration and does not automatically become a MADRE semantic Workflow.

Physical workflow machinery must not silently acquire semantic/application effects such as modifying Module state, sending application email, committing project/domain state or invoking another Module's semantic Operation merely because a framework can call tools.

Inference engines/runtimes remain external mechanisms connected through physical bindings. Useful bindings can include provider/model interoperability, OpenAI-compatible endpoints, generic HTTP/protocol, process/script wrappers, A2A/remote intelligence and future Owner-defined mechanisms.

MADRE does not require a first-version connector marketplace or universal hot-loader. An unusual Owner-controlled inference environment should normally be integrable through configuration or a physical binding rather than provider-specific changes to Kernel architecture.

MADRE-provided inference conveniences must use the same class of physical construction surface available to advanced Owners. No provider/runtime receives a privileged Kernel ontology or hidden first-party lifecycle.

### MEAI and MAF containment

The leading target implementation may reuse Microsoft.Extensions.AI and Microsoft Agent Framework as open-source physical infrastructure.

They do not define MADRE concepts:

```text
MADRE Agent         != MAF AIAgent
MADRE Workflow      != MAF Workflow
InferenceCapability != IChatClient
InferenceCapability != MAF AIAgent
InferenceCapability != MAF Workflow
DRE                  != MAF
```

MEAI can provide common inference interoperability behind a capability binding.

MAF can provide physical workflow graphs, fan-out/fan-in, executor sequencing, checkpoint/resume and custom physical implementations where a selected DRE strategy actually benefits from those mechanisms. Simple inference must not be forced through a workflow merely because MAF exists.

### Durable physical truth

MADRE keeps one authoritative `PhysicalInferenceWork` lifecycle. SQLite is the current leading local persistence choice unless a real requirement demonstrates otherwise.

Framework checkpoint state is subordinate execution state. A MAF checkpoint, if used, does not independently determine whether MADRE Work exists, is cancelled, terminal or should resume. Durable strategy/binding identity must make incompatible continuation after upgrades detectable rather than silently restoring old state into a changed implementation.

Restart recovery must remain truthful. Loss of certainty about an active attempt cannot be rewritten as definite failure merely for convenience; the current reference implementation's `UNKNOWN_COMPLETION` behavior is valuable evidence for the replacement.

Generic scheduler/workflow platforms such as Quartz, Wolverine, Elsa or Temporal are not currently required. They may be reconsidered if concrete needs make them simpler than the small MADRE-owned durable substrate.

## Delayed Reasoning Effort

DRE allows semantic reasoning to outlive an immediate interaction while keeping semantic and physical persistence separate.

Semantic MADRE retains why the work exists, relevant context and continuation. It derives the physical inference requirements it can know from application meaning.

Kernel retains and schedules the durable physical inference Work, combines those requirements with configured capability facts, current state and observations, and physically realizes the requested reasoning effort over time.

There is no global semantic scheduler. A Module/Agent can have an outstanding physical reasoning request for minutes or hours while keeping ownership of its own semantic continuation.

Whether semantic preparation is verbally counted as "DRE" or DRE is named only for the Kernel-side scheduler is terminology; the architectural responsibility split is fixed.

This is a core part of MADRE's thesis: time, decomposition, domain knowledge and local resources can substitute for some amount of instantaneous frontier inference, while stronger providers remain available selectively.

## SDK as generation target

MADRE is intended not only to run AI-native software but to make creation of owner-native software cheap.

A capable development AI should eventually be able to use the public SDK plus a domain requirement to generate, build, test and install an ordinary Module locally. The builder should not be required for normal runtime execution after the software exists.

A future BuilderModule may be deferred. The requirement that the public SDK be simple, explicit and sufficient is current.

## Current implementation state

The active Lane C correction branch currently contains the completed C++ LCR1–LCR3 reference implementation and Java physical client:

- native C++ Kernel;
- durable physical Work and restart recovery;
- candidate-specific one-shot `ProcessInvocation` and generic `HttpInvocation` execution;
- bounded retry/cancellation and truthful `UNKNOWN_COMPLETION` semantics;
- terminal payload/result release;
- isolated local Unix-domain socket / Windows named-pipe IPC;
- SQLite durable state;
- Java physical client using protocol v4;
- Linux/Windows behavioral CI evidence.

That implementation does not ship a model runtime, provider implementation, inference-engine inventory or llama.cpp worker.

It is now a **reference implementation/test oracle**, not the target Lane C architecture, because its Runtime→Kernel contract still assumes inference choice is already reduced to concrete invocation candidates before Kernel.

The accepted target is a capability-aware physical Kernel in which semantic MADRE supplies physical inference requirements and Kernel DRE schedules them against `InferenceCapability` configuration/state/observations.

The leading implementation candidature is a cross-platform .NET Kernel using SQLite, MEAI where useful, selective MAF physical workflow/checkpoint machinery where useful, and open generic/custom physical binding seams. C++ is not preserved merely because the reference implementation exists.

The semantic SDK/Module layer and Runtime described above are accepted architecture but are **not yet implemented in the active tree**. Do not restore discarded historical semantic implementations to hide that gap.

## Development character

MADRE is a one-Owner research/product project developed heavily with AI assistance.

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Simplicity does not mean deleting the semantic ownership/application points that make a concept operational. Do not generalise SPIRA, Module/Agent/Operation semantics or the semantic/physical boundary into vague platform abstractions.

Likewise, simplicity does not mean narrowing Kernel until meaningful physical inference scheduling/capability choice has already been completed elsewhere. DRE must remain operational.

## Authority order

1. current Owner instruction;
2. `docs/product/lane-c-owner-decision.md` for the accepted Lane C/DRE/physical-inference correction and causal rationale;
3. `docs/product/owner-intent-corpus.md` for detailed product meaning not superseded by that later decision;
4. `NORTH_STAR.md` as the short mandatory anti-drift recovery checkpoint;
5. this file as the detailed repository-level product/semantic overview;
6. `docs/architecture/security-algebra.md` for SPIRA;
7. `docs/architecture/mid-level-architecture.md` for whole-system engineering;
8. `docs/architecture/kernel.md` for target Kernel architecture;
9. active implementation/tests/CI as evidence.

Historical implementation is evidence only and loses whenever later Owner intent supersedes it.
