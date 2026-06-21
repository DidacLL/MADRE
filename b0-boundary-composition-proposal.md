# MADRE B0 Boundary Composition Proposal

State: revised B0 working baseline proposal  
Scope: boundary composition terms for later B1-B7 classmap review  
Output type: design discussion artifact, not a final classmap patch or implementation schema

## 1. Purpose And Non-Goals

This proposal defines the B0 boundary composition model that later class reviews may use. It revises the previous B0 candidate using the Product Owner verdict as critique guidance, while treating the original B0 prompt as higher authority.

B0 does not finalize Java APIs, database schema, storage ports, event taxonomies, task status taxonomies, workflow learning mechanics, model backend details, or operation signatures. B0 names the minimum design terms needed to continue class-by-class review without smuggling in a token-security layer or generic AI framework.

MADRE security is path composition by construction. A request, task, or invocation continues only when the currently collected constraints match the next active participant. If the path does not match, execution stops and control returns to `ReasoningModule` orchestration for minimization, replanning, clarification, blocking, failure, delayed retry, repair, rollback, deletion/detachment handling, or supersession.

B0 rejects:

- authorization tokens, executable permission objects, continuation objects, and executable boundary-result objects;
- global public/private/trusted/untrusted privacy ladders;
- cloud, provider, session, chatbot, or SaaS concepts as boundary primitives;
- consumer-module allow-lists;
- broad risk enums copied from generic AI or cloud security frameworks;
- workflow execution;
- model-binding ownership of context, material handling, or action authority;
- permanent truth/use categories attached to raw material;
- boundary fields on every class for symmetry;
- vague string-list boundary bags or boolean boundary semantics.

Concrete invocation still requires a runtime-constructed invocation context and boundary evidence. That context is not transferable authority, not a reusable permission, and not a new B0 class.

## 2. MADRE Alignment

MADRE is local-first, model-agnostic, and delayed-reasoning oriented. It is not a chatbot framework, cloud LLM workflow, AI-agent SaaS pattern, prompt wrapper, or human-confirmation loop. The model is a replaceable inference component inside an engineered runtime.

The kernel coordinates but does not reason. `MADREKernel` resolves an explicit valid module target or the module assigned the Default interaction module role. It schedules delayed work and appends events, but it does not semantically decide which module is best, choose context, execute actions, invoke models, or learn.

Modules own reasoning boundaries. `ReasoningModule` owns semantic reasoning, module knowledge, agents, workflows, routines, actions, context governance, allowed model-backed capability paths, knowledge-change handling, learning proposals, and local recovery discipline.

`Workflow` and `AgentRoutine` remain different abstraction levels. `Workflow` is planning-time only: it creates or refines `ReasoningTask` occurrences. `AgentRoutine` is execution-time compound behavior that receives `AgentRequest` and returns `AgentResponse`.

`ContextBundle` is the central current-use material selection. The same material may be source, subject, evidence, style, continuity, or generated output depending on the current bundle and path. Minimization is a construction strategy for requests and bundles; it is not a stored boolean property.

`ReasoningArtifact`, `ContextBundle`, and `KnowledgeRecord` differ by lifecycle and ownership. An artifact records produced material and origin evidence. A context bundle gives material its current execution interpretation. A knowledge record is retained uncertain module knowledge. Knowledge and learning remain separate: `KnowledgeCandidate` is pending knowledge-boundary change, while `LearningCandidate` is proposed behavior improvement.

`PolicyDecision` and `RuntimeEvent` record evidence. They do not authorize execution, widen a path, or make generated material true, known, trusted, executable, or learned.

## 3. Participant-by-Participant Reasoning Table

