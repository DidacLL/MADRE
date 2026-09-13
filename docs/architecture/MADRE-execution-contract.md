# MADRE Physical Execution Contract

## Module-created work

A Module Operation converts its Material into a bounded physical command. For the
initial implementation that command is text inference: text input plus a small,
strongly typed set of generation options required by the real llama.cpp and
OpenAI-compatible connectors.

The Module submits a work request containing:

- originating `ModuleId`;
- the physical command and opaque input;
- accumulated Sensitivity of the information present in that input;
- other algebraic demand only when the concrete physical connection genuinely uses
  it;
- immediate or durable timing;
- priority, timeout, and physical retry configuration;
- typed physical preferences supported by the current Kernel contract.

It contains no Material, Material type, Agent, Operation, concrete Capability
identity, future Material identity, output Sensitivity, or semantic continuation.

The SDK work builder accepts Material as a Module-side convenience only while
constructing the accumulated values and physical input. The resulting Kernel request
contains neither Material identity nor Module payload semantics.

## Capability selection

Kernel begins with the registered adapters that accept the request's physical command
type. For each installed manifest, the request's carried values either compose with
the manifest values or that Capability is unreachable.

Kernel then applies:

- current availability;
- resource capacity;
- installation rules;
- the request's typed physical preferences;
- deterministic tie-breaking.

No rejected candidate is created. If no Capability is currently reachable, immediate
work returns ordinary unavailability. Durable work remains governed by its scheduling
and physical retry configuration; algebra creates no separate state.

Modules do not need a Capability name for normal work. Advanced owner-directed
preferences may be added as typed physical constraints when a real use requires
them. They must not become a generic property map.

## Invocation

Kernel reserves physical resources and invokes the selected adapter with only:

- the physical command/input;
- connector options already bounded by the command contract;
- timeout and cancellation mechanics needed for execution.

The adapter cannot inspect MADRE domain objects. Provider-specific requests and
responses stay inside the adapter.

llama.cpp and OpenAI-compatible text inference both return a physical text result.
Kernel records which installed Capability actually ran as ordinary attempt telemetry
and delivers the text result to the originating Module.

## Module-owned result

Physical output is not Material. The Module Operation:

1. receives the physical result;
2. interprets it according to its own behavior;
3. constructs new Material with a new identity and explicit Sensitivity when the
   result has semantic value;
4. updates its own state or UI;
5. submits another work request, invokes another bounded Operation, or stops.

No physical result type has an execution method or a reference to Kernel.

## Immediate and durable work

Immediate work returns its physical result to the waiting Module.

Durable work stores an opaque serialized request until physical execution and stores
an opaque physical result until the Module collects it. Kernel persists only
scheduling, attempts, delivery state, and those temporary bytes. Restart recovery
continues eligible work.

Physical failure may be retried according to the submitted policy. Lack of a currently
reachable Capability may wait when the work was deliberately submitted as durable.
Neither creates Module-domain state.

## Connector environment

The initial adapters are:

- a llama.cpp text-inference connector, optimized for the owner's local path;
- an OpenAI-compatible text-inference connector for an endpoint configured by the
  installation.

MADRE owns neither connector authentication nor provider accounts. An externally
prepared transport may supply the provider's own connection session. The local
llama.cpp path requires no account mechanism.
