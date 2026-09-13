# MADRE Master Development Plan

## Objective

Deliver one complete local MADRE environment that the Owner can install and use as
the foundation for a PhD Module.

Completion means all of the following work together:

```text
owner interaction
    -> shipped ordinary Module assigned to CORE
    -> Module-owned Agent and bounded Operation
    -> Module-created physical work request
    -> Kernel selection, scheduling and durable execution
    -> real llama.cpp or OpenAI-compatible Capability
    -> opaque physical result
    -> CORE Module interpretation and new Material
    -> visible response or Module-owned background continuation
```

The public SDK must also be suitable for another independently implemented Module.
A passing fixture, endpoint stub, empty extension point, or serialized example does
not satisfy this objective.

Development proceeds in the large coherent slices below. Each slice is committed and
pushed only after its complete boundary works. Pull-request merging remains an Owner
action.

## Technology baseline

Use Java 21 for the complete implementation. Do not retain or translate the removed
Python architecture.

Use a Gradle Kotlin DSL multi-project build with the Gradle wrapper committed. Initial
Maven coordinates use the Owner's public namespace:

```text
group: io.github.didacll
artifacts:
  madre-algebra
  madre-sdk
  madre-kernel
  madre-text-inference
  madre-adapter-llamacpp
  madre-adapter-openai-compatible
  madre-module-owner-interaction
  madre-app
```

The modules are dependency boundaries, not one-class packages:

- `madre-algebra` is a small dependency-free public value library.
- `madre-sdk` is the public Module programming model and codecs.
- `madre-kernel` owns registries, physical work, routing, scheduling and storage.
- `madre-text-inference` defines the first physical Capability command and result.
- each adapter binds that physical contract to one connector family;
- `madre-module-owner-interaction` is the shipped ordinary Module implementation;
- `madre-app` assembles an installable local environment and assigns CORE.

Prefer the JDK, Jackson for explicit JSON codecs, SQLite JDBC for the physical work
queue, and SLF4J with a small local backend for runtime logs. Add a concrete local
transport only with the first external-process Module that uses it; do not represent
direct in-process calls as transport. Do not introduce Spring, dependency injection
frameworks, workflow engines, actor systems, or plugin frameworks.

Public domain classes never inherit from JSON, HTTP, SQLite, or framework types.

## Public model to implement

### Algebra

Implement five nominal enums or final value types:

- `Sensitivity`
- `Privacy`
- `Integrity`
- `Risk`
- `Autonomy`

Each exposes its rank. Sensitivity exposes same-type maximum composition. Privacy and
Integrity expose same-type minimum composition. Cross-carrier method calls cannot
compile.

Reachability uses the intrinsic ordering between one accumulated Sensitivity and one
accumulated Privacy. The implementation returns or constructs the combined same-type
value; it creates no domain result object.

`Privacy.UNKNOWN` is explicit P2. No constructor or codec supplies it as a default
for omitted data.

### Identities and Material

Create nominal immutable identities only for real independently identifiable
objects:

- `ModuleId`
- `AgentId`
- `SkillId`
- `WorkflowId`
- `OperationId`
- `EffectProfileId`
- `MaterialId`
- `MaterialTypeId`

Child identities include their owning Module identity where ownership is real.

`MaterialType<T>` declares a Module semantic content type and its codec.
`Material<T>` contains its identity, owning Module, type, typed payload, and
Sensitivity.

Creating adapted Material always requires a new `MaterialId` and explicit
Sensitivity. No Material API accepts ancestry, derivation, freshness, completed
output, security history, or permission data.

### Module declarations

Implement immutable:

- `ModuleDefinition`
- `AgentDefinition`
- `SkillDefinition`
- `WorkflowDefinition`
- `OperationDefinition<I, O>`
- `EffectProfile`

An Operation directly declares:

- accepted Material types and the Privacy of that receiving boundary;
- Material types it may produce and their promised maximum Sensitivity;
- its EffectProfiles when consequential execution variants exist.

Use nominally typed maps or cohesive immutable collections where multiple accepted or
produced types are required. Do not create input-surface, output-surface, or
responsibility-surface identities or objects.

An Agent references its actual Skills, Workflows, and exposed Operations. Its
effective Privacy is derived from the referenced Operations.

A Module owns its canonical definitions and validates reference resolution. Reuse is
legal: an Agent or Workflow may reference the same Skill, Workflow, or Operation when
the Module intends it. The Module derives its effective Sensitivity for an exact
state from currently reachable Material and declared outputs.

Definitions contain no behavior class names, prompt text, provider payload,
Capability identity, generic metadata map, or import path.

### Behavior ports

Expose only ports exercised by real behavior:

- `ExecutionService` accepts physical work from Module behavior;
- `Operation<I, O>` implements one declared bounded Operation;
- `ModuleDirectory` lists exact currently reachable Module behavior;
- `ModuleRegistration` registers the running Module definition.

A later concrete cross-Module behavior defines the smallest invocation port required
by that exact Operation. The first useful installation does not invent a generic
Module invocation engine.

Agents, Skills, and Workflows remain Module-owned behavior and data. The SDK does not
provide a universal Agent loop, assistant turn, planner, or workflow interpreter.

### Operation construction

A bounded Operation call binds:

- one exact Operation;
- one of that Operation's EffectProfiles;
- actual input Material;
- the Integrity values of actual non-user causal participants.

The constructor applies information reach and non-user causal composition. A later
WorkRequest carries that same profile's Risk so Kernel selection can compose it with
the physical Capability selected at that later boundary. The Module cannot name or
pretend to know that physical realizer in advance.

Operations without consequential EffectProfiles do not receive dummy Risk, Autonomy,
or Integrity values.

### Codecs

Provide versioned JSON codecs as separate adapter classes. Domain objects are created
and used directly.

Codecs must reject:

- missing required algebraic facts;
- unknown fields;
- identity-category substitution;
- unresolved definition references;
- generic executable or metadata payloads.

The serialized form contains each canonical declaration once and references it by
nominal identity. A future XML codec must be possible without changing the domain
model.

## Physical Capability model

### Capability SPI

Kernel owns the Capability SPI. The generic connector shape is:

```text
Capability<C, R>
    manifest() -> CapabilityManifest<C, R>
    availability() -> physical availability
    execute(C command, ExecutionContext context) -> R
```

`C` and `R` are physical command and physical result types. They are not Material
types.

A Capability manifest contains only:

- `CapabilityId`;
- the physical command/result contract implemented by the adapter;
- explicit receiving Privacy;
- physical-realizer Integrity where applicable;
- physical location, latency and resource claims that are actually used;
- bounded connector options supported by that adapter.

The adapter invocation receives only the physical command, bounded connector options,
timeout/cancellation, and physical execution context. It receives no Module,
Material, Agent, Operation, or algebra object.

### Initial text-inference contract

The first public physical extension is text inference:

```text
TextInferenceCommand
    prompt text
    bounded generation options used by both real adapters

TextInferenceResult
    generated text
    physical completion metadata required by Module behavior
```

Begin with only options required by the shipped CORE Operations and real connectors,
such as maximum generated tokens and stop sequences. Add temperature, context limits,
structured output, or provider-specific controls only when the real implementation
uses them.

Do not model generic computation kinds. The Java command type is the physical
contract. A future deterministic connector introduces its own command/result
interface rather than expanding a universal property dictionary.

### Work request

`WorkRequest<C, R>` contains:

- originating `ModuleId`;
- one physical command `C`;
- accumulated Sensitivity of information encoded in that command;
- applicable physical demand only when required by an actual bounded effect;
- immediate or durable execution mode;
- priority, eligibility time, timeout, cancellation, and physical retry policy;
- typed physical preferences presently used by Kernel.

It contains no Material, MaterialType, Agent, Operation, Capability identity, output
Material declaration, or semantic continuation.

Module-side builders may accept Material to construct prompt text and accumulated
Sensitivity, but their output is the physical WorkRequest above.

### Selection

The live Capability registry:

1. considers adapters implementing the request command type;
2. retains only manifests whose applicable algebraic values compose with the request;
3. retains only currently available adapters whose resource claims can be satisfied;
4. applies typed request preferences and installation configuration;
5. selects deterministically;
6. records only the adapter actually invoked as physical telemetry.

Non-composable adapters are never materialized as rejected candidates. If the
reachable collection is empty, immediate work reports unavailability. Durable work
waits or ends only according to its ordinary timing and retry contract.

Normal Module behavior does not identify a Capability. A future advanced-user option
may constrain physical properties or a connector family through a typed request
extension justified by an actual use. It must not become an arbitrary map.

## Kernel runtime

### Durable work

Implement one dispatch path shared by immediate and durable work.

SQLite stores:

- serialized opaque WorkRequest command bytes;
- originating Module identity for delivery;
- timing, priority, cancellation, and physical retry state;
- selected-attempt telemetry;
- serialized opaque physical result until delivery.

A schema change recreates development storage. No migration from the removed Python
prototype is required.

Queued physical input survives restart. Running work recovered after interruption
follows its physical retry policy. A pending result survives restart until its Module
collects or acknowledges it. Payload cleanup removes input after successful execution
and output after delivery or retention expiry.

The Kernel cannot query or reuse queued bytes as Module knowledge.

### Resource coordination

