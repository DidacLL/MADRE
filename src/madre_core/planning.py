"""Concrete reviewed-planning Agent behavior owned by MADRE CORE."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Literal

from madre_core.client import CoreClient, CoreRuntimeError

PlanningWorkflowId = Literal[
    "planning.draft",
    "planning.critique",
    "planning.synthesize",
]
WorkPlanStepId = Literal["draft", "critique", "synthesis"]
WorkPlanStepStatus = Literal["pending", "running", "succeeded", "failed", "blocked"]

_AGENT_ID = "core-planning-agent"
_DRAFT_WORKFLOW_ID: PlanningWorkflowId = "planning.draft"
_CRITIQUE_WORKFLOW_ID: PlanningWorkflowId = "planning.critique"
_SYNTHESIS_WORKFLOW_ID: PlanningWorkflowId = "planning.synthesize"

_DRAFT_INSTRUCTION = """\
You are MADRE CORE's planning Agent performing the planning.draft workflow. Produce an
actionable first plan for the supplied objective. Identify concrete steps, dependencies,
important assumptions, and any user decision or review gates that materially affect the
plan. Keep the plan specific to the objective. Do not critique or revise the plan in this
workflow; return the draft plan only.
"""
_CRITIQUE_INSTRUCTION = """\
You are MADRE CORE's planning Agent performing the planning.critique workflow. Review the
supplied draft plan against the original objective. Identify missing dependencies, unsafe
assumptions, likely failure modes, contradictions, sequencing problems, and gates that
should be explicit. Do not rewrite the full plan; return a concise critique that a later
synthesis workflow can use.
"""
_SYNTHESIS_INSTRUCTION = """\
You are MADRE CORE's planning Agent performing the planning.synthesize workflow. Produce
the final revised plan for the original objective using both the draft and the critique.
Resolve the critique rather than merely appending it. Keep concrete steps, dependencies,
important assumptions, failure handling, and user review gates explicit where relevant.
Return only the final user-facing plan.
"""


@dataclass(frozen=True)
class CorePlanningWorkflow:
    """One reusable reasoning behavior actually offered by the CORE planning Agent."""

    id: PlanningWorkflowId
    description: str


@dataclass
class WorkPlanStep:
    """One semantic step in CORE's concrete reviewed-planning WorkPlan."""

    id: WorkPlanStepId
    workflow_id: PlanningWorkflowId
    depends_on: tuple[WorkPlanStepId, ...] = ()
    status: WorkPlanStepStatus = "pending"
    work_id: str | None = None
    output: str | None = None
    failure: str | None = None


@dataclass
class CoreWorkPlan:
    """Process-local semantic state for one reviewed planning objective."""

    objective: str
    agent_id: str
    steps: list[WorkPlanStep]

    def step(self, step_id: WorkPlanStepId) -> WorkPlanStep:
        for step in self.steps:
            if step.id == step_id:
                return step
        raise KeyError(step_id)

    @property
    def final_output(self) -> str | None:
        synthesis = self.step("synthesis")
        return synthesis.output if synthesis.status == "succeeded" else None


class CoreWorkPlanError(RuntimeError):
    """A concrete WorkPlan failed while materializing ordinary MADRE runtime work."""

    def __init__(self, plan: CoreWorkPlan, failed_step_id: WorkPlanStepId, message: str) -> None:
        super().__init__(f"WorkPlan step {failed_step_id} failed: {message}")
        self.plan = plan
        self.failed_step_id = failed_step_id


