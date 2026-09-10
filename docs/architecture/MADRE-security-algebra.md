# MADRE Security Algebra

Authority: `MADRE.md` defines product meaning. This document owns the normative security object model, transition topology, deterministic admissibility algebra and security-decision evidence.

MADRE security is a small typed algebra over the concrete material, participants and bounded effects involved in one prospective transition.

It is not an account, role, ACL, clearance, token or permission system.

## 1. Normalized domain

All ordinary security values use the ordered set:

```text
L = {1, 2, 3, 4, 5}
1 < 2 < 3 < 4 < 5
```

The runtime algebra requires only:

```text
join = max
meet = min
```

`SYSTEM_RESERVED = 0` is not an ordinary member of `L`. It is an internal reserved sentinel only. A role-required ordinary security value equal to 0 is structurally invalid and fails before numeric evaluation.

The common scale exists for deterministic composition. The five concepts remain typed and are not interchangeable merely because they share the same numbers.

## 2. Final normalized vocabulary

The runtime security vocabulary is exactly:

```text
Sensitivity
Privacy
Integrity
Risk
Autonomy
```

Their meanings are independent.

### Sensitivity

**Applies to:** `Artifact`, `ContextBundle`.

Sensitivity is the confidentiality demand of this concrete material representation.

```text
LEVEL_1  public or negligible disclosure consequence
LEVEL_2  private/limited material with low disclosure consequence
LEVEL_3  materially private or confidential
LEVEL_4  highly sensitive with substantial disclosure consequence
LEVEL_5  maximum sensitivity: severe personal/system/secret consequence
```

Sensitivity belongs to the representation. A genuinely minimized or anonymized derived representation may have lower Sensitivity than its source; the source SecurityObject remains unchanged.

### Privacy

**Applies to:** `Module`, `Agent`, endpoint, `Capability`, and an `EffectProfile` only when the bounded effect itself exposes material.

Privacy is the resulting normalized assurance that a concrete participant/path protects material against unintended observation, retention, onward transmission or exposure.

```text
LEVEL_1  weak/unknown effective privacy protection
LEVEL_2  limited protection
LEVEL_3  ordinary bounded protection
LEVEL_4  strong protection
LEVEL_5  strongest applicable controlled/private protection
```

Why a subject receives a given Privacy valuation—locality, isolation, sandboxing, retention behavior, network exposure, contractual guarantees, platform controls or other evidence—is valuation/audit information. Those factors are not separate runtime algebra dimensions unless a future architecture revision proves that the decision function must observe them independently.

Privacy is not semantic correctness and is not permission.

### Integrity

**Applies to:** `Artifact`, `ContextBundle`, `Module`, `Agent`, endpoint, `Capability`, and `EffectProfile`.

Integrity is assurance that the subject can participate in security-significant causal control or effect execution according to its bound contract without unauthorized or unintended alteration.

Subject-specific interpretation is:

- material Integrity: assurance that the representation/provenance/validation is suitable to drive security-significant control at that level;
- actor Integrity: assurance that the actor preserves/transforms control-relevant information according to its bound behavior;
- Capability Integrity: assurance that the concrete physical mechanism preserves the control-relevant execution route;
- EffectProfile Integrity: assurance that the bounded effect implementation faithfully realizes that declared profile.

Integrity is not factual truth. A high-Integrity Artifact may still contain an incorrect opinion; a high-Integrity model may still make an incorrect prediction. The concept concerns security-significant manipulation resistance and contract fidelity.

```text
LEVEL_1  weak/untrusted control/effect assurance
LEVEL_2  limited assurance
LEVEL_3  ordinary bounded assurance
LEVEL_4  strong assurance
LEVEL_5  strongest applicable controlled/validated assurance
```

### Risk

**Applies to:** immutable Operation `EffectProfile`.

Risk is the consequence of realizing the bounded effect incorrectly, unexpectedly, repeatedly or adversely.

