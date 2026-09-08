# MADRE Platform Architecture

Authority: `MADRE.md` defines product meaning. This document owns detailed system topology and responsibility placement.

## 1. Topology

```text
                         MADRE installation

Module A                    Kernel                    Inference mechanisms
---------              -------------------            -------------------
domain state     --->   public interoperability --->  local model runtime
private Agents           security algebra             remote provider API
Skills/Workflows         transient inference          provider CLI/session
WorkPlans                 durable work                MCP/software adapter
Operations                scheduling/resources        embeddings/speech/...
UI/history                recovery/evidence

Module B / Default Module / adapters participate through the same logical boundaries.
```

The Kernel and execution engine may be one process or several. Logical responsibility, not process topology, defines the architecture.

## 2. Module boundary

A Module is an independently owned application or integration boundary: desktop application, service, script, editor integration, adapter over another program, MCP integration, API integration or first-party Module.

The Module is the semantic owner. It decides what data is relevant, what a request means, how an Agent behaves, which Skill/Workflow is useful, what a WorkPlan means, how output is interpreted and which domain mutation should occur.

MADRE must not reconstruct those semantics from Module-private data.

## 3. Public interoperability boundary

MADRE separates public contract from private implementation:

```text
AgentDescriptor            Agent implementation
OperationDescriptor        Operation implementation
SkillDescriptor            adopted Skill mechanism
WorkflowDescriptor         Workflow execution
       |                           |
       v                           v
MADRE interoperability      owning Module
```

The registry knows that published surfaces exist and how to route them. Registration is not semantic certification or authorization.

Detailed manifest, descriptor, discovery, brokering and SDK-facing semantics belong to `MADRE-agent-interoperability.md`.

## 4. Kernel boundary

Kernel responsibility is deterministic control:

- evaluate carried security/boundary facts;
- expose latency-sensitive transient inference execution;
- admit and schedule durable work;
- allocate scarce GPU/CPU/RAM and execution resources;
- select a physically compatible admissible inference mechanism/model from Module requirements/preferences;
- acquire durable-work material just-in-time from its owner;
- track attempts and truthful physical outcomes;
- recover accepted execution after restart;
- broker explicit registered Agent/Operation invocations;
- deliver transient results;
- retain execution/security evidence.

Kernel does not semantically route arbitrary user text, choose an Agent because prompt contents appear relevant, own WorkPlans, persist Agent state or interpret generated content.

Kernel also does not cache queued private work material. Detailed transient/durable execution semantics belong to `MADRE-execution-contract.md`.

## 5. Inference mechanism / Capability boundary

The current code uses **Capability** for a physical computation implementation. Architecturally, this means an available physical inference/execution mechanism with declared properties, not a semantic Skill or ability.

```text
Operation  = Module-owned semantic callable
Capability = MADRE physical computation/inference mechanism
```

A provider is not one Capability. One provider may expose multiple mechanisms: official APIs, account-authenticated CLIs, MCP paths, SDKs, local gateways or user-installed adapters over software the user controls. Their cost, latency, modality, authentication, model inventory and resource behavior can differ.

Provider-specific request formats, login/credential/session handling, process/SDK mechanics, model loading and backend device/cache behavior remain behind the adapter/external-software boundary.

The current OpenAI-compatible HTTP adapter is one concrete mechanism, not the architectural template for all provider support.

Detailed requirement/preference matching and mechanism selection belong to `MADRE-execution-contract.md`.

## 6. Interactive and durable paths

The platform exposes two execution classes with different topology and lifecycle:

```text
Transient interactive inference
    Module -> ephemeral input -> Kernel -> mechanism -> transient result -> Module

Durable work
    Module -> execution intent/material handle -> Kernel queue/recovery
           -> JIT material resolution -> mechanism -> transient result -> Module
```

An interactive Module may use both paths for the same user input: one low-latency inference keeps interaction natural while a parallel Module-owned reasoning branch may answer, schedule durable work or delegate.

Kernel supplies execution paths and resource arbitration; the Module owns what those inferences mean.

## 7. Default Module and SDK placement

A default general-purpose Module may ship with MADRE. It is architecturally ordinary and uses the same public registry, security and execution boundaries as other Modules.

Public Module contracts should be sufficiently stable and segregated for a modular MADRE SDK. Module code should not depend on Kernel storage, scheduler, FastAPI or provider internals. CORE should be the first substantial consumer of that public boundary rather than a privileged second Kernel.

Detailed SDK-facing interfaces belong to `MADRE-agent-interoperability.md`.

## 8. Transport neutrality

HTTP, IPC, in-process APIs, MCP, vendor CLIs, local automation bridges and third-party protocols are transports/adapters. None defines the ownership model.

User-installed adapters over software the user controls are allowed even when unconventional. Their owners are responsible for the software/account implications; Kernel sees the declared execution mechanism and its boundary facts.

## 9. Responsibility test

Place a new concept by asking what decision requires it:

- understand meaning, plan, remember, present, learn, choose Skills/Workflows or mutate a domain → Module;
- publish/discover a public semantic contract → interoperability;
- evaluate boundaries, execute transient inference, admit/schedule durable work, allocate hardware, select a mechanism, recover physical execution, route an explicit target or record evidence → Kernel;
- load/talk to a model/provider or wrap concrete inference software → Capability adapter/external mechanism software.
