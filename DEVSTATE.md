# MADREdev now

- Phase: Runtime landing definition ready
- Objective: Prove durable scheduled inference and restart recovery with deterministic fakes.
- State: Bootstrap complete locally; runtime implementation has not started.
- Branch: `agentic/madredev-bootstrap`
- Last verified evidence: 2026-09-06: local links, packet fields (256 words), trace references and git diff checks passed.
- Next actor: CLASSIC
- Next action: Implement the bounded first runtime slice.
- Owner attention: NONE
- Drift signal: Review rationale informs the plan; inherited tools, dashboards and reports do not establish requirements. Kernel scheduling and inference non-authority remain required.

## Current evidence

- Coordination has two owners: AGENTS.md for procedure, this file for state/handoff. The [slice contract](docs/runtime-first-slice.md) defines product behavior, not a third coordination surface.
- Removed the old agent instruction tree, duplicated state/dashboard, CI, build helpers and broad TeX ignore catalogue. No runtime, scheduler for development, service, framework or replacement dashboard was added.
- Product dossier source, its shared preamble and LICENSE are unchanged. The generated PDF was removed; no TeX build or tooling is part of this bootstrap. Python 3.13.3 with SQLite 3.49.1 was verified locally; the slice uses Python 3.11+ standard library only.
- Checks: direct Python inspection of local Markdown links, removed references, exact packet fields and dossier trace IDs; `git diff --check`. No runtime tests exist yet. TeX compilation was deliberately excluded from scope.
- The bootstrap/review rationale was used to form the current contract and packet; temporary input copies and obsolete local PDFs/auxiliary output were removed. No historical report or private dialogue remains in the active development surface.

## Current risk

The slice is defined, not implemented or validated. Process interruption, single-worker ownership and atomic journal/output commits need executable evidence. Recovery allows repeated side-effect-free inference after a crash; it does not promise exactly-once execution or power-loss safety. No product decision blocks implementation.

## Next-agent packet

```text
NEXT ACTOR: CLASSIC
WHY THIS ACTOR: The architecture and failure boundaries are explicit; this is bounded repository implementation.

TASK: Implement the first durable scheduled-inference slice, with deterministic fake backends only.
START FROM: The committed agentic/madredev-bootstrap branch (or main after its integration). Create agentic/durable-work-slice from that state; preserve unrelated changes.
READ: AGENTS.md; DEVSTATE.md; docs/runtime-first-slice.md. Read only the dossier sections cited by the slice if a contract needs clarification.

DO: Implement the slice in madre/ and tests/ using Python 3.11+ standard library and SQLite. Provide submit, one-item worker and read-only inspect CLI commands. Test lifecycle, scheduling, backend substitution, denied binding/scope, inert hostile output, worker exclusion and actual process-kill recovery. Document runnable CLI/test examples in README. Update DEVSTATE with actual evidence and the next bounded packet; commit the implementation on the topic branch.
DO NOT: Redesign product semantics; implement other runtime domains; add real providers, network, credentials, dependencies, agent frameworks, development tooling or extra instruction/state files.

DONE WHEN: Every acceptance scenario in docs/runtime-first-slice.md passes offline, including interruption before commit and during inference, with coherent persisted events and one terminal result. Inspection must not mutate state. README examples run from a fresh checkout with Python installed.
VALIDATE WITH: python -m unittest discover -s tests -v; the documented CLI examples on a temporary database; git diff --check. Report commands and outcomes, including platform limits.

ESCALATE IF: Repeated failures yield no new evidence, recovery/locking cannot satisfy the contract, or the dossier conflicts with it. Route reproduction/platform uncertainty to TERRA and contract/architecture uncertainty to ASTRA; stop only affected work.
OWNER DECISION NEEDED: NONE.
```
