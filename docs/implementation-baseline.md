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
scheduler task queries persisted eligibility and sleeps until work may run, while an
in-process event wakes it when a new submission may change the schedule. Restart
recovery comes from persisted `accepted` records rather than from the in-memory wake
mechanism.

The accepted-to-running transition is conditional and durable before capability
invocation begins. This allows accepted work with no started attempt to remain safely
recoverable after restart while preserving the existing conservative rule for a
previously running attempt whose capability outcome may be unknown.

## What executes today

Explicit TOML configuration, package/CLI entry points, authenticated loopback service
access, and exclusive runtime-data ownership are implemented. Service lifespan owns a
local SQLite database; the OS releases its ownership lock after process exit. During
active development, the executable identifies its current persisted structure by a
private fingerprint of the active DDL. Incompatible generated development storage is
discarded and recreated in the current format rather than migrated or preserved for
compatibility that the project does not yet owe.

`POST /v1/work` accepts both immediate and future-eligible `WorkSubmission` values.
For every valid submission MADRE allocates an ID, durably records the application-
selected input and execution constraints as `accepted`, notifies runtime scheduling,
and returns that accepted record. The request does not invoke the capability and does
not wait for scarce-resource admission. Immediate eligibility means the work may be
executed by the runtime now; future eligibility means the same accepted work remains
pending until its configured time.

An application may optionally supply `Idempotency-Key` when one logical submission may
need to be retried after transport uncertainty. The key is runtime acceptance metadata,
not part of `WorkSubmission`, and is scoped together with `application_id`. Reusing the
same application/key with the same normalized submission returns the original durable
work record in whatever state it currently has. Reusing it with different normalized
work is rejected with HTTP 409. Omitting the key preserves ordinary distinct
submissions. The durable uniqueness constraint and persisted key survive service
restart; replay never creates another work record, attempt or scheduler wakeup.

Failed work can be explicitly retried through the ordinary runtime API. A retry is a
new durable authorization for another physical attempt, with its own idempotency key
and preserved evidence of the failure it supersedes. Interrupted work whose previous
capability outcome may be unknown requires explicit caller consent before another
invocation is allowed. MADRE does not automatically retry work.

Cancellation is also durable and truthful. Accepted work can be cancelled before its
pending execution starts. If an invocation is already running, MADRE records the
cancellation request but does not release scarce-resource admission or claim the
external capability stopped until physical execution actually returns. Cancellation is
monotonic for one logical work identity.

The service-owned scheduler discovers accepted work from SQLite and executes both
already-eligible and future-eligible work through the same runtime execution method:
capability lookup, capability-specific input validation, scarce-resource admission
when applicable, conditional durable attempt start, `invoke_chat`, and durable success
or classified failure. There is no request-owned immediate execution path, no separate
delayed-work record type, and no second queue/state model.

Eligible applications rotate in durable round-robin order so one application's backlog
cannot monopolize the shared runtime. `WorkSubmission.priority` is bounded from `-100`
to `100` and orders work only inside that application's turn. Equal-priority work uses
a durable monotonic queue sequence for exact FIFO behavior, and an explicit retry gets
a fresh queue position. The fairness cursor and queue ordering survive restart.

`GET /v1/work/{id}` reads the same durable record before, during and after execution
and is the generic completion boundary for applications that need eventual results.
Applications may submit and poll immediately, continue foreground interaction, submit
additional work, or inspect later. Unknown capabilities and capability-specific input
errors become durable failures when runtime scheduling processes the accepted work.
A restart preserves accepted work that has not started an attempt and rediscovers it.
A restart converts an unfinished running attempt to an `interrupted` failure whose
evidence states that the capability outcome may be unknown; it does not infer success
or automatically retry the invocation.

The configured chat-completion adapter invokes real local inference and returns
generated text, model, finish reason and timing, or a classified failure. It enforces
a total invocation timeout and local-only transfer constraints. Local declarations
require literal loopback destinations; HTTP clients disable proxies and redirects.
The executable behavior and validation rules live in code/tests; README owns setup,
HTTP examples and the real end-to-end acceptance command.

