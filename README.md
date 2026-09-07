# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** provides a shared execution runtime for independent applications and first-party CORE intelligence that need permitted AI work to execute under software control now or later.

Applications own domain meaning, state, workflows and result interpretation. CORE owns default generic/system intelligence. MADRE Runtime owns the lifecycle of submitted work. Inference engines, model providers and tools are replaceable capabilities behind that execution boundary.

Start here:

- [`MADRE.md`](MADRE.md) — canonical product definition and behavioral acceptance path.
- [`AGENTS.md`](AGENTS.md) — minimal repository harness for coding agents.
- [`docs/implementation-baseline.md`](docs/implementation-baseline.md) — implementation decisions, dated environment evidence, and the next behavior.
- [`docs/core.md`](docs/core.md) — interactive CORE behavior, usage, and real local product acceptance.

## What runs today

MADRE provides an installable typed Python distribution, explicit TOML runtime configuration, an authenticated loopback HTTP service, exclusive SQLite runtime ownership, durable runtime-work records, a real chat-completion capability adapter, and CORE as a separate first-party application package/entry point that uses that same HTTP boundary.

An independent application or CORE submits work to `POST /v1/work`. Every valid submission is durably created as `accepted` before the response returns, regardless of whether it is eligible now or later. The service-owned scheduler executes already-eligible accepted work and waits for future eligibility using those same persisted records. Physical capability execution is not owned by the submitting HTTP request, so applications obtain the durable work ID without waiting for capability completion or scarce-resource admission.

Eligible applications receive scheduler turns in durable round-robin order, so one application's accepted backlog cannot monopolize the shared runtime. Each work submission may carry a bounded `priority` from `-100` to `100`, defaulting to `0`. Higher priority is selected first within that application's turn; equal-priority work is FIFO by its current queue entry. Priority is execution intent, not permission to acquire more global turns. Explicit retry creates a new queue entry for that logical work, and the fairness cursor survives restart.

Applications may optionally send an `Idempotency-Key` header when a logical submission may need to be retried. The key is scoped to `application_id` and persisted with runtime acceptance. Reusing the same key with the same normalized `WorkSubmission` returns the original durable work record, including after restart or completion, rather than creating another invocation. Reusing that key for different work returns HTTP 409. Omitting the header preserves ordinary distinct submissions.

Failed work can be explicitly requeued through `POST /v1/work/{id}/retry`. Retry requests require their own `Idempotency-Key`, are durably recorded, and replay safely without authorizing duplicate execution. Physical attempts identify which retry authorized them. MADRE never retries work automatically. A work item interrupted while a capability may already have executed is retryable only when the caller explicitly sets `allow_unknown_outcome` because another invocation may duplicate external effects.

Work can be cancelled through `POST /v1/work/{id}/cancel`. If the work is still `accepted`, cancellation is durable and terminal as `cancelled`; the currently pending execution is prevented from starting a capability attempt. For an initial submission that means the capability is never invoked; if a previous attempt already failed and was explicitly retried, that earlier attempt remains in the evidence. If a physical attempt is already `running`, MADRE records `requested_while_running` cancellation evidence but does not pretend that cancelling its own HTTP coroutine would prove the external capability stopped. The invocation keeps its scarce-resource admission until it actually returns, and its real success or failure is then recorded together with the cancellation request. Cancellation is monotonic for one logical work item: work with a recorded cancellation request cannot later be retried under the same identity.

`GET /v1/work/{id}` returns the durable record at any point in that lifecycle and is the generic completion boundary for applications that need the eventual result. A process restart preserves accepted work that has not started a capability attempt, including an accepted explicit retry, and preserves cancelled work without executing it. A previously in-flight attempt becomes an `interrupted` failure whose evidence states that the capability outcome may be unknown; any previously recorded running cancellation request remains part of that evidence.

Python is the current implementation language, not a permanent product boundary. The application API is language-neutral HTTP; C/C++ implementations can be introduced where concrete runtime responsibilities benefit.

## Bootstrap and checks

Use Python 3.13. Development is pinned to 3.13.3; uv can install that interpreter when it is absent. Run from the repository root on Windows or Linux:

```console
python -m pip install uv==0.12.10
python -m uv sync --locked
python -m uv run --locked pytest
python -m uv run --locked ruff check .
python -m uv run --locked ruff format --check .
python -m uv run --locked mypy
python -m uv build --python .venv --no-build-isolation
```

Apply formatting with `python -m uv run --locked ruff format .`. Dependencies and development tools are pinned in `uv.lock`; build tooling is installed by the dev group; explicitly selecting `.venv` makes `--no-build-isolation` use those locked versions even when uv itself was installed in the host Python. CI runs deterministic checks, builds the wheel, and verifies its import outside the checkout. Normal bootstrap/tests never download a model or require inference.

