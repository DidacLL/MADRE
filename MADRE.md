# MADRE

MADRE — **Model-Agnostic Delayed Reasoning Effort Agentic System** — is an owner-controlled, local-first environment for using and creating AI-native software.

This file is the concise cross-repository definition. Read `docs/product/owner-intent-corpus.md` for the product reasoning, `docs/architecture/security-algebra.md` for the operational SPIRA model, `docs/architecture/mid-level-architecture.md` for the accepted whole-system design, and `docs/architecture/kernel.md` for the current physical Kernel architecture.

Current Owner instructions override repository documentation when more specific. Historical code/tests/PRs/commits and familiar AI-platform patterns are evidence only.

## Product thesis

MADRE is built around the idea that useful application capability does not have to equal one synchronous frontier-model invocation.

Domain knowledge, deterministic bounded Operations, reusable Agents/Skills, accumulated state, decomposition and **Delayed Reasoning Effort** can make local/open intelligence sufficient for a large part of ordinary user work. Stronger external inference remains available selectively when it adds real value or the Owner wants it.

The application and reasoning environment stays with the Owner. Models/providers are resources used by that environment rather than the place where the environment must live.

## Owner sovereignty

The Owner owns the complete installation and is never MADRE's adversary.

MADRE may provide minimisation, SPIRA composition, diagnostics, recovery and useful defaults. Those mechanisms serve the Owner; they do not create an authority above the Owner.

The Owner may inspect, modify, replace or experiment with Modules, Agent state, Runtime behaviour, CORE assignment, inference configuration, Kernel state/implementation, generated software and source code.

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

## ReasoningRequest and Runtime

A `ReasoningRequest` is a semantic need for reasoning created by an Agent. It can involve context, Material, provenance, Owner instruction, relevant SPIRA facts and the reasoning objective.

It is **not** required to become a generic SPIRA tuple. The facets remain on the actual semantic facts where they belong.

Agents do not construct Kernel Work directly.

The SDK defines a bounded required function/Operation:

```text
ReasoningRequest + execution preferences/declarations
    -> physical Work requirements
```

Runtime executes the installation-selected implementation. The implementation belongs to a Module; the shipped CORE Module provides the default. The Owner may wrap or replace it.

Runtime supplies installation mechanics—Module discovery/lifecycle/addressing/routing, persistence/correlation, configured execution and diagnostics. Runtime does **not** become the semantic owner/evaluator of SPIRA merely because it transports or executes calls.

## CORE

CORE is an ordinary Module assigned the CORE installation role.

It can provide ordinary Owner interaction, default/meta behaviour, default Agents and shipped implementations of required Module-owned Runtime Operations.

CORE is not Runtime, Kernel, owner of other Modules or a special SPIRA authority.

## Kernel

Kernel owns already-physical inference Work only.

It handles durable Work, physical engine matching, scheduling/eligibility, resource accounting, worker supervision, retry/cancellation and terminal technical results.

Kernel must remain blind to Module, Agent, Operation semantics, Material, ReasoningRequest, SPIRA, CORE, Workflow/WorkPlan semantics and semantic continuation.

Factual engine descriptors can be inspected above the boundary. Semantic software translates its conclusions into physical constraints; Kernel does not derive Privacy or Integrity from locality/provider/model facts.

The Kernel does not depend on the semantic SDK.

Physical Kernel internals should remain replaceable/experimentable where real needs arise without importing semantic MADRE concepts. An inference-assisted physical router is acceptable if it reasons only over physical Kernel facts.

## DRE

Delayed Reasoning Effort allows semantic reasoning to outlive an immediate interaction while keeping semantic and physical persistence separate.

Semantic MADRE retains why the work exists, relevant context and continuation. Kernel retains only durable physical execution responsibility.

This is a core part of MADRE's thesis: time, decomposition, domain knowledge and local resources can substitute for some amount of instantaneous frontier inference, while stronger providers remain available selectively.

## SDK as generation target

MADRE is intended not only to run AI-native software but to make creation of owner-native software cheap.

A capable development AI should eventually be able to use the public SDK plus a domain requirement to generate, build, test and install an ordinary Module locally. The builder should not be required for normal runtime execution after the software exists.

A future BuilderModule may be deferred. The requirement that the public SDK be simple, explicit and sufficient is current.

## Current implementation state

`main` contains the accepted native Kernel/Lane C implementation and Java physical client:

- native C++ Kernel;
- durable physical Work;
- scheduling/restart recovery;
- worker lifecycle/resource accounting;
- local IPC and Windows/Linux hardening;
- Java `madre-kernel-client`;
- native llama.cpp worker path.

The semantic SDK/Module layer and Runtime described above are accepted architecture but are **not yet implemented in the active tree**. Do not restore discarded historical semantic implementations to hide that gap.

## Development character

MADRE is a one-Owner research/product project developed heavily with AI assistance.

Engineering target:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

Simplicity does not mean deleting the semantic ownership/application points that make a concept operational. Do not generalise SPIRA, Module/Agent/Operation semantics or the semantic/physical boundary into vague platform abstractions.

## Authority order

1. current Owner instruction;
2. `docs/product/owner-intent-corpus.md`;
3. this file;
4. `docs/architecture/security-algebra.md` for SPIRA;
5. `docs/architecture/mid-level-architecture.md` for whole-system engineering;
6. `docs/architecture/kernel.md` for Kernel;
7. active implementation/tests/CI as evidence.

Historical implementation is evidence only and loses whenever later Owner intent supersedes it.