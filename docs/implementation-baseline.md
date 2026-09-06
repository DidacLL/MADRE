# Implementation baseline

`MADRE.md` defines product meaning and application/domain ownership. This baseline
connects the executable foundation to the next substantive implementation.

## Chosen architecture

The Owner selected a **shared local runtime**, independent of application lifetimes,
with **local HTTP as the initial language-neutral application boundary**. Python
3.13 is the current implementation language; C or C++ may take over responsibilities
where the working system benefits. The inference engine already runs in a separate
C++ process. The Owner authorized a small local model for development validation.

The importable runtime package uses FastAPI/Pydantic for service and data validation,
HTTPX for capability transport, and SQLite for runtime persistence. Applications own
the meaning and consequence of their inputs and outputs; MADRE owns work execution,
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
domain material may leave; CORE owns MADRE's default generic/system intelligence;
MADRE Runtime owns durable execution, scheduling, recovery, admission and ultimately
the selected permitted execution path; capabilities perform bounded computation and
provider-specific mechanics. Applications may also invoke the runtime directly without
CORE. CORE uses the same runtime execution plane.

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

The source tree contains CORE as the first real first-party application in a separate
`madre_core` package with its own `madre-core` entry point. CORE connects to a
separately running MADRE service through authenticated loopback HTTP and submits
ordinary chat work with stable `application_id = "madre-core"`. Its production code
does not import `WorkRuntime`, `invoke_chat`, storage, scheduler or service internals.
The runtime has no CORE-specific branch.

The first CORE surface is an interactive terminal conversation. Successful assistant
text is kept with user messages in process memory and sent back as ordinary chat input
on the next turn. Restarting CORE forgets that history. CORE introduces no durable
session store, memory system, agent abstraction, planner or autonomous/background
reasoning behavior.

CORE accepts the runtime's ordinary `accepted`, `running`, `succeeded` and `failed`
work states. When a submission response exposes pending work, CORE inspects that work
until it becomes terminal. Durable runtime/capability failures are surfaced directly
to the user. Current immediate `POST /v1/work` behavior still returns only after the
immediate execution path finishes, so an immediate work ID is not available to CORE
while that original POST is itself waiting for scarce-resource admission; this is a
property of the generic HTTP contract, not a CORE special case.

The first CORE application boundary is canonical on `main` as of
`fc3a5c4f670013fe234b5ef33281df1b7f965087`.

CORE separates HTTP transport (`CoreClient`) from interaction behavior
(`CoreConversation`). Each foreground turn remains exactly one ordinary runtime work
item. The interaction layer adds a transient system instruction asking the configured
chat capability for both a concise immediate answer and one bounded recommendation:
`fast` when the foreground answer is sufficient or `deeper` when materially stronger
handling would require deeper multi-step reasoning, verification, research, planning
or tools.

CORE strips the internal recommendation marker before displaying or remembering the
assistant response. A missing or malformed marker conservatively yields `deeper` while
preserving useful generated text. The recommendation is observable in the CLI and
returned as part of `CoreTurn`. "Fast" names the foreground interaction responsibility
rather than promising wall-clock latency; ordinary runtime admission can still delay
the submitted work item.

The explicit fast/deeper recommendation behavior is canonical on `main` as of
`b71e244e39fe81e3a2db02b73e5a219fb64702b1`.

The current source gives `deeper` one execution consequence. Only when the latest fast
turn recommended deeper reasoning, the user may enter `/deeper`. CORE then submits one
second ordinary MADRE work item through the same HTTP client, capability and stable
application identity. It supplies the process-local conversation, the fast answer as a
draft, a transient deeper-analysis instruction, a transient final request to produce
the replacement answer, and a larger token budget (768 by default versus 256 for the
fast interaction). This represents stronger reasoning intent using the capability that
exists today; it does not define a new work type or capability class.

A successful deeper result replaces the fast draft in CORE's process-local history and
consumes the opportunity. Sending another ordinary user message abandons the previous
opportunity. If deeper work fails, the fast draft and explicit retry opportunity remain.
CORE never escalates automatically. No runtime, scheduler, storage, admission or
capability code changes are required, and `/deeper` does not deploy an agent, invoke a
Planner, create a workflow or select a different capability.

## Runtime-work direction and next behavior

Immediate and delayed execution share `WorkSubmission`, durable work state, attempt
evidence and capability execution. Delayed eligibility and restart recovery are
canonical on `main` as of `75d88417b34a3959a61ba89452a671fc7d4b39d6`.

The current admission slice adds one runtime invariant: across one shared MADRE runtime,
at most one heavyweight local LLM capability invocation may execute at once. The
service-lifetime `WorkRuntime` owns the admission primitive, so immediate HTTP work,
delayed scheduler work and CORE-originated work using the same runtime plane share the
same limit without application-specific handling.

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

This admission behavior is canonical on `main` as of
`60ce6eac049fc40fe2db400793d2a00a3a07d745`.

The canonical product-definition realignment is on `main` as of
`7bd9c979f16597ebb4f49b9d13c58ea7f3a9e404`. It establishes the ownership invariant
that applications own domains, CORE owns default generic/system intelligence, MADRE
Runtime owns execution, and capabilities perform computation.

The dependency/value order is now:

1. Real immediate runtime work execution — implemented, accepted and canonical.
2. Delayed eligibility and restart recovery — implemented, accepted and canonical.
3. Minimal global local-inference admission — implemented, accepted and canonical.
4. Canonical product-definition realignment — implemented, accepted and canonical.
5. Minimal CORE interaction through the ordinary runtime HTTP boundary — implemented, accepted and canonical.
6. Explicit CORE fast-response responsibility plus observable `fast`/`deeper` recommendation — implemented, accepted and canonical.
7. User-controlled `/deeper` stronger follow-up through ordinary MADRE work — implemented in the current source tree.
8. Choose the next CORE/runtime behavior from evidence produced by this working two-stage path rather than prebuilding a generalized agent, Planner, workflow engine, memory system or capability router.

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
