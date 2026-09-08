# MADRE Agent Interoperability

MADRE interoperability standardizes what Modules intentionally publish without standardizing their private agentic implementation.

## 1. Module Manifest

A Module Manifest publishes:

```text
module identity / version / description
semantic discovery terms
provenance
exported Agent descriptors
exported Skill descriptors
exported Workflow descriptors
exported Operation descriptors
```

The manifest exposes no private database, prompt, Agent memory/state, WorkPlan or implementation object.

Registration means only that MADRE knows a descriptor exists and which Module publishes it. Registration does not authorize the Module, grant trust, or establish reusable permission.

Descriptors may carry boundary/security envelopes because invoking that published surface introduces facts into a concrete crossing. Those envelopes participate in the additive algebra **when the crossing is formed**; they are not registry grants.

## 2. Agent Descriptor

A public Agent descriptor carries interoperability facts such as:

- public identity and owning Module;
- purpose/discovery text;
- accepted input/result contract references;
- published Skill/Workflow references when useful;
- boundary/security envelope;
- invocation metadata/provenance.

Kernel may discover and explicitly route this Agent. The owning Module remains responsible for prompts, memory, internal state, reasoning loops, internal Operations, Skill choice and Workflow choice.

## 3. Skill Descriptor

A Skill is reusable behavior/knowledge for adoption by compatible Agents. Its public descriptor may include version, purpose, input/output contracts, related Operations/Workflows, compatibility information, boundary facts and provenance.

Adoption is Module-defined. Kernel does not install Skill semantics into Agent implementations.

## 4. Workflow Descriptor

A Workflow is reusable semantic behavior. Public Workflow contracts support discovery, compatibility, input/output description, provenance and adaptation. MADRE does not require a universal Workflow executor.

## 5. Operation Descriptor

An Operation is callable Module-owned behavior. The descriptor contains enough information for discovery and deterministic brokering:

- identity and owner Module;
- purpose;
- input/output contract;
- effect characteristics;
- repeatability semantics;
- boundary/security envelope;
- provenance.

MADRE does not impose an arbitrary global naming hierarchy on descriptor IDs. Ownership is an explicit field, not inferred from string syntax.

## 6. Boundary-filtered discovery

Discovery receives the requester's carried `SecurityContext`.

For each descriptor, Kernel appends that descriptor's boundary envelope and evaluates the resulting context. It does not resolve requester trust from a Module registry.

Changing unrelated Module registry metadata therefore cannot change the carried security state of a discovery request.

The requester decides which visible descriptor is semantically useful. Actual invocation is evaluated again with the concrete material and endpoint boundary facts.

## 7. Explicit brokering

The broker takes an exact published identity and the request's carried security context:

```text
requesting Module identity (routing/evidence only)
    + carried SecurityContext
    + request material envelope
    + selected Agent/Operation descriptor envelope
    + concrete target endpoint boundary envelope
    -> input algebra evaluation
    -> Module-owned endpoint dispatch
    -> returned material envelope
    -> continued carried context
    -> output algebra evaluation
```

The registry supplies the descriptor and owner routing information. It supplies no authorization.

Broker evidence is append-only around the physical dispatch boundary. For Operations, an exception after dispatch is conservatively recorded as an unknown external effect unless stronger effect evidence exists.

## 8. Cross-Module cooperation

The intelligent participant decides which Module/Agent/Skill/Operation is relevant and what context to provide. MADRE supplies registry, boundary-filtered discovery, deterministic algebra, routing, transient transfer and evidence.

A public Agent can submit ordinary MADRE inference work from its owning Module. Kernel still does not manage that Agent's reasoning state.

## 9. Transport and provider credentials

Transport is not architecture.

The current MADRE installation is local and administered by the machine owner; MADRE does not add a separate bearer-token/per-Module authentication framework to authorize ordinary local work.

Provider authentication is different: an OpenAI/provider adapter may use API keys, OAuth or provider SDK credentials because that adapter must authenticate to its external backend. Those credentials remain inside the adapter/configuration boundary and do not become MADRE authorization.

## 10. Guided Agent tooling

Future Agent creator/import tooling should consume these public contracts to generate Modules, manifests, descriptors, boundary metadata and integration scaffolding. Tooling does not move Agent internals into Kernel.
