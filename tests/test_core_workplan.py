import asyncio
import sqlite3

import httpx
import pytest

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.contracts import WorkSubmission
from madre.inference import CapabilityError, ChatResult
from madre_core import CoreClient
from madre_core.planning import (
    CorePlanningAgent,
    CoreWorkPlanError,
    create_reviewed_planning_work_plan,
    execute_reviewed_planning_work_plan,
)

CAPABILITIES = {
    "local-chat": CapabilityConfig(
        endpoint="http://127.0.0.1:8080/v1",
        model="test-model",
    )
}


def test_planning_agent_offers_workflows_selected_by_objective_specific_workplan():
    client = CoreClient("http://127.0.0.1:8731", "test-token", "local-chat")
    agent = CorePlanningAgent(client)
    plan = create_reviewed_planning_work_plan(
        agent,
        "Design a zero-data-loss migration plan for a stateful service.",
    )

    assert agent.id == "core-planning-agent"
    assert [workflow.id for workflow in agent.workflows] == [
        "planning.draft",
        "planning.critique",
        "planning.synthesize",
    ]
    assert plan.agent_id == agent.id
    assert plan.objective == "Design a zero-data-loss migration plan for a stateful service."
    assert [(step.id, step.workflow_id, step.depends_on) for step in plan.steps] == [
        ("draft", "planning.draft", ()),
        ("critique", "planning.critique", ("draft",)),
        ("synthesis", "planning.synthesize", ("draft", "critique")),
    ]
    assert set(WorkSubmission.model_fields) == {
        "application_id",
        "capability_id",
        "input",
        "eligible_at",
        "priority",
        "constraints",
    }


def test_reviewed_planning_workplan_executes_through_runtime_http_boundary(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    observed_messages: list[list[dict[str, object]]] = []

    async def fake_invoke(capability, request, constraints):
        messages = [message.model_dump() for message in request.messages]
        observed_messages.append(messages)
        instruction = request.messages[0].content
        if "planning.draft workflow" in instruction:
            text = "draft: stop writes, copy state, verify, cut over"
        elif "planning.critique workflow" in instruction:
            text = "critique: rollback and verification gates are underspecified"
        else:
            assert "planning.synthesize workflow" in instruction
            text = "final: stop writes, snapshot, verify, cut over with rollback gate"
        return ChatResult(
            text=text,
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)
    app = create_app(runtime_settings)

    async def exercise():
        async with app.router.lifespan_context(app):
            client = CoreClient(
                "http://127.0.0.1:8731",
                "test-token",
                "local-chat",
                poll_interval_seconds=0.001,
                transport=httpx.ASGITransport(app=app),
            )
            agent = CorePlanningAgent(client, max_tokens=96, timeout_seconds=5)
            plan = create_reviewed_planning_work_plan(
                agent,
                "Design a zero-data-loss migration plan for a stateful service.",
            )
            return await execute_reviewed_planning_work_plan(agent, plan)

    plan = asyncio.run(exercise())

    assert [step.status for step in plan.steps] == ["succeeded", "succeeded", "succeeded"]
    assert all(step.work_id for step in plan.steps)
    assert plan.final_output == "final: stop writes, snapshot, verify, cut over with rollback gate"
    assert len(observed_messages) == 3
    assert "draft: stop writes" in str(observed_messages[1][-1]["content"])
    assert "rollback and verification gates" in str(observed_messages[2][-1]["content"])

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute(
            "SELECT application_id, status, priority FROM runtime_work ORDER BY queue_sequence"
        ).fetchall()
    assert rows == [
        ("madre-core", "succeeded", 0),
        ("madre-core", "succeeded", 0),
        ("madre-core", "succeeded", 0),
    ]


def test_runtime_failure_is_truthful_in_workplan_and_blocks_dependent_work(tmp_path, monkeypatch):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    invocations = 0

    async def fake_invoke(capability, request, constraints):
        nonlocal invocations
        invocations += 1
        if invocations == 2:
            raise CapabilityError("connection", "critic capability failed")
        return ChatResult(
            text="draft plan",
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    runtime_settings = Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)
    app = create_app(runtime_settings)

    async def exercise():
        async with app.router.lifespan_context(app):
            client = CoreClient(
                "http://127.0.0.1:8731",
                "test-token",
                "local-chat",
                poll_interval_seconds=0.001,
                transport=httpx.ASGITransport(app=app),
            )
            agent = CorePlanningAgent(client, max_tokens=96, timeout_seconds=5)
            plan = create_reviewed_planning_work_plan(agent, "Plan a risky state migration.")
            with pytest.raises(CoreWorkPlanError) as caught:
                await execute_reviewed_planning_work_plan(agent, plan)
            return plan, caught.value

    plan, error = asyncio.run(exercise())

    assert error.plan is plan
    assert error.failed_step_id == "critique"
    assert plan.step("draft").status == "succeeded"
    assert plan.step("critique").status == "failed"
    assert plan.step("critique").work_id is not None
    assert plan.step("critique").failure == (
        "MADRE work failed [connection]: critic capability failed"
    )
    assert plan.step("synthesis").status == "blocked"
    assert plan.step("synthesis").work_id is None
    assert plan.step("synthesis").failure == "blocked by failed dependency: critique"
    assert plan.final_output is None
    assert invocations == 2

    with sqlite3.connect(runtime_settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute(
            "SELECT application_id, status FROM runtime_work ORDER BY queue_sequence"
        ).fetchall()
    assert rows == [
        ("madre-core", "succeeded"),
        ("madre-core", "failed"),
    ]
