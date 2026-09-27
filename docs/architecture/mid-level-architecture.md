# MADRE — mid-level architecture

Status: **accepted whole-system engineering boundaries; Lane C physical implementation current, semantic SDK/Runtime not yet implemented in the active tree**.

This document translates Product/Owner intent plus the current Lane C Owner decision into engineering boundaries. It is intentionally mid-level: concrete enough to constrain implementation and explain the complete system, while avoiding speculative private classes and exhaustive catalogues.

Normative SPIRA semantics are in `docs/architecture/security-algebra.md`. The causal rationale for the DRE/physical-inference boundary is in `docs/product/lane-c-owner-decision.md`.

Two rules govern this architecture:

> Assign ownership only where MADRE actually needs ownership. Preserve intrinsic composition as composition rather than creating managers for relations.

> Keep public contracts explicit enough that an independent human or AI builder can implement a Module—and an advanced Owner can extend physical inference—without hidden first-party knowledge.

## 1. System concerns

```text
Owner
  ↓
Modules / applications and semantic Agents
  ↓
Public MADRE SDK + MADRE Runtime semantic environment
  ↓
ReasoningRequest / actual semantic context
  ↓
small derived physical inference requirement
  ↓
madre-kernel-client
======== HARD SEMANTIC / PHYSICAL BOUNDARY ========
  ↓
Kernel durable PhysicalInferenceWork + DRE + InferenceCapability truth
  ↓
open physical binding
  ↓
Owner-selected independent inference environment
```

These are not equivalent services.

- **Modules** contain application/domain meaning.
- **SDK** is the public construction vocabulary shared by shipped, independent and generated software.
- **Runtime** supplies the installed semantic environment: Module lifecycle/discovery/routing plus shared semantic execution mechanics and correlation with physical inference Work.
- **ReasoningRequest** is semantic.
- **physical inference requirement** contains only physical consequences semantic MADRE can derive for Kernel.
- **Kernel** owns durable physical inference Work, configured capability knowledge/observations, DRE scheduling and physical execution.
- **InferenceCapability** describes a physical intelligence path as experienced by MADRE; it is not a provider/model/worker ontology.
- **physical bindings** are replaceable implementation mechanisms beneath MADRE physical concepts.

A Module may privately use another AI/inference environment without shared Kernel involvement. Kernel is not a universal interceptor.

## 2. Module boundary

A Module is an independently installable application/domain semantic boundary. It may internally own domain state, persistence, UI, integrations, private intelligence and any implementation its application needs. Only the public semantic surface matters to MADRE composition.

A Module may be agentless, UI-less, very small, or a thin binding around existing software.

Conceptually a Module may provide or expose:

```text
0..* Agents        semantic reasoning actors
0..* Operations    bounded executable behavior
0..* Skills        reusable know-how
optional UI/integrations/domain types/state
meaningful Material/context
```

An Agent may use Skills, Workflows, Operations and ReasoningRequests. A WorkPlan may coordinate Agents and Workflows. These are semantic concepts rather than Kernel scheduling shapes.

## 3. Agent and Operation are actor and action

An Agent is the semantic actor. An Operation is one bounded action the actor can perform.

A Module can expose Operations without providing an Agent. Another Agent can invoke them. Runtime transports and activates; it does not become the actor merely because it routes a call.

The distinction is fundamental:

```text
Operation invocation -> use another bounded action; initiating Agent keeps semantic continuation
Agent delegation     -> another Agent receives delegated semantic continuation
```

A WorkPlan may coordinate multiple Agents and Workflows, but it is semantic planning rather than Runtime scheduling or Kernel Work.

## 4. SPIRA direct carriers

SPIRA is not one universal security context. Each facet comes from the semantic fact where it actually has meaning.

```text
Actual Material / context              -> Sensitivity
Actual receiving/exposure boundary     -> Privacy
Actual Agent / provenance participant  -> Integrity
Actual selected Operation / effect     -> Risk
Current acting Agent continuation      -> Autonomy
```

