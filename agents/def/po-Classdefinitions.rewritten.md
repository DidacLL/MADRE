# MADRE Runtime Class Map

- **Author:** Product Owner
- **State:** disposable
- **Scope:** Durable runtime structure draft. This document describes MADRE product/runtime concepts, not MADREdev process.

## Purpose

This document records the current class-level map for MADRE’s runtime structure. It is intended to keep implementation vocabulary aligned with MADRE’s architecture: local-first runtime coordination, module-bounded reasoning, model-bounded agents, governed context, explicit actions, module-owned knowledge, evaluated learning, and traceable runtime records.

## Runtime Coordination

### MadreKernel

Deterministic runtime coordinator.

Responsibilities:
- Register `ReasoningModule` objects.
- Assign the default interaction module.
- Route local requests to modules.
- Schedule delayed work.
- Coordinate runtime resources.
- Record runtime events through `RuntimeJournal`.

Boundary:
- Does not reason.
- Does not prompt directly.
- Does not learn.
- Does not own knowledge truth.
- Does not execute inference directly.

### WorkRecord

Durable record of work being performed.

Responsibilities:
- Track delayed-work lifecycle.
- Link work to plan, module, request, runtime events, and recovery references.
- Preserve recoverability and inspectability for work execution.

Boundary:
- Does not represent plan state.
- Does not represent module state.
- Does not represent knowledge state.
- Does not represent candidate state.
- Does not represent learning state.
- Does not represent system health.

### RuntimeEvent

Append-only record of runtime activity.

Examples of recorded activity:
- Request received.
- Plan created or refined.
- Work scheduled.
- Context built.
- Policy decision made.
- Action executed.
- Inference completed.
- Output produced.
- Failure recorded.
- Correction, invalidation, rollback, deletion, or detachment recorded.

### RuntimeJournal

Local runtime trace mechanism.

Responsibilities:
- Write `WorkRecord` data.
- Append `RuntimeEvent` history.
- Store or reference payloads related to work, context, inference, policy, output, and recovery.

Boundary:
- Not a repository layer.
- Not a database abstraction.
- Not a separate audit subsystem.
- Not a separate evidence subsystem.
- Not a general logging framework.

### PolicyDecision

Explicit authority decision.

Responsibilities:
- Represent allow, block, or authorization-required outcomes.
- Carry decision reason.
- Carry affected action/data class.
- Carry authority boundary.
- Link to related `RuntimeEvent`.

Boundary:
- Generated material may inform reasoning, but does not create or widen `PolicyDecision`.

## Reasoning Modules

### ReasoningModule

Governed reasoning unit.

Responsibilities:
- Own bounded reasoning scope.
- Own privacy/risk level.
- Own module knowledge boundary.
- Own module records.
- Own workflows, routines, actions, and agents.
- Own allowed model/inference scope for its agents and actions.
- Use its module-owned knowledge and learning to contribute to reasoning work.

Boundary:
- A module may be assigned as the default interaction module by kernel registration.
- Default interaction is a module role, not a separate module type.

### Default Interaction Module

Kernel registration role for the module that receives ordinary interaction first.

Boundary:
- Not a distinct class.
- Any compatible `ReasoningModule` may occupy this role.

### ModuleMainAgent

Module role held by one `MADREAgent`.

Responsibilities:
- Coordinate ordinary module operation.
- Preserve foreground interaction when the module supports it.
- Detect whether deeper reasoning, workflow execution, action execution, or delayed work is needed.

Boundary:
- Not a distinct class.
- May be fulfilled by a `ComplexAgent`.

## Plans and Execution Structure

### ReasoningPlan

Runtime plan for reasoning work.

Responsibilities:
- Carry objective, origin, target module, scope, constraints, context references, reasoning history, execution references, correction references, rollback references, and evaluation material.
- Preserve reasoning continuity across module contributions.
- Be refined by modules as they contribute to the work.
- Reference workflows, routines, or actions when useful.

Boundary:
- Does not require a separate module-assignment object.
- Does not require a separate module-plan object.
- Does not require a separate workflow-plan object.

### Workflow

Reusable module-level execution structure.

