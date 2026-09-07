# MADRE

This file defines the proposed canonical product architecture for **MADRE — Model-Agnostic Delayed Reasoning Effort Agentic System** on the clean-slate architecture branch.

MADRE is a modular, privacy-aware and user-controlled agentic environment. It allows useful intelligence work to continue immediately, later, or over long-lived plans while keeping domain ownership, agent reasoning, security enforcement, orchestration, physical execution and provider-specific computation separate.

The primary ownership invariant is:

```text
Modules own domains and exposed functionality.
Agents perform semantic/intelligence work from bounded inputs.
MADRE Kernel owns trusted coordination, security and orchestration.
MADRE Runtime owns durable physical execution.
Capabilities perform bounded computation.
```

MADRE is local-first. Local/private execution is a first-class high-trust and offline-capable tier, but MADRE may also use authorized external capabilities when the governing security and transfer constraints allow it.

## 1. System model

The logical architecture is:

```text
User / external system
        │
        ▼
      Module
        │ bounded task, context, Skills and Operations
        ▼
   MADRE Kernel
        │ Agent resolution / orchestration / security
        ▼
   AgentInstance
        │
        ├── Skills
        ├── Workflows
        ├── ContextBundles
        └── permitted Module Operations
        │
        ▼
     WorkPlan
        │
        ▼
     AgentTasks
        │
        ▼
   MADRE Runtime
        │
        ▼
    Capabilities
```

These are logical responsibilities, not mandatory process boundaries.

## 2. MADRE Module

A **Module** is MADRE's semantic and security integration boundary for one externally coherent domain or service.

A Module may wrap a MADRE-native component, desktop application, service, API, MCP server, local script, library, remote integration or compatibility adapter. MADRE does not require or inspect a particular internal architecture behind that boundary.

A Module owns the meaning and authority of its domain state. It decides what domain context may be exposed and what domain Operations are available.

A Module may expose, as applicable:

- identity, purpose and scope metadata;
- discovery and visibility policy;
- security properties;
- bounded context providers;
- zero or more Operations;
- zero or more Skills;
- zero or more AgentDefinitions;
- artifacts or local interaction projections.

A Module with no Agents is a first-class MADRE Module. Agentless integrations are expected to be common.

MADRE may adapt a non-MADRE-aware external system into a Module. For example, an MCP server or conventional API can be wrapped so that its callable functions become Module-owned Operations under MADRE's security boundary.

## 3. CORE Module role

**CORE is a replaceable Module role, not a different architectural species.**

MADRE ships a standard default Module intended to occupy the CORE role. Another compatible Module may replace it.

The CORE role provides default system-level responsibilities such as:

- unified/default interaction;
- generic system reasoning;
- cross-domain coordination;
- semantic routing over visible Modules;
- fallback AgentDefinitions;
- fallback planning/orchestration behavior;
- adaptation when an integrated Module requests agentic behavior but does not manage its own Agents.

CORE privileges are assigned through Kernel policy. They do not imply direct access to another Module's storage, unrestricted data visibility, or an alternate execution path.

CORE may receive high-sensitivity local context when explicitly permitted and should therefore be strongly isolated. Cross-domain access still occurs through bounded Module interfaces and Kernel-enforced information-flow rules.

## 4. Module Operations

An **Operation** is the canonical callable functionality exposed by a Module.

Operations are Module-owned. MADRE has no separate architectural `Tool` entity.

An Operation describes at least the information required for an Agent and Kernel to use it safely:

- identity and owning Module;
- purpose;
- input contract;
- output contract;
- side-effect semantics;
- repeatability or idempotency information when relevant;
- security requirements;
- transfer/egress characteristics when relevant.

The implementation is opaque to the Agent. An Operation may internally use deterministic code, HTTP, MCP, RPC, a database service, a remote API, a native executable or another mechanism.

External ecosystems are adapted into this contract:

```text
external function / MCP tool / API action
                │
                ▼
           Module adapter
                │
                ▼
             Operation
```

An Operation does not require a Workflow wrapper. Simple bounded functionality may be called directly by an Agent.

## 5. Skills

A **Skill** is a reusable ability package that may be attached to an AgentDefinition or AgentInstance.

A Skill is broader than one prompt, one Operation or one Workflow. It may contain:

- semantic purpose and descriptions;
- instructions or harness fragments;
- references to relevant Module Operations;
- context expectations;
- expected outputs or artifact conventions;
- zero or more WorkflowDefinitions;
- provenance and revision information.

A Skill may contain no Workflows at all.

Skills provide a compatibility surface for reusable agent abilities from MADRE Modules, user configuration and external skill/harness ecosystems. Importing or attaching a Skill does not imply autonomous learning.

## 6. Workflows

A **WorkflowDefinition** is a first-class reusable explicit recipe for accomplishing a known task or subtask.

