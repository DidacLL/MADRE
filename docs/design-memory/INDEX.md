# MADRE Design Memory

This directory preserves useful product reasoning that is too rich, illustrative or exploratory for the canonical architecture.

It is **not product authority**. `MADRE.md` and the focused documents under `docs/architecture/` are normative.

Design memory should help a fresh developer understand product intent without loading old conversations or implementation history.

## What belongs here

Keep information such as:

- product rationale and constraints;
- interaction/UX intuition;
- representative examples that help test an architecture;
- research directions not yet frozen;
- future product/module ideas;
- external-project observations worth remembering;
- implementation-language/engineering preferences that should guide, but not define, product architecture.

Migration notes, obsolete schemas and instructions for reconciling the current branch belong under `docs/refactors/`, not here.

## Evidence labels

Use simple labels only when useful:

- **OWNER** — directly stated or explicitly confirmed product intent;
- **AUDIT** — a mathematical/engineering derivation or adversarial check preserved as supporting evidence; it is not authority independently of the canonical document;
- **DIRECTION** — strong but still refinable direction;
- **OPEN** — deliberately unresolved;
- **EXAMPLE** — illustrative, not normative;
- **EXTERNAL** — outside-project observation only.

Generated historical documents are not Owner authority by themselves.

## Retrieval map

Load only the relevant topic.

| Working on | Read |
| --- | --- |
| CORE, default UI/UX, interaction Agent, resource-constrained UX | `core-and-interaction.md` |
| security algebra rationale, mathematical audit, representative cases, open valuation mechanism | `security-algebra.md` |
| SDK, public object model, Agents/Skills/Workflows/WorkPlans, developer tooling | `architecture-and-sdk.md` |
| inference mechanisms, provider ecosystem, durable material, advanced adapters | `inference-and-execution.md` |
| comparable projects / harness and ecosystem observations | `ecosystem-notes.md` |
| voice/audio Capability research, OpenWhispr, OS-native speech, ASR/TTS/VAD mechanism comparisons | `openwhispr-voice-ecosystem-audit.md` |

## Maintenance rule

Search the relevant topic before adding another statement. Refine the existing concept when possible instead of creating parallel formulations.

If a design-memory direction becomes a required product invariant, move the normative statement to the correct canonical document and leave only the rationale/example here.
