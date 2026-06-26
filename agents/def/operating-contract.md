# MADRE Agent Operating Contract

This file owns stable agent procedure for this repository. Keep it short enough for every agent to read.

## Authority Order

1. Current user request.
2. `docs/tex/MADRE-AgenticSystem.tex` for MADRE product truth.
3. `docs/classmap-baseline.md` and `docs/usecases-baseline.md` for preserved
   classmap and scenario pressure.
4. `AGENTS.md` for routing.
5. This operating contract for repository procedure.
6. Task-specific files in `agents/def/**` or `agents/state/**`.

When sources diverge, keep product architecture in the TeX dossier, stable agent procedure here, and active work state under `agents/state/**`.

## Path Ownership

- `docs/tex/**`: canonical product dossier source.
- `docs/**`: human documentation and preserved system behavior references.
- `agents/def/**`: stable agent guidance.
- `agents/state/current.md`: current focus and next action.
- `agents/state/classmap-review/**`: active class-by-class classmap discussion workflow.
- `agents/state/**`: other mutable agent working state.
- `scripts/**`: local build and maintenance helpers.

Use the existing owner path that fits the information. Add a new Markdown file when it improves future agent workflow more than extending an existing owner.

## Working Rules

- Start with the fast path in `AGENTS.md`.
- Read the smallest source set that can answer or implement the current request.
- Prefer current canonical sources over duplicated summaries.
- Preserve behavior specifications, use cases, classmap discussion records, and system-design rationale when cleaning Markdown.
- Preserve philosophy, motivation, UML/class design, scenario pressure, and
  validation depth unless a packet review rejects the specific item.
- Use the MADRE baseline guideline as a review lens, not as replacement product
  text.
- Convert raw discussion into neutral current decisions or working-state notes before storing it.
- Keep stable rules positive, concise, and current.
- Keep implementation changes scoped to the requested artifact and its direct references.

## Editing Rules

- Identify the owning file before editing.
- Use targeted edits for existing files.
- Keep agent workflow text out of product deliverables.
- Keep product architecture text out of agent procedure files unless it is a compact derived orientation.
- Before deleting Markdown, retain useful behavior, use-case, or design-decision content in the owning active file.
- Rewrite bloated prose into ownership, responsibility, construction, data
  flow, scenario pressure, or validation language.
- Do not remove UML, class inventories, class definitions, or diagrams silently.

## Validation And Reporting

- Run the relevant local checks for the files touched.
- For Markdown-only work, check links, duplicate guidance, and stale references.
- Report changed files, deleted files, validation performed, and any remaining design uncertainty.
