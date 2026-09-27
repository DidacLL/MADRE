# Lane C .NET architecture-validation slices

This tree validates the accepted capability-aware physical Kernel alongside the existing C++/Java reference implementation. It is intentionally not a wholesale replacement and contains no semantic MADRE Runtime, Module, Agent, Operation, Material, SPIRA, ReasoningRequest, semantic Workflow or WorkPlan implementation.

This implementation validates required architectural relationships. Its concrete DTOs, enum vocabularies, HTTP surface, MAF API choices and current DRE heuristics are not promoted to MADRE architectural contracts by their presence here.

## Current validation request

The current `PhysicalInferenceRequest` fields are deliberately small because these slices have a concrete consumer for each of them. The exact type and field set remain design work rather than settled MADRE ontology.

| Current field | Validation consumer |
| --- | --- |
| `PreparedInput` | passed to the selected physical binding; Slice 2 stage B receives stage-A physical output as its prepared input |
| `RequestedEffort` | DRE rejects capabilities whose declared supported effort is lower; `High` participates in the concrete Slice 2 strategy choice |
| `Urgency` | durable eligible-Work ordering; `Interactive` enables the current observed-latency preference; `Background + High` selects the one Slice 2 strategy |
| `EligibleAt` | SQLite eligible-Work query |
| `Deadline` | queued expiry before dispatch, with no attempt |
| `ExecutionBoundary` | DRE hard admissibility filter (`LocalOnly` rejects external capabilities in this validation) |

`PreparedInput` being a `string`, `Low / Standard / High` being the effort vocabulary, `Background / Normal / Interactive` being the urgency vocabulary, and binary `LocalOnly / ExternalAllowed` are validation choices. They are not established public contracts.

## Capability model

The current `InferenceCapability` carries capability identity, binding identity/version, configured execution-boundary and supported-effort facts, and an integer Owner preference. `OwnerPreference` is definitionally Owner-owned and therefore does not carry a second generic provenance value. Execution-boundary and supported-effort provenance remain explicit because this validation exercises configured facts whose sources may differ.

A freshly configured capability starts with current state `Unknown` and no fabricated observation timestamp. Only explicitly `Available` capabilities are dispatchable. `Unknown` and `Unavailable` admissible capabilities leave Work pending until state changes or its deadline expires.

Configured facts live in `capabilities`. Current availability lives in `capability_state`. Historical MADRE evidence comes from durable `attempts`. These stores are deliberately separate.

For `Interactive` Work, if every currently available admissible candidate has successful latency evidence, the current DRE implementation selects the lowest observed average latency. Otherwise it falls back to descending Owner preference, with stable capability identity as the final tie break. `Normal` currently uses Owner preference after hard filtering/current availability. Slice 2 uses one deliberately narrow concrete DRE rule: `High + Background` selects `maf-two-stage-inference/v1`. These are validation rules, not permanent DRE policy contracts.

## Bindings

All physical inference in these slices uses the current `IInferenceBinding` seam after DRE has selected an `InferenceCapability`.

The slice includes a real `MeaiInferenceBinding` that invokes `Microsoft.Extensions.AI.IChatClient`, a shell-free bounded `ProcessInferenceBinding`, and an Owner custom binding implemented only in the validation host. The DRE/core has no binding-type branch.

The exact `IInferenceBinding` API is provisional. Its presence proves binding parity and separation from capability selection; it does not freeze a public Kernel extension API.

## Durable truth

SQLite is authoritative for Work, capability configuration/state, attempts/observations and retained result state. Ordinary interrupted `RUNNING` attempts and Work still become `UNKNOWN_COMPLETION`; they are never automatically repeated.

`single-inference/v1` remains the ordinary one-shot validation strategy. Slice 2 adds exactly one checkpointable strategy, `maf-two-stage-inference/v1`. Its concrete `Checkpointed` state and checkpoint identity fields exist to prove the required authority boundary and are not a general workflow-state model.

For the checkpointable strategy SQLite retains only the MAF checkpoint `SessionId` and `CheckpointId`. The subordinate MAF workflow state is stored by MAF's filesystem JSON checkpoint store under `<kernel-db>.maf-checkpoints/<work-id>/`. MADRE does not copy the workflow graph/state into its Work columns.

