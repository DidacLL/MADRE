# MADRE

**MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** is a local-first platform that connects AI-capable applications to shared intelligence resources.

Applications and their Modules decide what intelligence work is useful. MADRE provides deterministic infrastructure for security evaluation, durable work, scheduling, resource arbitration, model/capability execution, recovery, result delivery and execution evidence. MADRE also provides a public interoperability registry through which independently developed Modules may publish discoverable Agents, Skills, Workflows and Operations.

The architecture is governed by this file and the focused documents under `docs/architecture/`:

- `MADRE-platform-architecture.md` — topology and responsibility ownership;
- `MADRE-execution-contract.md` — work, material, capabilities, scheduling, results and recovery;
- `MADRE-agent-interoperability.md` — Module manifests, public descriptors, discovery and brokering;
- `MADRE-security-algebra.md` — immutable envelopes, provenance and deterministic boundary evaluation.

When another active document conflicts with these authorities, these documents win.

## Responsibility boundary

```text
Module
    semantic/domain ownership and private agentic implementation
        |
        | public descriptors / explicit executable work
        v
MADRE interoperability
    Module/Agent/Skill/Workflow/Operation contracts
    registry and boundary-filtered discovery
        |
        | explicit destination / execution projection
        v
Kernel
    deterministic security, work, inference mechanisms,
    scheduling/resources, recovery, brokering and evidence
        |
        v
Capability adapter
    bounded physical computation/provider integration
```

If a Kernel decision requires understanding prompt or domain meaning, that responsibility is misplaced.

If application-private prompts, context, model answers, history, Agent state or WorkPlans are durably persisted by MADRE, that responsibility is misplaced.

If a public descriptor is confused with ownership of the implementation behind it, that responsibility is misplaced.

## Module ownership

A **Module** is an independently owned application or integration boundary. It owns its domain and, as appropriate:

```text
domain records and persistence
UI / interaction state
conversation history and private context
knowledge / retrieval
Agents and Agent state
Skills
Workflows
WorkPlans
artifacts
Operations and domain mutations
domain-specific privacy/routing semantics
learning and adaptation
```

A Module may contain no Agents. It may simply publish useful Operations or data projections.

## Public interoperability

An installed Module may publish a **Module Manifest** containing public descriptors for intentionally exposed Agents, Skills, Workflows and Operations. These contracts make independent Modules and developer tooling interoperable without exposing private implementation state.

Kernel stores and filters public descriptors. Registration means only that MADRE knows the descriptor and its owning Module. Kernel does not semantically certify competence or select an Agent/Operation because prompt contents appear relevant.

The requester discovers boundary-visible descriptors, chooses semantically, and explicitly requests invocation. Kernel then performs deterministic boundary evaluation and routing.

## Agents, Skills, Workflows and WorkPlans

An **Agent** is a Module-owned intelligent actor. Its prompt, memory, state, internal tool use, Skill selection, Workflow selection and reasoning architecture remain private to its Module.

A **Skill** is reusable Agent behavior or knowledge packaged for adoption by compatible Agents. MADRE standardizes its transferable public contract; the receiving Module decides how adoption changes its Agent.

A **Workflow** is reusable semantic behavior owned and executed by the adopting Agent/Module. MADRE does not require one universal Workflow execution language.

A **WorkPlan** is semantic planning state owned by the Module/Agent that created it. MADRE never persists or interprets the semantic WorkPlan. The Module projects executable pieces into ordinary MADRE work and may provide execution-only dependency/group metadata.

## Interactive dual-lane behavior

MADRE is not fundamentally a blocking chatbot or an oracle that must synchronously answer every user request.

For interactive Modules, user input is expected to enter a low-latency interaction path and fork into two logical lanes:

```text
user input
    |
    +--> fast transient inference
    |       minimal interaction material
    |       short natural user-facing response
    |       no durable-work/recovery promise
    |
    +--> non-blocking reasoning path
            may answer without deeper work
            or schedule further durable reasoning/work
            or delegate/escalate through Module-owned intelligence
```

The fast lane is itself MADRE inference, not a fixed set of canned acknowledgements. On constrained hardware it may legitimately respond with a short natural acknowledgement such as an intent to think about the request while the second lane progresses. If the reasoning path later concludes that substantive work is needed, the Module can inform the user naturally and deliver the result/artifact when ready. If no deeper work is needed, the reasoning path can simply provide the substantive response.

