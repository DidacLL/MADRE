# MADRE Agent Interoperability

Authority: `MADRE.md` defines product meaning. This document owns public
Module/Agent/Skill/Workflow/Operation contracts, CORE capability, registry/brokering,
and the SDK-facing boundary.

## Ownership

A Module owns domain meaning, data, UI/interaction, Agent reasoning and state, Skills,
Workflows, WorkPlans, transformations, validation, result interpretation, and domain
effects. Kernel owns deterministic physical execution, routing, security composition,
durable lifecycle, resources, and evidence. A model never emits semantic commands that
Kernel interprets as Operations.

CORE is an ordinary replaceable Module using the same SDK and security relations. It
has no bypass or private Kernel contract.

## Public descriptors

`ModuleManifest` publishes a Module revision and its Agent, Skill, Workflow, and
Operation descriptors. Descriptors are existence, compatibility, and routing facts;
registration creates no authority.

An Agent descriptor exposes identity, purpose, input/output contracts, published
Skills/Workflows, exact participant SecurityObject, and provenance. Agent private
reasoning, sessions, memory, prompts, and domain state remain private.

Skills are reusable instructions/resources and Workflows are reusable semantic
recipes. MADRE does not impose a universal Skill instance, Workflow engine, Planner,
Task ontology, or Agent loop.

## Operations and EffectProfiles

An Operation is bounded callable behavior implemented by its owning Module. Its
descriptor publishes identity/revision, purpose, input/output contracts, effect and
repeatability facts, and one or more immutable EffectProfiles.

An EffectProfile is only a bounded Operation variant:

```text
exact Operation/profile identity + Risk + Autonomy
```

It contains no Integrity, Privacy, SecurityObject, or disclosure flag. Actual
observers and executors are properties of the concrete route, not the abstract
profile.

The caller supplies an `OperationUse` selecting a published profile plus any
additional concrete disclosure observers and semantic non-user controllers. The
active direct-user interaction may be carried only for the immediate invocation.
Risk and Autonomy cannot be caller-overridden. Broker adds protocol-known target
observers and effect executors, then composes Disclosure, Control, and EffectExecution
normal forms atomically before dispatch.

Unknown external-effect outcomes are not blindly retried or reclassified as ordinary
failures.

## Material and derivation

Artifact and ContextBundle are Module-owned immutable representations. Their material
scope requires exact content binding and Sensitivity. Ordinary material construction
cannot assert Integrity.

Ordinary derivation preserves the source Sensitivity lower bound. Selection uses
exactly retained members. A semantic transform is a Module-owned procedure producing
a new representation and may establish a different Sensitivity. Explicit validation
binds an exact procedure and actual validators to a distinct Integrity-bearing
projection. Raw inference output remains ordinary material without Integrity.

Material contracts carry exact SecurityObjects plus `SecurityEvidence`; evidence is
not active admission state. Kernel does not persist private material payloads.

## Registry and discovery

Registry is a catalog/router. It supports registration, exact Module/Agent/Operation
lookup, and deterministic `list_agents`, `list_skills`, `list_workflows`, and
`list_operations`. These lists accept no security history/evidence input and make no
admissibility claim.

Useful security-aware discovery cannot be decided from accumulated history or a
partial source alone. A future subsystem must receive a complete typed prospective use
including source, selected EffectProfile, controllers, executor route, and destination
facts. That subsystem is deliberately deferred; the current honest catalog leaves it
unblocked.

Semantic usefulness and target choice remain Module/Agent responsibilities.

## Exact brokering

`InvocationContext` explicitly carries the current Module, optional Agent or
Operation, exact endpoint attachment, and optional live direct-user interaction.
It is created at Module/Kernel entry and propagated through endpoint, behavior, and
bound nested clients. It is never reconstructed from evidence and never stored as
ambient asynchronous state.

Agent input discloses only to actual target Module/Agent/endpoint observers. Agent
output creates a new disclosure to the captured requester recipients.

Operation input uses `OperationUse` plus Kernel-known topology:

```text
source -> actual target and additional observers
profile + actual non-user controllers
profile + actual effect executors
```

All three forms must be accepted before dispatch. Return material gets a new
disclosure relation. Historical participants, credentials, ownership, Work IDs,
retries, and denied route candidates are not operands.

Endpoint attachment captures the exact Module publication and endpoint SecurityObject.
Replacement or mutation cannot silently change an in-flight target. Additional
transport recipients/executors must be represented explicitly by the adapter or
Broker route that actually introduces them.

## SDK boundary

The SDK exposes immutable public contracts and segregated factories for material,
observer/participant, and controller/executor scopes; EffectProfile declaration;
relation composition; Module semantics; material derivation; execution clients; and
broker clients.

Configured clients are inert. Module entry creates invocation-bound copies; the
binding expires when execution ends and cannot be rebound. Callers may add evidence,
observers, or controllers through typed contracts, but cannot replace the active
causal identity.

The SDK imports only stable public Kernel contract namespaces. Kernel imports neither
SDK nor CORE. CORE imports only the SDK.
