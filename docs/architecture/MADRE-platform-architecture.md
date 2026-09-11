# MADRE Platform Architecture

Authority: `MADRE.md` defines product meaning. This document owns topology and responsibility placement.

## 1. Topology

```text
                         MADRE installation

Module A              SDK / interoperability              Kernel
--------              ----------------------              ------
domain state   --->   public typed contracts     --->     security composition
UI                     descriptors/endpoints              durable work
Agents                 material resolution                scheduling/resources
Skills                  discovery/brokering                mechanism selection
Workflows                                                  recovery/evidence
WorkPlans                                                          |
Operations                                                         v
                                                                  Inference
Module B / CORE / adapters                                      mechanisms
```

Logical responsibility, not process topology, defines the architecture. Kernel, SDK-facing transport and execution workers may be in one process or several.

## 2. Module boundary

A Module is an independently owned application or integration boundary.

It owns semantic decisions and private state: domain data, UI, context/history, Agents, Skills, Workflows, WorkPlans, artifacts, Operations, domain-specific minimization/validation, learning and result interpretation.

A Module may expose zero Agents and may rely on CORE for generic interaction/fallback behavior.

MADRE must not reconstruct Module semantics from private content.

## 3. SDK and interoperability boundary

The SDK is the stable software boundary between Module semantics and Kernel execution.

It provides public typed contracts for concepts that independent implementations need to share, including as appropriate:

```text
Module + ModuleManifest
Agent + public AgentDescriptor
Skill + SkillDescriptor
Workflow + WorkflowDescriptor
Operation + OperationDescriptor + EffectProfile
Artifact / ContextBundle
Work submission/inspection/result access
MaterialHandle + durable material resolver
InferenceRequirement
CapabilityDescriptor
SecurityID / SecurityObject / relation-normal-form propagation
```

These contracts do not imply that every Module must implement every concept. Module-facing responsibilities should be interface-segregated.

Module code should not depend on Kernel persistence classes, scheduler internals, HTTP/FastAPI implementation details or provider-adapter internals.

The SDK may provide higher-level reusable helpers and reference implementations without turning them into Kernel semantics. Examples include interaction patterns, delegation helpers, material/context construction helpers, EffectProfile/relation builders and adapter scaffolding.

Public contracts should remain language-neutral even while the current prototype is implemented in Python.

## 4. Kernel boundary

Kernel responsibility is deterministic shared execution:

- validate SecurityObjects and compose prospective relation normal forms at governed boundaries;
- execute transient inference requests;
- admit and schedule durable work;
- allocate scarce GPU/CPU/RAM and execution resources;
- select compatible inference mechanisms/models from Module requirements/preferences;
- acquire durable-work material just-in-time from its owner;
- verify material/security continuity;
- record physical attempts and truthful outcomes;
- recover accepted durable execution after restart;
- broker explicit registered Agent/Operation invocations;
- deliver transient results;
- retain execution/security evidence without private payloads.

Kernel does not own semantic UI behavior, Agent reasoning, WorkPlans, prompt interpretation, generated-result meaning, material classification, semantic validation or domain mutation.

Transient inference and durable work are execution primitives. Whether a Module uses transient inference as a fast-response lane, validation step, background probe or another pattern is Module/Agent semantics.

## 5. Capability / inference mechanism boundary

A **Capability** is an available physical inference/execution mechanism with known properties.

```text
Operation  = Module-owned bounded semantic callable
Capability = physical computation/inference mechanism
```

One provider may expose several mechanisms through API, authenticated CLI/session, SDK, MCP, gateway, local bridge or user-installed adapter. Their cost, latency, privacy, modality, resource behavior and authentication can differ.

Provider-specific request/response schemas, credentials/login/session handling, model loading, KV cache/session controls, process mechanics and backend/device behavior remain behind the mechanism adapter or external software boundary.

The generic Kernel may expose controlled extension seams through the SDK/adapters when a mechanism has useful native capabilities, without making those mechanism-specific concepts mandatory fields of every MADRE request.

## 6. Execution paths

Kernel exposes two broad execution lifecycles:

```text
Transient inference
    Module -> ephemeral material -> Kernel -> Capability -> transient result -> Module

Durable work
    Module -> execution intent + MaterialHandle -> Kernel queue/recovery
           -> JIT resolution -> Capability -> transient result -> Module
```

Transient inference is non-durable and may carry minimal ephemeral material directly.

Durable work never queues private material inside Kernel.

The semantic composition of these primitives belongs to Modules/Agents.

At governed boundaries, the public execution structure identifies actual disclosure,
control, and effect participants so Kernel can compose the corresponding feasible
normal forms without inspecting payload semantics.

## 7. CORE placement

CORE is a Module satisfying the installation's CORE contract.

MADRE ships with a default CORE Module. The user may select another CORE-capable Module.

CORE uses the same SDK, registry, Security Algebra and execution boundaries as other Modules. It is not a second Kernel.

CORE eligibility requires sufficiently strong Privacy and Integrity characteristics for the sensitive disclosure/control paths it is expected to participate in. This does not imply low material Sensitivity: CORE-owned information may itself carry the highest Sensitivity levels.

The shipped CORE commonly provides:

- general/default UI and interaction Agent;
- fallback behavior for UI-less or Agentless Modules;
- generic delegation/escalation;
- system-oriented intelligent assistance such as configuration/install support.

When CORE owns the UI, its interaction Agent may implement low-latency response plus additional reasoning using ordinary Kernel execution primitives. Native Modules with their own UI may use their own strategy or delegate governed interaction material to CORE.

## 8. Transport neutrality

HTTP, IPC, in-process APIs, MCP, vendor CLIs and other adapters can realize the same logical boundaries. Transport does not create MADRE authority.

## 9. Responsibility test

- semantic meaning, interaction, planning, memory, learning, Skills/Workflows, material classification/validation and domain mutation → Module;
- reusable public typed integration, security-scope/relation construction and helpers → SDK/interoperability;
- deterministic security validation/evaluation, durable lifecycle, resources, mechanism selection, recovery, explicit routing and evidence → Kernel;
- provider/model/backend/software-specific computation and optimization → Capability adapter/external mechanism.
