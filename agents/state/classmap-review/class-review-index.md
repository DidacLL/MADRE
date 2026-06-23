# Class Review Index

Use this order to reduce rework during class-by-class discussion.

## Review Order

| Bunch | Scope | Status | Depends on | Notes |
|---|---|---|---|---|
| B0 | Boundary model orientation | pending |  | Use `agents/state/classmap-review/boundary-model.md` first. |
| B1 | `MADREKernel`, `ReasoningModule` | pending | B0 | Use `agents/state/classmap-review/packets/B1-runtime-ownership-entry.md`. |
| B2 | `MADREAgent`, `SimpleAgent`, `TwinAgent`, `ComplexAgent`, `SystemAgent` | pending | B1 | Use `agents/state/classmap-review/packets/B2-agent-family.md`. |
| B3 | `Workflow`, `AgentRoutine`, `AgentAction`, `ModelAction`, `ModelBinding` | pending | B0, B1, B2 | Use `agents/state/classmap-review/packets/B3-capability-taxonomy.md`. |
| B4 | `ReasoningRequest`, `ReasoningResponse`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `AgentResponse` | pending | B0, B3 | Use `agents/state/classmap-review/packets/B4-request-plan-invocation.md`. |
| B5 | `ContextBundle`, `ReasoningArtifact`, `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate` | pending | B0, B4 | Use `agents/state/classmap-review/packets/B5-context-material-knowledge-learning.md`. |
| B6 | `PolicyDecision`, `RuntimeJournal`, `RuntimeEvent` | pending | B0 | Use `agents/state/classmap-review/packets/B6-trace-authority-evidence.md`. |
| B7 | Full classmap consistency | pending | B1-B6 | Use `agents/state/classmap-review/packets/B7-final-integration-review.md`. |

## Class Status

| Class | Bunch | Status | Depends on | Finalization notes |
|---|---|---|---|---|
| `MADREKernel` | B1 | pending | B0 | Deterministic coordination and target handling. |
| `ReasoningModule` | B1 | pending | B0 | Owns module reasoning, knowledge, agents, workflows, routines, actions, and local governance. |
| `MADREAgent` | B2 | pending | B1 | Abstract module-owned runtime actor. |
| `SimpleAgent` | B2 | pending | `MADREAgent` | Direct execution profile or class. |
| `TwinAgent` | B2 | pending | `MADREAgent`, B0 | Checked-output invariant. |
| `ComplexAgent` | B2 | pending | `MADREAgent` | Foreground, reasoning, and reflection capacity with local constraints. |
| `SystemAgent` | B2 | pending | `ComplexAgent`, `ReasoningModule` | Module-bound main operational agent. |
| `Workflow` | B3 | pending | `ReasoningModule`, B0 | Module-level planning blueprint that creates or refines tasks. |
| `AgentRoutine` | B3 | pending | `MADREAgent`, `AgentAction`, B0 | Agent-level compound executable capability. |
| `AgentAction` | B3 | pending | B0 | Smallest executable capability. |
| `ModelAction` | B3 | pending | `AgentAction`, `ModelBinding` | Inference-backed action. |
| `ModelBinding` | B3 | pending | B0 | Constrains model-backed execution. |
| `ReasoningRequest` | B4 | pending | B1, B0 | Passive module-level reasoning or delegation need. |
| `ReasoningResponse` | B4 | pending | `ReasoningRequest` | Passive module-level response. |
| `ReasoningPlan` | B4 | pending | B3 | Durable reasoning-continuity aggregate. |
| `ReasoningTask` | B4 | pending | `ReasoningPlan`, B3, B0 | Planned execution occurrence. |
| `AgentRequest` | B4 | pending | B2, B3, B0 | Concrete executable invocation context. |
| `AgentResponse` | B4 | pending | `AgentRequest`, `ReasoningArtifact` | Concrete invocation result. |
| `ContextBundle` | B5 | pending | B0, B4 | Minimized selected context. |
| `ReasoningArtifact` | B5 | pending | `AgentResponse`, `RuntimeEvent` | Produced or materialized output. |
| `KnowledgeRecord` | B5 | pending | `ContextBundle`, `RuntimeEvent` | Broad uncertain retained module knowledge. |
| `KnowledgeCandidate` | B5 | pending | `KnowledgeRecord`, B0 | Pending knowledge-boundary change. |
| `LearningCandidate` | B5 | pending | `RuntimeEvent`, B0 | Pending evaluated module behavior improvement. |
| `PolicyDecision` | B6 | pending | B0 | Passive recorded boundary outcome. |
| `RuntimeJournal` | B6 | pending | all runtime transitions | Append-only trace mechanism. |
| `RuntimeEvent` | B6 | pending | all classes | Append-only runtime fact or evidence. |

## Use-Case Clusters

- UC-A: ordinary interaction and preference knowledge. Pressure: `CoreModule`, `SystemAgent`, `KnowledgeRecord`, `KnowledgeCandidate`, `RuntimeEvent`.
- UC-B: academic writing with safe delegation. Pressure: `ReasoningRequest`, `ReasoningResponse`, `ReasoningModule`, `SystemAgent`, `ContextBundle`.
- UC-C: web research with privacy and injection risks. Pressure: boundary mechanism, `ResearchModule`, `ContextBundle`, `PolicyDecision`, `RuntimeEvent`, `SecurityCheck TwinAgent`.
- UC-D: math reasoning versus simple math action. Pressure: `AgentAction`, `SimpleAgent`, `ReasoningRequest`, `ReasoningTask`, `MathModule`.
- UC-E: LaTeX document production and recovery. Pressure: `Workflow`, `AgentRoutine`, `AgentAction`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `ReasoningArtifact`, `RuntimeEvent`.
- UC-F: knowledge and learning distinction. Pressure: `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate`, `RuntimeEvent`, boundary mechanism.
- UC-G: delayed work and background supersession. Pressure: `ReasoningPlan`, `ReasoningTask`, `RuntimeJournal`, `RuntimeEvent`, `ComplexAgent`, `SystemAgent`.

## Cross-Bunch Invariants

- Kernel target resolution is explicit valid target or CoreModule fallback.
- Modules own semantic reasoning and delegation.
- Main Agent role is held by one module-owned `SystemAgent`.
- `SystemAgent` is locally bound.
- `Workflow` is module-level planning-time task generation and refinement.
- `AgentRoutine` is agent-level execution-time compound action.
- `ReasoningTask` represents planned execution before execution selection.
- `ModelAction` carries model-backed execution through ordinary runtime trace and artifact paths.
- `KnowledgeRecord` is broad uncertain retained knowledge.
- `ReasoningArtifact` is produced or materialized output.
- `LearningCandidate` is proposed behavior improvement.
- `PolicyDecision` records an outcome.
- Boundary enforcement is designed through composed runtime constraints.
- Runtime trace supports inspection and recovery.
