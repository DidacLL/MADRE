# MADRE Agent Interoperability

MADRE interoperability standardizes what Modules intentionally publish without standardizing their private agentic implementation.

## 1. Module Manifest

A Module Manifest publishes:

```text
module identity / version / description
semantic discovery terms
security envelope and provenance
exported Agent descriptors
exported Skill descriptors
exported Workflow descriptors
exported Operation descriptors
```

Every exported descriptor identifies its owning Module. The manifest exposes no private database, prompt, Agent memory/state, WorkPlan or implementation object.

Descriptor provenance may come from native MADRE metadata, generated tooling, known protocols, MCP/OpenAPI adapters, package metadata, source inspection or manual configuration. Weak provenance is represented conservatively rather than treated as safe.

## 2. Agent Descriptor

A public Agent descriptor carries stable interoperability facts such as:

- public identity and owning Module;
- purpose/discovery text;
- accepted input/result contract references;
- published Skill/Workflow references when useful;
- boundary/security properties;
- invocation metadata/provenance.

Kernel may discover and explicitly route this Agent. The owning Module remains responsible for prompts, memory, internal state, reasoning loops, internal Operations, Skill choice and Workflow choice.

## 3. Skill Descriptor

A Skill is reusable behavior/knowledge for adoption by compatible Agents. Its public descriptor may include version, purpose, input/output contracts, related Operations/Workflows, compatibility requirements and provenance.

Adoption is Module-defined. It may mean adding prompt material, resources, retrieval configuration, Workflows, Operations or state-machine changes. Kernel does not install Skill semantics into Agent implementations.

## 4. Workflow Descriptor

A Workflow is reusable semantic behavior. Public Workflow contracts support discovery, compatibility, input/output description, provenance and adaptation. MADRE does not require a universal Workflow executor. Execution belongs to the Module/Agent that adopts the Workflow.

## 5. Operation Descriptor

An Operation is callable Module-owned behavior. The descriptor contains enough information for discovery and deterministic safe brokering:

- identity and owner;
- purpose;
- input/output contract;
- effect characteristics;
- repeatability/idempotency semantics;
- execution boundary/security requirements;
- provenance.

A requester selects an Operation semantically and asks Kernel to invoke that exact identity. Kernel evaluates the crossing and dispatches to the owning Module endpoint.

## 6. Boundary-filtered discovery

Discovery is filtered before descriptors are returned. Current evaluation uses requester/destination envelope integrity, requester trust and scope visibility constraints. The requester then decides which visible descriptor is useful.

Kernel does not inspect a user's prompt and automatically choose a public Agent/Operation.

## 7. Explicit brokering

The broker takes an exact registered identity:

```text
requesting Module
    -> explicit Agent/Operation id
    -> current material + envelope
    -> Kernel boundary evaluation
    -> owning Module endpoint
```

Broker dispatch records security-decision evidence but does not persist invocation content.

This proves the architectural distinction between `AgentDescriptor` and Agent implementation: the reference Kernel contains descriptors and routing only; tests supply Module endpoints that own the actual behavior.

## 8. Cross-Module cooperation

The intelligent participant decides which Module/Agent/Skill/Operation is relevant and what context to provide. MADRE supplies registry, filtered discovery, deterministic security, routing, transient transfer and evidence.

A public Agent can itself submit ordinary MADRE inference work from its owning Module. Kernel still does not manage that Agent's semantic reasoning state.

## 9. Guided Agent tooling

Future Agent creator/import tooling should consume these public contracts to generate Modules, manifests, descriptors, security metadata and integration scaffolding. Tooling does not move Agent internals into Kernel.
