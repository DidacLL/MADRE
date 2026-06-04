# Java Walking Skeleton Runtime Slice

State: draft
Owner path: `agents/state/runtime-slices/java-walking-skeleton.md`
Artifact type: runtime implementation definition
Source authority: `docs/tex/MADRE-AgenticSystem.tex`, Runtime Domain Model

## Behavior Scope

Implement a first Java walking skeleton for the MADRE runtime v5 class map. The slice covers in-memory construction and execution of the minimal request flow:

`MADREKernel.dispatch(ReasoningRequest)` routes to a registered `ReasoningModule`; module-owned behavior creates or handles a plan, invokes a `MADREAgent` through an `AgentRequest`, runs a `MADREAction`, returns an `Artifact`, and records `RuntimeEvent` entries in `RuntimeJournal`.

The implementation uses Java 21, Maven Wrapper, JUnit tests, and an optional `de.kherud:llama:4.2.0` inference adapter behind `MADREAction.actionKind = INFERENCE`.

## Non-Goals

- No persistence engine, database schema, HTTP API, UI, service discovery, or background scheduler thread.
- No concrete policy engine beyond minimal `PolicyDecision` and boundary checks.
- No learning lifecycle, knowledge promotion workflow, or model-provider abstraction beyond the optional llama.cpp adapter.
- No implementation of deferred v5 concepts as domain classes.

## Contract

- Runtime domain code lives under `runtime/java`.
- Public domain names mirror the 16 v5 class boxes.
- Value concepts remain enums or simple value records.
- Relationship references may exist as Java IDs or references only where needed to make the walking skeleton executable.
- Llama-backed inference is optional and must not be required for default tests.

## Expected Evidence

- `runtime/java/mvnw test` passes without a local model file.
- Tests cover routing, scheduling, passive task behavior, workflow non-execution, policy block, boundary join, artifact non-promotion, journal append/trace, and optional llama adapter loading when `MADRE_LLAMA_MODEL` is set.

## Negative Path

- A raw `ReasoningRequest` cannot be scheduled directly by the kernel.
- A blocked policy decision prevents action execution.
- A generated `Artifact` does not become a `KnowledgeRecord` automatically.
- A `RuntimeEvent` records history but does not authorize or perform transitions.

## Validation Method

Run:

```powershell
.\runtime\java\mvnw.cmd test
```

or from a Unix-like shell:

```sh
./runtime/java/mvnw test
```
