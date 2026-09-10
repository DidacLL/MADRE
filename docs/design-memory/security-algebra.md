# Security Algebra Design Memory

Authority: non-normative design memory. Canonical security architecture is `docs/architecture/MADRE-security-algebra.md`.

This file preserves the reasoning and discriminating cases behind the frozen algebra so later development does not reconstruct or expand it from convention.

## Why the algebra exists

**OWNER — CONFIRMED**

MADRE needs a compact deterministic way to evaluate the concrete consequence of moving material through Agents, Modules, Operations and inference mechanisms without requiring case-by-case user approval for every internal step.

The original intuition was a small common `1..5` normalized scale with `0` reserved for the system. The scale exists so security-relevant facts can compose mechanically; it was never intended to create a conventional permission matrix.

## Final conceptual split

**OWNER + MATHEMATICAL AUDIT — CONFIRMED**

The security problem separates into two observable planes:

```text
information disclosure
causal control / physical realization of bounded effects
```

This is why the final algebra needs explicit transition roles rather than reducing all carried SecurityObjects into one global score.

A SecurityObject describes a subject. A SecurityTransition describes how a subject participates in one concrete prospective crossing/effect.

The final role vocabulary is:

```text
DISCLOSURE
CONTROL
EFFECT_EXECUTION
DERIVATION
```

`DERIVATION` records security-relevant transformation history; it is not itself a numeric admission predicate.

## Why the final five concepts survive

**CONFIRMED**

The frozen normalized vocabulary is:

```text
Sensitivity
Privacy
Integrity
Risk
Autonomy
```

Each concept preserves a required distinction:

- `Sensitivity`: public material versus secret material on the same path;
- `Privacy`: a strongly private/local path versus a weak/exposed path;
- `Integrity`: a strong versus weak causal controller or effect executor;
- `Risk`: harmless versus destructive bounded effect;
- `Autonomy`: direct-user-controlled versus autonomous realization of the same consequence.

Removing any one collapses a case MADRE needs to distinguish.

## Why earlier confidentiality sub-properties do not survive

**MATHEMATICAL AUDIT — CONFIRMED**

An intermediate candidate modeled actor confidentiality as:

```text
min(ConfidentialityAssurance, Isolation)
```

and Capability confidentiality as:

```text
min(ConfidentialityAssurance, Privacy)
```

No later runtime predicate could observe those components separately; it only consumed their minima.

Therefore they are exactly quotientable into one pre-valued `Privacy` assurance without changing any runtime decision.

Detailed reasons why an object receives `Privacy=3`—sandboxing, locality, retention, network exposure, isolation, contractual/platform controls, etc.—belong to valuation/audit evidence rather than the runtime algebra.

This is important to MADRE's intended simplicity: SecurityObjects expose the resulting normalized facts the evaluator actually needs instead of reconstructing a large threat model during each transition.

## Why numeric IntendedUse does not survive

**OWNER + AUDIT — CONFIRMED**

The underlying concept behind `IntendedUse` remains valid: how material participates in a concrete action matters.

The mistake would be representing that meaning as another ordered number when the real distinction is structural.

For example, the same Artifact may be:

```text
merely disclosed to an Agent
used as a causal control input to a bounded Operation
used by an effect path that itself publishes/discloses material
transformed into a new representation
```

These are better represented by `DISCLOSURE`, `CONTROL`, `EFFECT_EXECUTION` and `DERIVATION` relationships.

This preserves the Owner intention behind IntendedUse while making the algebra more precise and avoiding a purpose/permission level with no defensible ordering.

## Why generic Trust does not survive

**AUDIT — CONFIRMED**

A generic `Trust` number conflated two different obligations: protecting disclosed material and preserving security-significant causal/effect integrity.

Those are now represented directly by `Privacy` and `Integrity`.

Neither means semantic truth or model quality.

## Why Capability Risk does not survive

**AUDIT — CONFIRMED**

Capability data exposure belongs to `Privacy`; control/execution fidelity belongs to `Integrity`; bounded external consequence belongs to an Operation EffectProfile's `Risk`.

A generic Capability `Risk` would otherwise overlap these meanings or drift into model-quality semantics that MADRE security does not own.

## EffectProfile and Autonomy

**OWNER + MATHEMATICAL AUDIT — CONFIRMED**

Autonomy cannot be supplied as an arbitrary invocation number. That would allow callers to claim `Autonomy=1` to weaken a security boundary.

At the same time, attaching only one fixed Autonomy value to a generic Operation cannot represent a legitimate Operation that supports both direct-user-controlled and autonomous execution shapes.

The solution is an immutable Operation-owned `EffectProfile` containing its bound:

```text
Risk
Autonomy
Integrity
Privacy when the effect itself discloses material
```

A concrete transition selects a bound EffectProfile; it does not invent its values.

This models user involvement as a different physical/control topology rather than an authorization bit.

## Why control demand is min(Risk, Autonomy)

**MATHEMATICAL AUDIT — CONFIRMED**

Risk and Autonomy jointly determine the amount of machine-control integrity required:

```text
ControlDemand = min(Risk, Autonomy)
```