The purpose is interaction continuity: long inference must not unnecessarily block the rest of the Module or MADRE UX. Exact wording, semantic routing, escalation and user adaptation remain Module/Agent responsibilities; Kernel provides execution paths and resource control rather than interpreting user meaning.

A fast transient inference may carry minimal ephemeral input directly because it is neither queued nor recoverable. The durable-work material ownership rule below applies once execution becomes accepted/queued work.

## Durable execution primitive

MADRE's durable execution primitive is:

```text
WorkSubmission
      |
      v
WorkRecord
      |
      v
0..N WorkAttempts
```

Work carries only execution-relevant semantics: originator, inference requirements/preferences, eligibility, priority/budget/resource constraints, scheduling relationships, boundary/security references, retry/idempotency semantics and opaque correlation metadata.

Immediately eligible durable work and delayed durable work use the same lifecycle.

For **all durable work**, regardless of whether it is eligible now or later, MADRE never owns queued/accepted prompt, context or other private material. Durable state contains a verifiable opaque material handle: reference, expected digest, immutable security envelope and retrieval coordination data. The originator retains the actual material. Kernel acquires it just-in-time only after the work has been selected for real execution, verifies it, uses it transiently and discards it.

Restart and retry therefore recover execution intent rather than recovering private content: the originator is asked to supply the material again when execution is actually attempted.

## Content persistence invariant

MADRE may durably persist:

```text
public registry descriptors
inference-mechanism/model metadata
policy/configuration
WorkRecord / WorkAttempt lifecycle metadata
scheduling/resource evidence
material and output digests
security envelopes and decisions
failure/delivery evidence
opaque references/correlation/retrieval values
```

MADRE does **not** durably persist:

```text
prompts
conversation/private/retrieved context
private documents
Agent memory/state
WorkPlans
model answers or generated content
application history/domain records
```

Queued/accepted durable work contains no private material payload in memory or on disk. Input exists inside Kernel only transiently after just-in-time acquisition for actual execution. Output may exist transiently only while required for delivery. After the originator consumes a result, content is discarded while digest/delivery evidence remains.

Completion and delivery are separate facts. If MADRE restarts after output production but before consumption, it records that the transient result was lost rather than inventing stronger durability.

## Models, inference mechanisms and Capabilities

A **Capability** in the current code means an available physical inference/execution mechanism with known properties. It is not a semantic statement that a model is "capable" of a user task. Code and documentation should make this physical-execution meaning obvious; names are part of maintainability, not cosmetic decoration.

Examples include a local llama.cpp model, a local vision model, an OpenAI API connection, a provider CLI authenticated by the user's account, an MCP-backed bridge, an embedding engine, a speech model, an image model or another user-installed software adapter.

A provider is not one Capability. One provider may expose many independently usable mechanisms with different authentication, cost, latency, modality, model inventory and operational properties. For example, Anthropic access might come from an API adapter, a vendor CLI/session, an MCP path, or another user-installed adapter over software the user controls. MADRE must not flatten that ecosystem into one provider-shaped integration or forbid unconventional local adapters merely because they are not the vendor's primary API path. Adapter owners/users remain responsible for the software and accounts they connect.

The OpenAI-compatible HTTP adapter is one useful reference mechanism, not the canonical shape all provider integrations should copy.

Modules normally do not need to know the installed mechanism inventory. They state execution requirements and preferences such as:

```text
modality / specialization
latency class (interactive / normal / background)
reasoning effort / quality target
cost policy (forbid paid / prefer free / paid allowed)
locality or privacy constraints
resource/availability requirements
preferred provider or model, with explicit fallback semantics
```

Kernel matches those requests deterministically against currently available inference mechanisms and resource state. A user preference such as "use Claude" can be represented as a provider/model preference rather than necessarily a hard identity requirement; if fallback is allowed and the preferred mechanism is unavailable, Kernel may select the closest admissible alternative. Paid execution must remain visible to the application/UI rather than being silently hidden inside conversational text.

Provider-specific protocols, credentials, login/session mechanisms and validation remain inside Capability adapters or the external provider software they invoke. Kernel must not assume API keys are the universal connection method and must not become a credential-management architecture merely because one adapter uses an API key.

