# MADRE Agent Router

Read this file first. Then read the smallest task-specific source set.

## Fast Path

1. `agents/def/operating-contract.md`
2. `agents/state/current.md`
3. One support file for the task:
   - product or architecture orientation: `agents/def/product-orientation.md`
   - classmap discussion: `agents/state/classmap-review/README.md`
   - TeX editing: `agents/LaTeXdocumentation.md`
   - documentation cleanup decisions: `agents/state/documentation-rewrite-ledger.md`

## Source Map

- `docs/tex/MADRE-AgenticSystem.tex`: canonical MADRE product source.
- `docs/**`: human-facing documentation and preserved behavior references.
- `agents/def/**`: stable agent operating guidance.
- `agents/state/**`: active agent working state and discussion continuity.

Keep new agent-facing guidance concise, current, and placed in the owning path.
Use the MADRE baseline guideline to judge existing text; do not paste it into
product documents as replacement prose.
