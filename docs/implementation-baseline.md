# Implementation baseline

`MADRE.md` defines product meaning and ownership boundaries. This file records only the current executable foundation and the constraints that matter for the next implementation step. Historical development rationale belongs in Git history unless it remains operationally relevant.

## Current architecture

MADRE is a shared local runtime, independent of application lifetimes, exposed initially through authenticated loopback HTTP. Python 3.13 is the current runtime implementation language; the application boundary is language-neutral. SQLite owns durable runtime state. The implemented inference capability is currently chat-completions through a replaceable provider boundary.

Ownership remains:

- applications own domain meaning, state, workflows and interpretation;
- CORE owns MADRE's default generic/system intelligence;
- MADRE Runtime owns durable work execution, scheduling, recovery, admission and inspection;
- capabilities perform bounded provider/tool computation.

The public work contract is already broader than chat because `WorkSubmission` carries a generic `capability_id` and JSON `input`. The implementation below that boundary is intentionally narrower today: configured capabilities support `chat_completions`, validated as `ChatInput`, and execute through `invoke_chat`. Do not generalize the capability architecture until a concrete non-chat behavior requires it.

## Runtime work lifecycle

`POST /v1/work` durably accepts valid work before physical capability execution. Immediate and future-eligible submissions use the same `WorkSubmission`, durable record and scheduler path. The request does not own capability invocation and does not wait for scarce-resource admission.

Accepted work is the durable queue state. The service-owned scheduler discovers eligible records from SQLite. Once admitted, the accepted-to-running transition and attempt creation are persisted immediately before capability invocation. Completion or classified failure is then persisted on both the attempt and work record.

`GET /v1/work/{id}` is the generic inspection/completion boundary. Applications may wait, poll, continue interacting, submit other work or inspect later. Those choices are application semantics, not runtime foreground/background states.

A normal runtime restart using the **current schema** preserves accepted work that has not started and rediscovers it. A work item whose attempt was running when the runtime stopped is conservatively converted on startup to durable `interrupted` failure evidence because the external capability outcome may be unknown. MADRE does not infer success or retry that invocation.

Unexpected ordinary Python exceptions raised by capability execution after attempt start are contained as durable `internal_error` failures with a generic non-sensitive message. `BaseException` and `asyncio.CancelledError` are not converted to `internal_error`, preserving shutdown/interruption semantics.

## Scarce local inference admission

Across one shared runtime, at most one currently implemented heavyweight local chat-completion invocation executes at a time. Work is persisted before admission, and a waiting work item remains `accepted` with no started attempt. Admission is tied specifically to the current local chat-completion capability path; it is not a generalized resource scheduler and must not automatically wrap future non-heavy capabilities.

The current scheduler is serial, which is valid for the single implemented heavyweight local-chat path. Do not build GPU accounting, a resource registry, generalized semaphores, priority machinery or a capability hierarchy until concrete execution behavior requires them.

## Durable submission idempotency

Applications may optionally send `Idempotency-Key` on `POST /v1/work`. The key is acceptance metadata, not part of `WorkSubmission`, and is scoped by `application_id`.

For the same application/key:

- the same normalized submission returns the original durable work record in its current state;
- different normalized work is rejected with HTTP 409;
- replay does not create another work row, attempt or scheduler wakeup;
- failed or interrupted work is replayed as-is rather than retried.

Omitting the header preserves ordinary distinct-submission behavior. The runtime uses a SQLite uniqueness constraint to make concurrent same-key acceptance safe. This feature prevents accidental duplicate acceptance after transport uncertainty; it is not execution retry policy and does not claim exactly-once external side effects.

## Development database policy

MADRE is still in active pre-release development. Runtime durability within the current schema is product behavior; compatibility between obsolete development schemas is not.

The current database schema version is 3. A fresh database (`PRAGMA user_version = 0`) is initialized directly as the current schema. A database carrying any other non-current schema version is rejected with an instruction to delete/recreate the development runtime data directory.

Do **not** implement migrations, backward-compatible upgrade paths or preservation machinery for development databases unless the Owner explicitly requests that policy. When the schema changes during development, old local data is disposable. Tests should prove current-schema behavior and stale-schema rejection, not migration of obsolete fixtures.

This distinction is important: deleting an obsolete development database is acceptable; losing accepted work during an ordinary restart of the same current runtime schema is not.

## CORE boundary

CORE is a separate first-party application package and entry point. It uses the same authenticated HTTP runtime boundary and stable `application_id = "madre-core"`; runtime production code has no CORE-specific scheduling or capability branch.

The current terminal conversation, fast/deeper marker protocol and `/deeper` interaction are development experiments, not MADRE UX contracts. CORE currently chooses to wait/poll for its submitted work. MADRE itself permits applications to respond immediately, wait, remain silent while reasoning is scheduled, or inspect later.

Do not infer Agent, Planner, workflow or memory architecture from the current CORE terminal. In MADRE terminology, an agent is not merely a skill/tool integration. Introduce agentic abstractions only when a concrete inference-chain behavior requires them.

## Current canonical milestones

The executable foundation on `main` includes:

1. durable immediate runtime work execution;
2. delayed eligibility and restart recovery;
3. global admission for the current heavyweight local inference path;
4. the canonical application / CORE / Runtime / capability ownership split;
5. CORE through the ordinary runtime HTTP boundary;
6. experimental fast/deeper CORE follow-up behavior;
7. runtime-owned execution for all accepted work;
8. durable application-scoped submission idempotency.

Runtime-owned submission/execution convergence became canonical at `459cce2f982f39a8e4d5dc926417f6c2e94ed9bc`. Durable submission idempotency became canonical at `6c3d82c12a90675cb271122671bb21bf771413e2`.

## Verified evidence

The repository's deterministic suite covers configuration, storage ownership, current-schema lifecycle, runtime scheduling/recovery, admission, inference failures, CORE behavior and idempotency. The idempotency milestone was validated with 48 deterministic tests plus Ruff lint/format, strict mypy, package build, wheel reinstall and isolated import.

Real Windows acceptance has exercised the authenticated HTTP path against the pinned llama.cpp `b10809` / Qwen2.5-0.5B-Instruct Q4_K_M fixture. For idempotency, one keyed submission executed one real `madre-smoke` attempt, produced non-empty terminal output, and replay of the identical key/body returned that same terminal record without a second invocation.

The old development-schema migration evidence is intentionally discarded as a requirement: development schema compatibility is not a product acceptance criterion.

## Next implementation direction

Continue reliable shared execution from concrete runtime/application evidence. Substantive remaining gaps include explicit cancellation semantics, explicit retry policy, priority/fairness when a real contention use case demands it, and broader permitted capability/execution-path selection.

Do not spend the next slice on chatbot polish, model-floor hunting, speculative Agent/Planner/workflow abstractions, generalized resource scheduling or backward-compatible development database migrations.
