# MADRE Security Algebra

## Purpose

The Security Algebra carries only the facts required to determine whether exact
current scopes can compose. It is a standalone public value system. It has no
evaluator, broker, policy service, observability role, persistence model, or history.

## Carriers

`Sensitivity`, `Privacy`, `Integrity`, `Risk`, and `Autonomy` are five
different ordered types with ranks 1 through 5.

- Sensitivity belongs to an exact scope whose exposure has a confidentiality
  consequence.
- Privacy belongs to an exact observer scope.
- Integrity belongs to an exact non-user controller or effect-realizing executor.
- Risk and Autonomy belong together on one exact Operation EffectProfile.

The rank values support ordering only within their carrier. They never form a global
score.

`Privacy.UNKNOWN` is an applicable Privacy fact at rank 2. A missing Privacy value
means Privacy does not apply to that scope. Locality and provider identity are not
inputs to Privacy.

Integrity is bounded causal/effect-realization responsibility. It says nothing about
truth, intelligence, reliability, provider quality, reputation, or generic trust.

## Exact scopes and surfaces

A `SecurityScope` binds the applicable Sensitivity, Privacy, and/or Integrity facts
to one exact scope identity and revision.

A `SecuritySurface` is the immutable structural composition of the scopes exposed
or reached together. It is associative, commutative, and idempotent for identical
scope facts. Conflicting facts for the same exact identity cannot compose.

For the applicable members of a surface:

```text
S(surface) = max Sensitivity
P(surface) = min Privacy
I(surface) = min Integrity
```

If no member carries a facet, that facet is not applicable to the surface.

This same composition describes nested structures. A Module surface includes the
Material and subscopes it actually manages or exposes. An Agent surface includes the
Operations and other surfaces it actually exposes. A narrower isolated surface is
free to have a different value from its owner’s complete surface.

Aggregate values are never separately declared. They are consequences of the exact
included scopes.

## Disclosure

A `Disclosure` is only this relation:

```text
S_D = S(actual exposed source surface)
P_D = P(actual observer surface)

match iff S_D <= P_D
```

Both facets must apply. Adding a source joins Sensitivity by maximum. Adding an
observer meets Privacy by minimum. An addition that violates the inequality raises
`SecurityMismatch`; the prior immutable Disclosure remains unchanged and no rejected
relation exists.

There are no extra, implied, historical, or framework-supplied observers. The actual
observer surface already carries the composition of what it exposes.

There is no user exception. Presence, approval, acknowledgement, or a live session
cannot change this relation.

## Control

One EffectProfile supplies Risk and Autonomy:

```text
D_C(R,A) = min(R,A)

I_C = min Integrity of actual non-user controllers
      or I5 when there are none

match iff D_C(R,A) <= I_C
```

A human user is not entered as an Integrity-bearing controller. A1 describes an exact
live user action through Autonomy; A2 through A5 describe progressively greater
residual machine execution. Autonomy affects only Control.

Adding a non-user controller meets Integrity by minimum. An incompatible addition
cannot construct a new Control value.

## Effect execution

```text
I_E = min Integrity of actual effect-realizing executors
executor surface must be nonempty

match iff R <= I_E
```

Only the executors that physically realize this exact bounded effect participate.
Adding another executor meets Integrity by minimum. An incompatible addition cannot
construct a new EffectExecution value.

Risk or Autonomy values from separate EffectProfiles never combine.

## New Material

Any transformation, selection, minimization, tokenization, anonymization, validation,
summary, or other adaptation produces new Material with:

- a new Material identity;
- its own content contract and payload;
- its own exact applicable security facts.

No algebraic rule universally derives the new Sensitivity or Integrity from prior
Material. The Module performing the semantic work owns the new facts. The original
Material remains unchanged.

MADRE defines no derivation record, ancestry, continuity, freshness, completed-output
set, lineage graph, validation projection, or security retry. Optional provenance
would be separate metadata and could not become an algebra operand.

## Failure and observability

`SecurityMismatch` reports why one attempted construction could not produce a
composite. It is transient failure information.

A runtime may log that an execution request failed, just as it may log a timeout or
unavailable mechanism. Such diagnostics are not security state, do not modify any
scope, and cannot authorize or constrain a later request.