Responsibilities:
- Coordinate `AgentRequest`, `AgentRoutine`, and `AgentAction` according to module capabilities.
- Represent reusable module behavior.

Boundary:
- A workflow is a capability used by a plan.
- A workflow is not a second plan.

## Agents

### MADREAgent

Runtime actor owned by one `ReasoningModule`.

Responsibilities:
- Hold role semantics, instructions, routines, actions, allowed model binding, and module-scoped runtime state.
- Execute agent requests through routines or actions.
- Use module-owned knowledge and learning.
- Operate inside the owning module’s scope, risk boundary, and model/inference boundary.

Boundary:
- The reusable element is the agent class/profile/definition.
- A created agent object belongs to one module boundary.
- Knowledge and learning remain module-owned.
- Reusing an agent definition elsewhere creates a new module-scoped runtime actor unless an explicit transfer mechanism exists.

### SimpleAgent

`MADREAgent` specialization or profile for direct task execution.

### TwinAgent

`MADREAgent` specialization or profile with review/evaluation behavior over a produced result.

### ComplexAgent

`MADREAgent` specialization or profile capable of foreground interaction, deeper reasoning supervision, and reflective/evaluative work.

Boundary:
- May fulfill the `ModuleMainAgent` role.
- Does not imply `ModuleMainAgent`.

### ModelBinding

Allowed inference configuration assigned to a `MADREAgent` or to one of its model-backed actions.

Responsibilities:
- Constrain which `InferenceProfile`, model files, and inference backends the agent may use.
- Preserve data security, module scope, reproducibility, and locality constraints.

## Agent Interaction and Actions

### AgentRequest

Concrete request sent to an agent.

Carries:
- Objective.
- Scope.
- Context.
- Constraints.
- Selected routine/action when applicable.
- Caller/module information.
- Policy-relevant metadata.

### AgentResponse

Concrete response from an agent.

Carries:
- Result material.
- Refusal, block, or failure information.
- Produced records.
- References to `RuntimeEvent`.

### AgentRoutine

Reusable agent-level recipe.

Responsibilities:
- Describe what it does.
- Define when it should be used.
- Define accepted request shape.
- Coordinate actions or reasoning steps.

### AgentAction

Executable capability owned by an agent or module.

Responsibilities:
- Execute deterministic, inference-backed, or integration-backed behavior.
- Operate under module and kernel authority.
- Return material suitable for `AgentResponse`, `RuntimeEvent`, records, or later evaluation.

### ModelAction

`AgentAction` that uses inference.

Responsibilities:
- Prepare inference input from `AgentRequest`, `ContextBundle`, and `InferenceProfile`.
- Use the agent’s allowed `ModelBinding`.
- Execute through `InferenceBackend`.
- Return `InferenceOutput`.

Boundary:
- Does not create model authority.
- Does not bypass module or kernel policy.

## Inference

### InferenceProfile

Execution recipe for running a model file through an inference backend.

May define:
- Model path.
- Backend type.
- Executable path.
- Context size.
- Sampling parameters.
- Seed.
- Timeout.
- Resource constraints.

Boundary:
- Not the model itself.
- Does not make MADRE dependent on one model.

### InferenceBackend

Replaceable execution boundary for inference software.

Responsibilities:
- Execute local or authorized inference engines.
- Hide the concrete mechanism used to run inference.

### LlamaCppCliBackend

`InferenceBackend` implementation that invokes llama.cpp through local process execution.

Boundary:
- llama.cpp is an inference engine boundary.
- llama.cpp is not a MADRE model.
- llama.cpp is not MADRE runtime authority.

### InferenceRecord

Passive record of one inference execution.

Captures:
- Inference profile used.
- Backend used.
- Command/config metadata.
- Timing.
- Exit status.
- Output reference.

### InferenceOutput

Generated material produced by an `InferenceRecord`.

Possible handling:
- Retain as conversation material.
- Store as known material.
- Evaluate as input for learning.
- Discard.
- Transform into another runtime artifact.

Boundary:
- Not truth by itself.
- Not policy by itself.
- Not memory by itself.
- Not knowledge by itself.
- Not behavior by itself.
- Not candidate by itself.

