# MADRE Agent Harness

MADRE is a single-Owner research and software project. Keep the active repository small, explicit and reversible.

## Authority

1. The current Owner request is the task goal.
2. Explicitly Owner-approved lane architecture documents are implementation authority for their lane.
3. `MADRE.md` records only established cross-lane invariants.

Historical code, tests, PRs, commits, issue discussions and familiar software or AI-platform patterns are evidence only. Do not reconstruct MADRE semantics from them.

## Clean-foundation rule

This branch deliberately removed the previous generated implementation rather than migrating it. Git history is the archive. Do not restore discarded architecture merely to recover APIs, tests or directory structure.

Do not invent missing semantic MADRE details. Future semantic/runtime lanes must be driven by their own Owner-approved contracts.

## Lane C

`docs/architecture/lane-c-native-kernel.md` is authoritative for Lane C.

Lane C owns already-physical Work, the native Kernel, worker supervision and the small physical Java client only. The Kernel must remain ignorant of Module, Agent, Operation, Material, Compound, ReasoningRequest, Security Algebra, CORE, Workflow, semantic continuation and semantic persistence.

Do not merge to `main` without explicit Owner instruction.
