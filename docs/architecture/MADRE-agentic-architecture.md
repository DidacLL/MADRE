# MADRE modular agentic architecture

Status: **parallel clean-slate architecture proposal**

This document is intentionally isolated from the canonical architecture on `main`.
It must not be used to reinterpret or partially merge the discarded `ReasoningModule`
class hierarchy into the new design. Historical MADRE material may later be consulted
for concrete unresolved questions, but it is not an ontology source for this proposal.

The purpose of this branch is to establish a clean semantic model first. Only after the
Owner accepts it should the canonical `MADRE.md` and implementation be reconciled.

---

## 1. Architectural thesis

MADRE is a modular, local-first agentic environment in which semantic ownership, agent
reasoning, security enforcement, durable orchestration and physical computation remain
separate concerns.

The primary invariant is:

```text
Modules own domains and exposed functionality.
Agents reason over explicitly supplied context, Skills, Workflows and Operations.
MADRE Kernel controls coordination, security and orchestration.
MADRE Runtime controls durable physical execution.
Capabilities perform computation.
```

MADRE does not require every integrated application to implement Agents. It supplies an
agentic environment around modules that may expose only data, context, Operations and
Skills.

MADRE also does not require an external provider to understand MADRE natively. A
connector may adapt an application, API, MCP server, local script, service or other
bounded system into a MADRE Module.

Logical architecture:

```text
                              MADRE
                                │
                    ┌───────────┴───────────┐
                    │                       │
               MADRE Kernel            MADRE Modules
              trusted control          semantic domains
                    │                       │
                    │          ┌────────────┼────────────┐
                    │          │            │            │
                    │       CORE role     AAAAT       Other Module
                    │          │            │
                    │       Agents?      Agents?
                    │       Skills       Skills
                    │       Operations   Operations
                    │          │            │
                    └──────────┴──── agentic layer
                                │
                         AgentDefinitions
                                │
                          AgentInstances
                                │
                            WorkPlans
                                │
                            AgentTasks
                                │
                  ┌─────────────┴──────────────┐
                  │                            │
          Module Operations              Agent reasoning
                  │                            │
                  │                       Runtime work
                  │                            │
                  └──────────────┬─────────────┘
                                 │
                          MADRE Runtime
                                 │
                          WorkSubmission
                          WorkRecord
                                 │
                           Capabilities
```

These are logical responsibility boundaries. They do not imply one process or package
per box.

---

## 2. MADRE Module

The old name `ReasoningModule` is replaced by **MADRE Module**, normally shortened to
**Module**.

The word `reasoning` is deliberately removed: a Module does not inherently perform
inference. AgentInstances reason using MADRE capabilities.

A Module is MADRE's semantic integration boundary.

Its internal implementation may be anything:

```text
desktop application
service
library
MCP server
REST service
native process
Python application
collection of internal services
adapter over third-party software
```

MADRE does not impose internal structure. Facing MADRE, the provider exposes one
governed Module contract.

Conceptually:

```text
Module
 ├── ModuleManifest
 ├── SecurityProfile
 ├── discovery/scope metadata
 ├── bounded context interfaces
 ├── 0..N Operations
 ├── 0..N Skills
 ├── 0..N AgentDefinitions
 └── optional artifacts / projections / UX integration
```

A Module with zero Agents is fully valid and is expected to be common.

Examples:

```text
CalcModule
   └── calculate Operation
```

```text
AAAATModule
   ├── candidature Operations
   ├── CV Operations
   ├── private context providers
   ├── AAAAT Skills
   └── zero native Agents initially
```

```text
CursorModule
   ├── coding-related context and Operations
   ├── imported harness / Skills
   └── requests for suitable MADRE Agents
```

A non-MADRE-aware application can therefore still become a Module through an adapter.

---

## 3. CORE is a Module role

CORE is not another architectural species.

**CORE is a privileged role assigned to one compatible Module.**

```text
Module
   +
CORE role assignment
   =
CORE Module
```

MADRE ships a standard default Module capable of occupying this role. That standard
implementation is the default MADRE CORE Module, but it is replaceable.

Any compatible Module implementing the required contract may theoretically occupy CORE.
The role grants responsibilities and privileges; the concrete implementation does not
possess them inherently.

