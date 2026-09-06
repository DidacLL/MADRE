# MADREdev now

- Phase: Bootstrap and first runtime slice complete; integration pending.
- Objective: Put the simplified development environment and tested runtime slice on main.
- State: Published on `agentic/durable-work-slice`; not yet merged. Main was verified at pre-bootstrap `d4361d3` on 2026-09-06.
- Branch: `agentic/durable-work-slice` includes both the bootstrap and runtime implementation.
- Last verified evidence: All 11 runtime tests passed on Windows; Linux pass reported by the implementation executor. README CLI examples passed. Runtime/test code remains at `3c74bac`.
- Next actor: CLASSIC with GitHub access.
- Next action: Open or update one PR from this branch to main and complete integration under repository protections.
- Owner attention: Send the packet below to CLASSIC. No product decision or manual QA is needed.
- Drift signal: Integration is the next task. Do not start another feature, broad review, CI project or repeat platform validation.

## Current evidence and limit

The runtime durably queues immediate/delayed work, executes deterministic fake inference, records lifecycle/output and recovers after process interruption. Windows 10 build 19045 / Python 3.13.3 / SQLite 3.49.1 passed `python -m unittest discover -s tests -v`: 11 tests in 2.145s, including native worker exclusion and actual process termination. Linux/POSIX / Python 3.13.5 / SQLite 3.46.1 passed the same suite according to the implementation executor. Windows README immediate/delayed CLI examples also passed. No runtime portability fix or CI was needed.

Recovery covers process interruption on healthy local storage; attempts may repeat, with one committed terminal result. Power-loss safety and exactly-once inference are not claimed. No observed blocker remains for integration. Recheck remote state before acting because another executor may have integrated the branch.

## Copy this entire packet to CLASSIC

```text
NEXT ACTOR: CLASSIC with GitHub access.
WHY THIS ACTOR: Implementation and Windows/Linux validation are complete; only repository integration remains.

TASK: Integrate the MADREdev bootstrap and first durable runtime slice into main in DidacLL/MADRE.
START FROM: https://github.com/DidacLL/MADRE, latest agentic/durable-work-slice branch. Fetch current remote state; do not start from the old main implementation.
READ: AGENTS.md, DEVSTATE.md and docs/runtime-first-slice.md on that branch; inspect its diff against current main.

DO: Find an existing PR for this branch or create one targeting main. The branch includes both bootstrap cleanup and runtime implementation. Merge when repository permissions/protections allow. If already integrated, verify that and continue the handoff. Update DEVSTATE to reflect the result. Leave one copyable prompt for ASTRA to choose and define the next bounded runtime behavior; do not choose that scope yourself.
DO NOT: Add features, CI, documentation builds or review machinery; force-push; bypass protections; rerun the runtime suite for documentation-only changes.

DONE WHEN: Main contains the bootstrap and slice, and DEVSTATE clearly identifies the next actor and exact prompt; otherwise record the specific integration blocker and required action.
VALIDATE WITH: git diff --check and affected content/link checks. Reuse recorded runtime evidence unless integration changes runtime/test code or exposes a concrete behavioral uncertainty; then run python -m unittest discover -s tests -v.

ESCALATE IF: A conflict requires lifecycle/policy changes (ASTRA), or repository permissions require Owner action. Do not ask Owner to reconstruct the task.
OWNER DECISION NEEDED: NONE unless an actual permission restriction prevents integration.
```