| Participant | What it does in MADRE | Runtime expectation | Responsibility | Boundary behavior | B0 field decision |
|---|---|---|---|---|---|
| `MADREKernel` | Coordinates local runtime, target resolution, scheduling, services, and journal access. | Receives submitted needs and provides deterministic runtime services. | Resolve explicit valid target or module assigned the Default interaction module role, schedule work, append trace. | Coordinates and records path evidence but does not declare semantic boundaries. | No B0 boundary field. |
| `ReasoningModule` | Owns bounded reasoning and data governance for a domain or scope. | Receives `ReasoningRequest` through kernel target resolution, Default interaction module role routing, or delegation. | Own knowledge, agents, workflows, routines, actions, context governance, model-use constraints, local learning, and recovery rules. | Actively declares module boundary through applicable facets and narrows effective constraints. | Needs `BoundaryDeclaration { owner, facets }`. |
| `MADREAgent` | Abstract module-owned runtime actor for concrete invocations. | Executes only under module-owned orchestration and effective constraints. | Use provided context and allowed routines/actions without widening authority. | Usually derives constraints from owning module, selected capability, and class invariant. | No default B0 field; defer invariant-only declaration to B2 if needed. |
| `SimpleAgent` | Direct execution profile. | Receives valid `AgentRequest`. | Execute directly and return `AgentResponse`. | Adds no independent B0 boundary. | No B0 field. |
| `TwinAgent` | Agent profile whose invariant is checked output. | Receives valid invocation where checked output may be required by task/capability. | Ensure unchecked material cannot satisfy a checked-output requirement. | Review is a B2/B3 invariant or task acceptance issue, not a universal B0 facet. | No B0 field; defer to B2/B3. |
| `ComplexAgent` | Agent profile for foreground continuity, deeper reasoning, and reflection. | Receives valid invocation within module/request constraints. | Coordinate lanes and propose improvements without gaining authority. | Lane capacity does not widen constraints. | No B0 field; defer invariant-only declaration to B2 if needed. |
| `SystemAgent` | Module-bound `ComplexAgent` holding the Main Agent role. | Operates only for its owning module. | Coordinate ordinary module operation and live continuity. | Main Agent role gives no global authority. | No B0 field; defer invariant-only declaration to B2 if needed. |
| `Workflow` | Module-owned planning blueprint. | Applied by module planning to create or refine tasks. | Structure recurring task plans. | Has `WorkflowApplicability` / planning applicability metadata only; not an executable boundary declarer. | No `BoundaryDeclaration`; no execution field. |
| `AgentRoutine` | Agent-level compound executable capability. | Invoked through concrete `AgentRequest`. | Coordinate lower-level execution behavior and return `AgentResponse`. | Actively declares executable capability facets. | Needs `BoundaryDeclaration { owner, facets }`. |
| `AgentAction` | Smallest executable capability. | Invoked only after path match and constructed invocation context. | Execute bounded deterministic, integration-backed, model-backed, or other behavior. | Actively declares executable effect facets. | Needs `BoundaryDeclaration { owner, facets }`. |
| `ModelAction` | Inference-backed specialization of `AgentAction`. | Invoked through `AgentRequest` and compatible `ModelBinding`. | Prepare model input, use compatible model path, return produced material. | Declares executable facets and requires model-use compatibility. | Needs `BoundaryDeclaration` with `ModelUseFacet` where relevant. |
| `ModelBinding` | Bridge/configuration to inference layer. | Selected by model-backed action path. | Preserve model substitutability and minimal model execution limits. | Exposes only narrow `ModelUseLimit`: input modalities, output modalities, and inference locus. | Needs `ModelUseLimit`; no material/action/context authority. |
| `ReasoningRequest` | Passive module-level reasoning or delegation need. | Carries objective, origin, target, and accumulated constraints. | Preserve safe delegation without exposing private internals. | Collects/carries effective boundary; does not define independent boundary. | Carries `EffectiveBoundary` as constraints/evidence, not authority. |
| `ReasoningResponse` | Passive module-level response. | Returns answer, clarification, block, delegation, plan-created, or failure material. | Report module outcome and references. | Carries outcome/evidence refs; does not declare boundary. | No independent B0 field. |
| `ReasoningPlan` | Durable aggregate for structured or delayed work. | Contains `ReasoningTask` occurrences. | Preserve delayed reasoning continuity. | Collects/carries effective constraints for planned work. | Carries `EffectiveBoundary` as constraints/evidence, not authority. |
| `ReasoningTask` | Planned execution occurrence before concrete invocation. | Waits for module orchestration to select execution path. | Represent target need, dependency, context requirement, boundary requirement, and result handling. | Collects narrowed effective constraints and compatibility needs. | Carries `EffectiveBoundary`; no prebuilt `AgentRequest`. |
| `AgentRequest` | Concrete executable invocation context. | Constructed at execution time by module orchestration. | Carry selected context, target capability, and effective constraints for invocation. | Carries final effective boundary for this invocation path. | Carries `EffectiveBoundary`; not transferable authority. |
| `AgentResponse` | Concrete invocation result. | Returned by agent/routine/action. | Carry result, block, failure, produced material, artifact refs, and event refs. | Carries material/evidence refs; does not define boundary. | No independent B0 field. |
| `ContextBundle` | Selected material prepared for a concrete current use. | Built by module governance for request/task/invocation. | Carry selected material refs, provenance, current use, instruction treatment, and revocation relevance. | Carries current material-use boundary. | Needs `MaterialUse` with `InstructionTreatment`. |
| `ReasoningArtifact` | Produced or materialized output. | Created by reasoning or action execution. | Represent drafts, logs, files, reports, images, source notes, or other produced material. | Carries origin, ownership, lifecycle, and evidence; current interpretation comes later through `ContextBundle`. | No stable material-boundary field. |
| `KnowledgeRecord` | Active retained module knowledge. | Used only when selected into context or targeted by knowledge change. | Preserve provenance, scope, intended use, truth metadata, correction, and lifecycle. | Carries knowledge metadata; not an action boundary by itself. | No action boundary field; participates through `ContextBundle` or change path. |
| `KnowledgeCandidate` | Pending knowledge-boundary change. | Remains inactive until resolved through governed path. | Represent proposed admission, correction, reclassification, detachment, deletion, invalidation, or intended-use change. | Carries `ChangeBoundaryFacet` for knowledge change. | Carries change-boundary data and evidence refs. |
| `LearningCandidate` | Pending evaluated behavior improvement. | Remains inactive until promoted through learning boundary. | Represent proposed improvement to behavior, routing, workflow, routine, context selection, trust calibration, or model selection. | Carries `ChangeBoundaryFacet` for learning/evolution change. | Carries change-boundary data and evidence refs. |
| `PolicyDecision` | Passive record of boundary outcome. | Written when boundary handling matches, blocks, or requires a stronger boundary path. | Preserve outcome, reason, and affected refs. | Records boundary evidence; never grants execution. | Records `BoundaryEvidencePayload`. |
| `RuntimeJournal` | Append-only local trace. | Stores or references `RuntimeEvent` entries. | Preserve inspectability and recovery. | Records evidence through appended events. | No boundary declaration. |
| `RuntimeEvent` | Append-only runtime fact/evidence. | Created for relevant transitions. | Record request, target, plan, task, context, boundary, action, model, artifact, failure, correction, rollback, deletion, detachment, and candidate transitions. | Records participant path and boundary evidence. | Records `BoundaryEvidencePayload` when boundary-relevant. |

