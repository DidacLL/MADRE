# MADRE Runtime Class Map — Discussion Baseline

**State:** discussion baseline  
**Scope:** MADRE runtime class map only.  
**Purpose:** provide a clean, self-contained basis for the next class-by-class review without importing speculative classes, attributes, operations, or implementation patterns.

This document captures the accepted architectural intent for the MADRE runtime class map. It is intentionally conservative. It includes only concepts that are currently justified by the MADRE runtime thesis: local-first coordination, module-owned reasoning, bounded agents, governed context, delayed reasoning, controlled action execution, uncertain knowledge, explicit learning candidates, runtime trace, and recoverability.

This document does not freeze Java implementation details, database schemas, scheduler internals, threading models, prompt templates, storage engines, UI transports, or model-provider adapters.

---

## 1. Class Map Discipline

A runtime class is allowed only when it has at least one of these properties:

1. Stable runtime identity.
2. Stable ownership boundary.
3. Stable lifecycle.
4. Stable responsibility that cannot be represented as a value inside another class.
5. Required participation in the canonical MADRE runtime flow.

A class, attribute, or operation must not be created only because a common agent framework, backend pattern, chatbot architecture, or LLM-generated design would usually include it.

Before adding anything, ask:

- Could the runtime still work correctly without this class, attribute, or operation?
- Is this concept stable enough to expose in the class map?
- Is this a true MADRE runtime concept or only an implementation mechanism?
- Does this simplify the architecture, or does it hide an unresolved design problem behind a name?
- Can the same responsibility be expressed as a role, metadata value, runtime event, or module-owned behavior?

If the answer is uncertain, the concept remains a note, not a class.

---

## 2. Accepted Runtime Thesis

MADRE is a local-first, model-agnostic, delayed-reasoning runtime. The user may experience one coherent assistant, but the runtime may internally coordinate multiple modules, agents, actions, reasoning tasks, context bundles, knowledge records, and learning candidates.

The apparent “one mind” behavior is a product-level illusion created by disciplined runtime coordination. It must not hide the engineering truth: every module, agent, action, context selection, model-backed execution, knowledge change, learning proposal, and recovery path needs an accountable boundary and trace.

The model is never the system. Model-backed behavior is one possible action execution path. Its produced material is returned through ordinary MADRE response/material mechanisms and recorded through runtime events. A separate inference-output class is not currently justified.

All retained system knowledge is uncertain. User-provided material, generated material, imported material, web-retrieved material, extracted material, inferred material, fictional material, stylistic material, and procedural material may all become module knowledge. The system must preserve provenance, intended use, scope, sensitivity, truth level, truth authority, confidence, correction, and lifecycle information instead of pretending that only externally verified facts are knowledge.

---

## 3. Non-Class Runtime Roles and Boundaries

The following concepts are accepted but are not currently separate classes.

### Core Module

The Core Module is the kernel-assigned role held by one compatible `ReasoningModule`. It receives ordinary interaction first when the request has no valid explicit module target.

It is not a class and not a special module subtype.

### Main Agent

The Main Agent is the mandatory module role held by one module-owned `SystemAgent`.

It is not a class. The role adds no authority outside the owning module.

### Access Surface

An access surface is a CLI, local console, GUI, speech interface, application interface, or other user/system entry point. It submits requests and may expose inspection state.

It is not runtime authority. It must not own policy, module knowledge, scheduling, routing semantics, action authority, or learning promotion.

A `SystemAgent` may use a live interaction channel exposed through an access surface, but it does not own the surface.

### Module Storage Access

The kernel may provide scoped storage access to modules. The concrete storage mechanism may be SQLite, graph storage, file storage, hybrid storage, or another local/authorized mechanism.

Storage access is not currently class-mapped as a MADRE domain class. It is an infrastructure responsibility whose shape remains implementation-defined.

### Boundary Composition

MADRE needs a strong boundary mechanism, but the current class map does not define it as a class yet.

The accepted requirement is this: a request, task, agent invocation, workflow invocation, routine invocation, or action invocation must not be able to flow through runtime actors or capabilities whose declared boundaries are incompatible with the accumulated constraints of the execution path.

This must be enforced by design, not by an overridable `checkPolicy` method or a generic object containing loose text and booleans.

The future design must express at least:

- immutable boundary declarations exposed by modules, agents, workflows, routines, and actions;
- accumulated effective constraints carried through request, plan, task, and invocation context;
- a constructed execution authorization that must exist before an action or agent invocation can run;
- monotonic restriction: the execution path may narrow constraints but must not silently widen them;
- trace: the boundary result must be recorded through runtime events and policy/authorization evidence.

The exact class structure for this mechanism is intentionally not fixed in this document.

---

## 4. Canonical Runtime Flow

