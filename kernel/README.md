# MADRE Kernel

Lane C is MADRE's local physical inference substrate. It owns durable physical Work, capability truth/observations, DRE scheduling, binding execution and truthful recovery. It does not own Module, Agent, Material, SPIRA, ReasoningRequest, CORE or application semantics.

## Startup

The Kernel is valid with zero configured capabilities. A missing configuration file therefore does not block startup. Configuration only becomes necessary when the owner expects the Kernel to execute inference.

The host accepts these technical process arguments:

- `--config <path>`: optional JSON capability configuration;
- `--db <path>`: optional SQLite location override;
- `--ipc-path <path>`: optional local-socket path override, mainly useful for tests/embedding;
- `--max-concurrent <n>`: optional physical-concurrency override.

There is no TCP port and no web server. The default endpoint is a versioned Unix-domain socket in the current owner's local application-data directory. Linux creates the endpoint directory owner-only and the socket owner read/write. Windows uses the platform Unix-domain-socket implementation supported by .NET and Java 21.

## Local IPC

The Java client and .NET host speak a small versioned framed protocol over the local socket. Each message is:

1. a 4-byte big-endian frame length;
2. one UTF-8 JSON request or response envelope.

`KernelProtocol` owns protocol version and payload/frame limits on the .NET side. `KernelProtocol` in `madre-kernel-client` owns the Java-side constants. Acceptance proves parity through the live `ProtocolInfo` operation. Callers use independent short-lived connections, so a stalled or disappearing caller cannot own the Kernel lifetime or block other callers.

## Capability truth and DRE

Configured capability facts, current availability and historical observations remain distinct. Startup resets current availability to `Unknown` and probes asynchronously. DRE prefers known-available admissible capabilities. When none are known available, an otherwise admissible configured `Unknown` capability may be tried because `Unknown` is absence of evidence, not evidence of unavailability. Execution then contributes physical evidence.

Known-unavailable capabilities are not dispatched. They are re-observed automatically using Kernel-owned timing defaults; no caller must refresh them forever. Owner preference and observed successful latency remain DRE inputs. Eligibility, deadlines, execution boundary and explicit effort admissibility remain physical Kernel policy.

## Bindings

`IInferenceBinding` is the open physical seam. A binding receives only `InferenceExecutionRequest`, currently the prepared physical input, rather than scheduling metadata. The provided process binding and MEAI interoperability use the same execution responsibility as owner-defined bindings. Common result validation and conversion of binding exceptions to truthful physical outcomes happen once in `BindingExecutor`.

## Durability

SQLite is authoritative for Work, attempts, configured capability catalogue/current state and retained results. Removing a configured capability removes it from the selectable current catalogue while attempt history remains historical evidence. Active attempts interrupted by Kernel process loss recover as `UnknownCompletion`; they are never implicitly duplicated.

Release preserves Work identity/attempt history while deleting retained input/result payload. No production checkpoint/MAF strategy is present. Git history retains the earlier validation experiment; a future real physical strategy may introduce additional machinery only when an actual MADRE need requires it.