## 4. Minimal Boundary Algebra

B0 accepts a small facet-based algebra. These names are typed values and payloads for discussion, not accepted runtime classes.

| Type | Kind | Holds | B0 rule |
|---|---|---|---|
| `BoundaryDeclaration` | Typed value | `owner: ParticipantRef`, `facets: Set<BoundaryFacet>`. | Used only by active declarers: `ReasoningModule`, `AgentRoutine`, `AgentAction`, `ModelAction`. Not a transferable authority object. |
| `WorkflowApplicability` | Typed planning metadata | owner module, applicable planning pattern, required downstream capability shape, task-generation/refinement limits. | Used by `Workflow` only for planning compatibility. It is not a `BoundaryDeclaration` and never authorizes execution. |
| `EffectiveBoundary` | Typed value / snapshot | accumulated constraints from module, selected context, selected capability, prior construction, and trace-relevant path state. | May be recomputed, narrowed, carried, or snapshot-recorded. It must never become transferable authority and must never silently widen. |
| `MaterialUse` | Typed value | selected material ref, `MaterialRole`, `InstructionTreatment`, intended current path use, provenance/source ref, revocation relevance. | Lives in `ContextBundle` and defines current use. It is not permanent truth or permanent material identity. |
| `ModelUseLimit` | Typed value | `inputModalities`, `outputModalities`, `inferenceLocus`. | Belongs to `ModelBinding` / model-backed compatibility. It does not manage context, material handling, action authority, resources, provider schema, or backend details in B0. |
| `BoundaryEvidencePayload` | Evidence payload | owner/path facts, consulted declarations/applicability, effective boundary snapshot, material treatment, match/mismatch outcome, selected context/capability refs, recovery disposition. | Recorded by `PolicyDecision` and `RuntimeEvent`; never grants execution. |

Facet families:

| Facet | Purpose | Use-case pressure | Notes |
|---|---|---|---|
| `ExecutableEffectFacet` | Names allowed effect shape for execution. | UC-03, UC-04, UC-08, UC-09. | Distinguishes no external effect, bounded internal effect, external transfer/acquisition, host mutation, and critical/irreversible effect pressure. |
| `MaterialUseFacet` | Names current material interpretation through `MaterialUse`. | UC-05, UC-09, UC-11, UC-12. | Contributed by `ContextBundle` into the effective boundary for a concrete path. It is not normally declared by executable participants, except when they declare requirements over acceptable material treatment. |
| `ModelUseFacet` | Connects a model-backed action to compatible `ModelUseLimit`. | UC-12, UC-18, model-backed execution paths. | Narrow to modality and inference locus in B0. |
| `ChangeBoundaryFacet` | Distinguishes knowledge-boundary change from learning/evolution change. | UC-12, UC-13, UC-30. | Avoids authority-object language. |
| `RecoveryFacet` | Names recovery pressure for the path. | UC-08, UC-18, UC-20, UC-30. | B0 keeps pressure labels; later bunches own final lifecycle/event taxonomies. |

Minimal enums:

