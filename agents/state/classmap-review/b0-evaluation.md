# B0 Boundary Algebra -- Multi-Agent Comparative Evaluation

State: completed evaluation
Scope: comparative evaluation of two B0 boundary composition proposals
Output type: multi-agent deliberation artifact with scored verdict

---

## Proposals Evaluated

- **Proposal A ("Algebra"):** `b0-boundary-algebra.md` -- Flat-dimension approach with 7 explicit dimensions as standalone enums, flat typed-value records, and exhaustive per-use-case narrative validation.
- **Proposal B ("Composition"):** `b0-boundary-composition-proposal.md` -- Facet-based approach with `BoundaryDeclaration { owner, facets: Set<BoundaryFacet> }`, five facet families, `WorkflowApplicability`, `PathPhase`, and a formal decision ledger.

## Evaluation Axes

Each axis scored 1-5, justified by MADRE use-case pressure and product-orientation principles.

1. Security by mechanical composition
2. Anti-cloud/anti-SaaS fidelity
3. Framework scope discipline
4. Monotonic restriction soundness
5. Extensibility without overengineering
6. Model-agnostic / local-first integrity
7. Material governance clarity
8. Traceability and evidence discipline
9. Deferral quality
10. Internal consistency

---

## Phase 1: Independent Analysis

### Advocate A Report: Defense of Proposal A ("Algebra")

#### Axis Scores

| # | Axis | Score | Justification |
|---|------|-------|---------------|
| 1 | Security by mechanical composition | 5/5 | Explicit 10-step composition flow where each step is a concrete construction operation with named match checks (scope, sensitivity, effect), not a policy query. Step 9's three match checks are verifiable by construction. |
| 2 | Anti-cloud/anti-SaaS fidelity | 5/5 | No PathPhase enum, no WorkflowApplicability abstraction, no facet-dispatch layer. Seven flat dimensions each justified by at least two MADRE use cases. |
| 3 | Framework scope discipline | 4/5 | Names concrete enum values (e.g., open / scopeRestricted / private) which brush close to implementation, but self-critique flags these as starting points subject to B1 revision. Values make composition rules testable now. |
| 4 | Monotonic restriction soundness | 5/5 | Explicit total orderings for Sensitivity and EffectKind. Accumulation rule for RecoveryExpectation (union-only). Every narrowing rule is one sentence and mechanically checkable. |
| 5 | Extensibility without overengineering | 4/5 | Adding a new dimension means adding a field to BoundaryDeclaration and EffectiveBoundary -- localized change. No dead fields now. |
| 6 | Model-agnostic / local-first integrity | 5/5 | ModelUseConstraint is three fields: input modalities, output modalities, inference locus. Model cannot own context, material, action authority, or resources. |
| 7 | Material governance clarity | 5/5 | MaterialUse on ContextBundle separates permanent material identity from current-use interpretation in a single auditable typed value. |
| 8 | Traceability and evidence discipline | 4/5 | BoundaryEvidence captures consulted declarations, effective boundary snapshot, material treatment, match outcome, recovery disposition. matchOutcome: Text is the one soft spot. |
| 9 | Deferral quality | 4/5 | Deferrals organized by B1-B7 with specific items per batch. Self-critique names 8 provisional points. |
| 10 | Internal consistency | 5/5 | One structural pattern per participation role (declares / collects / carries / records / none) with no overlap. No concept defined twice. |

**Total: 46/50**

#### Top 3 Strengths Unique to Proposal A

1. **Flat dimensions with explicit orderings eliminate facet-dispatch ambiguity.** Seven dimensions each have a named ordering or accumulation rule. Composition is direct comparison on a known scale. Fewer interpretive layers between declaration and match-check means fewer places a smuggled widening can hide.
2. **Sensitivity as a first-class ordered dimension with per-material enforcement.** Defines open < scopeRestricted < private as a total order, enforced per MaterialUse item against module sensitivityCeiling. This is the mechanical privacy firewall that UC-04 and UC-28 demand.
3. **No WorkflowApplicability abstraction.** Planning stays planning without a parallel type hierarchy. Treats the Workflow/execution boundary as a hard architectural split, not a type-system distinction.

