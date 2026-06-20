# B4 Requests Plans Tasks And Invocations

Classes under review: `ReasoningRequest`, `ReasoningResponse`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `AgentResponse`.

## Required Context

This packet defines the flow from reasoning need to plan/task to concrete invocation.

`ReasoningRequest` is module-level reasoning/delegation need.

`ReasoningResponse` is module-level response.

`ReasoningPlan` is durable reasoning continuity when structured or delayed work is required.

`ReasoningTask` is one planned execution occurrence inside exactly one plan.

`AgentRequest` is concrete execution invocation, created after task selection.

`AgentResponse` is concrete invocation result.

`ReasoningTask` keeps task intent and selection data separate from the later concrete invocation.

## Adjacent Concepts

- Kernel resolves explicit target or CoreModule fallback.
- Module semantic delegation creates minimized `ReasoningRequest`.
- Workflow application creates/refines tasks.
- `AgentRoutine`, `AgentAction`, and `ModelAction` execution receives `AgentRequest`.
- `AgentResponse` may reference `ReasoningArtifact`.
- `RuntimeEvent` records significant transitions.

## Use-Case Pressure

- UC-B: academic writing delegation from CoreModule to CollegeTasksModule.
- UC-C: CollegeTasksModule delegates neutralized research to ResearchModule.
- UC-D: CollegeTasksModule delegates non-trivial math to MathModule.
- UC-E: LaTeX workflow expands into tasks; selected tasks invoke routines/actions.
- UC-G: delayed work and background supersession require durable plan/task state.

## `ReasoningRequest`

Review focus:

- What is the minimum needed for user-originated and module-originated requests?
- Is `objective` enough, or must scope/constraints be structured?
- How should explicit target be represented?
- Which fields preserve minimized delegation?

Boundary focus:

- module-level reasoning need
- target and scope constraints
- minimized delegation payload
- composed boundary constraints carried forward

## `ReasoningResponse`

Review focus:

- What outcome categories are stable?
- Is `outcome: Text` acceptable until taxonomy discussion?
- How should plan-created or delegation responses reference created objects?

Boundary focus:

- module-level response
- created plan/delegation references
- produced material references through governed classes

## `ReasoningPlan`

Review focus:

- What makes a plan a durable continuity aggregate?
- Are `planId`, `sourceRequest`, `ownerModule`, `tasks`, and `status` enough?
- Which transitions are stable enough as operations?
- How does a plan stay a continuity aggregate?

Boundary focus:

- durable plan state
- task collection
- status transitions
- traceable changes
- execution selected through tasks/invocations

## `ReasoningTask`

Review focus:

- What target kinds are allowed?
- How to represent workflow application tasks versus executable tasks?
- Which fields are needed before execution selection?
- How to represent dependency on delegated `ReasoningRequest` while preserving target-module boundaries?

Boundary focus:

- planned execution occurrence
- target kind
- dependencies
- accumulated constraints
- later concrete invocation creation

## `AgentRequest`

Review focus:

- What makes an invocation concrete?
- Which selected capability reference is needed?
- How does it carry context and boundary evidence through structured fields?
- Which type constraints route it to executable capability classes?

Boundary focus:

- concrete invocation context
- selected executable capability
- selected context
- boundary result/evidence reference

## `AgentResponse`

Review focus:

- What must be returned by agents/routines/actions?
- Does it need artifact refs and event refs?
- How does block/failure material differ from produced artifact?

Boundary focus:

- concrete invocation result
- artifact references
- event references
- block/failure/result material separation

## Output

Finalize the request/plan/invocation separation with operation pre/postconditions where stable.