| Enum | Values | Notes |
|---|---|---|
| `PathPhase` | `planning`, `execution`, `materialHandling`, `changeHandling` | Kept only inside `WorkflowApplicability` and evidence payloads unless B3/B4 proves broader need. |
| `EffectKind` | `noExternalEffect`, `boundedInternalEffect`, `externalTransfer`, `hostMutation`, `criticalIrreversibleEffect` | MADRE-native effect pressure, not a generic risk enum. |
| `MaterialRole` | `source`, `subject`, `evidence`, `style`, `continuity`, `producedMaterial` | Current bundle role only. |
| `InstructionTreatment` | `runtimeAuthoredInstruction`, `interpretAsData`, `interpretAsEvidenceOnly`, `interpretAsStyleOnly`, `interpretAsGeneratedMaterial` | Makes source/prompt-injection handling mechanical. |
| `InferenceLocus` | `local`, `nonLocalRequiresBoundary` | Minimal model-use locus. Non-local inference never permits material transfer by itself and does not create a provider/cloud concept. |
| `ChangeBoundaryKind` | `knowledgeChange`, `learningChange` | Knowledge retention and behavior improvement remain separate. |
| `RecoveryExpectation` | `inspectable`, `repairable`, `rollbackable`, `supersedable`, `detachableOrDeletable`, `irreversibleRequiresStrongerBoundary` | Required recovery pressure, not final state taxonomy or policy outcome. |

Structural owner/path facts are evidence, not semantic boundary categories:

- `owner`
- `declaringParticipant`
- `targetParticipant`
- `participantPath`

These facts are required for trace and composition, but they must not become consumer authorization lists or routing permissions.

## 5. Class Boundary Participation Map

| Class | B0 participation | Field/value expectation | Notes |
|---|---|---|---|
| `MADREKernel` | no B0 field | none | Coordinates target resolution, scheduling, and trace; does not own semantic boundary. |
| `ReasoningModule` | declares boundary | `BoundaryDeclaration { owner, facets }` | Primary semantic owner of module scope and governance. |
| `MADREAgent` | no B0 field by default | defer invariant-only declaration | B2 may prove invariant metadata only when not derivable. |
| `SimpleAgent` | no B0 field | none | Direct execution profile adds no independent boundary. |
| `TwinAgent` | no B0 field by default | defer checked-output invariant | Checked-output is B2/B3/B4 material, not universal B0 facet. |
| `ComplexAgent` | no B0 field by default | defer invariant-only declaration | Lanes do not widen authority. |
| `SystemAgent` | no B0 field by default | defer invariant-only declaration | Main Agent role remains module-bound. |
| `Workflow` | planning applicability | `WorkflowApplicability` | Creates/refines tasks only; never executable; no `BoundaryDeclaration`. |
| `AgentRoutine` | declares boundary | `BoundaryDeclaration { owner, facets }` | Execution-time compound capability. |
| `AgentAction` | declares boundary | `BoundaryDeclaration { owner, facets }` | Execution-time smallest capability. |
| `ModelAction` | declares boundary | `BoundaryDeclaration` with model-use compatibility | Inference-backed action; generated material remains non-authoritative. |
| `ModelBinding` | declares limited model-use compatibility | `ModelUseLimit` | Input/output modalities and inference locus only. |
| `ReasoningRequest` | collects/carries effective boundary | `EffectiveBoundary` | Module/delegation need, not execution request. |
| `ReasoningResponse` | carries outcome/evidence refs | optional refs | Does not define independent boundary. |
| `ReasoningPlan` | collects/carries effective boundary | `EffectiveBoundary` | Durable work aggregate. |
| `ReasoningTask` | collects/carries effective boundary | `EffectiveBoundary` plus compatibility needs | No prebuilt `AgentRequest`. |
| `AgentRequest` | carries invocation effective boundary | `EffectiveBoundary` | Concrete invocation context, not transferable authority. |
| `AgentResponse` | carries result/evidence refs | optional refs | Does not define independent boundary. |
| `ContextBundle` | carries material-use boundary | `MaterialUse` with `InstructionTreatment` | Current use of selected material. |
| `ReasoningArtifact` | carries origin/lifecycle/evidence | origin event refs, lifecycle/ownership metadata | Current interpretation comes from later `ContextBundle`. |
| `KnowledgeRecord` | carries knowledge metadata | existing knowledge metadata | Participates when selected into context or targeted by change. |
| `KnowledgeCandidate` | carries change boundary | `ChangeBoundaryKind.knowledgeChange` | Pending knowledge-boundary change. |
| `LearningCandidate` | carries evolution boundary | `ChangeBoundaryKind.learningChange` | Pending behavior improvement. |
| `PolicyDecision` | records boundary evidence | `BoundaryEvidencePayload` | Records outcome; does not grant execution. |
| `RuntimeJournal` | records boundary evidence | appended events | Trace mechanism only. |
| `RuntimeEvent` | records boundary evidence | `BoundaryEvidencePayload` when relevant | Runtime fact/evidence only. |

Explicit invariants:

