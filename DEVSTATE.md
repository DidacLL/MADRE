# MADRE: current focus

The first runtime slice exists: durable immediate/delayed work, fake inference, lifecycle inspection and process-interruption recovery. Its 11 tests passed on Windows here and on Linux according to the implementation executor. Runtime/test code at `3c74bac` is the tested baseline; use current code/tests and live Git state to verify later changes.

Next: CLASSIC implements **withdraw pending work**. A user can schedule work but currently cannot withdraw it through the API/CLI. No further architecture session, integration-only handoff, CI work or Owner decision is needed.

```text
NEXT ACTOR: CLASSIC
WHY THIS ACTOR: One bounded runtime behavior using existing lifecycle, journal and lock primitives.

TASK: Let the local user withdraw pending work so it cannot execute later.
START FROM: Latest main in DidacLL/MADRE; create agentic/withdraw-pending-work. Complete routine PR/integration work within this task when permissions allow.
READ: AGENTS.md; madre/runtime.py, _journal.py, _lock.py and cli.py; existing tests. Product basis: dossier FR-008/D-008/D-010 (explicit recovery outcomes and local user control).

DO: Add cancel_work(db, work_id) and CLI cancel --work-id. Under the existing worker lock, atomically change queued or recovered work to blocked with an explicit user_cancelled journal reason. Preserve attempt/output/history. Do not requeue recovered work during cancellation. Repeated cancellation is idempotent with no extra event. Missing work/storage and running or other terminal states return clear errors without mutation; lock contention returns existing busy behavior. Add a README example.
DO NOT: Add a new state, schema migration, running-process cancellation, scheduler service, providers, dependencies, framework or CI. Generated output must never invoke cancellation.

DONE WHEN: Withdrawn work stays blocked across restart and future worker calls, with zero new backend calls; tests cover queued/recovered work, idempotence, invalid targets and a live worker race. Existing lifecycle tests remain green.
VALIDATE WITH: Focused new tests, then python -m unittest discover -s tests -v once for the runtime change; CLI example on a temporary database; git diff --check. Reuse results for subsequent prose-only edits.

ESCALATE IF: Existing locking/lifecycle cannot meet these bounds without broader change. Bring reproduction to this coordinating task; do not invent a larger design.
OWNER DECISION NEEDED: NONE.
```
