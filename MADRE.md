# MADRE

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

Read `NORTH_STAR.md` first before substantial work. `docs/product/owner-intent-corpus.md` preserves deeper product reasoning; `docs/product/lane-c-owner-decision.md` is the current Lane C/DRE correction; `docs/architecture/mid-level-architecture.md` describes whole-system engineering; `docs/architecture/kernel.md` describes current physical Lane C.

Current Owner instructions override repository documentation when more specific. Historical code/tests/commits and familiar platform patterns are evidence only.

## Product thesis

MADRE does not equate application capability with one synchronous frontier-model invocation. Domain knowledge, deterministic Operations, accumulated state, reusable Agents/Skills and **Delayed Reasoning Effort** can make local/open intelligence sufficient for much ordinary work, while stronger external inference remains selectively available.

The application and reasoning environment stays with the Owner. Models/providers are resources used by that environment rather than the place where the environment must live.

## Owner sovereignty

The Owner owns the complete installation and is never MADRE's adversary. MADRE may provide minimisation, diagnostics, recovery and useful defaults; those mechanisms serve the Owner rather than governing them.

The Owner may inspect, modify, replace or experiment with Modules, Agent state, Runtime behavior, CORE assignment, inference configuration, Kernel state/implementation, inference bindings, generated software and source code.

## Modules, Agents and Operations

A **Module** is an independently installable application/domain semantic boundary. It owns its domain state, persistence, types, integrations, optional UI, optional Agents and domain-specific behavior.

An **Agent** is a semantic reasoning actor. An **Operation** is bounded executable behavior. The distinction matters: another Agent can invoke an Operation exposed by an agentless Module without transferring semantic continuation. Agent-to-Agent delegation is explicit and different.

A **Skill** is reusable know-how. Where MADRE uses Workflow/WorkPlan concepts, they remain semantic Agent behavior/planning rather than Kernel scheduling.

The public SDK is intended to be the construction surface for shipped, independent and eventually AI-generated Modules. It must remain small and explicit enough that an automated builder can generate ordinary installed software without hidden MADRE knowledge.

## SPIRA

MADRE's Security Algebra is intrinsic composition of actual semantic facts, not a central authorization service or Agent-owned mutable security context.

```text
Material/context representation       -> Sensitivity
actual receiving/exposure boundary    -> Privacy
actual Agent/provenance participant   -> Integrity
actual selected Operation/effect      -> Risk
current acting Agent continuation     -> Autonomy
```

Only actual constituents participate. If a semantic composition is incompatible, software changes the actual constituents rather than relabeling facts. SPIRA remains above the Kernel boundary.

## ReasoningRequest and physical inference

A `ReasoningRequest` represents a semantic need for reasoning and is created by an Agent. It can involve context, Material, provenance, Owner instruction and relevant semantic facts.

It does not cross into Kernel. Semantic MADRE derives a small physical inference requirement containing only what physical execution needs today, such as prepared input, requested effort, urgency, eligibility/deadline and hard execution restrictions.

This boundary preserves model agnosticism: application meaning is not defined by whichever physical inference mechanism is eventually used.

## CORE

CORE is an ordinary Module assigned the CORE role. It can provide the normal Owner interaction and useful default/meta behavior. It does not own other Modules/Agents, Runtime, Kernel or SPIRA.

## Kernel and Delayed Reasoning Effort

Kernel is MADRE's shared durable physical inference substrate. It owns:

```text
PhysicalInferenceWork
InferenceCapability configured facts
current capability availability
historical physical observations/evidence
DRE physical scheduling
attempt/result/cancel/recovery lifecycle
```

It does **not** understand Module, Agent, Operation semantics, Material meaning, ReasoningRequest semantics, SPIRA, CORE or semantic continuation.

Kernel capability knowledge is deliberately empirical. Configured facts, current availability and historical observations are separate. DRE uses the physical evidence it actually has rather than assuming all inference paths are equal.

