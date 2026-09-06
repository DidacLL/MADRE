# MADRE Agent Harness

This file is the standing execution contract for coding agents working in this repository. Keep it short, stable and grounded in recurring project needs.

## Sources of truth

Use the current user request as the task goal.

Use `MADRE.md` for product meaning and ownership boundaries.

Use the current code, tests and runtime evidence for implementation truth.

Repository history and supporting documents are evidence to inspect when a task needs them; current product semantics come from `MADRE.md`.

## Context loading

Start with `MADRE.md` and the smallest code surface that can answer the task.

Expand context through concrete dependencies, failing behavior, interfaces or tests. Prefer targeted repository search and direct file inspection over broad ingestion.

Keep standing context focused. A durable product conclusion belongs in `MADRE.md`; a recurring repository-wide agent instruction belongs here; a recurring software failure is best captured by an executable test or check.

## Engineering loop

For each task:

1. Identify the observable behavior or product decision the task requires.
2. Inspect the current implementation and the nearest relevant evidence.
3. Choose the smallest coherent change that produces the required behavior end to end.
4. Use concrete technologies and abstractions that make the current solution simpler, clearer or more reliable.
5. Validate the changed behavior with the strongest practical evidence available.
6. Complete ordinary branch, commit, pull-request and merge work when permissions and repository rules allow it.
7. Leave the repository itself sufficient for the next agent to continue from latest `main`.

When the user asks simply to continue, inspect the current code against the product acceptance path in `MADRE.md` and advance the earliest behavior that is not yet demonstrated.

## Design decisions

Preserve the ownership model in `MADRE.md` while allowing implementation architecture to evolve from evidence.

Prefer direct use of a suitable concrete dependency over an abstraction whose only purpose is hypothetical replaceability. Introduce a distinct abstraction when an observed responsibility becomes clearer, safer, more reusable or easier to test because of it.

Keep application semantics in the application, runtime execution semantics in MADRE, and provider/tool mechanics at the capability boundary.

Resolve genuine product-meaning ambiguity with the Owner. Resolve implementation uncertainty through code, documentation, experiments and tests whenever those can provide the answer.

## Evidence

Acceptance follows real behavior.

A successful real execution path is evidence for real execution. Controlled fixtures and mocks are useful for deterministic edge cases, protocol behavior and failure handling.

Run validation proportional to the changed surface. Record exactly what was executed and what the result established.

Use deterministic tooling before additional model reasoning when a compiler, test, formatter, type checker, runtime probe or repository query can answer the question directly.

## Repository learning

Let recurring evidence improve the repository at the narrowest durable owner:

- product semantics → `MADRE.md`;
- cross-task agent operating knowledge → `AGENTS.md`;
- implementation behavior → code and tests;
- historical rationale → Git history, commit/PR context or a focused design record when the rationale itself remains operationally important.

Persistent guidance should reduce future context and repeated reasoning. Keep a rule only while it continues to earn that cost.

## Completion

Finish a task with a concise report of:

- what can now actually be used;
- what was executed and verified;
- the next substantive missing behavior or genuine product decision, if one remains.
