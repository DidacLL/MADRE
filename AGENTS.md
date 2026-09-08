# MADRE Agent Harness

This file is the standing execution contract for coding agents working in this repository. Keep it short, stable and limited to recurring repository-wide operating rules.

MADRE is a personal, single-owner research and software project. Optimize for durable product progress, repository legibility and working behavior.

## Authority and precedence

Use the current explicit Owner request as the task goal and highest project-specific authority for that task.

If an Owner decision changes enduring product meaning, propagate it into the appropriate canonical repository document during the task so chat does not become shadow product authority.

Repository authority is otherwise:

1. `MADRE.md` — canonical product meaning and invariants;
2. the focused owner under `docs/architecture/` — detailed architecture for its responsibility;
3. `docs/implementation-baseline.md` — descriptive current implementation stage, gaps and validation state;
4. current code, tests and runtime evidence — implementation truth;
5. `README.md` — runnable setup and current usage;
6. Git history, PRs, issues and supporting material — provenance/evidence only.

History does not restore superseded product semantics.

## Context loading

Load only the context needed for the task.

- Product/architecture work: read `MADRE.md`, then the focused architecture owner for the responsibility being changed.
- Runtime convergence work: read `MADRE.md`, `docs/implementation-baseline.md`, the relevant architecture owner, then the smallest code/test surface that answers the task.
- Build/setup/usage work: start from `README.md`, configuration and the directly relevant code; load product architecture only if the task reaches it.
- Historical investigation: inspect history only when provenance or an unresolved question requires it.

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
3. Choose the simplest coherent implementation suited to the present system.
4. Implement the behavior end to end.
5. Validate it with evidence proportional to the changed surface and use failures to steer corrections.
6. Review the changed surface for correctness, unnecessary complexity and ownership drift.
7. Complete ordinary branch, commit and pull-request work when useful. Integration into the default branch remains Owner-controlled unless explicitly delegated.
8. Leave the repository sufficient for a fresh session to continue without reconstructing private chat history.

## Design discipline

Preserve the ownership model in `MADRE.md` while allowing implementation architecture to evolve from evidence.

A conventional architecture, security mechanism, policy, abstraction, service or dependency is not justified merely because it is common or considered best practice. Introduce it only when required by the current Owner request, canonical MADRE behavior, a demonstrated implementation need or concrete evidence.

Use suitable concrete dependencies directly when they make the current solution simpler, clearer or more reliable. Introduce a distinct abstraction when an observed responsibility becomes clearer, safer, more reusable or easier to test because of it.

When correcting architectural drift, preserve unrelated useful behavior. Fix the violated responsibility or contract rather than compensating by deleting the surrounding subsystem unless that subsystem itself conflicts with MADRE.

Treat names as part of software correctness and maintainability. Prefer terminology that lets a human reader infer responsibility without reconstructing hidden architectural meaning.

Resolve genuine product-meaning ambiguity with the Owner. Resolve implementation uncertainty through code, documentation, experiments and tests whenever those can provide the answer.

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
- recurring repository-wide agent behavior → `AGENTS.md`;
- implementation behavior → code and tests;
- historical rationale → Git/PR history or a focused design record only when the rationale itself remains operationally useful.

Do not add a standing harness rule merely because one agent made a mistake. First ask whether the correction belongs in product authority, architecture, baseline, code/tests/tooling or history.

A rule belongs here only when it is recurring, repository-wide and continues to reduce future reasoning. Remove rules that stop earning permanent context.

## Completion

Finish with a concise report of:

- what can now actually be used;
- what was executed and verified;
- any real blocker or unverified behavior;
- the next substantive behavior or genuine product decision, when one remains.
