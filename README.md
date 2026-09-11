# MADRE

This document owns runnable setup and current usage. Product meaning lives in [`MADRE.md`](MADRE.md); implementation stage and validation state live in [`docs/implementation-baseline.md`](docs/implementation-baseline.md).

MADRE is a local-first governed execution and agent-interoperability platform for AI-capable applications.

## Current implementation

The reference Python distribution exposes three deliberate namespaces:

- `madre` — public Kernel contracts plus the current reference Kernel/transport implementation;
- `madre_sdk` — the Module-facing typed SDK boundary;
- `madre_core` — the shipped default CORE-capable Module, implemented only against `madre_sdk`.

The Kernel provides reference-only durable `WorkSubmission -> WorkRecord -> WorkAttempt` execution, JIT Module-owned material resolution, generic transient inference, deterministic Capability selection, discovery, explicit Agent/Operation brokering, restart/retry/cancellation, transition-local Security Algebra evaluation, and execution/security evidence.

The SDK provides typed helpers for Modules, minimal Agents, portable Skills/Workflows, WorkPlan projection, immutable Operation-owned EffectProfiles, Artifact/ContextBundle construction and derivation, Module-owned durable material resolution, transient inference, durable submission/result access, discovery/brokering and CORE fallback selection/delegation. It imports only public MADRE contracts/protocols and does not expose runtime, storage, scheduler, FastAPI or provider-adapter internals.

The shipped `madre_core.CoreModule` is an ordinary SDK Module. Its interaction Agent uses transient inference for the immediate path and can independently delegate to an explicit Agent or project continuation into ordinary durable work. CORE receives no Kernel bypass.

The executable Security Algebra is the scoped model in `docs/architecture/MADRE-security-algebra.md`: `Sensitivity`, `Privacy`, `Integrity`, `Risk`, and `Autonomy` over explicit disclosure/control/effect topology. The previous Trust/Isolation/IntendedUse compatibility evaluator is no longer part of the runtime.

The SDK intentionally does **not** provide a universal Agent session/memory framework, Planner, Workflow executor, security-policy DSL, generic shell/Internet surface or provider credential framework.

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

Module code should normally import from `madre_sdk`. The SDK `Module` helper can publish a manifest, optional Agent/Operation endpoints and a Module-owned `MaterialRepository` through interface-segregated public protocols.

Facets follow exact scopes and roles through `SecurityValues`. Sensitivity can apply to Module/Agent surfaces; ordinary material has no mandatory Integrity. `Artifact` and `ContextBundle` derivation creates new immutable representation/SecurityID bindings; ordinary derivation cannot increase Integrity, while explicit validation derivation is bounded by its validator path.

An SDK `Operation` owns one or more immutable `EffectProfile`s. Each profile pairs `Risk` and `Autonomy`, with optional material-exposure `Privacy`. Actual executors separately declare Integrity. Invocation selects a profile identity rather than supplying these values ad hoc.

`MaterialRepository.retain(...)` produces a `MaterialHandle`; Kernel resolves that handle only when a durable attempt is ready and after candidate mechanism security evaluation. The supplied repository is in-memory. Recovery across a complete host restart requires the owning Module to retain/reconstruct its material and reattach its resolver; Kernel queue durability alone does not provide that.

Agentless/UI-less Modules can use `CoreDelegate` with a configured `CoreSelection`. Selecting another CORE-capable Module changes ordinary Module configuration only; Kernel contains no special meaning for the shipped CORE module name.

## Local HTTP runtime

Copy `madre.example.toml` to `madre.toml`, configure a Capability endpoint/model and its actual participation `privacy` (declare `integrity` only when its role requires it), then run:

```bash
uv run madre --config madre.toml
```

The current HTTP transport exposes Module-manifest registration, generic transient inference, durable work submission/inspection/cancellation/retry and one-shot durable result consumption. It remains a transport for the local owner-controlled installation, not an independent authorization layer or the architecture of the SDK.

Native in-process Module integrations can additionally bind material resolvers and Agent/Operation endpoints through the public protocols used by the SDK. The current launcher does not assemble those integrations or start a CORE interaction surface. Module-manifest registration alone does not attach executable endpoints or a material resolver. The HTTP transport is not expanded into private content storage merely to make durable execution convenient.

## Privacy behavior

Durable runtime storage contains execution intent, `MaterialHandle` metadata, digests, immutable SecurityObjects, accepted SecurityTransitions/derivations, scheduling/lifecycle metadata and compact execution/security evidence. It contains no prompt/context/model-output payload columns and no arbitrary provider-error message text.

Transient inference input/result bytes are not persisted. Durable produced result bytes live only in process memory until consumed; restart before consumption marks delivery evidence as `lost`.

See `docs/architecture/MADRE-execution-contract.md` for canonical execution semantics, `docs/architecture/MADRE-security-algebra.md` for the security model and `docs/implementation-baseline.md` for current executable truth.

## First local release work

The current distribution is a validated runtime/SDK foundation, not yet an assembled
end-user installation. The active stage and concrete completion criteria are in
[`docs/implementation-baseline.md`](docs/implementation-baseline.md).

The example local inference participation is P3. The current CORE interaction code
classifies its contexts as S5, so it cannot use that route without appropriate
bounded material construction/explicit disclosure evidence. Do not raise the
Capability declaration to P5 merely to get a demonstration working. A local URL
alone establishes neither SECRET containment nor an Integrity warrant.
