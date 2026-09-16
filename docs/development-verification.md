# Development verification policy

MADRE is a single-developer project. Verification exists to protect product behavior and public contracts; it is not a development objective in its own right.

## Default development loop

Use the smallest verification that can falsify the change being made.

- Run focused unit/contract tests while implementing a slice.
- Run the affected Gradle project tests before committing.
- Run root `check` when a change crosses project boundaries or before handing off a coherent slice.
- Prefer local verification because it provides immediate feedback and does not serialize development behind hosted runners.
- Do not wait for, repeatedly poll, or shepherd GitHub Actions after every push. CI is an asynchronous signal, not a per-commit gate.

A coherent change may be committed and pushed after appropriate local verification. "Exact-head CI is green" is not a standing requirement for continuing development.

## Automatic CI

Pull requests run one lightweight Linux validation job:

```text
./gradlew --no-daemon --build-cache check
```

Documentation-only changes do not start this workflow. Concurrency cancellation ensures a newer push supersedes an older in-progress run.

The default PR workflow intentionally does not build native installers, publish verification artifacts, run Windows matrices, recreate independent installation journeys, or execute every historical acceptance proof.

## Extended verification

The following workflows remain available through `workflow_dispatch` for explicit use when their evidence is relevant:

- `Extended SDK developer acceptance` — builds/tests the independent SDK consumer outside the checkout, then exercises the installed-product Module lifecycle, semantic owner/PUBLIC boundaries, failed-replacement rollback, and non-destructive manual-placement compatibility for both Module and reasoning artifacts on Windows and Linux.
- `Extended reasoning owner configuration` — owner reasoning-provider configuration and lifecycle journey on Windows and Linux.
- `Extended native owner package` — native application/package build and packaged owner journey on Windows and Linux.

These workflows preserve expensive product evidence without making it part of the continuous development loop.

Use extended verification when one of these conditions applies:

1. the implementation materially changes the corresponding boundary;
2. a local or lightweight-CI failure suggests a platform-specific or packaging defect;
3. preparing a release or an explicit owner-requested acceptance checkpoint;
4. the Owner explicitly asks for the extended proof.

Do not run all extended workflows merely because a commit exists, a PR head changed, or a handoff is being written.

## Failure handling

A failing relevant check is engineering evidence and must be understood. Fix genuine regressions before layering dependent work on them. But a queued, running, cancelled, stale, or unrelated hosted workflow is not itself a reason to stop implementation.

Do not add new mandatory CI stages without a demonstrated defect class that cannot be covered cheaply by an existing local or lightweight check. Prefer improving tests and local reproducibility over adding orchestration.

## Release posture

Cross-platform packaging and end-to-end installation proofs are release/acceptance concerns. They remain valuable, especially because Windows and Linux are first-class hosts, but they should be exercised deliberately rather than continuously.
