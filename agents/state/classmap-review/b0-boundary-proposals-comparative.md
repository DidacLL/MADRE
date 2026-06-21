## Executive decision

Do **not** accept `b0-boundary-algebra.md` entirely.

Do **not** regenerate from zero either.

Use `b0-boundary-composition-proposal.md` as the **primary accepted base**, and generate one **combined final B0 proposal** that imports only the strongest mechanical ideas from `b0-boundary-algebra.md`: monotonic narrowing, explicit match/mismatch rules, and clearer examples of how material exposure fails before crossing a boundary.

The combined proposal should **not** import Algebra’s fixed `Sensitivity { open, scopeRestricted, private }` ladder, flat `BoundaryDeclaration` schema, use-case-driven justification style, or total ordering of effects. Those are the main risks.

The class review index confirms B0 is only the boundary model orientation feeding B1–B7, while concrete ownership, capability metadata, request/plan/invocation shape, context/material/knowledge handling, and evidence taxonomy are reviewed later.  That means B0 should define **composition invariants**, not freeze a near-schema.

# Summary scoring

Scale: 0–10. Higher is better.

| Criterion                               | Composition proposal | Algebra proposal | Winner            |
| --------------------------------------- | -------------------: | ---------------: | ----------------- |
| MADRE philosophy alignment              |                  9.2 |              7.6 | Composition       |
| Security through mechanical composition |                  8.4 |              8.7 | Algebra, narrowly |
| Minimality / anti-overengineering       |                  8.8 |              5.9 | Composition       |
| Avoidance of generic AI/cloud practices |                  9.3 |              8.0 | Composition       |
| Model-agnostic inference discipline     |                  9.0 |              7.5 | Composition       |
| Context/material correctness            |                  8.9 |              8.1 | Composition       |
| Classmap safety for B1–B7               |                  9.0 |              6.7 | Composition       |
| Framework-level scope                   |                  8.7 |              5.8 | Composition       |
| Implementation clarity                  |                  7.9 |              8.5 | Algebra           |
| Risk of freezing wrong schema           |                  8.6 |              5.4 | Composition       |

Overall: **Composition proposal: 8.8 / 10. Algebra proposal: 7.2 / 10.**

The Algebra proposal is more immediately executable as a testable mechanism. The Composition proposal is more faithful to MADRE as a framework-level architecture and safer for classmap review.

# Point-by-point comparison

## 1. Foundational posture

Composition proposal: Strong. It states that B0 is a working baseline for B1–B7, not a classmap patch or implementation schema, rejects tokens/permission objects, rejects workflow execution, rejects model-binding authority, and keeps `EffectiveBoundary` non-transferable. 

Algebra proposal: Strong opening, but more brittle. It correctly states MADRE is local-first, delayed-reasoning, model-agnostic, not a chatbot/cloud workflow/SaaS pattern, and that the model is replaceable.  Its weakness is that it immediately frames B0 around dimensions “justified by use cases.” That is useful for validation, but B0 should be framework-level. Use cases should pressure-test the algebra, not define it.

Composition score: **9.2**.
Algebra score: **8.0**.

Winner: **Composition**.

## 2. Participant reasoning

Both proposals are good on participant-by-participant reasoning.

Composition is cleaner because it keeps `ReasoningModule`, `AgentRoutine`, `AgentAction`, and `ModelAction` as active declarers, while carriers collect effective boundary and evidence classes record boundary evidence. It also treats `Workflow` as planning applicability only, and `ReasoningArtifact` as origin/lifecycle/evidence whose current interpretation comes later through `ContextBundle`. 

Algebra is thorough, but it reintroduces older terminology: `Core Module role` / fallback instead of consistently using the `Default interaction module role`. It also says `ModelBinding` “declares” a narrow boundary. That is acceptable if wording is careful, but it is riskier than Composition’s “limited model-use compatibility.” The Product Owner clarification was that `ModelBinding` is a bridge/configuration, not a material/action authority.

Composition score: **9.0**.
Algebra score: **7.6**.

Winner: **Composition**.

## 3. Workflow handling

Composition is correct: `Workflow` has `WorkflowApplicability`, creates/refines tasks, never receives `AgentRequest`, never declares executable boundaries, and execution boundaries derive from eventual routines/actions/model actions. 

