import asyncio
import sqlite3
from pathlib import Path

import httpx
import pytest
from pydantic import ValidationError

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.inference import ChatResult
from madre_kernel.agents import (
    CORE_AGENT,
    CORE_AGENT_DEFINITION,
    CORE_FINAL_SCHEMA,
    build_core_module,
)
from madre_kernel.contracts import (
    ActorSecurityFacts,
    AgentDefinition,
    AgentDefinitionRef,
    AgentInstance,
    AgentInstanceRef,
    AgentRef,
    AgentStateRef,
    AgentTask,
    AgentTaskRef,
    ClassificationTransform,
    ContextBundle,
    ContextBundleRef,
    ContextProvenance,
    DataSecurityFacts,
    DefinitionProvenance,
    DiscoveryPolicyRef,
    EffectKind,
    EffectSemantics,
    ExecutionBoundary,
    InterruptedOutcome,
    ModuleAgentDefinitionRef,
    ModuleAgentInstanceRef,
    ModuleManifest,
    ModuleRef,
    ObjectiveText,
    OperationDescriptor,
    OperationOutcomeKind,
    OperationRef,
    OperationSecurityFacts,
    PrivateFixturePayload,
    Repeatability,
    RuntimeEvidencePurpose,
    SchemaRef,
    ScopeDescriptor,
    ScopeRef,
    SecurityDecisionRef,
    SecurityLevel,
    SkillDefinition,
    SkillRef,
    WorkflowDefinition,
    WorkflowRecipeRef,
    WorkflowRef,
    WorkPlan,
    utc_now,
)
from madre_kernel.kernel import (
    DiscoveryPolicy,
    DiscoveryRule,
    Kernel,
    SecurityRejectedError,
    repeat_permitted,
)
from madre_kernel.modules import (
    CALC_OBJECTIVE_SCHEMA,
    CALC_OUTPUT_SCHEMA,
    CALC_SCOPE,
    CALCULATE,
    InProcessModule,
    OperationMaterial,
    UnknownOperationEffect,
    build_calculator_module,
    ref_key,
)
from madre_kernel.runtime_client import KernelRuntimeClient
from madre_kernel.security import SecurityAlgebra
from madre_kernel.storage import KernelStore

CAPABILITIES = {
    "local-chat": CapabilityConfig(
        endpoint="http://127.0.0.1:8080/v1",
        model="test-model",
    )
}


def _runtime_client(
    transport: httpx.AsyncBaseTransport | None = None,
) -> KernelRuntimeClient:
    return KernelRuntimeClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        application_id="madre-core",
        poll_interval_seconds=0.001,
        transport=transport,
    )


def _kernel(
    path: Path,
    transport: httpx.AsyncBaseTransport | None = None,
) -> Kernel:
    return Kernel(store=KernelStore(path), runtime=_runtime_client(transport))


def _provenance(module: ModuleRef) -> DefinitionProvenance:
    return DefinitionProvenance(created_at=utc_now(), created_by=module)


def test_reference_validation_and_reserved_level_rejection():
    with pytest.raises(ValidationError):
        ModuleRef(module_id="")
    with pytest.raises(ValidationError, match="SYSTEM_RESERVED"):
        DataSecurityFacts(
            sensitivity=SecurityLevel.SYSTEM_RESERVED,
            trust=SecurityLevel.LEVEL_1,
            scopes=frozenset(),
        )
    with pytest.raises(ValidationError, match="SYSTEM_RESERVED"):
        ActorSecurityFacts(
            trust=SecurityLevel.LEVEL_1,
            maximum_handled_sensitivity=SecurityLevel.LEVEL_2,
            execution_risk=SecurityLevel.SYSTEM_RESERVED,
        )
    with pytest.raises(ValidationError, match="SYSTEM_RESERVED"):
        OperationSecurityFacts(
            risk=SecurityLevel.SYSTEM_RESERVED,
            minimum_input_trust=SecurityLevel.LEVEL_1,
            maximum_input_sensitivity=SecurityLevel.LEVEL_2,
            source_scopes=frozenset(),
            destination_scopes=frozenset(),
            execution_boundary=ExecutionBoundary.LOCAL_TRUSTED,
        )
    assert "status" not in WorkPlan.model_fields
    assert "work_id" not in AgentTask.model_fields