## Scheduling and resources

Kernel owns global execution control including:

```text
interactive latency-sensitive execution
immediate/delayed durable eligibility
application fairness and priority
budgets/deadlines
GPU/CPU/memory admission
model residency/device coordination
concurrency
cancellation/retry
restart recovery
```

MADRE targets ordinary personal machines where inference resources may be severely constrained, including low-VRAM systems with OS/application memory pressure. Interactive execution therefore needs latency-sensitive treatment without allowing long background work to make the Module UX feel blocked.

A Module may submit execution relationships such as dependency/group/budget metadata. Kernel interprets only their execution effect, not why the semantic plan contains them.

## Operations and explicit Agent invocation

An **Operation** is callable behavior intentionally exported and implemented by a Module. The public descriptor belongs in MADRE interoperability; execution ownership remains with the Module.

Likewise, a public Agent descriptor allows explicit invocation without moving Agent implementation into Kernel. Request material is evaluated before dispatch, and returned material is a new governed crossing back to the requester; successful dispatch is not blanket permission for whatever bytes the target returns.

The cooperation rule is:

```text
Module-owned intelligence decides what is useful.
MADRE discovers, evaluates, routes, executes/transfers and records evidence.
```

## Security

MADRE uses immutable, traceable **Security Envelopes** composed through a carried **SecurityContext**.

Ordinary values use normalized levels 1..5; `0` is system-reserved. The initial independent numeric dimensions are sensitivity, trust and risk. They are never arithmetically added, averaged or collapsed into one generic score. Scope/domain facts remain independent.

Security state belongs to the concrete request/work lifecycle. Originating facts are carried forward; material, Module/Agent/Operation boundaries, inference-mechanism/model boundaries and derived representations contribute new immutable envelopes only when those crossings actually occur. Kernel evaluates the resulting accumulated context deterministically.

No registry entry, installation acceptance, bearer token, allowlist, role assignment, previous decision or identifier authorizes work. Registration means only that a descriptor exists and can be discovered/routed. A registry update cannot rewrite the security history already carried by accepted work.

The current minimal reference composition uses conservative normalized relations (highest sensitivity, lowest trust, highest risk) and admits only contexts whose effective trust can carry both effective sensitivity and risk. Future relations must remain algebra over carried boundary facts rather than external grants.

Current Kernel `trust` is a boundary/provenance property. It is **not** semantic truth scoring, prompt-injection detection, hallucination detection, model-answer reliability or generic AI-content safety. Such deterministic signals may be added in the future only when MADRE has a concrete model for them; Kernel does not inspect prompt meaning today.

Derived material receives a new digest/envelope/provenance chain. Kernel does not perform semantic redaction of private domain content.

## Default Module, SDK and transports

An installation may designate one registered Module as its default general-purpose Module. It may provide general Agents, Skills, Workflows, Operations, cross-domain coordination, developer tooling and optional UI. Default status does not bypass Kernel security.

MADRE's public Module-facing contracts should be usable through a modular SDK without exposing Kernel persistence, scheduler or provider internals. The default CORE Module should consume those same public contracts rather than becoming a privileged second Kernel.

MADRE may be used through native SDKs, in-process APIs, local IPC, HTTP, MCP or other adapters. Transport is not architecture. The current local single-owner runtime does not add a separate Module authentication/authorization framework; provider credentials remain provider-adapter/external-software concerns.

## Greenfield rule

MADRE has no production compatibility obligation. Superseded classes, schemas, persistence formats, tests and package boundaries may be deleted rather than wrapped. Preserve behavior only when it still belongs in this architecture.

## Durable summary

1. Modules own intelligence semantics and private state.
2. MADRE publishes public interoperability contracts without owning the private implementations behind them.
3. Kernel deterministically controls security, work, scheduling/resources, inference-mechanism selection, recovery, brokering and evidence.
4. Capability adapters expose bounded physical computation mechanisms; providers may contribute many different mechanisms.
5. Fast transient inference supports natural low-latency interaction without creating durable work.
6. Durable work never gives Kernel ownership of queued private material; content is acquired just-in-time from its Module.
7. Private prompt/context/output content remains with its originator except for transient execution/delivery.
8. Delayed reasoning separates semantic planning from durable execution control.
