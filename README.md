# MADRE Agentic System

State: read

MADRE is a local-first, model-agnostic runtime architecture for governed agentic systems.

Its central claim is that useful agentic behavior should come from software architecture, not from treating a language model as the whole system. MADRE places replaceable model runtimes inside a governed kernel that owns context, policy, internal actions, memory, learning, audit, recovery, and user authority.

## Canonical Dossier

The authoritative MADRE architecture source is:

```text
docs/tex/MADRE-AgenticSystem.tex
```

That dossier defines product identity, architectural views, requirements, traceability, threat model, quality model, benchmark catalogue, glossary, and conceptual module and agent sketches.

## MADREdev

MADREdev is the repository's agentic engineering support surface. It is separate from MADRE product architecture.

Agents should start with `AGENTS.md` and `agents/def/product-orientation.md`; the latter is a digest and not product authority.

State and dashboard files are advisory coordination aids; current user instructions and the canonical dossier control work.

Development changes should be made through topic branches, PRs, and CI; `main` is not the normal work branch.

```text
AGENTS.md                         Root agent entrypoint
agents/workflow.md                MADREdev workflow
agents/LaTeXdocumentation.md      LaTeX editing guidance
agents/def/agenticdocumentation.md Agent-facing Markdown rules
agents/def/artifact-taxonomy.md   Repository path ownership
agents/def/product-orientation.md Non-authoritative product digest
agents/def/repository-governance.md Branch, PR, and main-protection rules
agents/def/technical-artifacts.md Controlled technical-artifact policy
agents/state/current.md           Mutable development state, not authority
devboard/index.html               Static read-only development dashboard
```

Open `devboard/index.html` directly in a browser to inspect current development status. It does not require a server.

## Build

The architecture dossier depends on [P3CTeX](https://github.com/DidacLL/P3CTeX). Local development expects P3CTeX in MiKTeX and uses `pdflatex`.

Compile recently modified TeX files locally:

```powershell
.\scripts\compile-recent-tex.ps1
```

The script writes auxiliary files under `docs/tex/auxfiles`, writes deliverable PDFs under `docs`, and removes auxiliary files after successful compilation.

Manual single-document compile from `docs/tex`:

```powershell
pdflatex -interaction=nonstopmode -halt-on-error -file-line-error MADRE-AgenticSystem.tex
```

Run twice when references, labels, or the table of contents change.

## Status

MADRE is currently an architecture-stage software product. Runtime implementation must conform to the canonical dossier rather than provider-specific conventions or unreviewed construction assumptions.

## License

GPL-3.0. See `LICENSE`.
