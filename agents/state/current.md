# MADREdev Coordination Snapshot

Author: ag
State: read

## Purpose

This file is an advisory coordination snapshot. It is never product authority and never blocks a current user-authorized task by itself.

## Current Baseline

MADREdev v0.2 corrective baseline is usable for preparing first-slice technical artifacts.

## Authority

- Product architecture: `docs/tex/MADRE-AgenticSystem.tex`.
- Agent procedure: `AGENTS.md`, `agents/workflow.md`, and `agents/def/**`.
- Current coordination: this file and `devboard/index.html`.
- Private/research input: `dev/**`, `docs/raw/**`, and `*.inform.md`.

## Runtime Implementation Rule

Runtime code requires the engineering readiness gate, not manual review of this file:

- Owned behavior definition.
- First-slice scope.
- Expected trace/evidence.
- Known negative paths.
- Explicit non-goals.

## Open Questions

- First runtime-slice technical artifact.
- Local hardware validation profile.
- Kernel boundary/language decision if not already settled.

## Next Agent Action

- If asked to implement, first check the readiness gate.
- If the gate is incomplete, create or patch the minimal owning technical artifact.
- If the gate is complete, proceed with implementation.