1. An access surface submits a user, system, or module-originated reasoning need.
2. The kernel resolves the explicit target module if present and valid.
3. If the target is missing, empty, unknown, or invalid, the kernel resolves the request to the Core Module.
4. The kernel records runtime events and provides runtime services such as module access, scheduling, storage access, and journal access.
5. The kernel does not semantically decide which module is best for the request.
6. A `ReasoningModule` receives a `ReasoningRequest` and handles it through module-owned behavior, normally through its `SystemAgent` in the Main Agent role.
7. The module may answer, clarify, block, delegate, create a reasoning plan, or fail.
8. Structured or delayed work is represented through a `ReasoningPlan` containing `ReasoningTask` objects.
9. A ReasoningTask targets one of these:
	- a delegated ReasoningRequest;
	- an executable agent capability such as an AgentRoutine, AgentAction, or ModelAction;
	- a module-level Workflow only as a reusable planning blueprint to be applied into concrete ReasoningTask occurrences.
10. When a Workflow is selected, module planning applies the workflow to create or refine specific ReasoningTask objects inside the owning ReasoningPlan. The workflow itself is not executed as an AgentRequest and does not return an AgentResponse.
11. When an executable task is selected for execution, module orchestration constructs the concrete AgentRequest under the effective context and boundary mechanism. The selected agent, AgentRoutine, AgentAction, or ModelAction returns an AgentResponse.
12. A model-backed execution is represented as a `ModelAction` producing ordinary response material or artifacts, with evidence recorded in `RuntimeEvent`.
13. Retained material may become `KnowledgeRecord` only through explicit knowledge handling.
14. Proposed knowledge-boundary changes are represented by `KnowledgeCandidate`.
15. Proposed module-behavior improvements are represented by `LearningCandidate`.
16. All significant transitions are appended as `RuntimeEvent` entries through `RuntimeJournal`.

---

## 5. Class Inventory

Accepted classes for the current discussion baseline:

- `MADREKernel`
- `ReasoningModule`
- `MADREAgent`
- `SimpleAgent`
- `TwinAgent`
- `ComplexAgent`
- `SystemAgent`
- `ReasoningRequest`
- `ReasoningResponse`
- `ReasoningPlan`
- `ReasoningTask`
- `AgentRequest`
- `AgentResponse`
- `Workflow`
- `AgentRoutine`
- `AgentAction`
- `ModelAction`
- `ModelBinding`
- `ContextBundle`
- `ReasoningArtifact`
- `KnowledgeRecord`
- `KnowledgeCandidate`
- `LearningCandidate`
- `PolicyDecision`
- `RuntimeJournal`
- `RuntimeEvent`

The list is intentionally small. Any future class must be justified against the class-map discipline in section 1.

---

## 6. Class Definitions

### 6.1 MADREKernel

**Description**  
Deterministic local runtime coordinator.

**Responsibilities**

- Register `ReasoningModule` objects.
- Assign one registered module as the Core Module role.
- Resolve explicit module targets and Core Module fallback.
- Provide module access paths.
- Schedule delayed reasoning plans at runtime level.
- Provide module-scoped storage access through implementation-defined infrastructure.
- Append runtime events through `RuntimeJournal`.
- Own low-level runtime bindings for local execution, device resources, access-surface adapters, scheduling, and host-service access.

**Boundaries**

- Does not reason.
- Does not semantically decide module suitability.
- Does not inspect module-private knowledge for routing decisions.
- Does not plan module work.
- Does not execute actions.
- Does not invoke models directly.
- Does not learn.
- Does not own module knowledge.
- Does not promote knowledge or behavior changes.

**Known attributes**

- `kernelId: UUID`
- `coreModule: Ref<ReasoningModule>`
- `moduleRegistry: Map<UUID, ReasoningModule>`
- `runtimeJournal: RuntimeJournal`

Storage assignments, scheduler internals, access-surface adapters, and hardware/resource bindings are implementation-defined and should not be expanded in this class map yet.

**Known operations**

`registerModule(module: ReasoningModule): void`

- Precondition: `module` has a stable module identity and does not duplicate an already registered module id.
- Postcondition: the module is available for explicit target resolution and may later be assigned a runtime role.

`assignCoreModule(moduleRef: Ref<ReasoningModule>): void`

- Precondition: `moduleRef` resolves to a registered module compatible with ordinary interaction entry.
- Postcondition: unresolved ordinary interaction falls back to that module.

`resolveTarget(request: ReasoningRequest): Ref<ReasoningModule>`

- Precondition: `request` has been submitted through a valid runtime entry path.
- Postcondition: returns the explicit valid target module when present; otherwise returns the Core Module. This operation must not perform semantic module selection.

`appendEvent(event: RuntimeEvent): void`

- Precondition: `event` represents a runtime-relevant transition or observation.
- Postcondition: the event is appended through the runtime journal without rewriting previous history.

---

### 6.2 ReasoningModule

**Description**  
Governed reasoning and data-governance unit for a bounded domain, topic, user scope, system scope, or operational scope.

**Responsibilities**

- Own bounded module knowledge.
- Own module-local context governance.
- Own allowed agents, workflows, routines, and actions.
- Own allowed model-backed action bindings for its agents and actions.
- Own module-local risk, sensitivity, recovery, and learning boundaries.
- Receive and create `ReasoningRequest` objects.
- Answer directly when safe and sufficient.
- Ask for clarification when intent, scope, context, or risk is insufficient.
- Block requests that cannot cross its boundaries.
- Create `ReasoningPlan` objects when structured or delayed work is needed.
- Create minimized delegated `ReasoningRequest` objects when another module is required.
- Ensure output is minimized to the consumer scope.

