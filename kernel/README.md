# MADRE Kernel

Lane C is MADRE's local physical inference substrate. It owns durable physical Work, capability truth/evidence, DRE scheduling, binding execution and truthful recovery. It does not own Module, Agent, Material, SPIRA, ReasoningRequest, CORE or application semantics.

## Startup and ownership

The Kernel is valid with zero configured capabilities. A missing configuration file therefore does not block startup. Configuration only becomes necessary when the Owner expects the Kernel to execute inference.

Exactly one Kernel process owns a SQLite database at a time, regardless of IPC path. A database-scoped process-lifetime lease is acquired before endpoint handling or scheduling starts. A second Kernel targeting that database fails without unlinking the live owner's socket or starting another scheduler.

The host accepts only these technical process arguments, each at most once and with an explicit value:

- `--config <path>`: optional JSON capability configuration;
- `--db <path>`: optional SQLite location override;
- `--ipc-path <path>`: optional local-socket path override, mainly for tests/embedding;
- `--max-concurrent <n>`: optional physical-concurrency override.

Unknown flags, duplicate flags, missing values and malformed values fail startup. The ordinary physical-concurrency default has one owner in `KernelHostDefaults`.

## Local IPC

There is no TCP port and no web server. The default endpoint is a versioned Unix-domain socket in the current owner's local application-data directory. Linux creates the Kernel-owned endpoint directory owner-only and the socket owner read/write. Windows uses the Unix-domain-socket support in .NET and Java 21.

The Java client and .NET host speak a small versioned framed protocol:

1. a 4-byte big-endian frame length;
2. one UTF-8 JSON request or response envelope.

`KernelProtocol` owns protocol version and payload/frame limits on the .NET side; the Java package has one corresponding source and acceptance proves parity through live `ProtocolInfo`.

A live endpoint is never deleted. A stale filesystem socket is removed only after it is established not to be live. Client connections are independent and short-lived. The server bounds active handlers as well as frame size, and the Java client has a bounded technical call timeout against a stalled/bogus local peer.

IPC parsing is strict: required fields are required, numeric enum encodings and unknown parsed properties are rejected rather than silently becoming enum/default values.

## Capability truth and DRE

Configured capability facts, current availability and historical observations remain distinct. Startup resets current availability to `Unknown` and observes asynchronously. A slow/broken probe cannot hold Kernel startup hostage; a probe technical timeout is `Unknown`, not proof of unavailability.

DRE prefers known-available admissible capabilities. When none are known available, an admissible configured `Unknown` may be tried because absence of evidence is not unavailability. Actual execution can then contribute physical evidence.

Known-unavailable capabilities are not dispatched. They are re-observed automatically while relevant pending Work creates demand; idle Kernel does not periodically probe every capability forever. Explicit refresh remains available.

Owner preference and successful latency remain DRE inputs. Latency evidence is scoped to the current capability/binding/version, so changing a binding/version under the same capability id does not reuse stale evidence. Durable attempt history is retained; unused success/failure aggregate counters are not part of the current capability contract.

Eligibility, deadlines, execution boundary and explicit effort admissibility remain physical Kernel policy.

## Scheduling and bindings

The scheduler is wake/deadline driven and considers the complete eligible metadata set, preserving no-head-starvation behavior. Scheduling candidates do not load `prepared_input`; the physical payload is loaded only when Work is actually claimed for execution.

`IInferenceBinding` is the open physical seam. A binding receives only `InferenceExecutionRequest`, currently the prepared physical input, rather than scheduling metadata. The process binding, MEAI interoperability and Owner-defined bindings use the same execution responsibility. Common result validation and exception/result conversion happen once in `BindingExecutor`.

The process binding is shell-free and explicitly UTF-8 for stdin/stdout/stderr.

## Durability and fatal truth

SQLite is authoritative for Work, attempts, configured capability catalogue/current state and retained results. The current schema has an explicit identity/version. An incompatible unversioned/pre-release database fails early; Lane C does not add migrations or compatibility machinery for those schemas.

Removing a configured capability removes it from the selectable current catalogue while attempt history remains historical evidence. Active attempts interrupted by Kernel process loss recover as `UnknownCompletion`; they are never implicitly duplicated.

Scheduler, capability-state persistence and attempt completion/persistence are supervised Kernel infrastructure. If authoritative physical state can no longer be maintained, the host fails/terminates rather than continuing to answer healthy. A persisted attempt left running by such a fatal exit recovers as `UnknownCompletion` on restart.

Release preserves Work identity/attempt history while deleting retained input/result payload.

No production checkpoint/MAF strategy is present. Git history retains the earlier validation experiment; a future real physical strategy may introduce suitable machinery only when an actual MADRE need requires it.
