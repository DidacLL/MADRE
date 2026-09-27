# MADRE

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

This file is the detailed repository-level product and semantic overview. It intentionally preserves the reasoning connections among Modules, Agents, Operations, SPIRA, DRE, Runtime, CORE, Kernel and the SDK generation target.

Read `NORTH_STAR.md` first as the short mandatory recovery checkpoint before substantial work. Read `docs/product/lane-c-owner-decision.md` for the current DRE/physical-inference correction and rationale, `docs/product/owner-intent-corpus.md` for deeper product reasoning, `docs/architecture/security-algebra.md` for the operational SPIRA model, `docs/architecture/mid-level-architecture.md` for the whole-system design, and `docs/architecture/kernel.md` for current physical Lane C.

Current Owner instructions override repository documentation when more specific. Historical code/tests/PRs/commits and familiar platform patterns are evidence only.

## Product thesis

MADRE does not equate application capability with one synchronous frontier-model invocation. Domain knowledge, deterministic bounded Operations, reusable Agents/Skills, accumulated state, decomposition and **Delayed Reasoning Effort** can make local/open intelligence sufficient for much ordinary work, while stronger external inference remains selectively available when it adds real value or the Owner wants it.

The application and reasoning environment stays with the Owner. Models/providers are resources used by that environment rather than the place where the environment must live.

## Owner sovereignty

The Owner owns the complete installation and is never MADRE's adversary. MADRE may provide minimisation, diagnostics, recovery and useful defaults; those mechanisms serve the Owner rather than governing them.

The Owner may inspect, modify, replace or experiment with Modules, Agent state, Runtime behavior, CORE assignment, inference configuration, Kernel state/implementation, inference bindings, generated software and source code.

## Modules, Agents and Operations

The public SDK is the construction surface for shipped, independent and eventually AI-generated MADRE software.

A **Module** is an independently installable application/domain semantic boundary. It owns its domain state, persistence, types, integrations, optional UI, optional Agents and domain-specific behavior. A Module may be agentless, UI-less, very small, or a thin binding around existing software.

An **Agent** is a semantic reasoning actor. An **Operation** is bounded executable behavior. The actor/action distinction is intentional: another Agent can invoke an Operation exposed by an agentless Module without transferring semantic continuation. Agent-to-Agent delegation is explicit and different.

A **Skill** is reusable know-how. A **Workflow** is reusable Agent behavior. A **WorkPlan** is objective-specific semantic planning that may coordinate multiple Agents/Workflows. Reusable behavior does not become a Module merely because it is useful.

## SPIRA

MADRE's Security Algebra is intrinsic composition of the actual semantic facts participating now. It is not a permission/authorization service, policy engine or mutable Agent-owned security context.

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

There is no mandatory `EffectProfile`, Agent-owned `Compound`, central `Authorization` object or Runtime policy evaluator in the current architecture.

Where the actual semantic construction makes the relations relevant:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)

min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)

R_actual_operation
    <= min(I_actual_effect_realizers)
