# MADRE

This branch is the owner-aligned clean development foundation.

The previous active implementation was deliberately removed instead of migrated. Its Git history remains available as evidence, but it is not architectural authority.

Current active authority is intentionally small:

- `MADRE.md` — established cross-lane repository invariants;
- `AGENTS.md` — development/authority rules;
- `docs/architecture/lane-c-native-kernel.md` — Owner-approved Lane C implementation contract.

There is currently no production MADRE module in this foundation checkpoint. The root Gradle build exists only as neutral repository/bootstrap infrastructure so new lanes can start from a coherent tree without inheriting discarded APIs or tests.

Lane implementations branch from this exact checkpoint and must not infer semantic design from historical code.
