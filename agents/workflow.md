# MADREdev Workflow

Author: ag
State: read

This file is agent procedure only. It does not define MADRE product architecture.

## Run Shape

1. Read the user request and identify the owned file before editing.
2. Read the smallest source set needed to answer the task.
3. Keep product documentation, agent procedure, mutable state, and private research separate.
4. Confirm the branch is not `main` before mutation.
5. Make the smallest coherent change that satisfies the request.
6. Check whether `devboard/index.html` still reflects human-visible status; update it only when status changed.
7. Validate only what the change can affect.
8. Summarize changed files, validation, and any remaining risk.

## Source Priority

1. User request for the current task scope.
2. `docs/tex/MADRE-AgenticSystem.tex` for product architecture.
3. `agents/def/product-orientation.md` as a non-authoritative product digest.
4. Root `AGENTS.md`, `agents/workflow.md`, `agents/LaTeXdocumentation.md`, and `agents/def/**` for agent procedure.
5. `agents/state/**` for current development status only.
6. `dev/**`, and `*.inform.md` as private or research input only.

If sources conflict, the user request controls the task, product architecture stays in the canonical dossier, and procedure stays in agent docs. Do not create a new document to avoid locating the owner.

## Work Classes

- TeX product docs: edit only consolidated product truth; compile when changed.
- Agent docs: keep instructions concise, operational, and non-authoritative for product architecture.
- `agents/state/**`: record current coordination only; avoid history dumps.
- Code: implement only behavior already defined at the right product or technical level.
- Dashboard: display human-visible status and evidence; do not make it an authority source or planning document.

## Branch Discipline

Before mutation, confirm the current branch is not `main`. If it is `main`, create or switch to an appropriate topic branch, or stop and report that a topic branch is required.

Do not merge or push to `main` directly.

## File Creation Check

Before creating a file, answer:

- What artifact type is this?
- Which path class in `agents/def/artifact-taxonomy.md` owns it?
- Is the target path mutable or stable?
- Is it generated, stable procedure, product documentation, technical contract, state, report, or dashboard?
- Which paths are explicitly forbidden?

Create or patch the minimal owning artifact in the path class defined by `agents/def/artifact-taxonomy.md`. Use `agents/def/technical-artifacts.md` for technical-contract rules.

## Implementation Definition Check

Runtime code may start when the current user request authorizes implementation and the owned behavior has a product or technical definition, scoped work, expected trace/acceptance evidence, and negative-path validation. If any of those are missing, create or patch the minimal owning artifact in the path class defined by `agents/def/artifact-taxonomy.md`; do not block solely on old coordination-state wording.

Agents must not wait for perfect fine-grained design, but must not invent runtime behavior without an owner artifact.

Old state files may reveal unresolved risks, but they are not blockers by themselves. Convert relevant risks into explicit readiness questions or technical-artifact tasks.

Before code work, answer:

- What behavior is being implemented?
- Which dossier/spec/ADR section defines it?
- What records or traces must prove it?
- What negative path must fail safely?
- What must not be implemented yet?

Short technical specs and ADRs are allowed when explicitly requested or needed to derive implementable contracts from the dossier. Use `agents/def/technical-artifacts.md`; keep them minimal and never let them become a parallel product dossier.

## Quality Checks

- No product/procedure scope leakage.
- No private wording or disposable review narrative in tracked public files.
- No permanent MADREdev personas, role-play modes, ceremony, unmanaged ADR/spec forest, or parallel product architecture.
- No stale implementation terms unless quoted as external concepts.
- No broad test work for documentation-only changes.
- No TeX formatting churn or hard line wrapping.

## Validation Defaults

- TeX changed: run `pdflatex` from `docs/tex`, twice if references or contents changed.
- Markdown changed: inspect links, scope, brevity, and leakage.
- Code changed: run tests tied to documented behavior and keep existing tests intact.
- Dashboard changed: inspect HTML/CSS for layout issues, update `devboard/README.md` only when maintenance rules change, and keep dashboard content synchronized with human-visible status.
- Runtime-slice drafts default to `agents/state/runtime-slices/runtime-slice-XXX.md`.
- Working ADR/decision notes default to `agents/state/decisions/D-XXX.md` unless explicitly promoted to human documentation.
- Human documentation updates go under `docs/**`; agent implementation working artifacts do not.
