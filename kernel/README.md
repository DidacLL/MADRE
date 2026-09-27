# MADRE Kernel

This directory contains the sole current Lane C implementation: the .NET MADRE physical inference Kernel.

The Kernel is a local-first physical control plane. It owns durable `PhysicalInferenceWork`, `InferenceCapability` configuration/current state/observations, physical DRE scheduling, attempts, checkpoint linkage, results, cancellation and restart recovery. It does not depend on `madre-sdk` and does not understand Module, MADRE Agent, Operation, Material, ReasoningRequest, SPIRA, CORE, semantic Workflow/WorkPlan or semantic continuation.

## Control boundary

The production host listens only on loopback HTTP (`127.0.0.1`) by default. This is intentionally the smallest cross-platform boundary that gives the Kernel an independent process lifetime, concurrent clients, bounded requests and a versionable physical contract on both Windows and Linux.

Current endpoints are under `/v1` for submit, inspection, result collection, cancellation, release, capability inspection and physical capability-state refresh. There is no remote discovery, TLS/PKI, tenancy or network control plane.

## Owner-configured capability

Normal startup reads an explicit JSON configuration:

```text
dotnet run --project kernel/src/Madre.Kernel.Host -- --config ./madre-kernel.json
```

`madre-kernel.example.json` shows the first production binding: a shell-free generic process/executable capability. The executable receives the prepared physical input on stdin and writes its physical result to stdout. `probeArguments` are optional but are the normal way for the process binding to establish current availability from actual physical evidence. Without a probe, current availability remains `Unknown` and DRE will not dispatch through that capability.

The process binding is not privileged. It implements the same `IInferenceBinding` seam as `MeaiInferenceBinding` and Owner/custom bindings. MADRE does not dynamically discover DLLs or build a connector marketplace; an advanced Owner can compose another binding against the same seam or put unusual intelligence behind an executable/service wrapper.

## Capability truth and DRE

Configured facts, current physical state and observed attempt history are stored separately. Configuration alone never marks a capability `Available`. Kernel probes bindings at startup and when `/v1/capabilities/refresh` is called; the resulting observation and timestamp become current state.

The current first-version DRE rules are intentionally narrow:

- eligibility and deadline are durable admission facts;
- hard `LocalOnly` restrictions reject external capabilities;
- requested effort rejects capabilities that declare insufficient effort;
- only currently `Available` capabilities dispatch;
- `Interactive` Work uses persisted successful latency evidence when all available candidates have comparable evidence;
- otherwise Owner preference is the stable choice rule;
- `High + Background` selects the concrete two-stage checkpointed physical strategy;
- all other Work uses one-shot physical inference.

There is no provider/model-specific routing and no semantic answer-quality judgment.

## Durable truth

SQLite is authoritative for Work, configured/current capability facts, physical attempt history and retained results. Active attempts whose completion becomes unknowable across hard Kernel death become `UnknownCompletion` and are not implicitly repeated. Cancellation only becomes confirmed where the binding can physically confirm it. Terminal retained input/result can be explicitly released while preserving Work identity and history.

The two-stage physical strategy uses Microsoft Agent Framework checkpoint state only as subordinate execution state. MADRE Work remains authoritative for existence, cancellation, terminal state, deadline, strategy version and selected capability/binding compatibility before continuation.
