# Implementation baseline

`MADRE.md` defines product meaning and application/domain ownership. This baseline
connects the executable foundation to the next substantive implementation.

## Chosen architecture

The Owner selected a **shared local runtime**, independent of application lifetimes,
with **local HTTP as the initial language-neutral application boundary**. Python
3.13 is the current implementation language; C or C++ may take over responsibilities
where the working system benefits. The inference engine already runs in a separate
C++ process. The Owner authorized a small local model for development validation.

The importable package uses FastAPI/Pydantic for service and data validation, HTTPX
for capability transport, and SQLite for runtime persistence. Applications own the
meaning and consequence of their inputs and outputs; MADRE owns work execution,
durability, recovery and inspection. Capabilities provide bounded computation.

Delayed execution does not introduce a second job abstraction or storage model.
`accepted` work in the existing schema is the durable queue state. One service-owned
scheduler task queries the next persisted eligibility time and sleeps until that time,
while an in-process event wakes it when a new submission may change the schedule.
Restart recovery comes from the persisted `accepted` records rather than from the
in-memory wake mechanism.

The accepted-to-running transition is conditional and durable before capability
invocation begins. This allows accepted work with no started attempt to remain safely
recoverable after restart while preserving the existing conservative rule for a
previously running attempt whose capability outcome may be unknown.

## What executes today

Explicit TOML configuration, package/CLI entry points, authenticated loopback
service access, and exclusive runtime-data ownership are implemented. Service
lifespan owns a local SQLite database; the OS releases its ownership lock after
process exit. Startup rejects unknown schema versions and migrates the original
schema-1 envelope to schema 2, which owns durable runtime work and attempt records.

`POST /v1/work` accepts both immediate and future-eligible `WorkSubmission` values.
MADRE durably records the application-selected input and execution constraints before
execution. Already-eligible work follows the configured chat-completion capability
path immediately. Future-eligible work returns as `accepted` without waiting for
execution and remains in that durable state across service restart.

The service scheduler discovers accepted work from SQLite and executes it only once
its `eligible_at` is reached. Immediate and delayed work converge on the same runtime
execution method: capability lookup, capability-specific input validation, durable
attempt start, `invoke_chat`, and durable success or classified failure. There is no
scheduler-specific execution path and no separate delayed-work record type.

`GET /v1/work/{id}` reads the same durable record before and after execution. Unknown
capabilities and capability-specific input errors are durable failures. A restart
preserves accepted work that has not started an attempt. A restart converts an
unfinished running attempt to an `interrupted` failure whose evidence states that
the capability outcome may be unknown; it does not infer success or retry the
invocation.

The configured chat-completion adapter invokes real local inference and returns
generated text, model, finish reason and timing, or a classified failure. It enforces
a total invocation timeout and local-only transfer constraints. Local declarations
require literal loopback destinations; HTTP clients disable proxies and redirects.
The executable behavior and validation rules live in code/tests; README owns setup,
HTTP examples and the real end-to-end acceptance command.

`invoke_chat` remains the initial chat-completion capability adapter, not MADRE's
general work API. The verified llama.cpp path needs no provider credential. The
service-access token authenticates local applications only; the inference adapter
sends no authorization header. Future authenticated capabilities should follow the
credential-ownership preference in `MADRE.md`, using a provider-supported client or
host integration where appropriate.

## Runtime-work direction and next behavior

Immediate and delayed execution now share `WorkSubmission`, durable work state,
attempt evidence and capability execution. The application owns domain meaning;
MADRE persists only the selected execution material and lifecycle evidence needed
to execute and inspect runtime work.

**Exercise a real independent application next:** integrate an application that
selects its own domain context, submits immediate or delayed work through the local
HTTP boundary, and consumes the eventual result while retaining domain state and
interpretation. That is the next acceptance step in `MADRE.md` and will provide
concrete evidence for whatever runtime controls should follow.

Retry, cancellation, richer concurrency/resource policy and additional capabilities
remain intentionally outside the current slice until an application or experiment
demonstrates a requirement for them.

The dependency/value order is now:

1. Real immediate runtime work execution — implemented and accepted.
2. Delayed eligibility and restart recovery — implemented with deterministic runtime evidence.
3. Real independent-application integration beyond the acceptance client — next.
4. Capabilities, execution controls and further behavior grown from actual use.

## Verified development evidence — 2026-09-06

On Windows, Python 3.13.3 / SQLite 3.49.1 ran on a host with 23.8 GiB RAM and a
GTX 1050 Ti / 4 GiB VRAM. The optional CPU fixture uses llama.cpp `b10809` and
Qwen2.5-0.5B-Instruct Q4_K_M outside the checkout. Exact revisions, URLs and verified
SHA-256 hashes are preserved in `tools/install-smoke-model.ps1`.

The original capability probe returned real inference in 0.932 seconds. The small
model also failed an exact-output instruction: the probe establishes connectivity
and computation rather than application-quality reasoning. Clean locked bootstrap,
external wheel import, live authenticated HTTP, exclusive SQLite ownership/reopening,
deterministic tests and static checks passed. Bootstrap CI passed on Windows and
Linux.

The immediate-work HTTP slice was subsequently accepted on Windows at commit
`f53c3e7f3828bae1a104210d7d5b1c520fe70489`. Python 3.13.3 passed all 27 tests,
`ruff check`, `ruff format --check`, and strict `mypy`. The pinned llama.cpp CPU
fixture loaded the real Qwen model and the direct `local-chat` probe generated text.
The standalone client then submitted work through authenticated local HTTP; MADRE
persisted a successful attempt and real generated result, and `GET /v1/work/{id}`
reproduced the durable record. After MADRE restart, the successful work remained
inspectable with the same result. With llama.cpp stopped, a new submission was
durably recorded as failed with capability failure code `connection` and the message
`could not communicate with capability endpoint`. Together with green Ubuntu PR CI,
this establishes real immediate execution and accurate durable failure on the
supported local path.

The delayed-work slice was validated on Ubuntu at code head
`f12e8e13398bef0d30de9f96519f8d637cef6418`. All 27 tests passed, including a
controlled-clock restart test that closes and reopens the runtime database, runs the
actual recovered scheduler loop, proves no capability invocation before eligibility,
and records invocation and durable success when the clock reaches `eligible_at`.
The HTTP test also proves future work returns a durable `accepted` record without
invocation. `ruff check`, `ruff format --check`, strict `mypy`, wheel build/install,
and isolated wheel import all passed.

No additional real-model run or Windows matrix was executed for the delayed slice.
The schema and capability adapter are unchanged; the preceding immediate-work slice
provides Windows SQLite/restart and real-model capability evidence, while the new
scheduler/recovery behavior is covered by the deterministic current Linux CI suite.