WorkflowDefinitions may originate from:

- a SkillDefinition; or
- direct AgentDefinition/user configuration.

Therefore a Workflow is not required to belong exclusively to either an Agent or a Skill.

An Agent's effective Workflow repertoire is the union of:

```text
direct Agent Workflows
        +
Workflows contributed by attached Skills
```

This permits portable Skill-provided Workflows while also supporting Agent-specific or user-created Workflows without manufacturing an artificial Skill container.

Workflow and Skill revisions must be explicit enough that an existing Agent configuration is not silently changed by a later Module update. Customized definitions may derive from an upstream definition while preserving provenance.

`Routine` is not a MADRE architecture concept.

## 7. AgentDefinition and AgentInstance

An **AgentDefinition** is reusable Agent configuration.

It may define or reference:

- identity;
- the Module responsible for managing it;
- base harness, instructions or personality configuration;
- attached Skills;
- direct Workflows;
- permitted Operation scope;
- security constraints;
- state/memory policy.

An AgentDefinition is not one active reasoning process and may be instantiated concurrently.

An **AgentInstance** is the actual working reasoning actor for a task. It has bounded task context, working state and the effective Skills, Workflows and Operations available for that work.

A responsible Module manages an AgentDefinition, but the Agent does not implicitly gain knowledge of or unrestricted access to that Module's domain. It receives only explicitly supplied ContextBundles and permitted Operations.

The architecture intentionally allows future Agent persistence policies without requiring different Agent subclasses. An instance may eventually be ephemeral, derived from a persistent named configuration, or participate in longer-lived persona/state semantics. The initial architecture requires only AgentDefinition and AgentInstance.

## 8. Agent fallback and resolution

A Module is not required to provide Agents.

When semantic/intelligence work is required and the originating Module does not supply a suitable AgentDefinition, MADRE may use a fallback AgentDefinition supplied by the CORE Module.

For example:

```text
Agentless Module
    + Module Skill
    + permitted Module Operations
    + bounded Module context
    + CORE fallback AgentDefinition
        ↓
    AgentInstance
```

This keeps one agentic execution model instead of creating separate agentic and non-agentic orchestration systems.

A task may request an Agent by required behavior or properties rather than by hard-coded implementation identity. The Kernel resolves the most appropriate visible and permitted AgentDefinition, falling back to CORE defaults where appropriate.

All semantic/intelligence work is mediated through AgentInstances. Deterministic Kernel, Runtime and Module housekeeping remains ordinary software and does not require fake Agents.

## 9. Multi-Agent behavior

MADRE does not require a separate multi-Agent workflow ontology.

When an Agent needs another Agent, it delegates a child AgentTask through the Kernel. The Kernel resolves an appropriate AgentDefinition and creates or binds an AgentInstance for that task.

The delegation remains part of the WorkPlan and execution trace.

This provides multi-Agent behavior without prematurely introducing Agent-team, coordinator, supervisor or multi-Agent-workflow class hierarchies.

## 10. WorkPlan

A **WorkPlan** is durable MADRE orchestration state for one objective.

A WorkPlan is not a Runtime queue, not a WorkSubmission, and not domain persistence owned by one participating Module.

It preserves the semantic continuity required to continue an objective across foreground interaction, delayed execution, process interruption and restart.

A WorkPlan records at least the concepts required to understand and resume the objective, including as needed:

- plan identity;
- objective;
- originator/provenance;
- orchestrating Agent reference;
- AgentTasks;
- produced outputs/artifact references;
- creation/update/retention information;
- derivation/replay provenance.

The originating Module remains authoritative for its domain and can be consulted later for additional bounded context. MADRE owns the durable orchestration representation required to continue the plan.

WorkPlans are retained for a configurable period after completion so that users and developers can inspect, clone, modify and replay agentic behavior. Replay should preserve provenance rather than overwrite prior evidence.

Dynamic mutation/versioning of active plans may be introduced later when concrete replanning behavior requires it.

## 11. AgentTask

An **AgentTask** is one objective-specific assignment inside a WorkPlan.

It may describe or reference:

- task identity and instruction/objective;
- required or resolved AgentDefinition;
- AgentInstance correlation;
- attached Skill references;
- selected Workflow reference when applicable;
- bounded ContextBundle references;
- prerequisites;
- eligibility/scheduling requirements;
- expected outputs/artifacts;
- semantic execution/result evidence.

One AgentTask may cause zero, one or many Module Operation invocations and zero, one or many Runtime WorkRecords. It may also delegate child AgentTasks.

Therefore there is no architectural 1:1 relation between AgentTask, Workflow and Runtime WorkRecord.

## 12. Readiness, gates and failures

MADRE does not encode generic AI-framework concepts such as `blocked` or `waiting_for_human` as universal WorkPlan lifecycle states.

