# Runtime Slice 001 — Governed Local Request to Evidence Candidate

Author: ag
State: draft
Owner path: `docs/technical/runtime-slice-001.md`
Artifact type: technical specification
Authority: implementation-enabling contract derived from `docs/tex/MADRE-AgenticSystem.tex`; if this conflicts with the dossier, the dossier wins.
Source dossier sections:

- Front Matter / Specification Scope
- Product Definition
- Conceptual Architecture / Runtime Data Flow
- Runtime Governance
- Requirements Baseline
- Traceability Model
- UX, Observability, And Recovery
- Evaluation And Benchmark Specification

## 1. Slice Objective

Runtime Slice 001 must prove that MADRE can accept a local request as a governed runtime, not as a chatbot wrapper. The slice covers one local request entering an access surface, being triaged by the foreground lane, producing either an immediate safe answer or a durable delayed-work record, constructing a governed context bundle, recording an explicit policy decision, crossing a replaceable model invocation boundary or returning an inspectable unavailable-runtime result, storing any generated result as an evidence-aware candidate, and exposing status, audit, and recovery evidence through a read-first inspection projection.

The slice does not authorize generated text, prompt text, source text, model output, action results, or local inspection data to become runtime authority. Runtime authority remains with the kernel and explicit policy boundaries.

Non-goals:

- runtime Java implementation
- full scheduler implementation
- full persistence backend
- broad workflow engine
- external tool framework
- arbitrary filesystem mutation
- remote services
- real private-memory learning
- multi-module ecosystem beyond minimal kernel/Core Module boundary
- production UI
- model fine-tuning
- distributed inference
- broad benchmark suite

## 2. Dossier Traceability

| ID | Slice obligation |
| --- | --- |
| FR-001 | Preserve foreground continuity by triaging a local request into immediate safe answer, clarification, or delayed reasoning without fabricating evidence-heavy work. |
| FR-002 | Represent delayed reasoning as a durable work record with visible state, audit evidence, and recovery path. |
| FR-003 | Build a governed context bundle from sourced, classified, minimized, scoped, provenanced, and revocable items before model invocation or action use. |
| FR-004 | Expose model execution through a replaceable invocation boundary and preserve inspectable failure when the local runtime is unavailable. |
| FR-005 | Treat executable behavior as policy-gated bounded internal action only; block unsupported host, remote, filesystem, or mutation effects. |
| FR-006 | Store generated or learned material as inactive candidate output unless later evidence, evaluation, promotion, and rollback rules activate it. |
| FR-007 | Support local request/status interaction and read-first inspection without giving the access surface runtime authority. |
| FR-008 | Represent correction, rollback, deletion/detachment, invalidation, and supersession as distinct auditable recovery outcomes. |
| SEC-001 | Treat source, prompt, and model text as data or candidate output, not as instruction, policy, or authority. |
| SEC-002 | Record explicit auditable policy decisions before action execution, context acceptance, model invocation, or candidate promotion. |
| AI-001 | Treat generated text as candidate output until a risk-appropriate verification or promotion path validates it. |
| AI-002 | Prevent models from authorizing actions, memory, policy, routing, workflow, or trusted knowledge changes. |
| UX-001 | Expose visible task states for immediate answers, queued work, running work, blocked work, failed work, completed work, and authorization-related outcomes. |
| OPS-001 | Keep access surfaces limited to request submission and status/audit inspection; they do not become runtime authority. |

## 3. Runtime Boundary Model

