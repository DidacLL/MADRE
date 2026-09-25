# MADRE — SPIRA Security Algebra

This document is the engineering authority for MADRE's Security Algebra.

It preserves the small operational model already established for SPIRA: **which MADRE contracts carry each value, how same-dimension values compose, when dimensions are compared, and how consequential Operation variants are represented by `EffectProfile`.**

The Product and Owner Intent Corpus remains authoritative for why SPIRA exists and how it serves the Owner. This document does not create an authorization subsystem, policy engine, central security manager, generic `SecurityObject`, or semantic Kernel responsibility.

## 1. Five nominal ordered carriers

SPIRA has five different ordered carriers. They share ranks so composition is deterministic, but their meanings are not interchangeable.

### Sensitivity

```text
0 SYSTEM_RESERVED
1 TRIVIAL
2 SHARED
3 PROFILING
4 SENSITIVE
5 SECRET
```

### Privacy

```text
0 SYSTEM_RESERVED
1 PUBLIC
2 UNKNOWN
3 LOCAL
4 MODULE
5 ISOLATED
```

`UNKNOWN` is an ordinary Privacy value, not an absent value. It means the applicable receiving boundary is not declared public but its handling cannot be established more strongly.

### Integrity

```text
0 SYSTEM_RESERVED
1 NOT_DECLARED
2 DECLARED
3 TRUSTED
4 ACCEPTED
5 VALIDATED
```

Meaning:

- `NOT_DECLARED` — the relevant integrity cannot be traced or meaningfully claimed;
- `DECLARED` — it comes from an explicit manifested declaration, but that declaration is not independently traceable;
- `TRUSTED` — the origin or behaviour is trusted through established provenance, common use or other evidence even though it is not fully analysable;
- `ACCEPTED` — stronger assurance exists because of effective boundaries, known origin, observable behaviour or direct Owner acceptance;
- `VALIDATED` — the relevant integrity property can be deterministically verified again when needed.

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

### Autonomy

```text
0 SYSTEM_RESERVED
1 LIVE_INTERACTION
2 ASK_ALWAYS
3 ASK_ONCE
4 ACKNOWLEDGE
5 AUTONOMOUS
```

`SYSTEM_RESERVED` is not an ordinary authored value in any carrier.

## 2. Values live on the contracts where they mean something

SPIRA is deliberately not one five-field label attached to every object.

The established direct application points are:

| MADRE contract/fact | SPIRA value | Meaning |
| --- | --- | --- |
| concrete `Material` | Sensitivity | confidentiality consequence of that representation |
| an Operation's accepted Material boundary, per accepted Material type | Privacy | how private that receiving boundary is for that input |
| an Operation's promised Material result, per produced Material type | maximum Sensitivity | strongest output Sensitivity that Operation contract promises for that result type |
| an Agent | Integrity | assurance of that semantic actor as an actual causal participant |
| another actual non-user causal participant | Integrity | assurance contributed only when it actually participates in the current causal construction |
| an actual physical/effect realizer whose fidelity matters to the effect | Integrity | assurance of that realizer for the concrete effect path |
| one selected Operation `EffectProfile` | Risk + Autonomy | consequence and residual machine control of that exact consequential execution variant |

A contract that has no responsibility for one of these meanings does not acquire a field merely to complete a five-value tuple.

Derived summaries may be calculated for discovery or explanation, but they are not new independent authority. For example, the effective Privacy visible through several actually exposed Operation input boundaries is the minimum of those boundaries. The underlying Operation contracts remain the source facts.

## 3. Same-dimension composition

Same-dimension accumulation is small and immutable:

```text
Sensitivity.combine = max
Privacy.combine     = min
Integrity.combine   = min
```

Conceptually:

```text
S2 + S5 -> S5
P5 + P3 -> P3
I4 + I2 -> I2
```

The plus sign here means structural accumulation, not arithmetic.

Risk and Autonomy do **not** form general running aggregates. They remain bound together on one exact selected `EffectProfile`.

Only actual constituents participate. Installed-but-unused Operations, hypothetical branches, unrelated earlier work and rejected destinations do not contaminate the current composition.

## 4. Material and Operation contracts

A concrete Material carries its own Sensitivity.

An Operation contract declares, for each accepted Material type, the Privacy of that input boundary. It also declares, for each produced Material type, the maximum Sensitivity promised for output of that type.

Conceptually:

```text
Operation
    acceptedMaterial[MaterialType] -> Privacy
    producedMaterial[MaterialType] -> maximum Sensitivity
    effectProfiles                 -> 0..* EffectProfile
```

