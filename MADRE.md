# MADRE

MADRE is personal software for one owner. It gives that owner a modular environment
for using local and external inference or deterministic mechanisms while retaining
control over which information can reach each mechanism.

MADRE is not a hosted AI platform and does not protect the owner from software the
owner deliberately installs. Its purpose is to make information reach explicit and
structural, especially when a Module could expose owner information to a third-party,
SaaS, cloud, public, or otherwise less-private boundary.

The owner can install, replace, configure, or remove every Module and Capability.

## Host platforms

Windows and Linux are first-class hosts for the same MADRE application, Kernel, SDK,
Modules, persistence and ordinary execution path. Windows is not a compatibility port
of a Unix implementation, and the public runtime must not depend on one Linux
distribution merely because that distribution is convenient CI infrastructure.

A physical mechanism may legitimately require a platform-specific Capability adapter.
That difference stays behind the Capability boundary and does not justify separate
Kernel, SDK, Module, Workflow or Security Algebra architectures. Supporting one useful
platform-specific connector does not reduce the required maturity of the shared
cross-platform product.

## Building blocks

A **Module** is an owner-installed application or integration. It owns meaning,
domain state, persistence, Material, transformations, Agents, the Skills it provides,
interpretation, continuation, user experience, and bounded Operations.

An **Agent** is a Module-owned intelligent actor. It has no universal loop or
assistant behavior. Its available behavior comes from learned Skills, Agent-owned
Workflows, and Operations its Module gives it.

A **Skill** is reusable Module-provided ability, knowledge, or instruction. Learning a
Skill may materialize as one or more Workflows for a particular Agent.

A **Workflow** is reusable Agent-owned semantic behavior. In the current minimal
model, a Workflow is an ordered sequence of Operations triggered together. A later
model may add conditional flow when real behavior requires it. A Workflow is not a
Kernel execution language or scheduler program.

An **Operation** is one explicitly bounded piece of Module behavior. It accepts
declared Material, may transform it into physical input, may request one or more
Capability executions through Kernel, interprets returned physical output, creates
new Material, and decides whether its Module continues. An Operation may also perform
a bounded effect that does not use inference.

An **EffectProfile** represents one bounded execution variant of an Operation. It
carries only that variant's Risk and Autonomy.

A **Material** is a typed value owned by a Module. It has its own identity, content
type, payload, and Sensitivity. Tokenizing, anonymizing, minimizing, selecting,
validating, summarizing, generating, or otherwise adapting information creates new
Material with a new identity and explicitly applicable values. The source Material is
unchanged.

A **Capability** is an installed connector to one physical inference or deterministic
mechanism. It exposes a manifest containing only its own physical contract,
configuration, availability, resource needs, and applicable algebraic values. It
accepts physical input and returns physical output. It knows nothing about Modules,
Agents, Operations, Material, or semantic continuation.

**Kernel** owns the shared physical runtime: live registries, Capability selection,
routing, resource coordination, scheduling, durable physical work, physical retries,
result delivery, and ordinary execution logging.

The **SDK** supplies the strongly typed, reusable, language-neutral construction
model used by every correct Module and Kernel extension. Domain objects are programmed
as objects. JSON or XML is only a boundary representation.

## CORE

CORE is an installation role assigned to one ordinary registered Module.

MADRE ships with a default CORE-capable Module so the installation is useful without
requiring another Module. The owner may replace it with any ordinary Module that
provides the required public behavior. Assignment to CORE does not change the
Module's type, algebraic values, execution path, or privileges.

The CORE role provides the installation's default interaction and fallback behavior.
A Module without its own user experience may make suitable Agents or Operations
reachable through CORE. CORE may also assist with installation-oriented interaction
through ordinary Module behavior.

The first shipped CORE-capable Module owns one ordinary interaction Agent with two
ordinary Operations:

- **standard prompt** performs one bounded inference request and returns the
  Module-interpreted result as new Material;
- **fast lane** starts a low-latency foreground inference for a prompt response while
  also submitting the same owner request for durable background inference that looks
  for harder work. The foreground result can be presented immediately. The same
  owner-interaction Module interprets the background result and decides what
  Module-owned continuation, if any, is useful.

Fast lane is Module behavior. It is not a Kernel lane, scheduler privilege, model
command, or special Capability.

The installed CORE assignment belongs to installation configuration and resolves to a
normal Module identity in Kernel's live Module registry.

## Security Algebra

MADRE uses five different ordered carriers:

- **Sensitivity** describes the confidentiality consequence of exposing information.
- **Privacy** describes the confidentiality boundary of a receiver.
- **Integrity** describes bounded causal or physical-realization responsibility.
- **Risk** describes the consequence of one Operation execution variant.
- **Autonomy** describes how much of that same variant remains machine-executed.

All five use ranks 1 through 5, but equal ranks never make different carrier types
interchangeable. Integrity does not mean truth, reliability, model quality, provider
reputation, or generic trust.

Each carrier combines intrinsically and immutably:

```text
Sensitivity: maximum
Privacy:     minimum
Integrity:   minimum
```