def _security_fixture():
    module = ModuleRef(module_id="security-fixture")
    scope = ScopeRef(module=module, scope_id="private")
    schema = SchemaRef(module=module, schema_id="payload", revision=1)
    operation_ref = OperationRef(module=module, operation_id="consume", revision=1)
    bundle = ContextBundle(
        ref=ContextBundleRef(bundle_id="bundle"),
        owner_module=module,
        purpose="security-test",
        payload={"schema": schema, "canonical_json": '{"private_text":"x"}'},
        security=DataSecurityFacts(
            sensitivity=SecurityLevel.LEVEL_2,
            trust=SecurityLevel.LEVEL_3,
            scopes=frozenset({scope}),
        ),
        created_at=utc_now(),
        provenance=ContextProvenance(producer_module=module),
    )
    operation = OperationDescriptor(
        ref=operation_ref,
        name="consume",
        purpose="security predicate fixture",
        input_schema=schema,
        output_schema=schema,
        security=OperationSecurityFacts(
            risk=SecurityLevel.LEVEL_2,
            minimum_input_trust=SecurityLevel.LEVEL_2,
            maximum_input_sensitivity=SecurityLevel.LEVEL_3,
            source_scopes=frozenset({scope}),
            destination_scopes=frozenset({scope}),
            execution_boundary=ExecutionBoundary.LOCAL_TRUSTED,
        ),
        effect_semantics=EffectSemantics(
            kind=EffectKind.NONE,
            repeatability=Repeatability.REPEATABLE,
            interrupted_outcome=InterruptedOutcome.DETERMINATE,
        ),
        visibility=DiscoveryPolicyRef(policy_id="public", revision=1),
        provenance=_provenance(module),
    )
    actor = ActorSecurityFacts(
        trust=SecurityLevel.LEVEL_4,
        maximum_handled_sensitivity=SecurityLevel.LEVEL_4,
        execution_risk=SecurityLevel.LEVEL_1,
    )
    task = AgentTaskRef(plan={"plan_id": "plan"}, task_id="task")
    agent = AgentInstanceRef(instance_id="agent")
    return bundle, operation, actor, task, agent


@pytest.mark.parametrize(
    ("bundle_update", "operation_update", "actor_update", "dimension"),
    [
        ({"sensitivity": SecurityLevel.LEVEL_4}, {}, {}, "sensitivity"),
        ({"trust": SecurityLevel.LEVEL_1}, {}, {}, "trust"),
        ({}, {"risk": SecurityLevel.LEVEL_5}, {}, "risk"),
        ({}, {}, {"execution_risk": SecurityLevel.LEVEL_5}, "risk"),
    ],
)
def test_security_algebra_rejects_sensitivity_trust_and_risk(
    bundle_update,
    operation_update,
    actor_update,
    dimension,
):
    bundle, operation, actor, task, agent = _security_fixture()
    bundle = bundle.model_copy(
        update={"security": bundle.security.model_copy(update=bundle_update)}
    )
    operation = operation.model_copy(
        update={"security": operation.security.model_copy(update=operation_update)}
    )
    actor = actor.model_copy(update=actor_update)
    decision = SecurityAlgebra().evaluate_operation(
        decision_ref=SecurityDecisionRef(decision_id="decision"),
        task=task,
        material=(bundle,),
        agent_ref=agent,
        agent=actor,
        operation=operation,
        actual_boundary=ExecutionBoundary.LOCAL_TRUSTED,
    )
    assert not decision.accepted
    assert dimension in {item.dimension.value for item in decision.deficits}


