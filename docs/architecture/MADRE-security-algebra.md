# MADRE Security Algebra

## Algebraic carriers

MADRE defines five nominally different ordered carriers with ranks 1 through 5:

```text
Sensitivity  S1 ... S5
Privacy      P1 ... P5
Integrity    I1 ... I5
Risk         R1 ... R5
Autonomy     A1 ... A5
```

A carrier accepts only values of its own type. Its combination returns a new value of
that same type:

```text
S₁ + S₂ = max(S₁, S₂)
P₁ + P₂ = min(P₁, P₂)
I₁ + I₂ = min(I₁, I₂)
```

The plus sign denotes structural accumulation, not arithmetic. Values are immutable.

Risk and Autonomy do not form general aggregates. They remain paired on one exact
EffectProfile.

## Applicability

Algebraic values belong directly to the MADRE object or declared contract where their
meaning applies:

- Material has Sensitivity.
- An Operation's accepted Material boundary has Privacy.
- An Operation's promised Material result has Sensitivity.
- An Agent derives Privacy from the Operations it exposes.
- A Module derives Sensitivity from Material and outputs actually reachable through
  it.
- A Capability manifest has receiving Privacy and, where applicable, physical
  Integrity.
- An actual non-user causal participant has Integrity.
- An EffectProfile has Risk and Autonomy.

An object without one of those responsibilities has no field for that carrier. No
generic all-facets container or separately identified surface is part of the model.

`Privacy.UNKNOWN` is P2. It explicitly represents applicable handling outside the
owner's control that is not declared public. It is not a missing value. P1 is public.
An installation supplies the receiving value as a fact. Locality, endpoint, provider,
model, or adapter never derives it.

## Reachability of information

For the exact information and receiver being connected:

```text
S = maximum Sensitivity carried into the connection
P = minimum Privacy of the receiver

connection exists iff S <= P
```

The comparison belongs to the values being composed. Existing accumulated values are
unchanged when the next value cannot join, and no new aggregate exists.

Examples:

```text
S2 + S5 -> S5
P5 + P3 -> P3
S2 reaches P2
S5 does not reach P2
```

A Module may own S5 Material and publish an Operation that actually tokenizes it into
new S4 Material, another that anonymizes it into new S3 Material, and another that
minimizes it into new S2 Material. Each result is a new Material chosen and justified
by Module behavior. The source remains S5.

An Agent that exposes Operations accepting P5 and P3 has effective P3. Removing the
P3 Operation changes the Agent's effective value to P5. A Module's current value
likewise changes with the Material and outputs currently reachable through it.

## Bounded Operation execution

For one Operation EffectProfile:

```text
D = min(profile Risk, profile Autonomy)

D <= minimum Integrity of actual non-user causal participants
     or I5 when no such participant exists

profile Risk <= minimum Integrity of actual physical realizers
physical realizers are nonempty
```

Only that EffectProfile contributes Risk and Autonomy. Another profile is an
independent construction.

Owner participation is represented by the profile's actual Autonomy. It never changes
information reach.

## Use in Modules and Kernel extensions

Module authors construct Material, Operations, EffectProfiles, and accumulated values
through the SDK. Kernel extension code constructs Capability manifests through the
same carrier types.

A work request carries the already accumulated values applicable to its physical
input. Kernel can only select a Capability whose manifest values compose with that
request. The selected Capability receives only physical input; algebraic values do
not cross into the connector invocation.

Composition creates values, not observations about values. Logging, scheduling,
attempts, physical retry, and diagnostics are ordinary runtime concerns.