Current values are:

| Facet | 0 | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- | --- |
| Sensitivity | SYSTEM_RESERVED | TRIVIAL | SHARED | PROFILING | SENSITIVE | SECRET |
| Privacy | SYSTEM_RESERVED | PUBLIC | UNKNOWN | LOCAL | MODULE | ISOLATED |
| Integrity | SYSTEM_RESERVED | NOT_DECLARED | DECLARED | TRUSTED | ACCEPTED | VALIDATED |
| Risk | SYSTEM_RESERVED | READ | WRITE | DELETE | EXECUTE | POTENTIALLY_HARMFUL |
| Autonomy | SYSTEM_RESERVED | LIVE_INTERACTION | ASK_ALWAYS | ASK_ONCE | ACKNOWLEDGE | AUTONOMOUS |

Integrity means increasing assurance: `NOT_DECLARED` has no meaningful traceability; `DECLARED` is manifested but not independently established; `TRUSTED` has provenance/common-use/other evidence despite incomplete analysis; `ACCEPTED` has stronger assurance from effective boundaries, known origin, observable behavior or direct Owner acceptance; `VALIDATED` is deterministically verifiable again when needed.

Same-facet reductions are:

```text
Sensitivity = max(actual participating Sensitivities)
Privacy     = min(actual participating Privacies)
Integrity   = min(actual participating Integrities)
```

Risk is the Risk of the actual selected Operation/effect. Autonomy is the Autonomy of the actual acting Agent continuation. They are not generic running aggregates. There is no mandatory `EffectProfile` in the current architecture.

## 5. Operation semantic surface

An Operation can expose the semantic facts necessary to compose with it before executing arbitrary implementation code.

Conceptually:

```text
Operation
    accepted Material type -> receiving Privacy
    produced Material type -> maximum promised Sensitivity
    concrete behavior/effect -> Risk
```

The exact public-language representation is not fixed at this level.

Produced Material that promises a bounded maximum Sensitivity must honor that semantic contract. A Module can deliberately produce a new minimised/anonymised representation with lower Sensitivity, but that representation is distinct from its source.

Derived summaries may help discovery. They are views derived from real constituents, not additional owners of a facet.

## 6. Intrinsic SPIRA composition

No Agent or Runtime component owns a mutable Compound object. Only actual constituents contribute. Unused Operations, possible future results, installed capabilities and unrelated branches do not contaminate the current construction.

The Agent does not decide what the algebra should say. The Agent decides what to try. SPIRA follows from the real semantic pieces of that attempt.

Where the current construction makes relations relevant:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)

min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)

R_actual_operation
    <= min(I_actual_effect_realizers)
```

These are intrinsic composition relations, not authorization or permission checks. An incompatible exact construction cannot continue unchanged. The Agent can change reality and derive again: choose another Operation/path, derive new Material, change continuation through Owner interaction, delegate, use another real participant, or stop.

## 7. Module-local semantic re-evaluation

Module boundaries are meaningful because Modules know their domains. A target Module does not blindly inherit one eternally global sensitivity label, and it does not discard source knowledge. It may apply bounded domain facts/strategies to derive a different current representation where its domain meaning justifies that derivation.

If a domain strategy genuinely needs reasoning, an Agent creates a ReasoningRequest. SPIRA itself does not secretly call a model.

Runtime routes context and known facts; it does not replace the target Module's domain interpretation.

## 8. ReasoningRequest remains semantic

A ReasoningRequest is created by an Agent when reasoning is actually needed. It may involve context, Material, provenance, Owner instruction, relevant SPIRA facts and the reasoning objective.

The architecture deliberately does not define a mandatory `ReasoningRequest.S/P/I/R/A` tuple. Risk remains with an actual Operation/effect. Autonomy remains with the current Agent continuation. Other facets remain with the actual objects/boundaries/provenance that contribute them.

ReasoningRequest does not cross into Kernel as a semantic object. Semantic MADRE derives the physical inference facts Kernel needs.

```text
Agent establishes actual semantic context/composition
        ↓
