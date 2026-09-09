# MADRE Agent Interoperability and SDK

Authority: `MADRE.md` defines product meaning. This document owns public Module/Agent/Skill/Workflow/Operation contracts, CORE capability, discovery/brokering and the SDK-facing boundary.

MADRE standardizes only the structure independent implementations need to share. Module-private reasoning remains Module-owned.

## 1. SDK role

The SDK is a first-class architectural boundary, not merely an HTTP convenience wrapper.

It should provide a small, typed, object-oriented and interface-segregated contract that:

- exposes real MADRE functions without Kernel internals;
- gives developers reusable helpers for common patterns;
- is difficult to misuse accidentally;
- remains flexible enough for very different Module/Agent implementations;
- can be represented in languages other than the current Python prototype.

A Module should implement only the surfaces it needs.

The public spine currently includes concepts such as:

```text
Module / ModuleManifest
Agent / AgentDescriptor
Skill / SkillDescriptor
Workflow / WorkflowDescriptor
Operation / OperationDescriptor
Artifact / ContextBundle
WorkSubmission / WorkRecord / WorkAttempt
MaterialHandle / material resolver
InferenceRequirement / CapabilityDescriptor
SecurityID / SecurityObject
```

This list defines conceptual responsibility, not a requirement for one class per noun in every language. Exact APIs should remain as small as possible while preserving stable boundaries.

## 2. Module Manifest

A Module publishes only intentionally interoperable surfaces:

```text
module identity / version / description
discovery terms
exported Agent descriptors
exported Skill descriptors
exported Workflow descriptors
exported Operation descriptors
optional UI/interaction endpoints
material-resolution endpoint when durable work is used
SecurityID / public security facts required by those surfaces
```

The manifest exposes no private database, Agent memory, WorkPlan, prompt history or implementation object graph.

Registration means that MADRE can find and route the published surface. Security admissibility is evaluated separately through the carried algebra.

## 3. Agent

An `Agent` is a Module-owned intelligent actor.

MADRE should define a minimal public/SDK contract, while the Module remains free to implement the Agent with one model call, a reasoning loop, deterministic logic, several models, internal tools, memory, state machines or other mechanisms.

A minimal Agent can be understood as:

```text
identity / purpose
instructions or behavior definition
accepted input / result contract
available Skills / Workflows / Operations
SecurityID
execution behavior

optional:
    state
    memory
    delegation / parallel execution
    persistent configuration
```

MADRE does not currently require a universal `AgentDefinition` + `AgentInstance` ontology. A Module may use those concepts internally if useful.

A public `AgentDescriptor` carries only discoverable/invocable facts and references the owning Module.

## 4. Skills and Workflows

A **Skill** is portable Agent behavior, knowledge or instruction material.

A public Skill contract may contain:

```text
identity / purpose / revision
instructions/resources
input/output expectations
related Operations
related Workflows
compatibility information
SecurityID where relevant
```

A **Workflow** is a reusable semantic recipe/behavior owned by an Agent or Module. MADRE does not prescribe one universal workflow engine or language.

The SDK should make both concepts simple enough to construct from other ecosystems. An external Agent package, `AGENTS.md`, `CLAUDE.md`, command description or natural-language specification may be translated—manually or with AI-assisted tooling—into MADRE Agents/Skills/Workflows when its behavior can be bounded by MADRE contracts.

Translation/import tooling is an interoperability feature, not Kernel reasoning.

## 5. WorkPlans

A `WorkPlan` belongs to the Agent/Module that created it.

The SDK may provide a useful base representation or helper interfaces, but it must not force all Modules into one planning ontology.

A WorkPlan may express semantic tasks, dependencies, delegation, research, verification, user decisions, Workflow use or expected artifacts. Only physical executable projections become Kernel `WorkSubmission`s.

## 6. Operations

An `Operation` is bounded callable behavior exported by a Module.

Its public descriptor includes only facts needed to discover, invoke and evaluate its concrete boundary, for example:

```text
identity / owner
purpose
input/output contract
effect/risk characteristics
autonomy characteristics
repeatability/idempotency facts
SecurityID
```

MADRE-provided AI integrations expose external/system effects through specific Operations or bounded mechanisms. There is no generic Agent shell or unrestricted Internet capability.

An Operation may internally run deterministic code, use another service, invoke a Workflow or submit MADRE inference work; those internals remain Module-owned.

