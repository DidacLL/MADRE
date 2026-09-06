# MADREdev now

- Phase: Bootstrap and first durable runtime slice complete; main integration blocked by stale required checks.
- Objective: Integrate the validated bootstrap/runtime baseline, then let ASTRA choose and define the next bounded runtime behavior.
- State: PR #6 (`agentic/durable-work-slice` -> `main`) is open and otherwise mergeable. `main` remains at pre-bootstrap `d4361d3` because its active ruleset requires three status checks that this branch cannot produce after the authorized bootstrap removed the old CI.
- Branch: `agentic/durable-work-slice` contains the bootstrap cleanup, runtime implementation/tests and this integration evidence.
- Last verified evidence: 11 runtime tests passed on Windows and Linux/POSIX; README CLI examples passed. PR #6 is six implementation/bootstrap commits ahead of the original main before this state-only commit, with no main-side divergence or runtime conflict.
- Next actor: OWNER for one repository-protection action, then CLASSIC resumes PR #6 integration.
- Next action: Remove the stale required status checks from ruleset `Protect Main` (ID `16803699`) so the intentionally removed CI is not required for merge; do not add a bypass. Then retry PR #6 using an allowed linear-history merge method.
- Owner attention: REQUIRED. The connected GitHub actor cannot bypass the active ruleset, and this task explicitly forbids bypassing protections or restoring CI merely to satisfy obsolete checks.
- Drift signal: Do not add features, CI, documentation builds or review machinery to solve this integration blocker.

## Integration blocker and evidence

PR #6 is mergeable at the content level. `main` has no commits beyond the branch merge base, so no lifecycle/policy conflict exists. A normal merge-commit attempt was rejected because main requires linear history; switching to squash reached the real blocker: GitHub reports all three required checks as expected.

Ruleset `Protect Main` (`16803699`) requires `Repository policy check`, `TeX compile check`, and `Markdown and link sanity`, all tied to integration ID `15368`. The branch intentionally removed `.github/workflows/ci.yml` during bootstrap, in accordance with the current development contract, and the PR head has no status records. The ruleset reports no bypass actors and `current_user_can_bypass: never`. Restoring CI or weakening protection through an executor would exceed this task.

Runtime code/tests were not changed during integration work, so the recorded Windows/Linux runtime evidence remains authoritative and the suite was not rerun.

## Post-integration ASTRA packet

Use this packet only after PR #6 and the final integration-state DEVSTATE update are on `main`.

```text
NEXT ACTOR: ASTRA
WHY THIS ACTOR: The bootstrap and first executable runtime slice are integrated and validated; the next uncertainty is architectural scope selection, not mechanical implementation.

TASK: Choose and define the next bounded executable MADRE runtime behavior after the durable scheduled-inference slice. Produce the implementation contract and exact executor handoff; do not implement it.
START FROM: Current `main` after PR #6 integration.
READ: AGENTS.md; DEVSTATE.md; docs/runtime-first-slice.md; the current `madre/` and `tests/` surface. Read only dossier sections needed to compare candidate next behaviors and establish traceability.

DO: Identify the smallest behavior that materially advances the MADRE runtime from the evidence already proved. Compare plausible candidates against the canonical dossier and current runtime boundaries, choose one, and define behavior, non-goals, minimal contracts, authority/boundary effects, negative path, recovery semantics, acceptance evidence and dossier traceability. Leave one exact bounded implementation packet for the appropriate executor.
DO NOT: Implement code; create a broad roadmap; assume a provider, framework or runtime domain without architectural evidence; redesign the proven first slice merely to make the next packet easier.

DONE WHEN: One next runtime behavior is selected and specified tightly enough that its executor does not need to reconstruct architecture or invent product semantics.
VALIDATE WITH: Cross-check the proposed contract against the canonical dossier, the first-slice contract and current runtime/tests. No runtime suite is required unless code is changed.

ESCALATE IF: Product intent remains materially ambiguous (OWNER) or selection depends on unresolved empirical/platform evidence (TERRA).
OWNER DECISION NEEDED: NONE unless ASTRA finds a genuine product-intent ambiguity.
```