Algebra is also correct at the principle level: workflow is planning-time only and planning applicability is deferred to B3. 

Difference: Composition is more useful for B1–B7 because it names `WorkflowApplicability` as a planning metadata placeholder without turning it into execution authority. Algebra defers entirely, which is safer but less operational for the next agent.

Composition score: **9.2**.
Algebra score: **8.6**.

Winner: **Composition**, slightly.

## 4. BoundaryDeclaration structure

Composition uses:

`BoundaryDeclaration { owner, facets }`

This is the better shape. It avoids forcing every declarer into the same fields and keeps the algebra extensible without becoming a generic policy blob. It explicitly treats `BoundaryDeclaration`, `EffectiveBoundary`, `WorkflowApplicability`, `MaterialUse`, `ModelUseLimit`, and `BoundaryEvidencePayload` as B0 discussion values, not accepted classes or APIs. 

Algebra uses:

```text
BoundaryDeclaration {
    scope,
    sensitivityCeiling,
    effectKind,
    recoveryExpectation
}
```

That is more concrete but too flat. It pushes B1/B3/B5 decisions into B0 and will likely create irrelevant fields on participants. It also hard-couples module scope, material exposure, action effect, and recovery into one universal record.

Composition score: **9.0**.
Algebra score: **6.2**.

Winner: **Composition**.

## 5. Scope

Algebra makes `Scope` foundational: module-declared domain identifiers, narrowing through delegation, scope mismatch causes rejection/delegation. That is mechanically attractive, but dangerous. It can mutate into semantic routing authority or consumer-module logic. Algebra says it does not enumerate scope values, but still gives scope first-class boundary status. 

Composition avoids making participant scope a semantic boundary category. It keeps `owner`, `declaringParticipant`, `targetParticipant`, and `participantPath` as structural evidence rather than permission categories. 

MADRE needs module ownership and participant path, but B0 should not become a module-routing ontology. Scope should be deferred to B1 `ReasoningModule` capability metadata, not frozen as a B0 dimension.

Composition score: **8.8**.
Algebra score: **6.7**.

Winner: **Composition**.

## 6. Sensitivity / privacy boundary

This is the most important divergence.

Algebra gives concrete values:

`open`, `scopeRestricted`, `private`

It says this is not a global trust ladder because sensitivity is per-material and module-assigned. Mechanically, it is strong: material sensitivity must be less than or equal to target module ceiling, and private material fails non-local/model/web paths. 

But the original B0 constraints explicitly reject global public/private/trusted/untrusted privacy levels. Algebra’s `open/scopeRestricted/private` is very close to a global privacy ladder even if renamed and scoped. It also risks freezing policy semantics before B5 context/material/knowledge review.

Composition avoids this by using `MaterialUse` and `InstructionTreatment` as current-path material interpretation and leaves classification detail for later. This is philosophically safer, but less mechanically explicit.

Best combined decision: **import Algebra’s monotonic exposure rule, but not its fixed sensitivity enum**.

Use this wording instead:

“Selected material may carry module-owned exposure constraints. A target participant may declare accepted material exposure requirements. B0 requires monotonic narrowing and match failure on incompatible exposure, but B0 does not freeze a global sensitivity ladder.”

Composition score: **8.4**.
Algebra score: **7.0**.

Winner: **Composition**, but import Algebra’s mechanical enforcement principle.

## 7. Effect handling

Algebra’s `EffectKind` is clear and useful:

`noExternalEffect`, `boundedInternalEffect`, `externalTransfer`, `hostMutation`, `criticalIrreversible`.

It gives strong mechanical enforcement. But it assumes a total ordering, and the proposal itself admits `externalTransfer` and `hostMutation` may be independent dimensions.  That is exactly why it should not be frozen in B0 as a full order.

Composition uses `ExecutableEffectFacet` and keeps final taxonomies deferred. That is safer. It still names the important pressures: bounded internal effect, external transfer/acquisition, host mutation, critical/irreversible effect. 

Best combined decision: keep Composition’s facet model and import Algebra’s monotonic “cannot widen effects” rule, but avoid claiming a final total order.

Composition score: **8.3**.
Algebra score: **8.1**.

Winner: **Composition**, with Algebra’s narrowing rule.

## 8. Instruction treatment

Both are strong.