Expected CORE responsibilities include:

```text
unified/default user interaction
generic system reasoning
cross-domain coordination
fallback AgentDefinitions
fallback planner/orchestrator Agent
agentic adaptation for Agentless Modules
semantic routing over visible Modules
privacy-aware cross-domain context coordination
```

Role-specific privileges are assigned through Kernel policy. CORE is expected to have a
high local trust/clearance because it may coordinate sensitive cross-domain material.

But:

```text
CORE privilege != Module boundary bypass
CORE privilege != unrestricted Module storage access
CORE privilege != Runtime bypass
```

CORE obtains domain material through the same bounded context/Operation contracts used
by other authorized participants.

Because the CORE role may receive high-risk data, the default CORE Module should be
strongly locally isolated.

---

## 4. ModuleManifest

A Module exposes its MADRE-facing contract through a manifest-like semantic structure.
This need not be one literal file or one concrete class.

Conceptually:

```text
ModuleManifest
 ├── module_id
 ├── identity / description
 ├── scope descriptors
 ├── discovery policy
 ├── SecurityProfile
 ├── Operations[]
 ├── Skills[]
 ├── AgentDefinitions[]
 ├── context interfaces
 └── optional artifact / projection descriptors
```

The manifest answers:

```text
What is this Module responsible for?
May the current principal know it exists?
What Operations does it expose?
What Skills can it provide?
Does it manage Agents?
What bounded context can it provide?
Under which security constraints?
```

Module existence does not imply authority to inspect or invoke it.

---

## 5. Operations

`Operation` is the canonical callable MADRE abstraction.

There is **no separate MADRE Tool entity**.

An Operation is one bounded function owned by a Module.

Examples:

```text
CalcModule.calculate
AAAAT.get_candidature_context
AAAAT.create_candidature
AAAAT.render_cv
CalendarModule.list_events
CalendarModule.create_event
FilesystemModule.read_file
```

Conceptually:

```text
OperationDescriptor
 ├── operation_id
 ├── owning Module
 ├── purpose / description
 ├── input schema
 ├── output schema
 ├── side-effect semantics
 ├── repeatability / idempotency information
 ├── security requirements
 └── transfer / egress characteristics
```

The implementation is opaque to the Agent. It may hide:

```text
local deterministic code
HTTP
MCP
RPC
database-backed application logic
remote API
legacy application integration
native executable
```

External ecosystem terminology is adapted into Module + Operation:

```text
external "tool"
     ↓ adapter
MADRE Module
     ↓
Operation
```

For example an MCP calculator is not registered as a global Tool. A connector exposes a
`CalcModule`, whose methods become Operations. This gives the external functionality a
domain boundary, security policy, visibility policy and governed input/output contract.

An Operation does not require a Workflow. A one-shot deterministic calculator Operation
is useful by itself.

---

## 6. Skill

A Skill is neither an Operation nor a Workflow.

A **SkillDefinition** is a reusable ability package that can be attached to an
AgentDefinition or AgentInstance.

Conceptually:

```text
SkillDefinition
 ├── skill_id
 ├── revision
 ├── semantic purpose
 ├── descriptions / instructions
 ├── harness or prompt fragments
 ├── recommended/required Module Operations
 ├── context expectations
 ├── output/artifact conventions
 ├── 0..N WorkflowDefinitions
 └── provenance
```

A Skill may contain zero Workflows.

For example an AAAAT Skill may primarily contain domain vocabulary, privacy-sensitive
handling guidance, relevant AAAAT Operations and artifact conventions without forcing
one predefined process.

Another Skill may contain several proven reusable Workflows.

Therefore:

```text
Skill != Workflow container
```

although Skills are allowed to contribute Workflows.

Existing ecosystems such as Claude/OpenAI-style skills, harness packages, MCP
instructions or application-specific instruction bundles can be adapted into MADRE
Skills when their semantics fit.

"Learning a Skill" initially means importing or attaching a deliberate SkillDefinition.
It does not imply autonomous ML learning or self-modifying behavior.

---

## 7. Workflow

A Workflow is an explicit reusable recipe for accomplishing something.

Examples:

```text
evaluate-job-offer
prepare-interview
review-software-plan
draft-critique-synthesize
calendar-weekly-planning
```

