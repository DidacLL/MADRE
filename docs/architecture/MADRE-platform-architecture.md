# MADRE Platform Architecture

## Responsibility map

```text
Owner
  |
  v
ordinary Module assigned to CORE role or another Module
  | owns Material, meaning, Agents, Operations and continuation
  | creates an opaque physical work request
  v
Kernel
  | live Module and Capability registries
  | selection, routing, resources, scheduling, queue and delivery
  v
Capability adapter
  | invokes one installed physical mechanism
  v
physical output
  | delivered opaquely
  v
originating Module
  | interprets output, creates new Material and continues or stops
```

The algebra is behavior of the values carried by these objects. It is not a runtime
component in the diagram.

## Module boundary

A Module is the semantic and application boundary. It owns:

- its domain state and persistence;
- Material identities, types, payloads and Sensitivity;
- Agents, Skills and Workflows;
- public and private Operations;
- conversion from Material to physical connector input;
- interpretation of physical output;
- creation of new Material;
- continuation and user presentation.

An Operation is the bounded entry to Module behavior. It declares the Material it can
accept, the receiving Privacy that applies, the Material it may produce, and its
EffectProfiles when it has consequential variants. Those declarations are direct
parts of the Operation; they are not separately identified surface objects.

Module behavior calls the SDK directly while composing its actual values. Correct
construction is the implementation model, not a voluntary call to a separate
service.

## Kernel boundary

Kernel owns mechanisms shared across Modules:

- an in-memory registry of running Module definitions;
- an installation registry of physical Capability manifests and adapter bindings;
- resolution of the configured CORE Module identity;
- Capability selection from request values, installed manifest values, current
  availability, resources, and deterministic configuration;
- immediate and durable physical work;
- timing, priority, timeout, cancellation, resource coordination, and retry after
  physical failure;
- opaque result delivery;
- ordinary runtime logging.

Kernel does not own Module definitions after restart, Module state, Material,
semantic workflows, or Operation behavior. It does not create or classify output.

The live Module registry makes exact public Agents and Operations reachable to other
Modules. Kernel does not interpret them. Invocation enters the target Module's
ordinary bounded Operation interface.

## Capability boundary

A Capability is a connector registered in Kernel. Its manifest describes only the
installed mechanism:

- Kernel identity;
- physical command/input and output contract;
- explicit receiving Privacy;
- Integrity where the connector is an actual physical realizer;
- availability and physical properties;
- required resources;
- connector configuration and supported bounded options.

The adapter accepts the physical command and returns physical output. It contains no
Material, Module, Agent, Workflow, Skill, or Operation reference.

The first physical contract is text inference. llama.cpp and OpenAI-compatible
adapters implement that same physical contract without exposing their private
protocol payloads to Modules. New physical contracts are introduced only when a real
connector requires them; they extend the Capability SPI rather than expanding one
generic dictionary.

## CORE installation role

Installation configuration assigns `CORE` to one ordinary `ModuleId`. Kernel
resolves that identity in the live registry. The surrounding application sends
default owner interaction to that Module.

A CORE candidate must expose the ordinary public behavior required by the role. The
shipped candidate provides standard-prompt and fast-lane Operations through its
ordinary interaction Agent. No CORE subtype, privileged path, special algebra, or
Kernel scheduling lane exists.

## Storage

Module persistence stays inside each Module.

Kernel persists only physical work that must survive restart:

- opaque queued input;
- scheduling and attempt state;
- pending physical output until delivery.

Runtime cleanup removes payload bytes according to delivery and retention. The live
Module registry is rebuilt when Modules start.

## Provider environment

MADRE configuration describes the connector endpoint and physical behavior. Account
sessions, credentials, authentication flows, and provider permissions are supplied
outside the MADRE model. They do not alter Capability Privacy or Integrity.
