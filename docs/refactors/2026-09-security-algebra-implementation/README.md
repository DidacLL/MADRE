# Final Security Algebra Implementation Handoff

This folder is temporary implementation guidance for migrating the current Python prototype to the frozen MADRE Security Algebra.

It is not product authority.

Product/security authority is:

```text
MADRE.md
docs/architecture/MADRE-security-algebra.md
```

Current implementation truth/gaps are owned by `docs/implementation-baseline.md`.

Remove this folder when the migration is complete and the baseline records executable convergence.

## Mission

Replace the temporary compatibility security schema/evaluator with the frozen five-concept, transition-local algebra as one coherent implementation stage.

Do not redesign unrelated Kernel, SDK or CORE behavior.

Do not use this migration as an excuse to add a security policy framework, IAM system, third-party scanner, generic shell/Internet interface, provider expansion or richer CORE UX.

## Frozen target

Runtime normalized concepts:

```text
Sensitivity
Privacy
Integrity
Risk
Autonomy
```

Transition roles:

```text
DISCLOSURE
CONTROL
EFFECT_EXECUTION
DERIVATION
```

Effectful execution uses immutable Operation-owned `EffectProfile`s.

Predicates:

```text
DisclosureSafe(d):
    Sensitivity(material) <= min(Privacy(actual disclosure path))

ControlSafe(c):
    min(Risk(effect_profile), Autonomy(effect_profile))
        <= min(Integrity(actual non-user controllers))

EffectSafe(c):
    Risk(effect_profile)
        <= min(Integrity(actual effect executors))
```

Overall decision:

```text
StructuralValid
AND every DisclosureSafe
AND ControlSafe when effectful
AND EffectSafe when effectful
```

## Current implementation structures to replace

The pre-freeze prototype currently contains:

```text
MaterialSecurityValues(sensitivity, intended_use)
ActorSecurityValues(trust, isolation)
OperationSecurityValues(risk, autonomy)
CapabilitySecurityValues(trust, privacy, risk)
SecurityContext(objects tuple)
CompatibilitySecurityEvaluator(global max/min reductions)
```

These are implementation history, not compatibility requirements.

MADRE is greenfield. Generated local databases/state may be recreated instead of introducing migration machinery unless a concrete test fixture requires conversion for the test itself.

## Required implementation shape

### 1. Typed final security values

Represent only subject-applicable final values:

```text
Artifact / ContextBundle: Sensitivity, Integrity
Module / Agent / endpoint: Privacy, Integrity
Capability: Privacy, Integrity
EffectProfile: Risk, Autonomy, Integrity, optional Privacy
```

Do not retain deprecated fields merely to avoid updating callers.

### 2. Globally unambiguous SecurityID binding

Do not derive identity solely from Module-local `subject_id`.

Bind SecurityObjects unambiguously to owner/kind/revision/representation while keeping identity separate from authority.

Requirements:

```text
same immutable binding stable across restart
different immutable bindings cannot collide intentionally
changed profile/representation -> new SecurityID
same SecurityID + conflicting content -> structural failure
Module-local Agent/Operation names may collide across Modules
```

Use the smallest clear representation. Do not build PKI/IAM.

### 3. Immutable EffectProfile

Split Operation semantic identity from security-relevant execution shape.

An Operation may expose one or more immutable bound EffectProfiles.

A concrete invocation selects an existing profile. It cannot override `Risk`, `Autonomy`, `Integrity` or `Privacy` numerically.

Update SDK descriptors/helpers and broker invocation contracts coherently.

### 4. SecurityTransition

Introduce the smallest typed transition structure needed to express actual:

```text
disclosure edges
causal controllers
effect executors
derivations
```

Do not infer those roles from prompt/output text.

Where roles are structurally known from the protocol, build them automatically. Examples:

```text
material brokered to Agent -> disclosure to target Module/Agent/endpoint
material sent to Capability -> disclosure to Capability
Operation input used to determine effect parameters -> control relation according to Operation/SDK contract
selected EffectProfile + effect path -> effect execution contributors
```

When a semantic role cannot be known without understanding private content, it remains the Module/SDK caller's responsibility to construct the correct transition relationship.

Make omissions difficult rather than creating a universal semantic classifier.

### 5. SecurityHistory

Replace append-only tuple semantics with idempotent immutable history sufficient for:

```text
SecurityObjects
transition/decision evidence
derivation relationships
```

Repeated same SecurityID must not multiply contribution.

Rejected candidate mechanisms are evidence of a rejected prospective transition, not evidence that material was disclosed to them.

Persist only security metadata/evidence; preserve the no-private-payload invariant.

### 6. Structural validation

Implement at minimum:

```text
all SecurityIDs resolve
bindings verify
no conflicting SecurityID contents
subject/value kinds match
all role-required values exist
ordinary levels are 1..5 only
0 is rejected as ordinary value
EffectProfile belongs to invoked Operation
transition references actual participants
material reference/digest/security continuity still holds
new derived representation has distinct binding
```

Missing values must return deterministic structural failure. Do not silently assign conservative numeric defaults inside the evaluator.

### 7. Final evaluator

Replace `CompatibilitySecurityEvaluator` with the canonical transition-local predicates.

Do not globally reduce all historical material/participants.

Decision evidence should identify the exact edge/chain and limiting SecurityIDs/values.

Keep the evaluator deterministic and small.

### 8. Derivation rules

SDK material helpers must preserve immutable source labels.

A transformed representation receives a new material identity/SecurityID.

Sensitivity may be lower only on a genuinely new Module-classified representation.

Integrity must not increase merely by wrapping/copying low-Integrity material. A stronger-Integrity representation requires an explicit validation derivation and adequate validator/producer assurance as specified by the canonical security document.

Kernel does not inspect content to verify semantic minimization/validation.

### 9. Runtime and broker integration

Preserve the existing carried-security continuity fixed during the SDK/CORE stage.

Nested broker -> Agent -> inference/durable work must continue the same immutable history, extending it with actual accepted transitions/participants rather than reconstructing context from registry state.

Capability selection may evaluate candidate prospective transitions before material disclosure. Only the selected/accepted path becomes actual disclosure history.

### 10. CORE

Migrate CORE declarations from old trust/isolation fields to final Privacy/Integrity values through public SDK APIs.

CORE receives no bypass.

CORE-owned material may remain maximum Sensitivity while CORE/its local mechanisms provide high Privacy/Integrity.

Do not add richer CORE product behavior in this migration.

## Required regression/case tests

At minimum cover:

1. maximum-sensitivity material succeeds through level-5 private CORE/local inference path;
2. the same material fails through a lower-Privacy remote path;
3. a weak participant affects only disclosures it actually participates in;
4. rejected candidate Capability does not become disclosure history;
5. minimized derived material can cross a path its source cannot, while source history remains intact;
6. low-Integrity generated/external material driving a high-risk autonomous effect is denied;
7. the same material merely being displayed does not become a controller;
8. direct-user EffectProfile lowers control demand but does not bypass the full effect-execution integrity gate;
9. weak effect executor denies destructive effect even with direct user control;
10. low-risk autonomous effect can pass low-integrity control when the frozen inequalities permit it;
11. explicit validation derivation can create a distinct stronger-Integrity representation only through the proper helper/contract;
12. sensitive material sent to a public/low-Privacy publishing EffectProfile fails confidentiality independently of effect control;
13. duplicate SecurityObjects are idempotent;
14. SecurityID collision/conflicting binding fails structurally;
15. two Modules can publish the same local Agent/Operation name without SecurityID collision;
16. restart/retry restores identical decisions for identical operands;
17. retry with a different Capability/EffectProfile evaluates a new prospective transition;
18. missing role-required value produces structural failure, not numeric defaulting;
19. level 0 is rejected as an ordinary security value;
20. registry/credentials/identity/reference/previous success never create an alternate authority path;
21. SQLite still contains no private prompt/context/output payload;
22. SDK/CORE import-boundary tests remain green.

Add an exhaustive finite test over ordinary levels `1..5` for monotonicity of the three numeric predicates. This validates implementation of the formula; it is not a substitute for the canonical architecture proof/rationale.

## Preserve existing behavior

Unless directly changed by the frozen security model, preserve:

```text
reference-only durable material
JIT material resolution
restart/retry/cancellation semantics
originator fairness + priority/FIFO
heavyweight local-resource admission
transient inference
hard requirements/preferences/fallback mechanism selection
provider/model/mechanism separation
OpenAI-compatible adapter as one mechanism
explicit Module+local-id Agent/Operation routing
broker carried-security continuity
unknown external effect handling
transient result/loss evidence
SDK/Core import boundaries
CORE replaceability
```

## Third-party valuation is not part of this refactor

The final algebra deliberately does not define how unknown Modules/adapters obtain values.

The implementation only needs a clean boundary by which complete, bound SecurityObjects enter the system and a deterministic `missing_security_value` failure when role-required facts are absent.

Do not build static analysis, installer valuation, conservative-default policy or AI security inspection in this run.

## Completion

The migration is complete when:

- compatibility security schemas/evaluator are removed rather than left as parallel authority;
- Kernel, SDK, CORE, broker/runtime, persistence and tests all use the final model;
- `docs/implementation-baseline.md` records executable convergence;
- this temporary folder can be removed;
- full locked validation is green.
