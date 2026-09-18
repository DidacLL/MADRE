# MADRE

This file is the canonical product definition for MADRE. Current Owner instructions
override it when they are more specific. Code, tests, documentation, history and prior
agent reports are evidence only; they do not define the product by repetition.

## Product

MADRE is an owner-sovereign, local-first environment for creating, installing,
running, composing, inspecting, modifying and experimenting with modular agentic
software.

It is one system with two inseparable uses:

- an installed environment the Owner can use directly; and
- the development platform for building MADRE-native software.

The Owner may move continuously from simple use to inspection, configuration,
development, debugging, modification, replacement and unrestricted experimentation.
These are depths of ownership, not separate user roles or permission classes.

MADRE exists because existing assistant architectures, agent loops, provider routers,
workflow engines, security products and AI platforms do not provide this environment.
Common industry structure is therefore not a default design argument. A concept enters
MADRE only because MADRE itself needs it.

The engineering target is:

> **powerful SDK, simple implementation, fast experimentation, minimal ceremony**

MADRE is a single-developer research project becoming durable software. It must avoid
enterprise decomposition, compatibility machinery without users, framework-within-
framework designs, duplicated infrastructure, speculative abstraction hierarchies,
large ritual test matrices and generated architectural bureaucracy.

## Owner sovereignty

The Owner owns the entire installation and is never the adversary.

The Owner may inspect, edit, replace, remove or bypass any Module, Agent state,
Material store, CORE assignment, inference-engine configuration, runtime state,
Kernel database, installed artifact, SDK-built software, source file or raw persistence.

MADRE should make those actions understandable and recoverable through descriptions,
validation, warnings, diagnostics, backups and restore facilities. Those facilities
assist the Owner; they do not gate the Owner. Shipped, manual and internal artifacts do
not acquire authority over the Owner.

The important adversarial concern is third-party providers obtaining or exploiting
Owner information without the Owner understanding and managing the information
journey. MADRE makes those journeys explicit without turning the Owner into a subject
of an access-control system.

## SDK

The MADRE SDK is a primary product, not a thin adapter around an internal platform.
It is the ordinary construction environment for shipped and independent MADRE
software.

The SDK provides useful composable building blocks, including:

- Modules, Material and bounded Operations;
- Agents, conversational and stateful Agent facilities;
- Skills and Agent-owned Workflows;
- semantic inference constructions;
- Security Algebra;
- Module discovery and composition;
- typed state, testing and authoring helpers;
- reusable operation facilities such as search, generation and embeddings when they
  are useful to Module developers.

An SDK facility does not require a shipped Module to justify its existence. Its test is
whether it solves a reusable MADRE development problem clearly and cheaply. The SDK
must not prescribe one universal Agent architecture, memory system, reasoning strategy,
tool loop, planner or user experience.

The shipped default Module uses the same public SDK model available to an independent
Module developer. Building real Modules is how repeated development problems are
discovered and how the SDK earns new abstractions.

## Modules, Agents, Workflows and Operations

A Module is an independently installable semantic/application unit. It owns a coherent
domain or application responsibility, including its state, persistence, Material,
Operations, integrations and optional specialised Agents.

A Module does not have to define an Agent.

Agency nevertheless exists for every Operation execution. Only an Agent executes an
Operation. The MADRE runtime resolves the acting Agent in this order:

1. an Agent explicitly selected by the semantic caller;
2. the unambiguous Module Agent that owns the Operation;
3. the default Agent of the ordinary Module assigned CORE.

If no Agent can be resolved, or multiple Module Agents claim the Operation without an
explicit choice, execution fails explicitly. An agentless Module therefore retains its
domain ownership while the default CORE Agent supplies the agency needed to act.

An Agent owns semantic agency: intent, interpretation, context use, semantic decisions,
composition, continuation and judgement about results. An Agent may execute Operations
belonging to its own Module or bounded Operations exposed by another Module.

A Workflow belongs to the Agent whose reusable semantic behaviour it represents. It is
executable Agent behaviour, not a Module declaration, Kernel schedule, generic DAG or
universal workflow engine.

An Operation is one bounded unit of Module-owned behaviour available to an Agent.
It may calculate, access files or databases, call an API, search, use MCP, invoke a
process, request inference or combine those actions. These ordinary transformations do
not become Kernel work merely because they affect the physical world.

CORE is an installation role assigned to an ordinary Module. Its default Agent normally
provides general Owner interaction and fallback agency for agentless Modules. CORE is
not a subtype, privilege, trust level, Kernel mode, execution bypass or protected
artifact.

