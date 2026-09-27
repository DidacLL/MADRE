# MADRE — mid-level architecture

Status: **accepted whole-system engineering boundaries; Lane C physical implementation current, semantic SDK/Runtime not yet implemented in the active tree**.

The architecture follows one recurring rule: do not invent an owner/manager/subsystem where MADRE only needs a semantic relation or bounded function.

## 1. Top-level regions

MADRE has three engineering regions, but they are not equivalent services:

```text
Modules / applications and semantic Agents
        ↓
MADRE Runtime semantic environment
        ↓
small derived physical inference requirement
        ↓
madre-kernel-client
======== HARD SEMANTIC / PHYSICAL BOUNDARY ========
        ↓
Kernel durable physical inference
        ↓
Owner-selected inference environment
```

Meaning decreases downward. Kernel is deliberately physical.

## 2. Public SDK construction surface

The SDK is the public semantic vocabulary from which MADRE-native applications are built. It defines Module, Agent, Operation, Skill/Workflow/WorkPlan concepts where applicable, Material/context, ReasoningRequest, SPIRA and composition surfaces.

First-party and eventually generated Modules use the same public surface. There must not be a richer hidden semantic architecture required only by shipped software.

## 3. Modules and Operations

A Module is an independently meaningful application/domain boundary. It owns its domain state, persistence, domain types, integrations, optional UI and internal software/intelligence.

A Module may expose zero or more Operations, Agents, Skills and meaningful Material/context. None is mandatory. Agentless Modules and thin bindings around existing software are normal.

An Operation is bounded executable behavior. Calling an Operation exposed by another Module does not automatically transfer semantic continuation to an Agent in that Module.

## 4. Agents and delegation

An Agent is a semantic reasoning actor provided by a Module. It can use context/Material, Skills, Operations, reusable behavior, ReasoningRequests and explicit delegation.

Operation invocation and Agent-to-Agent delegation are distinct:

```text
Operation invocation -> use bounded behavior; caller keeps semantic objective
Agent delegation      -> another semantic actor receives delegated objective
```

Runtime may route either interaction without becoming owner of the application meaning.

## 5. SPIRA

SPIRA is intrinsic composition of actual semantic facts:

```text
Material/context representation       -> Sensitivity
actual receiving/exposure boundary    -> Privacy
actual causal Agent/provenance        -> Integrity
selected concrete effect/Operation    -> Risk
current acting Agent continuation     -> Autonomy
```

There is no Agent-owned security Compound or central Runtime policy evaluator. Actual constituents compose; Agents change reality when they need a different composition.

SPIRA stays above the Kernel boundary.

## 6. Runtime responsibilities

Runtime has two broad responsibilities without becoming a semantic owner.

Module environment:

```text
installation/configuration
CORE role assignment
live Module discovery/lifecycle/addressing
Operation and Agent-delegation routing
inspection of exposed surfaces
```

Shared reasoning runtime:

```text
semantic ReasoningRequest persistence/correlation where needed
SDK-bounded semantic -> physical derivation seam
correlation between semantic request and Kernel Work/result
continuation support
```

Runtime machinery can persist/route semantic state while Module/Agent software owns what that state means.

## 7. ReasoningRequest to physical Work

A `ReasoningRequest` is semantic and does not directly become a Kernel object. Semantic MADRE derives only the concrete physical facts Kernel needs.

The initial implemented physical contract is intentionally small:

```text
prepared input
requested effort
urgency
eligibleAt / deadline
hard local/external boundary
```

Future fields earn their place only when an implemented DRE decision consumes them.

## 8. Delayed Reasoning Effort split

DRE spans two persistence domains without merging them.

Above Kernel:

```text
why reasoning exists
relevant domain context
ReasoningRequest
semantic continuation/correlation
```

Below Kernel:

```text
durable physical Work
eligibility/deadline/urgency
technical restrictions
capability truth/observations
attempt/result/cancel/recovery
```

Semantic processes may disappear while Kernel remains responsible for physical Work. Later semantic software can reconnect to the retained result and resume meaning.

## 9. Kernel physical model

Kernel owns shared physical inference concepts, not external inference engines.

### PhysicalInferenceWork

One authoritative durable Work lifecycle stores physical requirements and execution evidence. Current Work has no MAF/checkpoint/strategy scaffolding; those validation artifacts were removed after they ceased to represent product behavior.

SQLite is authoritative for current Lane C physical state.

### InferenceCapability