This has the intended behavior:

- destructive + fully autonomous -> high control-integrity demand;
- destructive + exact/direct user control -> low machine-control demand;
- harmless + highly autonomous -> low control-integrity demand.

The Operation still independently faces the full effect-execution gate `Risk <= executor Integrity`, so direct user control never makes a weak destructive implementation safe.

This separation is crucial.

## Why Integrity is necessary

**MATHEMATICAL AUDIT — CONFIRMED**

Sensitivity/Risk/Autonomy cannot distinguish a weak causal input from a validated one.

Example:

```text
public attacker-controlled string -> destructive autonomous Operation
```

The material may have low Sensitivity, but it is not safe to let it drive a high-consequence autonomous effect merely because the disclosure plane passes.

Material/actor/Capability Integrity provides the control-plane assurance needed to express that case.

Integrity is not factual truth. It concerns security-significant manipulation resistance and contract fidelity.

## Effect execution is separate from causal control

**MATHEMATICAL AUDIT — CONFIRMED**

A high-risk direct-user-controlled Operation can have low machine-control demand while still requiring a high-Integrity execution path.

This prevents user confirmation from masking an unreliable destructive implementation.

That distinction captures the same underlying concern that earlier speculative `IntendedUse` reasoning was trying to express: what the material/action actually does in the prospective transition matters.

## Transition locality and history

**OWNER + AUDIT — CONFIRMED**

Security history is immutable evidence, not one global reduction domain.

A secret `Sensitivity=5` may be legitimately transformed into a distinct minimized representation `Sensitivity=2`. A later transition that discloses only the new representation evaluates level 2; the level-5 source remains in derivation history but does not numerically poison unrelated later edges.

Conversely, a weak participant that actually receives the original secret cannot later be erased by stronger participants: that disclosure transition has already produced immutable decision/evidence.

A rejected candidate Capability does not become historical exposure merely because it was considered.

Objects/history compose by idempotent keyed union. Causal ordering that matters is represented explicitly by transition/derivation relationships rather than tuple insertion order.

## Generated output and validation

**OWNER — CONFIRMED; AUDIT REFINED**

Generated output remains ordinary Module-owned material after delivery and may be reused as later input, state, evidence, WorkPlan material or bounded Operation input.

Its security role is transition-dependent.

Low-Integrity generated material merely displayed to a user is not a causal controller of an effect. The same material directly choosing a destructive autonomous Operation target is a controller and participates in the control-integrity predicate.

A Module may create a new validated representation with stronger Integrity through an explicit validation transformation. The source remains immutable history; the new representation receives a new SecurityID.

## Unknown/third-party valuation remains separate

**OPEN PRODUCT/VALUATION MECHANISM; ALGEBRA BOUNDARY FROZEN**

The runtime algebra does not silently replace missing values with conservative numbers.

A transition requiring a value that has not been supplied is structurally invalid (`missing_security_value` or equivalent).

A separate future valuation mechanism may use:

```text
conservative defaults
installer/user declarations
concrete adapter/platform facts
MADRE-native SDK declarations
source/code inspection or validation
```

Those mechanisms establish SecurityObject facts. They do not grant authority.

The exact default/third-party valuation process remains open and should be designed from integration evidence rather than built into the algebra.

## Representative formula cases

The frozen formula should continue to be tested against at least:

1. maximum-sensitivity secret through CORE/local private inference;
2. the same secret through a weaker remote path;
3. public material driving a destructive autonomous Operation;
4. the same high-risk effect under a direct-user-control EffectProfile;
5. direct-user control with a weak effect executor;
6. genuinely minimized new material representation crossing externally;
7. low-Integrity generated/external material acting as a controller;
8. explicit validation producing a new higher-Integrity representation;
9. public publishing effect receiving sensitive material;
10. duplicate SecurityObjects, retries/restarts and rejected candidate mechanisms;
11. cross-Module delegation followed by nested inference/effect work.

## Bounded effects simplify the problem

**OWNER — CONFIRMED**

MADRE does not give its Agents generic unrestricted shell or Internet authority. Effects are explicit bounded Operations/mechanisms.

This is what allows the Security Algebra to reason over declared EffectProfiles and concrete transition relationships instead of trying to secure an omnipotent Agent process.

## Mathematical audit status

The final design followed successive adversarial revisions rather than one first-fit formula.

The decisive refinements were:

```text
global carried extrema -> transition-local relationships
single Trust -> separate confidentiality/control obligations
numeric IntendedUse -> structural transition role
Risk+Autonomy complement frontier -> control demand min(Risk,Autonomy)
control demand only -> separate full effect-execution integrity gate
seven runtime quantities -> exact five-concept quotient
implicit unknown defaults -> separate valuation stage
arbitrary invocation autonomy -> immutable bound EffectProfile
```

Finite enumeration over levels `1..5` was used to check monotonicity of the final numeric predicates and to verify that quotienting the earlier confidentiality sub-properties into one Privacy value does not change runtime decisions.

The durable normative result lives only in `docs/architecture/MADRE-security-algebra.md`.