#### 2 Weakest Points (Attackable)

1. **Scope is declared "foundational" but carries no concrete values or typing mechanism at B0.** Entire scope taxonomy deferred to B1, making Scope a placeholder rather than functional algebra term.
2. **EffectKind assumes a total ordering that may not hold.** Self-critique admits externalTransfer and hostMutation may be independent. If so, single effectKindCeiling narrowing rule breaks.

---

### Advocate B Report: Defense of Proposal B ("Composition")

#### Axis Scores

| # | Axis | Score | Justification |
|---|------|-------|---------------|
| 1 | Security by mechanical composition | 5/5 | Security through path construction: material that shouldn't cross a boundary is omitted before the path is composed. 11-step composition enforces at every delegation, context construction, and capability match. |
| 2 | Anti-cloud/anti-SaaS fidelity | 5/5 | Rejects authorization tokens, continuation objects, sessions, provider concepts by name. "Default interaction module role" eliminates residual "Core Module" naming. |
| 3 | Framework scope discipline | 4/5 | Stays at framework terms: typed values and discussion payloads, never Java APIs. WorkflowApplicability explicitly planning metadata. Minor: 5 facet families + 7 enums + 6 typed values approaches premature vocabulary. |
| 4 | Monotonic restriction soundness | 4/5 | EffectiveBoundary may narrow but never silently widen; RecoveryExpectation accumulates via union. Weakness: without total-ordered Sensitivity dimension, narrowing relies on construction discipline rather than mechanical ceiling check. |
| 5 | Extensibility without overengineering | 5/5 | Facet set lets B1-B7 introduce new facet families without rewriting declaration shape. A's flat BoundaryDeclaration forces every declarer to carry all fields. |
| 6 | Model-agnostic / local-first integrity | 5/5 | ModelBinding exposes only ModelUseLimit -- no resource budget, no provider schema. |
| 7 | Material governance clarity | 4/5 | ContextBundle.MaterialUse with InstructionTreatment cleanly separates permanent material identity from current-use interpretation. Gap: absence of per-material sensitivity in MaterialUse means privacy enforcement is purely constructive. |
| 8 | Traceability and evidence discipline | 4/5 | BoundaryEvidencePayload records full path facts, consulted declarations, effective boundary snapshot. Evidence explicitly non-reusable. |
| 9 | Deferral quality | 4/5 | Deferrals mapped to specific B1-B7 bunches. Decision Ledger (D1-D16) records two contrarian objections per decision, making deferral rationale traceable. |
| 10 | Internal consistency | 4/5 | 16-decision ledger stress-tests each term. MaterialUseFacet contributed by ContextBundle rather than declared by executables resolves asymmetry. Tension: RecoveryFacet "pressure" vs PolicyDecision "outcomes" boundary. |

**Total: 44/50**

#### Top 3 Strengths Unique to Proposal B

1. **Facet-based BoundaryDeclaration avoids dead fields.** A's flat record forces every declarer to carry all four fields even when irrelevant (e.g., ModelBinding does not need scope or recoveryExpectation). Facet set reflects that different participants contribute different boundary dimensions.
2. **WorkflowApplicability as distinct non-executable concept.** Locks the invariant that workflows are planning-time only before B1-B7 can accidentally import executable boundary semantics. A defers this entirely to B3.
3. **Participant scope demoted to structural evidence.** A elevates Scope as a "foundational" boundary dimension, risking evolution into a consumer allow-list. B demotes owner/participant references to structural evidence required for trace but never permission.

#### 2 Weakest Points (Attackable)

