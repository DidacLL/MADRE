# MADRE Agent Harness

MADRE is a single-Owner research/product project. Keep the active repository small, explicit, understandable and reversible.

## Mandatory reading and authority

Before substantial MADRE work, read `NORTH_STAR.md` first and answer its eight questions in the context of the task. This is a recovery gate: if the answers do not remain clear, stop deriving architecture from implementation and recover product meaning before continuing.

Then use this authority order:

1. current Owner request;
2. `docs/product/lane-c-owner-decision.md` for the accepted current Lane C / DRE / physical-inference correction and the reasons behind it;
3. `docs/product/owner-intent-corpus.md` for the detailed product meaning and causal reasoning not superseded by the more recent Lane C decision;
4. `NORTH_STAR.md` as the concise anti-drift checkpoint;
5. `MADRE.md` for the detailed cross-repository product/semantic overview;
6. `docs/architecture/security-algebra.md` for operational SPIRA semantics;
7. `docs/architecture/mid-level-architecture.md` for accepted whole-system engineering boundaries;
8. `docs/architecture/kernel.md` for current target Kernel architecture;
9. active implementation/tests/CI as evidence of what exists.

`NORTH_STAR.md` is deliberately short. It does not replace the richer authorities. `docs/product/lane-c-owner-decision.md` is deliberately narrow: it supersedes older exact-invocation-before-Kernel statements where they conflict, but it does not rewrite unrelated Module, Agent, Operation, Material or SPIRA semantics.

Historical code, PRs, commits, issues, discarded documents and familiar software/AI-platform patterns are evidence only. Later Owner corrections supersede historical implementation even when the historical code is more detailed.

Do not restore an old abstraction merely because it once compiled, and do not preserve a corrective abstraction merely because it successfully removed an earlier drift.

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

Do not answer these from memory, historical implementation or industry convention when the repository authorities are available.

If an implementation choice cannot be justified from those answers, the current Owner decision, the corpus, or a concrete current need, do not import it because mature platforms usually have it.

## Product invariants

- MADRE is an owner-controlled environment for using and creating AI-native software.
- DRE and domain-aware composition are core product ideas.
- Modules are independently installable application/domain boundaries.
- Not every useful capability is a Module.
- Not every Module has an Agent.
- Agent is semantic actor; Operation is bounded action.
- An agentless Module may expose Operations invoked by another Agent.
- Cross-Module Operation invocation does not imply Agent delegation.
- CORE is an ordinary Module assigned a role; it does not own other Modules/Agents.
- The public SDK is the construction surface for shipped, independent and eventually generated Modules.
- Owner sovereignty applies at every layer.
- The Owner's models/providers/runtimes remain independent inference mechanisms; Kernel must not rematerialize them as a MADRE-owned worker/engine ontology.
- Kernel is allowed and expected to know configured physical `InferenceCapability` facts, current physical state and provenance-preserving observations because DRE uses that knowledge to schedule physical inference.
- The opposite extreme is also rejected: semantic MADRE must not completely resolve provider/model/configuration into an exact concrete invocation before Kernel if that prevents Kernel DRE from choosing physical capability/effort/strategy.

## SPIRA

Do not reduce SPIRA to a five-row values table, generic security context, central evaluator or Agent-owned Compound.

The current direct semantic mapping is:

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

Risk is the Risk of the concrete Operation/effect actually selected. Autonomy is the current acting Agent continuation state.

**There is no mandatory `EffectProfile` in the current architecture.** Historical Java/SDK work that paired Risk and Autonomy in `EffectProfile` was superseded by the later Owner model. Do not restore it by historical inertia.

There is no mandatory `CompoundSecurity`, `Authorization`, `PolicyDecision` or central evaluator object either.

Where the actual semantic construction makes the relations relevant:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)

min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)

R_actual_operation
    <= min(I_actual_effect_realizers)
