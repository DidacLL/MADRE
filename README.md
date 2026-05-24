# MADRE Agentic System

MADRE is a local-first, model-agnostic runtime architecture for governed agentic systems.

Its central claim is that useful agentic behavior should come from software architecture, not from treating a language model as the whole system. MADRE places replaceable model runtimes inside a governed kernel that owns context, policy, internal actions, memory, learning, audit, recovery, and user authority.

## Canonical Dossier

The authoritative MADRE architecture source is:

```text
docs/tex/MADRE-AgenticSystem.tex
```

That dossier defines product identity, architectural views, requirements, traceability, threat model, quality model, benchmark catalogue, glossary, and conceptual module and agent sketches.

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