## Context

### ContextSource

Descriptor of source material available for context construction.

Carries:
- Provenance.
- Origin.
- Scope.
- Sensitivity.
- Trust classification.
- Source reference.

### ContextBundle

Governed context prepared for an `AgentRequest`, `AgentRoutine`, `AgentAction`, or inference execution.

Responsibilities:
- Select source material.
- Minimize context.
- Preserve scope.
- Preserve classification.
- Preserve source/provenance references.

Boundary:
- Source material does not become instruction by being included in context.

## Knowledge and Learning

### KnowledgeRecord

Material known by a module.

Carries:
- Content or content reference.
- Provenance.
- Scope.
- Sensitivity.
- Intended use.
- Content kind.
- Truth level.
- Truth authority.
- Correction semantics.
- Lifecycle metadata.

Behavior:
- Can be retrieved, transformed, compared, reasoned over, or used as context according to metadata.
- Can contain factual, non-factual, stylistic, procedural, speculative, generated, imported, user-provided, or research-derived material.
- Can be used without being treated as factual truth.

### KnowledgeCandidate

Pending change to a module knowledge boundary.

May represent:
- Inclusion.
- Ownership transfer.
- Reclassification.
- Promotion.
- Trust-level change.

Boundary:
- Accepted known material is `KnowledgeRecord`.
- Ordinary stored material does not remain a candidate merely because its truth level is limited.

### LearningCandidate

Proposed improvement to module behavior or module evaluation.

May affect:
- Behavior.
- Evaluation.
- Routing.
- Routines.
- Workflows.
- Context selection.
- Trust calibration.
- Model/inference profile selection.
- Other module functions.

May derive from:
- Evaluated records.
- Outcomes.
- Corrections.
- User feedback.
- Workflow efficiency.
- Repeated failures.
- Repeated context needs.
- Module rejection.
- Execution traces.
- Evaluated generated material.
- Repeated patterns.

Boundary:
- Saving material as `KnowledgeRecord` is not learning by itself.
- Learning requires evaluated improvement pressure and a proposed module change.

### ConversationRecord

Interaction material retained for continuity, traceability, or later evaluation.

Behavior:
- Supports conversational continuity.
- May inform later knowledge or learning handling.
- Selected material may become `KnowledgeRecord` through explicit runtime, module, or user handling.

Boundary:
- Not knowledge by default.

## Structural Relations

