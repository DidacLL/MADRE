# MADRE Security Algebra V2

Authority: `MADRE.md` defines product meaning. This document defines the public security contracts, transition roles and deterministic evaluation needed for independent Module composition.

## Purpose and ownership

MADRE evaluates concrete representations, disclosure boundaries and bounded effects. It does not assign permission to applications or judge generic AI trustworthiness. Modules retain their data, semantic transformations, interpretation and domain effects. Kernel binds and evaluates public execution facts without understanding private payload meaning.

Encapsulation changes the contract being evaluated. A bounded Operation exposes its permitted effect surface, not the application behind it. A contained participant does not become a separate exposure boundary merely because it processes bytes. Transformations produce new concrete representations; they do not add security points to their inputs.

## Typed contracts

Ordinary projections use the ordered levels 1 through 5. Zero is reserved and invalid for ordinary operands. No addition, subtraction, averaging or universal compensation is defined.

`SecurityObject` provides immutable subject/version binding and a deterministic `SecurityID`. The current concrete value schemas are:

| Bound subject | Values |
| --- | --- |
| Artifact / ContextBundle | Sensitivity, Assurance |
| Actual participating Agent / Module / implementation | Assurance |
| Capability computation | Assurance |
| Disclosure boundary | PrivacyCapacity |
| Operation EffectProfile | RiskEnvelope, Autonomy, realization Assurance, bound public role relationships |
| TransformContract | Contract identity and evidence-schema identity, bound to implementation/version |

Sensitivity describes disclosure consequence of this representation, not all information held by its owner. Assurance describes reliance on a concrete representation or implementation in a security-significant causal/realization role; it is not truth, intelligence, ownership or permission. PrivacyCapacity describes the sensitivity admissible through the actual containment/exposure boundary.

`RiskEnvelope` contains `control_risk` and `effect_risk`, with `1 <= control_risk <= effect_risk <= 5`. Control risk is the maximum consequence attributable to unsafe residual machine choice permitted by this profile. Effect risk is the maximum consequence reachable through realization of the bounded profile.

Autonomy describes how far the profile proceeds without user intervention. A1 requires explicit user participation; A5 proceeds autonomously. Autonomy orders feasible profiles; it never reduces a safety obligation. Generic acknowledgement and exact-target mediation can both be A1 while having different control risk.

These scalars are current projections of typed contracts, not complete descriptions of behavior. Equal projections do not make two contracts semantically interchangeable.

## Identities and topology

Routing identity identifies the Module and exported implementation being invoked. Security identity binds the actual contract or representation used in a calculation. Neither grants anything.

Endpoint attachments capture the exact Module publication and explicit boundary contracts. Forwarding endpoint identity is routing metadata, not another SecurityObject. An endpoint contributes Assurance only through explicit participating implementation contracts when it changes production or realization. Replacement under the same publication version still invalidates a stale attachment.

Execution-bound SDK services preserve the established actor and route independently of caller-supplied history. They expire when that execution ends. There is no ambient execution context. This is the public SDK contract, not process isolation from arbitrary Python/private host access.

Transitions use:

```text
DISCLOSURE: material representation -> actual serial boundary contracts
CONTROL: EffectProfile <- actual residual controller representations/implementations
EFFECT_EXECUTION: EffectProfile <- actual realization mechanisms
DERIVATION: sources -> new representation, actual producers, optional TransformContract
```

Delivery records identify destinations. Do not create another recipient-security list solely for logging. Do not infer control, production or execution roles from ownership, enclosure or historical participation.

The prototype's EffectProfile binds whether the invocation material and caller retain consequential control, additional explicit controllers/executors, and publication boundaries. These are properties of the exported execution contract, never per-call overrides. A profile that mediates a choice must actually implement that behavior. Kernel does not infer mediation from an acknowledgement or payload. Caller-declared additional semantic controllers can add facts, not remove the profile's required roles. The active Agent is used for an Agent selection; Operation behavior uses that Operation's bound realization identity rather than its owning Module's unrelated Assurance.

## Evaluation

For every real disclosure:

```text
Sensitivity(material) <= min(PrivacyCapacity(actual serial boundaries))
```