1. **No explicit Sensitivity dimension.** A defines a three-value Sensitivity enum with per-material sensitivity field and sensitivityCeiling. B relies entirely on construction discipline. Removes a redundant mechanical safety net for UC-04 and UC-28.
2. **Greater facet indirection increases cognitive and implementation risk.** 5 facet families, 7 enums, 6 typed values, 16-entry Decision Ledger vs A's 7 flat dimensions, 7 enums, 5 typed values. Facet indirection is harder to reason about statically.

---

### Contrarian Report: Attacking Both Proposals

#### Proposal A ("Algebra") -- Attack

**Smuggled Assumptions:**
- Sensitivity is a global privacy ladder in disguise. open < scopeRestricted < private is a three-tier clearance scheme. The narrowing rule (material.sensitivity <= module.sensitivityCeiling) is mechanically identical to security-clearance comparison in IAM systems.
- EffectKind total ordering presumes linear escalation identical to cloud IAM permission escalation ladder.
- participantPath is a delegation chain / capability chain of custody.

**Unjustified Dimensions:**
- ChangeKind has exactly two values and merely discriminates between KnowledgeCandidate and LearningCandidate -- types that already self-identify. Type tag, not a dimension.
- InferenceLocus is a boolean (local vs nonLocalRequiresBoundary) wearing an enum costume.
- RecoveryExpectation accumulates without bound or pruning.

**Inadvertent Permission Objects:**
- BoundaryDeclaration functions as a static capability descriptor -- one structural step from a permission object.
- EffectiveBoundary accumulated through the path is functionally a capability token narrowed at each hop.

#### Axis Scores (Attacker Perspective)

| # | Axis | Score | Attack justification |
|---|------|-------|---------------------|
| 1 | Security by mechanical composition | 3/5 | Step 9's match checks are assertion-based policy checks against accumulated state. |
| 2 | Anti-cloud/anti-SaaS fidelity | 3/5 | Sensitivity ordering = clearance tiers; EffectKind ordering = permission escalation. |
| 3 | Framework scope discipline | 2/5 | 14 fully-narrated use-case walkthroughs collapse B0 into implementation specification. |
| 4 | Monotonic restriction soundness | 4/5 | Narrowing rules explicit. Weakened by EffectKind ordering problem. |
| 5 | Extensibility without overengineering | 3/5 | Seven rigid enums require algebra-wide revision to extend. |
| 6 | Model-agnostic / local-first integrity | 4/5 | ModelBinding narrow. InferenceLocus is boolean ceremony. |
| 7 | Material governance clarity | 4/5 | MaterialUse per-bundle is clean. materialRef + sourceRef slightly redundant. |
| 8 | Traceability and evidence discipline | 3/5 | participantPath accumulation creates replayable provenance chain. |
| 9 | Deferral quality | 3/5 | Self-critique acknowledges EffectKind flaw without resolving. Scope foundational but unspecified. |
| 10 | Internal consistency | 3/5 | ChangeKind is both dimension and type discriminator. Scope foundational but valueless. |

**Total A: 32/50**

#### Proposal B ("Composition") -- Attack

**Smuggled Assumptions:**
- PathPhase introduces session-state thinking (planning / execution / materialHandling / changeHandling is a lifecycle state machine).
- WorkflowApplicability's "task-generation/refinement limits" are SaaS quotas.
- Owner field in BoundaryDeclaration reifies structural ownership as a semantic token.

**Unjustified Dimensions:**
- MaterialUseFacet is a facet most declarers do not use. Misplaced in the facet taxonomy.
- PathPhase (4 values, restricted to WorkflowApplicability and evidence) carries negligible compositional weight.
- WorkflowApplicability's "required downstream capability shape" is prospective permission / pre-authorization.

**Inadvertent Permission Objects:**
- BoundaryDeclaration { owner, facets } = access-control entry (principal + permissions).
- WorkflowApplicability's "required downstream capability shape" pre-authorizes what capabilities a workflow expects to invoke.
- Decision ledger could become standing precedent/authority for future agents.

#### Axis Scores (Attacker Perspective)