Composition uses `InstructionTreatment` values:

`runtimeAuthoredInstruction`, `interpretAsData`, `interpretAsEvidenceOnly`, `interpretAsStyleOnly`, `interpretAsGeneratedMaterial`.

Algebra uses similar values:

`runtimeAuthoredInstruction`, `treatAsData`, `treatAsEvidence`, `treatAsStyle`, `treatAsGeneratedMaterial`.

Both correctly make source/prompt-injection handling mechanical and current-bundle scoped. Algebra explains the mechanism more clearly; Composition places it better inside the facet architecture.

Composition score: **9.0**.
Algebra score: **9.0**.

Winner: **Tie**. Use Composition naming because it is more explicit: `interpretAsEvidenceOnly`, `interpretAsStyleOnly`.

## 9. ModelBinding / model-use boundary

Composition is stronger. It says `ModelUseLimit` is only modalities and inference locus; non-local inference never permits material transfer by itself; non-local inference still requires compatible material use, executable effect, policy evidence, and trace. 

Algebra also keeps `ModelUseConstraint` narrow, but uses `Set<Text>` for modalities because model capabilities evolve.  That is weaker typing. B0 should not hide uncertainty behind strings. A lightweight `ModalityRef` or open typed registry reference is better than raw `Text`.

Composition score: **9.0**.
Algebra score: **7.4**.

Winner: **Composition**.

## 10. Recovery

Both are good, but Composition has better wording.

Composition uses `irreversibleRequiresStrongerBoundary`, which avoids making recovery itself decide block/allow. 

Algebra uses `irreversibleRequiresStrongerPath`. That is acceptable, but its flat `BoundaryDeclaration` makes recovery a universal field too early. Algebra also says recovery expectations accumulate by union, which is a useful mechanical rule. 

Best combined decision: use Composition’s naming and Algebra’s accumulation rule.

Composition score: **8.7**.
Algebra score: **8.2**.

Winner: **Composition**, with Algebra’s union rule.

## 11. Change boundary: knowledge vs learning

Both are aligned. Algebra is clearer that learning cannot widen privacy/exposure boundaries. Composition is cleaner in avoiding “authority” language and uses `ChangeBoundaryFacet`.

Composition score: **8.8**.
Algebra score: **8.4**.

Winner: **Composition**, but import explicit rule: “learning proposals cannot widen material exposure, effect boundary, model locus, or recovery obligations.”

## 12. EffectiveBoundary

Composition defines `EffectiveBoundary` as accumulated constraints from module, selected context, selected capability, prior construction, and path state; it may be recomputed, narrowed, carried, or snapshot-recorded and must never become transferable authority. 

Algebra gives a concrete record with `scopeCeiling`, `sensitivityCeiling`, `effectKindCeiling`, `recoveryExpectations`, `modelUseConstraint`, and `participantPath`. That is clearer for testing but too schema-like for B0. It also bakes in the disputed scope/sensitivity/effect-order decisions. 

Composition score: **8.9**.
Algebra score: **7.0**.

Winner: **Composition**.

## 13. Evidence records

Composition uses `BoundaryEvidencePayload` and explicitly states it records owner/path facts, consulted declarations/applicability, effective snapshot, material treatment, match/mismatch outcome, selected refs, and recovery disposition; it never grants execution. 

Algebra’s `BoundaryEvidence` has useful fields, but uses `matchOutcome: Text` and `recoveryDisposition: Optional<Text>`, which violates the “no vague string bags” spirit. 

Composition score: **8.7**.
Algebra score: **6.8**.

Winner: **Composition**.

## 14. Mechanical composition

Algebra is clearer and more executable. Its step-by-step path construction, material sensitivity match, non-local inference failure, and effect match rules are concrete. That is useful.

Composition is less formal but safer. It defines the correct invariant: path construction, match against next participant, success constructs `AgentRequest`, failure returns to module orchestration, and `PolicyDecision`/`RuntimeEvent` record evidence. 

Best combined decision: keep Composition’s abstractions, import Algebra’s explicit operator style:

`collect -> narrow -> match -> execute-or-return -> record`

Composition score: **8.0**.
Algebra score: **8.8**.

Winner: **Algebra**, but only for operational clarity.

## 15. Class review compatibility