For effectful transitions:

```text
EffectProfile.control_risk <= min(Assurance(actual residual controllers))
EffectProfile.effect_risk <= min(Assurance(actual executors), profile realization Assurance)
```

The empty controller set has neutral Assurance 5. A real disclosure resolves a nonempty boundary path. The selected profile always contributes realization Assurance. Unresolved references, incompatible roles, missing required facts, altered bindings and omitted bound profile relationships fail structurally.

Admission requires structural validity and all applicable predicates. Raising demand cannot improve admission; raising applicable capacity cannot worsen it. No numerical monotonicity assumption is made about Autonomy.

One strong component cannot compensate for a weak component that still occupies the same causal role. A source whose influence has been mediated into a new representation remains provenance and is not automatically a direct controller. If it independently still determines the effect, it remains a controller.

## Representations and transformations

Ordinary derivation cannot increase Assurance above its actual source/producer basis. Ordinary forwarding/repackaging cannot lower Sensitivity; classification reduction requires a concrete TransformContract execution. Forwarding an unchanged representation adds delivery rather than new production.

A Module publishes a TransformContract bound to implementation/version, contract identity and evidence schema. Its transform behavior executes on concrete material and determines the resulting representation, Sensitivity and Assurance according to that contract. The SDK constructs the new immutable material and derivation; broker completion binds the exact dispatched transform, source ancestry and output. The prototype accepts one source representation per invocation; a ContextBundle can represent multiple source materials with explicit ancestry.

There is no generic validator role, transform strength, classification decrement or universal function of input Sensitivity. Different outcomes from equally sensitive sources may have different classifications. A real transformation may leave both numerical projections unchanged. No production transform catalogue is required.

Kernel verifies participation, digest/representation continuity and contract identity; semantic correctness stays with the transformation owner. A claimed transform-derived representation cannot cross another Kernel boundary until that execution completes. Completed output can be reused after restart. Completion evidence for one output cannot endorse a different output or transform.

Derivation identities are canonical, unique per output and acyclic. Identical repetition is idempotent. Conflicting ancestry fails. Ephemeral invocation identifiers do not enter durable representation/derivation identity.

## Feasible profiles and composition

The bound Operation SDK client exposes non-executing `evaluate_profiles`. For an already selected semantic Operation, the broker constructs a distinct prospective transition for each real profile using its own relationships, boundaries and risk envelope.

Results expose each decision and its limiting operands, feasible IDs, maximum feasible Autonomy, and every feasible ID at that maximum. Identifier sorting is deterministic presentation, not semantic choice. Ties return to the Module. There is no synthetic intermediate profile, automatic effect retry or Kernel selection of another semantic Operation.

Actual invocation rechecks the selected contract and attachment. Inspection creates no realized disclosure/effect history. Failure evidence lets Module behavior choose another representation, transform, mechanism, profile or Operation—or stop. Kernel does not prescribe semantic remediation.

## Persistence and valuation

Security objects, transitions and derivations use V2 identities; history and decisions identify V2. Incompatible V1 storage is rejected without deletion or migration. Stored immutable contracts and accepted relationships preserve retry/restart decisions. Rejected prospective transitions remain decision evidence and cannot be admitted as realized history.

Kernel persists only public execution/security metadata, opaque references, digests and completion evidence—not private input/output payloads. Runtime history is not Module memory.

Declarations, adapter/platform facts or a separate valuation mechanism establish security facts. Locality alone supplies no PrivacyCapacity; registration and credentials supply no permission. This run defines neither valuation infrastructure nor authentication.

## Required separation cases

The acceptance suite must distinguish containment from actor Assurance; control risk from both Autonomy and effect risk; independent realization failure; concrete transform outcomes from generic relabeling; ancestry from residual control; and real feasible profiles from synthetic or arbitrarily tie-broken alternatives. Exact publication, representation and transformation substitution must fail, including across restart.

Add a new projection only for a concrete MADRE pair that existing observable facts cannot distinguish but that needs different decisions. Removing a projection likewise requires preserving the distinctions MADRE actually uses, not merely preserving results of an already narrowed evaluator.
