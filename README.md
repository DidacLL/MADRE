# MADRE

This document owns runnable setup and current usage. Product meaning lives in [`MADRE.md`](MADRE.md); current development-stage gaps live in [`docs/implementation-baseline.md`](docs/implementation-baseline.md).

MADRE is a local-first governed execution and agent-interoperability platform for AI-capable applications.

## Current implementation

The reference Python package currently provides:

- durable work metadata with delayed eligibility, fairness, retry/cancellation and restart recovery;
- transient input/result handling so prompt/context/output bytes are not persisted by MADRE;
- immutable security envelopes plus carried `SecurityContext` evaluation and decision evidence;
- public Module/Agent/Skill/Workflow/Operation registry contracts whose registration provides discovery/routing only;
- boundary-filtered discovery from caller-carried security state;
- explicit Agent/Operation brokering with evaluated input/return crossings;
- replaceable Capability/model adapters with deterministic selection and one OpenAI-compatible HTTP chat adapter isolated at the mechanism boundary.

The current code still permits pushed material for some durable work while delayed work resolves Module-owned material later. It also does not yet expose the final generic non-durable transient-inference primitive, the completed inference-requirements contract, or the final `SecurityID`/`SecurityObject` schema. These are active Kernel convergence gaps; see `docs/implementation-baseline.md`.

The package does **not** provide a universal Agent runtime, CORE implementation or semantic WorkPlan persistence. Those belong to Modules/SDK stages.

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

## Local HTTP runtime

Copy `madre.example.toml` to `madre.toml`, configure a Capability endpoint/model, then run:

```bash
uv run madre --config madre.toml
```

The HTTP transport currently exposes Module-manifest registration plus work submission/inspection/cancellation/retry and one-shot result consumption. It is a transport for the local owner-controlled installation, not an independent authorization layer.

The configured OpenAI-compatible adapter interprets OpenAI-shaped JSON only after Kernel selects that concrete Capability. Provider credentials, when required, are configured through the adapter and authenticate only to that provider/mechanism.

For delayed work, native Module integrations currently register an originator material provider through the in-process runtime API. The HTTP surface does not become private content storage merely to make delayed execution convenient.

## Privacy behavior

Current durable runtime storage contains references, digests, security-envelope/context data, scheduling/lifecycle metadata and evidence. It contains no prompt/context/model-output payload columns and no arbitrary provider-error message text.

Produced result bytes live only in process memory until consumed. Restart before consumption marks delivery evidence as `lost`.

See `docs/architecture/MADRE-execution-contract.md` for target execution semantics and `docs/implementation-baseline.md` for the exact current-vs-target differences.