| # | Axis | Score | Attack justification |
|---|------|-------|---------------------|
| 1 | Security by mechanical composition | 3/5 | Same assertion-based match pattern. PathPhase adds phase-gate logic. |
| 2 | Anti-cloud/anti-SaaS fidelity | 3/5 | PathPhase = session state; task-generation limits = quotas; owner = principal-in-ACL. |
| 3 | Framework scope discipline | 3/5 | Less narrative sprawl, but 16-decision ledger is governance process in framework artifact. |
| 4 | Monotonic restriction soundness | 2/5 | Facet-set composition semantics undefined. No merge/intersection rule given. |
| 5 | Extensibility without overengineering | 4/5 | Facet families extensible. PathPhase and WorkflowApplicability are dead weight. |
| 6 | Model-agnostic / local-first integrity | 4/5 | Equally narrow to A. |
| 7 | Material governance clarity | 3/5 | MaterialUseFacet dual nature (context contribution vs capability requirement) is ambiguous. |
| 8 | Traceability and evidence discipline | 3/5 | BoundaryEvidencePayload detail risks becoming authority. |
| 9 | Deferral quality | 3/5 | D9 defers checked-output without sufficient boundary. |
| 10 | Internal consistency | 3/5 | MaterialUseFacet placement ambiguous. PathPhase quarantined. |

**Total B: 31/50**

#### Shared Flaws (Both Proposals)

1. Both implement clearance-based access control while denying it. Whether via flat Sensitivity enum or implicit material sensitivity through facets, the pattern is: classify material, compare against ceiling, allow/deny. This IS a security-clearance model rebranded.
2. EffectiveBoundary-as-accumulated-token in both. Passing narrowing constraints through request/plan/task/invocation is structurally a capability token refined at each hop.
3. Scope is foundational yet unspecified in both.
4. Neither addresses legitimate widening -- both offer only "return to orchestration" as escape hatch.

---

## Phase 2: Cross-Examination

### Synthesizer Report

#### Unified Comparison Table

| # | Axis | A avg | B avg | Reasoning |
|---|------|-------|-------|-----------|
| 1 | Security by mechanical composition | 4.0 | 4.0 | Both construction-gated. A more directly auditable; B routes through facet indirection. |
| 2 | Anti-cloud/anti-SaaS fidelity | 4.0 | 4.0 | A has "Core Module" residue; B has PathPhase lifecycle risk. Wash. |
| 3 | Framework scope discipline | 3.0 | 3.5 | A over-narrated with 14 UC walkthroughs. B slightly more disciplined. |
| 4 | Monotonic restriction soundness | 4.5 | 3.0 | A's clearest advantage: explicit orderings. B's facet composition undefined. |
| 5 | Extensibility without overengineering | 3.5 | 4.5 | B more extensible. A more disciplined. Trade-off. |
| 6 | Model-agnostic / local-first integrity | 4.5 | 4.5 | Equal. |
| 7 | Material governance clarity | 4.5 | 3.5 | A has per-material sensitivity field enabling mechanical ceiling check. |
| 8 | Traceability and evidence discipline | 3.5 | 3.5 | Functionally equivalent. |
| 9 | Deferral quality | 3.5 | 3.5 | Both organized by B1-B7 batch. |
| 10 | Internal consistency | 4.0 | 3.5 | A one pattern per role. B has MaterialUseFacet placement ambiguity. |

**Averaged Totals: A = 39.0/50, B = 37.5/50**

#### Genuine Disagreements vs Naming Differences

**Structural divergences (real design consequences):**

1. **Flat record vs facet-set BoundaryDeclaration.** Determines whether narrowing is a mechanical ordinal comparison (A) or a construction-discipline obligation (B). Auditability vs extensibility trade-off.
2. **Explicit Sensitivity dimension vs constructive-only enforcement.** A provides a redundant mechanical safety net that catches construction errors. B relies on material omission by construction without naming the ordinal.
3. **EffectKind total ordering vs independent facets.** A is simpler now but may need subdivision at B3; B defers the question but leaves composition undefined.