```

These describe intrinsic compatibility of the current construction, not permission from a central authority. Only actual constituents participate. If the exact construction is incompatible, semantic software changes actual constituents and derives again. A transformed/minimised representation is new Material rather than a relabelled source.

The Agent decides what to try; the algebra follows from what actually participates. A receiving Module can apply bounded domain knowledge because it owns domain meaning. Runtime does not become the SPIRA evaluator because it transports a call.

Detailed authority is `docs/architecture/security-algebra.md`.

## ReasoningRequest and the physical inference boundary

A `ReasoningRequest` is a semantic need for reasoning created by an Agent. It can involve context, Material, provenance, Owner instruction, relevant SPIRA facts and the reasoning objective. It is not required to become a generic SPIRA tuple; facets remain on the actual semantic facts where they belong.

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

The exact SDK carrier and Runtime journey remain design work. A field belongs in the first physical contract because an implemented DRE decision consumes it, not because a generic AI platform might want it.

The semantic side does not need to resolve an exact provider/model/configuration before Kernel. That intermediate correction removed Kernel-owned inference engines but over-corrected the boundary by preventing meaningful physical DRE choice.

## Runtime

Runtime is the installed semantic environment around Modules, SDK facilities and the Kernel boundary. It supplies installation/configuration, CORE role assignment, live Module discovery/lifecycle/addressing, Operation and Agent-delegation routing, inspection/diagnostics, persistence/correlation needed for delayed semantic reasoning, correlation between semantic requests and Kernel Work/results, continuation/recovery support, and transport of the derived physical inference requirement.

Runtime provides mechanics. It does not acquire Module domain meaning, become a global semantic scheduler, or become the semantic evaluator of SPIRA merely because it transports or executes calls.

## CORE

CORE is an ordinary Module assigned the CORE installation role. It can provide ordinary Owner interaction, default/meta behavior, default Agents and reusable generic behavior.

CORE is not Runtime, Kernel, owner of other Modules or a special SPIRA authority. Kernel inference-capability knowledge and physical DRE scheduling do not become CORE ownership merely because a previous architecture used a Module-owned physical executor.

## Kernel and InferenceCapability

Kernel is MADRE's shared durable physical inference substrate. It owns:

```text
PhysicalInferenceWork
InferenceCapability configured facts
current capability state
historical physical observations/evidence
DRE physical scheduling
attempt/result/cancel/recovery lifecycle
```

An `InferenceCapability` is a physical path through which MADRE can obtain intelligence. It is not a provider/model ontology and does not make the external runtime MADRE-owned. The same underlying model exposed with materially different configurations can be several capabilities; different providers/runtimes may also be interchangeable for a physical requirement.

Capability knowledge preserves provenance rather than flattening every fact into one mutable truth:

```text
CONFIGURED / DECLARED
    physical identity/binding
    locality/exposure boundary
    supported reasoning characteristics
    Owner configuration/preferences
    other typed facts only when a DRE consumer exists

CURRENT
    reachability / availability
    transient physical state where implemented

HISTORICAL / OBSERVED
    attempt identity/outcome
    latency for the physical capability/binding/version that produced it
    other evidence only when a real DRE strategy consumes it
