# MADRE

MADRE is a local-first governed execution and agent-interoperability platform for AI-capable applications.

Start with [`MADRE.md`](MADRE.md). The active architecture is split into focused documents under `docs/architecture/`; old semantic-Kernel Agent/WorkPlan orchestration is not part of the current system.

## Current implementation

The reference Python package provides:

- durable metadata-only work lifecycle with delayed eligibility, fairness, retry/cancellation and restart recovery;
- transient input/result handling so prompt/context/output bytes are not persisted by MADRE;
- immutable security envelopes plus carried additive `SecurityContext` evaluation and traceable decision evidence;
- public Module/Agent/Skill/Workflow/Operation registry contracts whose registration provides discovery/routing, never authorization;
- boundary-filtered discovery evaluated from the caller's carried security context;
- explicit Agent/Operation brokering with input and return crossings continuing the same security lifecycle;
- replaceable Capability/model adapters with deterministic selection and one OpenAI-compatible chat adapter isolated at the provider boundary.

It does **not** provide a universal Agent runtime or persist semantic WorkPlans. Those belong to Modules.

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

Copy `madre.example.toml` to `madre.toml`, configure a capability endpoint/model, then run:

```bash
uv run madre --config madre.toml
```

The HTTP transport exposes Module-manifest registration plus work submission/inspection/cancellation/retry and one-shot result consumption. It is a transport for the local owner-controlled installation, not an authorization layer.

The work submission model carries execution material plus its security lifecycle context. The configured OpenAI-compatible adapter interprets OpenAI-shaped JSON only after Kernel selects that capability. Provider credentials, when required, are configured through the adapter (for example `api_key_env`) and authenticate to the provider only; they do not authorize MADRE work.

For delayed work, native Module integrations register an originator material provider through the in-process runtime API. The HTTP surface does not become private content storage merely to make delayed execution convenient.

## Privacy behavior

Durable runtime storage contains references, digests, immutable security envelopes/contexts, scheduling/lifecycle metadata and evidence. It contains no prompt/context/model-output payload columns and no arbitrary provider-error message text.

Produced result bytes live only in process memory until consumed. Restart before consumption marks the delivery evidence as `lost`.

See `docs/architecture/MADRE-execution-contract.md` for exact semantics.
