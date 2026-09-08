# Architecture and SDK Design Memory

Authority: non-normative design memory. Canonical ownership remains in `MADRE.md` and focused architecture contracts.

## Minimum coherent architecture, not ontology inflation

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

MADRE must avoid two opposite failure modes.

### Over-definition

Do not turn every useful noun into a universal MADRE class hierarchy or framework ceremony. Avoid speculative ontologies for Agent internals, planners, session/memory systems, universal Workflow engines, policy objects, or other structures that belong inside Modules unless cross-boundary interoperability truly requires them.

### Under-definition

Do not leave core concepts structurally vague and then implement them additively as individual features arrive. That vacuum repeatedly causes unrelated industry conventions to leak into MADRE simply because they are familiar.

The target is the smallest coherent, modular, human-readable object model that makes MADRE's actual boundaries explicit enough for independent Modules and the SDK to interoperate safely.

## Class/materialization rule

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED principle**

Classes/contracts are justified when a concept has stable meaning across a MADRE boundary and independent implementations need to agree on its structure.

Examples that plausibly earn explicit public contracts include Module/public descriptors, executable work, material handles/resolution, inference requirements/mechanisms, bounded Agent/Operation endpoints, and carried security state.

Module-private reasoning internals should remain opaque unless a concrete interoperability requirement proves otherwise.

Do not preserve or reject a class merely because it already exists. Evaluate whether the responsibility itself belongs in MADRE.

## WorkPlans

Provenance: **DIRECT OWNER / later correction**
Status: **CONFIRMED**

Semantic WorkPlans belong to the Agent/Module doing the planning. Kernel receives only the executable projection needed for deterministic work scheduling/recovery.

Earlier generated schemas that persisted semantic WorkPlans or planning step machines inside Kernel are superseded.

The exact Module-side WorkPlan schema remains open and may vary by Agent/Module implementation.

## Agents, Skills and Workflows

Provenance: **DIRECT OWNER / CURRENT DIRECTION**
Status: **CONFIRMED concepts; exact public class shapes OPEN**

MADRE should support Agents, reusable Skills and Workflows as meaningful interoperable concepts without forcing one universal private implementation model.

The Owner has explicitly rejected the old `ReasoningModule`/ClassPath framing. A more natural direction is that Agents expose/adopt useful Workflows and Skills through clear public contracts while retaining their internal reasoning architecture inside their Module.

Do not respond to past ontology drift by declaring these concepts undefinable. They eventually need simple materialized contracts where interoperability requires them.

## SDK

Provenance: **DIRECT OWNER / OWNER CONFIRMED**
Status: **CONFIRMED**

The SDK should expose the actual MADRE protocol in a modular, SOLID/interface-segregated form. It should make correct MADRE integration pleasant and difficult to misuse without becoming an opinionated Agent/Workflow framework.

A Module should depend on the public surfaces it actually needs, not Kernel persistence, scheduler internals, FastAPI objects, provider-specific adapters, or unrelated semantic framework classes.

CORE should be the first substantial SDK consumer through exactly the same public Module contracts as third-party Modules. If CORE needs privileged semantic access, that is evidence the public boundary is incomplete or ownership has drifted.

## Owner consultation rule behind the architecture

Provenance: **DIRECT OWNER**
Status: **CONFIRMED**

When a material architectural gap cannot be resolved from canonical contracts and relevant design memory, ask the Owner rather than importing a conventional pattern. This does not mean asking for approval of routine code or sending large engineering documents for review.

The useful question is narrow: identify the unresolved concept, the concrete alternatives/consequences, and ask only for the product/architecture intent that cannot be inferred safely.