**Boundaries**

- Does not become the kernel.
- Does not own knowledge outside its module boundary.
- Does not directly access another module’s private data.
- Does not bypass context governance or runtime trace.
- Does not widen the authority of its agents or actions.

**Known attributes**

- `moduleId: UUID`
- `mainAgent: Ref<SystemAgent>`
- `agents: Set<Ref<MADREAgent>>`
- `workflows: Set<Workflow>`
- `actions: Set<AgentAction>`

Module knowledge storage, context indexes, storage ports, and internal repositories are not fixed as class-map attributes yet.

**Known operations**

`handle(request: ReasoningRequest): ReasoningResponse`

- Precondition: the request has been resolved to this module through kernel target resolution, Core Module fallback, or module-originated delegation.
- Postcondition: returns one of the accepted response outcomes: answer, clarification, block, delegation, plan creation, or failure. Any significant transition must be journaled.

---

### 6.3 MADREAgent

**Description**  
Abstract module-owned runtime actor that executes concrete agent invocations under module boundaries.

**Responsibilities**

- Execute concrete `AgentRequest` objects.
- Use provided context and module-authorized execution context.
- Invoke allowed routines or actions only through a valid invocation path.
- Return `AgentResponse` objects.
- Preserve module-private knowledge inside the owning module boundary.

**Boundaries**

- Does not own kernel resources.
- Does not widen its own authority.
- Does not access module knowledge except through module-authorized context and behavior.
- Does not promote generated or produced material into knowledge or learning by itself.

**Known attributes**

- `agentId: UUID`
- `owningModule: Ref<ReasoningModule>`

**Known operations**

`execute(request: AgentRequest): AgentResponse`

- Precondition: the request was constructed by module-owned orchestration and satisfies the effective boundary mechanism for the target agent/routine/action path.
- Postcondition: returns result material, artifact references, block material, or failure material. Execution evidence must be journaled.

---

### 6.4 SimpleAgent

**Description**  
Concrete `MADREAgent` for direct execution where no mandatory review or complex lane behavior is required.

**Responsibilities**

- Execute a valid `AgentRequest` directly.
- Return one `AgentResponse`.

**Boundaries**

- Does not double-check itself.
- Does not perform reflective learning behavior.
- Does not gain authority beyond the owning module and request boundary.

**Known attributes**

- Inherits `agentId` and `owningModule`.

**Known operations**

- Inherits `execute(request: AgentRequest): AgentResponse`.

---

### 6.5 TwinAgent

**Description**  
Concrete `MADREAgent` whose invariant is response production plus mandatory review before final dispatch.

**Responsibilities**

- Produce or obtain an initial response path.
- Review, verify, or double-check the produced material before it is accepted as the final `AgentResponse`.
- Return a checked `AgentResponse`.

**Boundaries**

- Does not learn by itself.
- Its review path does not widen action authority.
- It cannot dispatch unchecked material as final response when review is required.
- It does not require a specific implementation shape such as two models or two threads.

**Known attributes**

- Inherits `agentId` and `owningModule`.

**Known operations**

- Inherits `execute(request: AgentRequest): AgentResponse`.

**Execution postcondition**

- If execution succeeds, the returned response has passed the agent’s required review behavior.

---

### 6.6 ComplexAgent

**Description**  
Concrete `MADREAgent` capable of foreground continuity, deeper reasoning supervision, and reflective/evaluative work.

**Responsibilities**

- Preserve foreground conversational flow when used for interaction.
- Coordinate deeper reasoning behavior when work must be decomposed, delayed, verified, or delegated.
- Coordinate reflection over outcomes, feedback, repeated failures, and improvement pressure.
- Propose learning candidates when appropriate, without activating them.

**Boundaries**

- Does not gain extra authority from having multiple behavioral lanes.
- Reflection may propose improvement but cannot activate learning by itself.
- Foreground behavior must remain inside module and request boundaries.
- Lane scheduling and threading are not class-mapped here.

**Known attributes**

- Inherits `agentId` and `owningModule`.

Foreground, reasoning, and reflection lanes are accepted behavioral capacities, not currently exposed as class attributes.

**Known operations**

- Inherits `execute(request: AgentRequest): AgentResponse`.

---

### 6.7 SystemAgent

**Description**  
Module-bound `ComplexAgent` that serves as the module’s main operational agent. Its distinguishing property is not global authority but strong local binding to its owning module.

**Responsibilities**

- Hold the Main Agent role for one `ReasoningModule`.
- Coordinate ordinary module operation.
- Preserve live interaction continuity when the module is handling user-facing flow.
- Coordinate foreground work while deeper reasoning continues in the background.
- Learn from both execution data and module-governed context through controlled learning proposals.
- Use module-owned context and knowledge only within module boundaries.

**Boundaries**

- Does not become the kernel.
- Does not manage another module’s private knowledge.
- Does not own access surfaces.
- Does not gain authority outside the owning module.
- Does not directly promote knowledge or behavior changes.