`WorkflowDefinition` is a first-class entity. It does **not** have one exclusive parent.
It may originate from either:

```text
SkillDefinition
```

or directly from:

```text
AgentDefinition
```

Conceptually:

```text
WorkflowDefinition
 ├── workflow_id
 ├── revision
 ├── provenance
 ├── purpose
 ├── expected inputs
 ├── expected outputs / artifacts
 ├── explicit workflow structure/instructions
 └── referenced Operations / delegation behavior as needed
```

A Skill may therefore provide a Workflow, while an AgentDefinition may also carry a
user-created or customized Workflow directly.

The effective Agent repertoire is:

```text
direct Agent Workflows
        +
Workflows contributed by attached Skills
        =
effective Agent Workflow repertoire
```

This avoids forcing every custom Workflow into an artificial user Skill, while keeping
portable reusable workflows naturally bundled with Skills when appropriate.

`Routine` does not exist in the new architecture.

---

## 8. Skill and Workflow revision semantics

Portability must not mean silent mutation.

If an AgentDefinition uses:

```text
AAAAT Skill @ revision 3
```

and the Module later publishes revision 4, the existing Agent configuration should not
silently change.

AgentDefinition references therefore bind to explicit revisions.

Customization creates derived definitions rather than mutating upstream definitions
invisibly:

```text
AAAAT Skill @ 3
       │
       ▼
My AAAAT Skill @ 1
  derived_from = AAAAT Skill @ 3
```

Likewise:

```text
prepare-interview Workflow @ 2
       │
       ▼
my-private-interview Workflow @ 1
  derived_from = prepare-interview @ 2
```

This provides reproducibility, protects customized behavior against Module updates, and
leaves a future seam for experimentation or learning without implementing autonomous
promotion now.

---

## 9. AgentDefinition

The architectural Agent is primarily a reusable definition.

Conceptually:

```text
AgentDefinition
 ├── agent_id
 ├── responsible Module
 ├── base configuration
 ├── instructions / harness / personality
 ├── Skill references
 ├── direct Workflow references
 ├── permitted Operation scope
 ├── security constraints
 └── state/memory policy
```

The responsible Module manages the AgentDefinition.

This does **not** mean the Agent implicitly knows or owns that Module's domain.

An Agent receives explicit facilities:

```text
task
bounded ContextBundle
Skills
Workflows
permitted Operations
security envelope
working state
```

It does not receive arbitrary Module storage or hidden domain authority.

This enables combinations such as:

```text
CORE general Agent
    +
AAAAT Skill
    +
AAAAT Operations
```

without turning the Agent into an AAAAT domain object.

An Agent's responsible Module, Skill providers, Operation providers and underlying
Runtime Capability providers may all be different.

---

## 10. AgentInstance

Actual reasoning happens through an **AgentInstance**.

```text
AgentDefinition
      │
      ├── Instance 1
      ├── Instance 2
      └── Instance 3
```

Each instance can have independent:

```text
task context
working memory
reasoning state
current WorkPlan/AgentTask relationship
temporary Skills
temporary Workflow bindings
```

This makes concurrency natural: many planner jobs can instantiate the same planner
definition without sharing current reasoning state.

Future lifetime modes should initially be policy, not subclasses:

| Mode | Definition/configuration | Instance state |
| --- | --- | --- |
| Ephemeral | base configuration only | discarded after work |
| Named/variant | customized configuration persists | reset between works |
| Persistent/persona | customized configuration persists | selected state/memory may persist |

The initial implementation only needs AgentDefinition + AgentInstance. Rich persona
semantics remain future work.

---

## 11. Agent fallback

A Module is not required to manage Agents.

When semantic work must be performed for an Agentless Module, MADRE can instantiate a
default AgentDefinition supplied by CORE.

Example:

```text
AAAATModule
   ├── AAAAT Skill
   └── AAAAT Operations
```

may be used as:

```text
CORE default AgentDefinition
       +
AAAAT Skill
       +
bounded AAAAT context
       +
permitted AAAAT Operations
       ↓
ephemeral AgentInstance
```

No special `NoAgent` class is required. It is an ordinary AgentInstance with no
persistent personality or memory and only the bounded facilities required for the task.

