# MADRE Security Algebra

## Algebraic carriers

MADRE defines five nominally distinct ordered carriers with ranks 0 through 5. Rank 0 is `SYSTEM_RESERVED` and is not an ordinary Module, Agent, Operation, Material, EffectProfile, causal-participant or installed reasoning fact. Ordinary values occupy ranks 1 through 5.

```text
Privacy      SYSTEM_RESERVED(0), PUBLIC(1), UNKNOWN(2), LOCAL(3), MODULE(4), SECRET(5)
Sensitivity  SYSTEM_RESERVED(0), S1(1), S2(2), S3(3), S4(4), S5(5)
Integrity    SYSTEM_RESERVED(0), I1(1), I2(2), I3(3), I4(4), I5(5)
Risk         SYSTEM_RESERVED(0), READ(1), WRITE(2), DELETE(3), EXECUTE(4), POTENTIALLY_HARMFUL(5)
Autonomy     SYSTEM_RESERVED(0), LIVE_INTERACTION(1), ASK_ALWAYS(2), ASK_ONCE(3), ACKNOWLEDGE(4), AUTONOMOUS(5)
```

Privacy names describe actual confidentiality receiver boundaries:

- `PUBLIC`: disclosure to any receiver is acceptable;
- `UNKNOWN`: an applicable third-party boundary outside stronger owner-controlled boundaries;
- `LOCAL`: confined to the owner's local MADRE environment;
- `MODULE`: confined to an installed Module receiver boundary;
- `SECRET`: strongest ordinary confidentiality boundary.

`P1` through `P5` may be used only as textual installation notation for the five ordinary Privacy values. Rank 0 is never an ordinary installation fact.

A carrier combines only with values of its own nominal type:

```text
Sensitivity -> maximum
Privacy     -> minimum
Integrity   -> minimum
```

Values are immutable. Risk and Autonomy do not form general aggregates; they stay paired on one exact EffectProfile.

R0 does not change these carriers, ranks, combination rules or reachability equations.

## Applicability

Algebraic values attach directly to the real object or contract where their meaning applies:

- Material carries Sensitivity;
- an Operation's accepted Material boundary carries Privacy;
- an Operation declares maximum Sensitivity for Material it may produce;
- an Agent derives effective Privacy from the Operations associated with it;
- a Module may derive effective Sensitivity from reachable Material and outputs promised through its exposed interface;
- installed Module-to-Module delivery uses `Privacy.MODULE`;
- one EffectProfile carries Risk and Autonomy;
- an actual non-user causal participant contributes Integrity;
- one installed reasoning mechanism declares the Privacy of its receiving boundary.

A `ReasoningCapability` does not carry action-realizer Integrity. Operation Risk is not a reasoning-selection input. A reasoning mechanism computes information; it does not thereby become the external effect represented by a Module Operation's EffectProfile.

An object without responsibility for a carrier has no field for it. There is no universal security-facet container, separately identified security surface, policy-decision object, evidence record or security history model.

Module exposure, generic owner/debug entry, owner-interaction entry, public-disclosure binding and CORE assignment are not Algebra carriers.

Locality, provider/model identity, endpoint, process placement, class-loader placement, Module bundling, shipped status and CORE designation never derive an algebraic value.

## Information reach

For the exact information and receiver being connected:

```text
S = maximum Sensitivity carried into the connection
P = minimum Privacy of the receiver

connection exists iff S <= P
```

Existing values remain unchanged when another participant cannot compose, and no rejected-result domain object is created.

A Module may deliberately transform S5 Material into new S4, S3, S2 or S1 Material. Each result requires a new Material identity and explicit Sensitivity justified by Module behavior. The source remains S5.

### Actual external/public disclosure

The external/public receiver boundary is one use of information reach. Before internal result Material crosses that boundary, the Module must explicitly create transformed/minimized Material through its `PublicResultTransformer`. Runtime requires the new Material to satisfy the declared output contract and reach `Privacy.PUBLIC`.

