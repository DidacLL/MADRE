# Packet B7 — Final Integration Review

**Use with:** all accepted class definitions and `01-classmap-sprint-plan.md`  
**Scope:** full classmap consistency after all class bunches are reviewed.

## 1. Purpose

Validate the definitive classmap as a whole.

This pass does not add new classes unless a contradiction proves that the classmap cannot satisfy the reference scenarios. Prefer fixing responsibilities, boundaries, attributes, or operation pre/postconditions before adding anything.

## 2. Required Inputs

- Accepted definitions from B1 to B6.
- Status table from the sprint plan.
- Any user-approved boundary mechanism decisions.
- Latest use-case patches, especially the Workflow vs AgentRoutine distinction.

## 3. Whole-System Invariants

Check that the final classmap preserves:

- one coherent assistant experience without hiding module boundaries;
- kernel coordination without kernel reasoning;
- module-owned semantic reasoning and delegation;
- module-owned agents, workflows, routines, actions, knowledge, and learning boundary;
- `Workflow` as module-level planning blueprint;
- `AgentRoutine` as agent-level compound executable capability;
- `ReasoningTask` as planned occurrence, not prebuilt invocation;
- `AgentRequest` as concrete invocation context;
- `ModelAction` as inference-backed action without inference-output class;
- generated material as ordinary produced material with event evidence;
- broad uncertain knowledge model;
- learning as proposed behavior improvement;
- append-only trace and recoverability;
- boundary compatibility by construction, not optional logic.

## 4. Scenario Regression Set

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
- Which events must be journaled.
- Which boundaries prevent unsafe flow.

## 5. Naming Consistency

Reject any reintroduced alternative names for accepted concepts.

Use:

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

## 6. Operation Discipline

For every operation in the final classmap, verify:

- caller is clear;
- precondition is clear;
- postcondition is clear;
- side effects are clear;
- journal requirement is clear when relevant;
- operation does not smuggle unresolved boundary logic;
- operation does not make passive records active.

## 7. Output Expected

Produce:

1. final consistency verdict;
2. contradictions found;
3. classes marked `revise`;
4. missing but necessary pre/postconditions;
5. final patch list for the definitive classmap document.