ReasoningRequest: semantic reasoning need
        ↓
semantic derivation boundary
        ↓
small physical inference requirement
        ↓
Kernel PhysicalInferenceWork
```

The exact public request type remains design work. It must not become a second semantic ontology inside Kernel.

## 9. Runtime responsibilities

Runtime is the installed semantic environment around Modules, SDK facilities and the physical Kernel boundary.

### Module environment

Runtime provides installation mechanics for:

- Module installation/configuration;
- CORE role assignment;
- live discovery of exposed Module surfaces;
- activation/lifecycle;
- addressing;
- routing cross-Module Operation calls;
- routing Agent delegation;
- Owner/runtime inspection and diagnostics.

Runtime provides mechanics. It does not acquire the Module's domain meaning and does not become the evaluator of SPIRA.

### Shared reasoning execution

Runtime also provides installation mechanics for:

- persistence/correlation needed for delayed semantic reasoning;
- correlation between semantic requests and physical Kernel Work/results;
- semantic continuation/recovery support;
- transporting the physical inference requirement derived from semantic facts;
- Owner-facing inference configuration/inspection where appropriate.

The meaning of reasoning remains in the responsible Agent/Module semantic process. There is no Runtime-owned global semantic scheduler merely because Kernel physical inference can remain outstanding for a long time.

## 10. Semantic-to-physical inference contract

Agents create `ReasoningRequest`s. They do not construct Kernel `PhysicalInferenceWork` directly.

Conceptually:

```text
ReasoningRequest + actual semantic context/preferences
        ↓
semantic derivation
        ↓
prepared input
requested result characteristics
reasoning effort/depth indication
context/modality characteristics
urgency
acceptable delay / deadline
hard physical restrictions
Owner execution preferences where relevant
        ↓
