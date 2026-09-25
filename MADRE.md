# MADRE

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

This file is the concise cross-repository product definition. The full product reasoning is in `docs/product/owner-intent-corpus.md`; the accepted whole-system engineering design is in `docs/architecture/mid-level-architecture.md`; the normative operational Security Algebra is in `docs/architecture/security-algebra.md`.

Current Owner instructions override repository documentation when they are more specific. Historical code, tests, PRs, commits and familiar software/AI-platform patterns are evidence only.

## Product thesis

MADRE is built around the idea that useful application capability does not have to equal one synchronous frontier-model invocation.

Domain knowledge, deterministic bounded Operations, reusable Agents/Skills, accumulated state, decomposition and **Delayed Reasoning Effort** can make local/open intelligence sufficient for a large part of ordinary user work. Stronger external inference remains available selectively when it adds real value or the Owner wants it.

Provider independence is therefore achieved by keeping the application and reasoning architecture in the Owner's environment while treating models/providers as resources. Privacy and data ownership follow strongly from needing to export less information in the first place.

## Owner sovereignty

The Owner owns the complete installation and is never MADRE's adversary.

MADRE may provide minimisation, SPIRA composition, warnings, diagnostics, recovery and useful defaults. Those mechanisms serve the Owner; they do not create an authority above the Owner.

The Owner may inspect, modify, replace or experiment with Modules, Runtime behaviour, CORE assignment, inference configuration, Kernel data and implementation, generated software and source code.

## SDK and Modules

The public MADRE SDK is a primary product and the ordinary construction surface for shipped, independent and eventually AI-generated MADRE software.

A **Module** is an independently installable application/domain semantic boundary. It owns its domain state, persistence, types, UI/integrations and any internal implementation. It may expose bounded Operations, provide optional Agents/Skills and exchange meaningful Material/context.

A Module does not have to contain an Agent. It may be tiny, UI-less, or a thin binding around existing software.

Reusable behaviour such as research, search, generation, embeddings, file access or calendar access does not become a Module merely because it is useful.

An **Operation** is bounded executable behaviour. An **Agent** is a semantic reasoning actor. A **Skill** is reusable know-how. A **Workflow** is reusable Agent behaviour. A **WorkPlan** is objective-specific semantic planning that may coordinate multiple Agents/Workflows.

## SPIRA

MADRE's Security Algebra is intrinsic semantic composition, not a policy/authorization subsystem. Its operational structure is part of the public SDK design and must not be reduced to a generic five-value label.

The established carriers are:

```text
Material                          -> Sensitivity
Operation accepted Material type -> Privacy
Operation produced Material type -> maximum promised Sensitivity
Agent / actual causal participant -> Integrity
actual effect realizer           -> Integrity when it participates
selected Operation EffectProfile -> Risk + Autonomy
```

Values are:

```text
Sensitivity: SYSTEM_RESERVED, TRIVIAL, SHARED, PROFILING, SENSITIVE, SECRET
Privacy:    SYSTEM_RESERVED, PUBLIC, UNKNOWN, LOCAL, MODULE, ISOLATED
Integrity:  SYSTEM_RESERVED, NOT_DECLARED, DECLARED, TRUSTED, ACCEPTED, VALIDATED
Risk:       SYSTEM_RESERVED, READ, WRITE, DELETE, EXECUTE, POTENTIALLY_HARMFUL
Autonomy:   SYSTEM_RESERVED, LIVE_INTERACTION, ASK_ALWAYS, ASK_ONCE, ACKNOWLEDGE, AUTONOMOUS
```

Same-dimension composition is `max(Sensitivity)`, `min(Privacy)` and `min(Integrity)`. Risk and Autonomy are not generic aggregates: they remain paired on the one exact `EffectProfile` selected for a consequential Operation call.

An `EffectProfile` belongs to an Operation. A consequential call selects exactly one declared profile; a non-consequential Operation does not receive dummy Risk/Autonomy values.

The three distinct comparisons are:

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

The compound has no owner or manager. It exists inherently because the actual semantic constituents compose. Agents encounter that structure and react; they do not manage or rewrite it.

A `ReasoningRequest` accumulates only the actual Sensitivity, Privacy and Integrity constituents of that reasoning computation. It does not automatically inherit a surrounding Operation's Risk/Autonomy merely because an EffectProfile exists; those values matter when that consequential effect is actually selected and realized.