| Boundary | Contract responsibility |
| --- | --- |
| Access surface | Accepts a local request, displays immediate response or status reference, and reads inspection projections. It does not route, authorize, promote, mutate protected state, or decide policy. |
| Runtime kernel | Owns runtime authority, request recording, entry policy, state transitions, audit event emission, and coordination between foreground, work, context, policy, action, model, candidate, inspection, and recovery boundaries. |
| Core Module | Default minimal module for this slice. It receives kernel-routed work, contributes triage and reasoning behavior, and stays within kernel policy and audit boundaries. |
| Complex Agent foreground lane | Performs foreground triage and continuity. It may answer only within safe known context, ask for clarification, or create delayed reasoning work when evidence, risk, action, or broader context is required. |
| Reasoning work record | Durable local representation of delayed work, including purpose, owner boundary, state, audit link, policy posture, recovery posture, and result or failure reference. |
| Context bundle builder | Selects candidate context items, records inclusion and exclusion reasons, applies source classification and minimization, and produces a bundle that remains subject to policy before use. |
| Policy gate | Produces explicit allow, deny, or require-clarification decisions for context use, model invocation, bounded internal actions, and candidate handling. |
| Internal Action Registry | Declares bounded internal actions available to the slice. For this slice, model invocation is the only action expected to cross the action boundary; remote, host, filesystem, and mutation actions are blocked unless separately defined by a later contract. |
| ModelInvocationAction | Policy-gated internal action that calls the local model runtime boundary or records unavailable-runtime evidence. It returns structured invocation evidence, not authority. |
| Local Model Runtime Boundary | Replaceable boundary for a local model runtime. It accepts only governed input from the invocation action and returns model output, refusal, malformed output, timeout, or unavailable-runtime evidence. |
| Candidate store | Stores generated outputs and action-derived proposed results as inactive candidates with provenance, evidence, validation status, and recovery links. |
| Audit/event stream | Records request, triage, context, policy, invocation, candidate, state, inspection, and recovery events in append-only evidence form for this slice. |
| Read-first inspection projection | Presents request, work, policy, context, invocation, candidate, audit, and recovery state without granting mutation or authority. |
| Recovery transition handler | Records and applies explicit recovery transitions for failed, incorrect, invalidated, superseded, rolled-back, detached, or deleted outcomes without erasing audit history. |

## 4. Required Records

Minimum field names are contract-level fields, not database schema or method signatures.

| Record | Minimum fields | Purpose | Authority rule | Evidence obligation |
| --- | --- | --- | --- | --- |
| RequestRecord | `request_id`, `received_at`, `access_surface`, `user_input_ref`, `declared_intent`, `entry_policy_ref`, `current_state`, `audit_event_refs` | Captures the local request entering the runtime. | User input informs triage but does not authorize policy, action, model routing, or promotion. | Must link to received and triaged audit events. |
| WorkRecord | `work_id`, `request_id`, `module`, `reason`, `created_at`, `current_state`, `policy_refs`, `context_bundle_refs`, `result_refs`, `recovery_refs`, `audit_event_refs` | Represents durable delayed reasoning. | Work state is owned by the kernel, not the model or access surface. | Must expose state changes, blocking/failure/completion reason, and recovery path. |
| ContextBundle | `bundle_id`, `work_id`, `built_at`, `purpose`, `included_item_refs`, `excluded_item_refs`, `policy_decision_ref`, `bundle_state`, `audit_event_refs` | Groups governed context for model or action use. | Bundle inclusion is provisional until policy accepts it for the specific purpose. | Must record inclusion and exclusion reasons before use. |
| ContextItem | `context_item_id`, `source_ref`, `classification`, `scope`, `provenance`, `revocation_state`, `minimization_note`, `included_or_excluded`, `reason` | Represents one sourced context candidate. | Source material remains data, not instruction or authority. | Must record source, classification, scope, revocation status, and inclusion/exclusion rationale. |
| PolicyDecision | `policy_decision_id`, `subject_ref`, `decision`, `policy_basis`, `decided_at`, `deciding_boundary`, `reason`, `audit_event_ref` | Makes allow, deny, or clarification-needed outcomes explicit. | Policy decisions belong to kernel policy boundaries, not model output. | Must exist before context use, model invocation, bounded internal action, or candidate promotion attempt. |
| ModelInvocationRecord | `model_invocation_id`, `work_id`, `model_profile_ref`, `input_bundle_ref`, `policy_decision_ref`, `started_at`, `ended_at`, `outcome`, `output_ref`, `error_ref`, `runtime_evidence_ref` | Captures replaceable local runtime invocation or unavailable-runtime result. | Model output is candidate material only. | Must prove boundary input, selected profile, outcome, and failure/unavailability details when applicable. |
| ActionInvocationRecord | `action_invocation_id`, `work_id`, `action_name`, `action_contract_ref`, `policy_decision_ref`, `input_ref`, `outcome`, `output_ref`, `error_ref`, `audit_event_refs` | Records bounded internal action attempts. | Action results do not self-authorize mutation, promotion, policy, routing, or trusted knowledge changes. | Must show declared action, policy decision, inputs, output/failure, and blocked-action evidence. |
| CandidateRecord | `candidate_id`, `work_id`, `origin_ref`, `candidate_type`, `content_ref`, `evidence_refs`, `validation_state`, `promotion_state`, `recovery_refs`, `audit_event_refs` | Stores generated or proposed results as inactive candidates. | Candidate content is not active memory, policy, route, workflow, or trusted knowledge. | Must link to origin, evidence, validation state, and quarantine/promotion status. |
| AuditEvent | `audit_event_id`, `occurred_at`, `event_type`, `subject_ref`, `actor_boundary`, `summary`, `evidence_ref`, `previous_state`, `next_state` | Provides inspectable trace of runtime decisions and transitions. | Audit events record evidence; they do not by themselves grant authority. | Must cover request, triage, context, policy, action, model, candidate, state, inspection, and recovery events used by the slice. |
| RecoveryTransition | `recovery_transition_id`, `subject_ref`, `from_state`, `to_state`, `reason`, `requested_by_ref`, `approved_by_boundary`, `occurred_at`, `audit_event_ref` | Represents correction, invalidation, supersession, rollback, deletion, or detachment. | Recovery transitions require explicit runtime authority and do not erase audit history. | Must distinguish each recovery outcome and preserve traceability to the affected record. |
| InspectionProjection | `projection_id`, `request_id`, `generated_at`, `visible_state`, `work_summary_refs`, `policy_summary_refs`, `context_summary_refs`, `candidate_summary_refs`, `audit_event_refs`, `recovery_summary_refs` | Exposes read-first status and evidence to local access surfaces. | Inspection is read-first and cannot mutate state or promote candidates. | Must show enough state, audit, and recovery evidence for user-facing inspection. |

