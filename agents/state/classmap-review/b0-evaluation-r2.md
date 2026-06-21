# B0 Boundary Algebra -- Expanded Multi-Agent Evaluation (Run 2)

State: completed evaluation
Scope: expanded comparative evaluation with Philosophy Stakeholder and cross-review analysis
Output type: multi-agent deliberation artifact with constitutional assessment and scored verdict

---

## Context

Two prior reviews reached opposite verdicts:

- **Run 1** (`b0-evaluation.md`): Accept A (Algebra) at 42/50 vs B at 38/50, with 7 amendments.
- **Other agent** (`b0-boundary-proposals-comparative.md`): Accept B (Composition) at 8.8/10 vs A at 7.2/10, generate combined with B as base.

Run 2 adds a Philosophy Stakeholder working from the canonical TeX dossier, incorporates both prior reviews as input, and allows agents to propose redesigns.

## Proposals Evaluated

- **Proposal A ("Algebra"):** `b0-boundary-algebra.md` -- Flat-dimension approach with 7 explicit dimensions, flat typed-value records, explicit orderings, and exhaustive use-case validation.
- **Proposal B ("Composition"):** `b0-boundary-composition-proposal.md` -- Facet-based approach with `BoundaryDeclaration { owner, facets }`, five facet families, WorkflowApplicability, PathPhase, and decision ledger.

---

## Phase 1: Independent Analysis (4 parallel agents)

### Advocate A (Run 2) -- 45/50

**Response to other agent's critiques:**
1. Sensitivity is per-material/module-assigned, not a global ladder -- structural resemblance does not equal semantic identity.
2. Flat schema freezes composition rules (testable), not implementation -- Section 3 disclaims class status.
3. Scope is a rejection mechanism checked after routing, not routing itself (UC-25).
4. EffectKind ordering holds for all 30 UCs; proposed two-axis redesign for orthogonality.
5. UC walkthroughs validate rather than define the algebra (Section 6 is titled "Validation").

**Proposed redesigns:**
- R1: EffectKind split into TransferEffect + MutationEffect (two independent partial orders).
- R2: Scope demoted from foundational dimension to compatibility assertion (checked at reception, not part of narrowing chain).

**Scores:** Security 5, Anti-cloud 4, Framework 4, Monotonic 5, Extensibility 4, Model-agnostic 5, Material 5, Traceability 4, Deferral 4, Consistency 5.

---

### Advocate B (Run 2) -- 46/50

**Response to Run 1's critiques:**
1. Per-family composition operators resolve undefined semantics (narrowing, union, per-item match, compatibility, discrimination).
2. MaterialExposureConstraint achieves mechanical ceiling check without global sensitivity ladder.
3. MaterialUseFacet asymmetry is intentional: context contributes, capability declares requirements.
4. RecoveryFacet is prospective path-entry precondition vs PolicyDecision's retrospective outcomes.
5. PathPhase reduced to {planning, execution}; not session state.

**Proposed redesigns:**
- R1: Per-family composition operator table.
- R2: MaterialExposureConstraint -- typed facet, module-owned, current-path scoped.
- R3: PathPhase reduction to two values.

**Scores:** Security 5, Anti-cloud 5, Framework 5, Monotonic 4, Extensibility 5, Model-agnostic 5, Material 4, Traceability 4, Deferral 5, Consistency 4.

---

### Contrarian (Run 2) -- A: 30/50, B: 28/50

**Attack on Run 1:** Self-contradictory -- scores A 5/5 on monotonic soundness while admitting EffectKind ordering unproven. Seven amendments undermine the structure being accepted.

**Attack on other agent:** Incoherent hybrid -- imports mechanical principles while forbidding the structures that make them mechanical. "MaterialExposureFacet placeholder" is not enforcement.

**Points of AGREEMENT between prior reviews (strongest signals):**
1. Workflow is planning-only.
2. EffectiveBoundary must never become transferable authority.
3. ModelBinding stays narrow (modalities + locus).
4. ContextBundle is site of current material interpretation.
5. EffectKind total ordering is suspect.

