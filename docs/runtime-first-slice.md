# First runtime slice: durable scheduled inference

This is a bounded implementation contract subordinate to [the canonical dossier](tex/MADRE-AgenticSystem.tex), not a new product baseline. It proves lifecycle and recovery with deterministic inference; it does not claim to implement complete foreground conversation or the full runtime.

## Behavior and implementation boundary

Provide a local library and CLI to submit work, execute one eligible item, and inspect status, events and generated output. Submission durably acknowledges a WorkRecord before returning. Foreground work is immediately eligible; delayed work has a UTC `not_before` time and submission returns without waiting. A kernel scheduler selects eligible foreground work first, then delayed work, FIFO by persisted submission sequence within each class. Never execute future-dated work. No preemption or fairness guarantee is claimed in this slice.

Use Python 3.11+ standard library (`sqlite3`, `unittest`, `argparse`, `subprocess`), with `madre/` at repository root and `tests/`. SQLite owns atomic local persistence; MADRE code owns scheduling and transitions. Decision evidence: the repository has no runtime source/build manifest; local Python 3.13.3 with SQLite 3.49.1 is available; the dossier's Conceptual Components explicitly says JVM binding is not architectural authority. Java or Rust would add build/dependency setup without evidence this slice needs it. This bounded implementation choice does not fix future provider or application stacks. No agent framework or third-party runtime dependency is needed.

## Smallest contracts

Use plain records/functions; these names do not require a class hierarchy.

- `WorkRecord`: stable work/request IDs, fixed module and agent IDs, one-step plan reference, foreground/delayed mode, submission sequence, `not_before`, status, context and binding references, current attempt and recovery reason.
- `RuntimeJournal`: local SQLite records and append-only `RuntimeEvent` entries with ordered event ID, work/attempt references, transition, reason and timestamp. Persist each status transition and its event atomically. Acknowledge only committed submission. Never acknowledge success after a database error.
- One fixed `ReasoningModule` and module-owned agent submit a one-step `ModelAction`. `ContextBundle` contains only submitted text, source/request reference and fixed local-only scope. `ModelBinding` permits a configured fake backend/profile; a deterministic `PolicyDecision` allows only that scope and binding. Unknown bindings or invalid scope block before inference. No dynamic module registry, planner or policy language.
- `InferenceBackend`: replaceable `execute(context, profile)` function returning generated text or a typed failure. Supply two deterministic fakes (distinct outputs) and a failure fake. The backend receives no journal, scheduler, action executor or credentials.
- `InferenceRecord`: attempt ID, work ID, allowed profile/backend, timing, outcome/error and output reference. Commit its start with the running transition; commit final outcome, output and terminal work event together. `InferenceOutput` is generated material attached to that attempt; display it as such. Never parse it as commands, routing, policy, knowledge or candidate promotion.

## Lifecycle and restart contract

| Trigger | Observable transition and effect |
| --- | --- |
| Valid submission | absent -> queued; durable acknowledgement includes work ID |
| Eligible item, allowed binding/scope | queued -> running; persist policy and inference start before calling backend |
| Disallowed binding/scope | queued -> blocked; recorded policy reason, zero backend calls |
| Successful inference | running -> completed; record generated output atomically |
| Backend exception/invalid result | running -> failed; record error, no successful output, no automatic retry |
| Restart after interrupted inference | running -> recovered; unfinished attempt marked interrupted with unknown outcome and no published output |
| Explicit worker execution after recovery | recovered -> queued -> running; new attempt ID, same work ID, old attempt/events retained |

Opening for read-only inspection must never perform recovery, initialize storage or schedule work. An explicit worker invocation performs recovery, then executes at most one eligible item and exits. A recovered item keeps its original schedule/mode; it is inspectable and eligible for a subsequent explicit worker invocation, not silently completed. Queued future work survives restart without running early. Completed, failed and blocked work is never automatically rerun. No retry loop.

Permit one worker per database. Acquire a nonblocking OS advisory lock on a sidecar file for the canonical database path before recovery and hold it until worker exit (`msvcrt` on Windows, `fcntl` on POSIX). A competing worker reports busy without recovery or inference. The OS must release ownership on process death; a leftover lock file is not proof of a live worker. Keep database transactions short so status remains readable during inference. Test the locking boundary; never assume a second process means the first has died.

Recovery guarantees are for process interruption on healthy local storage. Execution may repeat after a crash between generation and commit: at-least-once attempts, one committed terminal result, no exactly-once inference claim. Only side-effect-free fakes are allowed. Disk failure/corruption and power-loss guarantees are excluded; storage errors must surface without falsely reporting completion.

## Acceptance evidence

Run `python -m unittest discover -s tests -v` offline, with temporary databases and no credentials. Cover durable submission, foreground priority, FIFO ties, future work exclusion using an injected clock, both backend substitutions, policy block with zero calls, backend failure, hostile output remaining inert, and read-only inspection leaving journal/state unchanged.

Use subprocess handshakes (not timing sleeps) to kill a worker after its running commit, and after fake output generation but before completion commit. Restart with the same database: retain old evidence, expose recovery, run a new attempt only on the next worker invocation, publish one terminal result. Also kill before submission commit (no acknowledged work) and verify committed queued and completed work across fresh processes. Try a second live worker: it must fail busy without changing the first worker's state. Assert database/event consistency, not just CLI exit codes.

Document actual CLI examples for submit, worker and inspect, plus the test command, in README. Do not build a benchmark platform.

## Traceability and exclusions

Dossier references: Functional Requirements FR-002/FR-004, bounded portions of FR-001/FR-003/FR-005/FR-007/FR-008; SEC-001; Decision Register D-003/D-006/D-007/D-008/D-010; figure `fig:madre-model-invocation-action`; Benchmark Catalogue rows Delayed work durability, Runtime journal traceability and Inference substitution. Recovery here proves interruption recording and resumption, not all FR-008 correction/rollback operations.

Exclude RAG, vector stores, embeddings, semantic long-term memory, learning, fine-tuning, multi-agent collaboration, GUI, speech, web search, real providers, marketplaces, MCP, distributed execution, sophisticated policy DSLs, workflow engines and broad benchmarking. There is no unresolved product decision required to implement this contract; implementation failures are evidence to resolve within this boundary.