def test_security_algebra_rejects_scope_mismatch_and_accepts_calculator_shape():
    bundle, operation, actor, task, agent = _security_fixture()
    other = ScopeRef(module=bundle.owner_module, scope_id="other")
    mismatched = bundle.model_copy(
        update={"security": bundle.security.model_copy(update={"scopes": frozenset({other})})}
    )
    rejected = SecurityAlgebra().evaluate_operation(
        decision_ref=SecurityDecisionRef(decision_id="scope-reject"),
        task=task,
        material=(mismatched,),
        agent_ref=agent,
        agent=actor,
        operation=operation,
        actual_boundary=ExecutionBoundary.LOCAL_TRUSTED,
    )
    assert not rejected.accepted
    assert "scope" in {item.dimension.value for item in rejected.deficits}
    accepted = SecurityAlgebra().evaluate_operation(
        decision_ref=SecurityDecisionRef(decision_id="accept"),
        task=task,
        material=(bundle,),
        agent_ref=agent,
        agent=actor,
        operation=operation,
        actual_boundary=ExecutionBoundary.LOCAL_TRUSTED,
    )
    assert accepted.accepted
    assert accepted.deficits == ()


def test_agent_identity_skill_revision_and_direct_workflow_persist_independently(
    tmp_path,
):
    kernel = _kernel(tmp_path / "kernel.sqlite3")
    module = ModuleRef(module_id="agent-owner")
    skill_module = ModuleRef(module_id="skill-owner")
    agent = AgentRef(module=module, agent_id="A")
    source1 = SkillRef(module=skill_module, skill_id="X", revision=1)
    source2 = SkillRef(module=skill_module, skill_id="X", revision=2)
    visibility = DiscoveryPolicyRef(policy_id="public", revision=1)
    skill1 = SkillDefinition(
        ref=source1,
        name="X",
        purpose="fixture",
        visibility=visibility,
        provenance=_provenance(skill_module),
    )
    skill2 = skill1.model_copy(update={"ref": source2})
    kernel.register_skill(skill1)
    x1 = kernel.install_skill(
        agent=agent,
        source_skill=source1,
        skill_instance_id="installed-X",
    )
    workflow = WorkflowDefinition(
        ref=WorkflowRef(module=module, workflow_id="custom", revision=1),
        name="custom",
        purpose="direct user workflow",
        input_schema=None,
        output_schema=None,
        recipe=WorkflowRecipeRef(
            module=module,
            recipe_id="custom-recipe",
            revision=1,
        ),
        visibility=visibility,
        provenance=_provenance(module),
    )
    kernel.register_workflow(workflow)

    def definition(revision: int, skills, workflows=()):
        return AgentDefinition(
            ref=AgentDefinitionRef(agent=agent, revision=revision),
            name="A",
            description="identity fixture",
            manager_definition_ref=ModuleAgentDefinitionRef(
                module=module,
                definition_id="opaque",
            ),
            skill_instances=skills,
            direct_workflows=workflows,
            security=ActorSecurityFacts(
                trust=SecurityLevel.LEVEL_3,
                maximum_handled_sensitivity=SecurityLevel.LEVEL_3,
                execution_risk=SecurityLevel.LEVEL_1,
            ),
            visibility=visibility,
            provenance=_provenance(module),
        )

    d1 = definition(1, (x1.ref,))
    d2 = definition(2, (x1.ref,), (workflow.ref,))
    kernel.register_agent_definition(d1)
    kernel.register_agent_definition(d2)
    kernel.register_skill(skill2)
    x2 = kernel.install_skill(
        agent=agent,
        source_skill=source2,
        skill_instance_id="installed-X",
        revision=2,
    )
    d3 = definition(3, (x2.ref,))
    kernel.register_agent_definition(d3)

    assert d1.skill_instances == d2.skill_instances == (x1.ref,)
    assert d2.direct_workflows == (workflow.ref,)
    assert x1.ref != x2.ref
    assert x1.source_skill == source1
    assert x2.source_skill == source2
    assert kernel.agent_definition(d1.ref).skill_instances == (x1.ref,)
    assert kernel.agent_definition(d3.ref).skill_instances == (x2.ref,)
    assert kernel.skill_instance(x1.ref).source_skill == source1
    derived_agent = AgentRef(module=module, agent_id="A-custom")
    assert derived_agent != agent
    kernel.close()


