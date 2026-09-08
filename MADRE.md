# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first platform that connects AI-capable applications to shared intelligence resources while keeping semantic ownership in those applications.

Applications and their Modules decide what intelligence work is useful. MADRE provides deterministic infrastructure for interoperability, security evaluation, transient inference, durable work, scheduling/resource arbitration, physical inference-mechanism selection, recovery, brokering, result delivery and execution evidence.

## Authority map

This file is the canonical product contract: it defines what MADRE is and the responsibility boundaries that implementations must preserve.

Detailed architecture is owned by focused documents:

- `docs/architecture/MADRE-platform-architecture.md` — topology and responsibility placement;
- `docs/architecture/MADRE-execution-contract.md` — transient/durable execution, material, inference requirements, scheduling-facing semantics, results and recovery;
- `docs/architecture/MADRE-agent-interoperability.md` — public Module/Agent/Skill/Workflow/Operation contracts, discovery, brokering and SDK-facing Module interfaces;
- `docs/architecture/MADRE-security-algebra.md` — immutable security facts, carried composition, provenance and deterministic admissibility.

`docs/implementation-baseline.md` describes repository state only; it is not product authority. `docs/design-memory/` preserves non-normative Owner rationale, examples, correction lineage and open directions for just-in-time retrieval.

## Responsibility model

```text
Module
    semantic/domain ownership and private agentic implementation
        |
        | public descriptors / explicit execution requests
        v
MADRE interoperability
    public Module/Agent/Skill/Workflow/Operation contracts
        |
        | explicit destination / execution projection
        v
Kernel
    deterministic security, transient/durable execution,
    scheduling/resources, mechanism selection, recovery,
    brokering, delivery and evidence
        |
        v
Inference mechanism / Capability adapter
    bounded physical computation and concrete provider/software integration
```

A **Module** is an independently owned application or integration boundary. It owns domain records and persistence, UI/interaction state, private context/history, knowledge/retrieval, Agents and Agent state, Skills, Workflows, WorkPlans, artifacts, Operations/domain mutations, domain-specific privacy/routing semantics, learning and adaptation. A Module may expose no Agents at all.

MADRE interoperability standardizes only what Modules intentionally publish. A public descriptor never transfers ownership of the implementation behind it.

Kernel owns deterministic execution control. If a Kernel decision requires understanding what a prompt/domain record means, that decision is misplaced. If private Module history, prompt/context, Agent state or semantic WorkPlans become durable Kernel state, ownership has drifted.

An inference mechanism (called a **Capability** in current code) is a physical execution route with known properties. It is not a semantic Skill or a claim that a model can solve a user task.

## Core semantic entities

An **Agent** is a Module-owned intelligent actor. Prompt, memory/state, reasoning architecture, internal tool use and semantic Skill/Workflow choice remain private to its Module.

A **Skill** is reusable Agent behavior or knowledge packaged for adoption by compatible Agents. MADRE standardizes transferable/public contracts, not the adopting Agent's internal implementation.

A **Workflow** is reusable semantic behavior owned and executed by an Agent/Module. MADRE does not require a universal Workflow execution language.

A **WorkPlan** is semantic planning state owned by the Module/Agent that created it. Kernel never owns or interprets the semantic plan; executable pieces are projected into ordinary MADRE work.

An **Operation** is callable behavior intentionally exported and implemented by a Module. Kernel may discover and explicitly broker it but does not own its semantics or side effects.

## Product invariants

### 1. Module semantics remain outside Kernel

Modules decide what data matters, what a request means, what reasoning/delegation is useful, how outputs are interpreted, and what domain mutation should occur. Kernel performs deterministic execution functions without reconstructing that meaning.

Generated model output is ordinary Module-owned material once delivered. A Module/Agent may deliberately use it as later input, evidence, state, planning material, or a basis for a bounded Operation according to its own semantics. Kernel does not impose a universal rule that generated material is semantically non-authoritative.

### 2. Interactive use is dual-lane, not blocking-chatbot execution

MADRE is not fundamentally a chatbot or oracle that must synchronously answer every request.

Interactive Modules may fork one user input into:

```text
fast transient MADRE inference
    +
non-blocking reasoning path
```

The fast lane is real inference, not canned acknowledgements. It uses minimal ephemeral interaction material and is optimized for continuity/low latency. The parallel reasoning path may answer, schedule durable work, or delegate/escalate through Module-owned intelligence. Exact wording, routing and UX remain Module/Agent responsibilities.

The exact topology of that fork may continue to evolve, but long reasoning must not unnecessarily block ordinary Module/MADRE interaction.

### 3. Durable work owns execution intent, never queued private material

Durable execution is:

```text
WorkSubmission -> WorkRecord -> 0..N WorkAttempts
```

Immediately eligible and delayed durable work use the same lifecycle.

For all accepted/queued durable work, Kernel persists only execution metadata and a verifiable material handle. The Module retains actual prompt/context/material. Kernel acquires it just-in-time for the concrete execution attempt, verifies it, uses it transiently and discards it. Restart and retry recover execution intent by resolving material again from the Module.

### 4. Kernel treats private/generated payload content as transient opaque material

MADRE may persist public descriptors, execution/mechanism metadata, work/attempt lifecycle state, scheduling/resource evidence, material/output digests, security facts/decisions, failure/delivery evidence and opaque coordination/correlation values.