PhysicalInferenceWork request
```

Only physical consequences cross the boundary. Module/Agent/MADRE Workflow/WorkPlan semantics, Operation meaning, raw SPIRA as a Kernel policy object and semantic continuation remain above it.

The first version must remain small. A field is justified by an implemented DRE consumer, not hypothetical platform flexibility.

The earlier exact `ConcretePhysicalInvocation`-before-Kernel boundary remains historical evidence only; it is not current architecture because it removes meaningful capability/effort selection from Kernel DRE.

## 11. Delayed Reasoning Effort split

DRE spans two persistence domains without merging them.

Above Kernel:

```text
why reasoning exists
relevant domain context
ReasoningRequest
semantic continuation/correlation
```

Below Kernel:

```text
durable PhysicalInferenceWork
eligibility/deadline/urgency
hard physical restrictions
capability truth/observations
attempt/result/cancel/recovery
```

Semantic processes may disappear while Kernel remains responsible for physical Work. Later semantic software can reconnect to the retained result and resume meaning.

DRE's physical questions include run now/later, which admissible capability is appropriate, whether known unavailability justifies waiting/re-observation, how much physical effort is required, and which physical evidence is relevant. Durable eligibility, deadlines and restart recovery are mechanics underneath those decisions, not a separate semantic scheduling system.

## 12. Kernel physical model

Kernel owns shared physical inference concepts, not external inference engines.

### PhysicalInferenceWork

One authoritative durable Work lifecycle stores the physical requirement and execution evidence needed for DRE/recovery. Current Work has no MAF/checkpoint/strategy scaffolding. SQLite is authoritative for current Lane C physical state.

### InferenceCapability

An `InferenceCapability` is a configured physical intelligence opportunity consisting of identity/binding and typed configured facts currently consumed by DRE. Configured facts, current availability and historical attempt observations remain separate.

Current implementation models execution boundary, supported effort, Owner preference, `Unknown/Unavailable/Available`, observation time and successful latency evidence scoped to current capability/binding/version. Durable attempts retain physical history; unused success/failure aggregate counters are not public capability state.

### DRE

Current DRE consumes eligibility/deadlines, effort/boundary admissibility, availability truth, Owner preference and binding-scoped observed latency where comparable for interactive Work.

Known available is preferred. `Unknown` remains usable when no known-available admissible candidate exists. Known unavailable waits and is automatically re-observed only while relevant pending Work creates demand. Probe timeout yields `Unknown`, not fabricated `Unavailable`.

Kernel does not judge application-level answer quality.

### Scheduling and durability

The scheduler is wake/deadline driven and considers the complete eligible metadata set so waiting head Work cannot hide later runnable Work. Scheduling candidates contain scheduling facts only; prepared payload is loaded only after a Work claim succeeds.

One process-lifetime owner exists per database regardless of IPC endpoint. Current SQLite schema identity is explicit. An incompatible unversioned/pre-release database fails early; there is no migration/compatibility layer.

Fatal scheduler, capability-observation persistence or attempt persistence failures are supervised. The host must fail/terminate instead of continuing to report healthy while authoritative physical state can no longer be maintained.

## 13. Physical construction surface

The current Kernel is cross-platform .NET because its work is asynchronous integration, structured physical state, SQLite durability, capability observation and scheduling rather than native model lifecycle.

`IInferenceBinding` is the open physical execution seam. The process binding, MEAI interoperability and Owner/custom bindings use the same responsibility. Bindings receive execution-relevant physical data only, not unrelated scheduling metadata.

Process execution is shell-free and explicitly UTF-8 for stdin/stdout/stderr. Common result validation, exception conversion and payload enforcement live in the common binding executor.

Microsoft Agent Framework is not a current production dependency or strategy. A future actual physical strategy may use MAF, another workflow mechanism or direct code if a concrete product need earns it. No strategy registry/checkpoint fields are retained speculatively.

## 14. Local physical boundary

`madre-kernel-client` and the .NET host communicate through a versioned bounded framed protocol over Unix-domain sockets on Windows/Linux.

There is no TCP port or web-server control plane. Each request uses an independent local connection. Server frame bounds are accompanied by a bound on active client handlers, so stalled peers cannot create unbounded handler exposure. Java client calls have a bounded technical timeout rather than blocking forever against a bogus/stalled peer.

Malformed protocol input is rejected rather than acquiring enum-zero/default semantics. The host configuration and CLI likewise reject unknown fields/flags, missing values and malformed values.

The Java client is a normal Java 21 library. Protocol operation/error vocabulary is a small typed client contract; acceptance helper classes remain test-only.

## 15. CORE position

CORE is an ordinary Module assigned the CORE installation role. The shipped default CORE may provide ordinary Owner interaction, default/meta behavior, default Agents and reusable general Operations/Skills.

CORE is not Runtime, Kernel, owner of other Modules, or a special SPIRA authority. Physical capability knowledge and DRE scheduling are Kernel responsibilities.

## 16. SDK as automated construction target

The public SDK must be sufficient for independent and eventually AI-generated Modules.

Intended journey:

```text
Owner requirement
  -> AI-assisted builder/development system
  -> public MADRE SDK + domain information
  -> ordinary Module code
  -> build/test/install locally
  -> normal installed Module