**Points of DISAGREEMENT (real decision points):**
1. Flat vs facet-set BoundaryDeclaration -- scope-of-B0 decision.
2. Explicit Sensitivity vs constructive-only enforcement -- structural vs rhetorical difference.
3. Schema commitment vs deferral -- what is B0 FOR?

---

### MADRE Philosophy Stakeholder -- A: 14/14 PASS, B: 12/14 (2 PARTIAL)

**Constitutional Assessment: Proposal A**

| # | Criterion | Verdict |
|---|-----------|---------|
| P1 | Kernel coordinates; modules govern | PASS |
| P2 | Boundary composition builds valid paths | PASS |
| P3 | Primary declarers are module + capabilities | PASS |
| P4 | Monotonic restriction | PASS |
| P5 | PolicyDecision precedes effects | PASS |
| P6 | Generated material never self-authorizes | PASS |
| P7 | Planning-time vs execution-time distinct | PASS |
| P8 | Context/source/instruction separation | PASS |
| P9 | Material lifecycle separation | PASS |
| P10 | Recovery tied to effect class | PASS |
| P11 | External paths require stronger boundary | PASS |
| P12 | 26-class conservative discipline | PASS |
| P13 | Model path is ModelAction + ModelBinding | PASS |
| P14 | Inspectable, journaled evidence | PASS |

**Constitutional Assessment: Proposal B**

| # | Criterion | Verdict | Issue |
|---|-----------|---------|-------|
| P1-P3, P5-P11, P13-P14 | (12 criteria) | PASS | -- |
| P4 | Monotonic restriction | PARTIAL | No ordering or merge rule for facet-sets. Monotonicity aspirational, not mechanically verifiable. |
| P12 | 26-class discipline | PARTIAL | `BoundaryDeclaration { owner, facets: Set<BoundaryFacet> }` resembles "generic boundary-contract container" dossier explicitly removed. |

**Dossier adjudications of disputed points:**
1. **Sensitivity:** A's mechanism is dossier-compliant (per-material, module-assigned sensitivity matches dossier attributes). A's specific three values are premature but not unconstitutional. B's omission FAILS to implement dossier's explicit sensitivity attributes.
2. **EffectKind ordering:** SUPPORTED by dossier's autonomy-level escalation (Observation < Draft < Bounded mutation < External < Critical).
3. **Facet-set vs flat record:** Dossier's anti-generic-container principle FAVORS A's flat record.
4. **Scope:** Valid dossier concept but should not be a full composition operator at B0.
5. **WorkflowApplicability:** B correctly implements a dossier principle. Not unnecessary ceremony.

**Points where NEITHER proposal satisfies dossier:**
1. Neither provides fully compliant sensitivity mechanism (A freezes too much, B omits too much).
2. Neither addresses dossier's `intendedUse` attribute on ContextBundle and KnowledgeRecord.

**Summary:** A is constitutionally closer on mechanical soundness. B contributes WorkflowApplicability and better terminology.

---

## Phase 2: Cross-Examination

### Synthesizer (Run 2) -- A: 40.5/50, B: 41.0/50

**Verdict:** Generate Combined -- scores nearly tied indicating structural complementarity. A as structural skeleton with B's naming/WorkflowApplicability. New synthesis needed on MaterialExposure, EffectKind split, cardinality bounds.

**Proposed redesigns assessment:**
- A-R1 (EffectKind split): Sound.
- A-R2 (Scope demotion): Sound.
- B-R1 (Per-family operators): Sound but required, not optional.
- B-R2 (MaterialExposureConstraint): Partial fix -- undecidable without shared ordering.
- B-R3 (PathPhase reduction): Sound.

---

### Contrarian Round 2 (Run 2) -- FORCE REVISION

Three objections to the Synthesizer's verdict:

1. **MaterialExposure is undecidable at B0.** "Module-defined partial order" either collapses to A's global ladder (rename) or produces incommensurable cross-module values no participant can enforce. The kernel does not inspect boundary content. There is no third option.