```text
LEVEL_1  negligible/reversible bounded consequence
LEVEL_2  low consequence
LEVEL_3  significant bounded consequence
LEVEL_4  high consequence, difficult recovery or broad external impact
LEVEL_5  destructive, irreversible, executable, publication/security-critical or similarly severe consequence
```

Risk is independent of material Sensitivity.

### Autonomy

**Applies to:** immutable Operation `EffectProfile`.

Autonomy is the amount of security-significant effect control left to machine execution after the interaction/control contract represented by that profile has occurred.

It includes residual machine discretion over trigger, target, parameters, scope, effect content and security-significant continuation/repetition.

```text
LEVEL_1  exact/direct user-controlled effect; negligible residual machine discretion
LEVEL_2  narrowly bounded machine discretion after direct control
LEVEL_3  mixed/bounded autonomous control
LEVEL_4  substantial autonomous discretion
LEVEL_5  fully or substantially machine-controlled effect
```

A generic confirmation dialog does not automatically imply low Autonomy. Autonomy reflects the actual bound execution shape.

User involvement is represented by selecting a different immutable EffectProfile whose residual machine control is lower. It is not represented by an `approved=true` authorization bit.

## 3. Subject mapping

The normative minimal mapping is:

```text
Artifact / ContextBundle
    Sensitivity
    Integrity

Module / Agent / endpoint
    Privacy
    Integrity

Capability
    Privacy
    Integrity

Operation EffectProfile
    Risk
    Autonomy
    Integrity
    Privacy?   # only if the bounded effect itself exposes material
```

No subject carries values that do not apply to it.

Missing role-required values are not implicitly replaced with level 1, level 5 or level 0. They make the prospective transition structurally invalid until a valuation process supplies a valid bound SecurityObject.

Third-party/default valuation policy remains separate from this algebra.

## 4. SecurityID and SecurityObject

Every security-relevant immutable binding has a globally unambiguous `SecurityID`.

`SecurityID` is evidence identity, never permission.

Conceptually:

```text
SecurityObject
    security_id
    subject_ref
    applicable normalized values
    binding / integrity evidence
```

`subject_ref` contains enough structural identity to bind the object to its actual owner/kind/revision without requiring Module-local names to be globally unique. A practical representation may include:

```text
owner_module_id
owner/publication revision where relevant
subject_kind
subject_local_id
subject/representation revision where relevant
```

Architectural requirements:

- the same immutable binding restores the same SecurityID across restart;
- distinct immutable bindings never intentionally share a SecurityID;
- one SecurityID resolving to conflicting contents is structural failure;
- changed security values/profile/representation produce a new immutable binding and SecurityID;
- Module-local Agent/Operation names may collide across Modules without SecurityID collision;
- SecurityID possession grants nothing.

How a valuation was obtained may be retained as optional audit evidence around the SecurityObject. It is not a mandatory operand of the minimal runtime object.

## 5. EffectProfile

An `Operation` is the semantic bounded callable. Security-significant execution variants are represented by immutable `EffectProfile`s owned by that Operation.

Conceptually:

```text
Operation
    identity / owner / callable contract
    EffectProfiles

EffectProfile
    SecurityID
    bound Operation reference
    Risk
    Autonomy
    Integrity
    Privacy?   # iff the effect itself creates a disclosure path
    binding / integrity evidence
```

A concrete effect transition selects a bound EffectProfile. The caller does not supply arbitrary Risk, Autonomy, Integrity or Privacy numbers.

Example:

```text
delete.resource / direct-control
    Risk      = 5
    Autonomy  = 1

 delete.resource / autonomous-cleanup
    Risk      = 5
    Autonomy  = 5
```

These are two security-relevant execution shapes of the same Operation, not two permission states.

## 6. SecurityTransition: roles, not a global score

A `SecurityObject` describes a subject. A `SecurityTransition` describes how concrete subjects participate in one proposed crossing/effect.

The minimum security-relevant relationship roles are:

```text
DISCLOSURE
CONTROL
EFFECT_EXECUTION
DERIVATION
```

`DERIVATION` is historical/structural rather than a direct admissibility inequality.

