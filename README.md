# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first runtime for independent applications that need AI work to run immediately or be scheduled, persisted, resumed and inspected later. Applications keep ownership of their domain data, knowledge, learning and workflows; MADRE owns the runtime execution lifecycle and the boundary to replaceable inference/tool capabilities.

The current authoritative product definition and implementation order are in [MADRE.md](MADRE.md). Development rules for coding agents are in [AGENTS.md](AGENTS.md).

## Continue development with ChatGPT Classic

Use one normal coding conversation with GitHub access:

```text
Work in DidacLL/MADRE from latest main. Read AGENTS.md and MADRE.md, then inspect only the code relevant to the next missing behavior. Implement one cohesive behavior from the earliest incomplete implementation stage in MADRE.md, including routine branch/commit/PR/merge work when permitted. Keep applications responsible for domain data, knowledge, learning and workflows; keep MADRE focused on runtime scheduling, execution, recovery and capability boundaries. Prefer the smallest working implementation, verify the real usage path with the execution available to you, and do not create project-state/handoff documents, CI machinery or speculative infrastructure. Finish with what works, what you actually verified, and the next substantive missing behavior.
```

Git history preserves superseded architecture experiments and development-process attempts; they are not active instructions.

GPL-3.0. See [LICENSE](LICENSE).
