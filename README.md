# MADRE

This document owns runnable setup and current usage. Product meaning lives in [`MADRE.md`](MADRE.md); implementation stage and validation state live in [`docs/implementation-baseline.md`](docs/implementation-baseline.md).

MADRE is a local-first governed execution and agent-interoperability platform for AI-capable applications.

## Current implementation

The reference Python package currently provides:

- reference-only durable `WorkSubmission -> WorkRecord -> WorkAttempt` execution with delayed eligibility, fairness, retry/cancellation and restart recovery;
- Module-owned `MaterialHandle` resolution only when a selected attempt is ready, with digest and bound `SecurityObject` continuity verification;
- generic non-durable transient inference with no `WorkRecord` or restart promise;
- bound `SecurityID` / `SecurityObject` state with subject-kind-specific values, carried composition and deterministic decision evidence;
- a temporary compatibility security evaluator isolated behind the public evaluator seam while the final Security Algebra remains a separate design task;
- public Module/Agent/Skill/Workflow/Operation registry contracts whose registration provides discovery/routing facts rather than authority;
- explicit Agent/Operation brokering with evaluated input/return crossings and unknown-effect evidence for uncertain Operations;
- deterministic Capability selection from hard inference requirements, ordered preferences and explicit fallback semantics;
- one OpenAI-compatible HTTP chat adapter isolated at the physical mechanism boundary;
- interface-segregated Module-facing protocols for registration, transient inference, durable work, material resolution, inspection/result access, discovery and optional Agent/Operation endpoints.

The package does **not** provide a universal Agent runtime, CORE implementation, semantic WorkPlan persistence, Workflow engine, generic unrestricted shell/Internet surface or the final Security Algebra formula. Those belong to later Module/SDK or focused security stages.

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

CI also reinstalls the built wheel and verifies an isolated import outside the checkout.

## Local HTTP runtime

Copy `madre.example.toml` to `madre.toml`, configure a Capability endpoint/model, then run:

```bash
uv run madre --config madre.toml
```

The HTTP transport exposes Module-manifest registration, generic transient inference, durable work submission/inspection/cancellation/retry and one-shot durable result consumption. It is a transport for the local owner-controlled installation, not an independent authorization layer.

The configured OpenAI-compatible adapter interprets OpenAI-shaped JSON only after Kernel selects that concrete Capability. Provider credentials, when required, are configured through the adapter and authenticate only to that provider/mechanism.

Native Module integrations that submit durable work register an originator `MaterialResolver` through the in-process public runtime boundary. The resolver retains private prepared material in the Module and returns it only when Kernel asks for the concrete ready attempt. The HTTP transport does not become private content storage merely to make durable execution convenient.

## Privacy behavior

Durable runtime storage contains execution intent, `MaterialHandle` metadata, digests, carried `SecurityObject` facts, scheduling/lifecycle metadata and execution/security evidence. It contains no prompt/context/model-output payload columns and no arbitrary provider-error message text.

Transient inference input/result bytes are not persisted. Durable produced result bytes live only in process memory until consumed; restart before consumption marks delivery evidence as `lost`.

See `docs/architecture/MADRE-execution-contract.md` for the canonical execution contract and `docs/implementation-baseline.md` for current executable truth.
