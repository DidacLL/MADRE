# MADRE Security Algebra

## Algebraic carriers

MADRE defines five nominally different ordered carriers with ranks 0 through 5.
Rank 0 is system-reserved in every carrier and is not an ordinary Module, Agent,
Operation, Material, EffectProfile, causal-participant, or installed-Capability value.
Ordinary values occupy ranks 1 through 5.

```text
Privacy      SYSTEM_RESERVED(0), PUBLIC(1), UNKNOWN(2), LOCAL(3), MODULE(4), SECRET(5)
Sensitivity  SYSTEM_RESERVED(0), S1(1), S2(2), S3(3), S4(4), S5(5)
Integrity    SYSTEM_RESERVED(0), I1(1), I2(2), I3(3), I4(4), I5(5)
Risk         SYSTEM_RESERVED(0), READ(1), WRITE(2), DELETE(3), EXECUTE(4), POTENTIALLY_HARMFUL(5)
Autonomy     SYSTEM_RESERVED(0), LIVE_INTERACTION(1), ASK_ALWAYS(2), ASK_ONCE(3), ACKNOWLEDGE(4), AUTONOMOUS(5)
```

Privacy names express the actual confidentiality boundary:

- `PUBLIC`: exposure to any receiver is acceptable;
- `UNKNOWN`: handling is outside the stronger owner-local, Module, or secret boundary
  but is not declared public;
- `LOCAL`: confined to the owner's local MADRE environment;
- `MODULE`: confined to the owning Module boundary;
- `SECRET`: strongest ordinary confidentiality boundary.

`P1` through `P5` may be used only as textual Privacy rank notation at installation
boundaries. They map to `PUBLIC`, `UNKNOWN`, `LOCAL`, `MODULE`, and `SECRET`. Rank 0 is
never an ordinary installation fact.

A carrier accepts only values of its own type. Its combination returns a value of that
same nominal type:

```text
S1 + S2 = max(S1, S2)
P1 + P2 = min(P1, P2)
I1 + I2 = min(I1, I2)
```

The plus sign denotes structural accumulation, not arithmetic. Values are immutable.
Risk and Autonomy do not form general aggregates. They remain paired on one exact
EffectProfile.

## Applicability

Algebraic values belong directly to the MADRE object or declared contract where their
meaning applies:

- Material has Sensitivity;
- an Operation's accepted Material boundary has Privacy;
- an Operation's promised Material result has Sensitivity;
- an Agent derives Privacy from the Operations it exposes;
- a Module derives Sensitivity from Material and outputs actually reachable through it;
- a physical Capability manifest currently has receiving Privacy and, where applicable,
  physical Integrity;
- an actual non-user causal participant has Integrity;
- an EffectProfile has Risk and Autonomy.

An object without one of those responsibilities has no field for that carrier. No
generic all-facets container or separately identified security surface is part of the
model.

`Privacy.UNKNOWN` is ordinary rank 2. It is not a missing value and is not the
system-reserved rank. Locality, endpoint, provider, model, process placement,
class-loader placement, Module bundling, or CORE designation never derives an
algebraic value.

## Reachability of information

For the exact information and receiver being connected:

```text
S = maximum Sensitivity carried into the connection
P = minimum Privacy of the receiver

connection exists iff S <= P
```

The comparison belongs to the values being composed. Existing accumulated values are
unchanged when the next value cannot join, and no new aggregate exists.

A Module may own S5 Material and deliberately transform it into new S4, S3, S2, or S1
Material. Each result requires a new Material identity and explicit Sensitivity chosen
and justified by Module behavior. The source remains S5.

The executable PUBLIC Operation boundary is one concrete use of that rule: the Module
must create new result Material that can reach `Privacy.PUBLIC` before it leaves the
external/public invocation boundary. The runtime does not invent the transformation
or derive a lower Sensitivity itself.

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
independent construction. Owner participation is represented by the profile's actual
Autonomy and never changes information reach.

## System-reserved rank zero

Rank 0 exists so the algebra has a system-reserved element in every carrier without
pretending that it is an ordinary domain classification. Ordinary public SDK objects
reject rank 0 where a Module, Agent, Operation, Material, EffectProfile or actual
causal-participant fact is required. Installed physical manifests likewise reject
rank 0 where an ordinary receiving Privacy or physical Integrity fact is required.

This is an intrinsic value/model invariant. It does not create a policy evaluator,
permission service, decision wrapper, exception path, or security retry mechanism.

## Use in Modules and current Kernel extensions

Module authors construct Material, Operations, EffectProfiles, and accumulated values
through the SDK. Where Module behavior submits current physical work, the WorkRequest
carries the exact values already composed from its bounded call and Kernel can select
only a physical mechanism whose manifest composes with them.

Composition creates values, not observations about values. Logging, scheduling,
attempts, physical retry, diagnostics, Module installation, discovery, and CORE role
lookup are ordinary runtime concerns and confer no algebraic privilege.