This makes information composition inspectable before invoking arbitrary implementation code.

A Module may create a genuinely new representation with a different Sensitivity—for example a minimized or anonymized representation. That new value belongs to the new Material. The source Material is not relabelled in place.

An Operation result must remain inside its declared result contract. If the Operation promises at most `SHARED` for one result type, returning `SECRET` Material under that result contract is structurally invalid.

## 5. EffectProfile belongs to an Operation

`EffectProfile` is the established MADRE representation of one security-relevant consequential execution variant of an Operation.

Conceptually:

```text
EffectProfile
    identity bound to one Operation
    Risk
    Autonomy
```

An Operation may declare zero, one or several EffectProfiles.

A concrete consequential invocation selects **one exact profile declared by that Operation**. The caller does not invent Risk or Autonomy values at invocation time.

Example:

```text
delete-resource / live-owner-action
    Risk     = DELETE
    Autonomy = LIVE_INTERACTION

delete-resource / autonomous-cleanup
    Risk     = DELETE
    Autonomy = AUTONOMOUS
```

Those are two execution shapes of the same semantic Operation, not two permission states.

An Operation with no consequential effect does not receive a dummy EffectProfile, dummy Risk or dummy Autonomy merely to satisfy a schema.

## 6. OperationCall binds the concrete invocation

The bounded invocation structure is:

```text
Operation contract
+ actual input Material
+ selected EffectProfile, only when the Operation declares consequential profiles
= concrete OperationCall
```

The call establishes that:

- the input Material type is accepted by the Operation;
- a consequential Operation uses exactly one of its own declared EffectProfiles;
- a non-consequential Operation does not fabricate one;
- produced Material stays within the Operation's declared output type and maximum Sensitivity.

`OperationCall` is not itself a central policy evaluator. It binds the concrete facts from which the relevant SPIRA comparisons can be made at the actual boundary.

## 7. Information reach / disclosure comparison

For the exact Material and receiving path being composed:

```text
S = maximum Sensitivity of the Material actually entering this connection
P = minimum Privacy of the actual receiving boundaries on this path

reachable iff S <= P
```

Examples:

```text
SHARED reaches UNKNOWN
SECRET does not reach UNKNOWN
```

The comparison is local to that disclosure. A secret Material that is not sent does not enter the comparison. A weak receiver considered but rejected does not poison another route.

If a target Module or Operation derives a new minimized Material, a later disclosure evaluates the new representation actually being sent, not the unchanged source representation.

## 8. Consequential effect: control comparison

For the selected `EffectProfile e`:

```text
ControlDemand(e) = min(Risk(e), Autonomy(e))
```

Let the controller Integrity be the minimum Integrity of the **actual non-user causal participants** that determine the security-significant effect—trigger, target, parameters, scope, effect content or autonomous continuation.

```text
ControllerIntegrity = min(I_actual_controllers)
```

When there is no non-user controller, the algebraic neutral top value is rank 5 (`VALIDATED`). This is a reduction identity, not a claim that an imaginary validated controller exists.

The control construction is compatible when:

```text
ControlDemand(e) <= ControllerIntegrity
```

Consequences:

- a high-Risk autonomous effect requires strong Integrity from the machine causal chain;
- direct Owner interaction lowers residual machine-control demand because the selected profile has low Autonomy;
- adding a strong participant cannot wash a weaker participant that still actually controls the effect;
- merely existing in earlier history does not make a subject a controller.

Owner participation is represented by the selected EffectProfile's Autonomy shape. It does not modify Material Sensitivity or disclosure Privacy.

## 9. Consequential effect: execution-fidelity comparison

Control and effect realization are different questions.

Let actual effect realizers be the nonempty set of participants/mechanisms whose behaviour can change the concrete effect that is realized. Their applicable Integrity values compose by minimum:

```text
EffectIntegrity = min(I_actual_effect_realizers)
```

The effect execution is compatible when:

```text
Risk(e) <= EffectIntegrity
```

Direct Owner control can reduce machine-control demand, but it cannot make an unreliable destructive/executable effect realization sound. A high-Risk selected profile still requires correspondingly strong Integrity from the actual realization path.

This comparison belongs on the semantic side of the MADRE boundary. If physical mechanism facts are needed to establish which realization choices are acceptable, the semantic Runtime/Module-owned reasoning executor must use the available factual descriptions and translate the result into physical Kernel constraints. **SPIRA values do not cross into Kernel Work.**

## 10. The three comparisons remain separate

For one concrete semantic construction, the relevant checks are therefore:

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