If the Module later publishes its own AgentDefinitions, they can become preferred
without changing the rest of the orchestration architecture.

---

## 12. Agent resolution

A task may request Agent characteristics rather than hard-code one implementation.

Examples:

```text
planner
coding
research
calendar-private
AAAAT-capable
```

plus Skill/security requirements.

MADRE resolves these needs against Agents visible and authorized to the current
principal/context.

A Module does not need to know the global Agent population.

For example a Cursor adapter might express a need for a planner Agent. MADRE can resolve
that to a Module-provided planner or fall back to the standard CORE planner.

Discovery belongs to Kernel coordination, with security filtering.

---

## 13. Multi-Agent work

MADRE does not introduce a separate multi-Agent Workflow ontology.

If one Agent needs another Agent, the semantic mechanism is delegation through normal
orchestration:

```text
AgentInstance A
       │
       │ requests child AgentTask
       ▼
MADRE Kernel
       │
       │ resolves AgentDefinition
       ▼
AgentInstance B
       │
       ▼
result returns into parent orchestration
```

The delegation remains visible in the WorkPlan and trace.

This avoids speculative entities such as `AgentTeam`, `MultiAgentWorkflow`,
`CoordinatorGraph` or `AgentGroup`.

A Workflow may contain an explicit delegation instruction, but the resulting work is
represented using ordinary AgentTasks.

---

## 14. All semantic work uses Agents

For architectural consistency:

```text
all semantic/intelligence work
       ↓
AgentInstance
```

This does not mean deterministic Kernel/Runtime housekeeping becomes agentic. Scheduling,
persistence, policy evaluation and state transitions remain software responsibilities.

It means requests requiring interpretation, reasoning, planning or intelligent use of
Module functionality pass through one AgenticLoop.

Agentless Modules therefore do not require a second semantic workflow engine.

---

## 15. WorkPlan

A **WorkPlan** is MADRE's durable semantic orchestration record for one objective.

It is not:

```text
a Runtime queue
a Module database entity
an AgentDefinition
a synonym for WorkSubmission
```

It is the structure that allows an objective to continue across hours, days, process
restarts and foreground interactions.

Conceptually:

```text
WorkPlan
 ├── plan_id
 ├── objective
 ├── originator
 ├── orchestrator Agent reference
 ├── AgentTasks[]
 ├── outputs/artifacts
 ├── provenance
 ├── creation/update metadata
 └── retention metadata
```

The originator may be a user, CORE interaction, Module request or another AgentTask.

A Module remains authoritative for its domain. The WorkPlan stores orchestration meaning,
not a private copy of the Module's full state.

If additional domain information is required later, the orchestrator requests fresh
bounded context from the relevant Module.

---

## 16. AgentTask

The natural WorkPlan unit is **AgentTask**, not a runtime-like generic Step.

Conceptually:

```text
AgentTask
 ├── task_id
 ├── objective / instruction
 ├── requested or resolved AgentDefinition
 ├── AgentInstance correlation
 ├── attached Skill references
 ├── selected Workflow reference if any
 ├── bounded ContextBundle references
 ├── prerequisites
 ├── eligibility / schedule
 ├── expected output / artifact definition
 └── execution/result evidence
```

One AgentTask may involve:

```text
zero Runtime WorkRecords
one Runtime WorkRecord
many Runtime WorkRecords
several Module Operation invocations
child AgentTasks
```

Therefore:

```text
AgentTask != WorkRecord
Workflow != WorkSubmission
```

No `work_id` 1:1 invariant belongs in the semantic planning schema.

---

## 17. WorkPlan readiness and prerequisites

MADRE must not copy a generic AI-framework lifecycle such as:

```text
pending
running
blocked
waiting_for_human
waiting_for_agent
```

into WorkPlan semantics.

Readiness is derived from facts.

For example:

```text
AgentTask is executable when:

prerequisite outputs exist
AND scheduled eligibility has arrived
AND required Agent can be resolved
AND required context may legally flow
AND required Operations are available/permitted
```

If any condition is false, MADRE can explain why without converting that reason into a
canonical `blocked` lifecycle state.

A user decision is one possible prerequisite. MADRE is not architecturally
human-in-the-loop and does not assume that generated outputs require user acceptance.

