# Implementation Baseline

This file describes current repository state. Product authority remains `MADRE.md`;
focused architecture is under `docs/architecture/`.

## Implemented security architecture

The Python SDK is a typed reference implementation of relation-local Security
Algebra normal forms.

- Sensitivity, Privacy, Integrity, Risk, and Autonomy are nominal public enums with
  integer wire ranks and runtime/static cross-facet rejection.
- Exact immutable SecurityObjects use role-neutral scope/revision identity and generic
  content/contract digests. They carry only applicable S/P/I facts.
- EffectProfile carries only exact Operation/profile identity, Risk, and Autonomy.
- OperationUse carries the selected profile plus actual additional observers,
  controllers, and optional immediate direct interaction.
- Disclosure, Control, and EffectExecution each have one fixed composition law and
  produce only feasible accepted forms. There is no injected evaluator or global
  score.
- DirectUserInteraction and freshly Kernel-bound DirectUserAction make one exact
  exceptional crossing possible. Durable work cannot carry this structure.
- SecurityEvidence stores scope facts, accepted forms, and derivations without
  becoming active numeric authority. A fixed reconstruction audit is used only for
  persistence verification, diagnostics, and tests.
- Ordinary/selection derivation preserves sensitivity closure; Module-owned transform
  can create a new sensitivity; explicit validation creates the only warranted
  Integrity-bearing material projection. Generated inference has no Integrity.

## Runtime and interoperability

Registry is a deterministic catalog/router with exact lookup and plain `list_*`
enumeration. It does not claim that listed descriptors are admissible. Complete
prospective security-aware discovery is deliberately deferred: it will require a typed
prospective use with source, profile, controller, executor-route, and destination
facts.

Capability privacy is declared by the adapter/installation. OpenAI-compatible omitted
privacy normalizes to UNKNOWN. Local/remote execution boundary never computes Privacy.

Transient inference and durable work compose candidate disclosure directly. Denied
candidates are decision evidence only. Durable material remains Module-owned,
reference-only, and JIT-resolved. Retry, cancellation, scheduling, originator fairness,
heavy-local admission, result loss, and unknown external-effect handling retain their
existing semantics.

Broker keeps explicit InvocationContext propagation, exact publication/endpoint
attachment, and lifetime-bound SDK clients. Agent calls compose actual input/output
disclosures. Operation calls atomically require accepted disclosure, control, and
effect-execution forms from OperationUse plus Kernel-known topology.

## Persistence

Generated SQLite state is recreated when its schema fingerprint changes. No
compatibility path exists for superseded development state.

Persistence contains reference-only work, exact SecurityObjects, canonical accepted
relation rows, derivations, relation-local decisions, broker causal evidence, digests,
and lifecycle metadata. Attempts carry the accepted disclosure relation identity.
There are no private payload columns, legacy history/transition columns, or output
Integrity.

## Package boundaries

`madre_sdk` imports public Kernel contracts. `madre_core` imports only `madre_sdk`.
Kernel imports neither SDK nor CORE. CORE remains an ordinary replaceable Module with
no security bypass.

## Current validation path

Run from a clean checkout:

```text
uv sync --locked
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```

Reinstall the built wheel into an isolated target and import `madre`, `madre_sdk`, and
`madre_core` from outside the checkout before publishing implementation changes.

## Deferred product assembly

The next product stage remains an assembled local usage path wiring Kernel, Broker,
CORE, and an independent SDK Module to real inference and one bounded Operation.
Module-owned material reacquisition across restart, supported setup, and ordinary
local interaction require real-machine acceptance. Do not confuse wheel/test evidence
with deployed usability, and do not expand into a universal planner, Agent memory,
policy engine, or dynamic security discovery while establishing that path.
