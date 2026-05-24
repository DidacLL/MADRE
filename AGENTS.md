# AGENTS.md - MADRE

State: read

## Root Router

`docs/tex/MADRE-AgenticSystem.tex` is the canonical MADRE product authority. It owns product terminology, architecture, requirements, traceability, threat model, quality model, benchmark catalogue, glossary, and conceptual sketches.

`docs/**` is human documentation and deliverable space.

`agents/def/**` is stable agent instruction space.

`agents/state/**` is mutable agent working state.

Do not work directly on `main`; use a topic branch for repository mutation unless the current user explicitly requests an emergency direct edit.

## Fast Check

1. `AGENTS.md`
2. `agents/def/operating-contract.md`
3. Task-specific support file only if needed

Use `agents/def/product-orientation.md` only as a compact, non-authoritative product digest. Use `agents/LaTeXdocumentation.md` only for TeX editing tasks.

Do not infer durable operating rules from advisory state, dashboards, generated reports, private notes, or review wording.