Autonomous execution is allowed whenever semantics, repeatability and policy permit it.

Future custom prerequisite/gate types can be added without changing the base lifecycle.

---

## 18. WorkPlan durability, replay and experimentation

WorkPlans are durable by design.

This follows directly from MADRE's delayed reasoning purpose: local inference and other
agentic work may span substantial time and plans may last days.

Runtime durability alone is insufficient because Runtime does not know why separate jobs
belong to one semantic objective.

Completed WorkPlans should remain inspectable for a configurable retention period.

MADRE serves both:

```text
non-technical agentic environment
```

and:

```text
agentic research/development playground
```

Users/developers should therefore be able to inspect, clone, modify, replay and compare
plans.

Replay creates lineage rather than rewriting historical evidence:

```text
WorkPlan B
  derived_from = WorkPlan A
```

Full live plan-revision/versioning semantics are deferred until concrete behavior needs
them.

---

## 19. User-editable agentic behavior

MADRE should not hide semantic automation structures merely because a default UI wants
to remain simple.

An advanced view may expose:

```text
AgentDefinition
AgentInstance
Skills
Workflows
WorkPlan
AgentTasks
ContextBundles
Operations
Runtime evidence
security decisions
```

A future builder may allow users to:

```text
create or derive WorkflowDefinitions
clone/modify Skills
customize Agents
replace planner Agents
modify a cloned WorkPlan before replay
change allowed Operations
adjust privacy/security settings
```

The UI chooses presentation depth; it does not define a second hidden architecture.

---

## 20. ContextBundle

Agents never receive implicit Module access.

They receive bounded **ContextBundles**.

Conceptually:

```text
ContextBundle
 ├── purpose
 ├── provenance
 ├── owning/source Module
 ├── security classification
 ├── permitted use / egress metadata
 └── bounded payload
```

The Module selects or projects domain material before providing it.

The Kernel enforces whether a ContextBundle may reach a particular AgentInstance,
Operation or Runtime Capability boundary.

This supports combinations such as:

```text
generic CORE Agent
      +
AAAAT Skill
      +
AAAAT-projected ContextBundle
```

without granting arbitrary AAAAT database access.

---

## 21. Security algebra

Security must be structural, deterministic and independent of model obedience.

Relevant boundaries expose enough metadata for information-flow decisions.

A minimal conceptual model includes:

```text
ContextBundle
    security classification

Module
    trust/clearance properties
    discovery policy

AgentInstance
    effective clearance / authority

Operation
    input/output/egress constraints

Capability
    execution/transfer boundary
```

The key information-flow invariant is:

```text
sensitive information cannot move into a less-trusted destination
unless an explicitly authorized transformation creates a new
lower-sensitivity representation with provenance.
```

Example:

```text
CORE private calendar data
       │
       │ direct transfer denied
       ▼
low-clearance external Module
```

but:

```text
private calendar data
       ↓
authorized minimization/anonymization
       ↓
derived ContextBundle + provenance
       ↓
lower sensitivity
       ↓
external Module
```

is possible.

CORE's high local clearance exists to enable safe cross-domain UX, not to eliminate
Module isolation.

Prompt-injection detection may later supplement this boundary, but model-layer scanners
must never become the authorization mechanism.

---

## 22. Discovery privacy

Availability and visibility are separate concepts.

A Module can exist without every Module, Agent or user context being authorized to know
that it exists.

This matters on shared installations: the fact that a private career-search Module is
installed can itself be sensitive information.

Therefore Module, Agent, Skill and Operation discovery are policy-filtered Kernel
projections rather than globally readable registries.

---

## 23. Semantic data and knowledge

MADRE Kernel does not implement a universal ontology that says generated text is a
`KnowledgeCandidate`, `LearningCandidate`, trusted fact or output requiring acceptance.

At the MADRE level, user input and generated content are both semantic payload with:

```text
provenance
security classification
authority/execution context
```

The owning Module decides whether material becomes domain state, memory, accepted fact,
artifact, history or nothing at all.

Future Module-specific learning and Agent-learning experiments remain owned by the
responsible Module.

This intentionally avoids restoring the discarded universal knowledge/learning class
hierarchy.

---

## 24. Agent state and memory

AgentInstance requires working state because a real reasoning process needs continuity.

