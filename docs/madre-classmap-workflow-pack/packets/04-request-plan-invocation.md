# Packet B4 — Requests, Plans, Tasks, and Invocations

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Classes under review:** `ReasoningRequest`, `ReasoningResponse`, `ReasoningPlan`, `ReasoningTask`, `AgentRequest`, `AgentResponse`

## 1. Required Context

This packet defines the flow from reasoning need to plan/task to concrete invocation.

`ReasoningRequest` is module-level reasoning/delegation need.

`ReasoningResponse` is module-level response.

`ReasoningPlan` is durable reasoning continuity when structured or delayed work is required.

`ReasoningTask` is one planned execution occurrence inside exactly one plan.

`AgentRequest` is concrete execution invocation, created only after task selection.

`AgentResponse` is concrete invocation result.

A `ReasoningTask` must not contain a prebuilt `AgentRequest` before execution selection.

## 2. Adjacent Concepts Required

- Kernel resolves explicit target or CoreModule fallback.
- Module semantic delegation creates minimized `ReasoningRequest`.
- Workflow application creates/refines tasks.
- AgentRoutine/AgentAction/ModelAction execution receives `AgentRequest`.
- AgentResponse may reference ReasoningArtifact.
- RuntimeEvent records significant transitions.

## 3. Use-Case Pressure

- UC-B: academic writing delegation from CoreModule to CollegeTasksModule.
- UC-C: CollegeTasksModule delegates neutralized research to ResearchModule.
- UC-D: CollegeTasksModule delegates non-trivial math to MathModule.
- UC-E: LaTeX workflow expands into tasks; selected tasks invoke routines/actions.
- UC-G: delayed work and background supersession require durable plan/task state.

## 4. Class Discussion Blocks

### `ReasoningRequest`

Review focus:

- What is the minimum needed for user-originated and module-originated requests?
- Is `objective` enough, or must scope/constraints be structured?
- How should explicit target be represented?
- What prevents delegation from carrying private module internals?

Mandatory boundaries:

- not plan;
- not scheduler;
- not action authorization;
- not concrete agent invocation.

### `ReasoningResponse`

Review focus:

- What outcome categories are stable?
- Is `outcome: Text` too weak or acceptable until taxonomy discussion?
- How should plan-created or delegation responses reference created objects?

Mandatory boundaries:

- not execution;
- not authority;
- not knowledge/learning promotion.

### `ReasoningPlan`

Review focus:

- What makes a plan a durable continuity aggregate?
- Are `planId`, `sourceRequest`, `ownerModule`, `tasks`, `status` enough?
- Which transitions are stable enough as operations?
- How does a plan avoid executing itself?

Mandatory boundaries:

- not actor;
- not scheduler;
- not action executor;
- no prebuilt `AgentRequest` creation before task selection;
- no private knowledge of delegated module's internal plan.

### `ReasoningTask`

Review focus:

- What target kinds are allowed?
- How to represent workflow application tasks versus executable tasks?
- Which fields are needed before execution selection?
- How to represent dependency on delegated `ReasoningRequest` without inspecting target module plan?

Mandatory boundaries:

- not capability definition;
- not executor;
- not authority;
- no prebuilt `AgentRequest`.

### `AgentRequest`

Review focus:

- What makes an invocation concrete?
- Which selected capability reference is needed?
- How does it carry context and authorization evidence without fake boundary fields?
- Confirm it cannot target `Workflow`.

Mandatory boundaries:

- not universal reasoning request;
- not plan;
- not self-authorizing;
- not sent to workflow.

### `AgentResponse`

Review focus:

- What must be returned by agents/routines/actions?
- Does it need artifact refs and event refs?
- How does block/failure material differ from produced artifact?

Mandatory boundaries:

- not knowledge by default;
- not learning by default;
- not authority;
- not returned by workflow.

## 5. Output Expected

Finalize the request/plan/invocation separation with operation pre/postconditions only where stable.