Structured `CapabilityError` classifications are preserved. If a scheduler-owned
capability invocation unexpectedly raises an ordinary Python `Exception` after its
attempt has started, MADRE durably fails that attempt and work as `internal_error`
with a generic non-sensitive message, releases admission and continues scheduling.
Raw exception text is not placed in the durable public work record. `BaseException`
and `asyncio.CancelledError` are not converted to `internal_error`; runtime shutdown
therefore preserves the existing unknown-outcome interruption semantics.

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

The current CORE surface is an experimental interactive terminal conversation used to
exercise the first-party path. Successful assistant text is kept with user messages in
process memory and sent back as ordinary chat input on the next turn. Restarting CORE
forgets that history. The terminal presentation, commands, fallback text and fast/deeper
staging are not a MADRE UX contract and must not be used to infer future Agent or
application architecture. CORE introduces no durable session store, memory system,
generic agent abstraction or Planner.

CORE accepts the runtime's ordinary `accepted`, `running`, `succeeded`, `failed` and
`cancelled` work states. Foreground completion submits ordinary work and inspects it
until terminal. Durable runtime/capability failures are surfaced directly to the user.
CORE therefore remains an ordinary application choosing when to wait and when not to;
the MADRE submission request itself never owns capability execution or scarce-resource
waiting. CORE currently omits submission `Idempotency-Key`, so its submissions retain
distinct-work behavior; no CORE special case exists in the runtime.

The first CORE application boundary is canonical on `main` as of
`fc3a5c4f670013fe234b5ef33281df1b7f965087`.

CORE separates HTTP transport (`CoreClient`) from interaction behavior
(`CoreConversation`). Each foreground turn remains exactly one ordinary runtime work
item. The interaction layer asks the configured chat capability for both a concise
immediate answer and one bounded recommendation. `fast` is the default. `deeper`
means one second pass by that same chat capability, with the same conversation and a
larger response budget but no new tools or external information, is likely to
materially improve correctness or completeness. The recommendation therefore describes
the concrete follow-up CORE can perform today rather than hypothetical research, tool
use or a future agent system.

CORE strips the internal recommendation marker before displaying or remembering the
assistant response. A missing or malformed marker conservatively yields `deeper` while
preserving useful generated text. A marker-only response is also treated as `deeper`;
the current CLI supplies an explicit diagnostic fallback rather than turning model
protocol failure into a runtime error. That fallback is instrumentation, not intended
conversational semantics. The recommendation remains observable in the CLI and
returned as part of `CoreTurn`. "Fast" names the foreground interaction responsibility
rather than promising wall-clock latency; runtime admission can still delay physical
capability execution and the eventual result that CORE has chosen to await.

The first fast/deeper recommendation behavior became canonical on `main` as of
`b71e244e39fe81e3a2db02b73e5a219fb64702b1`; its recommendation semantics were
subsequently narrowed from real-model evidence rather than by adding a new architectural
layer.

The current CORE change gives the existing explicit `deeper` recommendation a real DRE
execution shape. Only when the latest fast turn recommended deeper reasoning may the
user enter `/deeper`. CORE submits one second ordinary MADRE work item through the same
HTTP boundary, capability and stable application identity, then returns control instead
of waiting for capability completion. The work carries the current process-local
conversation, the fast answer as a draft, a transient deeper-analysis instruction and
a larger token budget. Fast CORE work uses higher priority than scheduled deeper work,
but both priorities operate only within the existing `madre-core` application turn and
do not grant CORE cross-application scheduler privilege.

CORE tracks at most one scheduled deeper item in process memory. Before handling the
next user action it inspects that ordinary runtime work. If the result completed before
the conversation advanced, CORE can safely replace the fast draft in local history. If
the user continued first, the later result is surfaced as late evidence but does not
retroactively rewrite conversation state that subsequent responses already consumed.
If CORE exits, MADRE still owns and executes the durable work, but the experimental CORE
process forgets the local association on restart. No CORE persistence architecture is
introduced.

CORE never escalates automatically. `/deeper` does not deploy an agent, invoke a
Planner, create a workflow or select another capability. This slice demonstrates the
product distinction the runtime was built to support: CORE may answer now while stronger
reasoning is runtime work, rather than treating every reasoning decision as one blocking
chat call.

