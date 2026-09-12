# MADRE Security Algebra

## Purpose

The algebra is the reusable value system through which MADRE objects describe their
actual information, receiving, causal, and physical-realization roles. Values are
immutable and combine structurally as participants are assembled.

No runtime component owns the algebra. A Module author uses its SDK objects while
constructing a Module, Material, public surface, or bounded Operation call. Kernel
extension code uses the same values while assembling installed Capability surfaces.

## Typed carriers

`Sensitivity`, `Privacy`, `Integrity`, `Risk`, and `Autonomy` are different ordered
types with ranks 1 through 5. They cannot be substituted for one another.

- Sensitivity accumulates by maximum.
- Privacy accumulates by minimum.
- Integrity accumulates by minimum.
- Risk and Autonomy remain paired on one EffectProfile.

Applicability is represented by the type of surface being built. An `InputSurface`
has Privacy. An `OutputSurface` has Sensitivity. A `ResponsibilitySurface` has
Integrity. An `EffectProfile` has Risk and Autonomy. There is no universal container
with empty facet slots.

`Privacy.UNKNOWN` is P2: an explicit receiving boundary outside the owner's control
that is not declared fully public. It is not missing information. Installation must
supply Privacy explicitly. Physical location and provider identity do not derive it.

Integrity means only bounded causal or physical-realization responsibility.

## Information composition

An immutable carried-information value owns the actual nonempty Material or outbound
surfaces being exposed. An immutable receiving value owns the actual nonempty input
surfaces being reached.

```text
S = maximum Sensitivity of carried information
P = minimum Privacy of receiving surfaces
composition exists while S <= P
```

Adding another member returns a newly composed value. If the inequality would not
hold, construction raises ordinary `ValueError`; no new value is returned and the
prior immutable objects remain unchanged.

## Bounded Operation composition

An `OperationCall` binds one exact Operation, one of its own EffectProfiles, its exact
input Material, the actual non-user causal participants, and the actual physical
realizers.

For profile Risk `R` and Autonomy `A`:

```text
min(R, A) <= minimum Integrity of actual non-user causal participants
             or I5 when that set is empty

R <= minimum Integrity of actual physical realizers
the physical-realizer set is nonempty
```

Only the values of that one profile participate. Construction returns a valid
`OperationCall` or raises ordinary `ValueError` without producing one.

Autonomy describes the execution variant. User presence does not change information
composition.

## Structural aggregates

Collections of the same role derive their carrier from their members. An aggregate
cannot declare a separate summary rank.

Module Sensitivity is the maximum of its actual current owned/reachable Material and
declared public outputs. Agent Privacy is the minimum of the exact Operation input
surfaces that Agent exposes. Narrowing those members changes the derived value without
special Module or Agent rules.

## Independent Material

Every semantic adaptation constructs new `Material` with its own nominal identity,
type, payload, owner, and explicit Sensitivity. The source Material is unchanged.
Historical metadata, if a Module wants it, remains ordinary Module metadata and has
no algebraic effect.

Execution attempts and diagnostics likewise have no algebraic effect. Only the exact
members of the value being constructed participate.