### Current DRE behavior

Current Lane C preserves durable eligibility/deadlines, hard execution-boundary/effort admissibility, current availability truth, Owner preference and observed successful latency where the implemented interactive rule consumes it.

Known available is preferred. A configured `Unknown` capability remains a legitimate option when no known-available admissible candidate exists because lack of probe evidence is not unavailability. Known-unavailable capabilities wait and are re-observed automatically.

Kernel does not become an application answer judge. A physically valid result that does not solve the user's semantic problem is handled by the Agent/Module above Kernel.

## Current physical construction surface

Current Lane C is a cross-platform .NET Kernel under `kernel/` with:

- SQLite authoritative durable Work, attempts, configured/current capability truth and retained results;
- wake/deadline-driven DRE scheduling with explicit domain policy;
- asynchronous capability observation so startup is not held hostage by slow/broken inference systems;
- configuration reconciliation so removed capabilities are no longer selectable while attempt history remains historical;
- typed physical failure vocabulary with separate technical detail;
- one physical binding-execution responsibility;
- shell-free process binding, MEAI interoperability and open `IInferenceBinding` extensibility;
- a small versioned bounded Unix-domain-socket IPC contract used by Java 21 on Windows/Linux;
- independent Kernel/client lifetimes, bounded concurrency, cancellation, release and truthful `UnknownCompletion` restart recovery.

A missing configuration and zero configured capabilities are valid; configuration becomes necessary only when inference is actually expected.

There is no TCP/loopback web control plane, configurable port or web-host dependency.

The earlier `High + Background` MAF two-stage/checkpoint behavior was a validation strategy rather than MADRE product strategy and has been deleted together with its production dependency/state/tests. Git history preserves that experiment. Future real physical strategies may use MAF when a concrete need exists; no scaffolding is kept speculatively.

## Physical binding openness

The Owner's inference systems remain independent. `InferenceCapability` describes the physical opportunity available to MADRE; it does not rematerialize the provider/runtime as a MADRE-owned engine.

MADRE-provided integrations use the same class of binding seam available to advanced Owners. Removing one adapter must leave Kernel architecture coherent. Connector marketplaces, plugin prisons, generic schedulers and speculative strategy registries are not current requirements.

## DRE and domain knowledge together

MADRE's practical comparison is not simply small local model versus frontier model. It is:

```text
domain-aware application
+ deterministic software
+ accumulated knowledge
+ semantic Agents
+ Delayed Reasoning Effort
+ selective physical inference
```

versus repeatedly reconstructing the whole domain in a synchronous provider conversation.

Time, decomposition and existing domain state can substitute for some amount of immediate model power. When they cannot, MADRE can still use stronger external intelligence selectively.

## SDK as generation target

A central product goal is that the public semantic surface be simple enough for AI-assisted development to generate reliable Modules. The eventual builder may be deferred, but the architecture must already avoid hidden conventions and privileged first-party semantic APIs.

Generated software should become normal installed local software. The development AI need not remain in the runtime loop after the Module exists.

## Current implementation status

Lane C is the only current implemented physical inference layer. Its Linux/Windows CI re-proves durable Work, capability-aware DRE, local bounded IPC, Java client interoperability, caller disappearance, eligibility/deadlines, cancellation, release, restart recovery, process/custom binding openness, optional-probe behavior, automatic availability recovery, configuration reconciliation and no-head-starvation scheduling.

The superseded native C++ Kernel, workers, model/process lifecycle, llama.cpp privilege, protocol-v4 shapes, loopback web host and MAF validation strategy are deleted from the active tree and remain historical evidence only.

The semantic SDK/Module layer and Runtime described by architecture are **not yet implemented in the active tree**. Lane C correction must not be used as an excuse to start SDK/Runtime work.

## Development character

MADRE is a one-Owner research/product project developed heavily with AI assistance.

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

New structure earns its place because an actual MADRE responsibility needs it, not because conventional platforms usually contain it.
