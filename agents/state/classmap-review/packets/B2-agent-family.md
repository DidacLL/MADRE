# B2 Agent Family

Classes under review: `MADREAgent`, `SimpleAgent`, `TwinAgent`, `ComplexAgent`, `SystemAgent`.

## Required Context

An agent is a module-owned runtime actor. It operates inside module authority and module knowledge boundaries.

The agent hierarchy is justified by stable behavior invariants rather than generic agent-framework taxonomy.

`SystemAgent` is justified by its binding to one module as the module's main operational agent. Its authority remains module-local.

## Adjacent Concepts

- `ReasoningModule` owns agents.
- Main Agent is a role held by one module-owned `SystemAgent`.
- `AgentRequest` is concrete invocation context, created after task execution selection.
- `AgentResponse` is concrete invocation result.
- `AgentRoutine` is agent-level compound executable behavior.
- `Workflow` is module-level planning blueprint.

## Use-Case Pressure

- UC-A: CoreModule `SystemAgent` handles live user interaction and preference capture.
- UC-C: SecurityCheck `TwinAgent` reviews malicious or prompt-injection material.
- UC-D: `SimpleAgent` may handle deterministic math action.
- UC-E: LaTeX Writing `ComplexAgent` uses routines and background repair.
- UC-G: `SystemAgent` and `ComplexAgent` preserve foreground behavior while delayed reasoning continues.

## `MADREAgent`

Review focus:

- Is it abstract?
- Are `agentId` and `owningModule` sufficient stable attributes?
- Is `execute(request: AgentRequest): AgentResponse` stable?
- What is true before execution?

Boundary focus:

- module-owned runtime actor
- valid invocation context
- module-local knowledge use
- authority preserved from composed runtime path

## `SimpleAgent`

Review focus:

- Is this class justified by direct execution invariant?
- Is it a concrete subclass/profile of `MADREAgent`?
- Which additional attributes are justified by the direct execution invariant?

Boundary focus:

- direct execution profile
- ordinary invocation result
- same authority boundary as the selected invocation

## `TwinAgent`

Review focus:

- Is the checked-output invariant enough to justify class identity?
- What postcondition defines success?
- Should quality gates select `TwinAgent` or an agent with review guarantee?

Boundary focus:

- checked-output guarantee
- review within the same composed authority
- checked result requirement for tasks that require checked output
- implementation independent of thread/model count

## `ComplexAgent`

Review focus:

- Is foreground/reasoning/reflection capacity stable?
- Should lanes be attributes or behavioral capacities? Current default: capacities.
- What outputs can reflection create? Current default: learning candidates.

Boundary focus:

- foreground behavior within module/request boundary
- reasoning and reflection inside module constraints
- reflection output as candidate material

## `SystemAgent`

Review focus:

- Which behavior needs a class beyond generic `ComplexAgent`?
- Is inheritance from `ComplexAgent` valid as a strict invariant?
- Which live-interaction operations are stable enough to define later?
- How does it use the access surface?

Boundary focus:

- module-bound main operational agent
- access-surface interaction
- module-local orchestration
- governed knowledge and learning promotion

## Output

Finalize or revise the agent hierarchy while keeping it human-readable and based on stable behavior invariants.
