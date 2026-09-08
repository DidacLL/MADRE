# MADRE Platform Architecture

`MADRE.md` is the canonical product contract. This document expands the system topology and ownership boundaries.

## 1. Topology

```text
                         MADRE installation

Module A                    Kernel                    Inference mechanisms
---------              -------------------            -------------------
domain state     --->   public registry        --->   local model runtime
private Agents           boundary discovery            remote provider API
Skills/Workflows         explicit brokering            provider CLI/session
WorkPlans                 work admission                MCP/software adapter
Operations                security algebra              embeddings/speech/...
UI/history                scheduling/resources
                          recovery/evidence

Module B / Default Module / adapters participate through the same boundaries.
```

The Kernel and execution engine may be one process or several. Logical responsibility, not process shape, defines the architecture.

## 2. Module

A Module is an independently owned application or integration boundary. It can be a desktop application, service, script, editor integration, adapter over another program, MCP integration, API integration or first-party Module.

The Module is the semantic owner. It decides what data is relevant, what a task means, how an Agent behaves, which Skill or Workflow is useful, what a WorkPlan means, how output is interpreted and which domain mutation should occur.

MADRE must not reconstruct those semantics from Module-private data.

Interactive Modules are expected to use MADRE's dual-lane interaction pattern: each user input can drive a latency-sensitive transient inference while a non-blocking reasoning path continues independently. The fast lane is still real inference, not canned text. The Module/Agent owns the semantics of acknowledgement, escalation, delegation and eventual result presentation.

## 3. Public descriptor versus implementation

MADRE interoperability intentionally distinguishes public contract from private owner:

```text
AgentDescriptor            Agent implementation
OperationDescriptor        Operation implementation
SkillDescriptor            adopted Skill mechanism
WorkflowDescriptor         Workflow execution
       |                           |
       v                           v
MADRE registry              owning Module
```

Kernel can persist, filter and route a descriptor. It cannot thereby become owner of the implementation.

## 4. Registry

Kernel maintains the installation registry of published descriptors:

```text
Module manifests
public Agents
public Skills
public Workflow descriptors
public Operations
inference-mechanism / model descriptors
```

Registry discovery is deterministic and boundary-filtered. The requester performs semantic selection over the returned visible descriptors.

Unknown/weakly described integrations may be represented conservatively with provenance that lowers trust or narrows admissible boundaries. Registration is not semantic certification or authorization.

## 5. Kernel

Kernel responsibility is deterministic control:

- evaluate current security/boundary algebra;
- expose latency-sensitive transient inference execution;
- admit explicit durable work;
- schedule immediately eligible/delayed durable work globally;
- allocate scarce execution resources;
- select a physically compatible admissible inference mechanism/model from Module requirements/preferences;
- acquire durable-work material just-in-time from its owner;
- track attempts and truthful physical outcomes;
- recover accepted execution after restart;
- broker explicit registered Agent/Operation invocations;
- deliver transient results;
- retain execution/security evidence.

Kernel does not semantically route arbitrary user text, choose an Agent because of prompt meaning, own WorkPlans, persist Agent state or interpret generated content.

Kernel also does not persist or cache queued private work material. Accepted durable work holds only a verifiable material handle and execution metadata; actual material is acquired only when execution is about to occur.

## 6. Capability / inference mechanism

The current code uses **Capability** for a physical computation implementation. In architecture discussions, read this as an available physical inference/execution mechanism with known properties, not as a semantic skill or ability.

Operations and Capabilities are distinct:

```text
Operation  = Module-owned semantic callable
Capability = MADRE physical computation/inference mechanism
```

A provider is not one Capability. The same provider may expose multiple mechanisms: official APIs, account-authenticated CLIs, MCP paths, SDKs, local gateways or user-installed adapters over software the user controls. Each mechanism can have different cost, latency, modality, authentication, model inventory and resource properties.

Provider-specific request formats, login/API-key/session handling, model loading, process/SDK details and backend device/cache mechanics stay at the adapter boundary. Kernel must not assume that API keys or HTTP are universal merely because one reference adapter uses them.

The OpenAI-compatible HTTP adapter is one mechanism among many and must not become the template that flattens later provider integration.

Modules normally describe requirements/preferences rather than enumerate installed backends. Kernel may match on dimensions such as modality/specialization, latency class, reasoning effort/quality, cost policy, locality/privacy, and preferred provider/model with explicit fallback semantics.

A Module Operation may internally submit MADRE work; a Capability does not become an Operation merely because it computes something.

## 7. Interactive and durable execution paths

Two execution paths have intentionally different ownership and latency semantics:

```text
Transient interactive inference
    direct minimal ephemeral input
    latency-sensitive
    not queued
    no restart/recovery promise
    input discarded after invocation

Durable work
    reference + digest + envelope + retrieval coordination
    no queued private payload
    scheduled/recoverable/retriable
    material resolved just-in-time
```

This distinction prevents the privacy/recovery rules of durable work from adding unnecessary round-trips to the fast interaction lane while preventing queued work from consuming RAM with duplicated Module-owned context.

The dual-lane user interaction pattern may use both paths for the same user input: a fast inference keeps the interaction natural while a background reasoning branch can answer, schedule deeper work, or delegate. Kernel provides the execution paths and resource arbitration; the Module owns what those inferences mean.

## 8. Default Module and SDK boundary

A default general-purpose Module may ship with MADRE. It is useful for generic Agents, cross-domain coordination and interaction surfaces, but is architecturally ordinary. It uses the same registry, security and execution paths as every Module.

The public Module boundary should be sufficiently stable and segregated to support a modular MADRE SDK. Module code should not need Kernel storage, scheduler, FastAPI or provider internals. CORE should become the first substantial consumer of that public SDK/boundary rather than a privileged second Kernel.

## 9. Transport neutrality

HTTP, MCP, IPC, in-process APIs, vendor CLIs, local automation bridges and third-party agent protocols are transports/adapters. None of them defines the ownership model.

MADRE should allow user-installed adapters over software the user controls even when their mechanism is unconventional. The adapter/user owns the correctness and account/software implications of that integration; Kernel sees only the mechanism's declared execution properties and callable boundary.

## 10. Responsibility test

Place a new concept by asking what decision requires it.

If it exists to understand meaning, plan, remember, present, learn, choose Skills/Workflows or mutate a domain, it belongs to a Module.

If it exists to publish/discover a public semantic contract, it belongs to interoperability contracts/registry.

If it exists to evaluate boundaries, execute a latency-sensitive inference, admit/schedule durable work, allocate hardware, select an execution mechanism, recover physical execution, route an explicit target or record evidence, it belongs to Kernel.

If it exists to load/talk to a model/provider, manage provider login/credentials/session mechanics, or wrap a concrete local/remote inference program, it belongs to a Capability adapter or external provider software.
