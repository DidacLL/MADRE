# MADRE Design Memory

This directory preserves high-value Owner intent that should not be compressed into canonical architecture or left only in chat/Git history.

It is **not product authority**. `MADRE.md` and the focused documents under `docs/architecture/` remain normative. Design memory exists to preserve why those contracts exist, recurring misinterpretations, examples, constraints, and strong directions that are not yet frozen.

## Evidence discipline

Generated documents, commits, PRs, schemas and implementation artifacts may contain valuable reasoning, but they are not proof of Owner intent by themselves. Treat them as evidence of an attempted interpretation unless corroborated by direct Owner statements or later Owner-confirmed corrections.

Use these provenance labels in topic notes:

- **DIRECT OWNER** — explicitly stated by the Owner.
- **OWNER CONFIRMED** — synthesized wording that the Owner explicitly accepted.
- **CURRENT DIRECTION** — strong design direction still open to refinement.
- **GENERATED INTERPRETATION** — agent-produced realization; useful as forensic evidence, not Owner authority.
- **EXTERNAL RESEARCH** — comparison/example only.

Use these semantic statuses:

- **CONFIRMED** — durable current intent.
- **OPEN** — deliberately unresolved.
- **EXAMPLE** — illustrative, not normative.
- **SUPERSEDED** — earlier interpretation replaced by later understanding.
- **REJECTED** — known bad interpretation that should not be reintroduced without new Owner evidence.

## Retrieval map

Load only the relevant topic for the task.

| Working on | Read |
| --- | --- |
| CORE, default/fallback intelligence, fast interaction, UX | `core-and-interaction.md` |
| security, privacy, risk, sensitivity, trust, admissibility | `security-algebra.md` |
| public classes/contracts, SDK, Agents/Skills/Workflows/WorkPlans | `architecture-and-sdk.md` |
| inference mechanisms, provider/model selection, material lifecycle | `inference-and-execution.md` |
| ambiguous historical intent or recurring drift | relevant topic note first; use Git/old documents only for provenance |

## Reading rule

A topic note should answer five questions where applicable:

1. What underlying Owner problem/principle is being protected?
2. What generated interpretation was attempted?
3. What failed or drifted?
4. What later Owner correction refined it?
5. What is current consequence versus still-open design?

Do not normalize contradictions away. Preserve the distinction between an enduring principle and a superseded implementation mechanism.