**Known attributes**

- Inherits `agentId` and `owningModule`.

**Known operations**

- Inherits `execute(request: AgentRequest): AgentResponse`.

Additional live-interaction operations are expected but not fixed yet. They require a specific class-by-class design pass.

---

### 6.8 ReasoningRequest

**Description**  
Passive request object for a reasoning need. It may originate from a user interaction, system event, module, agent, plan, or delegated task.

**Responsibilities**

- Carry the reasoning objective.
- Carry origin information.
- Carry explicit target module when known.
- Carry scope and constraints required to avoid unsafe or ambiguous execution.
- Carry context references when relevant.
- Support inter-module delegation without exposing private module internals.

**Boundaries**

- Does not build plans.
- Does not schedule itself.
- Does not execute work.
- Does not authorize actions.
- Does not carry module-private knowledge directly when delegation requires minimization.

**Known attributes**

- `requestId: UUID`
- `objective: Text`
- `originRef: Ref<?>`
- `targetModule: Optional<Ref<ReasoningModule>>`
- `contextRef: Optional<Ref<ContextBundle>>`
- `scope: Text`
- `constraints: Text`
- `expectedResult: Optional<Text>`

The exact structure of origin, scope, constraints, priority, sensitivity, and delegation-loop control remains open.

**Known operations**

- None fixed.

---

### 6.9 ReasoningResponse

**Description**  
Passive module-level response to a `ReasoningRequest`.

**Responsibilities**

- Represent the module’s response outcome.
- Carry answer, clarification, block, delegation, plan-created, or failure material.
- Reference produced artifacts or created plans when applicable.

**Boundaries**

- Does not execute work.
- Does not authorize action execution.
- Does not promote knowledge or learning by itself.

**Known attributes**

- `responseId: UUID`
- `requestRef: Ref<ReasoningRequest>`
- `outcome: Text`
- `materialRefs: List<Ref<?>>`

The exact outcome representation is not fixed yet.

**Known operations**

- None fixed.

---

### 6.10 ReasoningPlan

**Description**  
Durable reasoning-continuity aggregate derived from a `ReasoningRequest` when structured or delayed work is required.

**Responsibilities**

- Preserve the continuity of a reasoning process.
- Own the collection of planned `ReasoningTask` occurrences.
- Preserve dependency and result continuity between tasks.
- Support inspection, correction, supersession, failure, and recovery through journaled transitions.

**Boundaries**

- Does not execute itself.
- Does not schedule itself.
- Does not become an actor.
- Does not directly create `AgentRequest` objects before execution selection.
- Does not know the private internals of another module’s plan.

**Known attributes**

- `planId: UUID`
- `sourceRequest: Ref<ReasoningRequest>`
- `ownerModule: Ref<ReasoningModule>`
- `tasks: List<ReasoningTask>`
- `status: Text`

Objective, scope, constraints, result references, correction references, rollback references, and supersession references are likely needed, but their exact representation should be defined during the class-specific pass.

**Known operations**

- No public operation is fixed yet.

Controlled state transitions are required, but their exact operation names, preconditions, and postconditions must be designed after the boundary mechanism is clarified.

---

### 6.11 ReasoningTask

**Description**  
One planned execution occurrence inside exactly one `ReasoningPlan`.

**Responsibilities**

- Represent a plan-local unit of work.
- Target a reusable capability or delegated reasoning request.
- Preserve plan-specific dependency, blocking, context, expected result, retry, correction, and result-handling semantics.

**Boundaries**

- Does not redefine the reusable capability it targets.
- Does not contain a prebuilt `AgentRequest` before execution selection.
- Does not know the internals of another module’s resulting plan.
- Does not execute itself.
- Does not authorize its own execution.

**Known attributes**

- `taskId: UUID`
- `targetRef: Ref<?>`
- `targetKind: Text`
- `dependencies: List<Ref<ReasoningTask>>`
- `contextRequirements: Text`
- `expectedOutput: Optional<Text>`
- `status: Text`

Priority, blocking mode, parallelization, retry policy, correction policy, result handling, completion condition, and boundary requirements are acknowledged but not structurally fixed yet.

**Known operations**

- None fixed.

---

### 6.12 AgentRequest

**Description**  
Concrete invocation context sent to an agent, AgentRoutine, AgentAction, or ModelAction after module orchestration has selected an executable task.

An AgentRequest is not sent to a Workflow. A Workflow is applied at module planning/refinement time to create or refine ReasoningTask occurrences. The resulting tasks may later produce AgentRequest objects when selected for execution.

**Responsibilities**

- Carry the concrete execution objective.
- Carry the selected context bundle.
- Carry caller/module information.
- Carry selected executable capability information when applicable, normally an agent, AgentRoutine, AgentAction, or ModelAction.
- Carry enough execution-boundary evidence to ensure the invocation is valid.

**Boundaries**

- Not the universal reasoning request.
- Does not replace `ReasoningRequest`.
- Does not create plan state by itself.
- Does not authorize itself; it must be constructed through the accepted boundary mechanism.

**Known attributes**

