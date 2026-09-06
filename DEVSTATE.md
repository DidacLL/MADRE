# MADREdev now

- Phase: First durable runtime slice implemented
- Objective: Independently verify durable scheduled inference and restart recovery across supported local platforms before expanding runtime scope.
- State: The bounded Python/SQLite slice is implemented and passes its offline acceptance suite on Linux/POSIX.
- Branch: `agentic/durable-work-slice`
- Last verified evidence: 2026-09-06: 11 offline unit/integration tests passed, including real process-kill recovery and live-worker exclusion; documented submit/worker/inspect CLI lifecycle passed on a temporary SQLite database; `git diff --check` passed.
- Next actor: TERRA
- Next action: Reproduce the slice tests on Windows/Python 3.11+ and verify the `msvcrt` worker-lock branch and process-kill recovery without changing product semantics.
- Owner attention: NONE
- Drift signal: Keep the first slice bounded. Runtime lifecycle evidence now exists; do not use it as authorization to add providers, networking, RAG, frameworks or unrelated runtime domains.

## Current evidence

- `madre/` implements durable submission, persisted scheduling, deterministic policy/binding checks, replaceable deterministic fake inference, atomic terminal output/journal commits, explicit restart recovery and read-only inspection using Python standard library and SQLite only.
- The worker uses a nonblocking OS advisory sidecar lock for the canonical database path, held from before recovery until worker exit. POSIX `fcntl` behavior is exercised by the current suite; the Windows `msvcrt` branch is implemented but not exercised on this Linux runner.
- Recovery preserves the interrupted attempt as `interrupted` with unknown backend outcome and no published output. The recovery invocation does not retry that work; a later explicit worker invocation creates a new attempt. Tests kill a real subprocess after the running commit and after fake output generation but before completion commit.
- Policy-denied binding/scope performs zero backend calls. Backend exception and invalid-result paths fail without successful output. Hostile generated text remains inert persisted material.
- `inspect` uses SQLite read-only/query-only access, does not create missing storage, and is tested to leave the database bytes and modification time unchanged.
- Validation: `python -m unittest discover -s tests -v` -> 11 tests, OK; documented submit/worker/inspect commands on a fresh temporary database -> queued, completed, coherent journal/output; `git diff --check` -> clean.

## Current risk

The process/locking acceptance evidence is currently Linux/POSIX only. Windows locking uses the required `msvcrt` nonblocking advisory lock but remains empirically unverified. Recovery guarantees remain limited to process interruption on healthy local storage: attempts may repeat after a crash, there is one committed terminal result, and there is no exactly-once inference or power-loss guarantee.

## Next-agent packet

```text
NEXT ACTOR: TERRA
WHY THIS ACTOR: The runtime contract is implemented; the remaining bounded uncertainty is empirical Windows locking/process-kill behavior.

TASK: Independently validate the first durable scheduled-inference slice on Windows with Python 3.11+.
START FROM: `agentic/durable-work-slice` at its committed head; preserve unrelated changes.
READ: AGENTS.md; DEVSTATE.md; docs/runtime-first-slice.md; madre/runtime.py; tests/test_runtime_core.py; tests/test_runtime_recovery.py. Read dossier material only if an observed result conflicts with the slice contract.

DO: Run the full offline test suite on Windows; reproduce the documented CLI lifecycle on a temporary database; specifically verify `msvcrt` worker exclusion, OS lock release after process death, kill-after-running recovery and kill-after-inference-before-commit recovery. Record exact commands, Python/SQLite/Windows versions and evidence. Fix only reproducible portability defects that preserve the contract; rerun all tests after any fix and commit it on the same topic branch.
DO NOT: Redesign lifecycle/policy semantics; add providers, network, credentials, dependencies, frameworks, CI expansion or other runtime domains.

DONE WHEN: The complete acceptance suite passes on Windows, or a minimal reproducible platform defect is recorded with expected/observed behavior and no speculative workaround.
VALIDATE WITH: `python -m unittest discover -s tests -v`; documented CLI examples on a temporary database; `git diff --check`.

ESCALATE IF: Windows locking/recovery cannot satisfy the existing contract after evidence-driven attempts; route contract uncertainty to ASTRA and stop only the affected validation/fix.
OWNER DECISION NEEDED: NONE.
```
