# AGENTS.md - MADRE

State: read

## Authority

`docs/tex/MADRE-AgenticSystem.tex` is the canonical MADRE product dossier. It owns product terminology, architecture, requirements, traceability, threat model, quality model, benchmark catalogue, glossary, and conceptual sketches.

Use development notes only to understand the task. They do not define product architecture. `agents/def/product-orientation.md` is a digest for fast checks, not authority.

Temporal coordination files, dashboard labels, generated status markers, and previous review notes are advisory only. They must never override the current user request or the canonical dossier. Do not block work solely because an older file carries temporal review-status wording. If the current user request authorizes progress, evaluate the actual engineering readiness gate.

## Scope Boundaries

- Product documentation: `docs/tex/**`. User-facing, consolidated, additive, and architecture-owned.
- Agent procedure: `AGENTS.md`, `agents/workflow.md`, `agents/LaTeXdocumentation.md`, `agents/def/**`. Stable operational guidance only; not a catch-all destination for generated task output.
- Mutable development state: `agents/state/**`. Current coordination notes only; never product authority.
- Private research and scratch material: `dev/**`, `docs/raw/**`, `*.inform.md`. Input evidence only and not tracked authority.

Do not mix MADRE product behavior with MADREdev procedure. Do not add process history, private wording, permanent MADREdev personas, sprint ceremony, or unmanaged parallel product architecture.

## Working Rule

Before editing, establish ownership:

| Question | Required answer |
|---|---|
| Which file owns this change? | Name the exact file and section. |
| Why this file? | Tie it to the authority and scope boundaries above. |
| What must not change? | Name adjacent files or sections out of scope. |
| Is deletion involved? | Inspect for diagrams, class sketches, or source material first. |
| Is public documentation involved? | Keep it product-facing; move procedure to agent-only files. |

Before creating a file, classify it by artifact taxonomy and name the owner path.

Never create generated task artifacts under `agents/def/**`.

Do not work directly on `main`; prepare changes on a topic branch and integrate through PR/CI unless the current user explicitly requests an emergency direct edit.

Use the smallest coherent edit. Preserve the canonical dossier unless the task explicitly changes product architecture.

Runtime code may start only when the owned behavior has an accepted product/technical definition, a first-slice scope, expected trace evidence, and negative-path validation.

State fields describe evidence quality and lifecycle; they are not a manual approval workflow unless the current user request explicitly makes them one.

## Task Passes

Use passes as temporary checks, not permanent roles:

- Architecture consistency: keep dossier concepts, terms, diagrams, requirements, risks, quality, benchmarks, and glossary aligned.
- Documentation surface: keep public docs product-oriented and free of process notes.
- Agent procedure: keep agent-only instructions concise and operational.
- Diagram review: use diagrams only when clearer than prose for architecture or runtime boundaries.
- QA: scan for stale vocabulary, scope leaks, broken links, and validation failures.

## Agent Workflow

Fast-check order:

1. `AGENTS.md`
2. `agents/def/product-orientation.md`
3. `agents/def/artifact-taxonomy.md`
4. `agents/def/repository-governance.md`
5. `agents/workflow.md`
6. Relevant procedure files in `agents/def/**`

Use `agents/workflow.md` for MADREdev procedure, `agents/def/artifact-taxonomy.md` for path ownership, `agents/def/repository-governance.md` for branch and PR rules, `agents/def/technical-artifacts.md` for controlled specs/ADRs, and `agents/def/agenticdocumentation.md` for concise agent-facing Markdown rules.

For TeX edits, use `agents/LaTeXdocumentation.md` and compile from `docs/tex`:

```powershell
pdflatex -interaction=nonstopmode -halt-on-error -file-line-error MADRE-AgenticSystem.tex
```

Run twice when references, labels, or the table of contents change.
