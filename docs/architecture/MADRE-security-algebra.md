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

Privacy names describe actual confidentiality boundaries:

- `PUBLIC`: disclosure to any receiver is acceptable;
- `UNKNOWN`: an applicable third-party boundary outside stronger owner-controlled boundaries;
- `LOCAL`: confined to the owner's local MADRE environment;
- `MODULE`: confined to the owning Module boundary;
- `SECRET`: strongest ordinary confidentiality boundary.

`P1` through `P5` may be used only as textual installation notation for the five ordinary Privacy values. Rank 0 is never an ordinary installation fact.

A carrier combines only with values of its own nominal type:

```text
Sensitivity -> maximum
Privacy     -> minimum
Integrity   -> minimum
```

Values are immutable. Risk and Autonomy do not form general aggregates; they stay paired on one exact EffectProfile.

## Applicability

Algebraic values attach directly to the real MADRE object or contract where their meaning applies:

- Material carries Sensitivity;
- an Operation's accepted Material boundary carries Privacy;
- an Operation declares maximum Sensitivity for Material it may produce;
- an Agent derives effective Privacy from Operations it exposes;
- a Module derives effective Sensitivity from reachable Material and declared outputs;
- one EffectProfile carries Risk and Autonomy;
- an actual non-user causal participant contributes Integrity;
- one installed reasoning mechanism declares the Privacy of its receiving boundary.

A `ReasoningCapability` does not carry action-realizer Integrity. Operation Risk is not a reasoning-selection input. A reasoning mechanism computes information; it does not thereby become the external effect represented by a Module Operation's EffectProfile.

An object without responsibility for a carrier has no field for it. There is no universal security-facet container, separately identified security surface, policy-decision object, evidence record or security history model.

Locality, provider/model identity, endpoint, process placement, class-loader placement, Module bundling and CORE designation never derive an algebraic value.

## Information reach

For the exact information and receiver being connected:

```text
S = maximum Sensitivity carried into the connection
P = minimum Privacy of the receiver

connection exists iff S <= P
```

Existing values remain unchanged when another participant cannot compose, and no rejected-result domain object is created.

A Module may deliberately transform S5 Material into new S4, S3, S2 or S1 Material. Each result requires a new nominal Material identity and explicit Sensitivity justified by Module behavior. The source remains S5.

The executable PUBLIC Operation boundary is one use of this rule: before internal result Material crosses the external boundary, the Module must create new declared Material able to reach `Privacy.PUBLIC`. Runtime validation does not invent the transformation.

Owner-local invocation is a different receiver boundary. The owner receiving contract-valid Material inside their own MADRE installation does not imply public disclosure and therefore does not require the PUBLIC transformer or a new S1 Material. The Module-created Sensitivity remains unchanged. This distinction does not add an `OWNER` Privacy value, trusted-user Integrity level or another algebraic carrier; it is an invocation/receiver boundary in runtime architecture.

Reasoning selection is another use: carried request Sensitivity must be able to reach the selected reasoning mechanism's explicit receiving Privacy.

## Bounded consequential Operation execution

For one exact EffectProfile:

```text
D = min(profile Risk, profile Autonomy)

D <= minimum Integrity of actual non-user causal participants
     or I5 when no such participant exists
```

Only that EffectProfile contributes Risk and Autonomy. Another profile is an independent construction. Owner presence is represented by the profile's actual Autonomy and never changes information reach.

The current public `OperationCall` checks this causal composition before bounded behavior executes. Owner-local and PUBLIC invocation both use the same real `OperationCall`; neither may bypass this composition. A host caller supplies only actual non-user causal participants rather than fabricating Integrity values for a user or CLI.

Reasoning computation alone does not justify an EffectProfile. The shipped owner-interaction `standard-prompt` therefore has no profile. Its `fast-lane` profile is justified by durable/persistent write consequences, and its `collect-background` profile is justified by acknowledgement/cleanup consequences rather than by inference.

No Kernel policy authority is involved.

The previous generic physical-action design also attempted to carry Risk into mechanism selection and model physical-realizer Integrity on generic Capability manifests. That is intentionally removed. Reasoning mechanisms are not generic action realizers.

## System-reserved rank zero

Rank 0 gives every carrier a system-reserved element without pretending it is an ordinary domain classification. Public SDK objects reject rank 0 wherever an ordinary Material, Operation, EffectProfile, causal participant or installed reasoning Privacy fact is required.

This invariant creates no policy evaluator, permission service, decision wrapper, exception lifecycle or security retry mechanism.

## Runtime concerns are not algebra

Composition produces values, not observations about values. Logging, scheduling, attempts, retry, diagnostics, Module installation/discovery, owner-local/public receiver selection, CORE role lookup, reasoning availability and resource reservation are ordinary runtime concerns and confer no algebraic privilege.
