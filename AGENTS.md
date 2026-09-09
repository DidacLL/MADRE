# MADRE Agent Harness

This file is the standing execution contract for coding agents working in this repository. Keep it short, stable and limited to recurring repository-wide operating rules.

MADRE is a personal, single-owner research and software project. Optimize for durable product progress, repository legibility and working behavior.

## Authority and precedence

The current explicit Owner request is the task goal and highest project-specific authority for that task.

If an Owner decision changes enduring product meaning, propagate it into the appropriate canonical repository document during the task so chat does not become shadow product authority.

Repository authority is otherwise:

1. `MADRE.md` — canonical product meaning and invariants;
2. the focused owner under `docs/architecture/` — detailed architecture for its responsibility;
3. `docs/implementation-baseline.md` — descriptive current implementation stage, gaps and validation state;
4. current code, tests and runtime evidence — implementation truth;
5. `README.md` — runnable setup and current usage;
6. `docs/design-memory/` — non-normative product rationale, examples, constraints, research directions and ecosystem notes, loaded only when relevant;
7. Git history, PRs, issues and generated/supporting material — provenance/evidence only.

Generated documents, commits, PR descriptions, schemas and implementation artifacts are not proof of Owner intent merely because they were committed, merged or labelled canonical. Use them as implementation/history evidence and resolve product meaning from direct Owner guidance plus the current canonical corpus.

If the descriptive baseline disagrees with current executable behavior, code/tests/runtime evidence establish what actually works and the baseline must be corrected; executable behavior does not silently redefine product architecture.

## Context loading

Load only the context required by the task.

- Product/architecture work: read `MADRE.md`, then the focused architecture owner. Load the matching design-memory topic when product rationale, examples, constraints or an open direction materially affect the decision.
- Runtime convergence work: read `MADRE.md`, `docs/implementation-baseline.md`, the relevant architecture owner, then the smallest code/test surface that answers the task.
- SDK/Module work: read `MADRE.md`, `docs/architecture/MADRE-agent-interoperability.md`, the directly relevant design-memory topic, then the public contracts and smallest implementation surface.
- Build/setup/usage work: start from `README.md`, configuration and directly relevant code; load product architecture only if the task reaches it.
- Historical/refactor investigation: inspect `docs/refactors/` or Git history only when the task explicitly concerns that transition or provenance.

Expand context just in time through concrete dependencies, interfaces, failing behavior and tests. Prefer targeted repository search and direct inspection over broad ingestion.

## Unit of work and continuation

The explicit Owner request controls scope.

Otherwise, one development session should complete one substantive coherent behavior, or a tightly coupled set of behaviors, that leaves the repository working and materially advances MADRE.

When asked simply to continue:

1. read `docs/implementation-baseline.md`;
2. if it declares an active development stage with incomplete completion criteria, continue that stage;
3. otherwise inspect canonical product behavior for the next substantive unmet capability;
4. do not extend the most recently edited subsystem merely because it has natural follow-up work.

Keep working plans in the active session unless the plan itself becomes durable product or architecture knowledge.

## Engineering loop

For each task:

1. Identify the observable behavior or product decision required.
2. Inspect the nearest authoritative contract and current implementation evidence.
3. Load relevant design memory before filling a semantic gap from convention.
4. Choose the simplest coherent implementation suited to the present system.
5. Implement the behavior end to end.
6. Validate it with evidence proportional to the changed surface and use failures to steer corrections.
7. Review the changed surface for correctness, unnecessary complexity and ownership drift.
8. Complete ordinary branch, commit and pull-request work when useful. Integration into the default branch remains Owner-controlled unless explicitly delegated.
9. Leave the repository sufficient for a fresh session to continue without reconstructing private chat history.

## Design discipline

Preserve the ownership model in `MADRE.md` while allowing implementation architecture to evolve from evidence.

A conventional architecture, security mechanism, policy, abstraction, service or dependency is not justified merely because it is common or considered best practice. Introduce it only when required by the current Owner request, canonical MADRE behavior, a demonstrated implementation need or concrete evidence.

Do not confuse avoiding over-engineering with leaving architecture undefined. MADRE needs the smallest coherent, modular and human-readable set of contracts/classes required for its actual boundaries and SDK. Define cross-boundary concepts deliberately enough that later implementation does not fill a vacuum with unrelated conventions; keep Module-private semantics out of Kernel/framework ontology.

Use suitable concrete dependencies directly when they make the current solution simpler, clearer or more reliable. Introduce a distinct abstraction only when an observed responsibility becomes clearer, safer, more reusable or easier to test because of it.

When correcting architectural drift, preserve unrelated useful behavior. Fix the violated responsibility or contract rather than redesigning the surrounding subsystem without evidence that the wider structure is wrong.

Treat names as part of software correctness and maintainability. Prefer terminology that lets a human reader infer responsibility without reconstructing hidden architectural meaning.

When canonical contracts plus relevant design memory still leave a material product/architecture choice genuinely ambiguous, ask the Owner rather than silently importing a conventional answer. Ask the smallest focused question that exposes the concrete choice and consequence; do not offload large document reviews or routine implementation details to the Owner.

Resolve implementation uncertainty through code, documentation, experiments and tests whenever those can provide the answer.

## Development compatibility

MADRE has no installed user base or production data to preserve during active development. Do not implement migrations, backward-compatibility paths or preservation machinery solely to carry generated runtime state from previous development revisions forward unless the Owner explicitly asks for it. Prefer recreating incompatible generated local state.

This applies to generated development/runtime artifacts only. It does not make product definitions, architecture, source contracts or data-structure reasoning disposable.

## Evidence and review

Acceptance follows real behavior.

Use real execution for claims about real execution. Use controlled fixtures and mocks for deterministic edge cases, protocol behavior and failure handling.

Run validation proportional to the changed surface. Record exactly what was executed and what it established, including behavior the available environment could not exercise.

Use deterministic tooling before additional model reasoning when a compiler, test, formatter, type checker, runtime probe or repository query can answer the question directly.

Treat review as part of delivery. Add deeper or independent review when risk, uncertainty or blast radius makes it useful.

## Repository learning and harness maintenance

Put durable knowledge in the narrowest owner:

- product invariants → `MADRE.md`;
- detailed architecture → the focused `docs/architecture/` owner;
- current development stage/gaps → `docs/implementation-baseline.md`;
- product rationale, examples, constraints, exploratory directions and external-project lessons → `docs/design-memory/`;
- recurring repository-wide agent behavior → `AGENTS.md`;
- implementation behavior → code and tests;
- temporary migration/refactor instructions → a focused folder under `docs/refactors/`;
- historical/generated evidence → Git/PR history.

Before adding durable design memory, search the existing topic and update it when the new material refines the same concept. Prefer one maintained statement over parallel formulations.

Do not add a standing harness rule merely because one task exposed a local mistake. A rule belongs here only when it is recurring, repository-wide and continues to reduce future reasoning.

## Completion

Finish with a concise report of:

- what can now actually be used;
- what was executed and verified;
- any real blocker or unverified behavior;
- the next substantive behavior or genuine product decision, when one remains.