`Privacy.PUBLIC` describes that receiver. It does not mean an Operation is exposed to other Modules and is not an access-control/visibility marker.

### Module-to-Module delivery

Module composition is a different receiver boundary. The target Operation must first be part of the target Module's exposed interface; that exposure fact is outside the Algebra.

The caller must structurally declare the nominal Material contract it can receive. Runtime binds actual caller identity and uses fixed receiver Privacy `Privacy.MODULE`.

Concrete Material ownership is independent from nominal type ownership. A callee-owned concrete Material value may conform to a nominal contract defined by the caller or another Module. When the caller structurally declares that contract and the result Sensitivity can reach `Privacy.MODULE`, the exact callee value crosses unchanged. Its `MaterialId` remains callee-owned and its `MaterialTypeId` remains owned by the contract-defining Module.

An undeclared nominal contract or S5 result cannot reach the Module receiver. `PublicResultTransformer` does not participate in this connection.

### Generic owner/debug entry

Host expert/debug invocation is another runtime boundary. Returning contract-valid Material to the Owner inside the installation does not imply public disclosure and therefore does not require a `Privacy.PUBLIC` transformation. The Module-created Sensitivity remains unchanged.

This does not add an `OWNER` Privacy value or trusted-user Integrity level. Owner/debug entry is a host concern outside the Algebra.

### Owner interaction

Ordinary owner interaction is also a host/product entry concern, not a new Privacy carrier. The Module assigned CORE may expose exact owner-interaction bindings to the host, but CORE assignment and presentation do not alter Material Sensitivity or receiver Privacy.

Every interaction entry still uses an ordinary `OperationCall`; the same accepted-Material Privacy and consequential-effect rules apply.

### Reasoning selection

Carried request Sensitivity must be able to reach the selected reasoning mechanism's explicit receiving Privacy.

When Module-owned semantic context combines several source values before reasoning, the context is represented as actual Material at the combined maximum Sensitivity. The `ReasoningRequest` is then derived structurally from a bounded `OperationCall` over that contextual Material. Historical S4 information combined with a current S2 prompt therefore yields at least S4 contextual Material; no raw carried-Sensitivity override exists.

## Bounded consequential Operation execution

For one exact EffectProfile:

```text
D = min(profile Risk, profile Autonomy)

D <= minimum Integrity of actual non-user causal participants
     or I5 when no such participant exists
```

Only that EffectProfile contributes Risk and Autonomy. Another profile is an independent construction. Owner presence is represented only by the profile's actual Autonomy and never changes information reach.

The public `OperationCall` checks this causal composition before bounded behavior executes. Module-to-Module invocation, generic owner/debug entry, owner interaction and external/public disclosure all enter the same bounded call model; none bypasses it.

Reasoning computation alone does not justify an EffectProfile. Consequential Module behavior does. The shipped owner-interaction Module's state-write, durable-background and acknowledgement/cleanup profiles are justified by those actual consequences, not by inference itself.

No Kernel policy authority is involved.

## System-reserved rank zero

Rank 0 gives every carrier a system-reserved element without pretending it is an ordinary domain classification. Public SDK objects reject rank 0 wherever an ordinary Material, Operation, EffectProfile, causal participant or installed reasoning Privacy fact is required.

This invariant creates no policy evaluator, permission service, decision wrapper, exception lifecycle or security retry mechanism.

## Runtime concerns are not Algebra

Composition produces values, not observations about values. Logging, scheduling, attempts, retry, diagnostics, Module installation/discovery, Module exposure, generic owner/debug selection, owner-interaction selection, external/public receiver selection, CORE lookup, reasoning availability and resource reservation are ordinary runtime concerns and confer no algebraic privilege.

Do not reinterpret `Privacy.PUBLIC` as Module exposure. Do not introduce a replacement access-control/visibility lattice into Security Algebra. Cross-Module exposure is represented by the Module interface; actual public disclosure remains a real receiver Privacy boundary.
