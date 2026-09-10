# MADRE

This document owns runnable setup and current usage. Product meaning lives in [`MADRE.md`](MADRE.md); implementation stage and validation state live in [`docs/implementation-baseline.md`](docs/implementation-baseline.md).

MADRE is a local-first governed execution and agent-interoperability platform for AI-capable applications.

## Current implementation

The reference Python distribution exposes three deliberate namespaces:

- `madre` — public Kernel contracts plus the current reference Kernel/transport implementation;
- `madre_sdk` — the Module-facing typed SDK boundary;
- `madre_core` — the shipped default CORE-capable Module, implemented only against `madre_sdk`.

The Kernel provides reference-only durable `WorkSubmission -> WorkRecord -> WorkAttempt` execution, JIT Module-owned material resolution, generic transient inference, bound/carried security objects, deterministic Capability selection, discovery, explicit Agent/Operation brokering, restart/retry/cancellation and execution evidence.

The SDK provides small typed helpers for Modules, minimal Agents, portable Skills/Workflows, WorkPlan projection, bounded Operations, Artifact/ContextBundle construction and derivation, Module-owned durable material resolution, transient inference, durable submission/result access, discovery/brokering and CORE fallback selection/delegation. It imports only public MADRE contracts/protocols and does not expose runtime, storage, scheduler, FastAPI or provider-adapter internals.

The shipped `madre_core.CoreModule` is an ordinary SDK Module. Its interaction Agent uses transient inference for the immediate path and can independently delegate to an explicit Agent or project continuation into ordinary durable work. CORE receives no Kernel bypass.

The final MADRE Security Algebra architecture is now frozen in `docs/architecture/MADRE-security-algebra.md` around `Sensitivity`, `Privacy`, `Integrity`, `Risk`, `Autonomy`, explicit transition roles and immutable Operation `EffectProfile`s. The current Python runtime still uses the earlier compatibility security schema/evaluator; `docs/implementation-baseline.md` records the exact migration gap. Do not infer the final formula from current `trust`/`isolation`/`intended_use` implementation fields.

The SDK intentionally does **not** provide a universal Agent session/memory framework, Planner, Workflow executor, generic shell/Internet surface or provider credential framework.

## Development

The project requires Python 3.13 and the uv version pinned in `pyproject.toml`/CI.

```bash
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```

CI reinstalls the built wheel and verifies isolated imports of `madre`, `madre_sdk` and `madre_core` outside the checkout.

## Building a Module

Module code should normally import from `madre_sdk`. The SDK `Module` helper can publish a manifest, optional Agent/Operation endpoints and a Module-owned `MaterialRepository` through the corresponding interface-segregated public protocols. Modules that need only transient inference can depend only on `TransientInference`; Modules that schedule durable work additionally use `DurableWorkSubmission` and material-resolution registration.

`Artifact` and `ContextBundle` are Module-owned representations. Calling `derive(...)` currently creates a new identity/security binding instead of rewriting the source representation. The upcoming security migration will align their security values and derivation evidence with the frozen `Sensitivity`/`Integrity` model without moving semantic classification into Kernel.

`MaterialRepository.retain(...)` produces a `MaterialHandle`; Kernel resolves that handle only when a durable attempt is ready.

Agentless/UI-less Modules can use `CoreDelegate` with a configured `CoreSelection`. Selecting another CORE-capable Module changes ordinary Module configuration only; Kernel contains no special meaning for the shipped CORE module name.

## Local HTTP runtime

Copy `madre.example.toml` to `madre.toml`, configure a Capability endpoint/model, then run:

```bash
uv run madre --config madre.toml
```

The current HTTP transport exposes Module-manifest registration, generic transient inference, durable work submission/inspection/cancellation/retry and one-shot durable result consumption. It remains a transport for the local owner-controlled installation, not an independent authorization layer or the architecture of the SDK.

Native in-process Module integrations can additionally bind material resolvers and Agent/Operation endpoints through the public protocols used by the SDK. The HTTP transport is not expanded into private content storage merely to make durable execution convenient.

## Privacy behavior

Durable runtime storage contains execution intent, `MaterialHandle` metadata, digests, carried `SecurityObject` facts, scheduling/lifecycle metadata and execution/security evidence. It contains no prompt/context/model-output payload columns and no arbitrary provider-error message text.

Transient inference input/result bytes are not persisted. Durable produced result bytes live only in process memory until consumed; restart before consumption marks delivery evidence as `lost`.

See `docs/architecture/MADRE-execution-contract.md` for canonical execution semantics, `docs/architecture/MADRE-security-algebra.md` for the frozen security model and `docs/implementation-baseline.md` for current executable truth.