```

A generated Module must not require hidden first-party hooks, Runtime internals or Kernel internals. SPIRA is part of the public construction vocabulary so generated software can express actual information, receiving-boundary, provenance, consequence and autonomy semantics without inventing another security architecture.

The builder is absent from normal execution after the software exists. A future BuilderModule may be deferred; the public SDK sufficiency requirement exists now.

At the physical layer, an advanced Owner should likewise be able to create an unusual inference binding through the same class of surface used by provided adapters.

## 17. Logical dependency direction

```text
CORE Module ─────────────→ madre-sdk
independent Module ──────→ madre-sdk
MADRE Runtime ───────────→ madre-sdk
MADRE Runtime ───────────→ madre-kernel-client
madre-kernel-client ─────→ Kernel local IPC contract
Kernel ──────────────────→ physical libraries/bindings
bindings ────────────────→ Owner inference environment
```

Hard dependency rules:

```text
madre-kernel-client -X-> madre-sdk
Kernel              -X-> madre-sdk
Kernel              -X-> MADRE Runtime
```

Kernel physical implementation libraries do not leak into the semantic SDK. A Module may privately use external AI or other systems without creating a Kernel dependency.

## 18. Current Lane C implementation status

Current Lane C implements and CI-proves:

- zero-capability startup without mandatory configuration;
- one active process owner per database across same/different IPC paths and non-destructive live endpoint handling;
- SQLite durable Work/attempt/result lifecycle with explicit schema identity and truthful incompatible-pre-release failure;
- configured/current/observed capability truth and catalogue reconciliation;
- asynchronous startup observation plus demand-driven unavailable-capability recovery;
- probe timeout represented as `Unknown`;
- binding/version-scoped latency evidence;
- capability-aware DRE with explicit domain ordering;
- wake/deadline-driven metadata-only scheduling without fixed busy polling or fixed-head starvation;
- supervised fatal background infrastructure failures;
- typed physical failures and technical detail;
- shell-free UTF-8 process/MEAI/custom open binding seam;
- bounded local Unix-domain-socket IPC and bounded Java 21 calls on Windows/Linux;
- strict IPC/config/CLI parsing;
- caller disappearance, eligibility/deadlines, cancellation, retained release and restart `UnknownCompletion`;
- contamination checks preventing semantic leakage, web/port residue, checkpoint/MAF residue and test-helper production packaging.

The superseded C++/worker/model-lifecycle/llama.cpp/protocol-v4/web-host/MAF-validation shapes are historical evidence, not active alternatives.

The semantic SDK/Module layer and Runtime described above remain architecture, not work started by Lane C. Implementation status is submitted for Owner/orchestrator audit; this document does not declare Lane C closed.

## 19. Engineering invariants

1. A Module owns its application/domain meaning; Runtime coordination does not absorb it.
2. Not every Module has an Agent.
3. Every semantic Operation execution has an acting Agent continuation, but the provider Module may be agentless.
4. Operation invocation does not imply Agent delegation.
5. SPIRA values live on actual semantic facts where they have meaning; do not flatten them into one generic label.
6. Material/context contributes Sensitivity; receiving boundaries contribute Privacy; Agents/provenance contribute Integrity; concrete effect contributes Risk; current Agent continuation contributes Autonomy.
7. There is no mandatory `EffectProfile` or Agent-owned Compound in current architecture.
8. Only actual constituents participate; unrelated history, unused Operations and rejected destinations do not contaminate a construction.
9. Runtime routes/executes mechanics but does not own or semantically evaluate SPIRA.
10. ReasoningRequest remains semantic and is not mandated to contain a generic SPIRA tuple.
11. Agents create semantic ReasoningRequests, not Kernel Work.
12. Semantic MADRE derives a small physical requirement; exact provider/model/configuration selection is not required before Kernel.
13. Kernel owns durable Work, capability truth/observations, scheduling and physical execution only.
14. Kernel may use physical observations but does not become an application answer-quality evaluator.
15. Semantic continuation/persistence remains above Kernel.
16. Kernel does not own external inference-engine workers, model loading/warmness or generic RAM/VRAM lifecycle.
17. Physical multi-stage inference does not become a MADRE semantic Workflow merely because a workflow engine executes it.
18. Provided physical adapters use the same class of surface available to advanced Owners.
19. CORE is an ordinary Module with a role, not a privileged semantic subsystem.
20. Independent/generated Modules use the same public SDK as shipped software.
21. Modularity means small replaceable boundaries where a real responsibility exists, not a framework for every possible future idea.
22. Neither rejected extreme may return: Kernel-owned inference runtimes, or a Kernel so narrow that meaningful physical choice is already completed above it.
