# MADRE Runtime Classmap v2

State: draft

This classmap defines the runtime domain model at class level. It is not a
Java implementation plan, persistence schema, package map, or API reference.
Classes are included only when they carry a domain responsibility that the
runtime needs in order to preserve modularity, policy boundaries, traceability,
and model-agnostic execution.

## Class Discipline

- Actors receive requests.
- Actors use actions.
- Actions produce artifacts.
- Requests carry intent and execution context, but do not grant authority.
- Artifacts carry produced material or runtime state, but do not become truth,
  knowledge, learning, or policy without explicit handling.
- Manifestos describe scope, boundaries, inputs, outputs, and risk. They are
  used for registry filtering and policy matching before actors, actions,
  workflows, or artifacts are exposed across boundaries.
- `CoreModule` and `moduleManager` are runtime roles, not classes.

## Roles

### CoreModule

Kernel-assigned role held by one registered `ReasoningModule`.

Responsibilities:

- Receive ordinary interaction when no target module is specified.
- Hold the most restrictive user-facing boundary for sensitive configuration,
  foreground interaction, and first-lane planning.
- Route ordinary user intent into governed reasoning without becoming the
  kernel.

Boundary:

- Not a class.
- Not a special module subtype.
- Does not widen authority over other modules.

### moduleManager

Module role held by one module-bound `SystemAgent`.

Responsibilities:

- Provide the module's live interaction, meta-query, help, planning, and
  orchestration behavior.
- Operate with module-scoped knowledge and continuity records.

Boundary:

- Not a separate class.
- Replaces the older `MainAgent` role.
- Does not make reusable agents module-owned.

## Shared Descriptors

### MADREManifesto

Shared descriptor for runtime objects that must expose purpose and boundaries.

Minimal attributes:

- `manifestoId`
- `name`
- `description`
- `tags`
- `scope`
- `privacyLevel`
- `riskLevel`
- `authorityBoundary`
- `requiredParameters`

Minimal operations:

- `matches(otherManifesto)`: returns whether this object can be exposed to the
  requester described by another manifesto.
- `restrictedBy(childManifestos)`: returns the conservative boundary produced
  by owned or nested manifestos.

Boundary:

- Does not execute behavior.
- Does not grant authority by itself.
- Does not replace `PolicyDecision`.

### ActorManifesto

Manifesto specialization for actors and actor-like workflows.

Minimal attributes:

- `acceptedRequests`
- `availableActionRefs`
- `availableWorkflowRefs`
- `nestedManifestos`

Minimal operations:

- `exposesTo(requesterManifesto)`: returns the actor capabilities visible to a
  requester after boundary filtering.

Boundary:

- Describes actor exposure; it does not perform routing or execution.

### ActionManifesto

Manifesto specialization for executable capabilities.

Minimal attributes:

- `inputTypes`
- `outputTypes`
- `sideEffects`
- `recoveryExpectations`
- `modelUse`

Minimal operations:

- `allowsInvocation(requestManifesto, contextManifesto)`: returns whether the
  action may be invoked for the given request and context boundary.

Boundary:

- Describes invocation constraints; it does not execute the action.

### ArtifactManifesto

Manifesto specialization for produced material and mutable runtime artifacts.

Minimal attributes:

- `artifactType`
- `scope`
- `sensitivity`
- `lifecycle`
- `accessBoundary`

Minimal operations:

- `canBeReadBy(requesterManifesto)`: returns whether the artifact may be
  exposed to a requester.

Boundary:

- Describes artifact handling; it does not make produced material authoritative.

## Actors

### MADREKernel

Deterministic runtime coordinator for host-level resources, registries,
routing, scheduling, policy gates, and trace.

Minimal attributes:

- `moduleRegistry`
- `agentRegistry`
- `workflowRegistry`
- `actionRegistry`
- `modelBindings`
- `storageBindings`
- `coreModuleRef`
- `planQueue`
- `journal`

Minimal operations:

- `registerActor(actor)`: registers a module, agent, or workflow actor.
- `registerAction(action)`: registers an executable capability.
- `filteredRegistryView(requesterManifesto)`: returns only compatible actors,
  workflows, actions, bindings, and artifacts.
- `route(request)`: delivers a `ReasoningRequest` to its target module or to
  the `CoreModule`.
