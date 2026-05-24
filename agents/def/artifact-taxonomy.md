# Artifact Taxonomy

Author: ag
State: read

This file is stable agent procedure. It defines repository path ownership for MADREdev artifacts and does not define MADRE product architecture.

## Path Ownership

- `AGENTS.md`: root stable agent entrypoint.
- `agents/def/**`: stable agent-procedure definitions and compact derived digests only. No generated task artifacts, sprint output, or runtime-slice drafts.
- `agents/workflow.md`: stable operational workflow.
- `agents/state/**`: advisory coordination snapshots only. Never authority.
- `devboard/**`: advisory human inspection projection only. Never authority.
- `docs/tex/**`: canonical product documentation and user-facing consolidated truth.
- `docs/technical/**`: implementation-enabling technical contracts derived from the dossier or current accepted task context.
- `docs/decisions/**`: short proposed or accepted decision records when required.
- `scripts/**`: repository maintenance, validation, and local build helper scripts. Not product runtime code.
- `agents/reports/**` or `agents/backlog/**`: generated review outputs, agent reports, temporary analysis, and task summaries if they must be tracked.
- `dev/**`, `docs/raw/**`, and `*.inform.md`: private, scratch, or untracked input only.

## Forbidden Placement

- Do not put generated technical contracts in `agents/def/**`.
- Do not put product architecture in `agents/state/**`, `devboard/**`, agent reports, or backlog files.
- Do not put temporal review status into user-facing product docs.
- Do not create a new directory when an existing ownership class fits.

## Promotion

- Generated review or report material may inform a technical contract.
- A technical contract may inform implementation after the readiness gate is satisfied.
- Product truth must be consolidated into `docs/tex/MADRE-AgenticSystem.tex` or explicitly linked according to repository convention.
- Promotion is based on current user instruction plus engineering readiness, not manual reading of every generated file.
