# Packet B3 — Executable Capability Taxonomy

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Classes under review:** `Workflow`, `AgentRoutine`, `AgentAction`, `ModelAction`, `ModelBinding`

## 1. Required Context

This packet must enforce the corrected abstraction split:

`Workflow` is module-level and planning-time. It is a reusable blueprint that creates or refines `ReasoningTask` occurrences inside a `ReasoningPlan`.

`AgentRoutine` is agent-level and execution-time. It is a compound executable agent capability. It receives `AgentRequest`, may coordinate lower-level steps/actions, and returns `AgentResponse`.

`AgentAction` is the smallest executable capability.

`ModelAction` is an inference-backed `AgentAction`.

`ModelBinding` constrains model-backed execution.

## 2. Adjacent Concepts Required

- `ReasoningModule` owns workflows and actions.
- `MADREAgent` owns or exposes routines.
- `ReasoningTask` may target executable capabilities or delegated requests.
- `Workflow` application may create/refine `ReasoningTask` objects.
- `AgentRequest` is not sent to workflow.
- `AgentResponse` is not returned by workflow.
- Model-backed output is ordinary material/artifact plus event evidence.

## 3. Use-Case Pressure

- UC-E: LaTeX document workflow creates tasks; LaTeX agent routines execute within selected tasks.
- UC-C: Research action must be bounded and privacy-minimized.
- UC-D: simple math may be `AgentAction`/`SimpleAgent`, not full MathModule.
- UC-F: repeated successful task structures may create workflow improvements; repeated internal behavior may create routine improvements.

## 4. Class Discussion Blocks

### `Workflow`

Review focus:

- Is it a module-level planning blueprint only?
- What metadata is required for safe planning selection?
- Should workflow application become an operation? If yes, its postcondition must be task creation/refinement only.
- How are workflows learned or improved without automatic activation?

Mandatory boundaries:

- not actor;
- not action;
- not routine;
- not second plan;
- not scheduled object;
- not invoked through `AgentRequest`;
- does not return `AgentResponse`;
- does not execute generated tasks.

### `AgentRoutine`

Review focus:

- Is it a kind of compound `AgentAction` architecturally?
- Should the class map model it as subclass of `AgentAction` or separate class with action-like invocation?
- What minimum attributes distinguish it from action?
- What postcondition confirms it behaved as executable invocation?

Mandatory boundaries:

- not workflow;
- not planning blueprint;
- does not create `ReasoningTask`;
- no plan-specific scheduling semantics;
- does not widen authority.

### `AgentAction`

Review focus:

- Is it the smallest executable capability?
- What metadata must it expose without adding `ExecutionManifesto` as a class?
- Is `execute(request: AgentRequest): AgentResponse` stable?
- What precondition expresses valid authorization without faking boundary classes?

Mandatory boundaries:

- no raw user/model execution;
- no self-authorization;
- no arbitrary host/tool access by default;
- no plan-specific scheduling semantics.

### `ModelAction`

Review focus:

- Is it simply inference-backed `AgentAction`?
- Is `modelBinding` the only stable additional attribute?
- What event evidence is mandatory for model-backed execution?
- Confirm no separate inference-output or inference-record class is needed.

Mandatory boundaries:

- no model authority;
- no generated material as truth/knowledge/policy/learning;
- no bypass of context governance;
- no bypass of model binding.

### `ModelBinding`

Review focus:

- What minimum identity is needed?
- Which model/backend/profile/resource details are stable enough to include now?
- Does the class constrain execution without becoming actor or authority?

Mandatory boundaries:

- not model;
- not backend;
- not actor;
- not authority by itself;
- no direct execution.

## 5. Output Expected

Produce a precise capability taxonomy before finalizing `ReasoningTask` and `AgentRequest`.