- `schedule(plan)`: queues a `ReasoningPlan` for delayed execution.
- `assignResources(plan)`: grants one execution opportunity with bounded
  resources.
- `record(event)`: appends runtime trace material through `RuntimeJournal`.

Boundary:

- Does not reason, plan module work, execute actions, perform inference, or own
  module knowledge truth.
- Does not create delegated intermodule requests; module actors create them
  through governed planning or orchestration.

### MADREActor

Abstract base for runtime objects that can receive requests or coordinate
execution.

Minimal attributes:

- `actorId`
- `manifesto`

Minimal operations:

- `accepts(request)`: returns whether the actor can receive a request.
- `handle(request)`: produces an artifact, response, plan, delegated request,
  block, or failure.

Boundary:

- Does not imply knowledge ownership.
- Does not bypass kernel registry filtering, policy, context, or trace.

### ReasoningModule

Bounded reasoning scope registered by the kernel.

Minimal attributes:

- `moduleId`
- `manifesto`
- `moduleManager`
- `knowledge`
- `knowledgeCandidates`
- `learningCandidates`
- `conversationRecords`

Minimal operations:

- `process(request)`: handles a routed `ReasoningRequest` through direct
  response, clarification, block, failure, or plan creation.
- `advance(plan)`: advances a scheduled plan by invoking the module manager or
  assigned orchestration actor.
- `createDelegatedRequest(plan, task, targetModuleRef)`: creates a minimized
  `ReasoningRequest` for another module.
- `createNextRequest(plan)`: creates the next bounded request for continued
  plan execution.

Boundary:

- Does not directly own reusable `MADREAgent`, `AgentAction`, or `Workflow`
  objects.
- Access to reusable actors and actions comes from the kernel's filtered
  registry view.
- Owns only its own knowledge, candidates, and conversation continuity.

### SystemAgent

Module-bound complex actor used by a module in the `moduleManager` role.

Minimal attributes:

- `agentId`
- `manifesto`
- `moduleRef`
- `liveInteractionRoutine`
- `metaQueryRoutine`
- `planningRoutine`
- `orchestrationRoutine`
- `evaluationRoutine`

Minimal operations:

- `liveInteraction(request)`: supports continuous foreground interaction
  through the owning module.
- `metaQuery(request)`: answers module state, help, capability, and
  configuration questions within the module boundary.
- `plan(request)`: materializes a `ReasoningPlan` when deeper work is needed.
- `orchestrate(plan)`: selects and advances ready plan tasks.
- `proposeKnowledge(candidateMaterial)`: proposes module-scoped knowledge
  handling when allowed.

Boundary:

- Bound forever to one module.
- Cannot be cloned or moved to another module.
- May be replaced as `moduleManager`, but remains available only within its
  bound module boundary.

### MADREAgent

Abstract reusable execution actor registered by the kernel and exposed to
modules through manifesto matching.

Minimal attributes:

- `agentId`
- `manifesto`
- `defaultRoutine`
- `actionRefs`
- `modelSelectionRules`

Minimal operations:

- `execute(request)`: handles an `AgentRequest` and returns an `Artifact` or
  `AgentResponse`.
- `cloneForInvocation()`: creates a decontextualized runtime copy for one
  authorized invocation.

Boundary:

- Does not store module-sensitive knowledge.
- Does not carry module memory across modules.
- Does not widen its own model, action, or artifact authority.

### SimpleAgent

Reusable `MADREAgent` profile for direct execution.

Minimal attributes:

- `defaultRoutine`

Minimal operations:

- `execute(request)`: runs a selected routine or action and returns result,
  refusal, block, or failure material.

Boundary:

- Does not evaluate its own output.
- Does not update its own behavior.
- Does not hold `moduleManager`.

### TwinAgent

Reusable `MADREAgent` profile for review and evaluation over produced material.

Minimal attributes:

- `evaluationRoutine`
- `learningRoutine`

Minimal operations:

- `evaluate(artifact, criteria)`: returns evaluation material.
- `proposeLearning(evidence)`: creates a `LearningCandidate` when the module
  boundary permits it.

Boundary:

- Evaluation does not authorize action, promote learning, or become policy.

### ComplexAgent

Reusable `MADREAgent` profile for reflective and self-evaluating execution.

Minimal attributes:

- `reasoningRoutine`
- `reflectionRoutine`
- `evaluationRoutine`
- `learningRoutine`

Minimal operations:

- `reason(request)`: performs deeper bounded reasoning.
- `reflect(artifact)`: reviews recent output or execution evidence.
- `proposeLearning(evidence)`: creates a learning proposal for authorized
  evaluation.

Boundary:

- Focused on reasoning and reflection, not live module interaction.
- Does not gain module-scoped knowledge access unless invoked through an
  authorized module boundary.

### Workflow

Actor-like reusable intermodule coordination blueprint.

Minimal attributes:

- `workflowId`
- `manifesto`
- `entryRequestType`
- `taskTemplates`
- `artifactDependencies`
- `validationGates`

Minimal operations:

- `instantiate(request)`: creates plan tasks and artifact dependencies for a
  `ReasoningPlan`.
- `nextTasks(plan)`: identifies ready workflow-derived tasks.

Boundary:

- Does not execute by itself.
- Does not own module knowledge.
- Does not bypass module routing, manifesto matching, or plan orchestration.

## Actions

### AgentAction

Minimal executable capability used by an actor.

Minimal attributes:

- `actionId`
- `manifesto`

Minimal operations:

- `run(request)`: executes one bounded capability and returns artifacts,
  response, block, or failure.

Boundary:

- Does not grant authority beyond the invoking actor, request, context, and
  policy decision.

### DeterministicAction

`AgentAction` specialization for non-model execution.

Minimal attributes:

- `executorRef`
- `inputContract`
- `outputContract`

Minimal operations:

- `run(request)`: performs deterministic or integration-backed execution.

Boundary:

- Side effects must be declared in `ActionManifesto`.

### ModelInferenceAction

`AgentAction` specialization for governed model inference.

Minimal attributes:

- `bindingRef`
- `profileRef`
- `promptTemplateRef`

Minimal operations:

- `run(request)`: prepares governed context and invokes an allowed inference
  backend.

Boundary:

- Does not make generated output truth, knowledge, policy, memory, routing, or
  authority.

### AgentRoutine

Sequential compound action with no dependency graph.

Minimal attributes:

- `routineId`
- `manifesto`
- `actions`

Minimal operations:

- `run(request)`: executes contained actions in order while preserving the
  same invocation boundary.

Boundary:

- Does not model complex intermodule dependencies; use `Workflow` for that.

### ReasoningTask

Plan-local execution occurrence.

Minimal attributes:

- `taskId`
- `objective`
- `targetKind`
- `targetRef`
- `inputRef`
- `expectedArtifact`
- `dependsOn`
- `status`
- `resultRef`
- `failure`
- `delegatedRequestRef`

Minimal operations:

- `canStart(completedTasks)`: returns whether dependencies are satisfied.

Boundary:

- Does not execute itself.
- Does not own its state transitions independently; state is controlled through
  `ReasoningPlan` and the assigned orchestrator.
- Does not redefine the reusable actor, action, or workflow it targets.

## Requests

### ReasoningRequest

Actor-level request routed by the kernel.

Minimal attributes:

- `requestId`
- `objective`
- `origin`
- `targetModuleRef`
- `contextRefs`
- `constraints`
- `priority`
- `sensitivity`
- `expectedResult`
- `requestChain`
- `createdAt`

Minimal operations:

- `validate()`: returns whether the request is routable, invalid, or needs
  clarification.

Boundary:

- Routed by the kernel, not scheduled directly.
- Does not build plans, execute work, choose modules by itself, or reveal a
  source plan's internal state to another module.

### AgentRequest

Action-level invocation context for one plan step, agent execution, routine, or
action.

Minimal attributes:

- `requestId`
- `objective`
- `sourceReasoningRequestRef`
- `planRef`
- `taskRef`
- `actorRef`
- `actionRef`
- `contextBundle`
- `constraints`
- `interactionState`

Minimal operations:

- `appendResult(artifactRef)`: records intermediate result material for the
  current invocation.
- `complete(status)`: marks the invocation outcome.

Boundary:

- Shared execution context, not durable knowledge.
- Does not grant authority beyond the selected actor/action and policy boundary.

## Artifacts

### Artifact

Base class for generated material or runtime state produced by reasoning.

Minimal attributes:

- `artifactId`
- `manifesto`
- `producerRef`
- `sourceRequestRef`
- `contentRef`
- `status`
- `createdAt`

