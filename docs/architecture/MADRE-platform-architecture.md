# MADRE Platform Architecture

`MADRE.md` is the canonical product contract. This document expands the system topology and ownership boundaries.

## 1. Topology

```text
                         MADRE installation

Module A                    Kernel                    Capability adapters
---------              -------------------            -------------------
domain state     --->   public registry        --->   local model runtime
private Agents           boundary discovery            remote provider
Skills/Workflows         explicit brokering            embeddings/speech/...
WorkPlans                 work admission
Operations                security algebra
UI/history                scheduling/resources
                          recovery/evidence

Module B / Default Module / adapters participate through the same boundaries.
```

The Kernel and execution engine may be one process or several. Logical responsibility, not process shape, defines the architecture.

## 2. Module

A Module is an independently owned application or integration boundary. It can be a desktop application, service, script, editor integration, adapter over another program, MCP integration, API integration or first-party Module.

The Module is the semantic owner. It decides what data is relevant, what a task means, how an Agent behaves, which Skill or Workflow is useful, what a WorkPlan means, how output is interpreted and which domain mutation should occur.

MADRE must not reconstruct those semantics from Module-private data.

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
Capabilities / model descriptors
```

Registry discovery is deterministic and boundary-filtered. The requester performs semantic selection over the returned visible descriptors.

Unknown/weakly described integrations may be represented conservatively with provenance that lowers trust or narrows admissible boundaries. Registration is not semantic certification.

## 5. Kernel

Kernel responsibility is deterministic control:

- evaluate current security/boundary algebra;
- admit explicit work;
- schedule immediate/delayed work globally;
- allocate scarce execution resources;
- select a physically compatible permitted capability/model;
- track attempts and truthful physical outcomes;
- recover accepted execution after restart;
- broker explicit registered Agent/Operation invocations;
- deliver transient results;
- retain execution/security evidence.

Kernel does not semantically route arbitrary user text, choose an Agent because of prompt meaning, own WorkPlans, persist Agent state or interpret generated content.

## 6. Capability

A Capability is a physical computation implementation. Provider-specific request formats, model loading, process/SDK details and backend device/cache mechanics stay at this boundary.

Operations and Capabilities are distinct:

```text
Operation  = Module-owned semantic callable
Capability = MADRE physical computation backend
```

A Module Operation may internally submit MADRE work; a Capability does not become an Operation merely because it computes something.

## 7. Default Module

A default general-purpose Module may ship with MADRE. It is useful for generic Agents, cross-domain coordination and interaction surfaces, but is architecturally ordinary. It uses the same registry, security and execution paths as every Module.

## 8. Transport neutrality

HTTP, MCP, IPC, in-process APIs and third-party agent protocol adapters are transports/adapters. None of them defines the ownership model.

## 9. Responsibility test

Place a new concept by asking what decision requires it.

If it exists to understand meaning, plan, remember, present, learn, choose Skills/Workflows or mutate a domain, it belongs to a Module.

If it exists to publish/discover a public capability, it belongs to interoperability contracts/registry.

If it exists to evaluate boundaries, admit/schedule work, allocate hardware, select an execution backend, recover physical execution, route an explicit target or record evidence, it belongs to Kernel.

If it exists to load/talk to a model/provider or manage backend-specific computation, it belongs to a Capability adapter.
