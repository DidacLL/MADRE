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

### Owner product-direction clarification

The current executable path proves local chat-completion work first; it does not define
MADRE as a local-LLM runtime or consumer-GPU scheduler. MADRE's broader product purpose
is to provide one coherent, privacy-aware and user-controlled execution environment
over the intelligence capabilities that are both available **and permitted** for a
task. Local deterministic computation and local AI are first-class high-trust,
offline-capable execution tiers rather than the whole product boundary.

Future permitted capabilities may include local models and tools, remote providers,
application-provided AI, user-authorized account/subscription access, specialized
services and other execution providers. Availability never implies authorization:
application disclosure, user authorization, task need and execution-boundary policy
must still permit a capability before MADRE may use it.

The refined ownership direction remains: applications own domain authority and what
domain material may leave; future CORE owns MADRE's default generic/system intelligence;
MADRE Runtime owns durable execution, scheduling, recovery, admission and ultimately
the selected permitted execution path; capabilities perform bounded computation and
provider-specific mechanics. Applications may also invoke the runtime directly without
CORE. CORE, when implemented, must use the same runtime execution plane.

The current public work contract is already broader than a model request because it
carries a generic `capability_id` and JSON `input`. The current implementation below
that contract is intentionally narrower: `CapabilityConfig.kind` supports only
`chat_completions`, and `WorkRuntime` validates `ChatInput` and calls `invoke_chat`.
That is an implementation limitation of today's proven capability, not a product
boundary. Relax it only when a concrete non-chat capability requires the change.

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

Immediate and delayed execution share `WorkSubmission`, durable work state, attempt
evidence and capability execution. Delayed eligibility and restart recovery are
canonical on `main` as of `75d88417b34a3959a61ba89452a671fc7d4b39d6`.

The current admission slice adds one runtime invariant: across one shared MADRE runtime,
at most one heavyweight local LLM capability invocation may execute at once. The
service-lifetime `WorkRuntime` owns the admission primitive, so immediate HTTP work,
delayed scheduler work and future CORE-originated work using the same runtime plane
share the same limit without application-specific handling.

Today's configuration can identify this scarce path without a new capability
architecture: the only implemented capability kind is `chat_completions`, and local
inference is distinguished by `CapabilityConfig.boundary == "local"`. Admission is
therefore applied only to that current local-chat path. Remote chat is not part of
this scarce local resource rule, and future non-chat capabilities must not inherit it
merely because they are runtime work.

Work is persisted before admission. A local-chat work item waiting for the scarce slot
remains durably `accepted` with no started attempt. Once admitted, the existing
conditional accepted-to-running transition occurs immediately before the same
`invoke_chat` path used by immediate and delayed work. The admission slot is released
when physical invocation returns or raises, before durable result/failure finalization;
structured capability failure therefore cannot permanently occupy the slot, and
exception/cancellation unwinding also releases it through the async context manager.

This is the first concrete scarce-resource rule, not a generalized scheduler. No
resource registry, semaphore framework, GPU accounting, model-residency plan, priority
system, retry/cancellation product feature, capability class hierarchy or execution
router is introduced. Because there is no implemented non-heavy capability yet,
concurrent cheap-work execution cannot be exercised honestly in this slice; the
boundary is instead conditional on the existing capability configuration so it does
not inherently wrap all future work.

This admission behavior is implemented on the current PR branch but is not canonical
product behavior until the Owner merges it into `main`.

The dependency/value order is now:

1. Real immediate runtime work execution — implemented and accepted.
2. Delayed eligibility and restart recovery — implemented, accepted and canonical.
3. Minimal global local-inference admission — implemented on the current PR; Owner merge pending.
4. Small canonical `MADRE.md` product-direction realignment — next after Owner merge.
5. CORE development — after that product-direction realignment.
6. Further application integration, capabilities and execution controls grow from actual use.

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
