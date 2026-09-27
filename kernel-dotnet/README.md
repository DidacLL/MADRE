# Lane C .NET architecture-validation slice

This tree validates the accepted capability-aware physical Kernel alongside the existing C++/Java reference implementation. It is intentionally not a wholesale replacement and contains no semantic MADRE Runtime, Module, Agent, Operation, Material, SPIRA, ReasoningRequest, semantic Workflow or WorkPlan implementation.

## First-version physical request

`PhysicalInferenceRequest` has exactly six fields, each with a production consumer:

| Field | Production consumer |
| --- | --- |
| `PreparedInput` | passed unchanged to the selected `IInferenceBinding` |
| `RequestedEffort` | DRE rejects capabilities whose declared supported effort is lower |
| `Urgency` | durable eligible-Work ordering; `Interactive` also enables observed-latency preference |
| `EligibleAt` | SQLite eligible-Work query |
| `Deadline` | queued expiry before dispatch, with no attempt |
| `ExecutionBoundary` | DRE hard admissibility filter (`LocalOnly` rejects external capabilities) |

## Capability model

`InferenceCapability` contains only `CapabilityId`, `BindingId`, `BindingVersion`, and three explicitly provenanced configured facts: `ExecutionBoundary`, `SupportedEffort`, and `OwnerPreference`.

Configured facts live in `capabilities`. Current availability lives in `capability_state`. Historical MADRE evidence comes from durable `attempts`. These stores are deliberately separate.

For `Interactive` Work, if every currently available admissible candidate has successful latency evidence, DRE selects the lowest observed average latency. Otherwise it falls back to descending Owner preference, with stable capability identity as the final tie break. `Normal` and `Background` use Owner preference after hard filtering/current availability.

## Bindings

All physical execution uses `IInferenceBinding` after DRE has selected an `InferenceCapability`.

The slice includes a real `MeaiInferenceBinding` that invokes `Microsoft.Extensions.AI.IChatClient`, a shell-free bounded `ProcessInferenceBinding`, and an Owner custom binding implemented only in the validation host. The DRE/core has no binding-type branch.

## Durable truth

SQLite is authoritative for Work, capability configuration/state, attempts/observations and retained result state. Startup converts interrupted `RUNNING` attempts and Work to `UNKNOWN_COMPLETION`; it never automatically repeats them. `single-inference/v1` and selected binding identity/version are persisted.

The loopback HTTP control plane provides submit, status, inspect, result, cancel and idempotent terminal release, plus capability state/inspection endpoints used by the deterministic validation.

## Validation

From repository root:

```text
dotnet build kernel-dotnet/validation/Madre.Kernel.Acceptance/Madre.Kernel.Acceptance.csproj --configuration Release
dotnet run --project kernel-dotnet/validation/Madre.Kernel.Acceptance/Madre.Kernel.Acceptance.csproj --configuration Release --no-build
```

The acceptance harness launches a real independent Kernel process and proves MEAI/process/custom parity, request-field consumption, configured/current/observed separation, persisted observation feedback after restart, physical-not-semantic result judgment, factual process failure, caller disappearance, queued restart, active `UNKNOWN_COMPLETION` without duplicate execution, eligibility/deadline/urgency, cancellation truthfulness, bounded payloads and idempotent release.

MAF Workflow/checkpoint/resume is deliberately deferred to the second validation slice.