Initially it is sufficient to distinguish:

```text
AgentDefinition = reusable configuration
AgentInstance = current working reasoning state
Module = owner of any persistent semantic/learning policy
```

Future persona Agents may preserve selected state/memory across jobs. The responsible
Module owns those semantics, and CORE may eventually provide a standard implementation
for Modules that do not implement their own Agent management.

A universal Agent learning framework is out of scope for the initial architecture.

---

## 25. Planner and orchestrator

The initial architecture does not create separate Planner, Coordinator, Supervisor,
Executor and Verifier classes.

Planning/orchestration is Agent behavior.

Normally:

```text
objective
   ↓
planner/orchestrator AgentInstance
   ↓
WorkPlan
```

A Module may provide a preferred planner AgentDefinition. Otherwise MADRE falls back to
the standard planner supplied by the CORE Module.

Deterministic software may also construct a WorkPlan directly. WorkPlan is semantic data;
it does not require that an LLM generated it.

---

## 26. MADRE Kernel

The **MADRE Kernel** is the trusted deterministic control plane around Modules and Agents.
It is broader than the existing Runtime.

Conceptual responsibilities:

```text
Module registration and adaptation
CORE role assignment
policy-filtered Module/Agent/Skill discovery
AgentDefinition resolution
AgentInstance creation
ContextBundle brokering
security/information-flow enforcement
Operation invocation gateway
WorkPlan persistence
AgentTask orchestration
Agent delegation
semantic/execution correlation
```

The Kernel itself does not reason.

Whenever intelligence is required, it invokes or resumes an AgentInstance.

This prevents a hidden "smart kernel" from becoming another ungoverned reasoning layer.

---

## 27. MADRE Runtime

The existing MADRE Runtime remains the durable execution subsystem.

Its responsibilities remain:

```text
WorkSubmission acceptance
WorkRecord durability
eligibility and scheduling
resource admission
retry and cancellation semantics
restart recovery
Capability invocation
execution evidence
```

Runtime remains deliberately ignorant of:

```text
Module domain semantics
AgentDefinition
AgentInstance state
Skill
Workflow
WorkPlan objective
AgentTask dependencies
```

The Runtime may be deployed as part of the wider Kernel system while preserving this
clean semantic boundary.

---

## 28. Capabilities

A Runtime **Capability** is a bounded computation provider.

Examples:

```text
local LLM inference
remote inference
embedding engine
speech model
image model
specialized compute backend
other bounded execution provider
```

Capability and Operation answer different architectural questions:

```text
Operation:
    What can this Module/domain integration do?

Capability:
    What bounded computation can MADRE execute?
```

Agents do not need to reason directly about provider-specific Capability implementations
unless a future behavior explicitly needs capability selection semantics.

---

## 29. Operation execution versus Runtime work

Not every Module Operation must become a Runtime WorkSubmission.

A fast deterministic Operation such as arithmetic may execute directly through the
Module boundary while the Kernel still enforces policy and traceability.

If an Operation requires durable intelligence work, long-running computation or other
Runtime-managed execution, the owning Module/adapter can materialize the required
Runtime work.

Therefore MADRE does not force every function invocation through the durable scheduler.

---

## 30. Failure and repeatability

Operation metadata should expose enough semantics for safe failure handling, such as:

```text
pure / repeatable
idempotent
side-effecting
non-repeatable
unknown external effect
```

This does not mean Agents blindly retry. It gives orchestration enough evidence to make
or request a safe decision.

Repeatable work may be attempted again through explicit Runtime retry semantics.

If an external side effect may already have happened and is not safely repeatable, MADRE
surfaces the truthful evidence rather than automatically executing it again.

---

## 31. Missing Agent edge case

Durable plans create an important future failure mode: a WorkPlan may reference an
AgentDefinition that later disappears.

Future resolution should conceptually prefer:

```text
1. the same AgentDefinition revision
2. an equivalent/cloned definition
3. a compatible Agent with matching required Skills/Workflows/security
4. explicit user-visible substitution options
```

MADRE must not silently substitute a materially different semantic actor.

The first implementation does not need Agent similarity resolution, but its data model
must preserve enough Agent/Skill/Workflow provenance to make this possible later.

---

