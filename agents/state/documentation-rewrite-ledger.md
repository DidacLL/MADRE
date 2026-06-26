# MADRE Documentation Rewrite Ledger

This ledger is agent-facing. It records cleanup decisions for the documentation rewrite.

## Source Rule

- Use the MADRE baseline guideline as a review lens.
- Do not paste guideline prose into product documentation as replacement text.
- Treat existing documentation as suspicious but valuable.
- Preserve philosophy, motivation, UML/class design, requirements, use cases,
  diagrams, and validation depth unless a packet review rejects a specific item.

## Accepted Rewrite Rules

- Rewrite bloated prose into ownership, responsibility, construction, data flow,
  scenario pressure, or validation language.
- Keep the language model subordinate to software architecture.
- Keep local-first and model-agnostic commitments explicit.
- Keep `SystemAgent` module-bound.
- Keep `Workflow` planning-only and `ReasoningTask` non-executable.
- Keep context as selected material, not instruction authority.
- Keep trace and policy records observational unless concrete construction
  semantics justify more.

## Rejection Triggers

- Chat session, prompt stack, tool wrapper, or cloud provider described as the
  runtime foundation.
- Model-owned memory, routing, planning, action, context, continuity, or truth.
- Policy, approval, evidence, audit, or trace described as a substitute for valid
  construction.
- UML or class additions that do not map to stable identity, ownership,
  lifecycle, responsibility, relation, or construction rule.
- Prose that sounds like engineering but does not constrain implementation.