2. **Constitutional compliance must gate, not average.** A passes 14/14 dossier criteria; B passes 12/14. Absorbing PARTIAL failures into axis averages where they vanish into a 0.5-point gap is methodologically wrong for a security-by-construction runtime.

3. **"Generate Combined" is mislabeled.** Structural payload is entirely A's (named fields, explicit operators, ceiling checks, 10-step composition). B contributes terminology and WorkflowApplicability. Honest label: "Accept A with five B-sourced amendments."

---

## Phase 3: Jury Final Verdict

### 1. Final Scored Comparison

| # | Axis | A Score | B Score | Jury Reasoning |
|---|------|---------|---------|----------------|
| 1 | Security by mechanical composition | 5 | 3 | A's 10-step composition with named match checks is mechanically verifiable from original text. B's facet-set composition was absent; per-family operators introduced during defense. |
| 2 | Anti-cloud/anti-SaaS fidelity | 4 | 4 | A retains "Core Module" residue. B's naming cleaner. Both reject cloud/SaaS primitives substantively. |
| 3 | Framework scope discipline | 3 | 4 | A's 14 UC walkthroughs approach integration-test spec. B's stress matrix proportionate. A's "foundational yet unspecified" Scope is a contradiction. |
| 4 | Monotonic restriction soundness | 4 | 3 | A's explicit orderings make "narrows only" mechanical. Reduced from 5 to 4 per Contrarian: cannot score 5 with unproven EffectKind ordering. B defines no composition rule. |
| 5 | Extensibility without overengineering | 3 | 4 | B's facet-set extensible. A's flat record rigid. Tempered by Philosophy's P12 PARTIAL (generic-container risk). |
| 6 | Model-agnostic / local-first integrity | 5 | 5 | Equal. Both narrow. |
| 7 | Material governance clarity | 5 | 3 | A implements dossier's sensitivity attributes with auditable ceiling check. B omits. Contrarian confirms B's MaterialExposureConstraint is undecidable at B0. |
| 8 | Traceability and evidence discipline | 3 | 4 | B's BoundaryEvidencePayload naming and atomicity invariant cleaner. A's Text fields violate own "no string bags" principle. |
| 9 | Deferral quality | 3 | 4 | B's deferrals mapped with traceable rationale. A's "Scope foundational yet unspecified" contradiction. |
| 10 | Internal consistency | 4 | 3 | A one pattern per role. B has MaterialUseFacet ambiguity, PathPhase scope, and process-in-design-artifact mixing. |

**Final Totals: A = 39/50, B = 37/50**

**Constitutional summary:**
- **A: 14/14 PASS.** Dossier's autonomy levels support EffectKind ordering. Anti-generic-container principle favors flat record. Sensitivity values premature but not unconstitutional.
- **B: 12/14 PASS (2 PARTIAL).** PARTIAL on P4 (monotonic restriction aspirational) and P12 (facet-set resembles removed generic container).

**Constitutional compliance is a gate, not an averaging function.** B's two PARTIAL failures on a security-by-construction runtime cannot be dissolved into a 0.5-point engineering gap.

### 2. Key Structural Differences

1. **Flat record vs facet-set BoundaryDeclaration.** A closes B0 with provably monotonic algebra. B defers composition proof and risks generic-container drift. For B0's constraining purpose, auditability wins.

2. **Explicit Sensitivity vs constructive-only enforcement.** A provides redundant mechanical safety net (ceiling check catches construction errors). B relies on correct construction as single point of failure. Contrarian confirms B's proposed fix is undecidable.

3. **EffectKind total ordering vs unordered facet.** A provides working narrowing now; may split at B3. B offers no narrowing mechanism.

4. **Workflow metadata: none vs WorkflowApplicability.** B's WorkflowApplicability is a genuine structural contribution that locks a dossier principle with typed mechanism. Adopted as Amendment 1.

5. **Scope: foundational dimension vs structural evidence.** B's demotion is more honest and prevents permission-category drift. Adopted as Amendment 2.

### 3. Final Verdict

**Verdict: Accept Proposal A ("Algebra") with eight amendments**