Detailed normative model: `docs/architecture/security-algebra.md`.

## Runtime

MADRE Runtime is the installed semantic environment around the SDK and Kernel.

It owns installation-wide mechanics such as Module installation/configuration, CORE role assignment, live discovery, lifecycle/activation, addressing, cross-Module invocation/delegation, deterministic SPIRA composition at Runtime-coordinated semantic boundaries, semantic reasoning persistence/correlation, continuation support, inspection and diagnostics.

Runtime coordination does not make Runtime the owner of Module domains, Agent semantics or SPIRA values.

### ReasoningRequest → Work

Agents create semantic `ReasoningRequest`s. They do not construct Kernel Work directly.

The SDK defines a bounded special Operation/function:

```text
ReasoningRequest + execution preferences/declarations
    -> physical Work requirements
```

Runtime executes this required function through an installation-selected implementation. The shipped default implementation belongs to the ordinary Module assigned CORE.

The Owner may replace, wrap or decorate that implementation—for example to insert a logging/learning experiment—without changing Agent or Kernel semantics.

The implementation may resolve semantic constraints before the boundary, but the resulting Kernel Work contains only physical requirements. SPIRA does not cross into Kernel.

## CORE

CORE is an ordinary Module assigned the CORE installation role.

It can provide ordinary Owner interaction, default/meta behaviour, default Agents and the shipped implementations of MADRE-required Module-owned Runtime Operations.

CORE is not Runtime, Kernel, owner of other Modules or a special SPIRA authority.

## Kernel

Kernel owns physical inference Work only.

It handles durable Work, technical engine matching, scheduling/eligibility, resources, worker supervision, retry/cancellation and terminal technical results.

Kernel must remain blind to Module, Agent, Operation/EffectProfile semantics, Material, ReasoningRequest, SPIRA, CORE, Workflow/WorkPlan semantics and semantic continuation.

The Kernel does not depend on the semantic SDK.

The same replaceability philosophy still applies below the boundary: physical journeys should remain bounded enough for Owner experiments or alternate implementations, including inference-assisted physical routing, without importing semantic MADRE concepts into Kernel.

Current public Kernel architecture: `docs/architecture/kernel.md`.

## Development character

MADRE is a one-Owner research/product project developed heavily with AI assistance.

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

The architecture should remain simple enough that capable AI development systems can generate ordinary Modules against the public SDK and install them locally. A builder may be deferred; being a reliable generation target is an architectural requirement now.

Do not add enterprise decomposition, speculative plugin frameworks, compatibility machinery without users, hidden first-party semantic APIs or abstractions imported only because conventional AI platforms contain them.

Do not simplify an established semantic contract by deleting the ownership/application points that make it operational. Simplicity in MADRE means small explicit concepts, not vague generalisation.

## Current implementation state

`main` contains the accepted native Kernel/Lane C implementation and its Java physical client.

Implemented and validated:

- native C++ Kernel;
- durable physical Work;
- scheduling/restart recovery;
- worker lifecycle/resource accounting;
- local IPC and cross-platform hardening;
- Java `madre-kernel-client`;
- native llama.cpp worker path.

The semantic SDK/Module and Runtime layers described above are accepted architecture but are **not yet implemented in the active tree**. Do not resurrect discarded historical Python or previous generated semantic implementations to hide that gap.

Historical semantic code remains evidence for recovering already-settled contracts such as `EffectProfile`, Operation-bound Privacy/output Sensitivity and ReasoningRequest S/P/I composition. Evidence does not make that discarded implementation current code.

## Authority

For product meaning and engineering semantics, read in this order:

1. current Owner instruction;
2. `docs/product/owner-intent-corpus.md`;
3. this file;
4. `docs/architecture/security-algebra.md` for the operational SPIRA contract;
5. `docs/architecture/mid-level-architecture.md` for accepted whole-system engineering boundaries;
6. `docs/architecture/kernel.md` for current Kernel architecture;
7. implementation/tests as evidence.

`docs/architecture/lane-c-native-kernel.md` points to the current architecture and the archived original Lane C implementation contract. Historical upstream semantic examples do not override the current documents.