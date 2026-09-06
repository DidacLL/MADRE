# MADRE Agent Harness

MADRE is a personal, single-owner research and software project. Optimize for durable product progress, repository legibility and working behavior.

## Sources of truth

Use the current user request as the task goal.

Use `MADRE.md` for product meaning and ownership boundaries.

Use the current code, tests and runtime evidence for implementation truth.

Use repository history and supporting documents when provenance or a concrete unresolved question makes them relevant. Recover current product semantics from `MADRE.md`.

## Context loading

Start with `MADRE.md` and the smallest code surface that can answer the task.

For runtime implementation, read the short `docs/implementation-baseline.md` for the existing foundation and next behavior; `README.md` owns runnable setup. Keep enduring conclusions in their repository owner so fresh sessions need no PR/chat reconstruction.

Expand context just in time through concrete dependencies, interfaces, failing behavior and tests. Prefer targeted repository search and direct inspection over broad ingestion.

Keep persistent context only where it reduces future reasoning: durable product meaning in `MADRE.md`, recurring repository-wide operating knowledge here, and software behavior in code and tests.

## Unit of work

The explicit user request controls scope.

Otherwise, one development session should complete one substantive coherent behavior, or a tightly coupled set of behaviors, that leaves the repository working and materially advances MADRE.

Larger goals are realized through successive coherent changes. Finish the current behavior end to end, leave its evidence in the repository, and let later sessions continue from that evidence.

Keep working plans in the active session unless the plan itself becomes durable product or architecture knowledge.

## Engineering loop

For each task:

1. Identify the observable behavior or product decision required.
2. Inspect the current implementation and nearest relevant evidence.
3. Choose the simplest coherent implementation and concrete technologies suited to the present system.
4. Implement the behavior end to end.
5. Validate it with evidence proportional to the changed surface and use failures to steer corrections.
6. Inspect the changed surface for correctness, unnecessary complexity and consistency with the ownership model in `MADRE.md`.
7. Complete ordinary branch, commit and pull-request work when useful. Treat integration into the default branch as an Owner-controlled action unless the current request explicitly delegates it.
8. Leave the repository in a usable state from which another fresh session can continue.

When the user asks simply to continue, inspect the current code against the product acceptance path in `MADRE.md` and implement the next coherent behavior that most directly advances a useful MADRE system.

## Design decisions

Preserve the ownership model in `MADRE.md` while allowing implementation architecture to evolve from evidence.

Use suitable concrete dependencies directly when they make the current solution simpler, clearer or more reliable. Introduce a distinct abstraction when an observed responsibility becomes clearer, safer, more reusable or easier to test because of it.

A concrete language, database, library, transport, framework or provider integration may become a stable implementation dependency when the working system benefits from that choice. Product meaning remains defined by `MADRE.md`.

Keep application semantics in the application, runtime execution semantics in MADRE, and provider or tool mechanics at the capability boundary.

Resolve genuine product-meaning ambiguity with the Owner. Resolve implementation uncertainty through code, documentation, experiments and tests whenever those can provide the answer.

## Evidence and review

Acceptance follows real behavior.

Use real execution for claims about real execution. Use controlled fixtures and mocks for deterministic edge cases, protocol behavior and failure handling.

Run validation proportional to the changed surface. Record exactly what was executed and what the result established, including behavior that the available environment could not exercise.

Use deterministic tooling before additional model reasoning when a compiler, test, formatter, type checker, runtime probe or repository query can answer the question directly.

Treat review as part of delivering the behavior. Add deeper or independent review only when the risk, uncertainty or blast radius makes it materially useful.

## Repository learning

Let recurring evidence improve the repository at the narrowest durable owner:

- product semantics → `MADRE.md`;
- cross-task agent operating knowledge → `AGENTS.md`;
- implementation behavior → code and tests;
- historical rationale → Git history, commit/PR context or a focused design record when that rationale remains operationally useful.

Persistent guidance should reduce future context and repeated reasoning. Keep a rule while it continues to earn that cost.

## Completion

Finish a task with a concise report of:

- what can now actually be used;
- what was executed and verified;
- any real blocker or unverified behavior;
- the next substantive behavior or genuine product decision, when one remains.