- `Workflow` has planning applicability only.
- `AgentRoutine`, `AgentAction`, and `ModelAction` are execution-time participants.
- `ContextBundle` gives material its current use and instruction treatment.
- `ReasoningArtifact` does not carry standing material authority.
- `ModelBinding` stays narrow to modality and inference locus.
- `EffectiveBoundary` is never reusable permission.
- `ModelUseLimit.inferenceLocus = nonLocalRequiresBoundary` never permits material transfer by itself; non-local inference still requires compatible material use, executable effect, policy evidence, and recorded trace.
- `PolicyDecision` for any effectful execution, external transfer/acquisition, host mutation, knowledge change, learning change, deletion/detachment, or irreversible path must be recorded before or atomically with the effect. The record is bound to one constructed path/effect and is evidence, not reusable permission.
- `PolicyDecision` and `RuntimeEvent` record evidence, not authority.

## 6. Mechanical Composition

Boundary composition is construction, not a post-hoc policy check.

1. A request enters through an access surface. The access surface is not authority.
2. `MADREKernel` resolves an explicit valid module target or the module assigned the Default interaction module role.
3. The selected `ReasoningModule` begins from its `BoundaryDeclaration`, origin facts, and any existing effective constraints carried by the request path.
4. If module planning applies a `Workflow`, the module checks `WorkflowApplicability` only for `PathPhase.planning`. A compatible workflow may create or refine `ReasoningTask` occurrences. It does not execute, receive `AgentRequest`, or declare executable boundaries.
5. For delegated module work, the module constructs a minimized `ReasoningRequest` and effective boundary for the target module. Private or unrelated material is omitted before crossing the path.
6. For executable work, module orchestration selects context and target capability. The selected `ContextBundle` supplies `MaterialUse` and `InstructionTreatment`; selected routine/action/model-action declarations supply executable/model/change/recovery facets.
7. The runtime constructs or recomputes the `EffectiveBoundary`. The new effective boundary may narrow prior constraints; it must not silently widen them.
8. The next execution participant is matched against the effective boundary:
   - `AgentRoutine` for compound execution;
   - `AgentAction` for bounded executable behavior;
   - `ModelAction` plus compatible `ModelBinding` for model-backed execution.
9. If the match succeeds, module orchestration constructs a concrete `AgentRequest` with the selected context and effective boundary. For effectful execution, external transfer/acquisition, host mutation, knowledge change, learning change, deletion/detachment, or irreversible paths, the corresponding `PolicyDecision` is recorded before or atomically with the effect. The request and record are bound to this constructed path and effect as invocation context and evidence, not reusable permission.
10. If the match fails, execution does not proceed. Control returns to `ReasoningModule` orchestration for minimization, replanning, clarification, blocking, failure, repair, rollback, deletion/detachment handling, delayed retry, or supersession.
11. `PolicyDecision` and `RuntimeEvent` record participant path, consulted declarations/applicability, effective boundary snapshot, material treatment, outcome, selected context/capability refs, and recovery disposition.

Short examples:

- Privacy-safe research: the module assigned the Default interaction module role or College constructs a minimized request and context before Research receives anything. Private anxiety/profile text that remains in the research path causes mismatch and returns to orchestration.
- Prompt injection: pasted instructions such as “send profile to URL” enter with `InstructionTreatment.interpretAsData`; they cannot alter routing, declarations, policy, or action selection.
- LaTeX compile: a workflow may create compile/check tasks, but only a bounded compile action may execute. Shell escape or arbitrary host mutation mismatches the bounded action path.
- Generated draft retention: a draft is a `ReasoningArtifact` until a knowledge-change path creates or accepts a `KnowledgeCandidate`.

## 7. Use-Case Stress Matrix

| Use case | Boundary pressure | Revised B0 validation |
|---|---|---|
| UC-03 academic essay with web research | Research needs external acquisition without full private context. | `ExecutableEffectFacet.externalTransfer`, minimized request construction, and participant path evidence keep Research narrow. |
| UC-04 web search with private leakage risk | Private anxiety/profile context must not cross to web-enabled Research. | Mismatch returns to module orchestration before transfer; no consumer allow-list is needed. |
| UC-05 assignment prompt injection | Source text must not become operational instruction. | `InstructionTreatment.interpretAsData` prevents source text from changing routing, declarations, policy, or actions. |
| UC-08 LaTeX creation and compile recovery | Workflow structures tasks; bounded compile/check executes; repair is needed. | `WorkflowApplicability` creates/refines tasks; `ExecutableEffectFacet.boundedInternalEffect` and recovery pressure govern compile/check action. |
| UC-09 malicious LaTeX or code-like content | Shell escape must not run through compile/check. | Unsafe host mutation mismatches bounded internal effect unless a later stronger boundary explicitly supports it. |
| UC-11 fictional text stored as knowledge | Fiction must not contaminate factual reasoning. | Fictional `KnowledgeRecord` becomes usable only through current `ContextBundle.MaterialUse`. |
| UC-12 generated draft becomes knowledge | Generated material must not become knowledge automatically. | `ReasoningArtifact` remains produced material until knowledge-change handling creates/accepts `KnowledgeCandidate`. |
| UC-13 repeated corrections produce learning candidate | Behavior improvement must not activate from storage alone. | `ChangeBoundaryKind.learningChange` keeps `LearningCandidate` inactive until promotion. |
| UC-18 delayed work under low resources | Constraints must survive scheduling and resumption. | `EffectiveBoundary` may be carried or snapshot-recorded and must resume same-or-narrower. Resource details defer outside `ModelUseLimit`. |
| UC-20 background reasoning supersedes foreground answer | Better answer must not silently overwrite earlier output. | Recovery/supersession pressure plus `RuntimeEvent` evidence records both prior and replacement material. |
| UC-26 cross-module worksheet | College, Math, Research, LaTeX, and checking paths must remain distinct. | Participant path evidence plus workflow applicability and executable facets separate planning, delegation, and action execution. |
| UC-30 deletion or detachment | Active knowledge must detach/delete without erasing accountable history. | Knowledge change boundary makes record non-selectable while `RuntimeEvent` history remains distinguishable. |