- `agentRequestId: UUID`
- `objective: Text`
- `callerRef: Ref<?>`
- `contextBundle: Optional<Ref<ContextBundle>>`
- `selectedCapabilityRef: Optional<Ref<?>>`

The exact representation of execution authorization remains unresolved and must not be faked with loose text fields.

**Known operations**

- None fixed.

---

### 6.13 AgentResponse

**Description**  
Concrete result returned by an agent, AgentRoutine, AgentAction, or ModelAction invocation.

**Responsibilities**

- Carry produced material or artifact references.
- Carry block/refusal/failure material.
- Carry result references for later task, plan, knowledge, or learning handling.
- Link to runtime event evidence.

**Boundaries**

- Does not automatically become knowledge.
- Does not automatically become learning.
- Does not authorize later actions.
- Does not widen policy or boundary conditions.
- Not returned by a Workflow directly; workflow application changes or creates plan tasks, while executable task execution returns AgentResponse.

**Known attributes**

- `agentResponseId: UUID`
- `requestRef: Ref<AgentRequest>`
- `status: Text`
- `artifactRefs: List<Ref<ReasoningArtifact>>`
- `eventRefs: List<Ref<RuntimeEvent>>`

**Known operations**

- None fixed.

---

### 6.14 Workflow

**Description**  
Reusable module-level planning blueprint used to create or refine ReasoningTask occurrences inside a ReasoningPlan.

**Responsibilities**

- Represent reusable module-level task structure.
- Avoid repeated reasoning for recurring task patterns that have become stable and useful.
- Expose capability metadata sufficient for module planning and task generation.
- When applied, create or refine concrete ReasoningTask objects in a ReasoningPlan.
- Reference agents, AgentRoutine, AgentAction, ModelAction, or delegated ReasoningRequest objects only as task targets, not as immediately executed calls.
- Support later module learning when repeated successful task structures suggest a reusable workflow, without activating workflow changes outside the learning/promotion boundary.

**Boundaries**

- Not an actor.
- Not an agent action.
- Not an AgentRoutine.
- Not a second plan.
- Not a scheduled object.
- Not invoked through AgentRequest.
- Does not return AgentResponse.
- Does not execute generated tasks by itself.
- Does not own current-plan scheduling semantics.
- Does not widen module authority.

**Known attributes**

- `workflowId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `purpose: Text`

A future operation may apply a workflow to a request or plan context, but its postcondition must be task creation/refinement only. It must not execute actions, invoke agents, or return AgentResponse.

**Known operations**

- None fixed.

---

### 6.15 AgentRoutine

**Description**  
Agent-owned compound executable capability. It is a kind of compound AgentAction, not a module-level planning blueprint.

**Responsibilities**

- Represent reusable executable behavior owned by one agent.
- Compose several lower-level actions, prompts, checks, or reasoning steps behind one concrete invocation.
- Execute under the same invocation semantics as an agent action: it receives an AgentRequest and returns an AgentResponse.
- Expose capability metadata sufficient for safe task selection and boundary matching.
- Allow a ReasoningTask to target a familiar agent-level routine without exposing the full internal sequence to the plan.

**Boundaries**

- Not a module workflow.
- Not a planning blueprint.
- Does not create ReasoningTask objects.
- Does not carry plan-specific blocking, priority, dependency, or parallelization semantics.
- Does not widen module or agent authority.
- Does not execute outside a valid agent/module invocation context.

**Known attributes**

- `routineId: UUID`
- `ownerAgent: Ref<MADREAgent>`
- `purpose: Text`

The class map treats AgentRoutine as the agent-level compound executable capability. The later Java design may represent this through inheritance from AgentAction or through composition, but the architectural invariant is fixed: routines are executable agent capabilities, while workflows are module-level task blueprints.

**Known operations**

- None fixed.

---

### 6.16 AgentAction

**Description**  

Smallest executable capability available under module/agent authority.

AgentRoutine is the compound agent-level form of executable capability. An AgentAction is the smallest executable capability; an AgentRoutine may coordinate several actions or reasoning steps but still behaves as an executable invocation that receives AgentRequest and returns AgentResponse.

**Responsibilities**

- Execute deterministic, model-backed, integration-backed, or other bounded behavior.
- Expose capability metadata sufficient for safe selection.
- Operate only under module authority, governed context, runtime trace, and the accepted boundary mechanism.
- Return material suitable for `AgentResponse`, `RuntimeEvent`, artifacts, knowledge handling, or later evaluation.

**Boundaries**

- Does not carry plan-specific scheduling semantics.
- Does not authorize itself.
- Does not execute from raw model output or raw user text.
- Does not bypass runtime event trace.

**Known attributes**

- `actionId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `purpose: Text`

**Known operations**

`execute(request: AgentRequest): AgentResponse`

- Precondition: the request was constructed through module-owned orchestration and satisfies the effective boundary mechanism for this action.
- Postcondition: action result, failure, block, or produced material is represented in `AgentResponse` and significant evidence is recorded through `RuntimeEvent`.

---

### 6.17 ModelAction

**Description**  
Specialized `AgentAction` that uses a model/inference binding as part of execution.

**Responsibilities**