class CorePlanningAgent:
    """CORE reasoning actor that knows how to draft, critique, and synthesize plans."""

    id = _AGENT_ID
    workflows = (
        CorePlanningWorkflow(
            id=_DRAFT_WORKFLOW_ID,
            description="Draft an actionable objective-specific plan.",
        ),
        CorePlanningWorkflow(
            id=_CRITIQUE_WORKFLOW_ID,
            description="Critique a draft plan for omissions, assumptions, and failure modes.",
        ),
        CorePlanningWorkflow(
            id=_SYNTHESIS_WORKFLOW_ID,
            description="Synthesize a revised final plan from a draft and its critique.",
        ),
    )

    def __init__(
        self,
        client: CoreClient,
        *,
        max_tokens: int = 512,
        timeout_seconds: float = 120,
        priority: int = 0,
    ) -> None:
        if max_tokens <= 0:
            raise ValueError("planning max_tokens must be positive")
        if timeout_seconds <= 0:
            raise ValueError("planning timeout_seconds must be positive")
        if priority < -100 or priority > 100:
            raise ValueError("planning priority must be between -100 and 100")
        self.client = client
        self.max_tokens = max_tokens
        self.timeout_seconds = timeout_seconds
        self.priority = priority

    async def perform(self, plan: CoreWorkPlan, step: WorkPlanStep) -> str:
        """Perform one offered workflow by materializing it as ordinary MADRE work."""
        if plan.agent_id != self.id:
            raise ValueError(f"WorkPlan targets unknown Agent {plan.agent_id!r}")
        if step.workflow_id not in {workflow.id for workflow in self.workflows}:
            raise ValueError(f"Agent {self.id} does not offer workflow {step.workflow_id!r}")

        record = await self.client.submit(
            self._messages(plan, step),
            max_tokens=self.max_tokens,
            timeout_seconds=self.timeout_seconds,
            priority=self.priority,
        )
        step.work_id = record.id
        step.status = "running"
        while record.status in {"accepted", "running"}:
            await asyncio.sleep(self.client.poll_interval_seconds)
            record = await self.client.inspect(record.id)
        return self.client.result_text(record)

    @staticmethod
    def _messages(plan: CoreWorkPlan, step: WorkPlanStep) -> list[dict[str, str]]:
        if step.workflow_id == _DRAFT_WORKFLOW_ID:
            return [
                {"role": "system", "content": _DRAFT_INSTRUCTION},
                {"role": "user", "content": f"Objective:\n{plan.objective}"},
            ]
        if step.workflow_id == _CRITIQUE_WORKFLOW_ID:
            draft = plan.step("draft").output
            if draft is None:
                raise ValueError("planning.critique requires the draft workflow output")
            return [
                {"role": "system", "content": _CRITIQUE_INSTRUCTION},
                {
                    "role": "user",
                    "content": f"Objective:\n{plan.objective}\n\nDraft plan:\n{draft}",
                },
            ]
        if step.workflow_id == _SYNTHESIS_WORKFLOW_ID:
            draft = plan.step("draft").output
            critique = plan.step("critique").output
            if draft is None or critique is None:
                raise ValueError("planning.synthesize requires draft and critique workflow outputs")
            return [
                {"role": "system", "content": _SYNTHESIS_INSTRUCTION},
                {
                    "role": "user",
                    "content": (
                        f"Objective:\n{plan.objective}\n\nDraft plan:\n{draft}"
                        f"\n\nCritique:\n{critique}"
                    ),
                },
            ]
        raise ValueError(f"unsupported planning workflow {step.workflow_id!r}")


def create_reviewed_planning_work_plan(
    agent: CorePlanningAgent,
    objective: str,
) -> CoreWorkPlan:
    """Create the one concrete WorkPlan supported by this reviewed-planning behavior."""
    objective = objective.strip()
    if not objective:
        raise ValueError("planning objective is empty")

    selected_workflows: tuple[PlanningWorkflowId, ...] = (
        _DRAFT_WORKFLOW_ID,
        _CRITIQUE_WORKFLOW_ID,
        _SYNTHESIS_WORKFLOW_ID,
    )
    offered = {workflow.id for workflow in agent.workflows}
    missing = [
        workflow_id for workflow_id in selected_workflows if workflow_id not in offered
    ]
    if missing:
        raise ValueError(
            f"Agent {agent.id} does not offer required workflow(s): {', '.join(missing)}"
        )

    return CoreWorkPlan(
        objective=objective,
        agent_id=agent.id,
        steps=[
            WorkPlanStep(id="draft", workflow_id=_DRAFT_WORKFLOW_ID),
            WorkPlanStep(
                id="critique",
                workflow_id=_CRITIQUE_WORKFLOW_ID,
                depends_on=("draft",),
            ),
            WorkPlanStep(
                id="synthesis",
                workflow_id=_SYNTHESIS_WORKFLOW_ID,
                depends_on=("draft", "critique"),
            ),
        ],
    )


async def execute_reviewed_planning_work_plan(
    agent: CorePlanningAgent,
    plan: CoreWorkPlan,
) -> CoreWorkPlan:
    """Interpret this concrete WorkPlan above the runtime boundary."""
    if plan.agent_id != agent.id:
        raise ValueError(f"WorkPlan targets unknown Agent {plan.agent_id!r}")

    offered = {workflow.id for workflow in agent.workflows}
    for step in plan.steps:
        if step.workflow_id not in offered:
            raise ValueError(f"WorkPlan selects unavailable workflow {step.workflow_id!r}")
        unmet = [
            dependency
            for dependency in step.depends_on
            if plan.step(dependency).status != "succeeded"
        ]
        if unmet:
            step.status = "blocked"
            step.failure = f"blocked by unsatisfied dependency: {', '.join(unmet)}"
            raise CoreWorkPlanError(plan, step.id, step.failure)

        try:
            output = await agent.perform(plan, step)
        except CoreRuntimeError as exc:
            step.status = "failed"
            step.failure = str(exc)
            _block_dependents(plan, step.id)
            raise CoreWorkPlanError(plan, step.id, step.failure) from exc
        step.output = output
        step.status = "succeeded"

    return plan


def _block_dependents(plan: CoreWorkPlan, failed_step_id: WorkPlanStepId) -> None:
    blocked: set[WorkPlanStepId] = {failed_step_id}
    for step in plan.steps:
        if step.status != "pending":
            continue
        blockers = [dependency for dependency in step.depends_on if dependency in blocked]
        if not blockers:
            continue
        step.status = "blocked"
        step.failure = f"blocked by failed dependency: {', '.join(blockers)}"
        blocked.add(step.id)
