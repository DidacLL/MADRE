# Lane C .NET architecture-validation slice

This tree validates the accepted capability-aware physical Kernel alongside the existing C++/Java reference implementation. It is intentionally not a wholesale replacement and contains no semantic MADRE Runtime, Module, Agent, Operation, Material, SPIRA, ReasoningRequest, semantic Workflow or WorkPlan implementation.

This implementation validates required architectural relationships. Its concrete DTOs, enum vocabularies, HTTP surface and current DRE heuristic are not promoted to MADRE architectural contracts by their presence here.

## Current validation request

The current `PhysicalInferenceRequest` fields are deliberately small because this slice has a production consumer for each of them. The exact type and field set remain design work rather than settled MADRE ontology.

| Current field | Validation consumer |
| --- | --- |
| `PreparedInput` | passed unchanged to the selected `IInferenceBinding` |
| `RequestedEffort` | DRE rejects capabilities whose declared supported effort is lower |
| `Urgency` | durable eligible-Work ordering; `Interactive` also enables the current observed-latency preference |
| `EligibleAt` | SQLite eligible-Work query |
| `Deadline` | queued expiry before dispatch, with no attempt |
| `ExecutionBoundary` | DRE hard admissibility filter (`LocalOnly` rejects external capabilities in this validation) |

`PreparedInput` being a `string`, `Low / Standard / High` being the effort vocabulary, `Background / Normal / Interactive` being the urgency vocabulary, and binary `LocalOnly / ExternalAllowed` are validation choices. They are not established public contracts.

## Capability model

The current `InferenceCapability` carries capability identity, binding identity/version, configured execution-boundary and supported-effort facts, and an integer Owner preference. `OwnerPreference` is definitionally Owner-owned and therefore does not carry a second generic provenance value. Execution-boundary and supported-effort provenance remain explicit because this validation exercises configured facts whose sources may differ.

A freshly configured capability starts with current state `Unknown` and no fabricated observation timestamp. Only explicitly `Available` capabilities are dispatchable. `Unknown` and `Unavailable` admissible capabilities leave Work pending until state changes or its deadline expires.

Configured facts live in `capabilities`. Current availability lives in `capability_state`. Historical MADRE evidence comes from durable `attempts`. These stores are deliberately separate.

For `Interactive` Work, if every currently available admissible candidate has successful latency evidence, the current DRE implementation selects the lowest observed average latency. Otherwise it falls back to descending Owner preference, with stable capability identity as the final tie break. `Normal` and `Background` currently use Owner preference after hard filtering/current availability. This latency-versus-preference heuristic is a validation rule, not a permanent DRE policy contract.

## Bindings

All physical execution in this slice uses the current `IInferenceBinding` seam after DRE has selected an `InferenceCapability`.

The slice includes a real `MeaiInferenceBinding` that invokes `Microsoft.Extensions.AI.IChatClient`, a shell-free bounded `ProcessInferenceBinding`, and an Owner custom binding implemented only in the validation host. The DRE/core has no binding-type branch.

The exact `IInferenceBinding` API is provisional. Its presence proves binding parity and separation from capability selection; it does not freeze a public Kernel extension API.

## Durable truth

SQLite is authoritative for Work, capability configuration/state, attempts/observations and retained result state. Startup converts interrupted `RUNNING` attempts and Work to `UNKNOWN_COMPLETION`; it never automatically repeats them.

`single-inference/v1` is only the identity/version of this concrete persisted validation strategy. It is not a declaration that this is MADRE's permanent or only physical strategy shape.

The 1 MiB input/result limit is a validation implementation bound, not permanent MADRE ontology.

## Local validation control plane

The loopback HTTP host exists to exercise independent Kernel lifetime, caller disappearance and later inspection/result/release. Its exact routes and DTOs are validation transport choices rather than a settled public Kernel API.

Submit, status, inspect, result, cancel and release currently live under `/v1` because that was convenient for this executable proof; the version label does not constitute a compatibility commitment. Deterministic capability-state mutation is explicitly segregated under `/_validation/capabilities/{id}/state` and is validation machinery only, not an administration or client contract. Real capability-state observation/update remains future work.

## Validation

From repository root:

```text
dotnet build kernel-dotnet/validation/Madre.Kernel.Acceptance/Madre.Kernel.Acceptance.csproj --configuration Release
dotnet run --project kernel-dotnet/validation/Madre.Kernel.Acceptance/Madre.Kernel.Acceptance.csproj --configuration Release --no-build
```

The acceptance harness launches a real independent Kernel process and proves MEAI/process/custom parity, behavioral consumption of the current request facts, configured/current/observed separation, unknown-before-observed capability state, persisted observation feedback after restart, physical-not-semantic result judgment, factual process failure, caller disappearance, queued restart, active `UNKNOWN_COMPLETION` without duplicate execution, eligibility/deadline/urgency, cancellation truthfulness, bounded payloads and idempotent release.

MAF Workflow/checkpoint/resume is deliberately deferred. This correction pass does not begin the MAF validation slice.
