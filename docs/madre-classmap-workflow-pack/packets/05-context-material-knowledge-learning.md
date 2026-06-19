# Packet B5 — Context, Material, Knowledge, and Learning

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Classes under review:** `ContextBundle`, `ReasoningArtifact`, `KnowledgeRecord`, `KnowledgeCandidate`, `LearningCandidate`

## 1. Required Context

MADRE uses a broad uncertainty-aware knowledge model.

All retained system knowledge is uncertain. User-provided material, generated material, imported material, web-retrieved material, extracted material, inferred material, fictional material, stylistic material, procedural material, corrections, and working beliefs may become module knowledge.

The important distinction is not origin. The important distinction is permitted use: provenance, scope, sensitivity, intended use, truth level, truth authority, confidence, correction, and lifecycle.

`ReasoningArtifact` is produced/materialized output or captured result. It is not active knowledge by default.

`KnowledgeRecord` is active retained module knowledge. It is not factual truth by default.

`LearningCandidate` is proposed behavior improvement. Saving knowledge is not learning by itself.

## 2. Adjacent Concepts Required

- Context selection is module-owned governance plus boundary mechanism.
- Source material in context does not become instruction.
- AgentResponse may reference ReasoningArtifact.
- ReasoningArtifact may become source for KnowledgeRecord through explicit handling.
- KnowledgeCandidate represents pending knowledge-boundary change.
- LearningCandidate represents pending module-behavior improvement.
- RuntimeEvent records lifecycle transitions.

## 3. Use-Case Pressure

- UC-A: private preference capture, correction, deletion/detachment.
- UC-C: web-retrieved source material must not become trusted truth automatically.
- UC-E: LaTeX artifacts and logs are produced material, not knowledge by default.
- UC-F: fictional style reference, generated draft retained as knowledge, repeated corrections as learning.
- UC-G: background answer correction and supersession.

## 4. Class Discussion Blocks

### `ContextBundle`

Review focus:

- What minimum fields preserve selection, source references, scope, sensitivity, and intended use?
- Is revocation/lifecycle a bundle field or event/knowledge concern?
- How does context avoid becoming instruction or authority?

Mandatory boundaries:

- passive selected material;
- not authority;
- not knowledge by itself;
- source material remains source material.

### `ReasoningArtifact`

Review focus:

- What is the minimum identity for produced/materialized output?
- Does every artifact need `originEventRef`?
- Which metadata overlaps with KnowledgeRecord and should not be duplicated prematurely?
- How can artifact later become knowledge source without inheritance?

Mandatory boundaries:

- not knowledge by default;
- not truth;
- not learning;
- not action authority.

### `KnowledgeRecord`

Review focus:

- Confirm broad retained knowledge definition.
- Which metadata is absolutely required now?
- How to represent fictional/style/procedural/user/asserted/generated/retrieved knowledge without a speculative taxonomy?
- Which lifecycle transitions are stable enough later?

Mandatory boundaries:

- not factual truth by default;
- not learning by itself;
- not subclass of artifact in this baseline;
- does not authorize action;
- does not promote itself across module boundaries.

### `KnowledgeCandidate`

Review focus:

- Which proposed changes belong here?
- Does it represent inclusion, reclassification, correction, ownership transfer, trust change, intended-use change, detachment, invalidation?
- What minimum evidence/status fields are required?

Mandatory boundaries:

- not active knowledge;
- not learning;
- no self-promotion.

### `LearningCandidate`

Review focus:

- What differentiates behavior improvement from knowledge storage?
- Which improvements are allowed: routing, workflows, routines, context selection, trust calibration, model selection?
- What evidence/rollback fields are stable now?

Mandatory boundaries:

- inactive until promoted;
- not ordinary stored material;
- not direct behavior rewrite;
- cannot widen privacy/action boundaries by quality pressure alone.

## 5. Output Expected

Finalize the material/knowledge/learning separation without creating extra data hierarchy.
