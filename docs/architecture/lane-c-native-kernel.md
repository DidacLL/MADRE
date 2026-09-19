# LANE C — Native MADRE Kernel

## Mission

Build the actual MADRE Kernel as an independent native physical-execution subsystem.

The Kernel exists so MADRE Module/Agent developers do not need to understand or manage the inference ecosystem: process lifetimes, models, local engines, remote engines, RAM/VRAM, queues, retries, crashes, background execution, scheduling, or durable physical Work.

Lane C owns only the physical inference substrate.

Do not redesign MADRE semantics.

Do not implement Agents, Modules, Operations, Material, Compound, Security Algebra, CORE, UI, or semantic reasoning.

---

# 1. Architectural boundary

The boundary is:

```
SEMANTIC MADRE

Module
  Agent
    Compound
    ReasoningRequest
    Security Algebra / information journey
          |
          | Agent determines what physical execution is required
          v
=================== HARD BOUNDARY ===================

PHYSICAL MADRE

Physical Work
          |
          v
Native Kernel
          |
          v
Physical inference engine/worker
```

The Kernel MUST NOT know:

```
Module
Agent
Operation
Material
Compound
ReasoningRequest

Sensitivity
Privacy
Integrity
Risk
Autonomy

CORE
Workflow
semantic continuation
semantic persistence
```

If any Kernel code needs one of those concepts, the architecture has drifted.

---

# 2. Implementation technology

Implement the Kernel in C++.

Target:

```
C++20
small native executable
no JVM
no application server
no localhost HTTP server
no TCP requirement for local MADRE IPC
```

C++ is selected because the Kernel may remain running while all Java MADRE processes are closed and should have a small, predictable physical footprint.

Do not link heavy inference libraries directly into the Kernel process.

The Kernel is the control plane, not the model process.

---

# 3. Required process topology

Target topology:

```
Java MADRE Module / Agent
        |
        | KernelClient
        | local IPC
        v
+----------------------------+
|       madre-kernel         |
|          C++               |
|                            |
| durable Work               |
| scheduling                 |
| engine inventory           |
| resource accounting        |
| retry/cancel               |
| worker supervision         |
+-------------+--------------+
              |
       private worker IPC
              |
      +-------+--------+
      |                |
      v                v
llama.cpp worker    future worker
C++ + libllama     Python/C++/etc.
```

Remote providers may eventually be represented by engine workers that perform HTTPS themselves.

The Kernel core should not become OpenAI-specific, llama.cpp-specific, HTTP-specific, CUDA-specific, or provider-specific.

---

# 4. Independent lifetime

Kernel lifetime MUST be independent of:

```
CORE
MADRE UI
MADRE Runtime
Module JVMs
Java heap
```

This sequence must work:

```
Module submits physical Work
        ↓
Module process exits
        ↓
MADRE Runtime exits
        ↓
Kernel remains alive
        ↓
Work executes
        ↓
result is durably retained
        ↓
Module starts hours later
        ↓
result is collected
```

No callback into a live Java process may be required for correctness.

The Kernel may terminate automatically when:

```
no Work is queued
no Work is scheduled for future execution
no Work is running
no retained responsibility requires it to stay alive
```

Idle policy must be configurable.

A dormant MADRE installation must not require a permanent JVM.

---

# 5. Java boundary owned by Lane C

Lane C also owns a very small Java artifact:

```
madre-kernel-client
```

This is NOT part of the semantic SDK.

It contains only physical Kernel concepts.

Conceptual API:

```
interface KernelClient {

    WorkId submit(WorkRequest request);

    WorkStatus status(WorkId id);

    Optional<WorkResult> result(WorkId id);

    void cancel(WorkId id);

    void acknowledge(WorkId id);

    List<EngineDescriptor> engines();
}
```

Exact Java naming may improve during implementation.

The contract must stay small.

`madre-kernel-client` MUST NOT depend on `madre-sdk`.

`madre-kernel` obviously cannot depend on `madre-sdk`.

---

# 6. Physical Work

Work is already past the semantic boundary.

A Work request may contain physical information such as:

