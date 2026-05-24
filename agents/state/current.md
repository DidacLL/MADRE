# MADREdev Coordination Snapshot

Author: ag
State: read

## Purpose

This file is an advisory coordination snapshot. It is never product authority and never blocks a current user-authorized task by itself.

## Current Baseline

MADREdev requires repository-governance repair before preparing runtime-slice technical artifacts.

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

- Repair MADREdev governance: artifact taxonomy, branch/PR discipline, CI checks, and dashboard/state alignment.
- After the taxonomy is accepted by current task context, create runtime-slice technical contracts under `docs/technical/**`.
- If later asked to implement runtime code, first check the readiness gate and the owning technical contract.