**Naming/organizational only:**

1. "Core Module" (A) vs "Default interaction module role" (B)
2. BoundaryEvidence vs BoundaryEvidencePayload
3. 10-step vs 11-step composition -- identical logical flow
4. Decision Ledger vs Self-Critique section -- meta-documentation style

#### Contrarian Challenges Assessment

**Valid structural concerns (must address):**

1. EffectKind total ordering may not hold. Combined proposal must either prove the ordering or introduce a two-axis effect model at B3.
2. Scope is foundational but unspecified. Must provide at minimum a typing mechanism and one example taxonomy.
3. EffectiveBoundary accumulates without pruning. Must state maximum cardinality or demonstrate why unbounded growth is safe.
4. participantPath as replayable provenance. Must explicitly constrain to read-only structural evidence.

**Overreaches (dismissed):**

1. "Sensitivity = clearance tiers." Per-material module-assigned, not global trust ladder.
2. "ChangeKind = type discriminator." Separates two governance paths with distinct boundary implications (UC-28).
3. "InferenceLocus = boolean." Two values is minimal, not ceremonial. Enum allows future subdivision.
4. "14 UC walkthroughs = implementation." Validation against use cases is B0's stated purpose.
5. "Decision ledger = standing precedent." Governance process concern, not algebra defect.

#### Synthesizer Preliminary Verdict

**Combine** -- A as structural base with B's naming discipline and evidence non-reusability invariant, plus new synthesis needed on EffectKind ordering proof and participantPath constraint.

---

### Contrarian Round 2: Attacking the Synthesis

#### Bias Assessment

Equal axis weighting masks a structural asymmetry. Axes 4 (monotonic restriction), 7 (material governance), and 10 (internal consistency) are security-critical for MADRE's construction-by-path thesis. A leads all three (combined 13.0 vs 10.0). B's advantages cluster on process/organizational axes (3, 5) that matter less at B0.

Axis 5 scores B at 4.5 for extensibility while acknowledging its "governance risk." Rewarding openness that introduces governance risk on the same system whose core invariant is mechanical restriction is internally contradictory.

#### "Combine" = Disguised Accept?

Yes. The verdict's structural payload is entirely A's: flat BoundaryDeclaration, explicit Sensitivity ordering, seven dimensions, 10-step composition. From B comes only naming ("Default interaction module role"), an invariant A already enforces (evidence non-reusability), and WorkflowApplicability deferred to B3 (which A also defers). This is Accept A with terminology polish.

#### Unresolved Contradictions

No hard contradiction in the combination because it takes almost nothing structural from B. The real tension is internal to A: EffectKind total ordering is declared as A's "clearest structural advantage" (Axis 4, scored 4.5) but simultaneously classified as an unresolved concern. Cannot score 4.5 on soundness while admitting the ordering may be unsound.

#### Dismissed Concerns Revisited

**Sensitivity clearance dismissal too quick.** The structural pattern IS a clearance ladder regardless of assignment mechanism. A's Step 9 check is a ceiling comparison against an ordered enum. The algebra is a clearance ladder that differs from traditional clearance only in who assigns the label. Should acknowledge resemblance honestly and justify why acceptable.

**EffectiveBoundary accumulation.** participantPath grows monotonically and recoveryExpectations only accumulate. Neither bounds cardinality. On a commercial device with long reasoning chains, unbounded growth is a resource concern.

#### Missing Considerations

Per-invocation MaterialUse boundary checking is O(n) in selected material items. For a local-first commercial-device runtime, B0 should acknowledge this cost and state that B4 must address cardinality or batching.

#### Contrarian Round 2 Position

**CONCUR with four amendments:**

1. Relabel as **Accept A** with naming and invariant-wording improvements from B.
2. Downgrade Axis 4 to 3.5 or require EffectKind ordering proof before B0 acceptance, not deferred to B3.
3. Add explicit B4 deferral for participantPath and recoveryExpectations cardinality bounds.
4. Acknowledge Sensitivity clearance-ladder structural resemblance with honest justification.

