# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first runtime for independent applications that need AI work to execute under software control now or later.

Applications own domain meaning, state, workflows and result interpretation. MADRE owns the runtime lifecycle of submitted AI work. Inference engines, model providers and tools are replaceable capabilities behind that execution boundary.

Start here:

- [`MADRE.md`](MADRE.md) — canonical product definition and behavioral acceptance path.
- [`AGENTS.md`](AGENTS.md) — minimal repository harness for coding agents.
- [`docs/implementation-baseline.md`](docs/implementation-baseline.md) — implementation decisions, dated environment evidence, and the next behavior.

## What runs today

MADRE provides an installable typed Python package, explicit TOML configuration, an authenticated loopback HTTP service, exclusive SQLite runtime ownership, durable immediate and delayed work records, and a real chat-completion capability adapter.

An independent application submits work to `POST /v1/work`. Currently eligible work executes through the capability path before the response is returned. Future-eligible work is durably accepted with status `accepted` and returns immediately; a background scheduler executes it no earlier than `eligible_at`. `GET /v1/work/{id}` returns the durable lifecycle, attempt, generated result, or classified failure.

Accepted work remains queued across MADRE restart and is rediscovered from SQLite. A work item whose capability attempt had actually started but did not durably finish is instead recorded as `interrupted`, with evidence that the capability outcome may be unknown; MADRE does not retry that invocation. Immediate and delayed work use the same durable record and capability execution path.

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

The distribution is `madre-runtime`; the import is `madre`. Other Python projects can install the built wheel using `python -m pip install <path-to-wheel>` or install this repository at a chosen Git commit. No package-index publication is assumed. Import does not start the runtime or create its database:

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

A current chat-completion submission is shaped like:

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
  "eligible_at": "2030-01-01T12:00:00Z",
  "constraints": {
    "timeout_seconds": 120,
    "local_only": true
  }
}
```

Omit `eligible_at` for immediate work. A future value returns a durable `accepted` record without waiting for capability execution. The work remains inspectable while queued; after eligibility, the same endpoint exposes its terminal result or failure. The application owns the prompt and interpretation of the generated result. MADRE stores the selected execution material because durable work needs enough intent and evidence to be inspected and recovered.

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

The delayed scheduler reuses this exact durable capability execution path. Deterministic runtime tests establish delayed eligibility and restart recovery without downloading or rerunning the real model fixture.

## Continue implementation

Integrate one real independent application beyond the acceptance client: let the application select its own context, submit immediate or delayed work through the HTTP contract, and consume durable results while retaining its domain state and semantics. Additional capabilities, retry, cancellation, and richer concurrency/resource policy should continue to grow from concrete use.

GPL-3.0. See [`LICENSE`](LICENSE).