An `InferenceCapability` is a configured physical intelligence opportunity consisting of identity/binding and the typed configured facts currently consumed by DRE. Configured facts, current availability and historical attempt observations remain separate.

Current implementation models execution boundary, supported effort, Owner preference, `Unknown/Unavailable/Available`, observation time, successful latency and success/failure attempt evidence.

### DRE

DRE decides among admissible physical opportunities using actual current policy: eligibility/deadlines, effort/boundary admissibility, availability truth, Owner preference and observed latency where comparable for interactive Work.

Known available is preferred. `Unknown` remains usable when no known-available admissible candidate exists. Known unavailable waits and is automatically re-observed.

Kernel does not judge application-level answer quality.

## 10. Physical construction surface

The current Kernel is cross-platform .NET because its work is asynchronous integration, structured physical state, SQLite durability, capability observation and scheduling rather than native model lifecycle.

`IInferenceBinding` is the open physical execution seam. The process binding, MEAI interoperability and Owner/custom bindings use the same responsibility. Binding execution receives execution-relevant data only.

Microsoft Agent Framework is not a current production dependency or strategy. Earlier checkpoint validation is retained only in Git history. A future real physical strategy may select any suitable implementation mechanism when product evidence requires it.

## 11. Local physical boundary

`madre-kernel-client` and the .NET host communicate through a versioned bounded framed protocol over Unix-domain sockets on Windows/Linux.

There is no TCP port or web-server control plane. Connections are independent; caller disappearance does not own Work or Kernel lifetime. Default socket placement is in the current owner's local application-data area with owner-only Unix permissions.

The Java client is a normal Java 21 library. Acceptance helper classes live only in test tooling and are not packaged as a production CLI artifact.

## 12. CORE position

CORE is an ordinary Module assigned the CORE installation role. It may provide Owner interaction/default Agents/general behavior, but it is not Runtime, Kernel, owner of other Modules or SPIRA authority.

Physical capability knowledge and DRE scheduling are Kernel responsibilities.

## 13. Dependency direction

```text
CORE Module ─────────────→ madre-sdk
independent Module ──────→ madre-sdk
MADRE Runtime ───────────→ madre-sdk
MADRE Runtime ───────────→ madre-kernel-client
madre-kernel-client ─────→ Kernel local IPC contract
Kernel ──────────────────→ physical libraries/bindings
bindings ────────────────→ Owner inference environment
```

Hard anti-drift dependency rules remain:

```text
madre-kernel-client -X-> madre-sdk
Kernel              -X-> madre-sdk
Kernel              -X-> MADRE Runtime
```

## 14. Current Lane C status

Current Lane C implements and CI-proves:

- zero-capability startup without mandatory config;
- SQLite durable Work/attempt/result lifecycle;
- configured/current/observed capability truth and catalogue reconciliation;
- asynchronous/automatic capability observation;
- capability-aware DRE with explicit domain ordering;
- wake/deadline-driven scheduler without fixed busy polling;
- typed physical failures and technical detail;
- process/MEAI/custom open binding seam;
- bounded local Unix-domain-socket IPC and Java 21 client on Windows/Linux;
- caller disappearance, eligibility/deadlines, cancellation, retained release and restart `UnknownCompletion`;
- scheduler no-head-starvation behavior;
- contamination checks preventing semantic leakage, web/port residue, checkpoint/MAF residue and test-helper production packaging.

The superseded C++/worker/model-lifecycle/llama.cpp/protocol-v4/web-host/MAF-validation shapes are historical evidence, not active alternatives.

The semantic SDK/Module layer and Runtime described above remain architecture, not work started by Lane C.

## 15. Engineering invariants

1. Module owns application/domain meaning; Runtime coordination does not absorb it.
2. Operation invocation and Agent delegation remain distinct.
3. SPIRA stays intrinsic and semantic.
4. ReasoningRequest remains semantic; Agents do not construct Kernel Work directly.
5. Semantic MADRE derives a small physical requirement; Kernel retains meaningful physical DRE choice.
6. Kernel owns durable Work, capability truth/observations, scheduling and physical execution only.
7. Kernel does not own provider/model/runtime internals or application answer quality.
8. `Unknown` availability is not silently equated with `Unavailable`.
9. Current configuration determines the selectable capability catalogue; history remains history.
10. Infrastructure/frameworks earn their place from actual MADRE needs, not convention.
11. First-party inference adapters receive no architectural privilege unavailable in principle to Owner/custom bindings.
12. The physical boundary stays local, bounded, versioned and independent of web/TCP vocabulary.