Whether an AgentTask can execute is derived from current facts, for example:

```text
required predecessor results exist
AND scheduling eligibility is satisfied
AND an appropriate Agent can be resolved
AND required context may legally flow
AND required Operations are available and permitted
```

A user decision may be one prerequisite, but MADRE is not intrinsically human-in-the-loop. Autonomous continuation is permitted whenever semantics and policy allow it.

Failures are recorded as evidence. Retry decisions must respect the repeatability, side-effect and unknown-outcome semantics of the work involved. MADRE must not silently fabricate success or blindly repeat an operation whose external effect may be unknown.

## 13. Context and security

Agents never receive implicit Module access. They receive bounded **ContextBundles**.

A ContextBundle carries the task material together with the provenance and security metadata required to govern its use, including as needed:

- purpose;
- source/owning Module;
- security classification;
- permitted use or egress constraints;
- payload or references.

MADRE treats semantic payload as data with provenance and security properties. It does not attempt to define a universal distinction between user text, generated text, knowledge, memory, trusted facts or learning candidates. Modules decide the domain meaning assigned to data.

The Kernel enforces information-flow boundaries structurally. Model output cannot grant itself additional access.

A higher-sensitivity ContextBundle may flow only to destinations permitted by the active policy. Moving information into a lower-trust boundary requires an explicit authorized minimization, anonymization or other transformation that produces new derived data with provenance; MADRE must not silently strip data and declare it safe.

Module and Agent discovery metadata is itself subject to policy. The existence of a sensitive Module need not be visible to every user, Module or Agent on an installation.

The first implementation may use a deliberately small security model, but its schema must leave room for stronger policy and information-flow rules without changing the ownership boundaries above.

## 14. MADRE Kernel

The **MADRE Kernel** is the trusted deterministic control plane around Modules and Agents.

Its responsibilities include, as concrete behavior requires:

- Module registration and adaptation;
- CORE role assignment;
- policy-filtered Module/Agent/Skill discovery;
- AgentDefinition resolution;
- AgentInstance creation;
- ContextBundle brokering;
- security and information-flow enforcement;
- Operation invocation gateway;
- WorkPlan persistence and orchestration services;
- AgentTask delegation/routing;
- correlation between semantic orchestration and Runtime evidence.

The Kernel does not itself reason. When semantic judgement is required it uses an AgentInstance.

## 15. MADRE Runtime

The **MADRE Runtime** is MADRE's durable physical execution subsystem.

Its responsibilities include:

- accepting and identifying WorkSubmissions;
- immediate and delayed eligibility;
- durable WorkRecords and attempts;
- scheduling and resource admission;
- capability invocation;
- cancellation and retry semantics;
- interruption/restart recovery;
- execution-boundary enforcement required at capability use;
- execution evidence and inspection.

Runtime remains deliberately unaware of Module domain semantics, Agent working state, Skills, Workflows, WorkPlan objectives and AgentTask dependencies.

The existing Runtime service may remain an implementation subsystem beneath the wider Kernel architecture.

## 16. Capabilities

A **Capability** is a replaceable implementation that performs bounded computation for MADRE Runtime.

Examples include local or remote inference engines and other specialized computation providers.

Capabilities do not own Module semantics, Agent behavior or WorkPlan meaning. Provider-specific mechanics remain at the Capability boundary.

Module Operations and Runtime Capabilities answer different questions:

```text
Operation: what bounded functionality does this Module expose?
Capability: what bounded computation can Runtime execute?
```

Not every Module Operation must become a Runtime WorkSubmission. Fast deterministic Operations may execute through their Module boundary directly while still passing Kernel security and trace controls. Operations that require durable or capability-managed computation may materialize ordinary Runtime work as needed.

## 17. Persistence ownership

State ownership follows purpose:

| Purpose | Owner |
| --- | --- |
| Represent, interpret, remember or mutate a domain | Module |
| Manage a Module-provided AgentDefinition or future Agent-specific learning semantics | Responsible Module |
| Preserve active AgentInstance working state required by current orchestration | MADRE orchestration / responsible Agent management boundary |
| Preserve one objective's durable orchestration and replay history | MADRE Kernel / WorkPlan store |
| Schedule, execute, recover and inspect physical work | MADRE Runtime |
| Implement provider-specific computation/cache | Capability |

Physical storage location does not change semantic ownership.

## 18. Transparency and customization

MADRE serves both non-technical users and agentic-system experimentation.

The default UX should present the simplest useful interaction while the underlying agentic loop remains inspectable subject to security policy.

Users and developers should ultimately be able to inspect or customize, as appropriate:

- selected Modules;
- AgentDefinitions and AgentInstances;
- attached Skills;
- selected or user-created Workflows;
- bounded ContextBundles;
- WorkPlans and AgentTasks;
- Operation invocations;
- Runtime WorkRecords;
- outputs/artifacts;
- security decisions and execution evidence.