Executable PR changes run checks on Linux. Documentation-only PRs skip runtime CI; pushes do not duplicate PR checks. For packaging, dependencies, platform-specific code, storage/process semantics, or release readiness, request full Windows + Linux validation via **Actions → checks → Run workflow**, selecting the branch to validate.

The distribution is `madre-runtime`; the runtime import is `madre` and the first-party application import is `madre_core`. Other Python projects can install the built wheel using `python -m pip install <path-to-wheel>` or install this repository at a chosen Git commit. No package-index publication is assumed. Import does not start the runtime or create its database:

```python
from pathlib import Path
from madre import create_app, load_settings

settings = load_settings(Path("madre.local.toml"))
app = create_app(settings)  # Reads the configured token; service lifespan owns storage.
```

## Configuration and service

Copy `madre.example.toml` to `madre.local.toml` and edit explicitly. The local service token is an environment-variable reference, never a literal TOML value. Unknown configuration keys are errors. Relative data paths resolve beside the TOML file, independent of the caller's working directory. Omitting `data_dir` uses the OS user data directory (`%LOCALAPPDATA%\madre` on Windows, `$XDG_DATA_HOME/madre` or `~/.local/share/madre` on Linux). Use local storage, not a network filesystem.

PowerShell:

```powershell
Copy-Item madre.example.toml madre.local.toml
$env:MADRE_API_TOKEN = (python -c "import secrets; print(secrets.token_urlsafe(32))")
python -m uv run --locked madre --config madre.local.toml check-config
python -m uv run --locked madre --config madre.local.toml serve
```

POSIX shell:

```sh
cp madre.example.toml madre.local.toml
export MADRE_API_TOKEN="$(python -c 'import secrets; print(secrets.token_urlsafe(32))')"
python -m uv run --locked madre --config madre.local.toml check-config
python -m uv run --locked madre --config madre.local.toml serve
```

`MADRE_API_TOKEN` is generated locally to control access to your MADRE service. It is not a provider API key, requires no paid account, and is not forwarded to inference capabilities. Supply it only to the local applications using MADRE.

The authenticated endpoints are:

- `GET http://127.0.0.1:8731/health`
- `POST http://127.0.0.1:8731/v1/work`
- `POST http://127.0.0.1:8731/v1/work/{id}/retry`
- `POST http://127.0.0.1:8731/v1/work/{id}/cancel`
- `GET http://127.0.0.1:8731/v1/work/{id}`

All require `Authorization: Bearer <token>`. `/health` returns `{"status":"ok"}`. Missing or incorrect credentials return 401. Stop the foreground service with Ctrl+C. A second runtime using the same data directory is rejected; process exit releases ownership. Runtime files, local configuration, model weights, build output and credentials are not committed. During active development, a runtime database whose internal storage structure no longer matches the executable is discarded and recreated rather than migrated.

A chat-completion submission is shaped like:

```json
{
  "application_id": "my-application",
  "capability_id": "local-chat",
  "input": {
    "messages": [
      {"role": "user", "content": "Reply with a short greeting."}
    ],
    "max_tokens": 64
  },
  "eligible_at": "2035-01-01T12:05:00Z",
  "priority": 20,
  "constraints": {
    "timeout_seconds": 120,
    "local_only": true
  }
}
```

Omit `eligible_at` (or use `null`) for immediate eligibility. Immediate and future-eligible submissions both return a durable `accepted` record; the difference is only when the runtime may execute them. Poll `GET /v1/work/{id}` when the application needs eventual success, failure or cancellation. The application owns whether it waits, continues foreground interaction, submits additional work, cancels pending work, or inspects later. MADRE stores only the application-selected execution material and runtime evidence required to execute, recover and inspect the work.

Omit `priority` to use `0`. Values from `-100` through `100` are accepted, with larger values selected first among eligible work belonging to the same `application_id`. Priority does not move an application ahead of another application's fair scheduler turn. Within the same application and priority, the current queue order is FIFO; an explicit retry receives a fresh queue position instead of inheriting the original submission's age. Changing priority while reusing the same submission idempotency key is a conflict because priority is part of the execution intent.

When retrying one logical submission POST after a transport failure, send the same opaque key (1–128 characters) in `Idempotency-Key` and the same work body. The durable work identity is then stable even if the original response was lost. A key is intentionally not part of `WorkSubmission`: it controls acceptance/replay rather than changing what the work means.

To explicitly retry a failed work item, POST to `/v1/work/{id}/retry` with a new idempotency key for that logical retry:

```json
{
  "allow_unknown_outcome": false
}
```

The default `false` is appropriate for failures whose previous capability invocation has a known failure outcome. If the work failed as `interrupted`, MADRE rejects the retry until `allow_unknown_outcome` is explicitly set to `true`; that consent acknowledges that the previous invocation may already have produced an external effect. Reusing the same retry idempotency key replays that retry request instead of requeueing the work again. A different key represents a new explicit retry and is accepted only if the work is failed again. Work that already carries a cancellation request is not retryable; submit new work if the application deliberately changes that intent.

