# Packet B2 — Agent Family

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Classes under review:** `MADREAgent`, `SimpleAgent`, `TwinAgent`, `ComplexAgent`, `SystemAgent`

## 1. Required Context

An agent is a module-owned runtime actor. It does not own the kernel, does not widen authority, and does not carry knowledge outside its module.

The agent hierarchy is justified by stable behavior invariants, not by generic agent-framework taxonomy.

`SystemAgent` is justified only because it is strongly bound to one module and acts as the module's main operational agent. It must not mean global authority.

## 2. Adjacent Concepts Required

- `ReasoningModule` owns agents.
- Main Agent is a role held by one module-owned `SystemAgent`.
- `AgentRequest` is concrete invocation context, created after task execution selection.
- `AgentResponse` is concrete invocation result.
- `AgentRoutine` is agent-level compound executable behavior.
- `Workflow` is module-level planning blueprint, not an agent capability.

## 3. Use-Case Pressure

- UC-A: CoreModule SystemAgent handles live user interaction and preference capture.
- UC-C: SecurityCheck TwinAgent reviews malicious/prompt-injection material.
- UC-D: SimpleAgent may handle deterministic math action.
- UC-E: LaTeX Writing ComplexAgent uses routines and background repair.
- UC-G: SystemAgent/ComplexAgent must preserve foreground while delayed reasoning continues.

## 4. Class Discussion Blocks

### `MADREAgent`

Review focus:

- Is it abstract?
- Are `agentId` and `owningModule` sufficient stable attributes?
- Is `execute(request: AgentRequest): AgentResponse` stable?
- What must be true before execution?

Mandatory boundaries:

- does not own knowledge globally;
- does not widen authority;
- does not promote material;
- does not execute without valid invocation context.

### `SimpleAgent`

Review focus:

- Is this class justified by direct execution invariant?
- Is it only a concrete subclass/profile of `MADREAgent`?
- Are any additional attributes needed? Default answer should be no.

Mandatory boundaries:

- no self-review guarantee;
- no reflective learning;
- no extra authority.

### `TwinAgent`

Review focus:

- Is the checked-output invariant enough to justify class identity?
- What postcondition defines success?
- Should quality gates select `TwinAgent` or "agent with review guarantee"?

Mandatory boundaries:

- no learning by itself;
- review does not widen authority;
- unchecked output cannot satisfy a task requiring checked output;
- no assumption of two threads or two models.

### `ComplexAgent`

Review focus:

- Is foreground/reasoning/reflection capacity stable?
- Should lanes be attributes or behavioral capacities? Current default: capacities, not attributes.
- What outputs can reflection create? Current default: learning candidates only, not active learning.

Mandatory boundaries:

- lane behavior does not increase authority;
- reflection cannot activate learning;
- foreground remains within module/request boundary.

### `SystemAgent`

Review focus:

- What behavior cannot be reduced to generic `ComplexAgent`?
- Is inheritance from `ComplexAgent` valid as strict invariant?
- Which live-interaction operations are stable enough to define later?
- How does it use access surface without owning it?

Mandatory boundaries:

- not kernel;
- not global agent;
- not owner of access surface;
- not manager of other modules' private knowledge;
- no direct promotion of knowledge or learning.

## 5. Output Expected

Finalize or revise the agent hierarchy while keeping it human-readable and non-enum-driven.