```

Do not turn these into a permission/authorization service. They are intrinsic composition relations of the actual constituents.

Only actual constituents participate. Unused Operations, rejected destinations, possible outputs, every installed capability and unrelated branches do not contaminate the current construction.

A transformed/minimised representation is new Material. Do not relabel the source.

The Agent decides what to try and reacts to the resulting composition. It does not own/manage/rewrite the algebra.

A receiving Module can apply bounded domain strategies and local facts because it owns its domain meaning. Runtime does not become the SPIRA evaluator because it transports the call.

Detailed authority: `docs/architecture/security-algebra.md`.

## ReasoningRequest

A ReasoningRequest is semantic and is created by an Agent.

It may involve relevant context, Material, provenance, Owner instruction, SPIRA facts and the reasoning objective.

Do **not** invent or restore a mandatory `ReasoningRequest.S/P/I/R/A` tuple unless the Owner explicitly specifies one. Historical implementations that stored accumulated S/P/I are evidence from an earlier API, not current architecture authority.

Risk remains with the actual Operation/effect. Autonomy remains with the actual Agent continuation.

The responsible semantic Agent/Module context establishes what reasoning is needed and what current semantic construction is valid.

ReasoningRequest does not cross into Kernel as a semantic object.

## Semantic-to-physical inference boundary

Agents do not construct Kernel `PhysicalInferenceWork` directly.

Semantic MADRE derives a small physical inference requirement from the actual reasoning need and constraints. It may include facts such as prepared input, requested result characteristics, desired reasoning depth/effort, context characteristics, urgency, acceptable delay/deadline, modality requirements, hard restrictions derived from semantic composition and explicit Owner preferences.

The exact public carrier and the SDK/Runtime implementation journey remain design work. Do not freeze a giant request object or restore a provider-specific executor API merely to make the boundary concrete.

The invariant is:

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

Semantic MADRE must not preselect an exact provider/model/configuration merely because the current C++ implementation accepts `ConcretePhysicalInvocation` candidates. That exact-invocation contract is implementation evidence from an over-corrective Lane C stage, not the target boundary.

## Kernel / DRE

Kernel is the durable physical inference control plane.

Its MADRE-owned physical concepts include:

```text
PhysicalInferenceWork
InferenceCapability
configured/declared capability facts
current capability state
provenance-preserving capability observations/evidence
DRE inference-aware scheduling/strategy
physical attempts/results/recovery
```

Kernel may use declared and observed physical facts such as locality/exposure boundary, modalities, context characteristics, reasoning features, cost, reachability, latency, throughput, error/failure rates, availability history and explicitly supplied benchmark/evaluation evidence where a DRE strategy understands it.

A provider claim, an Owner declaration and an observation made by MADRE are distinct evidence and must not be silently collapsed into one authoritative mutable field.

Kernel/DRE may decide when to run, which admissible capability to use, how much physical effort to spend, whether to defer, whether a richer physical strategy is worthwhile, whether physically justified retry/escalation is appropriate and whether a checkpointed physical strategy should resume.

Kernel does **not** become an application semantic evaluator. A physically valid inference result is not retried merely because Kernel believes the answer is weak or does not solve the user's task. Semantic dissatisfaction belongs to the consuming Agent/Module.

Kernel must remain ignorant of:

```text
Module semantics
MADRE Agent semantics
Operation semantics
Material meaning
ReasoningRequest semantics
SPIRA as semantic objects/policy
CORE semantics
MADRE Skill / Workflow / WorkPlan semantics
semantic continuation
semantic persistence
```

A Module may privately use external intelligence without using the shared Kernel. Kernel is not a universal interceptor.

## Physical strategies and framework containment

A DRE strategy may legitimately use several inference capabilities, physical validators, fan-out/fan-in, comparison or synthesis. That is physical inference orchestration, not automatically a MADRE semantic Workflow.

Physical workflow machinery must not silently acquire application effects such as changing Module state, sending application email, committing domain/project state or invoking another Module's semantic Operation as though those were mere inference internals.

Microsoft.Extensions.AI and Microsoft Agent Framework are leading implementation tools, not MADRE ontology:

```text
MADRE Agent         != MAF AIAgent
MADRE Workflow      != MAF Workflow
InferenceCapability != IChatClient
InferenceCapability != MAF AIAgent
InferenceCapability != MAF Workflow
DRE                  != MAF
```

MEAI may back an inference binding. MAF Workflows may execute a physical strategy selected by DRE where graph/checkpoint machinery is actually useful. Simple physical inference must not be forced through MAF merely because it exists.

## Physical binding openness and first-party parity

Kernel must leave an open physical binding seam for common and unusual Owner-selected inference systems. Useful realizations can include MEAI/provider integrations, OpenAI-compatible endpoints, HTTP/protocol bindings, process/script wrappers, A2A/remote intelligence and future Owner-defined mechanisms.

Do not build a connector marketplace, hot-loader or defensive plugin prison without a real need. The requirement is that unusual Owner-controlled inference should normally be integrable through configuration or a binding rather than provider-specific edits to Kernel architecture.

MADRE-provided inference conveniences must use the same class of physical construction surface available to advanced Owners. No provider/runtime receives a privileged Kernel ontology or hidden first-party lifecycle.

## Durable physical truth

MADRE keeps one authoritative durable `PhysicalInferenceWork` lifecycle. SQLite is the current leading local persistence choice unless a real requirement demonstrates otherwise.

Framework state is subordinate. If a MAF Workflow is used, its checkpoint is physical strategy execution state referenced by MADRE Work; it does not independently decide whether the Work exists, is cancelled, terminal or resumable.

Durable strategy/binding identity must be sufficient to detect incompatible continuation after upgrades rather than silently restoring old checkpoint state into a changed implementation.

Loss of certainty about an active physical attempt remains an important truthfulness requirement. `UNKNOWN_COMPLETION` from the current reference implementation is evidence for the replacement: interruption must not be rewritten as definite failure merely for convenience.

## Current tree and target implementation

The active correction branch contains the C++ LCR1–LCR3 physical Kernel and Java client. That implementation successfully removed the rejected worker/engine/model-runtime ownership architecture and established useful physical behavior including durability, caller disappearance, deadlines, cancellation, attempt history, conservative unknown-completion semantics, terminal payload release, process/HTTP escape hatches and Linux/Windows behavior.

It is now a **reference implementation/test oracle**, not the target Lane C architecture.

Do not continue hardening the exact-invocation C++ design merely because it exists, except where needed to preserve evidence for the replacement.

The leading target implementation candidate is a cross-platform .NET Kernel with:

- MADRE-owned `PhysicalInferenceWork`, `InferenceCapability`, DRE and observations;
- SQLite as the initial authoritative durable state;
- Microsoft.Extensions.AI as useful inference interoperability infrastructure;
- selective Microsoft Agent Framework workflow/checkpoint machinery where a concrete physical strategy needs it;
- generic protocol/HTTP/process and Owner-defined physical bindings;
- no assumed Quartz/Wolverine/Elsa/Temporal layer unless later evidence makes one simpler than the small MADRE-owned durable substrate.

The semantic SDK/Module layer and Runtime remain accepted architecture but are not yet implemented in the active tree. Do not restore discarded Python or previous generated semantic code to make the repository look complete.

Historical semantic code can help recover reasoning, but when it conflicts with later Owner corrections—especially `EffectProfile`, Runtime-owned algebra evaluation, fixed ReasoningRequest algebra payloads or exact-invocation-before-Kernel inference selection—the later Owner model wins.

## Development style

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Prefer working behaviour, strong explicit contracts and behavioral evidence over speculative abstractions, compatibility fossils, universal registries or framework-within-framework designs.

A future AI builder should be able to construct an ordinary Module using the public SDK without hidden first-party knowledge. An advanced Owner should likewise be able to extend physical inference through the same class of seams used by provided integrations.

**Simplification must not erase semantics or DRE.** Removing a concrete carrier, causal relation or meaningful boundary is architecture drift; so is narrowing Kernel until all meaningful physical inference choice has already happened elsewhere.

Commit and push coherent behaviour. Keep implementation truth, documentation truth and CI evidence aligned.
