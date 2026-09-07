# MADRE Agent Interoperability

MADRE interoperability standardizes what Modules intentionally publish without standardizing their private agentic implementation.

## 1. Module Manifest

A Module Manifest publishes:

```text
module identity / version / description
semantic discovery terms
security envelope and provenance
inbound boundary requirements
exported Agent descriptors
exported Skill descriptors
exported Workflow descriptors
exported Operation descriptors
```

Every exported descriptor identifies its owning Module and uses an owner-qualified public identity. The manifest exposes no private database, prompt, Agent memory/state, WorkPlan or implementation object.

Descriptor provenance may come from native MADRE metadata, generated tooling, known protocols, MCP/OpenAPI adapters, package metadata, source inspection or manual configuration. Weak provenance is represented conservatively rather than treated as safe.

Registration is a trusted installation/adapter responsibility because it establishes public identity and security facts. An invocation payload cannot raise its own trust by supplying a different requester envelope.

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

- owner-qualified identity and owner;
- purpose;
- input/output contract;
- effect characteristics;
- repeatability/idempotency semantics;
- execution boundary/security requirements;
- provenance.

A requester selects an Operation semantically and asks Kernel to invoke that exact identity. Kernel evaluates the crossing and dispatches to the owning Module endpoint.

## 6. Boundary-filtered discovery

Discovery is filtered before descriptors are returned. The requester is identified by Module identity; Kernel resolves its current registered boundary rather than accepting trust values from the discovery request.

Visibility evaluates requester, descriptor and destination Module boundary facts. Visibility is not invocation authorization; the actual material crossing is evaluated again when an Agent/Operation is invoked.

Kernel does not inspect a user's prompt and automatically choose a public Agent/Operation.

## 7. Explicit brokering

The broker takes an exact registered identity:

```text
requesting Module identity
    -> current registered requester boundary
    -> explicit Agent/Operation id
    -> request material + envelope
    -> input boundary evaluation
    -> owning Module endpoint
    -> returned material + new envelope
    -> output boundary evaluation back to requester
```

Dispatch never transfers semantic ownership into Kernel. The endpoint returns bounded material rather than an unclassified bare payload, so the return crossing is independently governed.

Broker evidence is append-only around the dispatch boundary. For Operations, an exception after dispatch is conservatively recorded as an unknown external effect unless the Module can provide stronger effect evidence; Kernel does not assume a safe retry merely because it observed an exception.

Broker security/effect evidence persists identifiers, envelope fingerprints, decisions, digests and event state, not invocation content.

## 8. Cross-Module cooperation

The intelligent participant decides which Module/Agent/Skill/Operation is relevant and what context to provide. MADRE supplies registry, filtered discovery, deterministic security, routing, transient transfer and evidence.

A public Agent can itself submit ordinary MADRE inference work from its owning Module. Kernel still does not manage that Agent's semantic reasoning state.

## 9. Transport identity

Transport authentication is distinct from the architecture contracts. The reference HTTP service currently uses one installation-admin bearer credential for local compatibility/administration and is not a finished per-Module identity system.

That administrator credential must not be treated as a reusable Module trust token. Native IPC/SDK or future remote transports should bind authenticated caller identity to the registered Module identity through an installation-controlled mechanism.

## 10. Guided Agent tooling

Future Agent creator/import tooling should consume these public contracts to generate Modules, manifests, descriptors, security metadata and integration scaffolding. Tooling does not move Agent internals into Kernel.