A checkpoint never authorizes itself. MADRE Work must still be nonterminal, checkpointed, uncancelled, strategy-compatible, and associated with an admissible/available selected capability before Kernel invokes MAF resume. Cancelling checkpointed Work makes it terminal and restart does not resume the subordinate workflow. An incompatible stored strategy version fails factually instead of being reinterpreted.

The current validation database schema is not a released cross-version upgrade contract. Restart proofs use the same implementation/schema on both sides; no migration infrastructure is provided by these slices.

The 1 MiB input/result limit is a validation implementation bound, not permanent MADRE ontology.

## Slice 2 MAF boundary validation

Slice 2 uses `Microsoft.Agents.AI.Workflows` only for one genuinely multi-stage physical strategy:

```text
MADRE DRE chooses maf-two-stage-inference/v1
    ↓
MAF Workflow
    stage A executor
        ↓ actual inference through selected MADRE capability/binding
    RequestPort checkpoint boundary
        ↓ durable MAF checkpoint with pending mechanical continuation
    stage B executor
        ↓ actual inference through the same selected MADRE capability/binding
    physical result
```

The `RequestPort` is a mechanical durable barrier. It is not Owner approval, semantic interpretation, provider routing or an Agent. On resume MADRE authorizes continuation, MAF rehydrates the pending request from the checkpoint, Kernel echoes the already-produced stage-A physical output through that barrier, and stage B consumes it.

The MAF-specific code is localized in the concrete strategy implementation. Simple one-shot inference does not construct a MAF workflow or checkpoint directory. `InferenceCapability`, DRE, bindings, ordinary scheduling and Work persistence remain MADRE concepts rather than MAF types.

The current programmatic `WorkflowBuilder`, executor, `RequestPort`, `CheckpointManager.CreateJson`, `FileSystemJsonCheckpointStore`, `CheckpointInfo` and `InProcessExecution` choices are provisional implementation choices for this validation, not settled MADRE architecture.

## Local validation control plane

The loopback HTTP host exists to exercise independent Kernel lifetime, caller disappearance and later inspection/result/release. Its exact routes and DTOs are validation transport choices rather than a settled public Kernel API.

Submit, status, inspect, result, cancel and release currently live under `/v1` because that was convenient for this executable proof; the version label does not constitute a compatibility commitment. Deterministic capability-state mutation is explicitly segregated under `/_validation/capabilities/{id}/state` and is validation machinery only, not an administration or client contract. The Slice 2 validation host also accepts `--hold-checkpointed`; this is validation-only scaffolding that creates a deterministic hard-kill window after stage A and is not a Kernel scheduling feature.

## Validation

From repository root, Slice 1 remains:

```text
dotnet build kernel-dotnet/validation/Madre.Kernel.Acceptance/Madre.Kernel.Acceptance.csproj --configuration Release
dotnet run --project kernel-dotnet/validation/Madre.Kernel.Acceptance/Madre.Kernel.Acceptance.csproj --configuration Release --no-build
```

Slice 2 is:

```text
dotnet build kernel-dotnet/validation/Madre.Kernel.Slice2Acceptance/Madre.Kernel.Slice2Acceptance.csproj --configuration Release
dotnet run --project kernel-dotnet/validation/Madre.Kernel.Slice2Acceptance/Madre.Kernel.Slice2Acceptance.csproj --configuration Release --no-build
```

Slice 1 continues to prove MEAI/process/custom parity, behavioral consumption of the current request facts, configured/current/observed separation, unknown-before-observed capability state, persisted observation feedback after restart, physical-not-semantic result judgment, factual process failure, caller disappearance, queued restart, ordinary active `UNKNOWN_COMPLETION` without duplicate execution, eligibility/deadline/urgency, cancellation truthfulness, bounded payloads and idempotent release.

Slice 2 proves DRE strategy choice, real MAF workflow/checkpoint usage, stage-A durable physical evidence, hard Kernel death at the checkpoint boundary, restart/resume without stage-A replay, stage-B physical evidence and retained result, cancellation-before-resume authority, incompatible-version rejection, and continued MAF bypass for simple one-shot inference on both Linux and Windows.
