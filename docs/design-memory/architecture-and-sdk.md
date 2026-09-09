# Architecture and SDK Design Memory

Authority: non-normative design memory. Canonical ownership remains in `MADRE.md` and `docs/architecture/MADRE-agent-interoperability.md`.

## Minimum coherent architecture

**OWNER — CONFIRMED**

MADRE needs enough structure that independent implementations share the same concepts, while avoiding a universal ontology for everything an Agent or Module might do internally.

A useful admission test for a public class/contract is:

```text
Does this concept have stable meaning across a MADRE boundary?
    no -> keep it Module/private when possible
    yes
      |
      v
Do independent implementations need to agree on its structure?
    no -> keep it opaque/value-level
    yes -> define the smallest clear public contract
```

The architecture should be concrete enough that implementation work does not invent unrelated conventions by default.

## SDK as the materialized semantic boundary

**OWNER — CONFIRMED**

The SDK is where MADRE can deliberately provide structure that would be inappropriate inside Kernel.

It should expose typed, object-oriented, interface-segregated contracts for the real public concepts and provide small reusable helpers around them.

The target is not an opinionated Agent framework. A Module developer should be able to use only the parts required by that Module.

Useful SDK responsibilities include:

```text
Module/manifest publication
Agent/Skill/Workflow/Operation contracts
Artifact / ContextBundle construction
SecurityObject binding/propagation
transient inference
Durable WorkSubmission + material resolution
result consumption
discovery/brokering helpers
Capability adapter scaffolding
CORE interaction/delegation helpers
```

## Agent simplicity and interoperability

**OWNER — CONFIRMED**

A basic Agent should remain simple enough to infer from another ecosystem or even from an informal description.

Conceptually, the simplest Agent is close to:

```text
instructions / pre-prompt
execution behavior
optional parallel/delegated execution
optional state
optional memory
```

Richer internal implementations remain possible without forcing them into the public contract.

MADRE should be able to translate external Agent definitions into a bounded Module/Agent representation. AI-assisted conversion of repositories or protocol-specific Agent configurations is a desirable interoperability/experimentation feature.

## Skills and Workflows

**OWNER — CONFIRMED**

Skills and Workflows should be similarly portable and simple.

A Skill may be inferred from another protocol, repository instructions or natural-language description. A Workflow may be derived from documented procedures such as `AGENTS.md`/command descriptions when that procedure can be expressed through bounded MADRE concepts.

The SDK should make this conversion easy; Kernel should not become the translator or workflow engine.

## WorkPlans

**OWNER — CONFIRMED**

A WorkPlan is semantic Agent/Module state. Kernel receives only executable work projected from it.

The SDK may provide a useful base representation/helper library, but it should not require every Module to use one universal planning schema.

## OOP and implementation language

**OWNER — DIRECTION**

Python is useful for rapid AI-assisted prototyping, but Python-specific idioms must not become the architecture.

Public contracts should remain suitable for a future more strongly typed implementation. Favor clear ownership, typed interfaces/value objects, explicit lifecycles, composition and human-readable naming without importing ceremonial enterprise patterns.

## Advanced extension seams

**OWNER — DIRECTION**

Some developers/research may need lower-level inference controls such as model residency, KV-cache/session reuse or backend-native vectorized state.

These should be accessible through optional SDK/Capability adapter extensions when useful rather than being universal generic Kernel fields.

## Developer-facing Modules

**OWNER — DIRECTION**

A future `MADREDeveloper` Module, no-code builder or similar shipped Module could expose creation/customization of Modules, Agents, Skills, Workflows and inference experiments.

This is a good product/research direction, especially because MADRE should support later PhD software development quickly. It is not a prerequisite for Kernel foundation; a good SDK should make such a Module straightforward to build.