Transition topology is constructed from public contracts and concrete execution structure. Kernel does not infer roles by reading prompt/output semantics.

### Disclosure

A disclosure edge says that a concrete material representation will be exposed through actual recipients/mechanisms.

Conceptually:

```text
Disclosure
    material_security_id
    path_security_ids[]
```

Only participants that materially govern that disclosure path belong on the edge.

### Control

A controller is a security-relevant subject whose material/computation may determine a security-significant part of an EffectProfile invocation, such as trigger, target, parameters, scope, effect content or autonomous continuation.

Merely existing earlier in a lifecycle does not make a subject a controller. Merely receiving material does not necessarily make it a controller.

If generated or external material directly determines a destructive command, publication target, delete target or similar effect parameter, it is a controller.

### Effect execution

An executor is a security-relevant subject capable of changing the physical effect actually realized.

The selected EffectProfile itself is an execution-integrity contributor. The owning endpoint/Module and any Kernel-visible mechanism that can alter effect fidelity also participate where applicable.

Controllers answer “what effect is selected/parameterized?” Executors answer “is that bounded effect faithfully realized?”. A subject may occupy both roles.

## 7. Confidentiality predicate

For every disclosure edge `d=(m,P)`:

```text
PathPrivacy(d) = meet(Privacy(p) for p in P)
```

The edge is safe exactly when:

```text
DisclosureSafe(d)
    <=> Sensitivity(m) <= PathPrivacy(d)
```

Every relevant protection is necessary. One strong participant cannot numerically compensate for a weaker participant on the same actual disclosure path.

The predicate is edge-local. A high-sensitivity Artifact that is not exposed by the prospective edge does not enter that edge's reduction.

Disclosures with exactly the same path may be aggregated as an optimization:

```text
max(Sensitivity of material on path P) <= min(Privacy of P)
```

but the result must equal independent edge evaluation.

## 8. Control predicate

For an effect transition using EffectProfile `e`:

```text
ControlDemand(e) = meet(Risk(e), Autonomy(e))
                 = min(Risk(e), Autonomy(e))
```

Let `Controllers(c)` be the actual non-user security-relevant causal controllers of that effect.

```text
ControllerIntegrity(c)
    = meet(Integrity(x) for x in Controllers(c))
```

If there are no non-user controllers, the neutral assurance is level 5.

The control predicate is:

```text
ControlSafe(c)
    <=> ControlDemand(e) <= ControllerIntegrity(c)
```

Thus:

- high Risk + high Autonomy requires high-integrity machine control;
- high Risk + direct/exact user control produces low machine-control demand;
- low Risk does not require maximal controller Integrity solely because it is autonomous;
- adding a stronger later controller cannot wash a weak controller that still causally determines the effect.

## 9. Effect-execution predicate

Direct user control can lower machine-control demand, but it cannot make an unreliable destructive implementation safe.

Let `Executors(c)` be the actual effect-execution contributors, including the selected EffectProfile itself.

```text
EffectIntegrity(c)
    = meet(Integrity(x) for x in Executors(c))
```

The effect-execution predicate is:

```text
EffectSafe(c)
    <=> Risk(e) <= EffectIntegrity(c)
```

Therefore a level-5 destructive effect requires a level-5 effect-execution path even under an exact direct-user-control EffectProfile.

## 10. Complete admissibility

A prospective transition `c` is admissible exactly when all relevant obligations hold:

```text
Admissible(c) =
    StructuralValid(c)
    AND all(DisclosureSafe(d) for d in Disclosures(c))
    AND ControlSafe(c)        # true when no effect is executed
    AND EffectSafe(c)         # true when no effect is executed
```

No global security score exists.

No weighted sum exists.

No generic `Trust >= Sensitivity` or `Trust >= Risk` rule exists.

Identity, registry state, credentials or previous success do not appear in the predicate.

## 11. Structural validity

Before numeric evaluation, at minimum:

```text
every referenced SecurityID resolves
every SecurityObject passes binding/integrity verification
same SecurityID never resolves to conflicting contents
subject kinds match value schemas
every value required by its concrete transition role exists
ordinary values are in LEVEL_1..LEVEL_5
SYSTEM_RESERVED=0 is absent from ordinary valuation
the selected EffectProfile belongs to the invoked Operation
transition relationships reference actual participants
material identity/digest/security continuity holds where required
new derivation output identity differs from source identities
derivation Integrity bounds hold
```

Structural failure is deterministic inadmissibility.

A missing value is not an implicit conservative numeric value. It is `missing_security_value` (or equivalent) until valuation supplies a complete bound profile.

## 12. Security history and locality

Security history is conceptually:

```text
SecurityHistory
    objects       # immutable SecurityObjects actually introduced
    transitions   # governed transitions + deterministic decision evidence
    derivations   # security-relevant representation relationships
```

History is not one numeric reduction domain.

A prospective decision uses immutable history for identity, continuity and causal relationships, but evaluates only the subjects/edges referenced by that transition.

A remote Capability that was considered and rejected before receiving a secret did not receive that secret and does not poison later calculations.

Likewise, a low-Privacy Capability used in an unrelated earlier transition does not block a later disclosure when it is not on that later disclosure path.

Object/transition/derivation accumulation is keyed set union by stable identity and is therefore associative and idempotent. Chronological order that matters is represented explicitly by transition/derivation relationships, not tuple insertion order.

Restart/retry restores the same immutable security facts. The same restored state and prospective transition yields the same decision. Selecting a different Capability, EffectProfile, endpoint or material representation creates a new transition with those new bound facts.

Mutable registry metadata never retroactively rewrites prior accepted security facts.

## 13. Transformation and derivation

Security-relevant transformation creates a new material representation.

A source Artifact/ContextBundle SecurityObject is immutable. A Module cannot lower Sensitivity or raise Integrity by mutating it in place.

A minimized, anonymized, validated, summarized, generated, reclassified or otherwise transformed representation receives:

```text
new material identity
new SecurityID
new SecurityObject
DERIVATION relation to sources/producers/validators where relevant
```

### Sensitivity reduction

Sensitivity reduction is Module-owned semantic classification of a genuinely new representation.

Example:

```text
source secret        Sensitivity = 5
    -> minimization
new representation   Sensitivity = 2
```

A later disclosure of only the new representation evaluates level 2. The level-5 source remains immutable derivation history.

Kernel never reads private content to determine whether minimization is semantically valid.

### Ordinary derivation Integrity

Ordinary transformation cannot increase Integrity by repackaging or copying material.

For an ordinary derivation with source materials `S` and producing participants `P`:

```text
Integrity(output)
    <= meet(
        Integrity(s) for s in S,
        Integrity(p) for p in P
    )
```

Only actual security-relevant producers belong in `P`.

This means a high-Integrity actor cannot wash a low-Integrity source merely by wrapping it in a new Artifact.

### Validation derivation Integrity

An explicit validation transformation may create a new representation with higher Integrity than its source because the validators become the assurance basis of that new representation.

For validator set `V`:

```text
Integrity(validated_output)
    <= meet(Integrity(v) for v in V)
```

The validation relationship, sources and validators remain derivation history. The low-Integrity source need not remain a direct controller of a later effect when the validated representation—not the source—is what actually controls that effect.

A validation derivation is not a generic relabeling operation. It must be an explicit Module-owned transformation whose declared validation participants actually support the assigned Integrity.

Generated output follows the same rules. Once delivered it is ordinary Module-owned material. Its initial Integrity reflects its production/derivation path; later explicit validation may create a distinct higher-Integrity representation.

## 14. Unknown and third-party valuation

The algebra itself contains no implicit unknown-value policy.

Before a role can be evaluated, the required normalized values must exist in bound SecurityObjects.

A separate valuation process may use, for example:

```text
conservative defaults
installer/user declarations
concrete adapter/platform facts
MADRE-native SDK declarations
future source/code analysis or validation
```

