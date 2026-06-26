# MADRE Agentic System

MADRE is a local-first, model-agnostic runtime architecture for governed agentic systems.

Its central claim is that useful agentic behavior should come from software architecture, not from treating a language model, chat session, prompt stack, or tool wrapper as the system. MADRE places replaceable model runtimes inside a governed local runtime that owns context construction, module boundaries, internal actions, knowledge handling, learning promotion, trace, recovery, and user authority.

## Canonical Dossier

The authoritative MADRE architecture source is:

```text
docs/tex/MADRE-AgenticSystem.tex
```

That dossier defines product identity, architectural views, requirements, traceability, threat model, quality model, benchmark catalogue, glossary, and runtime domain model.

Supporting sources:

- `docs/classmap-baseline.md`: basic class and entity design pressure.
- `docs/usecases-baseline.md`: scenario pressure and use-case coverage.
- `agents/**`: agent routing and working-state guidance only; it is not product authority.

## Build

The architecture dossier depends on [P3CTeX](https://github.com/DidacLL/P3CTeX). Local development expects P3CTeX in MiKTeX and uses `pdflatex`.

Compile recently modified TeX files locally:

```powershell
.\scripts\compile-recent-tex.ps1
```

The script writes the deliverable PDF under `docs` and removes auxiliary files after a successful compile.

Manual single-document compile from `docs/tex`:

```powershell
pdflatex -interaction=nonstopmode -halt-on-error -file-line-error MADRE-AgenticSystem.tex
```

Run twice when references, labels, or the table of contents change.

## License

GPL-3.0. See `LICENSE`.
