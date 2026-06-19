# MADRE Classmap Review Workflow — Agent Instructions

**State:** working instruction  
**Scope:** LLM-assisted class-by-class discussion workflow only.  
**Not product documentation:** this file governs how to discuss and refine the classmap. It is not part of the MADRE runtime architecture.

## 1. Purpose

Use this file to keep class-by-class MADRE classmap discussions narrow, conservative, and context-safe.

Each discussion session must refine one class or one explicitly named class bunch. The objective is to end with a definitive full classmap design without importing generic agent-framework patterns, backend assumptions, or earlier speculative vocabulary.

## 2. Source Discipline

Use only the materials explicitly provided in the current session:

1. This workflow file.
2. The current sprint plan file.
3. The current class-specific packet.
4. Any current classmap baseline or patch text explicitly pasted by the user.

Do not infer authority from older chats, obsolete notes, generated reports, hidden project history, implementation guesses, or common LLM-agent architecture patterns.

When context conflicts, ask which source is authoritative instead of merging both.

## 3. Discussion Mode

Discuss design before writing final class definitions.

For each class, proceed in this order:

1. Restate the class purpose in one sentence.
2. Identify why the class is needed at all.
3. Identify what breaks if the class is removed or merged into another class.
4. Identify what must not belong to the class.
5. Review relationships to adjacent classes.
6. Review minimum attributes.
7. Review minimum operations.
8. Define preconditions and postconditions only for stable operations.
9. List unresolved design questions.
10. Produce a clean patch proposal for the sprint plan or classmap only after discussion.

Do not rewrite the whole architecture during one class discussion.

## 4. Class Creation Discipline

A class is allowed only if it has at least one of these properties:

- stable runtime identity;
- stable ownership boundary;
- stable lifecycle;
- stable responsibility that cannot be represented as a value inside another class;
- required participation in the canonical MADRE runtime flow.

A proposed attribute is allowed only if it is currently necessary to preserve identity, ownership, lifecycle, relation, traceability, or an accepted invariant.

A proposed operation is allowed only if its caller, preconditions, postconditions, and side effects are clear. Otherwise, describe the behavior as responsibility prose and leave operation design open.

Never add classes, attributes, operations, enums, managers, services, adapters, repositories, registries, or factories only because they are common in Java/backend/agent frameworks.

## 5. Boundary and Authority Discipline

Do not hide unresolved boundary design behind vague fields or overridable methods.

Avoid fields such as:

- `approved: Boolean`
- `policyChecked: Boolean`
- `risk: Boolean`
- `safe: Boolean`
- `allowed: Boolean`
- generic text-only boundary bags

MADRE requires boundary compatibility by design. Until the boundary mechanism is fully designed, record the need as an invariant or unresolved question.

Do not create a new boundary class unless the user explicitly approves it during the boundary discussion.

## 6. Workflow and AgentRoutine Discipline

Keep the abstraction levels separate.

`Workflow` is module-level and planning-time. It is a reusable blueprint that creates or refines `ReasoningTask` occurrences inside a `ReasoningPlan`.

`AgentRoutine` is agent-level and execution-time. It is a compound executable agent capability. It receives `AgentRequest`, may coordinate lower-level steps/actions, and returns `AgentResponse`.

A `Workflow` does not receive `AgentRequest`, does not return `AgentResponse`, and does not execute generated tasks by itself.

## 7. Knowledge Discipline

`KnowledgeRecord` is broad retained module knowledge, not verified factual truth.

Knowledge may originate from user input, generated material, web research, imports, extraction, fiction, style references, procedures, hypotheses, corrections, or module reasoning.

The important distinction is not origin. The important distinction is permitted use: provenance, scope, sensitivity, intended use, truth level, truth authority, confidence, correction, and lifecycle.

Do not collapse `KnowledgeRecord`, `ReasoningArtifact`, and `LearningCandidate`.

## 8. Inference Discipline

Inference is represented as `ModelAction` execution plus ordinary produced material and `RuntimeEvent` evidence.

Do not reintroduce separate inference-output or inference-record classes unless the discussion proves that `RuntimeEvent`, `AgentResponse`, `ReasoningArtifact`, and `ModelBinding` cannot satisfy model-backed traceability.

Generated material is not truth, knowledge, policy, behavior, routing, action authority, or learning by itself.

## 9. Output Format for Each Class Session

Use this structure:

```markdown
## Class under review: `<ClassName>`

### 1. Current verdict
Keep / remove / defer / merge / rename.

### 2. Justification
Why this class exists and what breaks without it.

### 3. Responsibilities
Only stable responsibilities.

### 4. Boundaries
What the class must not do.

### 5. Relationships
Adjacent classes and ownership/usage relations.

### 6. Minimal attributes
| Attribute | Type | Justification | Keep / defer |
|---|---|---|---|

### 7. Minimal operations
| Operation | Caller | Preconditions | Postconditions | Keep / defer |
|---|---|---|---|---|

### 8. Use-case checks
Which reference use cases stress this class.

### 9. Open questions
Only unresolved questions that block finalization.

### 10. Patch proposal
Clean text that can be copied into the classmap or sprint plan.
```

## 10. Session Completion Rule

At the end of each discussion, update the sprint plan status:

- `pending`: not discussed yet.
- `in discussion`: current class/bunch under review.
- `accepted`: definition stable enough for classmap.
- `blocked`: design depends on unresolved upstream issue.
- `revise`: previously accepted but affected by later decision.

Keep updates narrow. Do not edit unrelated classes.
