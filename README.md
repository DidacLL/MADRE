# MADRE

MADRE is a local-first, model-agnostic runtime for applications that need foreground interaction, durable delayed reasoning, governed context and actions, inspectable knowledge, evaluated learning and recovery. Inference is replaceable; software retains authority.

- [Product dossier](docs/tex/MADRE-AgenticSystem.tex): the full product and research requirements, preserved as source material. No documentation toolchain work is required.
- [Implementation plan](docs/IMPLEMENTATION.md): an ordered path to a usable system and then the broader product. It is not a status tracker.
- [AGENTS.md](AGENTS.md): the small development workflow, usable by Classic without Astra/Terra or previous chat context.

The product dossier preserves the full scope; the implementation plan sequences the work. Determine actual capabilities from code and real usage, not progress reports. No special coordinator or private conversation is required.

## Start a Classic task

Use this same prompt from a new ChatGPT Classic conversation with access to DidacLL/MADRE:

```text
Work in DidacLL/MADRE from latest main on a topic branch. Read AGENTS.md and docs/IMPLEMENTATION.md. Inspect only relevant code and implement one cohesive behavior from the earliest unfinished milestone; start with milestone 1 if real local inference is still missing. Preserve the product meaning in the referenced dossier sections, but do not build its toolchain or reproduce its concepts as unnecessary frameworks. Make ordinary implementation choices yourself. Make the actual usage path work and verify it with available execution; report any part you could not run without claiming success. Complete routine commits/PR/integration when permitted. Do not create state files, handoff paperwork, roles or new process, and do not require Astra/Terra. Finish with the usable result and any concrete remaining limitation.
```

GPL-3.0. See [LICENSE](LICENSE).