```

Provider claims, Owner declarations and MADRE observations are different facts.

Kernel does not understand Module, Agent, Operation semantics, Material meaning, ReasoningRequest semantics, SPIRA, CORE or semantic continuation. It also does not own model loading/warmness, provider/runtime internal lifecycle, generic engine RAM/VRAM accounting or external inference-engine implementation. A Module may privately use another AI environment without shared Kernel involvement.

## Delayed Reasoning Effort

DRE allows semantic reasoning to outlive an immediate interaction while semantic and physical persistence remain separate.

Semantic MADRE retains why work exists, relevant context and continuation, and derives the physical requirements it can know from application meaning. Kernel retains/schedules durable physical inference Work, combines those requirements with configured capability facts, current state and physical observations, and physically realizes the requested reasoning over time.

Kernel DRE is inference-aware rather than a generic timer service. It may decide when to run, which admissible capability is currently appropriate, whether current unavailability justifies waiting/re-observation, how much physical reasoning effort is required, and how current evidence should affect selection. Physical multi-stage inference remains legitimate when a real product strategy requires it; it does not become a semantic MADRE Workflow merely because a graph/workflow mechanism implements it.

Kernel does not become an application answer judge. A physically valid result that does not satisfy the user's semantic objective is handled above Kernel by the consuming Agent/Module.

There is no global semantic scheduler. A Module/Agent can have outstanding physical reasoning for minutes or hours while retaining ownership of its semantic continuation.

This is central to MADRE's thesis: time, decomposition, domain knowledge and local resources can substitute for some amount of instantaneous frontier inference, while stronger providers remain selectively available.

## Physical binding openness

The Owner's inference systems remain independent. `InferenceCapability` describes the physical opportunity available to MADRE; it does not rematerialize the provider/runtime as a MADRE-owned engine.

Useful physical realizations may include generic inference interoperability, OpenAI-compatible endpoints, protocol adapters, process/script wrappers, A2A/remote intelligence and future Owner-defined mechanisms when a concrete need exists.

MADRE does not require a first-version connector marketplace or universal hot-loader. An unusual Owner-controlled inference environment should normally be integrable through configuration or an `IInferenceBinding`, not provider-specific changes to Kernel architecture. MADRE-provided integrations use the same class of seam available to advanced Owners.

MEAI remains useful interoperability behind bindings. A future actual physical strategy may use MAF or another workflow mechanism when a concrete need earns that machinery; the current Kernel keeps no speculative checkpoint/strategy registry.

## Current physical construction

Current Lane C is a cross-platform .NET Kernel under `kernel/` with:

- one process-lifetime owner per SQLite database, independent of IPC path;
- explicit current SQLite schema identity and early rejection of incompatible pre-release databases rather than migrations;
- durable Work, attempts, configured/current capability truth and retained results;
- wake/deadline-driven DRE scheduling over metadata-only candidates, loading prepared input only after claim;
- asynchronous startup observation and demand-driven unavailable-capability re-observation, with probe timeout represented as `Unknown`;
- latency evidence scoped to the current capability/binding/version;
- configuration reconciliation so removed capabilities are no longer selectable while historical attempts remain historical;
- typed physical failures with separate technical detail;
- one physical binding-execution responsibility;
- shell-free explicitly UTF-8 process binding, MEAI interoperability and open `IInferenceBinding` extensibility;
- a small versioned bounded Unix-domain-socket IPC contract used by Java 21 on Windows/Linux;
- bounded active server handlers and bounded Java call lifetime;
- strict IPC/config/CLI parsing without silent enum/default leakage;
- supervised Kernel-owned background scheduler/probe/execution persistence, where fatal infrastructure failure fails/terminates the host rather than leaving `Health=ok`;
- independent Kernel/client lifetimes, bounded physical concurrency, cancellation, release and truthful `UnknownCompletion` restart recovery.

A missing configuration and zero configured capabilities are valid; configuration becomes necessary only when inference is expected.

There is no TCP/loopback web control plane, configurable port, web-host dependency, production MAF workflow/checkpoint behavior, compatibility/migration layer, provider-specific Kernel ontology or packaged Java acceptance CLI.

The semantic SDK/Module layer and Runtime are **not yet implemented in the active tree**. Lane C correction must not be used as an excuse to start SDK/Runtime work.

## SDK as generation target

MADRE is intended not only to run AI-native software but to make creation of owner-native software cheap.

A capable development AI should eventually be able to use the public SDK plus a domain requirement to generate, build, test and install an ordinary Module locally. The builder should not be required for normal runtime execution after the software exists.

A future BuilderModule may be deferred. The requirement that the public SDK be simple, explicit and sufficient is current. Independent/generated Modules must not need hidden first-party hooks, Runtime internals, Kernel internals or undocumented conventions.

## Development character

MADRE is a one-Owner research/product project developed heavily with AI assistance.

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Simplicity does not mean deleting semantic ownership/application points that make a concept operational, and it does not mean narrowing Kernel until meaningful physical inference choice has already been completed elsewhere. New structure earns its place because an actual MADRE responsibility requires it.

Lane C implementation state is evidence for Owner/orchestrator audit; the implementation does not declare Lane C closed.

## Authority order

1. current Owner instruction;
2. `docs/product/lane-c-owner-decision.md` for the accepted Lane C/DRE/physical-inference decision and causal rationale;
3. `docs/product/owner-intent-corpus.md` for detailed product meaning not superseded by that later decision;
4. `NORTH_STAR.md` as the short mandatory anti-drift recovery checkpoint;
5. this file as the detailed repository-level product/semantic overview;
6. `docs/architecture/security-algebra.md` for SPIRA;
7. `docs/architecture/mid-level-architecture.md` for whole-system engineering;
8. `docs/architecture/kernel.md` for current Kernel architecture/implementation;
9. active implementation/tests/CI as evidence.

Historical implementation is evidence only and loses whenever later Owner intent supersedes it.