Cancellation needs no idempotency key because it is itself monotonic and idempotent. Repeating `POST /v1/work/{id}/cancel` returns the same recorded cancellation. A `cancelled` record with `cancellation.disposition = "prevented"` proves that the accepted execution present when cancellation was recorded did not start another attempt. A running record with `cancellation.disposition = "requested_while_running"` means only that MADRE durably received the request after physical execution had begun; continue inspecting that work for the real execution outcome. A new cancellation request for work that already completed without any cancellation request returns HTTP 409.

## CORE

With the MADRE service running and the same `MADRE_API_TOKEN` available in a second terminal, start the minimal interactive first-party application:

```console
python -m uv run --locked madre-core --runtime-url http://127.0.0.1:8731 --capability local-chat
```

Enter a message at `you>` and CORE submits ordinary authenticated MADRE work as application `madre-core`. Assistant text is printed at `core>`, followed by `reasoning> fast` when the foreground answer is considered sufficient or `reasoning> deeper` when CORE recommends stronger follow-up reasoning. A deeper recommendation also exposes `deeper> /deeper`. Entering `/deeper` explicitly submits one second ordinary MADRE work item with a deeper-analysis instruction and the larger `--deeper-max-tokens` budget; its result is printed at `core(deeper)>` and replaces the fast draft in process-local conversation history. CORE never escalates automatically. Runtime/capability failures and work cancelled before execution are surfaced explicitly and are not appended to conversation history.

This terminal interaction is an executable development surface, not a stable MADRE UX contract. The deterministic suite proves its software semantics; owner-side model runs have shown useful execution through the real path while also showing that current fast/deeper judgement and chat presentation remain experimental. [`docs/core.md`](docs/core.md#local-product-acceptance) records that evidence and the local acceptance procedure.

CORE does not import the runtime scheduler, storage or capability invocation path. It does not contact llama.cpp directly. The runtime has no CORE-specific scheduling or admission behavior.

## Optional real local-model fixture

An existing compatible local server can be used by editing the capability's endpoint and model. Local endpoints must use literal loopback IP addresses, e.g. `127.0.0.1`; the adapter does not inherit proxies or follow redirects.

For the reproducible Windows CPU smoke fixture (about 491 MB of model data):

```powershell
./tools/install-smoke-model.ps1
$fixture = Join-Path $env:LOCALAPPDATA 'MADRE\inference'
& "$fixture\llama-b10809\llama-server.exe" `
  -m "$fixture\qwen2.5-0.5b-instruct-q4_k_m.gguf" `
  --host 127.0.0.1 --port 8080 --alias madre-smoke -c 2048 -ngl 0 -t 4
```

Keep that server running. The direct capability probe remains useful for isolating the inference boundary:

```console
python -m uv run --locked madre --config madre.example.toml probe local-chat --timeout 60
```

The probe does not need the MADRE service or its bearer token. It invokes the configured capability adapter directly and prints generated text, model, finish reason, and elapsed seconds.

## Real immediate-work acceptance

For the product path, run both the local model server above and MADRE itself. Use a local config whose `local-chat` capability points to that model server, then start MADRE:

```powershell
$env:MADRE_API_TOKEN = (python -c "import secrets; print(secrets.token_urlsafe(32))")
python -m uv run --locked madre --config madre.local.toml serve
```

In a third terminal, with the same `MADRE_API_TOKEN`, run the independent client. The script intentionally does not import `madre`:

```console
python tools/accept-immediate-work.py --capability local-chat
```

A successful check submits work through HTTP with a fresh idempotency key, requires the POST to return a newly accepted durable work ID, polls `GET /v1/work/{id}` while runtime-owned execution proceeds, requires non-empty generated text from the real configured model, re-inspects the durable terminal record, and then replays the original POST with the same key and requires the same terminal work record rather than a second work item. Capability failures are printed from MADRE's durable work record and cause a nonzero exit.

Fixtures and mocked inference establish deterministic protocol and failure behavior only. This acceptance command is the check for a claim that the complete HTTP runtime path executed a real model.

## Continue implementation

The first-party CORE path and its experimental fast/deeper follow-up have now produced enough real-model evidence for this stage. Further chatbot UX, classifier/prompt tuning and model-floor exploration are intentionally deferred.

Development remains focused on the reliable shared execution framework. Read `docs/implementation-baseline.md`, inspect current runtime code/tests, and select one concrete generic reliability behavior that materially improves dependable execution for applications and CORE alike. Do not prebuild a generalized Agent, Planner, workflow engine, memory system or capability router merely to continue development; broaden an abstraction only when a real behavior requires it.

GPL-3.0. See [`LICENSE`](LICENSE).