def test_concurrent_instances_and_zero_to_many_opaque_states():
    module = build_core_module()
    definition = module.agent_definition(CORE_AGENT_DEFINITION)
    manager = module.agent_manager(CORE_AGENT_DEFINITION)
    assert definition is not None and manager is not None
    first = manager.instantiate(definition)
    second = manager.instantiate(definition)
    assert first.definition == second.definition == CORE_AGENT_DEFINITION
    assert first.ref != second.ref
    assert first.state_refs == second.state_refs == ()
    with_states = AgentInstance(
        ref=AgentInstanceRef(instance_id="states"),
        definition=definition.ref,
        manager_instance_ref=ModuleAgentInstanceRef(
            module=CORE_AGENT.module,
            instance_id="manager",
        ),
        state_refs=(
            AgentStateRef(module=CORE_AGENT.module, state_id="one"),
            AgentStateRef(module=CORE_AGENT.module, state_id="two"),
        ),
        created_at=utc_now(),
    )
    assert len(with_states.state_refs) == 2


def test_private_discovery_is_filtered_without_implying_operation_authority(tmp_path):
    private = DiscoveryPolicyRef(policy_id="private-fixture", revision=1)
    module_ref = ModuleRef(module_id="private-module")
    module = InProcessModule(
        manifest=ModuleManifest(
            module=module_ref,
            revision=1,
            name="Private",
            description="private discovery fixture",
            visibility=private,
        ),
        schemas={},
    )
    kernel = Kernel(
        store=KernelStore(tmp_path / "kernel.sqlite3"),
        runtime=_runtime_client(),
        discovery=DiscoveryPolicy((DiscoveryRule(private, frozenset({"madre-core"})),)),
    )
    kernel.register_module(module)
    assert kernel.discover_modules("unauthorized") == ()
    assert [item.module for item in kernel.discover_modules("madre-core")] == [module_ref]
    kernel.close()


