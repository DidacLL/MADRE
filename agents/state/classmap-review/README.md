# MADRE Classmap Review

This directory owns active class-by-class classmap discussion workflow for agents.

## Required Sources

Read these in order:

1. `AGENTS.md`
2. `agents/def/operating-contract.md`
3. `agents/def/product-orientation.md`
4. `agents/state/classmap-review/boundary-model.md`
5. `agents/state/classmap-review/class-review-index.md`
6. The packet for the current bunch under `agents/state/classmap-review/packets/**`

Use preserved references when a class needs behavior pressure:

- `docs/classmap-baseline.md`
- `docs/classmap-v2.md`
- `docs/usecases-baseline.md`

## Discussion Shape

Review one class or one named bunch at a time.

For each class:

1. State the class purpose in one sentence.
2. State why the class exists and what breaks if it is removed or merged.
3. List stable responsibilities.
4. List adjacent classes and ownership or usage relations.
5. Propose minimal attributes with justification.
6. Propose minimal operations with caller, preconditions, postconditions, and side effects.
7. Check the relevant use-case clusters from the index.
8. Record unresolved questions that block finalization.
9. Produce a clean classmap patch proposal after discussion.

Use the current packet to keep class-specific questions, adjacent concepts, behavior pressure, and quality expectations in view.

## Class Admission

A class belongs in the runtime classmap when it owns stable runtime identity, lifecycle, ownership boundary, responsibility, required runtime-flow participation, or boundary participation.

Attributes support identity, ownership, lifecycle, relation, traceability, or accepted invariants. Operations require clear caller, preconditions, postconditions, and side effects. Behavior with unresolved mechanics stays as responsibility prose or an open question.

## Boundary Discipline

Boundary compatibility is designed through composed constraints across the request path. Planning-time compatibility applies to `Workflow`. Execution-time compatibility applies to `AgentRoutine`, `AgentAction`, and `ModelAction`.

Use `PolicyDecision` for recorded outcomes and `RuntimeEvent` for runtime evidence. Treat `ContextBundle`, `ReasoningArtifact`, `KnowledgeRecord`, and model output as governed material whose runtime use depends on the composed path.

## Output Template

```markdown
## Class under review: `<ClassName>`

### 1. Current verdict
Keep / remove / defer / merge / rename.

### 2. Justification
Why this class exists and the impact of removal or merge.

### 3. Responsibilities
Stable responsibilities.

### 4. Boundaries
Runtime limits and composed-constraint participation.

### 5. Relationships
Adjacent classes and ownership or usage relations.

### 6. Minimal attributes
| Attribute | Type | Justification | Keep / defer |
|---|---|---|---|

### 7. Minimal operations
| Operation | Caller | Preconditions | Postconditions | Keep / defer |
|---|---|---|---|---|

### 8. Use-case checks
Reference use cases that stress this class.

### 9. Open questions
Questions blocking finalization.
```
