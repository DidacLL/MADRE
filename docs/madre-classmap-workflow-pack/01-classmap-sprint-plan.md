# MADRE Class-by-Class Classmap Review Plan

**State:** working plan  
**Scope:** discussion workflow for reaching the definitive MADRE runtime classmap.  
**Update rule:** each discussion agent may update only status, decision notes, blockers, and next-step fields relevant to the class or bunch it reviewed.

## 1. Objective

Produce a definitive, conservative, implementation-ready runtime classmap for MADRE by reviewing each accepted class in a controlled order.

The workflow splits context into small packets so that each LLM-assisted session receives only the global instructions, the current sprint plan, the relevant class packet, and the current user discussion.

## 2. Global Review Order

The order is designed to minimize rework.

1. **Boundary model orientation**  
   Cross-cutting discussion before class finalization. No new class is accepted here. The goal is to define invariants that will affect `ReasoningTask`, `AgentRequest`, `AgentAction`, `ModelAction`, `Workflow`, `AgentRoutine`, `PolicyDecision`, and `RuntimeEvent`.

2. **Runtime ownership and entry**  
   `MADREKernel`, `ReasoningModule`.

3. **Agent family**  
   `MADREAgent`, `SimpleAgent`, `TwinAgent`, `ComplexAgent`, `SystemAgent`.

4. **Executable capability taxonomy**  
   `Workflow`, `AgentRoutine`, `AgentAction`, `ModelAction`, `ModelBinding`.

5. **Requests, plans, tasks, and invocations**  
   `ReasoningRequest`, `ReasoningResponse`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `AgentResponse`.

6. **Context, material, knowledge, and learning**  
   `ContextBundle`, `ReasoningArtifact`, `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate`.

7. **Trace and authority evidence**  
   `PolicyDecision`, `RuntimeJournal`, `RuntimeEvent`.

8. **Final integration pass**  
   Whole-classmap consistency check against the reference use cases and naming/operation discipline.

## 3. Bunch Status

| Bunch | Packet | Scope | Status | Blockers | Last decision notes |
|---|---|---|---|---|---|
| B0 | `packets/00-boundary-model-orientation.md` | Boundary invariants affecting later classes | pending | none | |
| B1 | `packets/01-runtime-ownership-entry.md` | `MADREKernel`, `ReasoningModule` | pending | B0 recommended first | |
| B2 | `packets/02-agent-family.md` | `MADREAgent`, `SimpleAgent`, `TwinAgent`, `ComplexAgent`, `SystemAgent` | pending | B1 recommended first | |
| B3 | `packets/03-capability-taxonomy.md` | `Workflow`, `AgentRoutine`, `AgentAction`, `ModelAction`, `ModelBinding` | pending | B0, B1, B2 context | |
| B4 | `packets/04-request-plan-invocation.md` | `ReasoningRequest`, `ReasoningResponse`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `AgentResponse` | pending | B0, B3 affect task/invocation semantics | |
| B5 | `packets/05-context-material-knowledge-learning.md` | `ContextBundle`, `ReasoningArtifact`, `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate` | pending | B0 affects context/knowledge use | |
| B6 | `packets/06-trace-authority-evidence.md` | `PolicyDecision`, `RuntimeJournal`, `RuntimeEvent` | pending | B0 affects authority evidence | |
| B7 | `packets/07-final-integration-review.md` | full classmap consistency | pending | all previous bunches | |

## 4. Class Status Table

| Class | Bunch | Status | Depends on | Finalization notes |
|---|---|---|---|---|
| `MADREKernel` | B1 | pending | B0 | Must remain deterministic coordination, not reasoning. |
| `ReasoningModule` | B1 | pending | B0 | Owns module reasoning, knowledge, agents, workflows, routines, actions, and local governance. |
| `MADREAgent` | B2 | pending | B1 | Abstract module-owned runtime actor. |
| `SimpleAgent` | B2 | pending | `MADREAgent` | Direct execution profile/class. |
| `TwinAgent` | B2 | pending | `MADREAgent`, B0 | Justified by checked-output invariant. |
| `ComplexAgent` | B2 | pending | `MADREAgent` | Foreground/reasoning/reflection capacity without extra authority. |
| `SystemAgent` | B2 | pending | `ComplexAgent`, `ReasoningModule` | Module-bound main operational agent, not global authority. |
| `Workflow` | B3 | pending | `ReasoningModule`, B0 | Module-level planning blueprint; creates/refines tasks. |
| `AgentRoutine` | B3 | pending | `MADREAgent`, `AgentAction`, B0 | Agent-level compound executable capability. |
| `AgentAction` | B3 | pending | B0 | Smallest executable capability. |
| `ModelAction` | B3 | pending | `AgentAction`, `ModelBinding` | Inference-backed action; no separate output class. |
| `ModelBinding` | B3 | pending | B0 | Constrains model-backed execution. |
| `ReasoningRequest` | B4 | pending | B1, B0 | Passive module-level reasoning/delegation need. |
| `ReasoningResponse` | B4 | pending | `ReasoningRequest` | Passive module-level response. |
| `ReasoningPlan` | B4 | pending | B3 | Durable reasoning-continuity aggregate. |
| `ReasoningTask` | B4 | pending | `ReasoningPlan`, B3, B0 | Planned execution occurrence; no prebuilt `AgentRequest`. |
| `AgentRequest` | B4 | pending | B2, B3, B0 | Concrete executable invocation context. |
| `AgentResponse` | B4 | pending | `AgentRequest`, `ReasoningArtifact` | Concrete invocation result. |
| `ContextBundle` | B5 | pending | B0, B4 | Minimized selected context; not instruction/authority. |
| `ReasoningArtifact` | B5 | pending | `AgentResponse`, `RuntimeEvent` | Produced/materialized output; not knowledge by default. |
| `KnowledgeRecord` | B5 | pending | `ContextBundle`, `RuntimeEvent` | Broad uncertain retained module knowledge. |
| `KnowledgeCandidate` | B5 | pending | `KnowledgeRecord`, B0 | Pending knowledge-boundary change. |
| `LearningCandidate` | B5 | pending | `RuntimeEvent`, B0 | Pending evaluated module behavior improvement. |
| `PolicyDecision` | B6 | pending | B0 | Passive recorded outcome, not authority object by itself. |
| `RuntimeJournal` | B6 | pending | all runtime transitions | Append-only trace mechanism. |
| `RuntimeEvent` | B6 | pending | all classes | Append-only runtime fact/evidence. |

