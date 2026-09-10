# Speculative Research Branch Discipline

Branch: `research/speculative-design-memory`

Status: **branch-local operating policy — non-authoritative research only**

This branch exists to preserve speculative ecosystem research, external-project audits, experiments in architectural reasoning, and other potentially useful evidence while MADRE's active development branches continue to refactor and evolve the repository.

It is deliberately **not** a parallel product, architecture, implementation, or integration branch.

## Purpose

Use this branch when research is worth preserving but is not yet justified as canonical MADRE architecture or implementation work.

Typical material includes:

- external ecosystem/project audits;
- alternative mechanism comparisons;
- provider/runtime surveys;
- speculative implementation techniques;
- deferred architectural observations;
- failure/complexity lessons from other systems;
- evidence that may later support, challenge, or redirect a MADRE decision.

Research preserved here remains advisory evidence only. It does not become Owner intent, product authority, SDK contract, Kernel behavior, Module semantics, or implementation baseline merely because it is committed.

## Change discipline

Prefer **additive, self-contained research files** with unique descriptive paths under `docs/design-memory/`.

Each research document should, where relevant:

- state that it is non-authoritative;
- identify the MADRE branch/commit snapshot against which it was written;
- pin important external repository/version snapshots where mutable upstream behavior matters;
- distinguish observed evidence from MADRE-specific interpretation;
- record primary sources so conclusions can be revalidated later;
- avoid assuming that deferred recommendations will still fit future MADRE architecture.

### Do not modify from this branch

Unless the Owner explicitly requests otherwise, speculative research must not edit:

- `MADRE.md`;
- `AGENTS.md`;
- `README.md`;
- `docs/architecture/**`;
- `docs/implementation-baseline.md`;
- shared design-memory indexes or maintained summaries;
- production/runtime source code;
- tests;
- build/package manifests;
- CI/workflow configuration;
- migration/refactor instructions.

The objective is to keep the branch's effective diff structurally independent from active repository development.

## Relationship to active development

Do not merge active development branches into this branch merely to keep it current.

Research documents should instead record the MADRE snapshot they were evaluated against. If later work needs current repository truth, re-evaluate the research against the then-current canonical branch rather than mechanically rebasing old conclusions into authority.

The branch may therefore become far behind `main` or another future canonical branch. That is acceptable. Its purpose is preservation, not continuous integration.

## Promotion back into MADRE

When research becomes relevant to current product work:

1. start from the **current canonical MADRE branch**, not from this research branch;
2. reread the current `MADRE.md`, focused architecture owner, implementation baseline, and directly relevant code/tests;
3. revalidate mutable external facts where necessary;
4. decide which conclusions still apply;
5. copy or cherry-pick only the still-useful additive research document/commit when preserving provenance is useful;
6. promote any actual product invariant or architectural decision separately into its correct canonical owner;
7. do not merge stale research conclusions wholesale into architecture or implementation.

A future merge should therefore be content-selective rather than branch-authoritative.

## Conflict-minimization rule

The preferred shape of this branch is:

```text
canonical snapshot
    + standalone research document A
    + standalone research document B
    + standalone research document C
    + ...
```

Avoid edits to files that active development also owns. This keeps later integration close to a sequence of additive file introductions and minimizes semantic as well as textual merge conflicts after large refactors.

## Scope boundary

This policy governs only `research/speculative-design-memory`.

It should normally **remain on this branch** rather than being promoted into canonical MADRE documentation. If the research workflow itself later becomes a recurring repository-wide requirement, define that separately from current repository truth and add the smallest appropriate rule to the canonical harness only after explicit justification.