## 32. Workflow generation and future learning

Reusable Workflows are deliberate configuration.

Initially they come from:

```text
Module/Skill authors
AgentDefinition authors
users / builder UI
```

An Agent may of course improvise a sequence while solving a task. That ad-hoc sequence
does not automatically become a persistent WorkflowDefinition.

Future learning functionality may propose/promote successful sequences, but that feature
is explicitly out of scope.

The architecture already has a clean future destination:

```text
generated sequence
       ↓ future evaluation
WorkflowDefinition
       ↓
Skill or Agent configuration
```

---

## 33. Effective Agent configuration

An AgentInstance's actual working environment is composition rather than subclassing:

```text
AgentDefinition
       +
Agent-local custom Workflows
       +
attached Skill revisions
       +
Skill-provided Workflows
       +
temporary Skills/Workflows
       +
bounded ContextBundle
       +
permitted Module Operations
       +
instance working state
       =
AgentInstance execution environment
```

This lets a standard CORE planner become a private calendar planner through configuration
rather than a new architectural class.

It also allows a standard CORE Agent to become AAAAT-capable by attaching AAAAT Skills,
context and Operations, without AAAAT having to implement its own Agent framework.

---

## 34. Core entity relationships

```text
Module
 ├── owns Operations
 ├── provides Skills
 ├── may manage AgentDefinitions
 └── provides bounded ContextBundles

SkillDefinition
 ├── describes reusable ability
 ├── may reference Module Operations
 └── may provide 0..N WorkflowDefinitions

WorkflowDefinition
 ├── is first-class
 ├── may originate from a Skill
 └── may originate directly from Agent configuration

AgentDefinition
 ├── is managed by one responsible Module
 ├── attaches 0..N Skills
 ├── may define 0..N direct Workflows
 └── is instantiated 0..N times

AgentInstance
 ├── instantiates one AgentDefinition
 ├── receives bounded Context
 ├── receives effective Skills/Workflows/Operations
 └── performs semantic work

WorkPlan
 ├── represents one durable objective
 └── contains AgentTasks

AgentTask
 ├── resolves/uses an AgentInstance
 ├── may select Skills/Workflow
 ├── may invoke Module Operations
 ├── may create child AgentTasks
 └── may correlate with 0..N Runtime WorkRecords

Runtime WorkRecord
 └── records physical execution

Capability
 └── performs bounded computation
```

---

## 35. Initial implementation boundary

The first implementation should introduce only enough semantic structure to establish the
working agentic environment.

| Concept | Initial need | Purpose |
| --- | --- | --- |
| `ModuleManifest` | yes | expose Module contract |
| minimal `SecurityProfile` | yes | enforce the first privacy boundary |
| `OperationDescriptor` | yes | expose bounded Module functionality |
| `SkillDefinition` | yes | portable Agent ability package |
| `WorkflowDefinition` | yes | explicit reusable Agent behavior |
| `AgentDefinition` | yes | reusable Agent configuration |
| `AgentInstance` | yes | actual reasoning state |
| `ContextBundle` | yes | bounded context transfer |
| `WorkPlan` | yes | durable semantic orchestration |
| `AgentTask` | yes | objective-specific Agent assignment |
| CORE role | yes | identify default privileged Module |
| persistent persona framework | no | defer |
| autonomous learning | no | defer |
| Workflow learning/promotion | no | defer |
| dynamic active-plan revision/versioning | no | defer |
| Agent similarity substitution | no | defer |
| multi-Agent team abstraction | no | defer |
| `Routine` | no | removed |
| global `Tool` entity | no | removed |

The concrete schemas should be designed from these semantics and the first runnable
behavior rather than by copying prototype dataclasses.

---

## 36. Architecture acceptance scenarios

The architecture should remain coherent for all of these without introducing special
execution models.

### A. Agentless Module

```text
CalcModule
  └── calculate Operation

User request
  ↓
CORE fallback Agent
  ↓
CalcModule.calculate
```

### B. Skill portability

```text
AAAATModule
  └── AAAAT Skill

CORE standard Agent
  +
AAAAT Skill
  =
AAAAT-capable AgentInstance
```

### C. Specialized configured Agent