def _build_aaaat_fixture(dispatch_log: list[str], before_uncertain=None):
    module = ModuleRef(module_id="aaaat-fixture")
    private_scope = ScopeRef(module=module, scope_id="private")
    shared_scope = ScopeRef(module=module, scope_id="minimized")
    schema = SchemaRef(module=module, schema_id="bounded-career-context", revision=1)
    bad_minimize = OperationRef(module=module, operation_id="bad-minimize", revision=1)
    minimize = OperationRef(module=module, operation_id="minimize", revision=1)
    consume = OperationRef(module=module, operation_id="consume", revision=1)
    uncertain = OperationRef(
        module=module,
        operation_id="uncertain-effect",
        revision=1,
    )
    visibility = DiscoveryPolicyRef(policy_id="public", revision=1)

    def descriptor(
        ref,
        purpose,
        maximum,
        transform=ClassificationTransform.NONE,
        repeatability=Repeatability.REPEATABLE,
    ):
        return OperationDescriptor(
            ref=ref,
            name=ref.operation_id,
            purpose=purpose,
            input_schema=schema,
            output_schema=schema,
            security=OperationSecurityFacts(
                risk=SecurityLevel.LEVEL_1,
                minimum_input_trust=SecurityLevel.LEVEL_1,
                maximum_input_sensitivity=maximum,
                source_scopes=frozenset({private_scope, shared_scope}),
                destination_scopes=frozenset({shared_scope}),
                execution_boundary=ExecutionBoundary.LOCAL_TRUSTED,
                classification_transform=transform,
            ),
            effect_semantics=EffectSemantics(
                kind=(EffectKind.EXTERNAL_EFFECT if ref == uncertain else EffectKind.NONE),
                repeatability=repeatability,
                interrupted_outcome=(
                    InterruptedOutcome.MAY_BE_UNKNOWN
                    if ref == uncertain
                    else InterruptedOutcome.DETERMINATE
                ),
            ),
            visibility=visibility,
            provenance=_provenance(module),
        )

    bad_minimize_descriptor = descriptor(
        bad_minimize,
        "invalid sensitivity lowering fixture",
        SecurityLevel.LEVEL_5,
    )
    minimize_descriptor = descriptor(
        minimize,
        "Module-owned bounded minimization",
        SecurityLevel.LEVEL_5,
        ClassificationTransform.MAY_RECALCULATE,
    )
    consume_descriptor = descriptor(
        consume,
        "low-sensitivity consumer",
        SecurityLevel.LEVEL_2,
    )
    uncertain_descriptor = descriptor(
        uncertain,
        "unknown external effect fixture",
        SecurityLevel.LEVEL_2,
        repeatability=Repeatability.UNKNOWN,
    )

    def minimized_material():
        return OperationMaterial(
            schema=schema,
            payload=PrivateFixturePayload(private_text="minimized role summary"),
            security=DataSecurityFacts(
                sensitivity=SecurityLevel.LEVEL_2,
                trust=SecurityLevel.LEVEL_4,
                scopes=frozenset({shared_scope}),
            ),
            purpose="minimized-career-context",
        )

    def bad_minimize_handler(inputs):
        dispatch_log.append("bad-minimize")
        return minimized_material()

    def minimize_handler(inputs):
        dispatch_log.append("minimize")
        return minimized_material()

    def consume_handler(inputs):
        dispatch_log.append("consume")
        payload = PrivateFixturePayload.model_validate_json(inputs[0].payload.canonical_json)
        return OperationMaterial(
            schema=schema,
            payload=payload,
            security=inputs[0].security,
            purpose="consumed-context",
        )

    def uncertain_handler(inputs):
        dispatch_log.append("uncertain")
        if before_uncertain is not None:
            before_uncertain()
        raise UnknownOperationEffect("external outcome cannot be established")

    skill1 = SkillDefinition(
        ref=SkillRef(module=module, skill_id="career-assistance", revision=1),
        name="career assistance",
        purpose="AAAAT-shaped exact upstream Skill",
        visibility=visibility,
        provenance=_provenance(module),
    )
    manifest = ModuleManifest(
        module=module,
        revision=1,
        name="AAAAT-shaped fixture",
        description="Agentless bounded career domain fixture",
        visibility=visibility,
        scopes=(
            ScopeDescriptor(
                ref=private_scope,
                name="private",
                description="private domain context",
            ),
            ScopeDescriptor(
                ref=shared_scope,
                name="minimized",
                description="minimized projection",
            ),
        ),
        operations=(bad_minimize, minimize, consume, uncertain),
        skills=(skill1.ref,),
        agents=(),
    )
    adapter = InProcessModule(
        manifest=manifest,
        schemas={schema: PrivateFixturePayload},
        operations=(
            bad_minimize_descriptor,
            minimize_descriptor,
            consume_descriptor,
            uncertain_descriptor,
        ),
        skills=(skill1,),
        handlers={
            ref_key(bad_minimize): bad_minimize_handler,
            ref_key(minimize): minimize_handler,
            ref_key(consume): consume_handler,
            ref_key(uncertain): uncertain_handler,
        },
    )
    return (
        adapter,
        schema,
        private_scope,
        shared_scope,
        bad_minimize,
        minimize,
        consume,
        uncertain,
        skill1,
    )


