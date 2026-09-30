# Public SDK: accepted semantic base

This is a development base for ordinary, independently built MADRE Modules. It records accepted semantic architecture without claiming that open Java call contracts are implemented. Product meaning comes first from the [Owner Intent Corpus](../docs/product/owner-intent-corpus.md), then the [North Star](../NORTH_STAR.md), [MADRE overview](../MADRE.md), [SPIRA architecture](../docs/architecture/security-algebra.md), and [mid-level architecture](../docs/architecture/mid-level-architecture.md). This file does not add authority over them.

MADRE supplies a public construction surface, an installed Runtime, and a physical Kernel boundary. Modules own application/domain meaning, types, state, persistence, UI, integrations, optional Agents, and internal behavior. CORE is a role assigned to an ordinary Module, not a special SDK subtype. A Module may be agentless, UI-less, or a binding around existing software. Not every useful facility is a Module. An Operation can be deterministic; neither it nor an Agent is synonymous with an inference call.

| Boundary | Accepted responsibility |
| --- | --- |
| Module and its Agents | Domain meaning, Material, strategies, state, persistence, UI, integrations, semantic continuation and result interpretation. |
| Runtime | Installation/configuration, live Module discovery and lifecycle, addressing, CORE-role assignment, Operation routing, Agent-delegation transport, selected required-function execution, semantic/physical correlation, and shared diagnostics. It does not acquire Module meaning or become a SPIRA evaluator. |
| Kernel client and native Kernel | The client transports the physical contract; Kernel owns already-physical inference Work, engine facts, durable technical scheduling/execution, resources and technical results. Neither receives Module, Agent, Material, ReasoningRequest or SPIRA semantics. |

## Accepted concepts and ownership

| Concept | Settled meaning and relationship | Current Java base |
| --- | --- | --- |
| MADREModule | Independently installable application/domain boundary. May provide Agents and Skills and expose Operations; owns its internals. | Public entry with installation-local id and optional collections. |
| MADREAgent | Semantic actor supplied by a Module. May use Skills and Workflows, invoke Operations, create ReasoningRequests, and explicitly delegate to another Agent. Contributes Integrity when actually participating. | Actor type and direct Integrity fact. No execution loop or permanent Autonomy field. |
| ModuleOperation | One bounded action. Its provider may have no Agent. The selected Operation/effect contributes Risk; accepted Material boundaries offer Privacy; produced Material may promise a maximum Sensitivity. | Action type and direct Risk fact. Input/output and invocation shapes remain open. |
| Material | Actual information/context representation contributing Sensitivity. A minimised or transformed result is new Material; its source is not relabelled. Domain types retain domain meaning. | Direct Sensitivity fact; no universal content schema. |
| Skill | Reusable know-how available to an Agent, not automatically another Module. | Named type; behavior shape open. |
| Workflow | Reusable Agent behavior, not a Runtime queue or Kernel schedule. | Named type; behavior shape open. |
| WorkPlan | Objective-specific semantic planning; may coordinate Agents and Workflows. | Named type; coordination shape open. |
| ReasoningRequest | Agent-created semantic need for reasoning. May involve relevant context, Material, provenance, Owner instruction, SPIRA facts, and objective. | Named type; no mandatory five-facet payload or Kernel dependency. |

~~~mermaid
classDiagram
    MADREModule "1" o-- "0..*" MADREAgent : provides
    MADREModule "1" o-- "0..*" ModuleOperation : exposes
    MADREModule "1" o-- "0..*" Skill : may provide
    MADREAgent ..> Skill : may use
    MADREAgent ..> Workflow : may use
    MADREAgent ..> ModuleOperation : invokes
    MADREAgent ..> ReasoningRequest : creates
    MADREAgent ..> MADREAgent : explicitly delegates
    WorkPlan ..> MADREAgent : may coordinate
    WorkPlan ..> Workflow : may compose
    ModuleOperation ..> Material : accepts or produces
~~~

The diagram states semantic relationships. It does not require a permanent object reference or Java method for every arrow. An Agent provided by one Module can act on another Module's domain context; the providing Module does not thereby own that domain.

## Operation invocation and Agent delegation

An Operation execution always occurs in an acting Agent's semantic continuation. An Agent may invoke an Operation from its own Module, an agentless Module, another Module with Agents, or the SDK. Cross-Module invocation does not switch the actor:

~~~mermaid
sequenceDiagram
    participant A as Agent A
    participant R as Runtime
    participant B as Module B
    participant O as Operation B
    A->>R: invoke selected Operation with relevant Material
    R->>B: locate and deliver call
    B->>O: execute bounded action
    O-->>B: result
    B-->>R: result
    R-->>A: result
    Note right of A: A retains its semantic continuation
~~~

Explicit Agent delegation is a different semantic act. The target Module receives selected context and known facts, and its Agent continues as the receiving actor. Runtime transports both paths but does not become an Agent or the owner of Module meaning.

~~~mermaid
sequenceDiagram
    participant A as Agent A
    participant R as Runtime
    participant B as Module B
    participant C as Agent B
    A->>R: explicitly delegate sub-objective and context
    R->>B: locate and deliver delegated work
    B->>C: begin receiving Agent continuation
    C-->>B: delegated result
    B-->>R: delegated result
    R-->>A: delegated result
~~~

