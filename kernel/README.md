# MADRE Kernel

Lane C is MADRE's local physical inference substrate. It owns durable physical Work, capability truth/evidence, DRE scheduling, binding execution and truthful recovery. It does not own Module, Agent, Material, SPIRA, ReasoningRequest, CORE or application semantics.

## Semantic → physical boundary

Semantic MADRE determines the information journey and derives physical admissibility before Kernel Work exists. Kernel receives physical restrictions only; it does not receive the semantic reason for them.

`PhysicalInferenceRequest` currently carries:

- prepared physical input;
- requested physical effort;
- urgency;
- optional eligibility/deadline;
- request-side `ExecutionBoundary` (`LocalOnly` or `ExternalAllowed`);
- optional `eligibleCapabilityIds`.

`ExecutionBoundary` is an **allowance/constraint on this Work**, not a factual description of a capability. `eligibleCapabilityIds` is an opaque physical admissibility set. When absent, DRE may choose any capability satisfying the other physical constraints. When present, DRE may choose only inside that set. A singleton set preserves an exact Owner/semantic physical choice while keeping provider/model semantics out of Kernel.

Capabilities expose the opposite direction: factual configured execution-path information. `InferenceCapability.executionPath` contains a factual `ExecutionLocation` (`Local` or `External`), destination, and optional route/data-retention facts with provenance. These facts are inspectable by semantic MADRE/Owner tooling so the information journey can be resolved above Kernel. Kernel does not interpret route/retention strings as SPIRA or provider policy.

The protocol carrying this repaired boundary is version 2. The current pre-release SQLite physical schema is version 2; incompatible older pre-release schemas are rejected rather than migrated.

## Startup and ownership

The Kernel is valid with zero configured capabilities. A missing configuration file therefore does not block startup. Configuration only becomes necessary when the Owner expects the Kernel to execute inference.

Exactly one Kernel process owns a SQLite database at a time, regardless of IPC path. A database-scoped process-lifetime lease is acquired before endpoint handling or scheduling starts. Linux ownership canonicalizes filesystem aliases/symlinks so the same physical database cannot obtain multiple owners through different path spellings. A second Kernel targeting that database fails without unlinking the live owner's socket or starting another scheduler.

The host accepts only these technical process arguments, each at most once and with an explicit value:

- `--config <path>`: optional JSON capability configuration;
- `--db <path>`: optional SQLite location override;
- `--ipc-path <path>`: optional local-socket path override, mainly for tests/embedding;
- `--max-concurrent <n>`: optional physical-concurrency override.

Unknown flags, duplicate flags, missing values and malformed values fail startup. The ordinary physical-concurrency default has one owner in `KernelHostDefaults`.

Configured process capabilities require explicit physical identity/binding, execution location, destination, supported effort and Owner preference. Optional route and data-retention facts must be nonblank when supplied. Programmatically constructed capabilities are validated against the same factual execution-path contract.

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

DRE first intersects the configured catalogue with the Work's opaque eligible capability set when one exists, then applies effort and request-side exposure admissibility. `LocalOnly` excludes capabilities whose factual execution location is external. `ExternalAllowed` permits either factual location; it does not describe where execution actually happens.

DRE prefers known-available admissible capabilities. When none are known available, an admissible configured `Unknown` may be tried because absence of evidence is not unavailability. Actual execution can then contribute physical evidence.

Known-unavailable capabilities are not dispatched. They are re-observed automatically while relevant pending Work creates demand; idle Kernel does not periodically probe every capability forever. Explicit refresh remains available.

Owner preference and successful latency remain DRE inputs. Latency evidence is scoped to the current capability/binding/version, so changing a binding/version under the same capability id does not reuse stale evidence. Durable attempt history is retained; unused success/failure aggregate counters are not part of the current capability contract.

Eligibility, deadlines, physical admissibility, execution allowance and explicit effort remain physical Kernel policy.

## Scheduling and bindings

The scheduler is wake/deadline driven and considers the complete eligible metadata set, preserving no-head-starvation behavior. Scheduling candidates do not load `prepared_input`; the physical payload is loaded only when Work is actually claimed for execution.

A scheduler pass snapshots the dispatch capacity available when the pass begins. Capacity released by a fast completion is reconsidered in a fresh pass instead of being reused against stale urgency ordering.

`IInferenceBinding` is the open physical seam. A binding receives only `InferenceExecutionRequest`, currently the prepared physical input, rather than scheduling metadata. The process binding, MEAI interoperability and Owner-defined bindings use the same execution responsibility. Common result validation and exception/result conversion happen once in `BindingExecutor`.

The process binding is shell-free and explicitly UTF-8 for stdin/stdout/stderr.

## Durability and fatal truth

SQLite is authoritative for Work, physical admissibility, attempts, configured capability catalogue/current state and retained results. An incompatible unversioned/pre-release database fails early; Lane C does not add migrations or compatibility machinery for those schemas.

Removing a configured capability removes it from the selectable current catalogue while attempt history remains historical evidence. Active attempts interrupted by Kernel process loss recover as `UnknownCompletion`; they are never implicitly duplicated.

Scheduler, capability-state persistence and attempt completion/persistence are supervised Kernel infrastructure. If authoritative physical state can no longer be maintained, the host fails/terminates rather than continuing to answer healthy. A persisted attempt left running by such a fatal exit recovers as `UnknownCompletion` on restart.

Release preserves Work identity/attempt history and physical metadata while deleting retained input/result payload.

No production checkpoint/MAF strategy is present. Git history retains the earlier validation experiment; a future real physical strategy may introduce suitable machinery only when an actual MADRE need requires it.