MADRE does not durably persist prompts, conversation/private/retrieved context, private documents, Agent state, semantic WorkPlans, model answers/generated content, application history or domain records.

Kernel does not derive semantic policy, truth, intent, permissions or scheduling meaning from prompt/output bytes. It handles only the explicit metadata, boundary/security facts, integrity references and transient material necessary for a concrete execution/transfer.

Successful durable-work output is transient until consumed. A restart before consumption is recorded truthfully as result loss rather than upgraded to hidden content durability. Once delivered, its semantic meaning belongs entirely to the receiving Module.

### 5. Modules request execution properties; Kernel selects physical mechanisms

Modules normally do not know the installed inference inventory. They express execution requirements/preferences; Kernel deterministically matches them against currently available mechanisms and resource state.

Local inference mechanisms are the primary product focus, while admissible remote/provider mechanisms remain valid first-class execution options.

Provider/model identity may be a preference or a hard requirement according to the request. Cost, latency/interaction class, reasoning effort/quality, modality/specialization, locality/privacy, resource availability and fallback semantics may all be execution properties. Detailed selection semantics belong to `MADRE-execution-contract.md`.

A provider is not one mechanism. The same provider may be reachable through multiple independently usable adapters/software paths with different cost, latency, authentication, modality and operational properties. MADRE must not flatten that ecosystem into one provider-shaped integration or prohibit user-installed mechanisms merely because they are unconventional.

### 6. Security admissibility comes only from the carried boundary algebra

Security state belongs to the concrete request/work lifecycle as immutable boundary/security facts accumulated in a carried context.

Registration, installation acceptance, identity, bearer credentials, roles, ACLs, allowlists, references and previous decisions grant no MADRE permission. Concrete material, Module/Agent/Operation and inference-mechanism boundaries contribute facts when those crossings actually occur; Kernel evaluates the accumulated state deterministically.

Mutable registry state cannot retroactively rewrite accepted work security history.

The normalized algebra is deliberately small and is not a disguised clearance/matching system. Do not invent actor sensitivity ceilings, minimum-input-trust fields, mirrored requirement/property matrices, or generic policy/role machinery merely because those are conventional security patterns. The exact algebra must follow real MADRE sensitivity/privacy/risk cases; detailed current status belongs to `MADRE-security-algebra.md`.

Current Kernel `trust` terminology refers only to boundary/provenance/security facts. It is not semantic truth, prompt-injection detection, hallucination probability, answer quality or generic AI-content safety. Future AI-specific security signals require an explicit deterministic design rather than being assumed today.

### 7. Public interoperability does not make Kernel an Agent framework

Modules may publish discoverable Agent, Skill, Workflow and Operation descriptors. Boundary-visible requesters choose semantic targets; Kernel evaluates/routes explicit invocations and records evidence.

MADRE-provided AI surfaces do not grant Agents an unbounded shell or unrestricted Internet environment. System/network/external effects are exposed through specific bounded Module Operations or concrete mechanisms with explicit boundary/risk facts. A user may install custom Modules/adapters, but their effects remain explicit integrations rather than hidden generic Agent authority.

Unknown external Operation effects must not be blindly retried when dispatch outcome is uncertain.

The public Module boundary should support a modular SDK. The default CORE Module must consume the same public contracts as third-party Modules and remain independent from Kernel.

CORE is a replaceable default/fallback Module role plus a shipped first-party implementation, not a privileged architectural layer. The shipped CORE may provide fallback intelligence for Agentless Modules, fast/default interaction behavior, generic UI/UX, module-independent intelligent tasks such as configuration/installation assistance, and routing/escalation for work that no more specific Module owns. Another Module may be configured to fulfill that role instead. Default status may influence fallback/routing preference but never security admissibility, resource exemption or private Kernel access.

### 8. Provider/software mechanics remain at the mechanism boundary

Provider request/response schemas, credentials/login/session handling, vendor CLIs, MCP, SDK details, local gateways, model loading and backend/device/cache behavior belong to concrete adapters or external software, not generic Kernel work/security semantics.

Transport is not architecture: in-process APIs, IPC, HTTP, MCP and other adapters may realize the same logical boundaries.

### 9. Architecture must be minimal but concrete

MADRE should define the smallest coherent, modular and human-readable set of public concepts/classes required by its real boundaries and SDK.

Avoid speculative universal ontologies and framework ceremony. Equally, do not leave cross-boundary concepts undefined and then allow implementation tasks to fill the vacuum with conventional industry abstractions. Define what MADRE itself needs, no more and no less.

### 10. Greenfield development may remove superseded implementation structure

MADRE has no production compatibility obligation during current development. Superseded generated-state formats, classes, schemas, tests and package boundaries may be replaced rather than wrapped when they no longer realize this architecture. This does not make product/architecture decisions disposable.

## Responsibility test

Place a concept by asking what decision requires it:

- understand meaning, plan, remember, present, learn, choose semantic behavior, mutate a domain → **Module**;
- publish/discover a public semantic contract → **interoperability**;
- evaluate boundaries, execute transient inference, admit/schedule durable work, allocate scarce resources, select a physical mechanism, recover execution, route an explicit target, deliver results or record evidence → **Kernel**;
- load/talk to a model/provider or wrap concrete local/remote inference software → **inference mechanism / Capability adapter**.

When detailed behavior is required, use the focused architecture owner listed above rather than expanding this canonical summary into another implementation manual.