There is no global security score and no weighted aggregate across all five facets.

A construction with no disclosure does not invent a disclosure comparison. A construction with no consequential EffectProfile does not invent Risk/Autonomy checks.

## 11. ReasoningRequest carries the actual reasoning composition

A `ReasoningRequest` is semantic inference intent, not Kernel Work.

When reasoning is created from a bounded Operation context, the request accumulates the **actual S/P/I constituents of that reasoning computation**:

- Sensitivity from the actual input Material and any additional Material genuinely included in the reasoning context;
- Privacy from the actual accepted Operation boundary and any additional receiving boundaries genuinely included in the path;
- Integrity from the acting Agent and any additional non-user causal participants genuinely participating in the reasoning construction.

Thus:

```text
ReasoningRequest.S = max(actual reasoning Material sensitivities)
ReasoningRequest.P = min(actual reasoning receiving boundaries)
ReasoningRequest.I = min(actual reasoning causal participant integrities)
```

Risk and Autonomy do **not** automatically propagate into the ReasoningRequest simply because the surrounding Operation declares an EffectProfile. Ordinary inference does not itself realize the external effect.

If later reasoning results actually control a consequential Operation, that later concrete effect construction includes the relevant derived Material/causal participants and selected EffectProfile at that point.

This prevents a parent Operation's possible effect from being flattened into every inference step while still preserving the real causal chain when inference output actually controls an effect.

## 12. Agents react to composition; they do not manage it

There is no Agent-owned mutable `Compound` security object.

The current algebraic values exist because the actual constituents of a concrete interaction are composing. The acting Agent can observe that a proposed route/effect is compatible or incompatible and react semantically:

- use another Operation;
- choose another destination;
- derive/minimize different Material;
- select a different declared EffectProfile when the Operation and situation genuinely offer one;
- ask the Owner where that changes the actual Autonomy shape;
- delegate;
- abandon that path.

Changing the actual constituents creates a different construction and therefore a different composition. The Agent does not mutate algebra values to make an incompatible construction pass.

## 13. Runtime and Module responsibility

The SDK defines these semantic carriers and bounded contracts. Module authors construct their Material, Agent, Operation and EffectProfile facts through that public surface.

Runtime composes and compares those facts at the concrete semantic boundaries it executes or coordinates. Runtime does not become a separate semantic owner merely because it performs the deterministic mechanics.

The Module remains responsible for semantic claims it is uniquely placed to know, such as the Sensitivity of genuinely derived Material and the effect variants its Operation actually offers.

SPIRA itself never invokes inference. If semantic reasoning is required to derive some application fact, an Agent performs that reasoning through an ordinary `ReasoningRequest`; the resulting semantic object is then composed normally.

## 14. Kernel boundary

The current native Kernel remains strictly physical.

Kernel does not receive or persist:

```text
Sensitivity
Privacy
Integrity
Risk
Autonomy
Material
Operation
EffectProfile
Agent
ReasoningRequest
```

The Runtime-executed, Module-owned reasoning-to-physical Operation resolves semantic reasoning requirements before producing physical `WorkRequest` constraints.

Kernel then schedules and executes only those physical constraints. Kernel may be internally replaceable or experimental, but it does not become the evaluator of SPIRA.

## 15. Owner sovereignty

SPIRA helps MADRE software understand information/effect composition for the Owner. It is not an authority above the Owner.

The Owner can inspect and modify the declarations, Module code, configured Runtime implementations, state and source that produce these facts. MADRE does not pretend to make equivalent facts inaccessible to the machine owner.

That sovereignty does not require the algebra to lie. At any moment, given the current declared/derived facts and actual constituents, composition remains deterministic. The Owner changes the software/facts/construction rather than receiving a hidden bypass bit.

## 16. Anti-drift invariants

Do not replace this model with any of the following without an explicit Owner architecture change:

- one generic five-field security context attached to every entity;
- an Agent-owned `Compound` manager;
- a central policy/authorization/IAM service;
- one global SPIRA score;
- Risk or Autonomy accumulated across unrelated Operations;
- a caller-supplied arbitrary Risk/Autonomy number instead of an Operation-owned `EffectProfile`;
- dummy EffectProfiles for non-consequential Operations;
- cross-dimensional checks hidden inside every scalar value class;
- all historical participants contaminating every later comparison;
- semantic SPIRA values placed in physical Kernel Work;
- model/provider/locality names silently deriving Privacy or Integrity without an explicit semantic declaration/valuation.

The model is small because each value has a concrete owner/application point and each comparison has a concrete event at which it matters—not because those details can be omitted.