def test_aaaat_shaped_skill_pinning_minimization_and_unknown_effect(tmp_path):
    dispatch_log = []
    before_unknown = []
    kernel = _kernel(tmp_path / "kernel.sqlite3")

    def observe_unknown_dispatch():
        before_unknown.append(kernel.operation_invocations())

    core = build_core_module()
    (
        fixture,
        schema,
        private_scope,
        shared_scope,
        bad_minimize,
        minimize,
        consume,
        uncertain,
        skill1,
    ) = _build_aaaat_fixture(dispatch_log, observe_unknown_dispatch)
    kernel.register_module(core)
    kernel.register_module(fixture)
    kernel.assign_core("madre-core")
    installed = kernel.install_skill(
        agent=CORE_AGENT,
        source_skill=skill1.ref,
        skill_instance_id="aaaat-installed",
    )
    skill2 = skill1.model_copy(update={"ref": skill1.ref.model_copy(update={"revision": 2})})
    kernel.register_skill(skill2)
    assert installed.source_skill == skill1.ref

    plan, task = kernel.create_work_plan(
        owner_module_id="aaaat-fixture",
        schema=schema,
        objective=PrivateFixturePayload(private_text="salary=private; role=engineer"),
        security=DataSecurityFacts(
            sensitivity=SecurityLevel.LEVEL_4,
            trust=SecurityLevel.LEVEL_4,
            scopes=frozenset({private_scope}),
        ),
    )
    task = kernel.bind_task_agent(task.ref)

    with pytest.raises(SecurityRejectedError):
        asyncio.run(
            kernel.invoke_operation(
                task_ref=task.ref,
                operation=consume,
                input_contexts=(plan.objective,),
            )
        )
    assert "consume" not in dispatch_log
    rejected = next(
        record for record in kernel.operation_invocations() if record.operation == consume
    )
    assert rejected.dispatched_at is None
    assert rejected.outcome is not None
    assert rejected.outcome.kind is OperationOutcomeKind.SECURITY_REJECTED

    with pytest.raises(ValueError, match="MAY_RECALCULATE"):
        asyncio.run(
            kernel.invoke_operation(
                task_ref=task.ref,
                operation=bad_minimize,
                input_contexts=(plan.objective,),
            )
        )
    invalid_derivation = next(
        record for record in kernel.operation_invocations() if record.operation == bad_minimize
    )
    assert invalid_derivation.dispatched_at is not None
    assert invalid_derivation.outcome is not None
    assert invalid_derivation.outcome.kind is OperationOutcomeKind.FAILURE

    derived_ref = asyncio.run(
        kernel.invoke_operation(
            task_ref=task.ref,
            operation=minimize,
            input_contexts=(plan.objective,),
        )
    )[0]
    derived = kernel.inspect_context(derived_ref)
    assert derived is not None
    assert derived.ref != plan.objective
    assert derived.derived_from == (plan.objective,)
    assert derived.derivation_operation is not None
    assert derived.security.sensitivity == SecurityLevel.LEVEL_2
    assert derived.security.scopes == frozenset({shared_scope})

    accepted_ref = asyncio.run(
        kernel.invoke_operation(
            task_ref=task.ref,
            operation=consume,
            input_contexts=(derived.ref,),
        )
    )[0]
    assert kernel.inspect_context(accepted_ref) is not None

    with pytest.raises(UnknownOperationEffect):
        asyncio.run(
            kernel.invoke_operation(
                task_ref=task.ref,
                operation=uncertain,
                input_contexts=(derived.ref,),
            )
        )
    assert len(before_unknown) == 1
    pending = [record for record in before_unknown[0] if record.outcome is None]
    assert len(pending) == 1
    assert pending[0].operation == uncertain
    assert pending[0].dispatched_at is not None

    unknown = next(
        record for record in kernel.operation_invocations() if record.operation == uncertain
    )
    descriptor = fixture.operation(uncertain)
    assert descriptor is not None
    assert unknown.outcome is not None
    assert unknown.outcome.kind is OperationOutcomeKind.UNKNOWN_EXTERNAL_EFFECT
    assert not repeat_permitted(descriptor, unknown)
    kernel.close()


