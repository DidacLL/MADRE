# MADRE Reasoning Execution Contract

## Scope

Kernel execution is intentionally restricted to reasoning. It is not a universal physical-action dispatcher and does not provide a generic `Capability<C,R>` SPI.

A Module may perform ordinary application I/O directly inside bounded Module behavior. File, database, network, search, device and similar effects do not enter Kernel merely because they are external.

## Module-created reasoning work

A Module starts from one valid `OperationCall` and constructs a nominal `ReasoningComputation<R>`. The SDK then creates a `ReasoningRequest<R,C>` where `C extends ReasoningComputation<R>`.

The request contains:

- originating `ModuleId`, derived from the bounded Operation call;
- the typed reasoning computation;
- carried Sensitivity, derived from the input Material of that call;
- immediate or durable execution mode;
- priority and eligibility time;
- timeout, cancellation key and reasoning retry policy;
- typed reasoning preferences currently limited to optional location and maximum latency.

The request contains no Material identity or Material type, Agent, Workflow, concrete reasoning-mechanism identity, semantic continuation or future output Material.

Operation Risk is deliberately absent. A reasoning mechanism computes information; it does not thereby realize the external effect described by a Module Operation's EffectProfile.

## Reasoning contract and mechanism selection

A `ReasoningCapability<R,C>` exposes:

```text
manifest()     -> ReasoningCapabilityManifest<R,C>
availability() -> ReasoningAvailability
execute(C computation, ReasoningExecutionContext context) -> R
```

Its manifest contains only installed facts used by reasoning selection:

- nominal `ReasoningCapabilityId`;
- exact `ReasoningContract<R,C>`;
- explicit receiving `Privacy`;
- `ReasoningLocation`;
- expected latency;
- resource claims.

It does not contain Material semantics, Module/Operation identities, Risk, action-realizer Integrity, provider-account authority or arbitrary metadata.

Kernel selection is deterministic and proceeds over mechanisms implementing the exact reasoning contract. A candidate is usable only when:

1. the request's carried Sensitivity can reach the manifest's receiving Privacy;
2. the mechanism is currently available;
3. required resources can be reserved;
4. typed request location/latency preferences are satisfied.

No rejected security-decision object is created. A non-composable mechanism is simply unreachable for that request.

## Invocation

Kernel reserves the selected resources and invokes the adapter with only the typed computation and execution mechanics needed for timeout/cancellation/attempt handling.

The adapter owns provider-specific protocol translation. Provider request/response structures, endpoint details and transport behavior stay inside the adapter.

Current reasoning realizations are text inference through:

- llama.cpp AF_UNIX transport;
- explicit llama.cpp loopback-HTTP compatibility transport;
- OpenAI-compatible HTTP transport.

All implement the same typed text-inference reasoning contract. No connector is silently enabled.

## Module-owned interpretation

A reasoning result is not Material. Kernel neither assigns Material identity nor chooses output Sensitivity.

The originating Module:

1. receives the reasoning result;
2. interprets it according to its bounded behavior;
3. creates new Material when the result has semantic value;
4. updates Module state/presentation as appropriate;
5. invokes another bounded Operation or stops.

There is no result object that can execute an Operation or submit more work by itself.

## Immediate and durable reasoning

Immediate reasoning uses the same registry selection, resource coordination, failure mapping and retry semantics as durable reasoning, but returns the result to the waiting Module call.

Durable reasoning stores only the runtime state needed to survive restart. SQLite persists opaque serialized computation bytes, Module identity for delivery, priority/eligibility/timeout/cancellation/retry state, attempts/failure category and opaque result bytes until collection and acknowledgement.

Queued input survives restart. Interrupted running work returns to an eligible state under the runtime's recovery rules. Successful output survives restart until collected/acknowledged or removed by retention cleanup.

The Kernel cannot inspect persisted computation bytes as Module knowledge.

## Failures

Reasoning failures use stable reasoning categories such as unavailable, timeout, cancelled, connection, protocol, remote failure, internal and interrupted. Retry is bounded by the request's `ReasoningRetryPolicy`.

Absence of an installed/available compatible mechanism is a reasoning-runtime condition, not a platform boot failure. MADRE can start with an empty reasoning registry.

## Search and other external I/O

SearXNG is not a `ReasoningCapability`. The repository's SearXNG integration is an ordinary Java client over the reusable web-search value model. A domain Module may use that client directly when web search belongs to its behavior.

The former standalone WebSearch Module and generic search Kernel capability are intentionally removed. Reintroducing search into Kernel would require a new concrete shared-Kernel responsibility, not analogy with tool-calling frameworks.
