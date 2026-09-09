# Next Run — Kernel Alignment to Final Corpus

Status: temporary refactor brief; not product authority.

Start from the latest `architecture/modular-agentic-clean-slate` in `DidacLL/MADRE`.

PR #40 has already been squash-merged into that branch. Treat it as provenance only. Create a fresh implementation branch/PR for this refactor; do not work from the old `architecture/reconcile-standalone-system` branch.

## Read first

1. `AGENTS.md`
2. `MADRE.md`
3. `docs/architecture/MADRE-platform-architecture.md`
4. `docs/architecture/MADRE-execution-contract.md`
5. `docs/architecture/MADRE-agent-interoperability.md`
6. `docs/architecture/MADRE-security-algebra.md`
7. `docs/implementation-baseline.md`
8. `docs/refactors/2026-09-final-corpus-alignment/README.md`
9. only then the smallest current code/test surface needed for the refactor

Design memory is optional/JIT; load a topic only when its rationale materially affects a decision.

## Mission

Finish the Kernel foundation alignment in one coherent refactor pass as far as repository truth permits, without implementing Module/CORE semantics inside Kernel.

### 1. Durable material

Remove the remaining pushed/cached durable-material path.

Every accepted durable item must persist only a `MaterialHandle` and execution/security metadata. Resolve Module-owned bytes JIT only when the attempt is genuinely ready, verify them, use them transiently and discard them. Restart/retry reacquire material.

### 2. Generic transient inference

Expose the non-durable inference primitive required by the execution contract.

It creates no `WorkRecord`, carries no recovery promise and is semantically neutral. Do not add Kernel `fast_lane`, acknowledgement or UX routing logic.

### 3. Mechanism request/selection

Converge public requests/descriptors enough to represent hard constraints versus preferences/fallbacks over the execution dimensions currently required by the contract.

Preserve deterministic selection and existing useful provider/local behavior. Do not build a speculative optimizer.

### 4. Security object seam

Move the public/runtime schema toward subject-bound `SecurityID`/`SecurityObject` propagation rather than assuming one universal envelope shape.

Preserve integrity/binding, durable carried identities, registry independence and deterministic evidence.

Do **not** design the final algebra formula in this run. If current execution requires a temporary compatibility evaluator, isolate it clearly as current implementation behavior so it can be replaced without changing every caller again.

### 5. SDK-ready boundary

Leave public protocols/interface segregation clean enough for the next stage to implement the SDK without importing Kernel storage, scheduler, transport-framework or provider internals.

Do not implement the full SDK or CORE in this run unless the Owner changes scope.

## Non-goals

Do not add:

```text
Agent memory/session framework
semantic WorkPlan persistence in Kernel
universal Workflow engine
CORE implementation
Kernel fast-response semantics
ACL/role/grant/clearance authorization
unrestricted Agent shell/Internet surface
generic credential manager
provider-specific architecture in generic Kernel
speculative multi-agent ontology
```

## Security handling

Treat `SecurityID` as the stable link from a participating MADRE object to its bound `SecurityObject`.

Different subject kinds may carry different applicable dimensions. Do not infer Sensitivity from trust/isolation or vice versa.

The final algorithm remains open; do not silently fill it with conventional security practice.

## Acceptance

Before ending:

- satisfy the `docs/implementation-baseline.md` Kernel completion criteria as a coherent set; if one criterion is genuinely blocked by an unresolved architecture fact, stop only that dependent part and report the exact blocker rather than substituting a conventional design;
- add/update tests for changed contracts and negative paths;
- run locked pytest, Ruff, strict mypy, package build/wheel reinstall and isolated import validation;
- verify private payload bytes are absent from durable runtime storage;
- update `docs/implementation-baseline.md` to executable truth;
- update or remove this temporary refactor folder when its instructions are no longer needed;
- open/update the implementation PR against `architecture/modular-agentic-clean-slate` and leave integration for Owner review unless explicitly instructed otherwise;
- report any remaining blocker as a concrete architectural or implementation fact, not a generic follow-up list.