The result of combining values is another value of the same carrier. No event,
decision, record, permission, evidence object, or historical state is produced.

An object exposes only values meaningful to its role. There is no universal security
container with optional fields and no independent identity for the place where a
value applies.

Examples:

- Material carries Sensitivity.
- An Operation directly declares the Privacy of the Material it accepts and the
  Sensitivity promised by the Material kinds it can produce.
- An Agent's effective Privacy is the minimum Privacy of the Operations it exposes.
- A Module's effective Sensitivity is the maximum Sensitivity of the Material and
  outputs actually reachable through it.
- A Capability manifest carries the explicit Privacy of its receiving boundary and
  Integrity where it is an actual physical realizer.
- An EffectProfile carries Risk and Autonomy.

`Privacy.UNKNOWN` is the explicit P2 value for an applicable third-party boundary
outside the owner's control that is not declared fully public. It is not absent
knowledge and is never inferred from a provider, endpoint, adapter, model, process, or
locality. `Privacy.PUBLIC` is P1. Installation supplies Capability values explicitly.

### Information reach

At any attempted connection, the information being carried has one accumulated
Sensitivity and the receiver has one accumulated Privacy:

```text
S = maximum Sensitivity of the actual information being carried
P = minimum Privacy of the actual receiving boundary

the connection exists only when S <= P
```

This is intrinsic composition of the carried and receiving values. It is not a
separate service or lifecycle. When the values do not compose, that participant
cannot join and no combined value exists. A caller may create different Material,
choose a different reachable destination, or stop.

A Module managing S5 secrets may expose separate Operations that construct S4
tokenized Material, S3 anonymized Material, or S2 minimized Material. The original
Material remains S5. Those new values come from the Module's real transformation, not
from history or permission.

### Bounded consequence

For one EffectProfile with Risk `R` and Autonomy `A`:

```text
min(R, A) <= minimum Integrity of actual non-user causal participants
             or I5 when no non-user causal participant exists

R <= minimum Integrity of actual physical realizers
at least one physical realizer participates
```

Only the values of that one EffectProfile participate. Risk and Autonomy from
different profiles never combine. Owner presence does not change information reach.

The Module constructs its bounded Operation invocation through the SDK. The
Operation implementation receives a composition that already exists; no Kernel
policy object or security authority is involved.

## Physical execution

A Module converts its Material into the bounded physical input required by its
Operation. The SDK derives a work request from that already-composed Operation call;
Module code cannot separately substitute its identity, carried Sensitivity, or the
selected EffectProfile's Risk. The request contains:

- the originating Module identity;
- opaque physical input;
- the accumulated algebraic values applicable to that physical connection;
- ordinary Kernel controls such as timing, priority, timeout, scheduling, and
  physical retry;
- any explicitly supported physical preferences needed by that Module behavior.

The request contains no Material identity or Material type for Capability selection,
no concrete Capability identity, no semantic continuation, and no future output
Material.

Kernel compares only physical facts:

1. the physical command accepted by each registered Capability;
2. intrinsic composition of the request's carried values with the Capability
   manifest's applicable values;
3. current availability and resource state;
4. deterministic installation configuration and request preferences.

A Capability whose values cannot compose is unreachable for that request and never
enters the available collection. If nothing is currently reachable, immediate work
returns unavailable and durable work follows only its ordinary scheduling policy.

Kernel forwards the opaque physical input to the selected Capability. The Capability
returns opaque physical output. Kernel delivers or temporarily buffers that output.
The originating Module interprets it and may construct new Material.

Kernel never assigns Material identity, chooses Sensitivity, interprets output,
selects an Operation, or decides semantic continuation. Physical output has no path
that can execute an Operation or submit further work.

An Agent-owned Workflow does not weaken these boundaries. Each Operation in a
Workflow receives its own actual Material and, when physical work is needed, creates
its own work request through the same bounded Operation-call path. Workflow
coordination stays semantic behavior outside Kernel.

## Live Module reachability

Starting Modules register their definitions in Kernel's in-memory Module registry.
The registry begins empty after restart.

Kernel makes registered Modules, Agents, and Operations reachable to other Modules
according to their declared public boundaries and the caller's accumulated algebraic
values. Registry membership grants nothing and is not persisted as Module-domain
state.

Definitions describe ordinary MADRE objects and references. Executable behavior stays
inside the running Module. A later Module invocation uses the target Module's bounded
Operation entrypoint; Kernel provides reachability and transport but never interprets
the Operation.

User experience may trigger an Agent Workflow because the interaction Agent detects a
need for that behavior or because the owner selects it directly in a Module or CORE
UI. That trigger belongs to user-experience and Module behavior, not Kernel.

## Persistence and external mechanics

A Module owns all semantic and domain persistence.

Kernel storage contains only what its physical runtime needs: queued work, scheduling
state, physical attempt telemetry, and undelivered physical output. Queued payloads
remain opaque and are removed according to work delivery and cleanup.

Provider accounts, credentials, authentication, authorization, permissions, roles,
and connection sessions belong to the provider's adapter environment. They are not
MADRE concepts and never affect algebraic values.