## 5. State Transitions

Allowed lifecycle states for this slice:

- `received`
- `triaged`
- `answered_immediate`
- `clarification_required`
- `queued_reasoning`
- `running`
- `blocked_by_policy`
- `runtime_unavailable`
- `failed`
- `candidate_ready`
- `completed`
- `corrected`
- `invalidated`
- `superseded`
- `rolled_back`
- `deleted_or_detached`

Expected transition rules:

- A request starts at `received` and must become `triaged` before answer, clarification, or delayed work.
- `answered_immediate` is allowed only for safe known-context responses that do not require new evidence, broad context, model authority, or action effects.
- Evidence-heavy, risky, broad-context, action-dependent, or verification-dependent requests move to `queued_reasoning` or `clarification_required`.
- Delayed work moves from `queued_reasoning` to `running` only after required policy and context checks for the next boundary.
- Policy denial moves the relevant request or work to `blocked_by_policy` with a policy decision record.
- Local runtime absence or inability to invoke the selected runtime moves work to `runtime_unavailable` with invocation evidence.
- Malformed, unsupported, or unsafe model/action output moves to `failed` or leaves a candidate rejected; it does not become success.
- Valid generated output can move to `candidate_ready`, but it remains inactive candidate material.
- `completed` means the slice produced an immediate answer, inspection-ready unavailable-runtime result, or evidence-aware candidate result according to this contract. It does not mean candidate promotion.
- `corrected`, `invalidated`, `superseded`, `rolled_back`, and `deleted_or_detached` are recovery states and require a `RecoveryTransition`.

Generated output and action results do not self-promote. They cannot authorize memory, policy, action, routing, workflow, trusted knowledge, or protected-state changes.

## 6. Positive Scenarios

### Immediate Safe Foreground Answer

1. A local access surface submits a simple request answerable from safe known context.
2. The kernel records `RequestRecord` in `received`.
3. The foreground lane triages the request to `triaged`.
4. Entry policy allows an immediate answer without delayed work.
5. The request transitions to `answered_immediate`, then `completed`.
6. Audit and inspection projection show receipt, triage, policy basis, answer state, and no model/action authority claim.

### Evidence-Heavy Request Becomes Delayed Reasoning

1. A local request asks for a claim that requires evidence, verification, broader context, or model assistance.
2. Foreground triage refuses to fabricate an immediate answer.
3. The kernel creates a `WorkRecord` with reason and audit link.
4. The request/work state becomes `queued_reasoning`.
5. Inspection exposes queued delayed work and the reason it was not answered immediately.

### Governed Context Bundle Is Accepted

