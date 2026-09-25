# MADRE Agent Harness

MADRE is a single-Owner research/product project. Keep the active repository small, explicit, understandable and reversible.

## Authority

Use this order:

1. current Owner request;
2. `docs/product/owner-intent-corpus.md` for product meaning;
3. `MADRE.md` for concise cross-repository invariants;
4. `docs/architecture/security-algebra.md` for operational SPIRA semantics;
5. `docs/architecture/mid-level-architecture.md` for accepted whole-system engineering boundaries;
6. `docs/architecture/kernel.md` for current Kernel architecture;
7. active implementation/tests/CI as evidence of what exists.

Historical code, PRs, commits, issues, discarded documents and familiar software/AI-platform patterns are evidence only. Later Owner corrections supersede historical implementation even when the historical code is more detailed.

Do not restore an old abstraction merely because it once compiled.

## North Star before substantial work

Be able to answer:

- What is MADRE?
- What is not MADRE?
- Why does MADRE exist?
- What does MADRE own and what remains Module/Agent/internal responsibility?
- What does the final Owner want?
- What should the Owner be able to inspect and edit?
- How much mandatory friction is acceptable?
- What is the development scope for this one-Owner project?

If an implementation choice cannot be justified from those answers or a concrete current need, do not import it because mature platforms usually have it.

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

## Reasoning-to-physical boundary

Agents do not construct Kernel Work directly.

The SDK defines a bounded required function/Operation:

```text
ReasoningRequest + execution preferences/declarations
    -> physical Work requirements
```

Runtime executes the installation-selected implementation. The implementation belongs to a Module; shipped CORE provides the default; the Owner can replace/wrap/decorate it.

Runtime supplies mechanics. It does **not** own Module semantics or evaluate SPIRA as a central subsystem.

The configured implementation can use the semantic request and factual engine descriptions to choose acceptable physical constraints. Only those physical constraints enter Kernel.

## Kernel

Kernel owns already-physical inference Work only.

It must remain ignorant of:

```text
Module
Agent
Operation semantics
Material
ReasoningRequest
SPIRA / Sensitivity / Privacy / Integrity / Risk / Autonomy
CORE
Skill
Workflow / WorkPlan semantics
semantic continuation
semantic persistence
```

`madre-kernel-client` must remain physical and must not depend on `madre-sdk`.

Kernel reports factual physical engine properties; semantic software above the boundary interprets those facts. Kernel does not derive Privacy/Integrity from locality, provider or model identity.

Kernel implementation should remain adaptable/replaceable where a real physical experiment needs it. An inference-assisted physical router is acceptable if it reasons only over physical Kernel facts.

Do not build a speculative generic extension framework merely to prove openness.

## Current tree

The active tree contains the implemented Lane C/native Kernel foundation and Java physical client.

The semantic SDK/Module layer and MADRE Runtime are accepted architecture but are not yet implemented. Do not restore discarded Python or previous generated semantic code to make the repository look complete.

Historical semantic code can help recover reasoning, but when it conflicts with later Owner corrections—especially `EffectProfile`, Runtime-owned algebra evaluation or a fixed ReasoningRequest algebra payload—the later Owner model wins.

## Development style

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Prefer working behaviour, strong explicit contracts and behavioral evidence over speculative abstractions, compatibility fossils, universal registries or framework-within-framework designs.

A future AI builder should be able to construct an ordinary Module using the public SDK without hidden first-party knowledge.

**Simplification must not erase semantics.** Removing a concrete carrier, causal relation or meaningful boundary is architecture drift, not simplification.

Commit and push coherent behaviour. Keep implementation truth, documentation truth and CI evidence aligned.