```
Work type / inference family

physical input payload

effort

urgency

required technical capabilities

eligible engine identities

optional exact engine

optional exact model where meaningful

eligible execution time

deadline / timeout

retry specification
```

It must not contain the semantic explanation for those decisions.

For example:

```
eligibleEngineIds = [
    "local-llama-8b",
    "local-llama-70b"
]
```

is valid.

Kernel does not know that a remote engine was excluded because some Material was SECRET or because of a Privacy composition.

That decision has already happened above the boundary.

---

# 7. Work types must be extensible

Do not make Kernel synonymous with chat completion.

Use an extensible physical work-type identifier.

Examples:

```
text-generation/v1
embedding/v1
vision/v1
image-generation/v1
speech-to-text/v1
text-to-speech/v1
```

Only the types actually needed now must be implemented.

Initial mandatory implementation:

```
text-generation/v1
```

The architecture must not require changing Kernel core merely to introduce another physical inference family later.

---

# 8. Effort

MADRE currently needs at least:

```
STANDARD
HIGH
```

Meaning:

```
STANDARD
ordinary reasoning requirement

HIGH
caller requests stronger/more computationally capable reasoning
```

Kernel may satisfy this differently depending on available engines.

Examples:

```
choose different model
enable an engine reasoning mode
route to an engine supporting HIGH effort
```

Kernel does not decide whether reasoning semantically deserves HIGH effort.

It only receives the physical requirement.

Do not invent more effort levels until an actual use case requires them.

---

# 9. Urgency

Support:

```
INTERACTIVE
NORMAL
BACKGROUND
```

This is principally a scheduling characteristic.

Examples:

```
INTERACTIVE
owner is waiting for immediate response

NORMAL
ordinary foreground/background work

BACKGROUND
latency is relatively unimportant
```

Do not silently equate lower urgency with lower reasoning quality.

Effort and urgency are independent dimensions.

---

# 10. Technical capabilities

Capabilities must be extensible identifiers rather than one enormous closed enum.

Examples:

```
image-input
audio-input
structured-output
long-context
code-specialized
```

Work supplies:

```
requiredCapabilities
```

Engines advertise:

```
supportedCapabilities
```

Kernel performs physical compatibility matching.

No Security Algebra is involved.

---

# 11. Engine descriptors

An engine descriptor contains factual physical information.

At minimum:

```
engineId

supported Work types

supported capabilities

placement / execution location facts

available model identities if applicable

provider identity if applicable

endpoint metadata if applicable

resource requirements / estimates

availability / health

current loaded/warm state where relevant
```

Important:

```
provider       OPTIONAL
model          OPTIONAL
endpoint       OPTIONAL
```

Do not assume every inference engine is an HTTP endpoint.

A local llama.cpp worker does not need an HTTP URI.

The semantic layer may inspect these factual descriptors and decide what engines are acceptable.

Kernel never performs the semantic interpretation.

---

# 12. Engine selection

Kernel receives an already-semantic-safe physical candidate space.

Selection pipeline should remain simple:

```
Work type compatible?
        ↓
required capabilities satisfied?
        ↓
inside eligibleEngineIds if allowlist supplied?
        ↓
exact engine/model constraint satisfied?
        ↓
can satisfy requested effort?
        ↓
resources available?
        ↓
prefer suitable already-loaded/warm engine where beneficial
        ↓
dispatch
```

The scheduler may evolve later.

Do not build a benchmark-ranking research project now.

Correctness and physical resource management matter first.

---

# 13. Resource model

Kernel must explicitly model scarce physical resources.

Initial useful resources:

```
system RAM
CPU concurrency

GPU identity
GPU VRAM
GPU occupancy/reservations
```

Engine workers declare/estimate requirements.

Kernel reserves resources before dispatch.

A worker/model that would exceed known available capacity must not be launched blindly.

Resource reservations are released after:

```
successful completion
failure
cancellation
worker crash
```

Kernel should prefer reusing an already-loaded suitable engine when that avoids expensive unload/load churn.

---

# 14. Heavy inference must not live in Kernel

Kernel process:

```
small
stable
durable
control-plane only
```

Inference workers:

```
heavy
model-owning
GPU-owning
replaceable
crashable
```

If a worker:

```
segfaults
OOMs
CUDA-crashes
hangs
```

the Kernel must survive.

Expected behavior:

```
worker dies
    ↓
Kernel detects process termination
    ↓
attempt recorded as failed/interrupted
    ↓
resources released
    ↓
retry / alternate eligible engine / final failure
```

---

# 15. llama.cpp architecture

Remove localhost HTTP from MADRE's llama.cpp path.

Target:

```
madre-kernel
    |
    | inherited pipes / private worker IPC
    v
madre-llamacpp-worker
    |
    | direct C++ API
    v
libllama / llama.cpp
```

`madre-llamacpp-worker` owns:

```
model loading
llama context
native RAM
VRAM
token generation
model-specific execution
```

Kernel owns:

```
when worker starts
which Work goes to it
resource reservation
timeout
cancellation
worker termination
retry
```

No:

```
127.0.0.1
HTTP server
REST API
local TCP port
llama-server requirement
```

---

# 16. Worker lifecycle

Workers must be demand-driven.

Expected lifecycle:

```
Work arrives
    ↓
eligible worker already alive?
    ├─ yes -> reuse
    └─ no  -> launch

load model if required
    ↓
execute Work
    ↓
possibly keep warm for configurable idle period
    ↓
terminate/unload when no longer useful
```

When no inference is active:

```
model RAM/VRAM -> zero where possible
worker count   -> zero
```

Kernel may remain if durable Work requires it.

---

# 17. Kernel IPC

Runtime/Modules must be able to reconnect to a Kernel that outlives them.

Therefore Runtime↔Kernel communication cannot depend solely on inherited stdin/stdout.

Use private local OS IPC behind an abstraction.

Target:

```
POSIX:
    Unix-domain socket

Windows:
    named pipe or equivalent local non-TCP IPC
```

No localhost TCP.

No HTTP.

Endpoint should be owner-local using normal OS filesystem/object permissions.

This is operational hygiene, not a MADRE security subsystem.

---

# 18. Worker IPC

Kernel owns engine-worker processes.

Therefore inherited pipes are preferred for Kernel↔worker communication.

This provides:

```
no port allocation
no network listener
natural lifetime relationship
simple crash detection
simple stdout/stderr separation
```

Use a framed protocol, not ad-hoc text parsing.

---

# 19. Protocol framing

Do not build a generic RPC framework.

Use a small versioned frame.

Conceptual frame:

```
magic/version
message type
correlation id
metadata length
payload length
metadata
binary payload
```

Metadata may initially use a compact JSON representation for implementation speed and inspectability.

Large inference input/result remains binary payload and must not require Base64.

Protocol version must be explicit.

Unknown future fields should not automatically break compatible peers.

---

# 20. Kernel commands

Initial client protocol needs only:

```
HELLO / VERSION

SUBMIT

STATUS

RESULT

ACKNOWLEDGE

CANCEL

LIST_ENGINES

ENGINE_STATUS
```

Optional but useful for interactive inference:

```
SUBSCRIBE
PROGRESS / STREAM_CHUNK
```

Streaming is not part of durability correctness.

Final results must always be durable independently of a live subscriber.

---

# 21. Durable storage

Use SQLite for durable Work metadata/state.

Use ordinary files for potentially large opaque payloads/results.

Suggested layout:

```
<kernel-data>/
    kernel.db

    work/
        <work-id>/
            input.bin
            result.bin
```

SQLite records:

```
WorkId

Work type

requirements

urgency

effort

eligible engines / exact selection

created time

eligible time

deadline / timeout

retry specification

current state

selected engine

attempt history

technical failure

input path

result path
```

Do not store semantic MADRE state.

---

# 22. Work lifecycle

Keep the public state machine small.

Suggested externally visible states:

```
QUEUED
RUNNING
SUCCEEDED
FAILED
CANCELLED
```

Scheduling time and retry timing remain metadata rather than multiplying public states unnecessarily.

Attempts have their own records.

On Kernel restart:

