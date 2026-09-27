# MADRE Agent Harness

MADRE is a single-Owner research/product project. Keep the active repository small, explicit, understandable and reversible.

## Mandatory reading and authority

Before substantial MADRE work, read `NORTH_STAR.md` first and answer its eight questions in the context of the task. Then use this authority order:

1. current Owner request;
2. `docs/product/lane-c-owner-decision.md` for the accepted Lane C / DRE / physical-inference correction;
3. `docs/product/owner-intent-corpus.md` for detailed product meaning not superseded by later decisions;
4. `NORTH_STAR.md` as the concise anti-drift checkpoint;
5. `MADRE.md` for the repository-level product/semantic overview;
6. `docs/architecture/security-algebra.md` for operational SPIRA semantics;
7. `docs/architecture/mid-level-architecture.md` for whole-system engineering boundaries;
8. `docs/architecture/kernel.md` for the current physical Kernel architecture and implementation;
9. active implementation/tests/CI as evidence of what exists.

Historical code, PRs, commits, issues, discarded documents and familiar software/AI-platform patterns are evidence only. Later Owner corrections supersede historical implementation even when the historical code is more detailed.

## North Star gate

Before substantial work recover these answers from authority, not convention:

- What is MADRE?
- What is not MADRE?
- Why does MADRE exist?
- What does MADRE own and what remains Module/Agent/internal responsibility?
- What does the final Owner want?
- What should the Owner be able to inspect and edit?
- How much mandatory friction / learning curve is acceptable?
- What is the development scope for this one-Owner project?

If a proposed abstraction cannot be justified from those answers or a concrete current need, do not import it because mature platforms usually have it.

## Product invariants

- MADRE is an owner-controlled environment for using and creating AI-native software.
- DRE and domain-aware composition are core product ideas.
- Modules are independently installable application/domain boundaries.
- Not every useful capability is a Module and not every Module has an Agent.
- Agent is semantic actor; Operation is bounded action.
- Cross-Module Operation invocation does not imply Agent delegation.
- CORE is an ordinary Module assigned a role; it does not own other Modules/Agents.
- The public SDK is the construction surface for shipped, independent and eventually generated Modules.
- Owner sovereignty applies at every layer.
- The Owner's models/providers/runtimes remain independent inference mechanisms; Kernel must not rematerialize them as MADRE-owned workers/engines.
- Kernel is expected to know configured physical `InferenceCapability` facts, current physical state and provenance-preserving observations because DRE uses them.
- Semantic MADRE must not completely resolve provider/model/configuration before Kernel when doing so amputates meaningful physical DRE choice.

## SPIRA

SPIRA is intrinsic semantic composition, not a central permission service or Agent-owned mutable security context.

```text
Material/context representation       -> Sensitivity
actual receiving/exposure boundary    -> Privacy
actual Agent/provenance participant   -> Integrity
actual selected Operation/effect      -> Risk
current acting Agent continuation     -> Autonomy
```

Only actual constituents participate. The Agent decides what to try and reacts to the resulting composition; it does not own or rewrite the algebra. A receiving Module may apply bounded domain knowledge because it owns domain meaning. Runtime does not become the SPIRA evaluator merely because it transports a call.

Detailed authority is `docs/architecture/security-algebra.md`.

## ReasoningRequest and the semantic/physical boundary

A `ReasoningRequest` is semantic and is created by an Agent. It may involve context, Material, provenance, Owner instruction, SPIRA facts and the reasoning objective. It does not cross into Kernel.

Agents do not construct Kernel Work directly. Semantic MADRE derives a small physical inference requirement from the actual reasoning need and constraints. It may include prepared input, requested result characteristics, desired reasoning effort, urgency, acceptable delay/deadline, modality requirements, hard physical restrictions and explicit Owner preferences.

The invariant is:

```text
ReasoningRequest + actual semantic context/preferences
        ↓
semantic derivation of physical inference requirement
        ↓
PhysicalInferenceWork
        ↓
Kernel DRE chooses physical capability / timing / effort
```

