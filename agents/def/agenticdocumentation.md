# Agent-Facing Markdown

Author: ag
State: read

This file defines writing rules for agent-facing MADREdev Markdown. It is not product architecture.

## Principles

- Write for the next agent's token budget.
- Prefer commands, checks, and constraints over explanation.
- Put product concepts in the canonical dossier, not in agent procedure.
- Put current coordination in `agents/state/current.md`, not in permanent instructions.
- Put private or disposable notes in ignored development files.
- Agent-facing Markdown must declare whether it is stable procedure, advisory state, generated report, or technical contract; the directory must match that classification.

## Structure

- Start with purpose and authority status.
- Use short sections and flat bullets.
- Keep examples minimal and task-relevant.
- Link to the owning file instead of duplicating content.
- Remove obsolete instructions instead of accumulating exceptions.

## Do Not Add

- Permanent MADREdev personas, role-play modes, or agent identity files. Use temporary task passes only.
- Sprint ceremony or PR ritual.
- Uncontrolled parallel ADR/specification forests. Short technical specs and ADRs are allowed only when explicitly derived from the canonical dossier, owned, linked, and accepted through review.
- Generated task outputs, reports, runtime-slice specs, ADR drafts, or implementation contracts under `agents/def/**`.
- Long history summaries.
- Private conversational wording.
- Product architecture restated as procedure.

## Temporal Language

- Avoid phrases that create stale blockers through manual-review, correction-state, baseline-readiness, or human-acceptance wording unless the current user explicitly asks for that status.
- Prefer neutral operational labels: "Open question," "Known risk," "Requires definition," "Readiness gate not satisfied," or "Next agent action."
- A missing definition can block implementation; an old status sentence cannot.

## Review Checklist

- Is this file the owner of the instruction?
- Can a shorter version preserve the same operational value?
- Does any sentence create product authority outside `docs/tex/MADRE-AgenticSystem.tex`?
- Does it duplicate another agent file?
- Does it contain private, stale, or disposable process material?