```
SUCCEEDED / FAILED / CANCELLED
    remain terminal

QUEUED
    remains queued

RUNNING
    previous attempt becomes INTERRUPTED
    Work returns to QUEUED if retry remains
    otherwise becomes FAILED
```

Exact internal implementation may vary while preserving this behavior.

---

# 23. Result retention

Completed result remains available until explicit acknowledgement or configured retention expiry.

Normal path:

```
Kernel completes Work
        ↓
result persisted
        ↓
caller reconnects later
        ↓
RESULT WorkId
        ↓
caller successfully consumes result
        ↓
ACKNOWLEDGE WorkId
        ↓
Kernel may remove payload/result according to retention policy
```

Never require the original caller process to remain alive.

---

# 24. Cancellation

Cancellation must work for:

```
QUEUED Work
RUNNING Work where worker supports interruption
```

Kernel forwards cancellation to worker when possible.

If worker does not terminate cooperatively and cancellation semantics require termination, Kernel may terminate the worker process.

Resource reservations must always be released.

---

# 25. Kernel process lifecycle

Kernel should support:

```
start on demand

continue independently while Work exists

accept reconnecting clients

optional configurable idle exit when no responsibility remains
```

Java client may provide:

```
ensureKernelRunning()
```

but Kernel correctness must not depend on Java remaining alive.

Cross-platform launch/detach behavior is Lane C responsibility.

---

# 26. Memory/footprint requirement

This is a scarce-resource inference system.

Measure actual RSS.

Do not merely claim C++ is lightweight.

Acceptance evidence must report:

```
Kernel idle RSS

Kernel RSS with queue but no worker

worker RSS before model load

worker/model RAM after load

VRAM after load

memory after worker termination
```

Primary objective:

> The overwhelming majority of memory consumed during inference must belong to the actual inference engine/model, not MADRE infrastructure.

---

# 27. Provider agnosticism

Do not build the Kernel around OpenAI compatibility.

Do not build the Kernel around llama.cpp.

llama.cpp is merely the first local worker implementation.

Future physical engines may include:

```
other C++ inference runtimes

Python model workers

ONNX

speech engines

image engines

embedding engines

remote-provider workers

special-purpose models
```

They should plug into the same physical engine/worker architecture.

---

# 28. Semantic inspection requirement

The semantic MADRE side needs factual engine information before producing Work.

Therefore `LIST_ENGINES` must expose sufficient physical facts for semantic code to reason about available destinations.

Kernel only reports facts.

Example:

```
engineId: local-llama-8b
placement: LOCAL_MACHINE
provider: null
models: [...]
capabilities: [...]
```

or:

```
engineId: remote-provider-x
placement: REMOTE
provider: "provider-x"
endpointDescription: "..."
capabilities: [...]
```

Kernel does not convert those facts to Privacy values.

The Agent/SDK does that above the boundary.

---

# 29. Explicit non-goals

Lane C MUST NOT implement:

```
MADRE Module system

Module JAR loading

Agent execution

Agent selection

Agent orchestration

Compound evaluation

Security Algebra

Sensitivity / Privacy / Integrity / Risk / Autonomy

ReasoningRequest semantics

CORE

Module UI

Module persistence

Module-to-Module invocation

Python/TS Module support

sandboxing

owner permission systems

central semantic policy

generic application runtime
```

These belong elsewhere or remain future work.

---

# 30. Repository ownership

Lane C should primarily own:

```
kernel/
    C++ Kernel source
    CMake build
    IPC
    durable Work
    scheduling
    workers
    engine descriptors
    resource management
    tests

kernel/workers/llamacpp/
    C++ llama.cpp worker

madre-kernel-client/
    Java physical client
```

Lane C must not redesign `madre-sdk` or `madre-runtime`.

If an integration change outside Lane C is genuinely necessary, keep it minimal and explain why before making it.

---

# 31. Testing strategy

Tests must prove behavior, not architecture vocabulary.

Mandatory integration tests:

## A. Caller disappearance

```
start Kernel

submit Work from Java

terminate Java process

Kernel completes Work

start new Java process

retrieve result
```

PASS only if no original Java process was required.

## B. Kernel restart

