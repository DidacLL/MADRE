# B1 Runtime Ownership And Entry

Classes under review: `MADREKernel`, `ReasoningModule`.

## Required Context

The kernel is deterministic runtime coordination. It registers modules, assigns the CoreModule role, resolves explicit valid targets, falls back to CoreModule, schedules delayed work, provides runtime access paths, and appends events.

Semantic reasoning, semantic module selection, module-private knowledge, action execution, model invocation, and learning stay owned by modules and their governed runtime structures.

A `ReasoningModule` is the bounded reasoning and data-governance unit. It owns scope, knowledge, agents, workflows, routines, actions, context governance, model-backed action bindings, local risk/sensitivity/recovery boundaries, and local learning proposals.

## Adjacent Concepts

- CoreModule is a role.
- Main Agent is a role held by one module-owned `SystemAgent`.
- Access surface provides entry and interaction.
- Kernel target resolution handles explicit valid target or CoreModule fallback.
- Module delegation is semantic and module-owned.
- Module storage access is infrastructure-defined at this stage.

## Use-Case Pressure

- UC-A: ordinary user interaction and preference capture enters through CoreModule.
- UC-B: academic writing task is semantically delegated by module behavior.
- UC-C: research delegation is minimized before ResearchModule receives it.
- UC-G: delayed work is scheduled while reasoning remains module-owned.

## `MADREKernel`

Review focus:

- Is deterministic coordination enough?
- Which attributes are stable?
- Are `registerModule`, `assignCoreModule`, `resolveTarget`, and `appendEvent` stable operations?
- Which operation semantics preserve kernel-level scheduling?
- Which preconditions keep semantic routing and policy reasoning in module-owned structures?

Boundary focus:

- deterministic coordination
- target resolution
- delayed-work scheduling
- runtime access paths
- event append

## `ReasoningModule`

Review focus:

- What is the minimum stable identity and ownership boundary?
- Should `mainAgent`, `agents`, `workflows`, and `actions` be direct attributes?
- How should routines be owned: by agents only, visible to module catalog, or both?
- Is `handle(request)` the only stable operation for now?
- What does minimized delegated request creation require?

Boundary focus:

- module-owned reasoning and delegation
- module-local knowledge governance
- context governance
- agent, workflow, routine, action, and model-binding ownership
- knowledge and learning promotion through explicit governed paths

## Output

For each class, produce final verdict, stable responsibilities, boundaries, minimal attributes, stable operations with pre/postconditions, unresolved issues, and class status update.