---

## Phase 3: Jury Final Verdict

### 1. Final Scored Comparison

| # | Axis | A Score | B Score | Jury Reasoning |
|---|------|---------|---------|----------------|
| 1 | Security by mechanical composition | 5 | 4 | A's flat dimensions with explicit orderings make match-check trivially verifiable: material.sensitivity <= target.sensitivityCeiling and capability.effectKind <= effectKindCeiling. B's facet-set composition rules are unspecified. |
| 2 | Anti-cloud/anti-SaaS fidelity | 4 | 4 | A retains "Core Module" naming residue. B's "Default interaction module role" is cleaner. Both equally reject cloud/SaaS primitives in substance. Wash. |
| 3 | Framework scope discipline | 3 | 4 | A's 14 full UC walkthroughs risk conflating B0 design discussion with integration test specification. B's stress matrix table is more proportionate. |
| 4 | Monotonic restriction soundness | 5 | 3 | A's decisive advantage. Explicit total orderings on Sensitivity and EffectKind make "narrows only" a mechanical invariant. B uses identical values but never states ordering or defines how Set of BoundaryFacet composes monotonically. |
| 5 | Extensibility without overengineering | 3 | 4 | B's facet pattern allows adding future facets without touching existing structures. A's flat record forces all declarers to carry all fields, though at B0 this is minimal. |
| 6 | Model-agnostic / local-first integrity | 5 | 5 | Equal. Both treat model as replaceable component, both gate non-local inference, neither introduces provider/cloud concepts. |
| 7 | Material governance clarity | 5 | 3 | A's Sensitivity as first-class dimension with explicit orderings enables trivially auditable ceiling check. B has no standalone Sensitivity dimension -- privacy enforcement is buried inside facet contributions without a named ordering. |
| 8 | Traceability and evidence discipline | 4 | 4 | Functionally equivalent. Both record through PolicyDecision and RuntimeEvent. B's explicit atomicity invariant well-stated; A states the same rule less prominently. |
| 9 | Deferral quality | 4 | 4 | Both organize B1-B7 deferrals appropriately. A's self-critique is more honest about known weaknesses. B's Decision Ledger documents contrarian objections inline but mixes process record with design artifact. |
| 10 | Internal consistency | 4 | 3 | A uses one pattern consistently: flat record per role, one enum per dimension, one composition rule per field. B has MaterialUseFacet placement ambiguity. |

**Final Totals: A = 42/50, B = 38/50**

### 2. Key Structural Differences

1. **Flat record vs facet-set BoundaryDeclaration.** A uses `BoundaryDeclaration { scope, sensitivityCeiling, effectKind, recoveryExpectation }` -- every field present, every ordering explicit. B uses `BoundaryDeclaration { owner, facets: Set<BoundaryFacet> }` -- extensible but composition semantics undefined. *Consequence:* A allows B0 to close with a provably monotonic composition algebra; B defers the composition proof to later bunches.

2. **Explicit Sensitivity dimension vs constructive-only enforcement.** A names Sensitivity as a standalone ordered dimension with per-MaterialUse field and per-module ceiling. B enforces privacy only through MaterialUseFacet contributions without a named ordering. *Consequence:* A makes privacy ceiling checks a first-class auditable invariant; B's privacy enforcement requires reconstructing the constraint from facet interactions.

3. **EffectKind total ordering vs unordered facet.** A states the 5-value ordering explicitly and defines "narrows" as moving down the ordering. B uses identical values but never defines their mutual relationship. *Consequence:* A's monotonic restriction is mechanical and provable; B's is aspirational until an ordering is established.

4. **No Workflow metadata vs WorkflowApplicability.** A gives Workflow no B0 field (deferred to B3). B introduces WorkflowApplicability with PathPhase enum for planning-only metadata. *Consequence:* B locks the planning-only invariant at B0; A relies on prose and class-level reasoning to preserve it until B3.

