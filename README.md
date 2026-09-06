# MADRE Agentic System

MADRE is a local-first, model-agnostic runtime architecture for governed agentic systems. Its kernel owns scheduling, context, policy, bounded actions, audit/recovery and user authority; inference remains replaceable and non-authoritative.

- [DEVSTATE.md](DEVSTATE.md): current position and copyable next-agent task.
- [AGENTS.md](AGENTS.md): stable development procedure.
- [First runtime slice](docs/runtime-first-slice.md): bounded implementation contract.
- [Canonical product dossier](docs/tex/MADRE-AgenticSystem.tex): product terminology, architecture, requirements and traceability.

## First executable runtime slice

The repository now contains the bounded durable scheduled-inference slice in `madre/`. It uses only Python 3.11+ standard library modules and SQLite. The only inference backends are deterministic fakes; generated output is persisted as generated material and is never interpreted as commands, policy, routing or knowledge.

From a fresh checkout with Python installed, run the complete test suite:

```text
python -m unittest discover -s tests -v
```

A minimal CLI lifecycle uses a disposable local database:

```text
python -m madre --db .madre-demo.sqlite submit --text "hello MADRE"
python -m madre --db .madre-demo.sqlite worker
python -m madre --db .madre-demo.sqlite inspect
```

`submit` acknowledges only after the queued work and journal event commit. `worker` acquires the per-database OS advisory lock, performs restart recovery, executes at most one eligible item, and exits. `inspect` opens the existing database read-only and never initializes storage or performs recovery. Delete `.madre-demo.sqlite` and `.madre-demo.sqlite.worker.lock` when finished.

Delayed work requires an explicit timezone-aware `not_before` value, for example:

```text
python -m madre --db .madre-demo.sqlite submit --text "later" --mode delayed --not-before 2030-01-01T12:00:00Z
```

Available fake bindings are `fake-a`, `fake-b`, and `fake-fail`. Any unknown binding or scope other than `local-only` is durably blocked before backend execution.

GPL-3.0. See [LICENSE](LICENSE).
