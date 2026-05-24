# Artifact Taxonomy

Author: ag
State: read

This file is stable agent procedure. It defines repository path ownership for MADREdev artifacts and does not define MADRE product architecture.

## Path Ownership

- `AGENTS.md`: root stable agent entrypoint.
- `docs/tex/**`: canonical human-facing product documentation and consolidated product truth.
- `docs/**`: human documentation, rendered deliverables, or public documentation assets only.
- `agents/def/**`: stable agent-procedure definitions and compact derived digests only. No generated task artifacts, run output, runtime-slice drafts, ADR drafts, or implementation contracts.
- `agents/state/**`: mutable agent working state, including current coordination snapshots, temporal implementation contracts, runtime-slice drafts, readiness gates, implementation notes, and working technical definitions.
- `agents/reports/**` or `agents/backlog/**`: generated review outputs, agent reports, temporary analysis, and task summaries if they must be tracked.
- `devboard/**`: advisory human status projection and dashboard maintenance only.
- `scripts/**`: repository maintenance, validation, and local build helper scripts. Not product runtime code.
- `dev/**`, `docs/raw/**`, `*.inform.md`: private/scratch/untracked input only.

## Forbidden Placement

- Do not put generated technical contracts in `agents/def/**`.
- Do not put temporal implementation contracts, runtime-slice drafts, or sprint working definitions under `docs/**`.
- Do not put product architecture in `agents/state/**`, `devboard/**`, agent reports, or backlog files.
- Do not put temporal review status into human product docs.
- Do not create a new directory when an existing ownership class fits.


## Promotion

- Agent working state may inform implementation.
- Agent working state may inform future human documentation.
- Product truth becomes human documentation only when consolidated into `docs/tex/MADRE-AgenticSystem.tex` or another explicitly human-facing doc.
- Promotion is based on current user instruction plus engineering readiness, not manual reading of every generated file.
