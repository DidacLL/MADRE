# MADRE Reasoning Execution Contract

## Scope

Kernel execution is intentionally restricted to reasoning. It is not a universal physical-action dispatcher and does not provide a generic `Capability<C,R>` SPI.

A Module may perform ordinary application I/O directly inside bounded Module behavior. File, database, network, search, device and similar effects do not enter Kernel merely because they are external.

## Public reasoning-adapter SPI

The published `madre-reasoning-spi` artifact is the boundary for independently built reasoning mechanisms. It exposes only the typed concepts a reasoning adapter needs: `ReasoningCapability`, capability identity/manifest, exact `ReasoningContract` and durable codecs, observed `ReasoningAvailability`, `ReasoningExecutionContext`, resource claims, typed failure reporting, and the minimal provider/materialization configuration boundary.

It does not expose Module/Agent/Workflow/Operation semantics, Material, SQLite runtime objects, Kernel registries, schedulers, application assembly, generic tools/actions or semantic continuation.

A reasoning adapter can therefore be built independently from `madre-app` and Kernel runtime implementation classes while still implementing a published computation contract such as `madre-text-inference`.

## Installation and provider materialization

Reasoning adapter JARs expose `ReasoningMechanismProvider` through Java's service-provider mechanism. The application discovers them from the configured reasoning installation directory with JDK path/class-loader APIs. Installed distributions default to sibling `reasoning/`; `reasoning.directory` may override it.

Module and reasoning installation remain separate. Reasoning providers do not participate in Module registration, CORE role resolution or Module lifecycle.

The application supplies providers with an immutable read-only view of the generic `reasoning.*` owner configuration namespace. Provider-specific field names and parsing belong to the provider. A provider may materialize multiple named mechanism instances from one installed adapter artifact.

Installation does not imply enablement. An absent directory, empty directory, provider with no configured instances, or provider whose instances are all disabled produces zero registered `ReasoningCapability` values and does not prevent MADRE from booting.

Privacy is an explicit configured mechanism fact. It is never derived from endpoint, transport, process placement or `ReasoningLocation`. Invalid enabled provider configuration is a startup error; adapters must not silently substitute different mechanism semantics.

Shipped llama.cpp AF_UNIX, explicit llama.cpp loopback-HTTP compatibility and OpenAI-compatible adapters use this same discovery/materialization path as independently supplied adapters. Bundled placement grants no selection privilege.

## Module-created reasoning work

A Module starts from one valid bounded `OperationCall` and constructs a nominal `ReasoningComputation<R>`. The SDK then creates a `ReasoningRequest<R,C>` where `C extends ReasoningComputation<R>`.

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

Installed order, shipped-vs-external origin and CORE designation do not create selection privilege. Ordinary configured installation preference and deterministic capability identity ordering remain the only ordering inputs after compatibility.

No rejected security-decision object is created. A non-composable mechanism is simply unreachable for that request.

## Invocation

Kernel reserves the selected resources and invokes the adapter with only the typed computation and execution mechanics needed for timeout/cancellation/attempt handling.

The adapter owns provider-specific protocol translation. Provider request/response structures, endpoint details and transport behavior stay inside the adapter.

Current shipped reasoning realizations are text inference through:

- llama.cpp AF_UNIX transport;
- explicit llama.cpp loopback-HTTP compatibility transport;
- OpenAI-compatible HTTP transport.

All implement the same typed text-inference reasoning contract. No mechanism is silently enabled merely because its adapter JAR is installed.

## Module-owned interpretation

A reasoning result is not Material. Kernel neither assigns Material identity nor chooses output Sensitivity.

The originating Module:

1. receives the reasoning result;
2. interprets it according to its bounded behavior;
3. creates new Material when the result has semantic value;
4. updates Module state/presentation as appropriate;
5. invokes another bounded Operation or stops.

For a PUBLIC Operation, the normal public boundary still applies after this interpretation: Module-owned semantic transformation must create new declared Material whose Sensitivity can reach `Privacy.PUBLIC` before the result leaves the runtime.

There is no result object that can execute an Operation or submit more work by itself.

## Immediate and durable reasoning

Immediate reasoning uses the same registry selection, resource coordination, failure mapping and retry semantics as durable reasoning, but returns the result to the waiting Module call.

Durable reasoning stores only the runtime state needed to survive restart. SQLite persists opaque serialized computation bytes, stable reasoning-contract identity, Module identity for delivery, priority/eligibility/timeout/cancellation/retry state, attempts/failure category and opaque result bytes until collection and acknowledgement.

Queued input survives restart. Interrupted running work returns to an eligible state under the runtime's recovery rules. Successful output survives restart until collected/acknowledged or removed by retention cleanup.

Durable persistence does not store concrete adapter implementation class names. On restart, persisted work is resolved through the stable reasoning-contract identity and becomes runnable once a compatible contract/mechanism is registered again.

The Kernel cannot inspect persisted computation bytes as Module knowledge.

## Installation and execution failures

Reasoning failures use stable reasoning categories such as unavailable, timeout, cancelled, connection, protocol, remote failure, internal and interrupted. Retry is bounded by the request's `ReasoningRetryPolicy`.

The reasoning registry rejects duplicate capability identity and conflicting computation-contract identity/type declarations. Provider materialization rejects null/invalid mechanism values. Invalid enabled provider configuration fails startup. Application assembly rolls back registrations/loaders if reasoning installation startup fails, leaving no half-registered mechanism state.

Absence of an installed/available compatible mechanism is a reasoning-runtime condition, not a platform boot failure. MADRE can start with an empty reasoning registry. An immediate reasoning request fails as unavailable when no compatible available mechanism can execute it.

## Search and other external I/O

SearXNG is not a `ReasoningCapability`. The repository's SearXNG integration is an ordinary Java client over the reusable web-search value model. A domain Module may use that client directly when web search belongs to its behavior.

The former standalone WebSearch Module and generic search Kernel capability are intentionally removed. Reintroducing search into Kernel would require a new concrete shared-Kernel responsibility, not analogy with tool-calling frameworks.
