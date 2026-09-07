# CORE reviewed-planning WorkPlan

`MADRE.md` owns the canonical definitions of Agent, Agent workflow and WorkPlan. This document records one concrete CORE behavior that makes those definitions executable; it does not define a second architecture vocabulary.

## Objective

The behavior serves a user objective that genuinely benefits from more than one reasoning operation: produce a reviewed actionable plan for a complex objective rather than accepting the first generated plan as final.

The experimental `madre-core` terminal exposes it as:

```text
/plan <objective>
```

For example:

```text
/plan Design a zero-data-loss migration plan for a stateful service, including rollback and review gates.
```

This behavior is independent of the current `fast` / `deeper` experiment. It does not classify the objective by a scalar reasoning depth and does not invoke `/deeper`.

## Concrete Agent and workflows

CORE owns one concrete reasoning actor for this behavior:

```text
core-planning-agent
```

The Agent statically advertises exactly three reusable workflows:

| Workflow | Behavior |
| --- | --- |
| `planning.draft` | Draft an actionable objective-specific plan. |
| `planning.critique` | Critique the draft for omissions, assumptions, sequencing problems and failure modes. |
| `planning.synthesize` | Produce the revised final plan from the draft and critique. |

The repertoire is software state on `CorePlanningAgent`. There is no Agent registry, plugin discovery mechanism, workflow DSL, inheritance hierarchy or generic workflow engine in this slice.

## Objective-specific WorkPlan

`create_reviewed_planning_work_plan(...)` creates process-local semantic planning state for the supplied objective:

```text
draft      planning.draft
  │
  ▼
critique   planning.critique
  │
  ▼
synthesis  planning.synthesize
```

The synthesis step explicitly depends on both the draft and critique outputs. Each step records its selected workflow, dependencies, semantic status, corresponding runtime work ID when submitted, generated output when successful, and failure text when execution fails.

The WorkPlan is owned by CORE because the objective is generic/system planning behavior. It remains process-local. Restarting CORE loses the semantic WorkPlan association; no WorkPlan persistence is introduced because this behavior does not yet require semantic recovery across CORE restart.

## Runtime boundary

CORE interprets the WorkPlan. An executable step becomes an ordinary authenticated submission through `CoreClient` with stable `application_id = "madre-core"`:

```text
objective-specific WorkPlan
          ↓
        CORE
          ↓
 ordinary WorkSubmission
          ↓
    MADRE Runtime
          ↓
 configured capability
```

MADRE Runtime receives the same public `WorkSubmission` shape used by every application: application ID, capability ID, JSON input, optional eligibility, priority and execution constraints. No objective, Agent identity, workflow identity, dependency or WorkPlan field is added to the runtime contract or runtime storage.

CORE does not import or invoke the inference adapter directly. The planning Agent uses the same authenticated loopback HTTP boundary as existing CORE behavior, and the runtime remains responsible for durable work execution, scheduling, scarce-resource admission and capability invocation.

The current WorkPlan dependencies are sequential, so CORE submits the critique only after the draft succeeds and submits synthesis only after both required semantic inputs exist. This dependency decision is CORE planning behavior, not runtime queue semantics.

## Failure semantics

Runtime failure is not converted into a synthetic planning result. If a submitted step fails or is cancelled, `CoreClient` surfaces the ordinary runtime evidence, the corresponding WorkPlan step becomes `failed`, and transitively dependent steps become `blocked`. A blocked synthesis is not submitted.

The CLI reports the failed semantic step and its runtime work ID when one was accepted. Successful earlier step output remains process-local evidence on the WorkPlan; it is not promoted into runtime knowledge.

## Validation

`tests/test_core_workplan.py` proves that:

- the concrete Agent advertises the workflows selected by the WorkPlan;
- a real objective creates structured WorkPlan state with deterministic dependencies;
- draft, critique and synthesis execute through the existing CORE → authenticated HTTP → runtime path;
- the three executable steps are ordinary `madre-core` runtime work items;
- the public runtime submission model does not acquire WorkPlan semantics;
- critique output depends on the successful draft and synthesis receives both successful predecessors;
- a durable runtime failure is represented truthfully in the WorkPlan and prevents dependent synthesis work.

Mocked inference is used for deterministic orchestration evidence. Real-model execution is still required before making product-quality claims about whether the configured model produces useful plans or critiques.

## Deliberately absent

This behavior does not introduce a universal Planner, Agent registry, workflow persistence, workflow/routine dual hierarchy, autonomous workflow invention, distributed Agents, persistent Agent sessions, memory framework, model router, scheduler extension, AAAAT integration, MCP integration, or any discarded `ReasoningModule` / `ReasoningPlan` architecture.
