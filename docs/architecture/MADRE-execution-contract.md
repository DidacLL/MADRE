# MADRE Physical Execution Contract

## Work request

A Module submits a typed `WorkRequest` containing:

- its nominal `ModuleId`;
- the actual nonempty `MaterialSet` to be forwarded;
- a typed computation contract and expected physical result type;
- typed physical requirements and preferences;
- timing, timeout, priority, and physical retry policy.

The request contains no concrete Capability identity and no future Material identity,
classification, Operation, Agent instruction, or semantic continuation.

## Selection and invocation

Kernel considers only installed Capabilities that implement the computation and
material contracts. It attempts immutable composition of the request's carried
Sensitivity with each Capability's explicit receiving Privacy. Capabilities that
cannot join simply do not enter the selectable set.

Kernel then applies physical requirements, availability, resource state, and typed
preferences through injected selection and coordination strategies. If no mechanism
can currently satisfy the work, the outcome is ordinary Capability unavailability.
Durable work may try later according to its physical retry policy.

For the selected mechanism Kernel records ordinary attempt telemetry, reserves its
typed resources, and forwards the Material payload opaquely. The adapter returns a
typed `PhysicalResult` containing physical output and mechanical metadata.

## Result ownership

Kernel returns or buffers `PhysicalResult`; it never returns Material. The requesting
Module interprets the output and decides whether to construct independent new
Material, submit another request, invoke a bounded Operation, present a result, or
stop.

Physical output has no execution interface. Text or structured data resembling an
Operation cannot invoke anything.

## Durable work

The queue serializes an opaque snapshot of the submitted payload together with the
typed work contract and carried values required to reproduce physical dispatch after
restart. A completed raw result remains in a delivery buffer until retrieved or
acknowledged.

Retention and cleanup remove input and output bytes according to work lifecycle and
delivery policy. Compact scheduling and physical attempt telemetry may remain. Queue
storage cannot query, reinterpret, transform, or reuse payload bytes as Module domain
knowledge.

Immediate and durable execution share one dispatch path. Idempotent submission,
cancellation, eligibility, priority, timeout, restart recovery, resource waiting,
transport interruption, and physical failure are runtime mechanics.

## Adapter contract

An adapter receives a typed invocation fixed by the selected Capability definition.
Submitted payload cannot override configured endpoint, model, computation contract,
or other immutable mechanism properties. Provider-specific dictionaries and response
DTOs stay private to the adapter. Connection setup is supplied by its environment.
