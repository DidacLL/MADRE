# Implementation Baseline

This document records executable behavior on the current branch. Product meaning and
ownership remain defined by `MADRE.md`.

## Public Module SDK

`madre_sdk` provides immutable, slotted domain objects with constructor-owned
invariants:

- nominal identities for Modules and each Module-owned definition;
- distinct Sensitivity, Privacy, Integrity, Risk, and Autonomy carriers;
- role-specific input, output, and responsibility surfaces;
- typed independent Material and nonempty Material sets;
- canonical Module, Agent, Skill, Workflow, Operation, and EffectProfile definitions;
- bounded Operation-call construction from one profile and its actual participants;
- typed physical work requests and raw physical results;
- versioned JSON codecs for definitions, directory entries, Material sets, work
  requests, and physical results;
- narrow execution, Operation-implementation, and Module-directory ports.

Definitions contain structure but no executable behavior. Pydantic is used only by
private wire and installation DTOs, not as the public domain inheritance model.

## Kernel physical execution

Installed Capability identities, definitions, and adapter bindings belong to the
`madre` Kernel package. A Module supplies a computation contract, its actual Material,
and typed physical requirements and preferences. It does not supply or receive a
Capability identity.

The live Capability registry considers computation and Material contracts, direct
composition of carried Sensitivity with explicitly installed receiving Privacy,
current availability, typed physical properties, and request preferences. Resource
coordination operates on typed resource claims. Kernel forwards payloads opaquely and
returns `PhysicalResult`; it never creates Material.

The OpenAI-compatible adapter is one private physical protocol boundary. Installation
supplies its endpoint, model, Material contracts, physical properties, resources, and
Privacy explicitly. Submitted payload cannot override the installed model or streaming
mode. Connection mechanics are injected into the adapter and are not MADRE domain
objects.

## Durable physical work

Immediate and queued work share Kernel's physical invocation path. SQLite stores only:

- opaque queued request snapshots needed across restart;
- scheduling state and physical attempt telemetry;
- raw physical results pending delivery.

Queued input and pending output survive restart. Successful execution removes the
queued input; delivery removes the raw output; cancellation removes queued input; and
failed input has explicit lifecycle cleanup. Capability unavailability defers queued
work without starting an attempt. Physical interruption or mechanism failure follows
the request's physical attempt policy.

The development database format is recreated when its schema fingerprint changes.
There is no installed-base compatibility or migration path.

## Live Module directory

Running Modules register their immutable definitions in an in-memory registry. The
registry begins empty after restart and returns only the public Agent and Operation
definitions whose exact input surfaces can receive the caller's current Material.
Module definitions are not written to SQLite.

The local FastAPI boundary exposes registration, removal, reachability, immediate
execution, durable submission, inspection, cancellation, and physical-result delivery
using the same explicit codecs.

## Exercised behavior

Focused tests establish:

- carrier aggregation, typed separation, independent Material adaptation, and bounded
  Operation construction;
- structural Module Sensitivity and Agent Privacy;
- full declarative definition JSON round-trip;
- a private fixture Module making a first request, interpreting the raw result,
  constructing new Material, and making its own second request;
- Capability selection by actual contracts and carried values without Module knowledge
  of the installed mechanism;
- restart survival, physical retry, output delivery, cleanup, and the work-only SQLite
  schema;
- live Module registration and exact reachable-surface filtering, including through
  HTTP;
- explicit installation Privacy and adapter-owned request properties.

The repository ships no Module or default interaction implementation. The executable
Module path is exercised only by a private integration fixture using the public SDK.