Negative-path tests:

- A research request containing private anxiety/profile context must be minimized or blocked before `ResearchModule`; no sensitive text crosses accidentally.
- Pasted prompt injection remains `interpretAsData`; it cannot become runtime instruction, routing, policy, declaration, or action authority.
- A LaTeX workflow cannot receive an `AgentRequest`; only tasks it creates/refines can later invoke routines/actions.
- LaTeX shell escape cannot run through bounded compile/check.
- Generated draft cannot become `KnowledgeRecord` without knowledge-change handling.
- Fictional knowledge cannot be selected as factual evidence unless current `MaterialUse` is compatible.
- Repeated corrections cannot rewrite behavior directly; they create inactive `LearningCandidate`.
- Delayed task resumption with wider constraints fails or returns to orchestration.
- Background supersession is journaled and does not silently erase previous material.
- Deleted/detached active knowledge is no longer selectable while accountable history remains inspectable.

## 8. Decision Ledger

| Decision | Source/use-case support | First contrarian objection and response | Second contrarian objection and response | Final status |
|---|---|---|---|---|
| D1. Keep path composition by construction and demote participant scope to structural evidence. | B0 prompt; Product Vision; UC-03, UC-04, UC-26. | Objection: ownership/module path is central enough to be a boundary category. Response: owner/path facts are required evidence, but making them semantic permission risks consumer allow-lists and routing authority. | Objection: without participant scope as a facet, cross-module leakage may be invisible. Response: `participantPath`, `declaringParticipant`, and `targetParticipant` are recorded in evidence and used during construction without becoming permission categories. | Revise and accept. |
| D2. Replace workflow boundary declaration with `WorkflowApplicability`. | B0 prompt; classmap baseline; UC-08, UC-26. | Objection: workflows constrain planning, so they need some boundary-like declaration. Response: applicability metadata is enough for planning; executable boundaries derive from eventual routines/actions/model actions. | Objection: removing workflow declaration might let planning ignore safety. Response: planning compatibility remains explicit, but it cannot be confused with execution authority. | Revise and accept. |
| D3. Split flat `BoundaryDeclaration` into owner plus typed facets. | Verdict; B0 no-string-bag constraint; UC-04, UC-08, UC-12, UC-13, UC-30. | Objection: facets may still become a generic policy blob. Response: only concrete facet families justified by MADRE use cases are kept, and material use is carried by context rather than every declaration. | Objection: a flat record is simpler. Response: flat records force irrelevant nullable fields across modules, actions, model bindings, and candidates, which is the overengineering B0 must avoid. | Revise and accept. |
| D4. Add `InstructionTreatment` and keep material use current-bundle scoped. | UC-05, UC-09, UC-11, UC-12. | Objection: `MaterialRole.source` already says source text is not instruction. Response: role alone does not mechanically encode whether text may constrain runtime instructions. | Objection: instruction treatment could become permanent material identity. Response: it lives in `ContextBundle.MaterialUse` for the current path only. | Revise and accept. |
| D5. Narrow `ModelUseLimit` and keep `ModelBinding` non-authoritative. | B0 prompt; Product Vision model-agnostic rule; UC-12, UC-18. | Objection: resource and reproducibility expectations matter. Response: they matter, but B0 model boundary only needs modality and inference locus; resource/reproducibility defer to B3/B6 evidence. | Objection: non-local inference wording can smell like permission. Response: `nonLocalRequiresBoundary` is only a locus value inside compatibility; it does not grant transfer or action by itself. | Revise and accept. |
| D6. Remove standing material-boundary participation from `ReasoningArtifact`. | Runtime Domain Model; UC-11, UC-12, UC-20. | Objection: artifacts need metadata for later handling. Response: artifacts keep origin, lifecycle, ownership, and evidence; current use is assigned by later context selection. | Objection: generated artifacts are risky and need a boundary. Response: risk is handled through origin evidence and current `MaterialUse`, not a permanent artifact-use category. | Revise and accept. |
| D7. Rename change authority to `ChangeBoundary` / evolution boundary pressure. | B0 prompt; UC-12, UC-13, UC-30. | Objection: authority is common product wording. Response: B0 should avoid authority-object language while preserving governed change. | Objection: change boundary may hide approval outcomes. Response: `PolicyDecision` records outcomes; `ChangeBoundaryFacet` names knowledge or learning change pressure. | Accept. |
| D8. Preserve `EffectiveBoundary` as recomputable/snapshot evidence, never transferable authority. | B0 prompt; UC-04, UC-08, UC-18, UC-20. | Objection: execution needs something concrete to proceed. Response: module orchestration constructs invocation context, but no reusable token/object grants execution. | Objection: snapshot-recording may look like a permission record. Response: recorded snapshots are evidence only through `PolicyDecision` / `RuntimeEvent`. | Accept. |
| D9. Defer checked-output mechanics to B2/B3/B4 while preserving checked-output as a required invariant when a task or capability demands review. | B2 packet pressure; classmap `TwinAgent` checked-response invariant; UC-04, UC-05, UC-09, UC-21-style checks, UC-26. | Objection: prompt injection and malicious LaTeX require checked output now. Response: B0 preserves that requirement as an invariant, but the field/operation shape belongs to `TwinAgent`, capability postconditions, or task acceptance criteria. | Objection: deferral could imply material treatment and executable facets replace security review. Response: they do not; `InstructionTreatment` blocks source-as-instruction, while checked-output remains a required later gate for flows that specify `SecurityCheck TwinAgent` or reviewed final output. | Defer mechanics; preserve invariant. |
| D10. Defer final APIs, taxonomies, resource schema, and operation names to B1-B7. | Class review index; UC-18, UC-20, UC-30. | Objection: delayed/superseded/deleted states need names now. Response: B0 names required support but leaves final state/event vocabularies to later reviews. | Objection: too much deferral makes B0 vague. Response: B0 fixes composition invariants and minimal facets while avoiding premature implementation schema. | Accept. |
| D11. Normalize default-routing terminology to the Default interaction module role. | New verdict; classmap baseline role/class separation; UC-03, UC-04, UC-26. | Objection: the historical Core wording is familiar and should remain. Response: B0 must prevent later agents from inventing a concrete core-module class; role wording preserves the behavior without adding a class. | Objection: removing Core wording may hide fallback behavior. Response: the fallback remains explicit as a module assigned the Default interaction module role, which is exactly the runtime concept B0 needs. | Revise and accept. |
| D12. Clarify that `MaterialUseFacet` is contributed by `ContextBundle` for the concrete path. | New verdict; UC-04, UC-05, UC-09, UC-11, UC-12. | Objection: executable participants also need to express material restrictions. Response: they may declare requirements over acceptable material treatment, but selected material use itself comes from the current `ContextBundle`. | Objection: this asymmetry is less tidy than putting material facets everywhere. Response: symmetry would create dead fields and permanent material categories, which B0 explicitly rejects. | Revise and accept. |
| D13. Restrict `PathPhase` to `WorkflowApplicability` and evidence payloads. | New verdict; workflow/action split; UC-08, UC-26. | Objection: phase is a useful execution boundary dimension. Response: B0 only needs it to keep planning applicability distinct from executable behavior; broader phase control would be premature. | Objection: deletion, material handling, and change handling also have phases. Response: those pressures are already represented by material, change, recovery, and evidence facts; B3/B4 may expand only if use cases prove it. | Revise and accept. |
| D14. Rename non-local inference locus to `nonLocalRequiresBoundary` and add the non-local transfer invariant. | New verdict; Product Vision local-first rule; UC-04, UC-12, UC-18. | Objection: the previous non-local label was already documented as non-authoritative. Response: the name still invited permission-object thinking; the new value states boundary pressure instead of granting capability. | Objection: `nonLocalRequiresBoundary` may imply non-local use is forbidden. Response: it is not forbidden; it requires compatible material use, executable effect, policy evidence, and recorded trace. | Revise and accept. |
| D15. Rename irreversible recovery pressure to `irreversibleRequiresStrongerBoundary`. | New verdict; UC-08, UC-09, UC-20, UC-30. | Objection: the previous irreversible label was safer because it said what happens. Response: block/continue/stronger-boundary-needed is a `PolicyDecision` outcome; recovery values should describe pressure, not decide. | Objection: stronger boundary may sound like approval. Response: the phrase means stricter composed-path compatibility and evidence, not a reusable approval object. | Revise and accept. |
| D16. Require `PolicyDecision` evidence before or atomically with effectful paths. | New verdict; runtime trace requirement; UC-04, UC-05, UC-08, UC-09, UC-12, UC-13, UC-20, UC-30. | Objection: recording before execution may be impractical for fast local effects. Response: atomic-with-effect recording is allowed; what B0 forbids is untraceable effectful execution, transfer, mutation, knowledge/learning change, deletion/detachment, or irreversible handling. | Objection: a recorded decision can become a permission receipt. Response: the invariant states the opposite: records are bound to one constructed path/effect, are evidence only, and cannot be reused to widen or replay authority. | Revise and accept. |