The deterministic suite proves the software semantics of the CORE path. The owner-side
model runs have supplied enough product evidence for the earlier synchronous experiment:
the 0.5B smoke fixture proved the path but was not a credible interaction model; the
1.5B acceptance fixture produced usable simple responses but still recommended
`deeper` for trivial inputs. The current classifier and terminal presentation remain
experiments. Further prompt/classifier/chat UX tuning stays deferred until richer CORE
behavior gives those choices a concrete product context.

## Runtime foundation milestone and product direction

The runtime foundation is now considered sufficient for product-layer development. Its
canonical behavior includes:

- durable immediate and delayed work;
- restart recovery and truthful interruption evidence;
- global heavyweight-local-inference admission;
- submission idempotency;
- explicit retry;
- truthful cancellation;
- durable application-fair scheduling;
- bounded intra-application priority and exact FIFO queue ordering.

The fairness/priority work is accepted as technically coherent and remains canonical.
Its sequencing nevertheless went deeper into generic runtime mechanics than the Owner's
intended product path. That implementation should not be rolled back merely because it
arrived early, but it must not become a reason to continue turning MADRE into a job
scheduler project.

### Runtime feature freeze

Do not add another generic runtime scheduling/resource-control feature merely because a
reasonable scheduler could have it. In particular, do not spend product-development
scope on scheduler weights, aging, starvation heuristics, dynamic quotas, more priority
classes, resource vectors, admission groups, preemption, retry policy or queue
introspection sophistication unless a concrete CORE/application behavior demonstrates
the need.

This is not a bug freeze. A real runtime defect exposed by CORE or another application
should be fixed at the narrowest responsible boundary. A new generic runtime behavior
must now earn its place from observed application/product pressure rather than from
scheduler completeness.

The development dependency/value order is therefore corrected to:

1. durable execution and delayed eligibility — implemented and canonical;
2. global scarce-resource admission — implemented and canonical;
3. canonical product ownership alignment — implemented and canonical;
4. CORE as the first first-party application through ordinary runtime HTTP — implemented and canonical;
5. explicit fast/default CORE interaction plus observable deeper recommendation — implemented as an experiment;
6. explicit stronger follow-up through ordinary runtime work — implemented;
7. asynchronous DRE in CORE, where stronger reasoning can be scheduled without blocking interaction — current product slice;
8. richer CORE reasoning/planning behavior from concrete product evidence;
9. multi-model evidence/selection when a working CORE behavior demonstrates why one capability is insufficient;
10. independent/domain application bindings after the generic CORE/runtime path proves the required boundary.

For the next slice, begin from CORE or another real application behavior. Do not begin
from the scheduler. If a product behavior exposes a runtime blocker, fix only the
minimum blocker required by that behavior and report the evidence that forced it.

## Verified development evidence — 2026-09-06

On Windows, Python 3.13.3 / SQLite 3.49.1 ran on a host with 23.8 GiB RAM and a
GTX 1050 Ti / 4 GiB VRAM. The optional CPU smoke fixture uses llama.cpp `b10809` and
Qwen2.5-0.5B-Instruct Q4_K_M outside the checkout. A separate pinned
Qwen2.5-1.5B-Instruct Q4_K_M fixture is available for CORE product experiments. Exact
revisions, URLs and verified SHA-256 hashes are preserved in the installer scripts
under `tools/`.

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

The first owner-side CORE product run then exercised the pinned Qwen 0.5B smoke model
through the actual `madre-core → authenticated HTTP → durable runtime → local-chat`
path. The model loaded and listened on `127.0.0.1:8080`. A trivial input produced only
the internal reasoning marker, which the previous parser surfaced as `CORE fast
interaction returned no user-facing response`. A benign poem request was first refused
and marked `deeper`; a repeated poem request produced a poem but was again marked
`deeper`. This was not runtime failure evidence: it demonstrated that the small model
could execute through the real CORE path while exposing marker-compliance and
recommendation-quality weaknesses. The parser/recommendation correction made
marker-only output an inspectable diagnostic fallback and aligned `deeper` with the
actual second same-capability pass rather than hypothetical tools or research.

