# Implementation baseline

Read `MADRE.md` for product meaning. This document records implementation decisions
that are not yet fully recoverable from working code. It is not a task tracker.

## Owner decisions

The Owner selected a shared local runtime, independent of client application
lifetimes, with a language-neutral HTTP interface and an importable Python package.
The Owner also authorized a small locally installed model for real environment
validation. Python is a present engineering choice, not a product requirement or
permanent architecture: use C or C++ where an observed responsibility benefits.
The local inference engine already runs as a separate C++ process. No speculative
native binding or language-abstraction framework is required.

## Executable foundation

Python 3.13, FastAPI/Pydantic, HTTPX, standard-library SQLite, and OS-backed file
locking form one package. A single foreground Uvicorn process owns its runtime
data directory. Package import performs no service startup or runtime-data writes.
SQLite schema version 1 initializes the database envelope only: **there are no
work tables, scheduler, or work endpoints yet**. `/health` is the implemented HTTP
route. Configuration and service lifecycle are executable; contract types are the
starting vocabulary for the next behavior, not a claim of implemented execution.

Runtime storage belongs on a local filesystem. Its lifetime is independent of
client applications. The exclusive file lock is released by the OS after process
exit; startup rejects unknown database schema versions. Subsequent schema changes
must be transactional, versioned migrations. Do not overwrite an unknown schema.

The service binds to a literal loopback address and requires a bearer credential
from the configured environment variable. No browser CORS access is enabled.
Application IDs attribute work; they are not tenant authentication. This is a
single-owner local service, not a network deployment or hostile-plugin sandbox.

## Next runtime behavior and contract

Implement `POST /v1/work` (202 after durable acceptance) and `GET /v1/work/{id}`
(200 snapshot, 404 unknown ID), using `contracts.py` as the version-one wire types.
The application selects a configured capability ID and JSON input. For the initial
`chat_completions` capability, validate input as `ChatInput` before acceptance.
The capability registry is the explicit configuration map, not generated content.
Keep the service handlers thin around reusable Python runtime functions.

Use work and attempt records in SQLite, with durable accepted input, effective
eligibility time, application ID, capability configuration snapshot (no secret
values), execution constraints, results, and failures. Record acceptance order and
attempt timing. Resolve absent eligibility to acceptance time. Store UTC timestamps.
Claim queued work transactionally; never hold a SQLite transaction across inference.
Start with one active invocation. Within ready work, higher priority runs first,
then earlier eligibility, then acceptance order. Future work must never run early.

Immediate and delayed execution use the same states: queued, running, succeeded,
failed, cancelled, interrupted. On restart, unfinished running attempts become
interrupted with an uncertain outcome; queued work remains actionable. Never
automatically replay an operation that might already have caused an effect.
Explicit retry adds an attempt to the same failed/interrupted work item; prior
attempt evidence is retained. There is no exactly-once guarantee across an external
capability boundary and no automatic submission deduplication in version one.

After immediate execution, add `POST /v1/work/{id}/cancel` and
`POST /v1/work/{id}/retry`, returning snapshots. Unknown IDs return 404; incompatible
states return 409. Cancelling queued work prevents invocation. Active cancellation
stops MADRE's await/transport when possible and records uncertainty; it does not
prove a provider or application effect was stopped or undone. Serialize transitions
so a late completion cannot overwrite a recorded cancellation.

Apply constraints immediately before invocation. The present adapter supports a
total invocation time limit and local-only transfer restriction; unknown constraints
are rejected. Local capability declarations require literal loopback destinations,
and HTTP clients disable environment proxies and redirects. Timeout bounds MADRE's
wait, not the lifetime of computation already accepted by an endpoint. Retain actual
endpoint/model/boundary and finish reason in evidence. Local endpoint configuration
is trusted owner configuration; MADRE cannot attest what an arbitrary server does
internally. Generated tool proposals never automatically become executable calls.

## Environment evidence — 2026-09-06

Initiation started from remote `main` at
`d384879a775b0d01f99cb2ca2271a0c2424d2dc9` on Windows. Host Python 3.13.3,
SQLite 3.49.1, Node 24.15.0, 23.8 GiB RAM, GTX 1050 Ti / 4 GiB VRAM.
The restricted shell did not expose host Python/network correctly; read-only host
checks found them. No existing inference service was found in standard locations
or on ports 11434, 1234, 8080. These are dated observations, not installation requirements.

Installed outside the checkout under `%LOCALAPPDATA%\MADRE\inference`:

- llama.cpp `b10809`, commit `5266f24da`, Windows x64 CPU build;
- Qwen/Qwen2.5-0.5B-Instruct-GGUF at revision
  `9217f5db79a29953eb74d5343926648285ec7e67`, Q4_K_M, 491,400,032 bytes.

Exact download URLs and verified SHA-256 hashes live in
`tools/install-smoke-model.ps1`. The first real probe generated nonempty output in
2.894 seconds with CPU execution, four threads, and a 2048-token context. The small
model failed an exact-output instruction: this establishes real local connectivity
and computation, not reasoning quality. It is replaceable without changing the
application lifecycle contract. Test fixtures cover deterministic protocol errors;
they are not evidence of real generation.

Validation executed on this host: uncached `uv sync --locked` into a fresh
temporary environment, installation/import of the built wheel outside the checkout,
installed CLI configuration validation, real Uvicorn HTTP health (200 with a token,
401 without), rejection of a second database owner, and reopening after service
process termination. Pytest, Ruff lint/format checks and strict mypy pass. A second
real inference probe returned a greeting in 0.932 seconds with `finish_reason=stop`.
The build command explicitly selects `.venv`: without that selection the host uv
invocation used host Python and could not import the locked build backend.
The current Starlette test client emits two upstream deprecation warnings; checks
do not suppress them. Linux behavior is covered by the repository CI matrix rather
than claimed from this Windows execution.

## Route to useful MADRE

1. **Immediate real execution:** independent HTTP client → durable submission →
   attempt → configured local inference → inspectable result or accurate failure.
2. **Delay and recovery:** persisted eligibility, restart processing, interruption,
   cancellation, and explicit retry using the same runtime work.
3. **Independent application acceptance:** a real consuming project retains its
   domain state, submits selected context, disconnects, and later retrieves results.
4. **Growth from use:** application-owned operations, replacement capabilities, and
   additional resource/transfer controls justified by real applications.

The exact next implementation is item 1, including local transfer enforcement and
failure evidence. Verify it with a separate client and real model, plus deterministic
failure fixtures. This initiation does not satisfy that acceptance goal by itself.
