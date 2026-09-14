# MADRE Security Algebra

## Algebraic carriers

MADRE defines five nominally different ordered carriers with ranks 0 through 5.
Rank 0 is system-reserved in every carrier and is not an ordinary Module, Agent,
Operation, Material, EffectProfile, causal-participant, or installed-Capability value.
Ordinary values occupy ranks 1 through 5.

```text
Sensitivity  SYSTEM_RESERVED(0), S1 ... S5
Privacy      SYSTEM_RESERVED(0), PUBLIC(1), UNKNOWN(2), LOCAL(3), MODULE(4), SECRET(5)
Integrity    SYSTEM_RESERVED(0), I1 ... I5
Risk         SYSTEM_RESERVED(0), R1 ... R5
Autonomy     SYSTEM_RESERVED(0), A1 ... A5
```

Privacy names express the actual confidentiality boundary:

- `PUBLIC` (1): exposure to any receiver is acceptable.
- `UNKNOWN` (2): not public to everyone, but handling is outside the stronger
  owner-local, Module, or secret boundaries; this is also the rank used when the
  installation knows only that shared/non-public handling applies.
- `LOCAL` (3): confined to the owner's local MADRE environment.
- `MODULE` (4): confined to the owning Module boundary.
- `SECRET` (5): strongest ordinary confidentiality boundary.

`P1` through `P5` may be used as textual rank notation at installation boundaries,
but they are not the Java domain names for Privacy. `P1` maps to `PUBLIC`, `P2` to
`UNKNOWN`, `P3` to `LOCAL`, `P4` to `MODULE`, and `P5` to `SECRET`. Rank 0 is never an
ordinary installation fact.

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

`Privacy.UNKNOWN` is ordinary rank 2. It explicitly represents applicable handling
that is not public but for which a stronger owner-controlled boundary is not declared.
It is not a missing value and is not the system-reserved rank. An installation supplies
the receiving value as a fact. Locality, endpoint, provider, model, or adapter never
derives it.

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
SECRET + LOCAL -> LOCAL
S2 reaches UNKNOWN
S3 reaches LOCAL
S4 reaches MODULE
S5 reaches SECRET
S5 does not reach UNKNOWN
```

A Module may own S5 Material and publish an Operation that actually tokenizes it into
new S4 Material, another that anonymizes it into new S3 Material, and another that
minimizes it into new S2 Material. Each result is a new Material chosen and justified
by Module behavior. The source remains S5.

An Agent that exposes Operations accepting `SECRET` and `LOCAL` has effective
`LOCAL` Privacy. Removing the `LOCAL` Operation changes the Agent's effective value to
`SECRET`. A Module's current value likewise changes with the Material and outputs
currently reachable through it.

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

## System-reserved rank zero

Rank 0 exists so the algebra has a system-reserved element in every carrier without
pretending that it is an ordinary domain classification. Ordinary public SDK objects
reject rank 0 where a Module, Agent, Operation, Material, EffectProfile or actual
causal-participant fact is required. Installed Capability manifests likewise reject
rank 0 as a receiving Privacy or physical Integrity fact.

This is an intrinsic value/model invariant. It does not create a policy evaluator,
permission service, decision wrapper, exception path, or security retry mechanism.

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
