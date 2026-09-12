# MADRE

MADRE is a local-first execution system through which independent Modules use shared
physical Capabilities while retaining ownership of meaning, Material, behavior, and
effects.

Product meaning is defined in [`MADRE.md`](MADRE.md). Current executable state is
recorded in [`docs/implementation-baseline.md`](docs/implementation-baseline.md).

## Packages

- `madre_sdk` contains the public declarative definitions, ordinary Material values,
  standalone Security Algebra, execution requests, and segregated behavior ports.
- `madre` contains the reference Kernel, physical Capability adapters, declarative
  catalog, durable lifecycle, SQLite metadata store, and local HTTP transport.
- `madre_core` contains private behavior for the shipped Module. The package name is
  an installation artifact; CORE is not a public type or Kernel concept.

## Implemented path

The repository executes this path today:

```text
ordinary Module behavior
    -> ExecutionRequest
    -> Kernel
    -> physical Capability
    -> new ordinary Material
    -> Module-owned interpreter
    -> another ordinary Material
```

The Capability output has no command status. Only the Module interpreter can decide
what it means or whether another request or bounded Operation follows.

Security values compose directly through immutable `SecurityScope`,
`SecuritySurface`, `Disclosure`, `Control`, and `EffectExecution` values.
There is no security evaluator, broker, evidence ledger, historical state, derivation
ontology, user-presence exception, or persisted security decision.

## Development

Python 3.13 and the uv version pinned by the repository are required.

```bash
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```

CI installs the wheel into an isolated target and imports `madre`, `madre_sdk`,
and `madre_core` outside the checkout.

## Defining a Module

Import public concepts from `madre_sdk`.

A `ModuleDefinition` is immutable and serializable. It contains only declarative
Module, Agent, Skill, Workflow, Operation, EffectProfile, contract, and exact security
scope values. Every identity declares its domain kind as well as owner, name, and
revision. Python behavior is bound separately through `ModuleRuntime`.

Aggregate security values are structural:

- Module Sensitivity is the maximum applicable Sensitivity among the exact scopes it
  manages or exposes.
- Agent Privacy is the minimum applicable Privacy among the Operations and other
  surfaces it exposes.
- Risk and Autonomy remain paired on one Operation EffectProfile.

A transformation or model response is a new `Material` with a new identity and its
own facts. It is never a derivation or fresh continuation token.

The definitions round-trip through JSON. Their value-only shape is deliberately
suitable for a future equivalent XML representation.

## Running the local transport

Copy `madre.example.toml` to `madre.toml`, describe the installed physical
Capability and its exact security scope, then run:

```bash
uv run madre --config madre.toml
```

The HTTP transport exposes:

- `POST /v1/modules` for declarative catalog registration;
- `POST /v1/executions` for immediate physical execution;
- durable work submission, inspection, cancellation, retry, and one-shot result
  consumption under `/v1/work`.

The transport is a local implementation boundary, not an authentication or
authorization layer.

## Durable privacy

Durable storage contains reference-only execution intent, Module and Capability
identities, Material handles, output specifications, scheduling/lifecycle metadata,
digests, sizes, and compact lifecycle failure/retry dispositions.

It contains no input/output payload columns, prompts, private Module state, algebra
relations, security decisions, rejected candidates, provenance graphs, or provider
error bodies. A Module supplies Material just in time through its resolver. Produced
payload bytes remain in process memory until consumed; a restart marks unconsumed
delivery as lost.

An incompatible algebraic composition ends that request. Its detailed mismatch is
transient; durable work retains only a generic terminal failure and cannot retry the
same rejected request.

## Adapter environment

MADRE does not model credentials, API keys, authentication, authorization,
permissions, clearances, or provider trust. Provider environment setup belongs
outside MADRE. The OpenAI-compatible adapter accepts an externally configured
`httpx.AsyncClient` when non-default transport mechanics are required.

Endpoint locality never supplies Privacy. The Capability definition must declare the
exact observer surface independently.
