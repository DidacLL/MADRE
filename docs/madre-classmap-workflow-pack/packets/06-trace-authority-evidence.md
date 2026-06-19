# Packet B6 — Trace and Authority Evidence

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Classes under review:** `PolicyDecision`, `RuntimeJournal`, `RuntimeEvent`

## 1. Required Context

Runtime trace is central to MADRE. It must support inspectability, recovery, correction, rollback, invalidation, supersession, deletion/detachment, action evidence, model-backed execution evidence, context evidence, and candidate lifecycle evidence.

`PolicyDecision` is a passive record of boundary or authorization outcome. It is not the object that grants execution authority by itself.

`RuntimeJournal` is append-only trace mechanism, not a general repository or reasoning actor.

`RuntimeEvent` is append-only runtime fact/evidence. It records what happened; it does not authorize what happened.

## 2. Adjacent Concepts Required

- Boundary mechanism produces execution authorization; policy decision records outcome.
- Model-backed execution is recorded as RuntimeEvent evidence.
- Knowledge/lifecycle transitions are evented.
- Plan/task transitions are evented.
- Runtime history and active knowledge are different concerns.

## 3. Use-Case Pressure

- UC-C: blocked private context leakage and prompt injection evidence.
- UC-E: LaTeX compile failure, repair, and success evidence.
- UC-F: knowledge correction, learning proposal, unsafe learning rejection.
- UC-G: delayed work, partial result, superseded foreground answer, recovery.

## 4. Class Discussion Blocks

### `PolicyDecision`

Review focus:

- Is it necessary as a separate passive record, or can RuntimeEvent cover it?
- If kept, what fields are required beyond outcome/reason/affected/event?
- How to make clear it does not grant authority?

Mandatory boundaries:

- not policy engine;
- not execution permit;
- not authority by itself;
- generated material cannot create or widen it.

### `RuntimeJournal`

Review focus:

- Is `journalId` enough?
- Is `append(event)` stable?
- What must be stored/referenced without becoming repository abstraction?
- What query behavior is needed later but not class-mapped now?

Mandatory boundaries:

- not general logger;
- not knowledge repository;
- not actor;
- not authority;
- does not make recorded material true.

### `RuntimeEvent`

Review focus:

- What attributes are mandatory for inspectability?
- Is `eventType: Text` too weak or acceptable until taxonomy?
- How to represent model-backed execution without inference-record class?
- What event types are stable enough to name now?

Mandatory boundaries:

- not behavior;
- not authority;
- not truth;
- not replacement for domain objects.

## 5. Output Expected

Finalize the minimal trace model that supports recovery and evidence without becoming a database schema.
