# MADRE — SPIRA Security Algebra

This document is the engineering authority for MADRE's Security Algebra.

SPIRA is the intrinsic algebraic structure produced by the **actual semantic constituents participating in the current construction**. It is not an authorization system, permission service, policy engine, central evaluator or mutable security-context object.

The Product and Owner Intent Corpus remains authoritative for why SPIRA exists. This document fixes the engineering meaning of the five facets, where their facts come from, how they compound, and where their compatibility relations become relevant.

## 1. Ordered facets

### Sensitivity

```text
0 SYSTEM_RESERVED
1 TRIVIAL
2 SHARED
3 PROFILING
4 SENSITIVE
5 SECRET
```

Sensitivity describes actual information. Higher values mean greater consequence if that information is exposed.

Its natural carrier is the actual Material/context representation participating in the construction.

### Privacy

```text
0 SYSTEM_RESERVED
1 PUBLIC
2 UNKNOWN
3 LOCAL
4 MODULE
5 ISOLATED
```

Privacy describes the containment/exposure offered by the actual receiving boundary. It is not a property saying that information itself is private.

`UNKNOWN` is a real ordinary value, not absence.

Where an Operation accepts Material, the accepted Material boundary can declare the Privacy offered to that input. Other real receiving boundaries may contribute their own Privacy when they actually participate.

### Integrity

```text
0 SYSTEM_RESERVED
1 NOT_DECLARED
2 DECLARED
3 TRUSTED
4 ACCEPTED
5 VALIDATED
```

Integrity describes assurance carried by actual semantic participants/provenance relevant to the current construction.

- `NOT_DECLARED` — the relevant integrity cannot be traced or meaningfully claimed;
- `DECLARED` — it comes from a manifested declaration, but that declaration is not independently traceable;
- `TRUSTED` — the origin or behaviour is trusted through established provenance, common use or other evidence despite incomplete analysis;
- `ACCEPTED` — stronger assurance exists because of effective boundaries, known origin, observable behaviour or direct Owner acceptance;
- `VALIDATED` — the relevant integrity property can be deterministically verified again when needed.

An actual Agent contributes Integrity to semantic work in which it participates. Other provenance-bearing participants contribute only when they are actually part of the current causal construction.

These are assurance values, not privilege levels.

### Risk

```text
0 SYSTEM_RESERVED
1 READ
2 WRITE
3 DELETE
4 EXECUTE
5 POTENTIALLY_HARMFUL
```

Risk belongs to the **concrete Operation/effect actually being selected**. It expresses increasing consequence.

Risk is not an abstract property of a Module and it is not a running aggregate of every Operation an Agent could use. Only the actual Operation/effect participating now contributes Risk.

### Autonomy

```text
0 SYSTEM_RESERVED
1 LIVE_INTERACTION
2 ASK_ALWAYS
3 ASK_ONCE
4 ACKNOWLEDGE
5 AUTONOMOUS
```

Autonomy describes the **actual acting Agent continuation state**: how independently that continuation is proceeding relative to Owner interaction.

Autonomy is not a permission on an Operation and is not a permanent Module property. The same Operation can participate under different actual Agent continuation states.

Owner interaction can therefore change the real Autonomy constituent without changing the Operation's Risk or the information's Sensitivity.

`SYSTEM_RESERVED` is not an ordinary authored value in any facet.

## 2. Direct facts and derived views

The facets do not form one universal five-field label attached to every MADRE object.

The direct semantic facts are:

| Actual semantic fact | SPIRA facet |
| --- | --- |
| participating Material/context representation | Sensitivity |
| participating receiving/exposure boundary | Privacy |
| participating Agent / provenance-bearing causal subject | Integrity |
| concrete Operation/effect selected now | Risk |
| acting Agent's current continuation state | Autonomy |

A public Operation contract may expose facts needed before execution, such as:

```text
accepted Material type -> receiving Privacy
produced Material type -> maximum promised Sensitivity
Operation behavior/effect -> Risk
```

The exact Java shape is not frozen by this architecture document. The important contract is semantic ownership: those facts belong to the bounded Operation where they mean something.

