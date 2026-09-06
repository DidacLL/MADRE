# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** provides a shared execution runtime for independent applications and first-party CORE intelligence that need permitted AI work to execute under software control now or later.

Applications own domain meaning, state, workflows and result interpretation. CORE owns default generic/system intelligence. MADRE Runtime owns the lifecycle of submitted work. Inference engines, model providers and tools are replaceable capabilities behind that execution boundary.

Start here:

- [`MADRE.md`](MADRE.md) — canonical product definition and behavioral acceptance path.
- [`AGENTS.md`](AGENTS.md) — minimal repository harness for coding agents.
- [`docs/implementation-baseline.md`](docs/implementation-baseline.md) — implementation decisions, dated environment evidence, and the next behavior.
- [`docs/core.md`](docs/core.md) — the first interactive CORE application behavior and how to run it.

## What runs today

MADRE provides an installable typed Python distribution, explicit TOML runtime configuration, an authenticated loopback HTTP service, exclusive SQLite runtime ownership, durable runtime-work records, a real chat-completion capability adapter, and CORE as a separate first-party application package/entry point that uses that same HTTP boundary.

An independent application or CORE submits work to `POST /v1/work`. Work that is already eligible is durably accepted and executed through the configured capability before the response returns. Work whose `eligible_at` is in the future is durably accepted with status `accepted` and returned without waiting for execution. The service scheduler uses those same persisted work records, executes them no earlier than eligibility, and rediscovers accepted work after runtime restart. Immediate and delayed work use the same validation, attempt, capability invocation, result/failure persistence and inspection path.

`GET /v1/work/{id}` returns the durable record at any point in that lifecycle. A process restart preserves accepted work that has not started a capability attempt. A previously in-flight attempt becomes an `interrupted` failure whose evidence states that the capability outcome may be unknown; MADRE does not retry it.

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
- `GET http://127.0.0.1:8731/v1/work/{id}`

All require `Authorization: Bearer <token>`. `/health` returns `{"status":"ok","schema_version":2}`. Missing or incorrect credentials return 401. Stop the foreground service with Ctrl+C. A second runtime using the same data directory is rejected; process exit releases ownership. Runtime files, local configuration, model weights, build output and credentials are not committed.

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
  "constraints": {
    "timeout_seconds": 120,
    "local_only": true
  }
}
```

Omit `eligible_at` (or use `null`) for immediate eligibility. A future value returns a durable `accepted` record immediately; poll `GET /v1/work/{id}` to inspect eventual success or failure. The application owns the prompt and interpretation of the generated result. MADRE stores only the application-selected execution material and runtime evidence required to execute, recover and inspect the work.

## CORE

With the MADRE service running and the same `MADRE_API_TOKEN` available in a second terminal, start the minimal interactive first-party application:

```console
python -m uv run --locked madre-core --runtime-url http://127.0.0.1:8731 --capability local-chat
```

Enter a message at `you>` and CORE submits ordinary authenticated MADRE work as application `madre-core`. Assistant text is printed at `core>`, followed by `reasoning> fast` when the foreground answer is considered sufficient or `reasoning> deeper` when CORE recommends stronger follow-up reasoning. A deeper recommendation also exposes `deeper> /deeper`. Entering `/deeper` explicitly submits one second ordinary MADRE work item with a deeper-analysis instruction and the larger `--deeper-max-tokens` budget; its result is printed at `core(deeper)>` and replaces the fast draft in process-local conversation history. CORE never escalates automatically. Runtime/capability failures are printed explicitly and are not appended to conversation history. See [`docs/core.md`](docs/core.md) for the exact behavior and options.

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

A successful check submits work through HTTP, requires non-empty generated text from the real configured model, then reads the work back through the inspection endpoint and requires the durable record to match the submission response. Capability failures are printed from MADRE's durable work record and cause a nonzero exit.

Fixtures and mocked inference establish deterministic protocol and failure behavior only. This acceptance command is the check for a claim that the complete HTTP runtime path executed a real model.

## Continue implementation

CORE now has one concrete two-stage reasoning path: a fast foreground response can recommend deeper handling, and the user can explicitly turn that recommendation into one stronger follow-up through ordinary MADRE work. The next behavior should be chosen from evidence produced by using this path rather than by prebuilding a generalized Agent, Planner, workflow engine, memory system or capability router.

GPL-3.0. See [`LICENSE`](LICENSE).
