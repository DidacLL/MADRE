# MADRE Agent Harness

MADRE is a single-Owner research/product project. Keep the active repository small, explicit, understandable and reversible.

## Mandatory reading and authority

Before substantial MADRE work, read `NORTH_STAR.md` first and answer its eight questions in the context of the task. This is a recovery gate: if the answers do not remain clear, stop deriving architecture from implementation and recover product meaning before continuing.

Then use this authority order:

1. current Owner request;
2. `docs/product/lane-c-owner-decision.md` for the accepted Lane C / DRE / physical-inference correction and the reasons behind it;
3. `docs/product/owner-intent-corpus.md` for detailed product meaning and causal reasoning not superseded by the more recent Lane C decision;
4. `NORTH_STAR.md` as the concise anti-drift checkpoint;
5. `MADRE.md` for the detailed cross-repository product/semantic overview;
6. `docs/architecture/security-algebra.md` for operational SPIRA semantics;
7. `docs/architecture/mid-level-architecture.md` for accepted whole-system engineering boundaries;
8. `docs/architecture/kernel.md` for the current closed physical Kernel architecture and implementation;
9. active implementation/tests/manual verification as evidence of what exists.

`docs/architecture/kernel-handoff.md` is the operational Lane C closure handoff for Lane A, Lane B and anti-drift agents. It records closure evidence, concrete Kernel defects found during verification, next-lane integration constraints and the manual verification policy. It is not a product authority above the list above.

`NORTH_STAR.md` is deliberately short. It does not replace the richer authorities. The Lane C Owner decision is deliberately narrow: it supersedes older exact-invocation-before-Kernel statements where they conflict, but it does not rewrite unrelated Module, Agent, Operation, Material, Runtime, CORE, SDK or SPIRA semantics.

The original `docs/product/lane-c-owner-decision.md` text was written before the final closure audit and therefore still contains historical implementation-status wording about pending audit. Its architectural rationale remains authoritative; Lane C closure status and qualified implementation truth are recorded by `docs/architecture/kernel.md` and `docs/architecture/kernel-handoff.md` after the Owner/orchestrator closure decision.

Historical code, PRs, commits, issues, discarded documents and familiar software/AI-platform patterns are evidence only. Later Owner corrections supersede historical implementation even when historical code is more detailed. Do not restore an old abstraction merely because it once compiled, and do not preserve a corrective abstraction merely because it successfully removed an earlier drift.

## Post-Lane-C lane map

The current work split after physical Kernel closure is:

```text
Lane A = public Java 21 semantic SDK / Module construction surface
Lane B = minimal Runtime: Module environment + semantic reasoning persistence/correlation + semantic→physical bridge
Lane C = closed qualified physical Kernel
```

Lane A and Lane B consume the closed physical boundary. They do not reopen Lane C merely because another semantic carrier or Runtime implementation would be easier against a different Kernel.

## North Star gate before substantial work

Explicitly answer:

- What is MADRE?
- What is not MADRE?
- Why does MADRE exist?
- What does MADRE own and what remains Module/Agent/internal responsibility?
- What does the final Owner want?
- What should the Owner be able to inspect and edit?
- How much mandatory friction / learning curve is acceptable?
- What is the development scope for this one-Owner project?

Do not answer these from memory, historical implementation or industry convention when repository authorities are available. If an implementation choice cannot be justified from those answers, the current Owner decision, the corpus, or a concrete current need, do not import it because mature platforms usually have it.

## Product invariants

- MADRE is an owner-controlled environment for using and creating AI-native software.
- DRE and domain-aware composition are core product ideas.
- Modules are independently installable application/domain boundaries.
- Not every useful capability is a Module and not every Module has an Agent.
- Agent is semantic actor; Operation is bounded action.
- An agentless Module may expose Operations invoked by another Agent.
- Cross-Module Operation invocation does not imply Agent delegation.
- CORE is an ordinary Module assigned a role; it does not own other Modules/Agents.
- The public SDK is the construction surface for shipped, independent and eventually generated Modules.
- Owner sovereignty applies at every layer.
- The Owner's models/providers/runtimes remain independent inference mechanisms; Kernel must not rematerialize them as a MADRE-owned worker/engine ontology.
- Kernel is expected to know configured physical `InferenceCapability` facts, current physical state and provenance-preserving observations because DRE uses that knowledge.
- The opposite extreme is also rejected: semantic MADRE must not completely resolve provider/model/configuration before Kernel when doing so removes meaningful physical DRE choice.

## Modules, Agents, Operations, Skills and planning

A **Module** is an independently installable application/domain semantic boundary. It owns its domain state, persistence, domain types, integrations, optional UI, optional Agents and domain-specific behavior. A Module may be agentless, UI-less, very small, or a thin binding around existing software.

An **Agent** is a semantic reasoning actor. An **Operation** is bounded executable behavior. Another Agent can invoke an Operation exposed by an agentless Module without transferring semantic continuation. Agent-to-Agent delegation is explicit and different.

A **Skill** is reusable know-how. A **Workflow** is reusable Agent behavior. A **WorkPlan** is objective-specific semantic planning that may coordinate Agents/Workflows. None of these become Kernel scheduling concepts merely because they can involve delayed reasoning.

