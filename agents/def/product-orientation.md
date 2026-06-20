# MADRE Product Orientation

Authority: compact derived digest. The TeX dossier at `docs/tex/MADRE-AgenticSystem.tex` wins for product truth.

MADRE is a local-first, model-agnostic runtime kernel for governed agentic systems. It coordinates foreground conversation, delayed reasoning, context selection, model invocation, bounded internal actions, memory, learning, audit, recovery, and user authority boundaries through software architecture.

## Runtime Thesis

- The language model is one replaceable runtime component inside MADRE.
- Modules own domain reasoning, knowledge, agents, workflows, routines, actions, and local governance.
- The kernel coordinates valid targets and execution flow.
- User authority, privacy, context, model usage, action scope, storage, learning, and remote transfer are runtime boundaries.
- Model output becomes useful material through governed runtime structures, trace, and review.

## Boundary Composition

Executable validity emerges from the composed boundary path. A request moves through modules, agents, workflows, routines, and actions while carrying effective constraints collected from each boundary participant.

Each path step declares capability and boundary metadata. Composition can narrow effective constraints. Concrete execution is valid when the selected invocation is compatible with the accumulated constraints and the runtime constructs the execution context required by that path.

`PolicyDecision` records a boundary outcome. `RuntimeEvent` records runtime evidence. `ContextBundle`, model output, developer-provided data, and policy records are descriptive material unless the composed runtime path makes a concrete invocation valid.

## Class Admission

Keep a class when it owns at least one stable runtime role:

- identity
- lifecycle
- ownership boundary
- responsibility that needs its own runtime owner
- required participation in the canonical runtime flow
- boundary participation needed for valid composition

Attributes preserve identity, ownership, lifecycle, relation, traceability, or accepted invariants. Operations need clear caller, preconditions, postconditions, and side effects.

## Classmap Orientation

- `Workflow`: module-level planning blueprint that generates or refines `ReasoningTask` instances inside a `ReasoningPlan`.
- `AgentRoutine`: agent-level compound executable capability that receives `AgentRequest` and returns `AgentResponse`.
- `AgentAction`: smallest executable agent capability.
- `ModelAction`: inference-backed `AgentAction` constrained by `ModelBinding`.
- `ModelBinding`: model selection and model-use constraints for model-backed execution.
- `PolicyDecision`: recorded boundary outcome.
- `RuntimeEvent`: append-only runtime fact or evidence.
- `KnowledgeRecord`: retained module knowledge with provenance, scope, sensitivity, confidence, correction state, and lifecycle.
- `ContextBundle`: minimized selected context for a runtime purpose.
- `ReasoningArtifact`: produced or materialized output.
- `LearningCandidate`: proposed behavior improvement awaiting evaluation.
