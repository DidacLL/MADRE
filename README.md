# MADRE

MADRE is a local-first governed execution and agent-interoperability platform for AI-capable applications.

Start with [`MADRE.md`](MADRE.md). The active architecture is split into four focused documents under `docs/architecture/`; old semantic-Kernel Agent/WorkPlan orchestration is not part of the current system.

## Current implementation

The reference Python package provides:

- durable metadata-only work lifecycle with delayed eligibility, fairness, retry/cancellation and restart recovery;
- transient input/result handling so prompt/context/output bytes are not persisted by MADRE;
- deterministic security envelopes/algebra and traceable decision evidence;
- public Module/Agent/Skill/Workflow/Operation registry contracts;
- boundary-filtered discovery using current registered Module security facts;
- explicit Agent/Operation brokering with input and return boundary evaluation;
- replaceable capability adapters with security-aware deterministic selection and one OpenAI-compatible chat adapter isolated at the provider boundary.

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

Copy `madre.example.toml` to `madre.toml`, configure a capability endpoint/model, and set the API token environment variable declared by `token_env`.

```bash
export MADRE_API_TOKEN='replace-me'
uv run madre --config madre.toml
```

The HTTP transport exposes Module-manifest registration plus work submission/inspection/cancellation/retry and one-shot result consumption. The work submission model is architecture-neutral JSON material; the configured OpenAI-compatible chat adapter interprets chat-shaped JSON only after Kernel selects an admissible compatible capability.

The current HTTP bearer credential is an **installation-admin/local compatibility credential**, not a finished per-Module identity protocol. Do not distribute it to untrusted Modules as if possession implied a MADRE trust level. Native/future transports should authenticate a Module identity and then resolve that identity's registered boundary facts.

For delayed work, native Module integrations register an originator material provider through the in-process runtime API. The HTTP compatibility surface does not become private content storage merely to make delayed execution convenient.

## Privacy behavior

Durable runtime storage contains bounded references/identifiers, digests, immutable security envelopes, scheduling/lifecycle metadata and evidence. It contains no prompt/context/model-output payload columns and no arbitrary provider-error text.

Produced result bytes live only in process memory until consumed. Restart before consumption marks the delivery evidence as `lost`.

See `docs/architecture/MADRE-execution-contract.md` for exact semantics.