The receiving Module may use its domain facts and bounded strategies to re-evaluate the actual representation. It does not discard source knowledge or silently relabel source Material.

## SPIRA belongs to actual constituents

The five enums in sdk.spira follow the established 0–5 order. SYSTEM_RESERVED is not an ordinary authored value. The facets have different carriers:

| Actual participating fact | Facet | Java base |
| --- | --- | --- |
| Material/context representation | Sensitivity | Material.sensitivity() |
| Actual receiving/exposure boundary, including an Operation's accepted Material boundary | Privacy | Enum exists; boundary Java shape open |
| Actual Agent or other provenance-bearing causal participant | Integrity | MADREAgent.integrity() for an Agent; other participant shapes open |
| Concrete selected Operation/effect | Risk | ModuleOperation.risk() for that bounded action |
| Current acting Agent continuation | Autonomy | Enum exists; continuation Java shape open |

The semantic carriers are settled; these three no-argument getters are current development choices, not Owner-frozen Java contracts. In particular, an exposed Operation that can select effects of different Risk cannot use one static value as a substitute for the Risk of the effect actually selected. The call design must preserve that distinction.

When actual participants contribute the same facet, Sensitivity accumulates by maximum, Privacy by minimum, and Integrity by minimum. Risk comes from the selected effect; Autonomy comes from the current continuation. Unused Operations, possible results, unselected destinations, unrelated history, and every installed capability do not participate.

Privacy.UNKNOWN is an ordinary value, not a missing declaration. Locality, provider identity, first-party origin or the CORE role does not automatically establish Privacy or Integrity. Owner interaction can change the current Autonomy without changing an Operation's Risk; it cannot make an unreliable high-consequence effect realizer reliable.

If an actual non-user causal Integrity reduction has no participants, rank 5 is its neutral top element; this does not invent a VALIDATED participant. A stronger participant cannot wash a weaker one that remains causally relevant.

The accepted relations apply only where the exact construction makes them relevant:

~~~text
max(S_actual_information) <= min(P_actual_receiving_boundaries)
min(R_actual_operation, A_current_agent_continuation)
    <= min(I_actual_non_user_causal_participants)
R_actual_operation <= min(I_actual_effect_realizers)
~~~

These are intrinsic composition relations. They are not a central authorization service, one global score, an Agent-owned mutable Compound, or permission against the Owner. An incompatible exact construction must change its actual constituents or stop; semantic software must not falsify a facet to force compatibility. Runtime transports facts and calls without owning this semantic evaluation.

## Reasoning, delayed continuation, and Kernel

An Agent creates a semantic ReasoningRequest when reasoning is needed. The responsible Agent/Module context establishes the relevant facts, valid current construction, objective, and acceptable inference choices. The request itself is not a mandatory S/P/I/R/A tuple.

The public SDK must ultimately expose a bounded required function/Operation:

~~~text
ReasoningRequest + execution preferences/declarations
    -> installation-selected, Module-owned reasoning executor
    -> physical Work requirements
    -> madre-kernel-client -> native Kernel
~~~

Runtime executes the configured Module-owned implementation and holds the installation selection. The architecture calls for a shipped default implementation supplied by an ordinary Module assigned the CORE role; the role itself has no behavior. The Owner can replace or wrap that implementation. The executor can inspect factual physical engine descriptions and resolve semantic choices above the boundary; only physical requirements enter Kernel. Neither Agent code nor ReasoningRequest constructs Kernel Work directly.

Delayed Reasoning Effort lets an Agent respond with what is known now while bounded research, verification or synthesis continues later. It separates persistence: the semantic side retains the request, context, origin/correlation, Module-owned continuation and interpretation. Kernel retains only physical Work identity, requirements, scheduling/execution state and terminal technical outcome. Runtime correlates the two and supports recovery; the Module/Agent interprets the result. Kernel remains ignorant of Module, Agent, Material, ReasoningRequest, SPIRA, CORE, Workflow, WorkPlan and semantic continuation. A Module may also use private intelligence that never passes through the shared Kernel.

## Owner sovereignty and independent construction

The public SDK must suffice for shipped, independent and eventually AI-generated Modules without Runtime internals or hidden first-party hooks. The Owner can inspect and alter installed Modules, Agent state, Runtime configuration and CORE assignment, consequential reasoning/external execution facts, and the physical Kernel they own. These are progressive options, not mandatory setup work for ordinary use. MADRE does not become an authority against its Owner.

## Java contracts still open

The accepted behavior above does not freeze these Java representations. They remain explicit design work rather than placeholder services or boilerplate:

- Operation input boundary per accepted Material type, receiving Privacy, produced Material maximum Sensitivity, and concrete selected Risk when effects vary;
- acting Agent continuation and current Autonomy, Operation invocation, result, and explicit Agent-to-Agent delegation;
- how a ReasoningRequest expresses objective and selected context without a mandatory facet tuple;
- execution preferences/declarations and the Module-owned reasoning executor's public signature;
- semantic request identity, durable Module-owned checkpoint, result delivery and replay-safe recovery;
- whether Skill, Workflow and WorkPlan need more public Java behavior than their accepted semantic roles.

No SDK type depends on madre-kernel-client. The current Runtime only installs, discovers and assigns the CORE role; it does not yet execute the journeys above. Those gaps are visible so Owner-led SDK work can design them without adapting to an invented framework.