- Prepare model-backed execution input from the `AgentRequest` and governed context.
- Use only model bindings allowed by the owning module/agent/action boundary.
- Return produced material through ordinary `AgentResponse` / `ReasoningArtifact` mechanisms.
- Record model-backed execution evidence through `RuntimeEvent`.

**Boundaries**

- Does not create model authority.
- Does not create a separate output class.
- Does not bypass module authority or context governance.
- Does not bypass model binding constraints.
- Does not treat generated material as truth, knowledge, behavior, routing, policy, action authority, or learning by itself.

**Known attributes**

- Inherits `actionId`, `ownerModule`, and `purpose`.
- `modelBinding: Ref<ModelBinding>`

**Known operations**

- Inherits `execute(request: AgentRequest): AgentResponse`.

**Execution postcondition**

- The response contains produced material or failure material, and the journal contains enough evidence to inspect the model-backed execution path.

---

### 6.18 ModelBinding

**Description**  
Configuration boundary that constrains which model-backed execution options a model-backed action or agent may use.

**Responsibilities**

- Constrain permitted model/backend/profile choices.
- Preserve model substitutability.
- Preserve local-first and authorized-remote execution boundaries.
- Preserve reproducibility and resource discipline information needed by runtime trace.

**Boundaries**

- Not the model itself.
- Not a runtime actor.
- Not authority by itself.
- Does not execute inference.

**Known attributes**

- `bindingId: UUID`
- `description: Text`

Concrete model file references, backend references, resource limits, sampling configuration, and remote/delegated execution details are not class-mapped here yet.

**Known operations**

- None fixed.

---

### 6.19 ContextBundle

**Description**  
Governed context prepared for one request, plan task, agent invocation, routine invocation, action invocation, or model-backed execution.

**Responsibilities**

- Carry selected context material or references.
- Preserve source/provenance references.
- Preserve scope and purpose.
- Preserve sensitivity and intended use.
- Support minimization and revocation semantics.

**Boundaries**

- Source material does not become instruction by inclusion.
- Context does not become authority by inclusion.
- Context selection authority belongs to module-owned governance and the boundary mechanism, not to the passive bundle.

**Known attributes**

- `bundleId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `sourceRefs: List<Ref<?>>`
- `scope: Text`
- `sensitivity: Text`
- `intendedUse: Text`

**Known operations**

- None fixed.

---

### 6.20 ReasoningArtifact

**Description**  
Materialized output or captured result produced by reasoning or action execution.

**Responsibilities**

- Represent produced material that may be returned, inspected, exported, referenced, corrected, discarded, or used as input to later handling.
- Preserve origin evidence through runtime event references.
- Preserve enough metadata for later knowledge, recovery, or evaluation handling.

**Boundaries**

- Not knowledge by default.
- Not truth by default.
- Not learning by default.
- Does not authorize actions.
- Does not become active module knowledge without explicit knowledge handling.

**Known attributes**

- `artifactId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `contentRef: Ref<?>`
- `originEventRef: Ref<RuntimeEvent>`
- `intendedUse: Text`

Scope, sensitivity, provenance, confidence, and lifecycle metadata may be needed, but exact duplication or sharing with `KnowledgeRecord` should be discussed before adding attributes.

**Known operations**

- None fixed.

---

### 6.21 KnowledgeRecord

**Description**  
Module-owned retained material that the system may later use as known context, evidence, preference, style, fiction, procedure, hypothesis, correction, memory, or working belief under explicit metadata.

`KnowledgeRecord` is not equivalent to verified factual truth. It is the central retained-material concept for MADRE’s uncertain knowledge model.

**Responsibilities**

- Represent retained module knowledge regardless of origin.
- Support uncertain, partial, fictional, stylistic, procedural, personal, generated, retrieved, imported, extracted, or inferred material.
- Preserve provenance and source references.
- Preserve scope, sensitivity, intended use, truth level, truth authority, confidence, correction, and lifecycle metadata.
- Allow use as context according to metadata and module boundaries.
- Support correction, reclassification, detachment, invalidation, deletion where permitted, and recovery evidence.

**Boundaries**

- Not factual truth by default.
- Not learning by itself.
- Not generated material by definition.
- Not a subclass of `ReasoningArtifact` in this baseline.
- May reference artifacts, source material, conversations, action results, or other records as provenance.
- Does not authorize actions.
- Does not promote itself across module boundaries.

**Known attributes**

- `knowledgeId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `contentRef: Ref<?>`
- `provenanceRefs: List<Ref<?>>`
- `scope: Text`
- `sensitivity: Text`
- `intendedUse: Text`
- `truthLevel: Text`
- `truthAuthority: Text`
- `confidence: Text`
- `lifecycleState: Text`

The exact taxonomy for truth, authority, confidence, content kind, intended use, correction, deletion, and detachment must be defined later. Do not replace this with a simple boolean truth flag.

**Known operations**

- None fixed.

Lifecycle transitions must be explicit and journaled, but operation names are not fixed yet.

---

### 6.22 KnowledgeCandidate

**Description**  
Pending change to a module knowledge boundary.

**Responsibilities**

- Represent proposed inclusion, reclassification, correction, ownership transfer, trust-level change, intended-use change, promotion, detachment, invalidation, or related knowledge-boundary mutation.
- Preserve source/evidence references.
- Remain inactive until accepted through the applicable authority path.

**Boundaries**

- Not active knowledge by itself.
- Not learning by itself.
- Does not promote itself.
- Does not authorize future use until resolved.

**Known attributes**

- `candidateId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `proposedChange: Text`
- `sourceRefs: List<Ref<?>>`
- `targetKnowledgeRef: Optional<Ref<KnowledgeRecord>>`
- `status: Text`

