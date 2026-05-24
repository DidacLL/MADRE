# Technical Artifacts

Author: ag
State: read

This file is agent procedure only. It defines when subordinate technical specs or ADRs are valid; it does not define MADRE product architecture.

## Purpose

Technical specs and ADRs are not banned. They are forbidden only when they become unmanaged parallel authority. Valid artifacts derive implementation contracts from `docs/tex/MADRE-AgenticSystem.tex` or an explicit accepted task and never replace the canonical dossier.

Technical artifacts live under `docs/technical/**` by default. ADRs or decision records, when needed, live under `docs/decisions/**`. Generated analysis and reports live under `agents/reports/**` or `agents/backlog/**`, not under `agents/def/**`.

`agents/def/**` may define technical-artifact policy but must not contain the generated technical artifacts themselves.

## Valid Uses

- Short ADRs for one concrete decision.
- Technical specs for interfaces, state machines, contracts, traces, policies, tests, or scoped runtime behavior.
- Review or research notes only as evidence until promoted into an accepted artifact.

## Required Fields

- Owner file/path.
- Artifact type.
- Status: `generated`, `draft`, `proposed`, `accepted`, `consolidated`, `superseded`, or `deprecated`.
- Source dossier section.
- Scope.
- Non-goals.
- Decision or contract.
- Acceptance evidence.
- Traceability link.

## Status Semantics

- `generated`, `draft`, or similar status means verify before relying on it as final truth.
- It does not mean stop by default.
- A current user prompt may authorize using, revising, promoting, or superseding an artifact.
- Generated artifacts can be used as evidence or as a draft owner for the next task.
- Runtime code still requires an implementation definition.
- Do not require review of every generated artifact.

## Acceptance Rules

- No artifact may contradict `docs/tex/MADRE-AgenticSystem.tex`.
- ADRs must be short and decision-specific.
- Specs must be concise, contract-oriented, and define implementable contracts instead of narrative product vision.
- Runtime-facing artifacts must include expected evidence and at least one negative safe-failure path.
- Generated content must not self-mark as `consolidated`. Use `draft`, `proposed`, or `accepted` according to the current task context and evidence.
- If a technical artifact becomes product truth, summarize/promote it into the dossier or link it from the dossier according to repository convention.
