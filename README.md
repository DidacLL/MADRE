# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first runtime for independent applications that need AI work to execute under software control now or later.

Applications own domain meaning, state, workflows and result interpretation. MADRE owns the runtime lifecycle of submitted AI work. Inference engines, model providers and tools are replaceable capabilities behind that execution boundary.

Start here:

- [`MADRE.md`](MADRE.md) — canonical product definition and behavioral acceptance path.
- [`AGENTS.md`](AGENTS.md) — minimal repository harness for coding agents.
- [`docs/implementation-baseline.md`](docs/implementation-baseline.md) — implementation decisions, dated environment evidence, and the next behavior.

## What runs today

An installable, typed Python package; explicit TOML configuration; a foreground,
authenticated local health service with exclusive SQLite ownership; and a real
chat-completion environment probe. Work submission, scheduling and recovery are
the next implementation behaviors. There are no placeholder work endpoints.

Python is the current implementation language, not a permanent product boundary.
The planned application API is language-neutral HTTP; C/C++ implementations can be
introduced where concrete runtime responsibilities benefit.

## Bootstrap and checks

Use Python 3.13. Development is pinned to 3.13.3; uv can install that interpreter
when it is absent. Run from the repository root on Windows or Linux:

```console
python -m pip install uv==0.12.10
python -m uv sync --locked
python -m uv run --locked pytest
python -m uv run --locked ruff check .
python -m uv run --locked ruff format --check .
python -m uv run --locked mypy
python -m uv build --python .venv --no-build-isolation
```

Apply formatting with `python -m uv run --locked ruff format .`. Dependencies and
development tools are pinned in `uv.lock`; build tooling is installed by the dev
group; explicitly selecting `.venv` makes `--no-build-isolation` use those locked
versions even when uv itself was installed in the host Python. CI runs deterministic
checks on Windows and Linux, builds the wheel, and verifies its import outside the
checkout. Normal bootstrap/tests never download a model or require inference.

The distribution is `madre-runtime`; the import is `madre`. Other Python projects
can install the built wheel using `python -m pip install <path-to-wheel>` or install
this repository at a chosen Git commit. No package-index publication is assumed.
Import does not start the runtime or create its database:

```python
from pathlib import Path
from madre import create_app, load_settings

settings = load_settings(Path("madre.local.toml"))
app = create_app(settings)  # Reads the configured token; service lifespan owns storage.
```

## Configuration and service

Copy `madre.example.toml` to `madre.local.toml` and edit explicitly. Secrets are
environment-variable references, never literal TOML values. Unknown configuration
keys are errors. Relative data paths resolve beside the TOML file, independent of
the caller's working directory. Omitting `data_dir` uses the OS user data directory
(`%LOCALAPPDATA%\madre` on Windows, `$XDG_DATA_HOME/madre` or `~/.local/share/madre`
on Linux). Use local storage, not a network filesystem.

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

Supply the same token to clients via their environment. The implemented endpoint is
`GET http://127.0.0.1:8731/health`, with `Authorization: Bearer <token>`. It returns
`{"status":"ok","schema_version":1}`. Missing/incorrect credentials return 401.
Stop the foreground service with Ctrl+C. A second runtime using the same data
directory is rejected; process exit releases ownership. Runtime files, local
configuration, model weights, build output and credentials are not committed.

## Optional real local-model probe

An existing compatible local server can be used by editing the capability's
endpoint and model. Local endpoints must use literal loopback IP addresses, e.g.
`127.0.0.1`; the adapter does not inherit proxies or follow redirects.

For the reproducible Windows CPU smoke fixture (about 491 MB of model data):

```powershell
./tools/install-smoke-model.ps1
$fixture = Join-Path $env:LOCALAPPDATA 'MADRE\inference'
& "$fixture\llama-b10809\llama-server.exe" `
  -m "$fixture\qwen2.5-0.5b-instruct-q4_k_m.gguf" `
  --host 127.0.0.1 --port 8080 --alias madre-smoke -c 2048 -ngl 0 -t 4
```

Keep that server running and, in another terminal, run:

```console
python -m uv run --locked madre --config madre.example.toml probe local-chat --timeout 60
```

The probe does not need the MADRE service or its bearer token. It sends a bounded
chat-completion request directly through the package adapter and prints JSON with
generated text, model, finish reason, and elapsed seconds. `finish_reason=length`
means the generation reached its output limit. Failures print a structured error
and exit nonzero. The small model establishes connectivity/computation only; choose
models appropriate to real application quality requirements. Stop the fixture
server with Ctrl+C when finished. Installation is optional and separate from MADRE
bootstrap; runtime/model revisions and verified hashes are pinned in the installer.

## Continue implementation

Implement immediate `/v1/work` submission and inspection through durable SQLite
work/attempt records and the real local capability. Acceptance requires a separate
application client receiving generated output and persisted execution evidence.
Then add delayed execution/recovery, validate a real independent application, and
grow capabilities and controls from use. See the focused baseline for lifecycle,
transfer, cancellation and retry decisions.

GPL-3.0. See [`LICENSE`](LICENSE).