Resource claims and capacities are physical values. The same coordinator supports an
exclusive loaded-model slot, CPU or GPU capacity, and future physical resources
without connector-specific Kernel branches.

Do not hard-code `local && heavyweight` logic. The llama.cpp installation declares
the actual resources it uses.

### Module registry and CORE resolution

The live registry stores running Module definitions in memory. It is empty after
restart and repopulates as Modules start.

Directory queries provide accumulated current values and return only reachable
Modules, Agents, and Operations. The response excludes unrelated internal Module
state and every Capability identity.

Installation configuration contains:

```text
roles.core = <ordinary ModuleId>
```

Kernel resolves that identity when the Module registers and exposes it to the local
application as the default interaction Module. CORE assignment does not change the
definition.

### Future Module invocation

The current foundation does not invent a universal Module invocation contract. The
first real cross-Module behavior will add the smallest transport its bounded
Operation requires, without moving Material interpretation into Kernel.

### Future local transport

The first useful installation injects SDK ports directly because its Module shares
the application process. The first external-process Module must add an actual local
transport, bound to loopback by default, for the exact boundaries it uses:

- Module registration and removal;
- reachable Module directory queries;
- immediate physical work;
- durable submission, inspection, cancellation, and result delivery;
- CORE role resolution and health.

There is no MADRE authentication or authorization model. Exposure beyond the owner's
local environment is an explicit deployment concern outside this first release.

## Real physical adapters

### llama.cpp

Implement `LlamaCppCapability` for a configured running `llama-server` instance.

Its installation configuration includes endpoint, model alias, explicit Privacy,
Integrity, latency, and resource claims. Locality does not supply the algebraic
values.

The adapter:

- checks real server availability;
- converts `TextInferenceCommand` to the llama.cpp protocol;
- prevents command data from overriding installed endpoint or model;
- returns generated text as `TextInferenceResult`;
- maps timeout, connection, HTTP, cancellation, and malformed response into ordinary
  physical failure categories;
- owns no model lifecycle unless a later concrete requirement adds it.

Acceptance uses a real local llama.cpp server and a real model, not an HTTP fixture.

### OpenAI-compatible

Implement a separate OpenAI-compatible Capability using the same physical text
contract.

The adapter owns protocol translation and configured endpoint/model. It accepts an
externally prepared transport so the provider's own account/session mechanism can be
used without introducing credential fields into MADRE.

The first release must work with an unauthenticated OpenAI-compatible endpoint. A real
externally authenticated provider is exercised only when the Owner supplies an
external transport/session; missing provider access does not block local acceptance.

## Shipped CORE-capable Module

`madre-module-owner-interaction` is an ordinary Module artifact. Its public
definition contains:

- one interaction Agent;
- standard-prompt Operation;
- fast-lane Operation;
- the Skills and Workflows genuinely used by those Operations;
- Material types for owner prompt, immediate answer, background analysis, and any
  visible follow-up;
- explicit input Privacy and output Sensitivity contracts.

Its behavior is bound privately and is absent from serialized definitions.

### Standard prompt

1. Accept owner prompt Material.
2. Construct one `TextInferenceCommand`.
3. Submit ordinary physical work through `ExecutionService`.
4. Receive `TextInferenceResult`.
5. Interpret it and construct new answer Material with a new identity and explicit
   Sensitivity.
6. Present or return that Material.

### Fast lane

1. Accept owner prompt Material.
2. Construct a low-latency foreground text-inference request.
3. Construct a durable background text-inference request over the same owner request,
   asking for harder work that would materially improve or continue the response.
4. Submit both through the same ordinary Kernel execution service.
5. Present the foreground answer as soon as it returns, without waiting for the
   background request.
6. Interpret the background result inside CORE when it becomes available.
7. Construct independent background-result Material.
8. Present a useful improvement or start another explicit CORE-owned bounded
   Operation; otherwise stop.

No model response contains an executable command. CORE behavior parses and interprets
text only inside its own bounded implementation.

Fast lane receives no Kernel priority privilege beyond the explicit ordinary priority
and durability fields available to every Module.

### Initial owner interface

Provide a simple local console interface in `madre-app`:

- `/standard <prompt>` invokes standard prompt;
- ordinary text invokes fast lane;
- foreground output appears without waiting for background completion;
- completed background work is surfaced clearly when available;
- physical failures remain physical failures and do not become model dialogue.

The interface is replaceable Module/application presentation, not a universal SDK
chat abstraction.

## Large development slices

### Slice 1 — Java public foundation and live Module boundary

Deliver together:

- Gradle multi-project build and Java 21 CI;
- complete algebra;
- nominal identities, Material, EffectProfile and Module definitions;
- JSON codecs;
- Operation construction;
- live Module registry, reachability, and CORE role resolution;
- SDK publication metadata and generated API documentation.