Module, Agent, MADRE Workflow/WorkPlan, Operation semantics, raw SPIRA policy and semantic continuation do not cross this boundary.

## Kernel / DRE

Kernel is the durable physical inference substrate. Its MADRE-owned physical concepts include:

```text
PhysicalInferenceWork
InferenceCapability
configured/declared capability facts
current capability state
provenance-preserving observations/evidence
DRE inference-aware scheduling
physical attempts/results/recovery
```

A provider declaration, an Owner declaration and a MADRE observation are distinct evidence and must not be collapsed into one mutable truth.

Kernel/DRE may decide when to run, which admissible capability to use, whether to wait for availability, and which current physical evidence should affect selection. Kernel does not become an application semantic evaluator. A physically valid result is not retried merely because Kernel thinks the answer is weak.

Kernel must remain ignorant of Module semantics, MADRE Agent semantics, Operation semantics, Material meaning, ReasoningRequest semantics, SPIRA as semantic policy, CORE semantics, MADRE Skill/Workflow/WorkPlan semantics, semantic continuation and semantic persistence.

## Current Lane C convergence

The active tree contains exactly one current Lane C implementation:

- cross-platform .NET Kernel under `kernel/`;
- SQLite-authoritative durable physical Work and attempt history;
- configured/current/observed `InferenceCapability` truth;
- capability-aware DRE using effort/boundary admissibility, availability, Owner preference and observed latency where implemented;
- zero-capability startup and optional configuration;
- asynchronous capability observation so slow/broken probes do not hold Kernel startup hostage;
- explicit distinction between `Unknown` and `Unavailable`; an admissible configured `Unknown` capability may be tried when no known-available option exists, while known-unavailable capabilities wait and are re-observed automatically;
- current configured capability catalogue reconciled on restart while historical attempt evidence remains historical;
- wake/deadline-driven scheduling with explicit domain ordering and Kernel-owned timing defaults;
- typed physical failure vocabulary with separate technical detail;
- one common binding-execution responsibility;
- shell-free process binding, MEAI interoperability and the open `IInferenceBinding` seam;
- a versioned bounded local IPC protocol over Unix-domain sockets for .NET↔Java 21 on Windows/Linux;
- Java `madre-kernel-client` using that local socket protocol;
- bounded concurrency, caller disappearance, eligibility/deadlines, cancellation, retained results/release and restart `UnknownCompletion` behavior;
- Linux and Windows behavioral CI plus contamination/destructive-convergence checks.

There is no TCP/loopback web control plane, configurable port, ASP.NET host dependency, production MAF workflow/checkpoint strategy, checkpoint state, MAF package dependency, or packaged Java CLI containing test classes.

The previous `High + Background` two-stage MAF behavior was validation scaffolding, not product strategy, and is deleted. Git history is sufficient evidence that checkpointing was explored. A future real physical strategy may use MAF or another mechanism only when an actual product need earns that machinery.

The superseded native C++ Kernel, workers, model/runtime ownership, llama.cpp integration, CMake/native acceptance, protocol-v4 implementation and validation-era side trees remain historical evidence only.

The semantic SDK/Module layer and Runtime remain accepted architecture but are not implemented by Lane C. Do not start or restore them while working a Kernel-only task.

## Physical bindings and first-party parity

A binding receives execution-relevant physical data, not unrelated scheduler metadata. MADRE-provided inference conveniences use the same class of physical seam available to advanced Owners. No provider/runtime receives a privileged Kernel ontology or hidden first-party lifecycle.

Do not build a connector marketplace, generic plugin system, security framework, speculative strategy registry or generic scheduler without a concrete MADRE need.

## Development style

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Prefer working behavior, explicit contracts and behavioral evidence over speculative abstractions, compatibility fossils, universal registries or framework-within-framework designs. Simplification must not erase semantic ownership or meaningful DRE.

Commit and push coherent behavior. Keep implementation truth, documentation truth and CI evidence aligned.