Those mechanisms establish input facts. They do not authorize execution.

Registration/installation acceptance never upgrades a SecurityObject merely by occurring.

The exact third-party valuation process remains a separate product/research problem.

## 15. Deterministic evidence and remediation boundary

A decision returns normalized evidence sufficient to reproduce the calculation without retaining private payload bytes.

At minimum evidence should identify:

```text
algebra version
transition identity/kind
structural failures

for disclosure checks:
    material SecurityID
    Sensitivity
    path SecurityIDs
    effective path Privacy
    limiting SecurityID(s)

for effect checks:
    EffectProfile SecurityID
    Risk
    Autonomy
    control demand
    controller SecurityIDs
    effective controller Integrity
    executor SecurityIDs
    effective effect Integrity
```

Stable deficit categories include, in substance:

```text
invalid_security_binding
security_id_conflict
invalid_subject_values
system_reserved_used_as_ordinary_level
missing_security_value
missing_transition_subject
invalid_effect_profile
invalid_derivation
confidentiality_capacity_below_sensitivity
control_integrity_below_demand
effect_integrity_below_risk
```

Kernel reports the failed structural fact/inequality and limiting subjects. It does not semantically prescribe remediation.

The nearest capable Module/Agent may respond by minimizing/anonymizing material, validating a control representation, choosing another Capability, choosing another EffectProfile/Operation, requiring a stronger direct-user-control execution shape, or abandoning the route.

## 16. Representative cases

### Maximum-sensitivity local path

```text
Material Sensitivity 5
CORE Privacy 5
CORE Agent Privacy 5
local Capability Privacy 5
```

Disclosure capacity is 5: allow.

### Same secret over weaker remote privacy

```text
Material Sensitivity 5
remote Capability Privacy 2
```

`5 <= 2` fails: deny.

### Public destructive autonomous effect

```text
Material Sensitivity 1
EffectProfile Risk 5
EffectProfile Autonomy 5
Controller Integrity 2
Executor Integrity 5
```

Confidentiality may pass, but `min(5,5)=5 > 2`: deny.

### Destructive direct-user effect

```text
EffectProfile Risk 5
EffectProfile Autonomy 1
Controller Integrity 1
Executor Integrity 5
```

Control demand is 1 and passes; effect execution still requires 5 and passes.

### Direct-user effect on weak executor

```text
EffectProfile Risk 5
EffectProfile Autonomy 1
Executor Integrity 2
```

Control may pass; `5 <= 2` fails: deny.

### Low-risk autonomous effect

```text
Risk 1
Autonomy 5
Controller Integrity 1
Executor Integrity 1
```

Control demand and effect demand are both 1: may proceed if disclosure obligations also pass.

### Minimized representation

```text
source Sensitivity 5
new minimized representation Sensitivity 2
later path Privacy 3
```

Later disclosure evaluates the representation actually sent: `2 <= 3`, while the source remains immutable derivation history.

### Low-Integrity generated control

```text
generated Artifact Integrity 1
EffectProfile Risk 5
Autonomy 5
```

If the Artifact is a controller, demand 5 exceeds Integrity 1: deny.

If it is merely displayed, it is not a controller and creates no control-integrity failure by itself.

### Validated derived control

A low-Integrity source may participate in an explicit validation derivation whose validators support a distinct higher-Integrity representation. If the validated representation—not the original source—controls the Operation, the control predicate evaluates that new representation while retaining the source and validation relationship in history.

### Publishing sensitive material

If an EffectProfile itself discloses material publicly, its Privacy contributes to the disclosure path. Maximum-sensitivity material therefore fails a public/low-Privacy publishing effect regardless of direct user control over the action.

## 17. Algebraic properties

For canonical immutable inputs:

### Determinism

The same transition and restored history yield one decision/evidence result. Mutable registry state, wall-clock order, prompt semantics and historical success are not implicit operands.

### Confidentiality monotonicity

For a fixed disclosure edge:

- increasing Sensitivity cannot turn a failure into a pass;
- decreasing path Privacy cannot turn a failure into a pass;
- increasing applicable Privacy cannot turn a pass into a failure.

### Control monotonicity

For fixed controller Integrity:

- increasing Risk cannot make an inadmissible control relation admissible;
- increasing Autonomy cannot make an inadmissible control relation admissible;
- decreasing controller Integrity cannot make an inadmissible relation admissible.

### Effect monotonicity

Increasing Risk cannot make a failed effect-execution relation pass. Increasing executor Integrity cannot make a passing relation fail.

### Non-compensation

Independent participants on the same edge/chain combine by `meet`. One strong participant cannot wash a weaker actual participant.

### Locality

A subject affects an inequality only when it participates in that concrete disclosure/control/effect relation.

### Idempotence

Repeatedly encountering the same immutable SecurityID does not multiply its contribution.

### Restart preservation

Restoring identical immutable history and transition operands preserves the decision.

### Identity non-authority

SecurityID, Module ID, EffectProfile identity, registry membership, credentials, Work IDs and references are locators/evidence only and do not appear as permission terms.

## 18. Minimality

Each retained numeric concept distinguishes a required MADRE case that would otherwise become inexpressible:

| Concept | Required distinction lost if removed |
| --- | --- |
| Sensitivity | public vs secret material on the same path |
| Privacy | strong/private vs weak/exposed disclosure path |
| Integrity | strong vs weak causal controller/effect executor |
| Risk | harmless vs destructive bounded effect |
| Autonomy | direct-user vs autonomous realization of the same consequence |

The runtime algebra therefore has five normalized concepts and no additional numeric permission/purpose/trust dimension.

Non-numeric transition roles carry relationship meaning that cannot be represented correctly by another scalar.

## 19. Authority exclusions

None of the following grants MADRE execution authority independently of the algebra:

```text
registration
installation acceptance
Module identity
SecurityID possession
Work ID
material reference
registry membership/trust
role
ACL
allowlist
bearer token
provider credential
previous successful decision
CORE identity
user-interface presence
```

Provider credentials permit access to their external mechanism only.

CORE uses the same predicates as every other Module and receives no bypass.

## 20. Bounded effects and payload opacity

MADRE-provided intelligent surfaces do not expose an unrestricted generic shell or unrestricted Internet environment.

System/network/external effects are explicit bounded Operations with bound EffectProfiles or concrete mechanisms with relevant SecurityObjects.

Executable/destructive/publishing effects carry high Risk according to their consequence even when their input is public.

Kernel does not inspect prompts/generated outputs to infer semantic permission, truth, control roles or classification. Modules/SDK contracts construct semantic material/transition facts; Kernel evaluates the bound facts deterministically.

Generated output cannot rewrite Kernel security through text. Once delivered it is ordinary Module-owned material and may be reused; a later governed transition evaluates the new material/roles normally.

## 21. Compact formal definition

Let:

```text
L = {1,2,3,4,5}
meet = min
join = max
```

For every disclosure edge `d=(m,P)`:

```text
PathPrivacy(d) = min(Privacy(p) for p in P)
DisclosureSafe(d) <=> Sensitivity(m) <= PathPrivacy(d)
```

For an effect transition `c` with bound EffectProfile `e`:

```text
ControlDemand(e) = min(Risk(e), Autonomy(e))

ControllerIntegrity(c)
    = min(Integrity(x) for x in Controllers(c))

EffectIntegrity(c)
    = min(Integrity(x) for x in Executors(c))

ControlSafe(c)
    <=> ControlDemand(e) <= ControllerIntegrity(c)

EffectSafe(c)
    <=> Risk(e) <= EffectIntegrity(c)
```

Finally:

```text
Admissible(c) <=>
    StructuralValid(c)
    AND all(DisclosureSafe(d) for d in Disclosures(c))
    AND ControlSafe(c) if effectful
    AND EffectSafe(c) if effectful
```

This five-concept, three-predicate model is the normative MADRE Security Algebra.
