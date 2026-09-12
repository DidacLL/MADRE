# MADRE Platform Architecture

## Ownership

```text
Module-owned domain
  Material, meaning, state, Agents, Skills, Workflows, Operations
            |
            | typed physical work request
            v
Kernel runtime
  live registries, selection, resources, queue, scheduling, delivery
            |
            | typed Capability invocation
            v
Physical mechanism
  inference or deterministic computation
            |
            | physical result
            v
Module-owned domain
  interpretation, new Material, continuation or stop
```

The SDK supplies the shared domain values and narrow ports. The algebra is behavior of
those values, not a box in this runtime diagram.

## Module boundary

A Module owns all decisions requiring semantic understanding. It constructs and
classifies Material, defines its public surfaces, implements its Agents and bounded
Operations, interprets physical output, and decides whether to continue.

Module definitions canonically own their Agent, Skill, Workflow, and Operation
definitions. Public references use nominal identities and constructors verify
ownership and resolution. None of these building blocks imposes a default behavior.

## Kernel boundary

Kernel maintains two live registries:

- running Module definitions available for public discovery;
- installed physical Capability definitions and their adapter bindings.

Kernel selects a physical mechanism from the computation contract, actual carried
values, physical requirements, current availability, resource state, and request
preferences. Modules neither name nor inspect installed Capabilities.

Kernel may queue Material payload bytes as opaque work input and buffer raw physical
results for delivery. It may inspect typed request metadata and algebraic values
needed for selection. It does not inspect semantic payload meaning, create Material,
choose output classification, or interpret results.

The durable store contains work lifecycle snapshots, scheduling data, physical
attempt telemetry, and pending result delivery. Module definitions are never stored
there; the Module registry starts empty after restart.

## Capability extension boundary

A Capability definition belongs to Kernel. It declares nominal identity, supported
computation and material contracts, explicit receiving Privacy, applicable physical
responsibility, location and latency properties, and typed resource claims. Its
adapter performs one physical invocation and returns the declared result.

Physical requirements and preferences are segregated value objects with matching or
ranking behavior. Resource coordination operates on generic `ResourceClaim` values.
Adding a new mechanism, property, or resource must not require provider-specific
Kernel branches.

Provider payload DTOs and protocol details remain private to adapters. Installation
supplies algebraic facts independently of provider and locality.

## Public Module directory

Starting Modules register their immutable definitions in memory. The public directory
returns only Module, Agent, and Operation surfaces reachable from the caller's exact
current carried values. The returned definitions describe options; invoking another
Module will be introduced only with a concrete behavior that establishes the required
port.

## Installation configuration

Installation may assign an ordinary Module to a default interaction role. Kernel and
the SDK neither inspect nor change behavior for that assignment.
