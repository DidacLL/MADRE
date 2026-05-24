# Technical Artifacts

Author: ag
State: read

This file is agent procedure only. It defines when subordinate technical specs or ADRs are valid; it does not define MADRE product architecture.

## Purpose

Technical specs and ADRs are not banned. They are forbidden only when they become unmanaged parallel authority. Valid artifacts derive implementation contracts from `docs/tex/MADRE-AgenticSystem.tex` or an explicit accepted task and never replace the canonical dossier.

## Valid Uses

- Short ADRs for one concrete decision.
- Technical specs for interfaces, state machines, contracts, traces, policies, tests, or first-slice runtime behavior.
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
- It does not mean stop until a human manually edits this file.
- A current user prompt may authorize using, revising, promoting, or superseding an artifact.
- Generated artifacts can be used as evidence or as a draft owner for the next task.
- Runtime code still requires the readiness gate to be satisfied.
- Do not require the human to manually read every generated artifact.

## Acceptance Rules

- No artifact may contradict `docs/tex/MADRE-AgenticSystem.tex`.
- ADRs must be short and decision-specific.
- Specs must define implementable contracts, not narrative product vision.
- Runtime-facing artifacts must include expected evidence and at least one negative safe-failure path.
- Generated content must not self-mark as `consolidated`, when the user confirms with explicit or implicit acceptance of previous artifacts it would be marked as draft, proposed or accepted depending on the user prompt and the agent criteria based on context. For that reason none agentic file would be never considerated 100% truth. Only when user is confident with that artifact could promote it as `consolidated`making it unmutable, but this is not expect to happen so often.
- If a technical artifact becomes product truth, summarize/promote it into the dossier or link it from the dossier according to repository convention.
