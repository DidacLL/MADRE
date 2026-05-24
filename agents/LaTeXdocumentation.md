# MADRE LaTeX Guidance

Author: ag
State: generated

This file is agent procedure only. Product truth belongs in `docs/tex/MADRE-AgenticSystem.tex`.

## Editing Rules

- Keep LaTeX user-facing, professional, and consolidated.
- Do not add development state, review history, open decisions, or agent procedure.
- Do not hard-wrap prose at an arbitrary column; rely on editor word wrap.
- Prefer targeted edits over generated rewrites.
- Preserve diagrams, tables, labels, and traceability unless the task explicitly changes them.

## Shared Support

- Use `docs/tex/shared/preamble.tex` for shared macros, colors, table helpers, TikZ helpers, and page style.
- Prefer existing P3CTeX and preamble commands before adding new local formatting.
- Add repeated table, diagram, or style logic to the shared preamble only when it is genuinely reused.
- P3CTeX is expected in the local TeX installation; do not vendor it into this repository.

Useful local helpers currently include `\MADRETABLE`, `\MADRETABLEv`, `\MTABLE`, `\MROW`, `\MHEAD`, `\LIST`, `\madreChapter`, `\code`, TikZ helpers, and MADRE logo/name commands.

## Validation

Compile from `docs/tex`:

```powershell
pdflatex -interaction=nonstopmode -halt-on-error -file-line-error MADRE-AgenticSystem.tex
```

Run twice when references, labels, diagrams, or the table of contents change.

If TeX was not edited, do not run TeX validation just to prove unrelated work.