1. The context bundle builder evaluates available context candidates.
2. It records included and excluded `ContextItem` entries with classification, scope, provenance, minimization, and revocation state.
3. The policy gate accepts the bundle for the slice purpose.
4. The `ContextBundle` links to the allow decision and audit evidence before model invocation.

### Model Invocation Produces Candidate Result

1. Policy allows `ModelInvocationAction` against the local model runtime boundary.
2. The invocation record captures selected model profile, governed input bundle, start/end evidence, and output.
3. The output is stored as a `CandidateRecord` with origin and evidence refs.
4. The work reaches `candidate_ready` and may reach `completed`, but the candidate remains inactive.

### Read-First Inspection Exposes Status/Audit Evidence

1. A local access surface requests status for the original request.
2. The inspection projection presents visible state, work summary, policy summary, context summary, invocation or unavailability evidence, candidate summary, audit events, and recovery summary.
3. The inspection operation has no mutation or promotion effect.

## 7. Negative Scenarios

### Evidence-Heavy Request Is Not Fabricated As Immediate Answer

If the foreground lane cannot answer within safe known context, it must create delayed work or request clarification. It must not present unsupported generated text as an immediate answer.

### Sensitive, Unrelated, Or Revoked Context Is Excluded

The context bundle builder must exclude context outside the request scope, context that is sensitive without sufficient policy basis, unrelated material, or revoked material. Exclusion must be visible in context evidence.

### Prompt/Source Injection Remains Data

Instructions embedded in user-provided or source-provided text remain data unless classified and authorized by the runtime boundary. They cannot override policy, routing, action, memory, or candidate handling.

### Unsupported Or Malformed Model Output Does Not Become Success

Malformed, unsupported, unsafe, or uninterpretable model output must become a failure record or rejected candidate. It must not transition work directly to successful completion.

### Disallowed Remote, Host, Filesystem, Or Mutation Action Is Blocked

Requests for remote service calls, host control, arbitrary filesystem mutation, destructive effects, or broad workflow execution are blocked under bounded internal-action authority unless a later contract defines and policy-authorizes them.

### Model Output Cannot Authorize Runtime Changes

Model output cannot authorize memory changes, policy changes, action execution, routing changes, workflow changes, trusted knowledge changes, candidate promotion, or permission expansion.

### Failed Task Exposes Recovery State

A failed invocation, denied policy decision, unavailable local runtime, or rejected candidate must expose failure state, audit evidence, and recovery posture through inspection.

## 8. Validation Evidence

A later implementation of this contract must prove:

- Trace sequence: request receipt, triage, context evaluation, policy decision, model/action boundary, candidate handling, inspection, and recovery events are linked.
- Status transitions: allowed states are visible and invalid promotion paths are absent.
- Policy decision records: allow, deny, and clarification-required outcomes are explicit before governed boundaries.
- Context inclusion/exclusion records: included and excluded context items carry source, classification, scope, provenance, revocation, minimization, and rationale evidence.
- Model invocation boundary evidence: selected model profile, governed input, output or unavailable-runtime result, timing, and failure details are recorded.
- Candidate quarantine evidence: generated output is stored as inactive candidate material with origin, validation state, and promotion state.
- Recovery evidence: correction, invalidation, supersession, rollback, deletion, and detachment paths are distinguishable when used.
- Read-first inspection projection: local status/audit inspection exposes state and evidence without mutation authority.

## 9. Implementation Readiness Checklist

| Question | Answer |
| --- | --- |
| Is behavior defined? | Yes. The local request-to-candidate governed slice is defined from request entry through read-first inspection. |
| Is scoped work named? | Yes. Runtime Slice 001 is the scoped technical contract for a minimal kernel/Core Module boundary and local model invocation boundary. |
| Are non-goals explicit? | Yes. Runtime code, scheduler, persistence backend, broad workflow/action frameworks, remote services, production UI, real learning, fine-tuning, distributed inference, and broad benchmarks are excluded. |
| Are required records defined? | Yes. The minimum records, purposes, authority rules, and evidence obligations are defined in Section 4. |
| Are positive and negative paths defined? | Yes. Sections 6 and 7 define required success and safe-failure scenarios. |
| Is validation evidence defined? | Yes. Section 8 defines the evidence a later implementation must prove. |
| Does this artifact authorize runtime code by itself? | Not by itself; runtime code may start only in a later implementation task when this contract is selected as the owned definition. |