The class review index puts `MADREKernel`/`ReasoningModule` in B1, agent family in B2, workflow/action/model binding in B3, request/plan/invocation in B4, context/material/knowledge/learning in B5, and policy/journal/event in B6. 

Composition respects this ordering better. It defers exact field shapes, task statuses, event taxonomies, capability metadata, knowledge truth, material lifecycle, and evidence payload detail to B1–B6.

Algebra gets too close to doing B1/B3/B5/B6 early: scope taxonomy, sensitivity ceiling, effect ordering, evidence record fields, and concrete `EffectiveBoundary` shape.

Composition score: **9.2**.
Algebra score: **6.5**.

Winner: **Composition**.

# Pros and cons of each proposal

## A. `b0-boundary-composition-proposal.md`

Numeric valuation: **8.8 / 10**.

Pros:

1. Best alignment with MADRE philosophy: local-first, model-agnostic, delayed reasoning, anti-cloud-wrapper, anti-chatbot, anti-token security.
2. Correct participant separation: declarers, collectors, material carriers, evidence recorders, no-field classes.
3. Keeps `Workflow` planning-only through `WorkflowApplicability`.
4. Keeps `ModelBinding` narrow and non-authoritative.
5. Keeps `ContextBundle` central and current-use scoped.
6. Avoids global privacy/trust ladders.
7. Avoids permanent material-use categories.
8. Avoids using `PolicyDecision` or `EffectiveBoundary` as permission.
9. Best B1–B7 compatibility.
10. Better at framework-level architecture than use-case enumeration.

Cons:

1. Less mechanically explicit than Algebra.
2. Does not define a clear monotonic exposure rule.
3. Does not define a clear effect-narrowing operator.
4. Leaves `BoundaryEvidencePayload` detail vague enough that B6 must be careful.
5. May be too abstract for agents trying to test B0 without B1–B6.
6. Needs a compact operator section: collect/narrow/match/return/record.
7. Needs explicit warning that material exposure/classification is not a global sensitivity ladder.

## B. `b0-boundary-algebra.md`

Numeric valuation: **7.2 / 10**.

Pros:

1. More mechanical and testable.
2. Strong participant-by-participant reasoning.
3. Clear monotonic narrowing rules.
4. Strong context enforcement: every selected material item gets `MaterialUse`.
5. Strong prompt-injection handling through `InstructionTreatment`.
6. Strong non-local inference warning: non-local does not grant transfer.
7. Clear recovery accumulation rule.
8. Concrete match failure behavior.
9. Useful validation examples and negative-path thinking.
10. Better as engineering intuition for future tests.

Cons:

1. Hard-codes a global-looking sensitivity ladder: `open`, `scopeRestricted`, `private`.
2. Freezes too much before B1/B3/B5/B6.
3. Uses a flat `BoundaryDeclaration` schema instead of owner + facets.
4. Elevates `Scope` to a foundational B0 dimension, risking semantic routing/consumer logic.
5. Assumes a total ordering of effects even though external transfer and host mutation may be orthogonal.
6. Uses `Set<Text>` for modalities, which weakens typing.
7. Uses text fields in `BoundaryEvidence`, which risks vague evidence bags.
8. Uses `Core Module role` wording instead of normalizing to Default interaction module role.
9. Is over-attached to use-case IDs and stress scenarios; B0 should be framework-level.
10. More likely to become an implementation schema by accident.

# Final choice

Do **not** accept either proposal entirely.

Accept **Composition** as the controlling base, but generate a **combined B0 proposal** using this rule:

Composition supplies the architecture, class participation, and deferral discipline. Algebra supplies the mechanical operator clarity and monotonicity principles. Algebra must not supply fixed sensitivity levels, flat boundary schema, use-case-driven foundation, total effect ordering, raw string modalities, or concrete evidence fields.

# Prompt to generate the combined proposal

Use this as the next-agent prompt:

```text
You are working on MADRE B0: the boundary composition model for the classmap.

Use `b0-boundary-composition-proposal.md` as the primary accepted base. Use `b0-boundary-algebra.md` only as a source of mechanical clarity. Do not copy its fixed sensitivity ladder, flat `BoundaryDeclaration` schema, total effect ordering, raw text modality fields, or use-case-driven foundation.

Goal:
Produce a final B0 working baseline that remains framework-level, minimal, typed, and aligned with MADRE’s philosophy: local-first, delayed-reasoning, model-agnostic runtime for commercial devices. MADRE is not a chatbot framework, not a cloud LLM workflow, not an AI-agent SaaS pattern, not a human-confirmation loop, and not a single-model inference wrapper. The model is only a replaceable inference component.

Design posture:
Security is mechanical composition by construction. A request, task, or invocation continues only when the collected constraints match the next active participant. If they do not match, control returns to `ReasoningModule` orchestration for minimization, replanning, clarification, blocking, failure, delayed retry, repair, rollback, deletion/detachment handling, or supersession.

Preserve from the composition proposal:
- `Workflow` has `WorkflowApplicability` only; it is planning-time and never executable.
- `AgentRoutine`, `AgentAction`, and `ModelAction` are execution-time participants.
- `ReasoningModule`, `AgentRoutine`, `AgentAction`, and `ModelAction` are active boundary declarers.
- `ReasoningRequest`, `ReasoningPlan`, `ReasoningTask`, and `AgentRequest` collect/carry effective boundary.
- `ContextBundle` carries current-use material interpretation through `MaterialUse` and `InstructionTreatment`.
- `ReasoningArtifact` carries origin/lifecycle/ownership/evidence only; current interpretation comes from later `ContextBundle`.
- `KnowledgeRecord` has knowledge metadata, not action boundary.
- `KnowledgeCandidate` and `LearningCandidate` carry change/evolution boundary pressure.
- `PolicyDecision` and `RuntimeEvent` record evidence; they never grant execution.
- `ModelBinding` exposes only narrow model-use compatibility: modalities and inference locus.
- `nonLocalRequiresBoundary` is boundary pressure, not transfer permission.
- `EffectiveBoundary` is never transferable authority.
- No global privacy/trust ladder, no consumer-module allow-list, no provider/session/chatbot/cloud concept, no permission token, no boundary-result object, no workflow execution.

Import from the algebra proposal only these mechanical principles:
- Use explicit composition operators: collect, narrow, match, return-to-orchestration, record.
- State that effective constraints may narrow but must not silently widen.
- State that material exposure constraints are checked before material crosses to a target participant.
- State that effect pressure cannot be widened by downstream participants.
- State that recovery expectations accumulate and cannot be removed by later steps.
- State that non-local inference requires compatible material exposure, executable effect, policy evidence, and trace; model-use locus alone never permits transfer.
- Include short negative examples, but do not make use-case IDs the foundation of the proposal.

Required correction to Algebra:
- Do not define `Sensitivity { open, scopeRestricted, private }`. Instead define a generic `MaterialExposureFacet` or `MaterialConstraint` placeholder that B5 will refine. It must be module-owned/current-path scoped and must not become a global privacy ladder.
- Do not make `Scope` a B0 semantic permission category. Treat owner, declaring participant, target participant, and participant path as structural evidence. Module capability scope may be deferred to B1/B3 metadata.
- Do not use a flat `BoundaryDeclaration { scope, sensitivityCeiling, effectKind, recoveryExpectation }`. Use `BoundaryDeclaration { owner, facets }`.
- Do not use raw `Text` for model modalities. Use `ModalityRef` or a typed extensible value object.
- Do not use `matchOutcome: Text` or `recoveryDisposition: Text`. Keep evidence payload typed or explicitly deferred to B6.
- Do not assume a final total ordering of effects. Express monotonic effect compatibility without freezing a final taxonomy.

Expected final structure:
1. Purpose and non-goals.
2. MADRE philosophical alignment.
3. Participant-by-participant reasoning table.
4. Minimal boundary facets.
5. Typed values/enums, marked as B0 discussion values, not classes/API.
6. Class-by-class participation map.
7. Mechanical composition algorithm: collect → narrow → match → execute-or-return → record.
8. Cross-bunch deferrals aligned with B1–B7.
9. Final acceptance statement for B0 as working baseline only.

Tone:
Clear, technical, conservative. No generic AI security taxonomy. No cloud-provider assumptions. No chatbot/session terminology. No implementation schema freeze. No use-case literature as foundation; use examples only to illustrate intention.
```

My recommendation: run this prompt once, then stop B0 iteration unless B1–B7 exposes a concrete contradiction.