Runtime may transport and activate these surfaces. Runtime does not acquire the application meaning merely because it routes a call.

## SPIRA

Do not reduce SPIRA to a five-row values table, generic security context, central evaluator or Agent-owned Compound.

The direct semantic mapping is:

```text
Material/context representation       -> Sensitivity
actual receiving/exposure boundary    -> Privacy
actual Agent/provenance participant   -> Integrity
actual selected Operation/effect      -> Risk
current acting Agent continuation     -> Autonomy
```

Values:

```text
Sensitivity
0 SYSTEM_RESERVED
1 TRIVIAL
2 SHARED
3 PROFILING
4 SENSITIVE
5 SECRET

Privacy
0 SYSTEM_RESERVED
1 PUBLIC
2 UNKNOWN
3 LOCAL
4 MODULE
5 ISOLATED

Integrity
0 SYSTEM_RESERVED
1 NOT_DECLARED
2 DECLARED
3 TRUSTED
4 ACCEPTED
5 VALIDATED

Risk
0 SYSTEM_RESERVED
1 READ
2 WRITE
3 DELETE
4 EXECUTE
5 POTENTIALLY_HARMFUL

Autonomy
0 SYSTEM_RESERVED
1 LIVE_INTERACTION
2 ASK_ALWAYS
3 ASK_ONCE
4 ACKNOWLEDGE
5 AUTONOMOUS
```

Same-facet accumulation is:

```text
Sensitivity = max(actual participating Sensitivities)
Privacy     = min(actual participating Privacies)
Integrity   = min(actual participating Integrities)
```

Risk is the Risk of the concrete Operation/effect actually selected. Autonomy is the current acting Agent continuation state. There is no mandatory `EffectProfile`, `CompoundSecurity`, `Authorization`, `PolicyDecision` or central evaluator object in the current architecture.

Where the actual semantic construction makes the relations relevant:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)

min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)

R_actual_operation
    <= min(I_actual_effect_realizers)
```

These are intrinsic composition relations, not permission from a central service. Only actual constituents participate. Unused Operations, rejected destinations, possible outputs, every installed capability and unrelated branches do not contaminate the current construction.

A transformed/minimised representation is new Material; do not relabel its source. The Agent decides what to try and reacts to the resulting composition. It does not own, manage or rewrite the algebra. A receiving Module may apply bounded domain strategies because it owns domain meaning. Runtime does not become the SPIRA evaluator because it transports the call.

Detailed authority: `docs/architecture/security-algebra.md`.

## ReasoningRequest and semantic-to-physical inference

A `ReasoningRequest` is semantic and is created by an Agent. It may involve relevant context, Material, provenance, Owner instruction, SPIRA facts and the actual reasoning objective.

Do not invent or restore a mandatory `ReasoningRequest.S/P/I/R/A` tuple. Risk remains with the actual Operation/effect; Autonomy remains with the actual Agent continuation; other facets remain on the actual semantic facts that contribute them.

Agents do not construct Kernel `PhysicalInferenceWork` directly. Semantic MADRE derives a small physical inference requirement from the actual reasoning need and constraints. It may include prepared input, requested result characteristics, desired reasoning effort, context characteristics, urgency, acceptable delay/deadline, modality requirements, hard restrictions derived from semantic composition and explicit Owner preferences.

The exact public carrier is Lane A design work. Its installed persistence/correlation, semantic continuation and transport into `madre-kernel-client` are Lane B work. Do not freeze a giant request object or restore a provider-specific executor API merely to make the boundary concrete.

```text
ReasoningRequest + actual semantic context/preferences
        ↓
semantic derivation of physical inference requirement
        ↓
PhysicalInferenceWork
        ↓