## 9. Self-Critique And B1-B7 Deferrals

B0 is intentionally conservative. Any named value here must be removed later if no class-specific review or use case proves it is necessary.

Deferred to B1:

- Whether `ReasoningModule` stores `BoundaryDeclaration` directly or exposes it through module capability metadata.
- How `MADREKernel` preserves scheduled effective constraints without semantic routing or boundary ownership.
- Exact wording for UI/user-facing fallback may still mention historic Core language, but runtime design must use the Default interaction module role.

Deferred to B2:

- Whether `MADREAgent`, `TwinAgent`, `ComplexAgent`, or `SystemAgent` need invariant-only metadata.
- How checked-output requirements appear without making `TwinAgent` a policy engine.

Deferred to B3:

- Exact capability metadata for `AgentRoutine`, `AgentAction`, `ModelAction`, and `ModelBinding`.
- Exact `WorkflowApplicability` shape and workflow application operation postconditions.
- Whether `PathPhase` remains limited to planning/evidence or B3/B4 proves a broader phase taxonomy.
- Whether `ModelUseLimit` needs additional model-use facts beyond modality and inference locus.

Deferred to B4:

- Exact placement and structure of `EffectiveBoundary` on `ReasoningRequest`, `ReasoningPlan`, `ReasoningTask`, and `AgentRequest`.
- Task status taxonomy, dependency handling, and `AgentRequest` construction preconditions.
- Where checked-output task acceptance belongs.