**Why A over B:**
1. Constitutional compliance gates. A: 14/14. B: 12/14 with PARTIAL on monotonic restriction and generic-container risk.
2. Mechanical decidability now. A's algebra is checkable from the original text. B required post-hoc redesigns.
3. Privacy enforcement has no acceptable single point of failure. A's Sensitivity ceiling is a redundant safety net. B relies on construction alone.
4. B0 must constrain, not only orient. Dossier and Philosophy confirm B0 must constrain B1-B7 mechanically.

**Addressing the Contrarian's FORCE REVISION objections:**
- Objection 1 (MaterialExposure undecidable): Agreed. A's three-value Sensitivity is the working minimum.
- Objection 2 (Constitutional compliance must gate): Agreed. B's two PARTIAL failures are decisive.
- Objection 3 ("Generate Combined" mislabeled): Partially agreed. B contributes three genuine structural elements (WorkflowApplicability, PathPhase, Scope demotion), adopted as amendments. The label is "Accept A with amendments."

**Required Amendments:**

1. **Adopt WorkflowApplicability from B.** Add as distinct non-executable planning concept. PathPhase reduced to `{ planning, execution }`. Locks planning-only invariant with typed mechanism.

2. **Demote Scope to compatibility assertion.** Verified at composition time (mismatch causes rejection) but not a narrowing dimension. Owner/participant path remain structural evidence. Scope taxonomy deferred to B1.

3. **Replace "Core Module" with "Default interaction module role."** Prevents later agents from inventing CoreModule class.

4. **Rename BoundaryEvidence to BoundaryEvidencePayload and type its fields.** Replace `matchOutcome: Text` and `recoveryDisposition: Optional<Text>` with typed values or explicit B6 deferral. A's own "no string bags" principle demands this.

5. **Add explicit non-reusability and atomicity invariant.** Adopt B's D16 formulation: "PolicyDecision for any effectful execution...must be recorded before or atomically with the effect. The record is bound to one constructed path/effect and is evidence, not reusable permission."

6. **EffectKind ordering -- provisional with explicit B3 split condition.** Accept total ordering as working B0 mechanism. If B3 finds use cases requiring independent external-transfer and host-mutation ceilings, EffectKind splits into TransferEffect + MutationEffect. Current composition rules apply to each independently.

7. **Acknowledge Sensitivity clearance-ladder resemblance honestly.** "The three-value Sensitivity ordering structurally resembles a clearance ladder. The critical differences: (a) assignment is per-material, module-owned at ContextBundle construction time, not a global identity attribute; (b) enforcement is constructive -- material exceeding the ceiling never enters the target module. This resemblance is acceptable because the mechanism prevents material leakage by construction."

8. **Add explicit B4 deferral for cardinality bounds.** participantPath and recoveryExpectations grow monotonically without bound. B4 must address pruning, summarization, or caps for long-lived plans on resource-constrained devices.

### 4. Alignment Certification

- **Local-first philosophy: PASS.** InferenceLocus gates non-local inference. No cloud/provider concept enters.
- **Security through mechanical composition: PASS.** Explicit orderings make composition provably monotonic. Three named match checks are mechanically verifiable. Sensitivity ceiling provides redundant safety net.
- **Anti-cloud/anti-SaaS stance: PASS.** No session, token, provider, or SaaS primitives. Amendment 3 removes "Core Module" residue.
- **Model-agnostic principle: PASS.** ModelUseConstraint stays narrow. Model is replaceable. Model output is non-authoritative.
- **Framework-scope discipline: PASS with concern.** A's 14 UC walkthroughs should be clearly marked as "validation, not definition."
- **One structural risk:** EffectKind total ordering is unproven. Amendment 6 provides explicit B3 fallback (dimension split). Acceptable risk for B0 closure.

---

*Run 2 deliberation closed. Proposal A accepted as B0 baseline with eight amendments incorporating B's genuine structural contributions (WorkflowApplicability, PathPhase, Scope demotion) and addressing the Contrarian's three FORCE REVISION objections.*
