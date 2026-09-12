# MADRE

MADRE is a personal runtime through which independently implemented Modules use
installed physical inference and deterministic mechanisms while retaining ownership
of their Material, meaning, and behavior.

Product meaning is defined in [`MADRE.md`](MADRE.md). Current executable behavior is
recorded in [`docs/implementation-baseline.md`](docs/implementation-baseline.md).

## Packages

- `madre_sdk` contains the immutable OOP model used to build Modules: nominal
  identities, typed Material, role-specific surfaces, the five algebraic carriers,
  declarative Module building blocks, bounded Operation calls, physical work
  contracts, and explicit JSON codecs.
- `madre` contains Kernel, installed Capability definitions and adapters, physical
  selection and resources, durable queueing and delivery, the live Module registry,
  and local HTTP transport.

There is no shipped Module. Installation may assign any separately installed ordinary
Module to a default interaction role without changing its SDK or Kernel behavior.

## Executable path

```text
Module-created Material
    -> typed WorkRequest without a Capability identity
    -> Kernel selects and schedules an installed physical Capability
    -> Capability receives opaque payload and returns physical output
    -> Kernel returns PhysicalResult
    -> Module interprets it and may construct new Material or continue
```

The private integration fixture proves a Module-owned second request after
interpreting the first physical result. Physical output has no interface through
which it can invoke Kernel or an Operation.

Capability selection uses the request's computation and Material contracts, direct
composition of carried Sensitivity with explicitly installed receiving Privacy,
typed physical requirements and preferences, current availability, and generic
resource claims. Modules do not name or inspect the selected Capability.

## Durable work

SQLite stores only the physical work queue, selected attempt telemetry, scheduling
state, opaque queued input, and raw results pending delivery. Queued input and pending
output survive restart. Kernel removes input after successful execution, removes
output after delivery, and provides cleanup for expired failed input.

The Module registry is live memory, not SQLite. Running Modules register their
definition at startup and may query the exact public Module, Agent, and Operation
surfaces reachable from their current Material.

## Running

Python 3.13 and the `uv` version pinned by the repository are required.

```bash
uv sync --locked
uv run madre --config madre.toml
```

Copy `madre.example.toml` to `madre.toml` and describe installed physical mechanisms.
Privacy is supplied explicitly for every installed receiving boundary; physical
location does not determine it.

The local HTTP process accepts the same versioned JSON documents produced by the SDK
codecs:

- `POST /v1/modules` registers or replaces a running Module definition;
- `DELETE /v1/modules/{module_name}` removes it;
- `POST /v1/modules/reachable` lists exact reachable public definitions;
- `POST /v1/executions` performs immediate physical work;
- `POST /v1/work` queues durable physical work;
- `GET /v1/work/{work_id}` inspects scheduling and attempt state;
- `POST /v1/work/{work_id}/cancel` cancels queued work;
- `POST /v1/work/{work_id}/result` consumes a pending physical result.

## Development verification

```bash
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```