Derived summaries can exist where useful but do not become new owners of the facets. For example, an Agent's currently exposed Operations can imply an effective receiving Privacy, and a Module's currently reachable Material/outputs can imply an effective Sensitivity. Such views are recomputed from the real exposed constituents.

There is **no mandatory `EffectProfile`** in the current model. Earlier implementation experiments used one to pair Risk and Autonomy, but the settled semantics keep them separate because they belong to different actual constituents: Operation/effect and Agent continuation.

## 3. Same-facet accumulation

When more than one actual constituent contributes the same facet, the established reductions are:

```text
Sensitivity -> max
Privacy     -> min
Integrity   -> min
```

Conceptually:

```text
S2 + S5 -> S5
P5 + P3 -> P3
I4 + I2 -> I2
```

The plus sign means structural accumulation, not arithmetic.

Risk and Autonomy are not general running aggregates. The current construction uses the Risk of the actual selected Operation/effect and the Autonomy of the actual acting Agent continuation.

Only actual constituents participate. Unused Operations, possible model outputs, unselected destinations, every installed capability and unrelated branches do not contaminate the current compound.

## 4. Intrinsic compound

There is no mandatory `CompoundSecurity`, `EffectProfile`, `Authorization`, `PolicyDecision` or evaluator object.

The compound is a **derived semantic property of the actual current constituents**.

Depending on the construction, relevant constituents may include:

- actual Material/context;
- the actual acting Agent;
- the actual receiving boundary;
- the concrete Operation/effect being executed;
- actual provenance-bearing causal participants;
- the Agent's actual continuation state.

The compound has no owner or manager. The Agent does not mutate a Compound object. The semantic pieces simply have the structure they have when composed.

## 5. Information and receiving boundary

When actual information enters an actual receiving boundary, the relevant relation is:

```text
max(S_actual_information) <= min(P_actual_receiving_boundaries)
```

The comparison is local to the information and path actually being composed.

Examples:

```text
SHARED can compose with UNKNOWN
SECRET cannot compose with UNKNOWN
```

A secret Material that is not sent does not participate. A destination considered but not used does not poison another route.

A Module may transform, tokenize, anonymize or minimise Material. The result is a **new representation** that may have a different Sensitivity justified by the transformation. The source Material is not relabelled in place.

A receiving Module may also possess domain facts that justify re-evaluating the sensitivity of the actual incoming representation through bounded Module strategies. This is local semantic knowledge, not a global classifier.

## 6. Operation, causal Integrity and Agent Autonomy

When a concrete Operation/effect is being carried by an Agent continuation, Risk and Autonomy meet the Integrity of actual non-user causal participants.

Let:

```text
R = Risk of the actual selected Operation/effect
A = Autonomy of the current acting Agent continuation
I = minimum Integrity of actual non-user causal participants relevant to that effect
```

The established relation is:

```text
min(R, A) <= I
```

If there is no non-user causal participant for the relevant reduction, rank 5 is the neutral top element. This is an algebraic identity, not an invented `VALIDATED` actor.

This relation preserves the distinction the facets exist to express:

- high consequence and high autonomy require stronger actual causal assurance;
- Owner interaction can lower the actual Autonomy constituent;
- changing Autonomy does not lower the Operation's Risk;
- a stronger participant cannot wash a weaker participant that remains causally relevant;
- merely appearing somewhere in earlier history does not make a subject part of the current reduction.

## 7. Effect realization

Where the concrete Operation/effect has actual realizers whose Integrity is semantically relevant to whether that effect is faithfully produced, the established relation is:

```text
R_actual_operation <= min(I_actual_effect_realizers)
```

This remains distinct from the Agent-continuation relation above. Direct Owner interaction can lower Autonomy, but it cannot turn an unreliable realization path into a reliable one for a high-consequence effect.

This is a semantic relation. The native Kernel does not receive SPIRA values. If physical engine facts affect which physical choices are semantically acceptable, the semantic side resolves that before Work crosses the hard boundary and expresses only physical constraints to Kernel.

## 8. Compatibility is not authorization

The relations above describe whether the **actual semantic construction composes** under its current constituents. They do not create a service that grants or denies the Owner permission.

A compatible current composition can continue.

An incompatible current composition cannot continue **as that exact construction**. Semantic software can change reality and derive again, for example by:

- using another Operation;
- using another receiving path;
- deriving/minimising different Material;
- changing the actual Agent continuation by involving the Owner;
- delegating to a different Agent;
- changing another real participant;
- abandoning that path.

It must not silently weaken Sensitivity, pretend greater Privacy, raise Integrity, lower Risk or alter Autonomy merely to make the unchanged construction fit.

## 9. Agent responsibility without Compound ownership

The Agent is the semantic actor carrying its current continuation. Operations are bounded actions used by that actor.

An Agent therefore encounters the SPIRA structure produced by what it is actually trying to compose and reacts to it. It does **not** own or manage the algebra.

When Agent A invokes an Operation exposed by Module B, Agent A remains the actor carrying that semantic continuation unless there is an explicit Agent-to-Agent delegation. Module B can be agentless.

A Module can contribute its own bounded semantic strategies at its boundary because it owns its domain meaning. Runtime routing does not make Runtime the semantic evaluator.

## 10. ReasoningRequest

A `ReasoningRequest` is a semantic need for reasoning created by an Agent.

It may involve:

- relevant context;
- Material;
- provenance;
- Owner instruction;
- SPIRA facts relevant to the current reasoning construction;
- the actual reasoning objective.

This document does **not** define a mandatory `ReasoningRequest.S`, `.P`, `.I`, `.R` or `.A` storage tuple. The actual semantic composition remains the composition of the real participating objects/facts.

The responsible Agent/Module semantic process resolves what reasoning is needed, what information is relevant, what destinations/inference choices are acceptable, and what current composition is valid.

`ReasoningRequest` remains semantic and does not cross into Kernel as a semantic object.

Risk remains with an actual selected Operation/effect. Autonomy remains with the actual Agent continuation. They are not automatically copied into every inference request merely because reasoning occurs while an Operation is being pursued.

## 11. Runtime

Runtime supplies installation mechanics: Module discovery, activation/lifecycle, addressing, routing, persistence/correlation mechanisms and execution of installation-required functions.

Runtime does **not** become the owner or semantic evaluator of SPIRA merely because it transports or executes calls.

The semantic values and their composition belong to the participating Module/Agent/Material/Operation context. Runtime can carry the facts and support the execution path without redefining their meaning.

The special ReasoningRequest-to-physical-Work function follows the same rule: Runtime executes the configured implementation, but that implementation belongs to a Module. The shipped default CORE Module provides the default implementation. The Owner can replace or wrap it.

## 12. Kernel boundary

Kernel owns physical inference Work only.

It does not receive or persist:

```text
Sensitivity
Privacy
Integrity
Risk
Autonomy
Material
Operation semantics
Agent
ReasoningRequest
```

The semantic side may inspect factual engine descriptors and choose acceptable physical constraints before creating physical Work. Kernel then performs only physical scheduling/execution within those constraints.

Kernel may itself remain physically modular and experimentable, but an inference-assisted Kernel routing experiment still reasons over physical facts; it does not import SPIRA into Kernel.

## 13. Owner sovereignty

SPIRA serves the Owner. It is not security *against* the Owner.

The Owner can inspect and modify Module declarations, semantic strategies, Agent state, Runtime configuration, Kernel implementation and the source/state they own.

Owner sovereignty does not require the algebra to lie. At a given moment the current facts compose deterministically. The Owner changes the software, facts or actual construction rather than receiving a hidden permission bypass.

## 14. Anti-drift invariants

Do not replace this model without an explicit Owner architecture change with:

- one generic five-field security context attached to every entity;
- an Agent-owned mutable Compound manager;
- mandatory `EffectProfile` objects pairing Risk and Autonomy;
- a central authorization/policy/IAM/evaluator service;
- Runtime ownership of SPIRA semantics;
- one global SPIRA score;
- Risk accumulated from unused Operations;
- Autonomy attached permanently to an Operation or Module;
- all historical participants contaminating later compositions;
- relabelling values to force compatibility;
- a mandatory five-facet field set on `ReasoningRequest`;
- semantic SPIRA values in physical Kernel Work;
- model/provider/locality facts silently converted into Privacy or Integrity without semantic valuation above the Kernel boundary.

The model stays simple by keeping each facet on the actual semantic fact where it means something and deriving composition only from what really participates.