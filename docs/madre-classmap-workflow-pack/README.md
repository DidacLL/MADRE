# MADRE Classmap Discussion Workflow Pack

This pack contains markdown files for token-bounded LLM-assisted class-by-class review.

## Files

- `00-agent-workflow.md` — narrow agent instructions for this review workflow.
- `01-classmap-sprint-plan.md` — updateable class-by-class sprint/review plan.
- `packets/00-boundary-model-orientation.md` — boundary invariants before class finalization.
- `packets/01-runtime-ownership-entry.md` — `MADREKernel`, `ReasoningModule`.
- `packets/02-agent-family.md` — agent hierarchy.
- `packets/03-capability-taxonomy.md` — workflow/routine/action/model binding distinction.
- `packets/04-request-plan-invocation.md` — requests, plans, tasks, invocations.
- `packets/05-context-material-knowledge-learning.md` — context, artifacts, knowledge, candidates, learning.
- `packets/06-trace-authority-evidence.md` — policy decision, journal, events.
- `packets/07-final-integration-review.md` — whole-classmap regression pass.

## Recommended Usage

For each new LLM-assisted class discussion, paste or provide:

1. `00-agent-workflow.md`
2. `01-classmap-sprint-plan.md`
3. only the relevant packet from `packets/`
4. the current class definition text under discussion

After the session, update only the relevant status and decision notes in `01-classmap-sprint-plan.md`.