5. **participantPath treatment.** Both include participantPath in EffectiveBoundary, but B explicitly demotes it to "structural evidence, not a permission category" with stronger formal language. *Consequence:* B is slightly more resistant to later agents misreading participantPath as a consumer allow-list.

### 3. Final Verdict

**Verdict: Accept Proposal A ("Algebra") with seven amendments**

Proposal A is structurally superior for MADRE's B0 needs. Its explicit orderings are the mechanical foundation that makes "security by construction" provably monotonic rather than aspirationally monotonic. At B0, the algebra must be auditable without requiring future bunch work to define composition rules. A achieves this; B does not.

B's advantages (facet extensibility, WorkflowApplicability, terminology) are real but secondary: extensibility becomes relevant at B3+ when capabilities diversify, and naming is adoptable without structural change.

**Required Amendments:**

1. **Terminology: Replace "Core Module" with "Default interaction module role."** Prevents later agents from inventing a concrete CoreModule class. B's formulation is precise and the classmap baseline supports it.

2. **Terminology: Rename BoundaryEvidence to BoundaryEvidencePayload.** B's naming better distinguishes the payload from the concept.

3. **Add explicit invariant statement for PolicyDecision non-reusability.** Adopt B's formulation: "PolicyDecision for any effectful execution, external transfer, host mutation, knowledge change, learning change, deletion/detachment, or irreversible path must be recorded before or atomically with the effect. The record is bound to one constructed path/effect and is evidence, not reusable permission."

4. **EffectKind ordering proof or explicit deferral with conditions.** The self-critique acknowledges externalTransfer and hostMutation may be independent dimensions. This must be resolved before B0 closes: either prove the total ordering holds for all 30 use cases, OR add an explicit B3 deferral that states: "If B3 finds use cases requiring independent external-transfer and host-mutation ceilings, EffectKind splits into two dimensions. Current composition rules apply to each independently."

5. **Add explicit B4 deferral for participantPath and recoveryExpectations cardinality bounds.** These collections grow unbounded. B4 must address whether pruning, summarization, or caps are needed for long-lived plans.

6. **Acknowledge the Sensitivity clearance-ladder resemblance honestly.** Add one sentence to the self-critique: "The three-value Sensitivity ordering structurally resembles a clearance ladder. The critical difference is assignment mechanism: sensitivity is per-material, module-assigned at ContextBundle construction time, not a global identity attribute. This resemblance is acceptable because the enforcement is constructive (material never enters) rather than authoritative (material enters and is policy-checked)."

7. **Adopt B's stronger participantPath language.** Replace the current "structural evidence for trace, not a permission list" with: "structural evidence required for trace and composition; must not become a consumer authorization list or routing permission."

### 4. Alignment Certification

The verdict aligns with MADRE principles:

- **Local-first philosophy:** A's InferenceLocus dimension mechanically gates non-local inference. No cloud/provider concept enters.
- **Security through mechanical composition:** A's explicit orderings make composition provably monotonic -- the core MADRE security claim. This is the primary reason for selecting A over B.
- **Anti-cloud/anti-SaaS stance:** Neither proposal introduces session, token, provider, or SaaS primitives. The terminology amendment removes "Core Module" residue.
- **Model-agnostic principle:** ModelUseConstraint stays narrow (modalities + locus). Model is a replaceable inference component in both proposals.
- **Framework-scope discipline:** B0 correctly stops at composition algebra without defining APIs, schemas, or taxonomies. All seven dimensions are justified by MADRE use cases, not imported from external frameworks.

**One concern noted:** The EffectKind total ordering is the single structural claim in A that has not been formally proven. If it fails under B3 scrutiny, the algebra requires a minor structural revision (dimension split), not a fundamental redesign. This is acceptable risk for B0 closure.

---

*Deliberation closed. Proposal A accepted as B0 baseline with seven amendments.*
