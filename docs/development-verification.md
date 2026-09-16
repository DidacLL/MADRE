# Development verification policy

MADRE is a single-developer project. Verification exists to protect product behavior and public contracts; it is not a development objective in its own right.

## Default development loop

Use the smallest verification that can falsify the change being made.

- Run focused unit/contract tests while implementing a slice.
- Run the affected Gradle project tests before committing.
- Run root `check` when a change crosses project boundaries or before handing off a coherent slice.
- Prefer local verification because it provides immediate feedback and does not serialize development behind hosted runners.
- Do not wait for, repeatedly poll, or shepherd GitHub Actions after every push. CI is an asynchronous signal, not a per-commit scheduler.

A coherent change may be committed and pushed after appropriate local verification. Exact-head extended CI is required only when the acceptance claim itself depends on those hosted product journeys or when the Owner explicitly requires it.

## Automatic CI

Pull requests run one lightweight Linux validation job for normal source changes:

```text
./gradlew --no-daemon --build-cache check
```

Documentation-only changes do not start this workflow. Concurrency cancellation ensures a newer push supersedes an older in-progress run.

Two expensive cross-platform workflows also have narrow path-sensitive PR triggers because they protect boundaries for which lightweight `check` is insufficient:

- `Extended SDK developer acceptance` runs when public SDK/application/independent-consumer or installed-owner acceptance paths change;
- `Extended native owner package` runs when native package, application, Kernel, shipped CORE, SDK or native owner-conversation acceptance paths change.

These are targeted acceptance gates, not a policy that every PR or commit must recreate every extended journey.

## Extended verification

The following workflows remain available through `workflow_dispatch`; some also run automatically under the narrow PR path triggers described above:

- `Extended SDK developer acceptance` — builds/tests independent SDK and reasoning consumers outside the checkout, then exercises installed-product Module/reasoning lifecycle, semantic owner/PUBLIC boundaries and replacement behavior on Windows and Linux. Its owner-interaction assertion uses ordinary plain text rather than CORE-private commands.
- `Extended reasoning owner configuration` — owner reasoning-provider configuration and lifecycle journey on Windows and Linux.
- `Extended native owner package` — native application/package build and packaged owner journey on Windows and Linux. Its sustained owner-conversation acceptance exercises ordinary foreground response, persisted multi-turn context, durable continuation, restart recovery and natural Agent-approved follow-up without exposing the CORE-private Operation/Material protocol.

Use extended verification when one of these conditions applies:

1. the implementation materially changes the corresponding boundary;
2. a local or lightweight-CI failure suggests a platform-specific or packaging defect;
3. preparing a release or an explicit owner-requested acceptance checkpoint;
4. the Owner explicitly asks for the extended proof.

Do not run all extended workflows merely because a commit exists, a PR head changed, or a handoff is being written.

## Failure handling

A failing relevant check is engineering evidence and must be understood. Fix genuine regressions before layering dependent work on them. But a queued, running, cancelled, stale, or unrelated hosted workflow is not itself a reason to stop implementation.

Do not add new mandatory CI stages without a demonstrated defect class that cannot be covered cheaply by an existing local or lightweight check. Prefer improving tests and local reproducibility over adding orchestration.

For durable reasoning/restart acceptance, account for the real configured retry/recovery semantics rather than shortening the test until it no longer exercises persistence. A graceful shutdown may interrupt an in-flight attempt and re-queue it according to the existing retry policy; the restarted process must remain alive long enough for that persisted work to become eligible and complete.

## Release posture

Cross-platform packaging and end-to-end installation proofs remain release/acceptance concerns even where selected path changes trigger them automatically. Windows and Linux are first-class hosts, and owner-interaction/package changes must preserve the same semantic application behavior on both.
