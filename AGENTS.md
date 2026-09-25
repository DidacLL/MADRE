# MADRE Agent Harness

MADRE is a single-Owner research/product project. Keep the active repository small, explicit, understandable and reversible.

## Authority

Use this order:

1. the current Owner request;
2. `docs/product/owner-intent-corpus.md` for product meaning;
3. `MADRE.md` for concise cross-repository invariants;
4. `docs/architecture/security-algebra.md` for the operational SPIRA contract;
5. `docs/architecture/mid-level-architecture.md` for accepted whole-system engineering boundaries;
6. `docs/architecture/kernel.md` for the current Kernel architecture;
7. implementation/tests/CI as evidence about what exists.

Historical code, PRs, commits, issue discussions, discarded documents and familiar software/AI-platform patterns are evidence only. Do not reconstruct MADRE by repetition or convention.

Historical code may still preserve already-settled semantics that were accidentally omitted from a newer summary. Recover such semantics only when they are supported by Owner discussion/current direction; do not restore historical implementation topology wholesale.

`docs/architecture/lane-c-native-kernel.md` points to the current Kernel architecture and the archived original Lane C implementation contract.

## North Star before substantial work

Before changing MADRE, be able to answer:

- What is MADRE?
- What is not MADRE?
- Why does MADRE exist?
- What does MADRE own and what remains Module/Agent/internal responsibility?
- What does the final Owner actually want?
- What should the Owner be able to inspect and edit?
- How much mandatory friction/learning curve is acceptable?
- What is the development scope for this one-Owner project?

If an implementation choice cannot be justified from those answers or a concrete lane need, do not import it merely because mature platforms usually have it.

## Product invariants

- MADRE is an owner-controlled environment for using and creating AI-native software.
- DRE and domain-aware composition are core product ideas, not optional background-job decoration.
- Modules are independently installable application/domain boundaries.
- Not every useful capability is a Module.
- Not every Module has an Agent.
- Operation invocation and Agent delegation are distinct.
- CORE is an ordinary Module assigned a role; it does not own other Modules/Agents.
- The public SDK is the construction surface for shipped, independent and eventually generated Modules.
- Owner sovereignty applies at every layer: MADRE should be understandable, replaceable and experimentable without constructing authority above the Owner.

## SPIRA

Do not reduce SPIRA to a five-row values table or a generic `Compound` narrative. The operational carrier/application points are part of the architecture.

The established direct mapping is:

```text
Material                          -> Sensitivity
Operation accepted Material type -> Privacy
Operation produced Material type -> maximum promised Sensitivity
Agent / actual causal participant -> Integrity
actual effect realizer           -> Integrity when it participates
selected Operation EffectProfile -> Risk + Autonomy
```

Integrity values are:

```text
0 SYSTEM_RESERVED
1 NOT_DECLARED
2 DECLARED
3 TRUSTED
4 ACCEPTED
5 VALIDATED
```

Same-dimension accumulation is:

```text
Sensitivity = max(actual participating Sensitivity)
Privacy     = min(actual participating Privacy)
Integrity   = min(actual participating Integrity)
```

Risk and Autonomy are not generic aggregates. An Operation owns immutable `EffectProfile`s; one exact profile supplies Risk and Autonomy for one consequential invocation. Non-consequential Operations do not receive dummy profiles.

Keep the three comparisons separate:

```text
DISCLOSURE
max(S_actual_material) <= min(P_actual_receiving_path)

CONTROL
min(R_selected_profile, A_selected_profile)
    <= min(I_actual_non_user_controllers)

EFFECT EXECUTION
R_selected_profile
    <= min(I_actual_effect_realizers)
```

A `ReasoningRequest` accumulates only the actual S/P/I constituents of that reasoning computation. Do not copy Risk/Autonomy from a surrounding Operation merely because an EffectProfile exists; include effect semantics later only if reasoning output actually participates in the consequential call.

Do not create an Agent-owned or Runtime-owned `Compound` manager. The compound exists because actual semantic constituents compose. Agents react to the resulting structure; they do not own or rewrite it.

Only actual constituents participate. Derived/minimised Material is a new representation, not a relabelled source. A stronger later participant cannot wash a weaker participant that remains causally relevant.

Do not reintroduce historical `SecurityObject`/`SecurityTransition` frameworks or semantic Security Algebra inside Kernel unless the Owner explicitly changes the architecture. The current detailed authority is `docs/architecture/security-algebra.md`.

## Reasoning boundary

Agents create semantic `ReasoningRequest`s. They do not construct Kernel Work directly.

The SDK defines a bounded special reasoning executor Operation/function:

```text
ReasoningRequest + execution preferences/declarations
    -> physical Work requirements
```

Runtime executes the installation-selected implementation. The shipped default implementation belongs to the Module assigned CORE. The Owner may wrap, replace or decorate it without changing Agent or Kernel semantics.

The configured implementation resolves semantic constraints before the physical boundary and expresses the result as physical Work constraints. SPIRA, Material, Agent and EffectProfile semantics do not enter Kernel Work.

Do not turn this seam into a central reasoning authority or a generic plugin framework.

## Kernel

Kernel owns already-physical inference Work only.

It must remain ignorant of:

```text
Module
Agent
Operation / EffectProfile semantics
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

Kernel implementation should remain internally adaptable/replaceable where a real physical experiment needs it. For example, an Owner may eventually experiment with learning-assisted engine routing. Such experiments remain physical and may not import semantic MADRE concepts into Kernel.

Do not build speculative extension frameworks merely to satisfy that openness. Preserve small explicit seams and replaceable responsibilities.

## Current tree

The active tree contains the implemented Lane C/native Kernel foundation and Java physical client.

The semantic SDK/Module layer and MADRE Runtime are accepted architecture but are not yet implemented. Do not restore discarded Python or previous generated semantic code to make the repository look more complete.

The current semantic architecture intentionally recovers some already-established public concepts from historical work—especially `EffectProfile`, Operation-bound Privacy/output Sensitivity, Agent Integrity and ReasoningRequest S/P/I composition—without treating the discarded implementation as current authority.

## Development style

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Prefer working behaviour, strong explicit contracts and behavioural evidence over speculative abstractions, compatibility fossils, universal registries, framework-within-framework designs or ritual architecture policing.

A future AI builder should be able to construct an ordinary Module using the public SDK without hidden first-party knowledge. Keep public concepts simple enough to generate against and solid enough to compose predictably.

**Simplification must not erase semantics.** Removing the concrete carrier, comparison point or causal relation that gives a concept meaning is architecture drift, not simplification.

Commit and push coherent behaviour. Keep implementation truth, documentation truth and CI evidence aligned.