```
submit Work

terminate Kernel during execution

restart Kernel

recover durable Work

retry or correctly fail according to retry policy

result remains queryable
```

## C. Worker crash

```
submit Work

force worker crash

Kernel survives

resources are released

attempt recorded

retry/final failure behaves correctly
```

## D. On-demand workers

```
Kernel idle

no worker exists

submit Work

worker starts

Work completes

idle timeout passes

worker terminates

model memory/VRAM released
```

## E. Eligibility

Multiple fake engines:

```
engine A
engine B
engine C
```

Work with eligibility/capability constraints must never dispatch to an ineligible engine.

## F. Exact selection

If caller requests exact engine/model:

```
available -> use it

unavailable -> fail explicitly
```

Do not silently broaden an exact request.

## G. Background schedule

```
submit future/background Work

close Java

wait until eligibility time

Kernel executes

reconnect

retrieve result
```

## H. No local web architecture

Acceptance must verify that Kernel and llama.cpp worker do not require a localhost HTTP/TCP listener.

## I. Resource accounting

Two fake workers that together exceed configured resource capacity must not be launched concurrently.

## J. llama.cpp smoke

Real llama.cpp worker:

```
load small configured model

execute text-generation/v1

receive result

terminate worker

verify worker process exits
```

---

# 32. Performance and simplicity constraints

Do not overengineer.

Avoid:

```
plugin marketplaces
distributed consensus
cluster scheduling
network service discovery
microservice frameworks
generic RPC frameworks
enterprise authorization
complex database abstraction layers
template-heavy C++ architecture
premature engine scoring research
```

One developer assisted by AI agents must be able to inspect the implementation.

Prefer:

```
small classes/functions

explicit state machines

plain SQLite

plain filesystem payload storage

simple C++ process management

simple framed IPC

few external dependencies
```

---

# 33. Deliverable slices

Implement in this order.

## Slice C1 — Kernel skeleton + protocol

Deliver:

```
C++ kernel executable

local IPC server

Java KernelClient

protocol version negotiation

SUBMIT / STATUS / RESULT / ACK / CANCEL

durable metadata/payload storage

fake engine
```

Acceptance:

```
Java can submit
Java exits
Kernel fake engine completes
new Java process retrieves result
```

This is the first hard gate.

## Slice C2 — scheduling + recovery

Deliver:

```
urgency

eligible time

timeout

retry

engine filtering

exact selection

restart recovery

attempt history
```

Acceptance:

```
Kernel crash/restart preserves Work correctly.
```

## Slice C3 — resource and worker lifecycle

Deliver:

```
worker process abstraction

resource declarations/reservations

on-demand launch

warm reuse

idle unload

worker crash handling
```

Acceptance:

```
heavy worker failure does not kill Kernel.
```

## Slice C4 — llama.cpp

Deliver:

```
C++ llama worker

direct libllama integration

no HTTP

text-generation/v1

streaming if feasible

model lifecycle
```

Acceptance:

```
real generation through native worker architecture.
```

## Slice C5 — hardening

Deliver:

```
Windows/Linux IPC acceptance

RSS/VRAM measurements

durable scheduled/background Work

cancellation

clean shutdown

engine inspection

documentation of physical boundary
```

---

# 34. Completion definition

Lane C is complete enough for semantic MADRE development when another developer can treat Kernel as:

```
engines = kernel.engines()

workId = kernel.submit(physicalWork)

status = kernel.status(workId)

result = kernel.result(workId)
```

and does NOT need to know:

```
llama.cpp API

CUDA lifecycle

model loading

worker processes

SQLite

IPC

engine crashes

resource reservations

retry persistence

Kernel restart mechanics
```

Likewise, the Kernel implementation does not know why a particular physical Work was created.

That is the architectural boundary.

---

# 35. Primary success criterion

The purpose of Lane C is not to create an impressive Kernel architecture.

Its purpose is:

> Make physical inference infrastructure boring enough that MADRE development can concentrate on Modules, Agents, Compounds, Material, Operations, Security Algebra and semantic experimentation.

If Lane C starts requiring semantic MADRE changes to make its internal implementation convenient, stop and re-evaluate the design.