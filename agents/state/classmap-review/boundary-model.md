# Boundary Model Discussion

This file owns the current boundary-model review used before finalizing later class definitions.

## Purpose

Clarify the boundary model enough for later classes to use real runtime constraints, typed metadata, constructed execution context, and traceable runtime evidence.

The discussion establishes constraints for later class review. Final boundary classes belong in the classmap once the class discussion justifies them.

## System Context

MADRE allows a request to move through modules, agents, workflows, routines, and actions when their declared capability and boundary metadata is compatible with the accumulated constraints of the request path.

The runtime preserves privacy, scope, risk, action authority, model usage, web access, storage access, and learning authority through construction of the executable path.

## Accepted Boundary Invariants

- Every module, agent, workflow, routine, and action exposes immutable capability and boundary metadata.
- A request, task, or invocation carries accumulated effective constraints.
- Each path step can narrow effective constraints.
- `Workflow` compatibility is planning-time compatibility for generating or refining tasks under current constraints.
- `AgentRoutine`, `AgentAction`, and `ModelAction` compatibility is execution-time compatibility for concrete invocation under current constraints.
- Concrete execution receives a runtime-constructed execution context from the boundary mechanism.
- `PolicyDecision` records the boundary outcome.
- `RuntimeEvent` records boundary evidence and runtime transitions.

## Reference Pressure

- UC-C: web research preserves private CoreModule context while using ResearchModule.
- UC-E: LaTeX compile/check remains a bounded internal action.
- UC-F: learning preserves privacy boundaries while improving future behavior.
- UC-G: delayed work preserves constraints across scheduling and resumption.

## Questions For B0 Review

1. Which minimum metadata must every capability declare?
2. Which constraints accumulate on `ReasoningRequest`, `ReasoningTask`, and `AgentRequest`?
3. Which public APIs construct valid executable paths?
4. Which classes reference the boundary result, and which classes are constrained by it?
5. Which facts belong in `PolicyDecision`, `RuntimeEvent`, or both?
6. Which information is planning-time and which is execution-time?

## B0 Output

Produce accepted boundary invariants, terms for later class review, affected classes, and blockers for class finalization.