**Known operations**

- None fixed.

---

### 6.23 LearningCandidate

**Description**  
Evaluated proposed improvement to module behavior, evaluation, routing, workflows, routines, context selection, trust calibration, model selection, or other module function.

**Responsibilities**

- Represent improvement pressure derived from evaluated traces, outcomes, corrections, user feedback, repeated failures, repeated context needs, module rejection, or evaluated generated material.
- Preserve evaluation evidence.
- Preserve scope and rollback expectation.
- Remain inactive until promoted through the applicable learning boundary.

**Boundaries**

- Not active behavior by itself.
- Not ordinary stored material.
- Saving a `KnowledgeRecord` is not learning by itself.
- Does not rewrite protected behavior directly.

**Known attributes**

- `candidateId: UUID`
- `ownerModule: Ref<ReasoningModule>`
- `proposedImprovement: Text`
- `evidenceRefs: List<Ref<?>>`
- `scope: Text`
- `rollbackRefs: List<Ref<?>>`
- `status: Text`

**Known operations**

- None fixed.

---

### 6.24 PolicyDecision

**Description**  
Passive record of an explicit boundary or authorization outcome.

In this baseline, `PolicyDecision` records the result of authority/boundary handling. It is not the object that grants execution authority by itself.

**Responsibilities**

- Record allow, block, or authorization-required outcomes.
- Carry reason/evidence for the outcome.
- Link to affected action, data, autonomy, or boundary class when known.
- Link to runtime events.

**Boundaries**

- Does not execute policy logic.
- Does not authorize by itself.
- Does not override the boundary mechanism.
- Generated material cannot create, override, or widen it.

**Known attributes**

- `decisionId: UUID`
- `outcome: Text`
- `reason: Text`
- `affectedRef: Optional<Ref<?>>`
- `eventRef: Optional<Ref<RuntimeEvent>>`

**Known operations**

- None fixed.

---

### 6.25 RuntimeJournal

**Description**  
Append-only local runtime trace mechanism.

**Responsibilities**

- Append `RuntimeEvent` entries.
- Store or reference payloads needed to inspect request handling, plan lifecycle, task execution, context construction, model-backed action execution, action execution, produced material, policy/authorization outcomes, failures, correction, invalidation, supersession, rollback, deletion, detachment, knowledge candidates, and learning candidates.
- Preserve inspectability and recoverability.

**Boundaries**

- Not a general logging framework.
- Not a separate reasoning actor.
- Not a knowledge repository by itself.
- Does not authorize what happened.
- Does not make recorded material true.

**Known attributes**

- `journalId: UUID`

Storage schema and query model are not fixed here.

**Known operations**

`append(event: RuntimeEvent): void`

- Precondition: `event` has stable identity and represents a runtime-relevant event.
- Postcondition: the event is durably appended without mutating previous events.

---

### 6.26 RuntimeEvent

**Description**  
Append-only record of something relevant that happened in the runtime.

**Responsibilities**

- Record request receipt, target resolution, plan creation, scheduling, task transition, context construction, boundary outcome, action execution, model-backed execution, artifact creation, response production, failure, correction, rollback, invalidation, supersession, deletion, detachment, knowledge-candidate transition, or learning-candidate transition.
- Preserve enough evidence for inspection and recovery.
- Link affected runtime objects and payload references.

**Boundaries**

- Does not authorize what happened.
- Does not execute behavior.
- Does not make generated, retrieved, or user-provided material true.
- Does not replace domain objects.

**Known attributes**

- `eventId: UUID`
- `occurredAt: Instant`
- `eventType: Text`
- `actorRef: Optional<Ref<?>>`
- `affectedRef: Optional<Ref<?>>`
- `summary: Text`
- `payloadRef: Optional<Ref<?>>`

The event taxonomy is not fixed yet, but model-backed action execution must be representable here without a separate inference-record class.

**Known operations**

- None fixed.

---

## 7. Inference Handling Rule

Inference is not currently represented by a separate output class or separate execution-record class.

A model-backed execution is a `ModelAction` execution. It returns ordinary produced material through `AgentResponse`, usually referencing one or more `ReasoningArtifact` objects when materialized. Runtime evidence for the model-backed execution is recorded through `RuntimeEvent`.

The runtime event evidence for model-backed execution must be sufficient to inspect:

- action identity;
- model binding identity;
- context bundle reference;
- execution timing/status;
- failure material if execution failed;
- produced artifact or material reference;
- relevant resource/cost information when available;
- local or authorized-remote execution boundary when relevant.