```text
CORE planner AgentDefinition
      ↓ derive/customize
Private Calendar Planner
      +
Calendar Skill
      +
high-security policy
```

### D. Concurrent instances

```text
Planner AgentDefinition
   ├── Instance A → migration plan
   ├── Instance B → holiday plan
   └── Instance C → software architecture plan
```

### E. Long-running objective

```text
WorkPlan
   ↓
AgentTask A today
   ↓
scheduled AgentTask B tonight
   ↓
process/runtime restart
   ↓
AgentTask C tomorrow
```

### F. One semantic task, many executions

```text
AgentTask
   ↓
reason
   ↓
Operation
   ↓
reason
   ↓
Runtime work
   ↓
delegate child Agent
   ↓
more Runtime work
```

### G. Cross-domain privacy

```text
CORE-private ContextBundle
   ↓ direct transfer denied
AAAAT Agent

CORE-private ContextBundle
   ↓ authorized minimization
Derived ContextBundle
   ↓
AAAAT Agent
```

### H. Third-party MCP

```text
MCP server
   ↓ Module adapter
SearchModule
   ├── search Operation
   └── optional Search Skill
```

### I. User custom Workflow

```text
AgentDefinition
   ├── imported Skill Workflow
   └── direct user-created Workflow
```

### J. Skill customization

```text
AAAAT Skill @ 3
   ↓ derive
My AAAAT Skill @ 1

upstream Module update does not silently alter the derived configuration
```

---

## 37. Architectural invariants

```text
Module is MADRE's semantic/domain integration boundary.

CORE is a replaceable privileged Module role.

Modules own Operations.

MADRE has no canonical global Tool entity.

Third-party tools/APIs/MCP systems enter through Module adapters.

A Module may provide zero Agents.

AgentDefinition is reusable configuration.

AgentInstance is the actual working reasoning actor.

Agents are not implicitly aware of their managing Module's domain.

Agents receive bounded Context, Skills, Workflows and permitted Operations.

Skill is a reusable ability package.

A Skill may contain zero or more WorkflowDefinitions.

WorkflowDefinition is first-class.

WorkflowDefinitions may originate from Skills or directly from Agent configuration.

Agent repertoire is the union of direct Workflows and Skill-contributed Workflows.

Skill/Workflow revisions are explicit and pinnable.

Customization derives new definitions rather than silently mutating upstream ones.

Routine does not exist.

All semantic/intelligence tasks are mediated through AgentInstances.

Agentless Modules use CORE/default fallback Agents.

Multi-Agent work is ordinary AgentTask delegation, not another workflow ontology.

WorkPlan is durable MADRE orchestration state.

AgentTask is semantic work and is not equivalent to Runtime WorkRecord.

Readiness is derived from facts; `blocked` is not a canonical semantic lifecycle state.

WorkPlans remain inspectable and replayable.

Replay creates provenance rather than rewriting history.

Kernel controls Modules, security, context, Agents and orchestration.

Runtime controls physical durable execution.

Capabilities perform bounded computation.

Modules determine domain meaning and any future learning semantics.

MADRE does not implement a universal knowledge/learning ontology.

Security is structurally enforced across context and Operation boundaries.

Discovery itself may be privacy-controlled.

Architecture remains inspectable and user-customizable.
```

---

## 38. MADRE AgenticLoop

The complete semantic loop is deliberately small:

```text
User / Application
       │
       ▼
     Module
       │
       │ bounded task + context
       ▼
 MADRE Kernel
       │
       │ resolve Agent
       ▼
 AgentInstance
       │
       ├── Skills
       ├── Workflows
       ├── Context
       └── Module Operations
       │
       ▼
    WorkPlan
       │
       ▼
   AgentTasks
       │
       ├── reason
       ├── invoke Module Operation
       ├── delegate AgentTask
       └── schedule future reasoning
       │
       ▼
 MADRE Runtime
       │
       ▼
 Capabilities
       │
       ▼
 execution evidence
       │
       ▼
 continue AgenticLoop
       │
       ▼
 response / artifact / domain Operation
```

The architecture intentionally stops here.

Persistent personas, autonomous learning, generated reusable Workflows, dynamic replanning,
Agent teams and richer security/policy systems all have explicit seams to attach later,
but none are prerequisites for establishing the initial agentic environment.