def test_core_replacement_changes_future_fallback_without_rewriting_existing_binding(
    tmp_path,
):
    kernel = _kernel(tmp_path / "kernel.sqlite3")
    calc = build_calculator_module()
    core = build_core_module()
    kernel.register_module(calc)
    kernel.register_module(core)
    kernel.assign_core("madre-core")

    support_module = ModuleRef(module_id="support-skill")
    support_skill = SkillDefinition(
        ref=SkillRef(module=support_module, skill_id="stable", revision=1),
        name="stable",
        purpose="CORE replacement identity fixture",
        visibility=DiscoveryPolicyRef(policy_id="public", revision=1),
        provenance=_provenance(support_module),
    )
    kernel.register_skill(support_skill)
    installed = kernel.install_skill(
        agent=CORE_AGENT,
        source_skill=support_skill.ref,
        skill_instance_id="stable-install",
    )
    workflow = WorkflowDefinition(
        ref=WorkflowRef(module=CORE_AGENT.module, workflow_id="stable", revision=1),
        name="stable",
        purpose="CORE replacement Workflow fixture",
        input_schema=None,
        output_schema=None,
        recipe=WorkflowRecipeRef(
            module=CORE_AGENT.module,
            recipe_id="stable-recipe",
            revision=1,
        ),
        visibility=DiscoveryPolicyRef(policy_id="public", revision=1),
        provenance=_provenance(CORE_AGENT.module),
    )
    kernel.register_workflow(workflow)

    plan, task = kernel.create_work_plan(
        owner_module_id="calc",
        schema=CALC_OBJECTIVE_SCHEMA,
        objective=ObjectiveText(text="old binding"),
        security=DataSecurityFacts(
            sensitivity=SecurityLevel.LEVEL_1,
            trust=SecurityLevel.LEVEL_3,
            scopes=frozenset({CALC_SCOPE}),
        ),
    )
    bound = kernel.bind_task_agent(task.ref)
    old_definition = bound.resolved_agent

    replacement_module = ModuleRef(module_id="replacement-core")
    replacement_agent = AgentRef(module=replacement_module, agent_id="fallback")
    replacement_ref = AgentDefinitionRef(agent=replacement_agent, revision=1)
    replacement_definition = AgentDefinition(
        ref=replacement_ref,
        name="replacement",
        description="replacement CORE fixture",
        manager_definition_ref=ModuleAgentDefinitionRef(
            module=replacement_module,
            definition_id="opaque",
        ),
        security=ActorSecurityFacts(
            trust=SecurityLevel.LEVEL_4,
            maximum_handled_sensitivity=SecurityLevel.LEVEL_4,
            execution_risk=SecurityLevel.LEVEL_1,
        ),
        visibility=DiscoveryPolicyRef(policy_id="public", revision=1),
        provenance=_provenance(replacement_module),
    )
    replacement = InProcessModule(
        manifest=ModuleManifest(
            module=replacement_module,
            revision=1,
            name="replacement",
            description="replacement CORE fixture",
            visibility=DiscoveryPolicyRef(policy_id="public", revision=1),
            agents=(replacement_ref,),
        ),
        schemas={},
        agents=(replacement_definition,),
    )
    kernel.register_module(replacement)
    kernel.assign_core("replacement-core")

    assert kernel.resolve_agent_definition(bound.agent_requirement).ref == replacement_ref
    assert kernel.inspect_task(task.ref).resolved_agent == old_definition
    assert kernel.inspect_plan(plan.ref).orchestrator_definition == old_definition
    assert kernel.agent_definition(CORE_AGENT_DEFINITION).ref.agent == CORE_AGENT
    assert kernel.skill_instance(installed.ref).ref == installed.ref
    assert kernel.workflow(workflow.ref).ref == workflow.ref
    kernel.close()


