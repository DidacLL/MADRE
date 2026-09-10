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
Operation / OperationDescriptor / EffectProfile
Artifact / ContextBundle
WorkSubmission / WorkRecord / WorkAttempt
MaterialHandle / material resolver
InferenceRequirement / CapabilityDescriptor
SecurityID / SecurityObject / SecurityTransition
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
exported Operation descriptors + bound EffectProfiles
optional UI/interaction endpoints
material-resolution endpoint when durable work is used
SecurityID / public security facts required by those surfaces
```

The manifest exposes no private database, Agent memory, WorkPlan, prompt history or implementation object graph.

Registration means that MADRE can find and route the published surface. Security admissibility is evaluated separately through the transition algebra.

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

## 6. Operations and EffectProfiles

An `Operation` is bounded callable behavior exported by a Module.

Its public descriptor includes only facts needed to discover and invoke the bounded callable, for example:

```text
identity / owner
purpose
input/output contract
repeatability/idempotency facts
available EffectProfiles
```

Security-significant execution shapes of the same Operation are represented by immutable **EffectProfiles** owned by that Operation.

An EffectProfile binds a `RiskEnvelope(control_risk, effect_risk)`, Autonomy, realization Assurance and its concrete public control/execution/disclosure relationships. Profiles describe actual implementations; callers cannot override these facts. User participation changes the bound execution shape, not an authorization bit.

The broker evaluates every real profile separately when requested. It returns decisions, feasible IDs and all highest-Autonomy ties. Kernel does not choose tied semantic alternatives, invent profiles or retry effects. Invocation revalidates the selected publication and contract.

An Operation may internally use deterministic code, services, Workflows or inference. Those semantics remain Module-owned. The exported bounded effect, not the owning application's full power, is evaluated.

## 7. Artifacts and ContextBundles

An `Artifact` is material owned by a Module.

A `ContextBundle` is an artifact-like bounded collection of material prepared for a concrete purpose. It carries a material SecurityObject with the values required by the final Security Algebra, including Sensitivity and Assurance.

Generated model output becomes ordinary Artifact material once delivered. It can be fed into subsequent Agents/Workflows/Operations according to Module semantics.

A Module-owned TransformContract binds a concrete transformation implementation/version and evidence schema. Execution produces a new representation and MaterialContract; broker completion binds its actual sources, transform and output. This can establish different Sensitivity or Assurance according to the transform's semantics. Ordinary derivation is constrained by source/producer Assurance and cannot reduce Sensitivity. There is no generic validator role.

The SDK provides `Transform`, `TransformBehavior`, `TransformOutput` and a bound transform client. Transform behavior receives material and execution services; consumers cannot supply a result classification in place of executing it. No particular transform is part of the initial production SDK catalogue.

## 8. Discovery and brokering

Discovery exposes descriptors visible/admissible under the caller's current carried security history and the prospective discovery/invocation boundary.

The registry supplies discovery and routing facts. It does not choose semantic usefulness and registration is not authority.

The intelligent participant chooses the target Agent/Operation/Skill/Workflow. Actual invocation is evaluated again with the concrete participants/material and transition roles.

The broker routes an explicit owning-Module + exported identity and records physical dispatch/result evidence. Brokered security context continues through the invoked endpoint so nested inference/work/delegation remains part of the same security lifecycle.

If an externally effectful Operation may have executed but outcome is unknown, the effect must not be blindly repeated.

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

CORE may legitimately handle extremely sensitive user/system material. A CORE-capable Module must therefore provide sufficiently strong boundary PrivacyCapacity and role-specific Assurance for the disclosure/control paths it is expected to participate in under MADRE's Security Algebra.

This is not a privilege grant. CORE receives no bypass and every transition is evaluated by the same predicates.

CORE-owned Artifacts/ContextBundles may simultaneously have the highest Sensitivity levels. Boundary PrivacyCapacity, participant Assurance and material Sensitivity are independent dimensions.

CORE must be able to minimize, anonymize, omit, validate or otherwise transform material that belongs to CORE's own semantic domain, including representations it has deliberately accepted into that domain, through concrete bound transformation execution before sending or using the resulting representation through another transition. A source Module remains responsible for domain-specific classification/transformation that only it can perform before exporting its representation to CORE.

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

These are SDK/Module patterns. Kernel only sees the resulting inference/work/Operation requests and security transitions.

## 11. SDK helpers and extension seams

The SDK should provide small reusable helpers where they remove repeated integration work without defining new Kernel semantics. Relevant examples include:

```text
Module/descriptor registration
Agent/Operation endpoint adapters
ContextBundle/Artifact construction
SecurityObject binding/propagation
SecurityTransition construction for known disclosure/control/effect relationships
EffectProfile declaration/selection
transient inference helper
Durable WorkSubmission + material resolver helper
result consumption
CORE interaction/delegation helper
Capability adapter scaffolding
```

Correct transition construction should be difficult to omit accidentally. Semantic roles that require private content understanding remain Module-owned; Kernel evaluates the supplied bound facts/relationships without reading payload meaning.

Mechanism-specific advanced features may use optional adapter extension interfaces. For example, a local inference mechanism could expose cache/session/residency controls needed for a KV-cache management experiment without adding raw vector/KV fields to every generic `InferenceRequirement`.

## 12. Developer/import tooling

The SDK should make later tools straightforward rather than making them Kernel features.

Examples include:

- AI-assisted conversion of external Agent packages/protocols into MADRE Modules/Agents;
- Skill/Workflow extraction from repository instruction files or natural descriptions;
- a `MADREDeveloper` Module;
- a no-code Module/Agent builder;
- inference-mechanism experimentation and benchmarking;
- Module/adapter security valuation and validation tooling that emits complete bound SecurityObjects before runtime algebra evaluation.

These are valuable product/research directions but are not required for Kernel foundation.


## Execution-bound SDK services

Module entry binds `ExecutionServices` to the actual executing Agent or Operation and the attached disclosure route. Agent/Operation/Transform nested calls cannot override that context through their arguments or supplied history. Unbound configurations cannot execute; handles expire on return, failure or cancellation. Independent invocations do not share ambient state.

An `EndpointBinding` names the forwarding attachment and its actual disclosure boundaries. It is not automatically a security participant. Additional producing/realizing components are declared only where the endpoint performs those roles. The active behavior contributes to production when creating a new representation; unchanged delivery adds no producer.