## 7. Artifacts and ContextBundles

An `Artifact` is material owned by a Module.

A `ContextBundle` is an artifact-like bounded collection of material prepared for a concrete purpose. It should preserve enough structure for the Module to assign the relevant SecurityObject and for later boundaries to identify the material being used.

Generated model output becomes ordinary Artifact material once delivered. It can be fed into subsequent Agents/Workflows/Operations according to Module semantics.

## 8. Discovery and brokering

Discovery exposes descriptors visible/admissible under the caller's current carried security objects.

The registry supplies discovery and routing facts. It does not choose semantic usefulness.

The intelligent participant chooses the target Agent/Operation/Skill/Workflow. Actual invocation is evaluated again with the concrete participants/material.

The broker routes an explicit published identity and records physical dispatch/result evidence. If an externally effectful Operation may have executed but outcome is unknown, the effect must not be blindly repeated.

## 9. CORE-capable Module contract

MADRE ships with a default CORE Module. A user may select another installed Module that satisfies the CORE contract.

CORE is an ordinary Module at the Kernel boundary and uses the same SDK/security/execution mechanisms as other Modules.

### Required functional capability

A CORE-capable Module must be able to provide the installation's generic/default intelligent role, including:

- a general interaction endpoint suitable for MADRE's default UI/UX;
- a default interaction Agent or equivalent intelligent behavior;
- fallback handling/delegation for Modules that do not provide their own suitable Agent/UI path;
- generic delegation/escalation across visible Module surfaces;
- MADRE-system-oriented intelligent assistance required by the standard experience, such as configuration/install guidance.

The shipped default CORE may add richer personal-assistant behavior, profiles, memory, Skills, Workflows and developer tooling. Those are Module-owned features, not Kernel requirements.

### Required security capability

CORE may legitimately handle extremely sensitive user/system material. A CORE-capable Module must therefore satisfy the strongest applicable isolation/privacy/trust characteristics defined by MADRE's final security algebra.

This is not a privilege grant. CORE receives no bypass and every crossing is evaluated by the same algebra.

CORE-owned Artifacts/ContextBundles may simultaneously have the highest Sensitivity levels. Actor/containment security and material sensitivity are independent dimensions.

CORE must be able to minimize, anonymize, omit or otherwise transform Module-owned material before sending it through a less-private or higher-risk boundary when the owning semantics permit such a transformation.

### Default selection

The shipped CORE remains selected by default. If another installed Module satisfies the CORE contract, the user may configure it as CORE. No elaborate election/role-management system is required.

## 10. Interaction surfaces

MADRE does not require one UI.

An interaction may originate from:

- a Module-owned native UI;
- the default CORE UI/UX;
- a provider/tool application GUI or CLI through an adapter;
- an automation or machine-facing integration.

When CORE owns the interaction surface, all direct user input enters CORE's interaction Agent/behavior. That Agent may use transient low-latency inference for immediate natural interaction and create/delegate further work as necessary.

A native Module with its own UI may handle its own semantics or explicitly send governed input to CORE's interaction endpoint.

A Module or adapter without its own UI may delegate its default interaction to CORE; this is the normal fallback for simple third-party/unknown wrappers.

These are SDK/Module patterns. Kernel only sees the resulting inference/work/Operation requests.

## 11. SDK helpers and extension seams

The SDK should provide small reusable helpers where they remove repeated integration work without defining new Kernel semantics. Likely examples include:

```text
Module/descriptor registration
Agent/Operation endpoint adapters
ContextBundle/Artifact construction
SecurityObject binding/propagation
transient inference helper
Durable WorkSubmission + material resolver helper
result consumption
CORE interaction/delegation helper
Capability adapter scaffolding
```

Mechanism-specific advanced features may use optional adapter extension interfaces. For example, a local inference mechanism could expose cache/session/residency controls needed for a KV-cache management experiment without adding raw vector/KV fields to every generic `InferenceRequirement`.

## 12. Developer/import tooling

The SDK should make later tools straightforward rather than making them Kernel features.

Examples include:

- AI-assisted conversion of external Agent packages/protocols into MADRE Modules/Agents;
- Skill/Workflow extraction from repository instruction files or natural descriptions;
- a `MADREDeveloper` Module;
- a no-code Module/Agent builder;
- inference-mechanism experimentation and benchmarking.

These are valuable product/research directions but are not required for Kernel foundation.