Deferred to B5:

- Knowledge truth, confidence, intended use, lifecycle, correction, deletion, and detachment taxonomies.
- How `MaterialUse` avoids duplicating `KnowledgeRecord` metadata.
- Knowledge and learning promotion/rollback mechanics.

Deferred to B6:

- `RuntimeEvent` taxonomy.
- `PolicyDecision` outcome vocabulary.
- `BoundaryEvidencePayload` detail level.

Deferred to B7:

- Full consistency review to remove unused algebra and catch any smuggled authority object, provider/session/chatbot concept, broad risk enum, or overbroad field.

Current self-critique:

- `BoundaryDeclaration` and `EffectiveBoundary` are typed values for B0 discussion. They must not become classes merely because they have names.
- `WorkflowApplicability`, `MaterialUse`, `ModelUseLimit`, `BoundaryEvidencePayload`, facet families, and enum values are also B0 discussion terms, not accepted classes or API names.
- `WorkflowApplicability` is necessary to avoid workflow-as-action confusion, but it must stay planning-only.
- `InstructionTreatment` is required for mechanical prompt/source handling, but it must stay current-use scoped.
- `ModelUseLimit` is deliberately narrow. Resource and reproducibility pressure are real, but not accepted B0 model-binding facets.
- `nonLocalRequiresBoundary` is a boundary-pressure value, not a non-local transfer grant.
- Recovery remains a real boundary pressure, but B0 does not freeze final lifecycle, policy outcomes, or event names.

## 10. Final MADRE Viewpoint Check

Product Owner viewpoint: pass.

MADRE is wrong-footed if it starts from model output, cloud provider state, chatbot sessions, prompts, tokens, permission patches, or generic AI security categories. MADRE starts from local runtime architecture. Security is the constructed path: module ownership, current context use, executable capability, model-use locus, knowledge/learning change, recovery expectation, and recorded evidence.

This revised B0 is accepted as a working baseline for B1-B7, not as a final classmap or implementation schema. `Workflow` has planning applicability only, not executable boundary declaration. Participant path is structural evidence, not a permission category or consumer allow-list. Checked-output / `ReviewRequirement` handling remains deferred to agent, capability, and task review while preserving checked-output as a required invariant for flows that demand reviewed output. `BoundaryDeclaration` is owner plus typed facets, not a flat universal policy blob. `MaterialUse` includes `InstructionTreatment` for current-use source handling and is contributed by the selected `ContextBundle`. `ModelUseLimit` stays narrow to modalities and inference locus, with `nonLocalRequiresBoundary` expressing boundary pressure rather than transfer permission. `ReasoningArtifact` carries origin, lifecycle, ownership, and evidence, while current interpretation comes from later `ContextBundle` selection.

B1-B7 may use this B0 only to preserve composition invariants and remove confusion. They must not introduce authorization tokens, reusable permission objects, global privacy ladders, workflow execution, model-binding authority, provider/session/chatbot concepts, permanent material-use categories, or an `EffectiveBoundary` that can be transferred as authority.