UI presentation may progressively disclose complexity; it must not create a hidden second architecture.

## 19. Delayed Reasoning Effort

Delayed Reasoning Effort means useful semantic work can be controlled in time without losing its orchestration meaning.

Runtime durability preserves physical work. WorkPlan durability preserves why that work exists and what should happen next.

A foreground interaction may therefore respond immediately, schedule later reasoning, continue other work, and eventually resume or expose the resulting plan evidence without requiring one blocking chat session.

DRE does not imply a universal `fast → deeper` progression. Later or parallel AgentTasks may verify, critique, research, plan, synthesize, invoke Operations, delegate another Agent, wait for eligibility or perform another useful task.

## 20. Product requirements

### R1 — Modular domain ownership

Integrating a system with MADRE does not transfer its domain model, persistence or mutation authority into Kernel, Runtime or CORE. The Module boundary exposes only the context, Operations, Skills and Agents intentionally made available.

### R2 — Replaceable CORE

MADRE supplies a standard CORE Module, but CORE is a role with an explicit contract and policy, not a permanently privileged implementation.

### R3 — Uniform agentic execution

Semantic/intelligence tasks are performed through AgentInstances. Agentless Modules can rely on CORE fallback Agents instead of implementing a parallel orchestration mechanism.

### R4 — Portable abilities

Skills and WorkflowDefinitions can be reused, pinned, derived and customized without requiring their provider Module to own every Agent that uses them.

### R5 — Durable orchestration

WorkPlans can survive long-running execution and preserve enough semantic continuity to resume objectives across interruption and restart.

### R6 — Durable physical execution

Runtime work has truthful lifecycle, recovery, retry, cancellation and evidence semantics independent of WorkPlan semantics.

### R7 — No semantic/runtime collapse

AgentTask, Workflow, WorkSubmission and WorkRecord remain distinct concepts. No 1:1 relationship is assumed between semantic planning and physical execution.

### R8 — Structural security

Context, Module discovery and Operation use are constrained by deterministic Kernel policy. Model-generated content cannot elevate its own authority.

### R9 — Replaceable computation

Capabilities may evolve independently of Modules, Agents and WorkPlans. Local/private execution remains a first-class trust tier and authorized external computation may coexist with it.

### R10 — Inspectable agentic loop

Users and developers can inspect and, where permitted, customize the semantic structures that determine agentic behavior rather than relying on hidden orchestration machinery.

### R11 — Evolvable implementation

Concrete technologies and internal schemas may evolve from evidence. New architectural elements should be introduced only when observed behavior becomes simpler, safer, clearer, more reliable or materially more capable because of them.

## 21. Product acceptance path

MADRE's architecture should be validated through progressively stronger end-to-end behavior:

1. **Current Runtime foundation:** real immediate/delayed durable work, restart recovery, scarce-resource admission, retry/cancellation and execution evidence remain usable.
2. **Module boundary:** one external or local system is represented as a Module with bounded Operations and context without leaking its internal model into MADRE.
3. **Agent environment:** Kernel can instantiate one AgentDefinition as an AgentInstance with bounded Context, Skills, Workflows and permitted Module Operations.
4. **Agentless Module fallback:** a Module with no Agents can still participate in agentic work through a CORE fallback Agent.
5. **Durable WorkPlan:** one objective produces a persistent WorkPlan whose AgentTasks can continue across foreground interaction and process restart while materializing ordinary Runtime work as needed.
6. **Skill portability:** one Module-provided Skill can be attached to a different AgentDefinition without transferring Module authority or silently changing the pinned configuration.
7. **Cross-domain security:** CORE coordinates bounded material from more than one Module while Kernel policy prevents unauthorized reverse access or unsafe transfer.
8. **Multi-Agent delegation:** one AgentTask can delegate another AgentTask through normal Kernel resolution without introducing a separate multi-Agent execution architecture.
9. **Transparency/replay:** a completed WorkPlan can be inspected, cloned and replayed with modifications while retaining provenance.

These are behavioral acceptance goals, not a mandate to implement speculative subclasses or frameworks before the relevant behavior exists.

## 22. Explicit non-goals for the first agentic architecture

The initial agentic environment does **not** require:

- autonomous learning or behavior promotion;
- a universal knowledge/truth ontology;
- persistent persona memory semantics;
- Agent subclass taxonomies;
- `Routine`;
- a separate `Tool` hierarchy;
- Agent teams or multi-Agent workflow classes;
- dynamic active-plan graph revision/versioning;
- Agent similarity/substitution machinery;
- universal human-in-the-loop states;
- Runtime awareness of WorkPlan/Agent/Skill/Workflow semantics.

These may be introduced later only if concrete behavior earns them.
