# Packet B0 — Boundary Model Orientation

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Scope:** cross-cutting boundary invariants only.  
**Classes under review:** none. Do not accept new classes in this packet.

## 1. Purpose

Clarify the boundary model enough to prevent fake attributes and fake policy checks from contaminating later class definitions.

This packet does not define final boundary classes. It establishes constraints that later classes must satisfy.

## 2. Minimal System Context

MADRE must allow a request to move through modules, agents, workflows, routines, and actions only when their declared capability/boundary metadata is compatible with the accumulated constraints of the request path.

The runtime must prevent accidental widening of privacy, scope, risk, action authority, model usage, web access, storage access, or learning authority.

The target design must be enforced by construction, not by arbitrary agent logic or an overridable method that future developers can bypass.

## 3. Non-Negotiable Invariants

- Every module, agent, workflow, routine, and action exposes immutable capability/boundary metadata.
- A request/task/invocation carries accumulated effective constraints.
- The path may narrow constraints but must not silently widen them.
- `Workflow` compatibility is planning-time compatibility: can this blueprint generate/refine tasks under the current constraints?
- `AgentRoutine`, `AgentAction`, and `ModelAction` compatibility is execution-time compatibility: can this concrete invocation run under the current constraints?
- Concrete execution must require an authorization context produced by the boundary mechanism.
- `PolicyDecision` records the result; it is not the execution authority by itself.
- Boundary results must be journaled through `RuntimeEvent`.

## 4. Reference Scenario Pressure

Use these stress cases:

- UC-C: web research request must not leak private CoreModule context into ResearchModule.
- UC-E: LaTeX compile/check must not become arbitrary shell access.
- UC-F: learning must not widen privacy boundaries even if it improves quality.
- UC-G: delayed work must preserve constraints across scheduling and resumption.

## 5. Discussion Questions

1. What minimum metadata must every capability declare without creating speculative classes?
2. What information must be accumulated on a `ReasoningRequest`, `ReasoningTask`, or `AgentRequest`?
3. What must be impossible to express through public APIs?
4. How can Java API design enforce "no valid execution without constructed authorization"?
5. Which classes must reference the boundary result, and which should only be constrained by it?
6. What must be recorded as `PolicyDecision` and/or `RuntimeEvent`?
7. Which information is planning-time only and which is execution-time only?

## 6. Output Expected

Produce only:

- accepted boundary invariants;
- terms to use during later class review;
- fields or operations that must not be added yet;
- affected class list;
- blockers for class finalization.

Do not create final class definitions here unless the user explicitly authorizes it.
