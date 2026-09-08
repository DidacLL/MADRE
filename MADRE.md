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
    deterministic security, work, models/capabilities,
    scheduling/resources, recovery, brokering and evidence
        |
        v
Capability
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

Work carries only execution-relevant semantics: originator, capability/model requirements, eligibility, priority/budget/resource constraints, scheduling relationships, boundary/security references, retry/idempotency semantics and opaque correlation metadata.

Immediate and delayed reasoning use the same lifecycle.

For delayed work, durable MADRE state contains an opaque material reference, expected digest, immutable security envelope and execution metadata. The originator retains the actual prompt/context and must provide it again at execution time. MADRE verifies the supplied material before execution.

## Content persistence invariant

MADRE may durably persist:

```text
public registry descriptors
capability/model metadata
policy/configuration
WorkRecord / WorkAttempt lifecycle metadata
scheduling/resource evidence
material and output digests
security envelopes and decisions
failure/delivery evidence
opaque correlation values
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

Input may exist transiently only while required for execution. Output may exist transiently only while required for delivery. After the originator consumes a result, content is discarded while digest/delivery evidence remains.

Completion and delivery are separate facts. If MADRE restarts after output production but before consumption, it records that the transient result was lost rather than inventing stronger durability.

## Models and Capabilities

A **Capability** is a bounded physical computation/provider integration such as a local LLM runtime, remote model provider, embedding engine, speech model or image model.

A Module may request a specific model/capability or execution properties such as modality, optional local-only execution, context/resource constraints or output contract. Kernel deterministically selects a compatible execution path using the carried security algebra plus current resource/availability facts. It does not inspect prompt meaning to choose a reasoning strategy.

Provider-specific protocols, credentials and validation such as OpenAI-compatible chat contracts remain inside Capability adapters. They are implementation details of those adapters, not MADRE authorization or Kernel work semantics.

## Scheduling and resources

Kernel owns global execution control including:

```text
immediate/delayed eligibility
application fairness and priority
budgets/deadlines
GPU/CPU/memory admission
model residency/device coordination
concurrency
cancellation/retry
restart recovery
```

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

Security state belongs to the concrete request/work lifecycle. Originating facts are carried forward; material, Module/Agent/Operation boundaries, capability/model boundaries and derived representations contribute new immutable envelopes only when those crossings actually occur. Kernel evaluates the resulting accumulated context deterministically.

No registry entry, installation acceptance, bearer token, allowlist, role assignment, previous decision or identifier authorizes work. Registration means only that a descriptor exists and can be discovered/routed. A registry update cannot rewrite the security history already carried by accepted work.

The current minimal reference composition uses conservative normalized relations (highest sensitivity, lowest trust, highest risk) and admits only contexts whose effective trust can carry both effective sensitivity and risk. Future relations must remain algebra over carried boundary facts rather than external grants.

Derived material receives a new digest/envelope/provenance chain. Kernel does not perform semantic redaction of private domain content.

## Default Module and transports

An installation may designate one registered Module as its default general-purpose Module. It may provide general Agents, Skills, Workflows, Operations, cross-domain coordination, developer tooling and optional UI. Default status does not bypass Kernel security.

MADRE may be used through native SDKs, in-process APIs, local IPC, HTTP, MCP or other adapters. Transport is not architecture. The current local single-owner runtime does not add a separate Module authentication/authorization framework; provider credentials remain provider-adapter concerns.

## Greenfield rule

MADRE has no production compatibility obligation. Superseded classes, schemas, persistence formats, tests and package boundaries may be deleted rather than wrapped. Preserve behavior only when it still belongs in this architecture.

## Durable summary

1. Modules own intelligence semantics and private state.
2. MADRE publishes public interoperability contracts without owning the private implementations behind them.
3. Kernel deterministically controls security, work, scheduling/resources, models/capabilities, recovery, brokering and evidence.
4. Capabilities perform bounded physical computation.
5. Private prompt/context/output content remains with its originator except for transient execution/delivery.
6. Delayed reasoning separates semantic planning from durable execution control.