Minimal operations:

- `exposeTo(requesterManifesto)`: returns whether the artifact may be read.

Boundary:

- Not truth, knowledge, learning, or policy by default.

### ReasoningPlan

Mutable artifact scheduled by the kernel and advanced by a module orchestrator.

Minimal attributes:

- `planId`
- `manifesto`
- `sourceRequestRef`
- `ownerModuleRef`
- `orchestratorRef`
- `tasks`
- `artifactRefs`
- `status`
- `planNotes`
- `createdAt`
- `updatedAt`

Minimal operations:

- `addTask(task)`: adds a plan-local task.
- `transitionTask(taskRef, status, evidenceRef)`: updates a task state.
- `readyTasks()`: returns tasks available for orchestration.
- `recordArtifact(artifactRef)`: links produced material to the plan.

Boundary:

- Does not reason or execute by itself.
- Does not invoke actors, actions, workflows, or models.
- Does not own module knowledge.

### AgentResponse

Simple artifact returned by an actor or action.

Minimal attributes:

- `responseId`
- `sourceRequestRef`
- `status`
- `messageRef`
- `artifactRefs`
- `delegatedRequestRefs`

Minimal operations:

- `withArtifact(artifactRef)`: links produced material.
- `withDelegatedRequest(requestRef)`: links created follow-up or intermodule
  request.

Boundary:

- Does not promote returned material into knowledge or learning.

### KnowledgeRecord

Module-owned known material.

Minimal attributes:

- `recordId`
- `manifesto`
- `moduleRef`
- `contentRef`
- `provenance`
- `truthLevel`
- `truthAuthority`
- `lifecycle`

Minimal operations:

- `supersedeBy(recordRef)`: records correction or replacement.

Boundary:

- Known material is not factual truth by default.
- Belongs to one module boundary.

### KnowledgeCandidate

Pending change to a module knowledge boundary.

Minimal attributes:

- `candidateId`
- `moduleRef`
- `proposedChange`
- `materialRef`
- `evidenceRefs`
- `status`

Minimal operations:

- `evaluate(evidenceRef)`: records evaluation evidence.

Boundary:

- Does not alter module knowledge until explicitly accepted.

### LearningCandidate

Evaluated proposal to improve behavior, evaluation, routing, routines,
workflows, context selection, or inference selection.

Minimal attributes:

- `candidateId`
- `moduleRef`
- `proposedImprovement`
- `evidenceRefs`
- `evaluationRef`
- `status`

Minimal operations:

- `promote(decision)`: applies the proposal only after authorized evaluation.
- `reject(reason)`: closes the proposal without changing behavior.

Boundary:

- Saving records or knowledge is not learning.
- Generated material does not become learning automatically.

### ConversationRecord

Retained interaction material for continuity, traceability, or later
evaluation.

Minimal attributes:

- `recordId`
- `moduleRef`
- `sessionRef`
- `contentRef`
- `provenance`
- `lifecycle`

Minimal operations:

- `candidateForKnowledge(reason)`: creates a `KnowledgeCandidate` when explicit
  handling is required.

Boundary:

- Not knowledge by default.
- Not portable outside its module boundary without governed handling.

### InferenceRecord

Passive record of one bounded inference execution.

Minimal attributes:

- `recordId`
- `actionRef`
- `bindingRef`
- `profileRef`
- `backendRef`
- `inputContextRef`
- `outputArtifactRef`
- `status`
- `timing`

Minimal operations:

- `asArtifact()`: returns the generated output artifact reference.

Boundary:

- Records inference execution; does not validate truth or authorize downstream
  use.

## Governance And Support Records

### ContextBundle

Governed context prepared for one request, action, workflow, or inference.

Minimal attributes:

- `bundleId`
- `sourceRefs`
- `scope`
- `classification`
- `purpose`
- `revocationRefs`

Minimal operations:

- `minimize(targetManifesto)`: returns a reduced bundle for a target boundary.
- `classify()`: updates classification metadata.

Boundary:

- Included source material remains data, not instruction or truth.

### PolicyDecision

Explicit allow, block, or authorization-required outcome.

Minimal attributes:

- `decisionId`
- `outcome`
- `reason`
- `affectedRef`
- `authorityBoundary`
- `eventRef`

