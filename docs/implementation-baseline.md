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

## What executes today

Explicit TOML configuration, package/CLI entry points, authenticated loopback
`/health`, and exclusive runtime-data ownership are implemented. Service lifespan
owns a local SQLite database; the OS releases its ownership lock after process
exit. Startup rejects unknown schema versions. Schema version 1 initializes the
database envelope; work storage, scheduling and submission remain to be implemented.

The configured chat-completion adapter invokes real local inference and returns
generated text, model, finish reason and timing, or a classified failure. It enforces
a total invocation timeout and local-only transfer constraints. Local declarations
require literal loopback destinations; HTTP clients disable proxies and redirects.
Timeout bounds MADRE's wait for the endpoint. The executable behavior and validation
rules live in code/tests; README owns setup and developer commands.

## Initial work direction and next behavior

Immediate and delayed execution share a runtime-work concept. `WorkSubmission`
provides initial vocabulary for application identity, selected capability/input,
eligibility intent and execution constraints. SQLite will retain enough execution
intent and outcome evidence to recover and inspect work across interruption.
Recovery guarantees must reflect what actually occurred at the capability boundary.

**Implement real immediate work execution next:** application → local HTTP → MADRE
runtime → configured local inference → durable, inspectable result or accurate
failure. Build on the existing service, database ownership and `invoke_chat` adapter.
Keep runtime execution reusable beneath HTTP and validate the path with a separate
client, real inference and deterministic failure cases.

The implementing session should choose and test the detailed endpoint/response
shapes, work/attempt storage, lifecycle transitions and recovery mechanics while
building that behavior. Later scheduling, retry, cancellation and concurrency policy
should follow the behavior being implemented.

The dependency/value order is:

1. Real immediate runtime work execution.
2. Delayed eligibility and recovery.
3. Real independent-application integration.
4. Capabilities, execution controls and further behavior grown from actual use.

## Verified development evidence — 2026-09-06

On Windows, Python 3.13.3 / SQLite 3.49.1 ran on a host with 23.8 GiB RAM and a
GTX 1050 Ti / 4 GiB VRAM. The optional CPU fixture uses llama.cpp `b10809` and
Qwen2.5-0.5B-Instruct Q4_K_M outside the checkout. Exact revisions, URLs and verified
SHA-256 hashes are preserved in `tools/install-smoke-model.ps1`.

Real inference returned a greeting in 0.932 seconds. The small model also failed
an exact-output instruction: the probe establishes connectivity/computation rather
than application-quality reasoning. Clean locked bootstrap, external wheel import,
live authenticated HTTP, exclusive SQLite ownership/reopening, deterministic tests
and static checks passed. Bootstrap CI passed on Windows and Linux. These are dated
results; current implementation evidence comes from current checks and runtime use.
