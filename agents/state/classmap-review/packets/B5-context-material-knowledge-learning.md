# B5 Context Material Knowledge And Learning

Classes under review: `ContextBundle`, `ReasoningArtifact`, `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate`.

## Required Context

MADRE uses a broad uncertainty-aware knowledge model.

Retained system knowledge may come from user-provided material, generated material, imported material, web-retrieved material, extracted material, inferred material, fictional material, stylistic material, procedural material, corrections, and working beliefs.

The important distinction is permitted use: provenance, scope, sensitivity, intended use, truth level, truth authority, confidence, correction, and lifecycle.

`ReasoningArtifact` is produced/materialized output or captured result.

`KnowledgeRecord` is active retained module knowledge.

`LearningCandidate` is proposed behavior improvement. Saving knowledge and changing behavior remain separate transitions.

## Adjacent Concepts

- Context selection is module-owned governance plus boundary mechanism.
- Source material in context stays source material.
- `AgentResponse` may reference `ReasoningArtifact`.
- `ReasoningArtifact` may become source for `KnowledgeRecord` through explicit handling.
- `KnowledgeCandidate` represents pending knowledge-boundary change.
- `LearningCandidate` represents pending module-behavior improvement.
- `RuntimeEvent` records lifecycle transitions.

## Use-Case Pressure

- UC-A: private preference capture, correction, deletion/detachment.
- UC-C: web-retrieved source material receives explicit trust and use boundaries.
- UC-E: LaTeX artifacts and logs are produced material.
- UC-F: fictional style reference, generated draft retained as knowledge, repeated corrections as learning.
- UC-G: background answer correction and supersession.

## `ContextBundle`

Review focus:

- What minimum fields preserve selection, source references, scope, sensitivity, and intended use?
- Is revocation/lifecycle a bundle field or event/knowledge concern?
- How does context stay selected material with governed use?

Boundary focus:

- passive selected material
- source references
- scope and sensitivity
- intended use
- revocation and lifecycle references

## `ReasoningArtifact`

Review focus:

- What is the minimum identity for produced/materialized output?
- Does every artifact need `originEventRef`?
- Which metadata overlaps with `KnowledgeRecord` and should be deferred until justified?
- How can artifact later become knowledge source through explicit handling?

Boundary focus:

- produced/materialized output
- origin event
- artifact lifecycle
- later knowledge-source relation

## `KnowledgeRecord`

Review focus:

- Confirm broad retained knowledge definition.
- Which metadata is required now?
- How to represent fictional/style/procedural/user/asserted/generated/retrieved knowledge while keeping taxonomy stable?
- Which lifecycle transitions are stable enough later?

Boundary focus:

- active retained module knowledge
- provenance
- scope and sensitivity
- intended use
- truth level, truth authority, confidence
- correction and lifecycle

## `KnowledgeCandidate`

Review focus:

- Which proposed changes belong here?
- Does it represent inclusion, reclassification, correction, ownership transfer, trust change, intended-use change, detachment, invalidation?
- What minimum evidence/status fields are required?

Boundary focus:

- pending knowledge-boundary change
- evidence and status
- promotion/rejection trace

## `LearningCandidate`

Review focus:

- What differentiates behavior improvement from knowledge storage?
- Which improvements are allowed: routing, workflows, routines, context selection, trust calibration, model selection?
- What evidence/rollback fields are stable now?

Boundary focus:

- pending behavior improvement
- evidence
- rollback path
- privacy/action boundaries preserved by quality pressure

## Output

Finalize the material/knowledge/learning separation while keeping the data hierarchy minimal.