A second owner-side run used the dedicated Qwen2.5-1.5B-Instruct acceptance fixture.
The trivial input `patata` produced the direct answer `Patata is Italian for potato.`;
a poem request produced a non-empty poem instead of the earlier refusal. Both requests
were still classified `deeper`. This is sufficient evidence for the present stage:
basic generated-answer quality improved with the stronger fixture, while the
fast/deeper judgement remains too weak to treat as settled product behavior. The Owner
therefore closed this interaction experiment for now and clarified that the chatbot-like
terminal flow and its fallback wording do not define MADRE UX. Development returns to
the reliable framework; later CORE interaction work should build on richer system
behavior rather than freezing today's diagnostic surface.

The durable-idempotency slice passed 48 deterministic tests on Ubuntu together with
`ruff check`, `ruff format --check`, strict `mypy`, package build, wheel reinstall and
isolated wheel import. The suite proves replay while running and after completion,
application scoping, conflict rejection, normalized-submission comparison, omission
compatibility, restart persistence and the then-current migration behavior. A real
Windows HTTP acceptance then used the pinned llama.cpp `b10809` / Qwen2.5-0.5B Q4_K_M
fixture. The initial keyed POST returned accepted work, runtime-owned execution produced
non-empty `madre-smoke` text in one successful attempt, inspection reached durable
success, and replaying the identical POST with the same key returned that same durable
terminal record. Later development deliberately removed migration lineage from active
storage because there is no installed data compatibility obligation; generated
development databases now follow the current format. The temporary CI job used only to
obtain real execution evidence was removed from the final repository diff.

## First AgenticLoop implementation evidence — 2026-09-07

Draft PR #39 on `agentic/vertical-kernel-loop` implements the first bounded executable
vertical slice of the clean-slate agentic architecture. It is based on
`architecture/modular-agentic-clean-slate` and is not canonical until Owner review and
integration.

The new `madre_kernel` package materializes validated references and definitions for
Modules, Scopes, Schemas, Operations, Skills, Workflows, Agents, Agent instances,
ContextBundles, WorkPlans, AgentTasks, security decisions and operation-invocation
evidence. WorkPlan and AgentTask do not acquire a second Runtime-style `status` or a
Runtime `work_id`; semantic completion/termination facts remain distinct from physical
Runtime lifecycle.

Kernel persistence is separate from `runtime.sqlite3` and uses responsibility-specific
SQLite tables for definitions, plans/tasks, contexts, Agent instances, operation
invocations, security decisions and Runtime evidence links. Module-owned Agent state is
represented only by opaque Module-scoped references. Runtime remains unaware of all
WorkPlan/Agent semantics and receives only its existing authenticated ordinary work
requests.

The Kernel is the Operation gateway. It evaluates the deterministic security algebra
before dispatch, persists the structured decision, creates durable invocation evidence
before any permitted physical Operation call, and records rejection without dispatch.
After dispatch it records success, determinate failure or unknown external effect.
Unknown-effect evidence is not interpreted as safe to repeat. A lower-sensitivity
derivation is accepted only when the Operation explicitly declares the classification
recalculation transform, and produced Scopes must remain within the Operation's declared
destination set.

The concrete acceptance path is intentionally narrow: a calculator Module declares no
Agent, so unresolved work falls back through the replaceable CORE role. A CORE-managed
AgentInstance performs two ordinary Runtime reasoning calls around one deterministic
calculator Operation. The result and final answer are typed ContextBundles, the task
and plan finish through semantic completion facts, and the two Runtime WorkRecords are
linked back as evidence rather than embedded as semantic lifecycle state. Reopening the
Kernel store reproduces the plan, task, invocation, contexts and Runtime links.

The current CORE turn protocol in this slice is calculator-specific fixture behavior
used to prove the architecture end to end. It is not the generalized CORE planner,
Operation argument language, Workflow engine or future MADRE UX. Planner generation,
WorkPlanStep, multi-Agent teams, learning/promotion, Workflow DSL execution, human
approval, remote Module transport, rich Scope hierarchy, generic artifact ontology and
scheduler expansion remain deferred.

Implementation commit `2f18a26d099140fd959ace85f0a3da2892d50089` passed the locked
Ubuntu PR validation pipeline: 79 deterministic tests, `ruff check .`,
`ruff format --check .`, strict `mypy`, package build, wheel reinstall and isolated
wheel import including `madre_kernel`. The ASGI calculator acceptance substitutes only
the physical model response with deterministic text; it does not claim new real-model
inference evidence.
