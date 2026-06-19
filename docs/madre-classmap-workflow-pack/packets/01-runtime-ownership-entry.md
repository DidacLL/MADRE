# Packet B1 — Runtime Ownership and Entry

**Use with:** `00-agent-workflow.md` and `01-classmap-sprint-plan.md`  
**Classes under review:** `MADREKernel`, `ReasoningModule`

## 1. Required Context

The kernel is deterministic runtime coordination. It registers modules, assigns the Core Module role, resolves explicit valid targets, falls back to CoreModule, schedules delayed work, provides runtime access paths, and appends events.

The kernel does not reason, does not semantically select modules, does not inspect module-private knowledge for routing, does not execute actions, does not invoke models, and does not learn.

A `ReasoningModule` is the bounded reasoning and data-governance unit. It owns scope, knowledge, agents, workflows, routines, actions, context governance, model-backed action bindings, local risk/sensitivity/recovery boundaries, and local learning proposals.

## 2. Adjacent Concepts Required

- Core Module is a role, not a class.
- Main Agent is a role held by one module-owned `SystemAgent`.
- Access surface is not authority.
- Kernel target resolution is not semantic routing.
- Module delegation is semantic and module-owned.
- Module storage access is infrastructure-defined, not a core domain class yet.

## 3. Use-Case Pressure

- UC-A: ordinary user interaction and preference capture must enter through CoreModule.
- UC-B: academic writing task must be semantically delegated by module behavior.
- UC-C: research delegation must be minimized before ResearchModule receives it.
- UC-G: delayed work must be scheduled without kernel becoming a reasoning actor.

## 4. Class Discussion Blocks

### `MADREKernel`

Review focus:

- Is deterministic coordination enough?
- Which attributes are truly stable?
- Are `registerModule`, `assignCoreModule`, `resolveTarget`, and `appendEvent` stable operations?
- Does any operation accidentally imply semantic routing or policy reasoning?
- How does scheduling stay kernel-level without making the kernel a plan executor?

Mandatory boundaries:

- no reasoning;
- no semantic module selection;
- no model invocation;
- no action execution;
- no knowledge ownership;
- no learning.

### `ReasoningModule`

Review focus:

- What is the minimum stable identity and ownership boundary?
- Should `mainAgent`, `agents`, `workflows`, and `actions` be direct attributes?
- How should routines be owned: by agents only, visible to module catalog, or both?
- Is `handle(request)` the only stable operation for now?
- What does "create minimized delegated request" require without adding speculative operations?

Mandatory boundaries:

- not kernel;
- no direct access to another module's private data;
- no bypass of context governance;
- no widening of agent/action authority;
- no silent knowledge or learning promotion.

## 5. Output Expected

For each class, produce:

- final verdict;
- stable responsibilities;
- boundaries;
- minimal attributes;
- stable operations with pre/postconditions;
- unresolved issues;
- sprint-plan status update.
