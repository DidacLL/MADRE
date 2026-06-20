# B7 Final Integration Review

Scope: full classmap consistency after all class bunches are reviewed.

## Purpose

Validate the definitive classmap as a whole.

Add a class during this pass only when a contradiction proves that the classmap fails a reference scenario. Prefer fixing responsibilities, boundaries, attributes, or operation pre/postconditions first.

## Required Inputs

- Accepted definitions from B1 to B6.
- Status table from `agents/state/classmap-review/class-review-index.md`.
- User-approved boundary mechanism decisions.
- Latest use-case patches, especially the Workflow versus AgentRoutine distinction.

## Whole-System Invariants

Check that the final classmap preserves:

- one coherent assistant experience with visible module boundaries where relevant
- kernel coordination with module-owned reasoning
- module-owned semantic reasoning and delegation
- module-owned agents, workflows, routines, actions, knowledge, and learning boundary
- `Workflow` as module-level planning blueprint
- `AgentRoutine` as agent-level compound executable capability
- `ReasoningTask` as planned occurrence
- `AgentRequest` as concrete invocation context
- `ModelAction` as inference-backed action represented through ordinary output and event evidence
- generated material as ordinary produced material with event evidence
- broad uncertain knowledge model
- learning as proposed behavior improvement
- append-only trace and recoverability
- boundary compatibility by construction

## Scenario Regression Set

Run the final classmap against these scenario clusters:

1. UC-A: ordinary interaction and preference knowledge.
2. UC-B: academic writing with safe delegation.
3. UC-C: web research with privacy and injection risks.
4. UC-D: math reasoning vs simple math action.
5. UC-E: LaTeX document production and recovery.
6. UC-F: knowledge and learning distinction.
7. UC-G: delayed work and background supersession.

For each scenario, verify:

- Which module receives the initial request?
- Which module owns semantic delegation?
- Which requests are created?
- Whether a plan is needed.
- Whether workflow application is planning-time only.
- Which tasks are created.
- Which executable capabilities are invoked.
- Which context is minimized.
- Which artifacts are produced.
- Which knowledge/candidate/learning transitions occur.
- Which events are journaled.
- Which boundaries preserve the safe flow.

## Naming Consistency

Use the accepted names consistently:

- `AgentAction`
- `ModelAction`
- `ReasoningRequest`
- `AgentRequest`
- `ReasoningArtifact`
- `KnowledgeRecord`
- `KnowledgeCandidate`
- `LearningCandidate`
- `RuntimeEvent`
- `RuntimeJournal`

## Operation Discipline

For every operation in the final classmap, verify caller, precondition, postcondition, side effects, journal requirement, boundary participation, and active/passive object role.

## Output

Produce final consistency verdict, contradictions found, classes marked `revise`, missing pre/postconditions, and final patch list for the definitive classmap document.