def test_complete_calculator_agentic_loop_uses_two_real_runtime_work_records(
    tmp_path,
    monkeypatch,
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    model_calls = []

    async def fake_invoke(capability, request, constraints):
        model_calls.append(request)
        text = (
            '{"kind":"operation","operation_id":"calculate","left":173,"right":419}'
            if len(model_calls) == 1
            else '{"kind":"final","answer":"72487"}'
        )
        return ChatResult(
            text=text,
            model="test-model",
            finish_reason="stop",
            elapsed_seconds=0.01,
        )

    monkeypatch.setattr("madre.runtime.invoke_chat", fake_invoke)
    settings = Settings(data_dir=tmp_path / "runtime", capabilities=CAPABILITIES)
    app = create_app(settings)
    kernel_path = tmp_path / "kernel" / "kernel.sqlite3"

    async def exercise():
        async with app.router.lifespan_context(app):
            kernel = _kernel(kernel_path, httpx.ASGITransport(app=app))
            calc = build_calculator_module()
            assert calc.manifest.agents == ()
            assert calc.operation(CALCULATE) is not None
            kernel.register_module(calc)
            kernel.register_module(build_core_module())
            kernel.assign_core("madre-core")
            plan = await kernel.run_objective(
                owner_module_id="calc",
                schema=CALC_OBJECTIVE_SCHEMA,
                objective=ObjectiveText(text="What is 173 * 419?"),
                security=DataSecurityFacts(
                    sensitivity=SecurityLevel.LEVEL_1,
                    trust=SecurityLevel.LEVEL_3,
                    scopes=frozenset({CALC_SCOPE}),
                ),
            )
            task = kernel.store.all("agent_task", AgentTask)[0]
            invocation = kernel.operation_invocations()[0]
            links = kernel.runtime_links()
            decisions = kernel.security_decisions()
            assert plan.completion is not None
            final = kernel.inspect_context(plan.completion.context_outputs[0])
            result = kernel.inspect_context(invocation.produced_contexts[0])
            snapshot = (
                plan,
                task,
                invocation,
                links,
                decisions,
                final,
                result,
                kernel.store.count("work_plan"),
                kernel.store.count("agent_task"),
                kernel.store.count("agent_instance"),
            )
            kernel.close()
            return snapshot

    (
        plan,
        task,
        invocation,
        links,
        decisions,
        final,
        result,
        plan_count,
        task_count,
        instance_count,
    ) = asyncio.run(exercise())
    assert plan.completion is not None and task.completion is not None
    assert task.ref.plan == plan.ref
    assert task.resolved_agent == CORE_AGENT_DEFINITION
    assert plan_count == task_count == instance_count == 1
    assert invocation.operation == CALCULATE
    assert invocation.outcome is not None
    assert invocation.outcome.kind is OperationOutcomeKind.SUCCESS
    assert len(links) == 2
    assert all(link.task == task.ref for link in links)
    assert all(link.purpose is RuntimeEvidencePurpose.AGENT_REASONING for link in links)
    assert len({link.runtime_work.work_id for link in links}) == 2
    assert len(decisions) == 3
    assert all(decision.accepted for decision in decisions)
    assert result is not None and result.payload.schema == CALC_OUTPUT_SCHEMA
    assert "72487" in result.payload.canonical_json
    assert final is not None and final.payload.schema == CORE_FINAL_SCHEMA
    assert "72487" in final.payload.canonical_json
    assert len(model_calls) == 2

    with sqlite3.connect(settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute(
            "SELECT application_id, status FROM runtime_work ORDER BY queue_sequence"
        ).fetchall()
    assert rows == [
        ("madre-core", "succeeded"),
        ("madre-core", "succeeded"),
    ]

    reopened = _kernel(kernel_path)
    reopened_plan = reopened.inspect_plan(plan.ref)
    reopened_task = reopened.inspect_task(task.ref)
    assert reopened_plan == plan
    assert reopened_task == task
    assert reopened.runtime_links() == links
    assert reopened.operation_invocations() == (invocation,)
    assert reopened.inspect_context(plan.objective) is not None
    assert reopened.agent_definition(CORE_AGENT_DEFINITION) is not None
    reopened.close()

    with sqlite3.connect(kernel_path) as connection:
        tables = {
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table'"
            ).fetchall()
        }
    assert "semantic_record" not in tables
    assert {
        "agent_task",
        "context_bundle",
        "operation_invocation",
        "runtime_evidence_link",
        "security_decision",
        "work_plan",
    }.issubset(tables)