```text
MadreKernel registers ReasoningModules.
A ReasoningModule assigned as default interaction module receives ordinary interaction first.
Default interaction is a kernel registration role.

MadreKernel schedules WorkRecords.
WorkRecord represents delayed work lifecycle.
WorkRecord does not represent plan state, module state, knowledge state, candidate state, learning state, or system health.

MadreKernel records RuntimeEvents through RuntimeJournal.
RuntimeJournal records work state, runtime history, and referenced payloads.
RuntimeJournal does not imply separate audit, work, evidence, repository, or storage subsystems.

ReasoningModule owns MADREAgents.
ReasoningModule owns module knowledge, learning, records, workflows, routines, actions, privacy/risk level, and allowed model/inference scope.

ModuleMainAgent is a role held by one MADREAgent in a ReasoningModule.
ComplexAgent may fulfill the ModuleMainAgent role.
ComplexAgent does not imply ModuleMainAgent.

MADREAgent belongs to one ReasoningModule boundary.
MADREAgent uses module-owned knowledge and learning.
MADREAgent does not carry module knowledge or learning outside its module.

MADREAgent has allowed ModelBinding.
ModelBinding constrains model/profile/backend access.

SimpleAgent, TwinAgent, and ComplexAgent describe agent capability profiles.
They do not define knowledge ownership.
They do not define module ownership.
They do not define unrestricted model access.

ComplexAgent creates or refines ReasoningPlan.
ReasoningPlan carries target module, objective, scope, constraints, refinement history, execution references, correction references, rollback references, and evaluation material.
ReasoningPlan may select or reference Workflow.
Workflow contributes to ReasoningPlan execution.
Workflow is not a second plan.

Workflow may send AgentRequests.
AgentRequest is the concrete invocation context.
AgentRoutine is reusable behavior.
AgentRequest invokes AgentRoutine or AgentAction.
AgentRoutine may execute AgentActions.
AgentRequest may directly target AgentAction when no routine decomposition is needed.

AgentAction is executable capability.
ModelAction is an AgentAction.
ModelAction uses InferenceBackend through allowed ModelBinding.
ModelAction does not create a model authority layer.

InferenceProfile describes how inference is executed.
InferenceProfile is not the model.
InferenceProfile may reference user-provided model files.

InferenceBackend executes inference.
LlamaCppCliBackend is one InferenceBackend.
llama.cpp is an inference engine boundary, not a MADRE model and not MADRE runtime authority.

InferenceRecord records one inference execution.
InferenceOutput is the generated material from that execution.
InferenceOutput may become ConversationRecord.
InferenceOutput may become KnowledgeRecord through explicit handling.
InferenceOutput may contribute to LearningCandidate through evaluation.
InferenceOutput may be discarded.

ContextSource describes available source material.
ContextBundle is selected and minimized context for one request, routine, action, or inference execution.
Source material does not become instruction by inclusion in ContextBundle.

PolicyDecision gates AgentAction.
PolicyDecision is recorded as RuntimeEvent.
Generated material cannot create, override, or widen PolicyDecision.

KnowledgeRecord stores material known by a module.
KnowledgeRecord carries truthLevel, truthAuthority, intendedUse, provenance, scope, sensitivity, and correction metadata.
KnowledgeRecord can be used without being treated as factual truth.

KnowledgeCandidate represents pending change to a module knowledge boundary.
KnowledgeCandidate may propose inclusion, ownership transfer, reclassification, promotion, or truth-level change.
KnowledgeCandidate is not required for already accepted known material.

LearningCandidate represents pending module improvement.
LearningCandidate is derived from evaluated traces, outcomes, corrections, examples, repeated patterns, or evaluated material.
LearningCandidate is not ordinary stored material.

ConversationRecord supports continuity and traceability.
ConversationRecord may inform KnowledgeRecord or LearningCandidate through explicit handling.
ConversationRecord is not knowledge by default.

RuntimeEvent is runtime history.
WorkRecord is durable work state.
ReasoningPlan is reasoning continuity.
KnowledgeRecord is known material.
LearningCandidate is proposed improvement.
PolicyDecision is authority decision.
```

## Naming and Design Discipline

```text
Use MADRE domain names: Kernel, Module, Agent, Plan, Workflow, Routine, Action, Inference, Context, Knowledge, Journal, Policy.

Use AgentRequest and AgentResponse for concrete agent interaction.

Use AgentAction for executable MADRE capabilities.

Use RuntimeEvent for recorded transitions.

Use InferenceBackend for replaceable inference execution.

Use InferenceProfile for inference execution configuration.

Use InferenceRecord for one recorded inference execution.

Use InferenceOutput for generated material.

Use KnowledgeRecord for known material.

Use LearningCandidate for proposed module improvement.

Use KnowledgeRecord.truthLevel and KnowledgeRecord.truthAuthority for factual reliability metadata.

Keep generated material, known material, learning proposals, policy decisions, work lifecycle, and runtime history as separate concepts.

Keep one RuntimeJournal unless a concrete runtime requirement justifies a stronger storage boundary.
```

## Core Runtime Chain

```text
User or system request enters through an access surface.
MadreKernel receives the request.
MadreKernel routes to the default-registered ReasoningModule.
ReasoningModule uses its ModuleMainAgent.
The agent creates or refines ReasoningPlan.
MadreKernel schedules delayed work when required.
ReasoningModule executes its contribution through Workflow, AgentRoutine, or AgentAction.
ModelAction uses InferenceBackend when inference is needed.
InferenceOutput returns as generated material.
AgentResponse returns result, refusal, block, or failure material.
ReasoningPlan and WorkRecord are updated.
RuntimeJournal records WorkRecord and RuntimeEvent.
KnowledgeRecord stores known material when explicitly handled as knowledge.
LearningCandidate proposes evaluated module improvement when learning pressure exists.
```
