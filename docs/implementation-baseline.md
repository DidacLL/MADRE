# Implementation Baseline

This file records current implementation truth. Product authority remains
`MADRE.md`.

## Public SDK

`madre_sdk` currently provides:

- nominal ordered Sensitivity, Privacy, Integrity, Risk, and Autonomy carriers;
- exact immutable SecurityScope and structural SecuritySurface values;
- intrinsically valid Disclosure, Control, and EffectExecution relations;
- immutable Module, Agent, Skill, Workflow, Operation, EffectProfile, Capability, and
  Material definitions;
- typed Material contracts, handles, and output specifications;
- typed Capability properties/queries and ExecutionRequest;
- segregated execution, Module behavior, Agent behavior, and Operation behavior ports;
- ModuleRuntime for binding one declarative definition to private behavior.

Definitions serialize through Pydantic JSON without behavior objects or extension
bags. Material payload is the only intentionally generic content value.

## Runtime

`madre.Kernel.execute` selects one physical Capability using typed execution
properties, constructs the exact Disclosure through the SDK algebra, executes the
adapter, and returns new ordinary Material.

An incompatible Capability surface raises SecurityMismatch before adapter execution.
The mismatch is not persisted and Kernel does not search security candidates.

The shipped Module behavior in `madre_core` uses the same ExecutionService and
ModuleRuntime boundary as any other Module. It receives Capability output as Material
and passes it to a Module-owned interpreter. No CORE-specific public contract exists.

The OpenAI-compatible adapter fixes configured model and streaming properties after
copying the input payload, so payload bytes cannot override them. It accepts an
externally configured HTTP client and contains no credential model.

## Durable work and catalog

Reference-only durable work retains:

- Module identity;
- typed Capability query;
- MaterialHandle;
- output MaterialSpecification;
- eligibility, priority, timeout, correlation, and idempotency;
- attempt lifecycle, selected Capability identity/boundary, result digest/size, and a
  compact failure code.

Input and output payloads remain transient. SQLite contains no tables for security
objects, relations, derivations, decisions, or broker events. A schema fingerprint
recreates obsolete development storage rather than carrying compatibility machinery.

The catalog stores ModuleDefinition JSON and lists contained definitions. It does not
route, filter, or grant anything.

## Verified behavior

The targeted suite proves:

- max Sensitivity, min Privacy, and min Integrity structural composition;
- order independence and idempotence for identical scopes;
- immutable failure of a nonmatching addition;
- UNKNOWN Privacy versus a non-applicable facet;
- absence of a live-user Disclosure exception;
- exact Control and EffectExecution equations;
- independent new Material at S4, S3, and S2 from an unchanged S5 source;
- Module Sensitivity and Agent Privacy derived from actual members;
- declarative definition JSON round-trip;
- ordinary Module -> Kernel -> Capability -> Material -> Module interpretation;
- no Capability execution after a Disclosure mismatch;
- local/remote execution boundary does not infer Privacy;
- durable execution and catalog persistence without private payload or security-state
  tables.

Run:

```text
uv run --locked pytest
uv run --locked ruff check .
uv run --locked ruff format --check .
uv run --locked mypy
uv build --python .venv --no-build-isolation
```

## Genuine next work

The repository now has the first useful in-process Module execution path. It does not
yet provide an assembled end-user interaction application, a concrete bounded
Operation execution journey, restart-capable Module material reacquisition, or a
declarative XML loader.

Those behaviors should be added only through concrete Module needs. They must not
reintroduce generic agent brokering, a security service, a universal behavior engine,
or CORE-specific SDK concepts.