## Security Algebra

Security Algebra is intrinsic compositional structure of semantic MADRE objects. Its
five nominal dimensions are Sensitivity, Privacy, Integrity, Risk and Autonomy.
Applicable values live on the semantic constructions where they mean something and
compose as those constructions combine.

Security Algebra is not:

- an authorization or policy service;
- an evaluator consulted for permission;
- IAM, credentials, clearance or provider trust;
- a disclosure subsystem;
- a global score;
- Kernel state or an inference-engine selection algorithm.

A valid semantic composition may continue. An incompatible composition cannot continue
as that construction. Publishing, revealing, minimising, transforming or sending
information are ordinary semantic Operations and Material constructions, not a special
universal disclosure architecture.

Semantic composition may yield a technical inference requirement such as local-only
execution. Kernel receives that technical requirement, never the Algebra values or the
semantic reason that produced it.

## MADRE runtime

The MADRE runtime is the installed semantic environment around the SDK and Kernel. It
is not a thin API host.

It owns installation-wide responsibilities:

- Module and inference-engine installation and configuration;
- live Module discovery and composition;
- CORE assignment and default-Agent resolution;
- Agent execution and bounded Operation invocation;
- Security Algebra application;
- semantic inference-request persistence and continuation;
- translation from semantic inference intent to technical requirements;
- correlation of Kernel Work with the requesting Agent;
- Owner interaction, inspection, diagnostics, modification, backup and recovery;
- startup, shutdown and packaging.

Domain meaning and state remain with their Modules and Agents. Runtime coordination
does not make the runtime the owner of those semantics.

## Inference Kernel

Kernel manages physical inference work only. Inference includes local or remote LLMs
and SLMs, multimodal and computer-vision systems, embeddings, classic machine-learning
algorithms and experiments, and future inference families that demonstrate a real need.

Kernel does not execute ordinary Module API calls, calculator functions, search clients,
filesystem actions or other non-inference Operations.

Kernel owns:

- the inventory and technical characteristics of installed inference engines;
- matching engine-agnostic inference requirements to available engines;
- explicit Owner engine/model choices when supplied;
- machine-resource allocation and admission;
- physical inference scheduling, priority and timing;
- retry, cancellation, interruption and recovery;
- technical execution attempts and experiment measurements;
- provision of the technical result back to MADRE runtime.

Kernel is blind to Module, Agent, Material, Operation, Workflow, Skill, ReasoningRequest,
Security Algebra, semantic continuation and result quality.

MADRE runtime submits technical requirements rather than an eligible-engine list. Those
requirements may describe inference family, locality, latency, urgency, family-specific
ability and an optional exact Owner-selected engine. Kernel knows which engines are
actually installed and chooses the best current match.

Llama.cpp and OpenAI-compatible inference are peer optional implementations. Neither is
privileged, and a valid MADRE installation may configure neither.

## Persistence and recovery

Semantic and inference-runtime persistence remain deliberately separate.

MADRE runtime or the owning Module/Agent persists prompts, context, semantic requests,
acting Agent, origin, plans, continuation and interpreted results.

Kernel persists technical lifecycle only: opaque Work identity, inference type,
requirements, scheduling state, attempts, selected engine, resources, timings,
cancellation, retry, interruption and technical failures. Kernel does not persist or
log prompt/input content, output content or semantic provenance.

After restart, MADRE runtime reattaches the exact executable input for pending Work from
semantic persistence. Kernel hands a completed technical result to runtime; runtime
durably correlates it before Kernel records delivery. Unknown outcomes are represented
honestly and never converted into silent duplicate inference.

## Development and evidence

Owner intent determines acceptance. Tests, CI, documentation and reports are evidence
about a particular implementation, never architectural authority.

Use real execution for real-execution claims. Use deterministic tests for bounded
failure behaviour and regressions. Prefer strong Java objects, dependency direction and
executable behaviour over regex architecture policing, metadata bags and declarative
files that merely promise another component will behave correctly.

Development should remain autonomous and interruption-safe. Commit and push each
coherent behaviour as it becomes usable. The Owner is not a recurring manual QA step.
Cross-platform or real-provider checks are run by agents or automation when they prove a
changed behaviour.

There is no installed user base or production data requiring development compatibility.
When a generated schema or API is wrong, replace it directly and recreate generated
state. Do not leave migrations, adapters, deprecated fossils or duplicated models that
teach future agents the rejected architecture.