This slice is complete only when an independently compiled consumer project can use
the built `madre-sdk` artifact to define a Module without depending on Kernel or
CORE. Mathematical and codec tests belong here; no test Module is presented as a
product implementation.

Commit and push one coherent foundation checkpoint.

### Slice 2 — Complete Kernel, durable work and real Capabilities

Deliver together:

- generic physical Capability SPI;
- text-inference command/result contract;
- deterministic registry selection through carried algebraic values;
- resource coordination;
- immediate and SQLite-backed durable execution;
- scheduling, cancellation, restart recovery, physical retry, and result delivery;
- loopback runtime transport;
- real llama.cpp adapter;
- OpenAI-compatible adapter;
- installation configuration with explicit algebraic values and no credential model.

This slice is complete only after real text is obtained from a running llama.cpp
server through the installed Kernel path and a durable result survives Kernel
restart.

Commit and push one coherent runtime checkpoint.

### Slice 3 — Shipped CORE Module and usable distribution

Deliver together:

- real ordinary CORE-capable Module definition and behavior;
- standard-prompt and fast-lane Operations;
- Module-owned Material construction and semantic continuation;
- installation assembly and CORE assignment;
- local console interaction;
- distribution archive and startup/stop instructions;
- public SDK source/Javadoc artifact;
- complete owner acceptance journey.

This slice is complete only when the Owner can start llama.cpp, start MADRE, enter an
ordinary prompt, receive a real foreground answer, observe independent durable
background inference, restart Kernel where required by the journey, and use the SDK
artifact as the dependency for a new PhD Module.

Commit and push the exact accepted head. Do not merge PR #47.

## Verification

### Invariant tests

Keep tests that can falsify stable public behavior:

- same-carrier aggregation and compile-time carrier separation;
- exact information reach examples;
- Operation EffectProfile construction;
- independent Material adaptation;
- derived Module Sensitivity and Agent Privacy;
- declaration reference invariants and JSON round-trip;
- Capability SPI isolation from Module-domain types;
- durable work recovery, physical retry and delivery;
- live registry reset and reachability;
- CORE assignment to an ordinary Module.

Tests do not contain a substitute product Module. CORE tests exercise the shipped
CORE artifact. Connector protocol tests may use a controlled server for failure
edges, but real acceptance additionally uses llama.cpp.

### Architecture checks

The build fails when active production code introduces:

- Python source or packaging;
- Material or MaterialType references in Capability packages;
- autonomous input, output, responsibility, or security surface types;
- generic all-facet security containers;
- algebra decisions, evidence, history, or checking services;
- derivation, ancestry, freshness, completed-output, or security retry state;
- direct-user or owner-presence exceptions;
- credential, authorization, role-permission, clearance, or provider-trust models;
- CORE subclasses, turns, continuations, privileges, or special execution lanes;
- universal Agent loops, assistant abstractions, model tool-call execution, or
  planner frameworks;
- generic `Map<String, Object>` public extension bags;
- Module-selected Capability identities;
- Kernel-created Material.

The word CORE is expected in installation-role and shipped-Module documentation and
code. Its presence is not itself contamination.

### Real acceptance

Run at the exact release head:

1. build and test all Java modules;
2. publish SDK artifacts to an isolated local Maven repository;
3. compile a fresh external consumer against only those SDK artifacts;
4. start a real llama.cpp server with an owner-selected local model;
5. start the assembled MADRE application with explicit Capability values and CORE
   assignment;
6. execute standard prompt and verify real returned text becomes Module-owned
   Material;
7. execute fast lane and verify foreground response arrives independently of durable
   background inference;
8. restart Kernel with work pending and verify physical continuation/result delivery;
9. configure the OpenAI-compatible adapter against an available compatible endpoint
   and verify protocol execution without MADRE credential fields;
10. inspect SQLite and confirm it contains only Kernel physical-work state;
11. inspect the active tree and built dependency graph against the architecture
    checks above;
12. build the installable distribution, sources artifact, Javadoc artifact, and
    public SDK JAR.

Record exact commands, model, connector versions, outputs, unexercised external
provider behavior, and the final pushed commit.

## Delegation protocol

Once the clean architecture checkpoint is pushed, delegate the three slices in order.
Each implementation agent receives this whole plan and `MADRE.md`, works on PR #47,
commits and pushes its complete slice, and does not merge.

An agent must not create a narrower substitute plan. If it cannot complete its slice,
it commits only a coherent usable boundary and reports the exact missing acceptance
step; it must not add placeholders, fake Modules, or tests claiming the missing
behavior.

Before starting a later slice, the implementing agent verifies the previous slice at
the pushed head. The final integration agent reviews the complete active tree rather
than trusting earlier test claims.
