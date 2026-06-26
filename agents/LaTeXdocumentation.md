# MADRE LaTeX Guidance

Use this file for TeX editing and validation tasks. Product truth belongs in `docs/tex/MADRE-AgenticSystem.tex`.

## Editing

- Keep LaTeX user-facing, professional, and consolidated.
- Preserve diagrams, tables, labels, traceability, and document structure unless the task changes them.
- Use targeted edits for existing sections.
- Rely on editor word wrap for prose.
- Use diagrams when they communicate architecture or runtime boundaries more clearly than prose.
- Use `longtable` for tables that may exceed most of a page.
- Keep repeated table, diagram, or style logic in shared support when it is genuinely reused.
- Preserve UML and class definitions unless a specific review decision rejects
  them.
- Prefer concise engineering claims over decorative architecture prose.
- Keep agent debate, review notes, and uncertainty ledgers out of TeX
  deliverables.

## Shared Support

- `docs/tex/shared/preamble.tex` owns shared macros, colors, table helpers, TikZ helpers, and page style.
- Prefer existing P3CTeX and preamble commands before adding local formatting.
- P3CTeX is expected in the local TeX installation.

Useful local helpers include `\MADRETABLE`, `\MADRETABLEv`, `\MTABLE`, `\MROW`, `\MHEAD`, `\LIST`, `\madreChapter`, `\code`, TikZ helpers, and MADRE logo/name commands.

## Validation

Compile recently modified TeX files:

```powershell
.\scripts\compile-recent-tex.ps1
```

The script uses local MiKTeX `pdflatex`, writes auxiliary files to `docs/tex/auxfiles`, writes PDFs to `docs`, and removes auxiliary files after a successful compile.

Manual single-document compile from `docs/tex`:

```powershell
pdflatex -interaction=nonstopmode -halt-on-error -file-line-error MADRE-AgenticSystem.tex
```

Run twice when references, labels, diagrams, or the table of contents change.
