# B6 Trace And Authority Evidence

Classes under review: `PolicyDecision`, `RuntimeJournal`, `RuntimeEvent`.

## Required Context

Runtime trace is central to MADRE. It supports inspectability, recovery, correction, rollback, invalidation, supersession, deletion/detachment, action evidence, model-backed execution evidence, context evidence, and candidate lifecycle evidence.

`PolicyDecision` is a passive record of boundary or authorization outcome.

`RuntimeJournal` is append-only trace mechanism.

`RuntimeEvent` is append-only runtime fact/evidence. It records what happened.

## Adjacent Concepts

- Boundary mechanism produces execution authorization; policy decision records outcome.
- Model-backed execution is recorded as `RuntimeEvent` evidence.
- Knowledge/lifecycle transitions are evented.
- Plan/task transitions are evented.
- Runtime history and active knowledge are different concerns.

## Use-Case Pressure

- UC-C: blocked private context leakage and prompt injection evidence.
- UC-E: LaTeX compile failure, repair, and success evidence.
- UC-F: knowledge correction, learning proposal, unsafe learning rejection.
- UC-G: delayed work, partial result, superseded foreground answer, recovery.

## `PolicyDecision`

Review focus:

- Is it necessary as a separate passive record, or can `RuntimeEvent` cover it?
- If kept, what fields are required beyond outcome/reason/affected/event?
- Which wording and fields keep execution authority in the boundary mechanism?

Boundary focus:

- passive boundary outcome
- affected object references
- reason/evidence references
- relation to runtime event

## `RuntimeJournal`

Review focus:

- Is `journalId` enough?
- Is `append(event)` stable?
- What must be stored/referenced while keeping repository concerns out of the classmap?
- What query behavior is needed later and can remain outside the class definition now?

Boundary focus:

- append-only trace mechanism
- event storage/reference
- recovery support
- inspection support

## `RuntimeEvent`

Review focus:

- What attributes are mandatory for inspectability?
- Is `eventType: Text` acceptable until taxonomy?
- How to represent model-backed execution through event evidence?
- What event types are stable enough to name now?

Boundary focus:

- runtime fact/evidence
- actor/source/time/object references
- lifecycle transition evidence
- model/context/action evidence

## Output

Finalize the minimal trace model that supports recovery and evidence while keeping database schema concerns outside the classmap.