Kernel DRE chooses physical capability / timing / effort / strategy
```

Module, Agent, MADRE Workflow/WorkPlan, Operation semantics, raw SPIRA as a Kernel policy object and semantic continuation do not cross this boundary.

## Runtime and CORE

Runtime is the installed semantic environment. Its mechanics include Module installation/configuration, CORE role assignment, live Module discovery/lifecycle/addressing, cross-Module Operation routing, Agent-delegation routing, inspection/diagnostics, semantic request persistence/correlation where needed, correlation with Kernel Work/results, continuation/recovery support and transport of the derived physical inference requirement.

Runtime provides mechanics. It does not acquire Module domain meaning, become a global semantic scheduler, or become the semantic evaluator of SPIRA.

CORE is an ordinary Module assigned the CORE role. It may provide useful Owner interaction, default/meta behavior, default Agents and reusable generic behavior. CORE is not Runtime, Kernel, owner of other Modules, or a special SPIRA authority.

## Kernel / DRE

Kernel is the durable physical inference substrate. Its MADRE-owned physical concepts include:

```text
PhysicalInferenceWork
InferenceCapability
configured/declared capability facts
current capability state
provenance-preserving capability observations/evidence
DRE inference-aware scheduling/strategy
physical attempts/results/recovery
```

A provider declaration, an Owner declaration and a MADRE observation are distinct evidence and must not be silently collapsed into one mutable truth.

Kernel/DRE may decide when to run, which admissible capability to use, whether to wait for availability, how much physical effort to spend and which current physical evidence should affect selection. Physical multi-stage strategies remain legitimate when a real product strategy needs them, but Kernel does not become an application semantic evaluator. A physically valid result is not retried merely because Kernel believes the answer is weak.

Kernel remains ignorant of Module semantics, MADRE Agent semantics, Operation semantics, Material meaning, ReasoningRequest semantics, SPIRA as semantic policy, CORE semantics, MADRE Skill/Workflow/WorkPlan semantics, semantic continuation and semantic persistence. A Module may privately use external intelligence without shared Kernel involvement.

## Physical binding openness and first-party parity

Kernel leaves an open physical binding seam for common and unusual Owner-selected inference systems. Useful realizations may include generic provider interoperability, OpenAI-compatible endpoints, protocol bindings, process/script wrappers, A2A/remote intelligence and future Owner-defined mechanisms when concrete needs justify them.

Do not build a connector marketplace, hot-loader or defensive plugin prison without a real need. An unusual Owner-controlled inference environment should normally be integrable through configuration or a binding rather than provider-specific edits to Kernel architecture.

MADRE-provided inference conveniences use the same class of physical construction surface available to advanced Owners. No provider/runtime receives a privileged Kernel ontology or hidden first-party lifecycle. MEAI may provide interoperability behind the same seam. MAF or another workflow mechanism may be used by a future actual physical strategy only when a concrete strategy earns that machinery; no current checkpoint/strategy scaffolding is retained speculatively.

## Current Lane C implementation

Lane C is closed and qualified. The active tree contains one current physical implementation under `kernel/` plus the Java 21 `madre-kernel-client`:

- cross-platform .NET Kernel with one physical process owner per database, including Linux path/symlink aliases;
- SQLite-authoritative durable physical Work and attempt history with explicit current schema identity;
- configured/current/observed `InferenceCapability` truth;
- capability-aware DRE using effort/boundary admissibility, availability, Owner preference and current-binding latency evidence where implemented;
- valid zero-capability startup and optional configuration;
- asynchronous startup observation and demand-driven re-observation of unavailable capabilities; probe timeout means `Unknown`, not `Unavailable`;
- current configured capability catalogue reconciled on restart while historical attempts remain historical;
- wake/deadline-driven scheduling over metadata-only candidates, loading physical payload only after claim;
- urgency-preserving dispatch when physical slots are released during scheduler traversal;
- typed physical failure vocabulary with separate technical detail;
- one common physical binding-execution responsibility;
- shell-free explicitly UTF-8 process binding, MEAI interoperability and the open `IInferenceBinding` seam;
- a versioned bounded local IPC protocol over Unix-domain sockets for .NET↔Java 21 on Windows/Linux, with bounded server handlers and bounded Java calls;
- strict IPC/config/CLI parsing rather than silent defaults;
- supervised Kernel-owned scheduler/probe/execution persistence: fatal infrastructure failure terminates/fails the host instead of leaving a zombie healthy Kernel;
- bounded concurrency, caller disappearance, eligibility/deadlines, cancellation, retained results/release and restart `UnknownCompletion` behavior;
- manual/on-demand Linux and Windows regression, qualification, stress and soak verification through `.github/workflows/kernel-verification.yml`.

The executable closure baseline is `95ddf2250c27d28e90e215391223164965b68729`; full cross-platform requalification is GitHub Actions run `36493627377`. The later closure-policy commit removed obsolete automatic Lane C CI without changing executable behavior.

There is no TCP/loopback web control plane, configurable port, ASP.NET host dependency, production MAF workflow/checkpoint strategy, checkpoint state, MAF package dependency, packaged Java CLI containing test classes, or compatibility/migration layer for pre-release databases.

Lane A and Lane B may now proceed against this physical boundary. Do not start or restore semantic SDK/Runtime code while working a Kernel-only task, and do not reopen Kernel during Lane A/B merely because a semantic implementation would prefer another physical architecture. Read `docs/architecture/kernel-handoff.md` first if a later lane believes Kernel must change.

## SDK as generation target

A capable development AI should eventually be able to use the public SDK plus a domain requirement to generate, build, test and install an ordinary Module locally. The builder should not be required for normal runtime execution after the software exists.

A future BuilderModule may be deferred. The requirement that the public SDK be small, explicit and sufficient is current. Generated Modules must not need hidden first-party hooks, Runtime internals, Kernel internals or undocumented conventions.

## Development style

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Prefer working behavior, explicit contracts and behavioral evidence over speculative abstractions, compatibility fossils, universal registries or framework-within-framework designs.

Simplification must not erase semantic ownership or meaningful DRE. Removing a concrete carrier, causal relation or meaningful boundary is architecture drift; so is narrowing Kernel until all meaningful physical inference choice has already happened elsewhere.

Commit and push coherent behavior. Keep implementation truth, documentation truth and verification evidence aligned. Lane C closure is an Owner/orchestrator decision already recorded in the active Kernel architecture and handoff; green tests support that decision but do not become architectural authority.