## 5. Reference Use-Case Clusters

Use these scenario clusters to stress classes without pasting the full use-case deck every time.

### UC-A: Ordinary interaction and preference knowledge

Covers user preference capture, correction, deletion/detachment, and "do you know this?" queries.

Primary pressure: `CoreModule`, `SystemAgent`, `KnowledgeRecord`, `KnowledgeCandidate`, `RuntimeEvent`.

### UC-B: Academic writing with safe delegation

Covers college assignment drafting, academic style, private preference minimization, and module delegation.

Primary pressure: `ReasoningRequest`, `ReasoningResponse`, `ReasoningModule`, `SystemAgent`, `ContextBundle`.

### UC-C: Web research with privacy and injection risks

Covers recent-source research, neutralized web requests, prompt injection, private-context leakage, source checking, and offline research failure.

Primary pressure: boundary mechanism, `ResearchModule`, `ContextBundle`, `PolicyDecision`, `RuntimeEvent`, `SecurityCheck TwinAgent`.

### UC-D: Math reasoning vs simple math action

Covers simple arithmetic inside a larger task and non-trivial mathematical reasoning delegated to `MathModule`.

Primary pressure: `AgentAction`, `SimpleAgent`, `ReasoningRequest`, `ReasoningTask`, `MathModule`.

### UC-E: LaTeX document production and recovery

Covers module workflow application, LaTeX agent routines, bounded compile/check actions, error repair, malicious LaTeX, and final artifacts.

Primary pressure: `Workflow`, `AgentRoutine`, `AgentAction`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `ReasoningArtifact`, `RuntimeEvent`.

### UC-F: Knowledge and learning distinction

Covers fictional style reference, generated draft becoming knowledge, repeated corrections becoming learning, and unsafe learning rejection.

Primary pressure: `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate`, `RuntimeEvent`, boundary mechanism.

### UC-G: Delayed work and background supersession

Covers low-resource scheduling, partial result delivery, background reasoning correction, and cross-module worksheet production.

Primary pressure: `ReasoningPlan`, `ReasoningTask`, `RuntimeJournal`, `RuntimeEvent`, `ComplexAgent`, `SystemAgent`.

## 6. Mandatory Cross-Bunch Invariants

Every bunch must preserve:

- Kernel target resolution is explicit valid target or Core Module fallback, not semantic routing.
- Modules own semantic reasoning and delegation.
- Main Agent role is held by one module-owned `SystemAgent`.
- `SystemAgent` is locally bound, not globally authoritative.
- `Workflow` is module-level planning-time task generation/refinement.
- `AgentRoutine` is agent-level execution-time compound action.
- `ReasoningTask` does not contain a prebuilt `AgentRequest` before execution selection.
- `ModelAction` does not create model authority or separate generated-output identity.
- `KnowledgeRecord` is broad uncertain retained knowledge, not verified truth.
- `ReasoningArtifact` is produced/materialized output, not active knowledge by default.
- `LearningCandidate` is proposed behavior improvement, not knowledge storage.
- `PolicyDecision` records an outcome; it does not grant authority by itself.
- Boundary enforcement must not be reduced to optional overridable checks or loose booleans.
- Runtime trace must be sufficient for inspection and recovery without becoming a general repository abstraction.

## 7. Update Protocol

When a class or bunch is discussed, update only these fields:

- status;
- blockers;
- last decision notes;
- class finalization notes.

Do not rewrite accepted decisions from unrelated bunches unless the current decision explicitly invalidates them. In that case, mark the affected class as `revise` and record the reason.