Generated material is not truth, knowledge, memory, behavior, routing, policy, action authority, or learning by itself.

---

## 8. Knowledge Handling Rule

MADRE uses a broad, uncertainty-aware knowledge model.

`KnowledgeRecord` is not restricted to verified facts. It may represent:

- user-provided material;
- inferred user preference;
- generated summary;
- extracted concept;
- imported document material;
- web-retrieved research result;
- fictional world detail;
- style reference;
- procedural note;
- hypothesis;
- correction;
- domain evidence;
- personal memory;
- system-local working belief.

The important distinction is not origin. The important distinction is lifecycle and permitted use.

`ReasoningArtifact` answers: what material was produced, captured, exported, displayed, or stored as content?

`KnowledgeRecord` answers: what may this module later treat as known material, under which provenance, scope, sensitivity, intended use, truth level, authority, confidence, correction, and lifecycle rules?

A `ReasoningArtifact` may become a source for a `KnowledgeRecord`. A `KnowledgeRecord` may reference a `ReasoningArtifact`. But `KnowledgeRecord` is not modeled as a subclass of `ReasoningArtifact` in this baseline.

---

## 9. Boundary Handling Rule

The class map must not hide the boundary design problem behind vague fields.

Avoid adding attributes such as `risk: Boolean`, `approved: Boolean`, `policyChecked: Boolean`, or generic text-only boundary containers. Those do not encode the architecture.

The accepted design direction is:

- each module, agent, workflow, routine, and action must expose immutable capability/boundary metadata, while preserving their different lifecycle semantics: workflows generate/refine plan tasks; routines and actions execute concrete invocations;
- boundary compatibility must distinguish planning-time compatibility for Workflow application from execution-time compatibility for AgentRoutine, AgentAction, and ModelAction invocation;
- request and task execution must carry accumulated effective constraints;
- concrete invocation must be constructible only when target capability and accumulated constraints are compatible;
- execution APIs should require the constructed authorization context rather than raw unchecked data;
- runtime trace must record the boundary outcome.

The exact class structure remains open and should be the next deep design discussion before finalizing `ReasoningTask`, `AgentRequest`, or `AgentAction`.

---

## 10. Naming Rules

Use the following names consistently:

- `AgentAction` for executable capabilities.
- `ModelAction` for inference-backed executable capabilities.
- `ReasoningRequest` for module-level reasoning/delegation needs.
- `AgentRequest` for concrete agent/routine/action invocation.
- `ReasoningArtifact` for materialized produced or captured material.
- `KnowledgeRecord` for active retained module knowledge.
- `KnowledgeCandidate` for pending knowledge-boundary changes.
- `LearningCandidate` for pending evaluated module improvements.
- `RuntimeEvent` for append-only runtime facts.
- `RuntimeJournal` for the append-only runtime trace mechanism.

Do not introduce alternative names for these concepts without a class-map review.

---

## 11. Open Design Questions for the Next Review

These questions are deliberately left open. They should be solved class by class, not by adding speculative fields everywhere.

1. What exact structure enforces boundary compatibility by construction?
2. What minimum execution-authorization object or context is needed before `AgentAction.execute` can be called?
3. Which `RuntimeEvent` attributes are mandatory for inspectability without becoming a full database schema?
4. Which `ReasoningPlan` transitions are stable enough to become operations?
5. Which `ReasoningTask` fields are truly needed before execution selection?
6. Which live-interaction operations belong to `SystemAgent` and which belong to access-surface adapters?
7. How should module-scoped storage access be represented without turning storage implementation into domain architecture?
8. What is the minimum taxonomy for `KnowledgeRecord.truthLevel`, `truthAuthority`, `confidence`, `intendedUse`, and `lifecycleState`?
9. What exact metadata must `ModelBinding` expose for model agnosticism and reproducibility without creating an inference class family?
10. Which operations need formal preconditions and postconditions before Java implementation?

---

## 12. Minimal Validation Checklist

A future class-map edit is acceptable only if it preserves these properties:

- The kernel remains deterministic coordination, not reasoning.
- The kernel target resolution remains explicit-target-or-Core-Module fallback, not semantic routing.
- Modules own reasoning, knowledge, agents, workflows, actions, and local governance.
- The Main Agent role is held by one module-owned `SystemAgent`.
- `SystemAgent` is module-bound, not globally authoritative.
- `ComplexAgent` lane behavior does not increase authority.
- `TwinAgent` guarantees checked response behavior when required.
- `ReasoningTask` does not contain a prebuilt `AgentRequest` before execution selection.
- Model-backed execution is represented as `ModelAction` plus `RuntimeEvent` evidence and ordinary produced material.
- Produced material does not become knowledge automatically.
- Knowledge can be uncertain and may originate from user input, inference, retrieval, import, extraction, or fiction.
- `KnowledgeRecord` is not factual truth by default and is not learning by itself.
- `LearningCandidate` remains inactive until promoted.
- Boundary enforcement is not reduced to a vague policy check or overrideable method.
- Access surfaces do not become runtime authority.
- Storage mechanisms remain implementation-defined until a stable domain boundary is proven.
