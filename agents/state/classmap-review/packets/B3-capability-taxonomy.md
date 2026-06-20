# B3 Executable Capability Taxonomy

Classes under review: `Workflow`, `AgentRoutine`, `AgentAction`, `ModelAction`, `ModelBinding`.

## Required Context

This packet enforces the corrected abstraction split.

`Workflow` is module-level and planning-time. It is a reusable blueprint that creates or refines `ReasoningTask` occurrences inside a `ReasoningPlan`.

`AgentRoutine` is agent-level and execution-time. It is a compound executable agent capability. It receives `AgentRequest`, may coordinate lower-level steps/actions, and returns `AgentResponse`.

`AgentAction` is the smallest executable capability.

`ModelAction` is an inference-backed `AgentAction`.

`ModelBinding` constrains model-backed execution.

## Adjacent Concepts

- `ReasoningModule` owns workflows and actions.
- `MADREAgent` owns or exposes routines.
- `ReasoningTask` may target executable capabilities or delegated requests.
- `Workflow` application may create/refine `ReasoningTask` objects.
- `AgentRequest` is sent to executable routines/actions.
- `AgentResponse` is returned by executable routines/actions.
- Model-backed output is ordinary material/artifact plus event evidence.

## Use-Case Pressure

- UC-E: LaTeX document workflow creates tasks; LaTeX agent routines execute within selected tasks.
- UC-C: Research action is bounded and privacy-minimized.
- UC-D: simple math may be `AgentAction`/`SimpleAgent`; non-trivial math may delegate to MathModule.
- UC-F: repeated successful task structures may create workflow improvements; repeated internal behavior may create routine improvements.

## `Workflow`

Review focus:

- Is it a module-level planning blueprint only?
- What metadata is required for safe planning selection?
- Should workflow application become an operation? If yes, its postcondition is task creation/refinement.
- How are workflows learned or improved through candidate paths?

Boundary focus:

- planning-time blueprint
- module-owned selection
- task generation/refinement
- compatibility with accumulated constraints

## `AgentRoutine`

Review focus:

- Is it a kind of compound `AgentAction` architecturally?
- Should the class map model it as subclass of `AgentAction` or separate class with action-like invocation?
- What minimum attributes distinguish it from action?
- What postcondition confirms executable invocation?

Boundary focus:

- agent-level compound executable capability
- `AgentRequest` input
- `AgentResponse` output
- authority preserved from selected invocation

## `AgentAction`

Review focus:

- Is it the smallest executable capability?
- What metadata must it expose while keeping boundary classes justified by later review?
- Is `execute(request: AgentRequest): AgentResponse` stable?
- What precondition expresses valid composed-boundary execution?

Boundary focus:

- smallest executable unit
- typed capability metadata
- bounded host/tool access
- execution under constructed invocation context

## `ModelAction`

Review focus:

- Is it inference-backed `AgentAction`?
- Is `modelBinding` the stable additional attribute?
- What event evidence is mandatory for model-backed execution?
- Can `RuntimeEvent`, `AgentResponse`, `ReasoningArtifact`, and `ModelBinding` cover model-backed traceability?

Boundary focus:

- inference-backed execution
- context governance
- model binding
- generated material as produced material with event evidence

## `ModelBinding`

Review focus:

- What minimum identity is needed?
- Which model/backend/profile/resource details are stable enough to include now?
- Does the class constrain execution as metadata/configuration?

Boundary focus:

- model-use constraints
- selected runtime/profile/resource limits
- compatibility with composed request path

## Output

Produce a precise capability taxonomy before finalizing `ReasoningTask` and `AgentRequest`.