Minimal operations:

- `requiresAuthorization()`: returns whether human or higher authority is
  needed.

Boundary:

- Cannot be created or widened by generated material alone.

### RuntimeEvent

Append-only record of a runtime transition, decision, failure, correction, or
recovery step.

Minimal attributes:

- `eventId`
- `eventType`
- `occurredAt`
- `actorRef`
- `workRef`
- `payloadRef`

Minimal operations:

- `relatesTo(ref)`: returns whether the event concerns the referenced object.

Boundary:

- History record, not authority or current state by itself.

### RuntimeJournal

Local trace mechanism for runtime events and related payload references.

Minimal attributes:

- `eventRefs`
- `workRefs`

Minimal operations:

- `append(event)`: stores an append-only event reference.
- `trace(ref)`: returns events related to a runtime object.

Boundary:

- Not a general repository, database abstraction, or policy engine.

### ModelBinding

Allowed relationship between model references, profiles, and inference
backends.

Minimal attributes:

- `bindingId`
- `modelRefs`
- `profileRefs`
- `backendRefs`
- `constraints`

Minimal operations:

- `allows(profileRef, backendRef)`: returns whether a model inference action may
  use that pair.

Boundary:

- Constrains inference options; it is not inference execution authority by
  itself.

### InferenceProfile

Bounded recipe for one kind of inference execution.

Minimal attributes:

- `profileId`
- `modelRef`
- `backendType`
- `configuration`
- `resourceConstraints`

Minimal operations:

- `fits(requestedCapability)`: returns whether the profile can satisfy an
  inference need.

Boundary:

- Not a model and not provider-specific runtime authority.

### InferenceBackend

Replaceable execution boundary for local or authorized remote inference.

Minimal attributes:

- `backendId`
- `backendType`
- `capabilities`
- `locality`

Minimal operations:

- `execute(contextBundle, profile)`: returns an `InferenceRecord`.

Boundary:

- Executes inference only through an allowed `ModelBinding` and governed
  `ModelInferenceAction`.

## Structural Relations

- `MADREKernel` registers `ReasoningModule`, reusable `MADREAgent`,
  `Workflow`, and `AgentAction` objects.
- `MADREKernel` assigns exactly one `ReasoningModule` as `CoreModule`.
- `ReasoningModule` owns one `SystemAgent` in the `moduleManager` role.
- `ReasoningModule` owns `KnowledgeRecord`, `KnowledgeCandidate`,
  `LearningCandidate`, and `ConversationRecord` objects in its boundary.
- `ReasoningModule` receives available reusable agents, workflows, actions, and
  bindings through `MADREKernel.filteredRegistryView`.
- `Workflow` materializes `ReasoningTask` objects inside a `ReasoningPlan`.
- `ReasoningPlan` is scheduled by `MADREKernel` and advanced by the owning
  module's orchestrator.
- `ReasoningTask` targets an actor, workflow, action, or delegated request.
- `AgentRequest` invokes a `MADREAgent`, `AgentRoutine`, or `AgentAction`.
- `ModelInferenceAction` uses `ModelBinding`, `InferenceProfile`, and
  `InferenceBackend` to produce `InferenceRecord`.
- `AgentResponse`, `ReasoningPlan`, `KnowledgeRecord`, `KnowledgeCandidate`,
  `LearningCandidate`, `ConversationRecord`, and `InferenceRecord` are
  artifacts or artifact-like records with explicit handling boundaries.

## Runtime Chain

1. An access surface creates a `ReasoningRequest`.
2. `MADREKernel.route` delivers it to an explicit target module or the
   `CoreModule`.
3. `ReasoningModule.process` answers directly, asks for clarification, blocks,
   fails, or creates a `ReasoningPlan`.
4. `MADREKernel.schedule` queues the plan.
5. `MADREKernel.assignResources` grants an execution opportunity.
6. The module's `SystemAgent` or assigned orchestrator advances ready
   `ReasoningTask` objects.
7. Tasks create `AgentRequest` objects for reusable agents, routines, actions,
   workflows, or delegated module requests.
8. Actions produce `Artifact` objects and `AgentResponse` objects.
9. Significant transitions are recorded as `RuntimeEvent` objects in
   `RuntimeJournal`.
10. Produced material becomes knowledge or learning only through explicit
    candidate and promotion boundaries.
