# MADREdev now

- Phase: First durable runtime slice validated on Windows and Linux
- Objective: Integrate the bounded slice without expanding runtime or development scope.
- State: Windows validation passed without runtime or test changes; ready for repository integration.
- Branch: `agentic/durable-work-slice`
- Last verified evidence: 2026-09-06: Windows suite passed all 11 tests in 2.145s; README immediate/delayed CLI examples passed; diff check clean.
- Next actor: CLASSIC
- Next action: Integrate the validated slice through the normal repository workflow.
- Owner attention: NONE
- Drift signal: Validation follows the changed surface. Documentation updates do not justify full runtime test runs or CI expansion.

## Current evidence

- Runtime and test code validated at `3c74bac57586e10713fe474860459732730ec502`; subsequent changes only record evidence and clarify documentation validation.
- Windows 10 build 19045, Python 3.13.3, SQLite 3.49.1: `python -m unittest discover -s tests -v` -> 11 tests, OK, 2.145s. The native `msvcrt` branch executed, including a second live worker returning busy without mutation and OS lock release after process death.
- Real subprocess termination before submission commit, after running commit and after inference before terminal commit passed. Recovery retained interrupted attempts, exposed recovered state, and produced one terminal result only after a later explicit worker invocation.
- README commands using a temporary database: submit -> queued/acknowledged; worker -> completed; inspect -> completed with `FAKE_A:hello MADRE` labelled generated. The delayed 2030 example remained queued across a fresh worker process.
- Implementation executor reported the same 11-test suite passing on Linux/POSIX, Python 3.13.5, SQLite 3.46.1. That run exercised `fcntl`; this validation independently exercised Windows.
- No runtime portability fix was needed. No CI was added. Documentation changes receive content/link and diff checks only.

## Current risk

No observed platform blocker remains for this slice on the tested Windows/Linux environments. Recovery is limited to process interruption on healthy local storage: attempts may repeat, with one committed terminal result; exactly-once inference and power-loss safety are not claimed. Integration must preserve the validated behavior.

## Next-agent packet

```text
NEXT ACTOR: CLASSIC
WHY THIS ACTOR: The slice has implementation and platform evidence; the remaining work is bounded repository integration.

TASK: Integrate the first durable scheduled-inference slice through the repository's normal PR workflow.
START FROM: Fetch origin and use the latest agentic/durable-work-slice branch. Preserve unrelated changes and inspect the current main/PR state before acting.
READ: AGENTS.md; DEVSTATE.md; docs/runtime-first-slice.md; README.md; the branch diff against main.

DO: Create or update one focused PR for the slice and its handoff. Use the recorded Windows/Linux evidence for unchanged runtime/test code. Resolve only straightforward integration conflicts. Complete integration when repository permissions and protections allow, then update DEVSTATE with the integration result and a bounded next task. Route selection of the next runtime behavior to ASTRA rather than inventing new scope.
DO NOT: Bypass protections; force-push; expand runtime behavior; add CI, documentation builds or review machinery. Do not repeat the runtime suite for documentation-only changes.

DONE WHEN: The slice is integrated with a correct DEVSTATE, or a concrete repository restriction is recorded with a directly executable next action.
VALIDATE WITH: git diff --check and checks for changed links/content. Run python -m unittest discover -s tests -v if integration changes runtime/test code or creates relevant behavioral uncertainty; report actual outcomes and reuse existing evidence otherwise.

ESCALATE IF: Conflicts require lifecycle/policy changes, current main invalidates the evidence, or a repository restriction prevents integration. Route architecture uncertainty to ASTRA; involve OWNER only for required authorization or product intent.
OWNER DECISION NEEDED: NONE.
```
