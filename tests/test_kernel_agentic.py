import asyncio
import sqlite3
from pathlib import Path
from typing import Annotated, Literal
from uuid import uuid4

import httpx
import pytest
from pydantic import BaseModel, Field, TypeAdapter, ValidationError

from madre import Settings, create_app
from madre.config import CapabilityConfig
from madre.inference import ChatResult
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
    OperationDescriptor,
    OperationOutcomeKind,
    OperationRef,
    OperationSecurityFacts,
    Repeatability,
    RuntimeEvidencePurpose,
    SchemaRef,
    ScopeDescriptor,
    ScopeRef,
    SecurityDecisionRef,
    SecurityLevel,
    SemanticModel,
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
    PUBLIC_DISCOVERY,
    AgentExecutionServices,
    InProcessModule,
    OperationMaterial,
    UnknownOperationEffect,
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

DOMAIN_MODULE = ModuleRef(module_id="test-domain")
DOMAIN_SCOPE = ScopeRef(module=DOMAIN_MODULE, scope_id="content")
OBJECTIVE_SCHEMA = SchemaRef(module=DOMAIN_MODULE, schema_id="objective", revision=1)
OPERATION_INPUT_SCHEMA = SchemaRef(
    module=DOMAIN_MODULE,
    schema_id="operation-input",
    revision=1,
)
OPERATION_OUTPUT_SCHEMA = SchemaRef(
    module=DOMAIN_MODULE,
    schema_id="operation-output",
    revision=1,
)
TRANSFORM = OperationRef(module=DOMAIN_MODULE, operation_id="transform", revision=1)

FALLBACK_MODULE = ModuleRef(module_id="test-fallback-provider")
FALLBACK_AGENT = AgentRef(module=FALLBACK_MODULE, agent_id="general")
FALLBACK_DEFINITION = AgentDefinitionRef(agent=FALLBACK_AGENT, revision=1)
FINAL_SCHEMA = SchemaRef(module=FALLBACK_MODULE, schema_id="final", revision=1)


class ObjectivePayload(SemanticModel):
    text: str = Field(min_length=1)


class TransformInput(SemanticModel):
    text: str = Field(min_length=1)


class TransformOutput(SemanticModel):
    text: str = Field(min_length=1)


class FinalPayload(SemanticModel):
    text: str = Field(min_length=1)


class BoundedPayload(SemanticModel):
    text: str = Field(min_length=1)


class _OperationTurn(SemanticModel):
    kind: Literal["operation"]
    operation_id: Annotated[str, Field(min_length=1)]
    text: Annotated[str, Field(min_length=1)]


class _FinalTurn(SemanticModel):
    kind: Literal["final"]
    text: Annotated[str, Field(min_length=1)]


_TURN: TypeAdapter[_OperationTurn | _FinalTurn] = TypeAdapter(_OperationTurn | _FinalTurn)


def _runtime_client(
    transport: httpx.AsyncBaseTransport | None = None,
) -> KernelRuntimeClient:
    return KernelRuntimeClient(
        "http://127.0.0.1:8731",
        "test-token",
        "local-chat",
        application_id="madre-kernel-test",
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


class _FallbackManager:
    def __init__(self, module: ModuleRef, final_schema: SchemaRef) -> None:
        self.module = module
        self.final_schema = final_schema

    def instantiate(self, definition: AgentDefinition) -> AgentInstance:
        return AgentInstance(
            ref=AgentInstanceRef(instance_id=uuid4().hex),
            definition=definition.ref,
            manager_instance_ref=ModuleAgentInstanceRef(
                module=self.module,
                instance_id=uuid4().hex,
            ),
            state_refs=(),
            created_at=utc_now(),
        )

    async def run_task(
        self,
        instance: AgentInstance,
        objective: ContextBundle,
        services: AgentExecutionServices,
    ) -> ContextBundleRef:
        operations = services.visible_operations()
        first = _TURN.validate_json(
            await services.reasoning(
                (
                    (
                        "system",
                        "Return a test operation request as JSON using only a visible operation.",
                    ),
                    ("user", objective.payload.canonical_json),
                ),
                (objective.ref,),
            )
        )
        if not isinstance(first, _OperationTurn):
            raise ValueError("fixture first turn must request an Operation")
        descriptor = next(
            (item for item in operations if item.ref.operation_id == first.operation_id),
            None,
        )
        if descriptor is None:
            raise ValueError("fixture requested an Operation outside discovery")
        input_ref = services.project_operation_input(
            descriptor.ref,
            TransformInput(text=first.text),
        )
        produced = await services.invoke_operation(descriptor.ref, (input_ref,))
        if len(produced) != 1:
            raise ValueError("fixture Operation must produce one ContextBundle")
        result_bundle = services.context(produced[0])
        result = TransformOutput.model_validate_json(result_bundle.payload.canonical_json)
        second = _TURN.validate_json(
            await services.reasoning(
                (
                    ("system", "Return the observed result as final JSON."),
                    ("user", result.text),
                ),
                produced,
            )
        )
        if not isinstance(second, _FinalTurn):
            raise ValueError("fixture second turn must return a final response")
        return services.emit_agent_context(
            self.final_schema,
            FinalPayload(text=second.text),
            DataSecurityFacts(
                sensitivity=result_bundle.security.sensitivity,
                trust=SecurityLevel.LEVEL_3,
                scopes=result_bundle.security.scopes,
            ),
            "fixture-final",
            produced,
        )


def _build_agentless_domain_module() -> InProcessModule:
    descriptor = OperationDescriptor(
        ref=TRANSFORM,
        name="transform",
        purpose="Test-only deterministic transformation.",
        input_schema=OPERATION_INPUT_SCHEMA,
        output_schema=OPERATION_OUTPUT_SCHEMA,
        security=OperationSecurityFacts(
            risk=SecurityLevel.LEVEL_1,
            minimum_input_trust=SecurityLevel.LEVEL_1,
            maximum_input_sensitivity=SecurityLevel.LEVEL_3,
            source_scopes=frozenset({DOMAIN_SCOPE}),
            destination_scopes=frozenset({DOMAIN_SCOPE}),
            execution_boundary=ExecutionBoundary.LOCAL_TRUSTED,
        ),
        effect_semantics=EffectSemantics(
            kind=EffectKind.NONE,
            repeatability=Repeatability.REPEATABLE,
            interrupted_outcome=InterruptedOutcome.DETERMINATE,
        ),
        visibility=PUBLIC_DISCOVERY,
        provenance=_provenance(DOMAIN_MODULE),
    )

    def project(payload: BaseModel) -> OperationMaterial:
        value = TransformInput.model_validate(payload)
        return OperationMaterial(
            schema_ref=OPERATION_INPUT_SCHEMA,
            payload=value,
            security=DataSecurityFacts(
                sensitivity=SecurityLevel.LEVEL_1,
                trust=SecurityLevel.LEVEL_3,
                scopes=frozenset({DOMAIN_SCOPE}),
            ),
            purpose="fixture-operation-input",
        )

    def transform(inputs: tuple[ContextBundle, ...]) -> OperationMaterial:
        if len(inputs) != 1 or inputs[0].payload.schema_ref != OPERATION_INPUT_SCHEMA:
            raise ValueError("transform expects one exact input ContextBundle")
        request = TransformInput.model_validate_json(inputs[0].payload.canonical_json)
        return OperationMaterial(
            schema_ref=OPERATION_OUTPUT_SCHEMA,
            payload=TransformOutput(text=request.text.upper()),
            security=DataSecurityFacts(
                sensitivity=SecurityLevel.LEVEL_1,
                trust=SecurityLevel.LEVEL_5,
                scopes=frozenset({DOMAIN_SCOPE}),
            ),
            purpose="fixture-operation-output",
        )

    return InProcessModule(
        manifest=ModuleManifest(
            module=DOMAIN_MODULE,
            revision=1,
            name="Test domain",
            description="Agentless test-only Module.",
            visibility=PUBLIC_DISCOVERY,
            scopes=(
                ScopeDescriptor(
                    ref=DOMAIN_SCOPE,
                    name="content",
                    description="Test-only content scope",
                ),
            ),
            operations=(TRANSFORM,),
            agents=(),
        ),
        schemas={
            OBJECTIVE_SCHEMA: ObjectivePayload,
            OPERATION_INPUT_SCHEMA: TransformInput,
            OPERATION_OUTPUT_SCHEMA: TransformOutput,
        },
        operations=(descriptor,),
        input_projectors={ref_key(TRANSFORM): project},
        handlers={ref_key(TRANSFORM): transform},
    )


def _build_fallback_role_module(
    module: ModuleRef = FALLBACK_MODULE,
    agent_id: str = "general",
) -> tuple[InProcessModule, AgentDefinitionRef, AgentRef, SchemaRef]:
    agent = AgentRef(module=module, agent_id=agent_id)
    definition_ref = AgentDefinitionRef(agent=agent, revision=1)
    final_schema = SchemaRef(module=module, schema_id="final", revision=1)
    definition = AgentDefinition(
        ref=definition_ref,
        name="Test fallback Agent",
        description="Test-only Agent managed by its own Module fixture.",
        manager_definition_ref=ModuleAgentDefinitionRef(
            module=module,
            definition_id="opaque-test-manager",
        ),
        security=ActorSecurityFacts(
            trust=SecurityLevel.LEVEL_4,
            maximum_handled_sensitivity=SecurityLevel.LEVEL_4,
            execution_risk=SecurityLevel.LEVEL_1,
        ),
        visibility=PUBLIC_DISCOVERY,
        provenance=_provenance(module),
    )
    adapter = InProcessModule(
        manifest=ModuleManifest(
            module=module,
            revision=1,
            name="Test fallback provider",
            description="Test-only Module occupying the CORE role.",
            visibility=PUBLIC_DISCOVERY,
            agents=(definition_ref,),
        ),
        schemas={final_schema: FinalPayload},
        agents=(definition,),
        manager=_FallbackManager(module, final_schema),
    )
    return adapter, definition_ref, agent, final_schema


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
    schema_ref = SchemaRef(module=module, schema_id="payload", revision=1)
    operation_ref = OperationRef(module=module, operation_id="consume", revision=1)
    bundle = ContextBundle(
        ref=ContextBundleRef(bundle_id="bundle"),
        owner_module=module,
        purpose="security-test",
        payload={"schema_ref": schema_ref, "canonical_json": '{"text":"x"}'},
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
        input_schema=schema_ref,
        output_schema=schema_ref,
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
        visibility=PUBLIC_DISCOVERY,
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


def test_security_algebra_rejects_scope_mismatch_and_accepts_valid_shape():
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


def test_agent_identity_skill_revision_and_direct_workflow_persist_independently(tmp_path):
    kernel = _kernel(tmp_path / "kernel.sqlite3")
    module = ModuleRef(module_id="agent-owner")
    skill_module = ModuleRef(module_id="skill-owner")
    agent = AgentRef(module=module, agent_id="A")
    source1 = SkillRef(module=skill_module, skill_id="X", revision=1)
    source2 = SkillRef(module=skill_module, skill_id="X", revision=2)
    skill1 = SkillDefinition(
        ref=source1,
        name="X",
        purpose="fixture",
        visibility=PUBLIC_DISCOVERY,
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
        visibility=PUBLIC_DISCOVERY,
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
            visibility=PUBLIC_DISCOVERY,
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
    kernel.close()


def test_concurrent_instances_and_zero_to_many_opaque_states():
    module, definition_ref, agent_ref, _ = _build_fallback_role_module()
    definition = module.agent_definition(definition_ref)
    manager = module.agent_manager(definition_ref)
    assert definition is not None and manager is not None
    first = manager.instantiate(definition)
    second = manager.instantiate(definition)
    assert first.definition == second.definition == definition_ref
    assert first.ref != second.ref
    assert first.state_refs == second.state_refs == ()
    with_states = AgentInstance(
        ref=AgentInstanceRef(instance_id="states"),
        definition=definition.ref,
        manager_instance_ref=ModuleAgentInstanceRef(
            module=agent_ref.module,
            instance_id="manager",
        ),
        state_refs=(
            AgentStateRef(module=agent_ref.module, state_id="one"),
            AgentStateRef(module=agent_ref.module, state_id="two"),
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
        discovery=DiscoveryPolicy((DiscoveryRule(private, frozenset({"requester"})),)),
    )
    kernel.register_module(module)
    assert kernel.discover_modules("unauthorized") == ()
    assert [item.module for item in kernel.discover_modules("requester")] == [module_ref]
    kernel.close()


def _build_classified_fixture(dispatch_log: list[str], before_uncertain=None):
    module = ModuleRef(module_id="classified-fixture")
    restricted_scope = ScopeRef(module=module, scope_id="restricted")
    shared_scope = ScopeRef(module=module, scope_id="shared")
    schema_ref = SchemaRef(module=module, schema_id="bounded", revision=1)
    invalid_project = OperationRef(module=module, operation_id="invalid-project", revision=1)
    project = OperationRef(module=module, operation_id="project", revision=1)
    consume = OperationRef(module=module, operation_id="consume", revision=1)
    uncertain = OperationRef(module=module, operation_id="uncertain-effect", revision=1)

    def descriptor(
        ref,
        maximum,
        transform=ClassificationTransform.NONE,
        repeatability=Repeatability.REPEATABLE,
    ):
        return OperationDescriptor(
            ref=ref,
            name=ref.operation_id,
            purpose="classified crossing fixture",
            input_schema=schema_ref,
            output_schema=schema_ref,
            security=OperationSecurityFacts(
                risk=SecurityLevel.LEVEL_1,
                minimum_input_trust=SecurityLevel.LEVEL_1,
                maximum_input_sensitivity=maximum,
                source_scopes=frozenset({restricted_scope, shared_scope}),
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
            visibility=PUBLIC_DISCOVERY,
            provenance=_provenance(module),
        )

    invalid_descriptor = descriptor(invalid_project, SecurityLevel.LEVEL_5)
    project_descriptor = descriptor(
        project,
        SecurityLevel.LEVEL_5,
        ClassificationTransform.MAY_RECALCULATE,
    )
    consume_descriptor = descriptor(consume, SecurityLevel.LEVEL_2)
    uncertain_descriptor = descriptor(
        uncertain,
        SecurityLevel.LEVEL_2,
        repeatability=Repeatability.UNKNOWN,
    )

    def projected_material():
        return OperationMaterial(
            schema_ref=schema_ref,
            payload=BoundedPayload(text="bounded projection"),
            security=DataSecurityFacts(
                sensitivity=SecurityLevel.LEVEL_2,
                trust=SecurityLevel.LEVEL_4,
                scopes=frozenset({shared_scope}),
            ),
            purpose="bounded-projection",
        )

    def invalid_handler(inputs):
        dispatch_log.append("invalid-project")
        return projected_material()

    def project_handler(inputs):
        dispatch_log.append("project")
        return projected_material()

    def consume_handler(inputs):
        dispatch_log.append("consume")
        payload = BoundedPayload.model_validate_json(inputs[0].payload.canonical_json)
        return OperationMaterial(
            schema_ref=schema_ref,
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
        ref=SkillRef(module=module, skill_id="bounded-assistance", revision=1),
        name="bounded assistance",
        purpose="exact upstream Skill fixture",
        visibility=PUBLIC_DISCOVERY,
        provenance=_provenance(module),
    )
    manifest = ModuleManifest(
        module=module,
        revision=1,
        name="Classified fixture",
        description="Agentless classified-domain fixture",
        visibility=PUBLIC_DISCOVERY,
        scopes=(
            ScopeDescriptor(
                ref=restricted_scope,
                name="restricted",
                description="restricted source context",
            ),
            ScopeDescriptor(
                ref=shared_scope,
                name="shared",
                description="bounded projected context",
            ),
        ),
        operations=(invalid_project, project, consume, uncertain),
        skills=(skill1.ref,),
        agents=(),
    )
    adapter = InProcessModule(
        manifest=manifest,
        schemas={schema_ref: BoundedPayload},
        operations=(
            invalid_descriptor,
            project_descriptor,
            consume_descriptor,
            uncertain_descriptor,
        ),
        skills=(skill1,),
        handlers={
            ref_key(invalid_project): invalid_handler,
            ref_key(project): project_handler,
            ref_key(consume): consume_handler,
            ref_key(uncertain): uncertain_handler,
        },
    )
    return (
        adapter,
        schema_ref,
        restricted_scope,
        shared_scope,
        invalid_project,
        project,
        consume,
        uncertain,
        skill1,
    )


def test_skill_pinning_projection_and_unknown_effect_are_independent(tmp_path):
    dispatch_log = []
    before_unknown = []
    kernel = _kernel(tmp_path / "kernel.sqlite3")

    def observe_unknown_dispatch():
        before_unknown.append(kernel.operation_invocations())

    fallback, _, fallback_agent, _ = _build_fallback_role_module()
    (
        fixture,
        schema_ref,
        restricted_scope,
        shared_scope,
        invalid_project,
        project,
        consume,
        uncertain,
        skill1,
    ) = _build_classified_fixture(dispatch_log, observe_unknown_dispatch)
    kernel.register_module(fallback)
    kernel.register_module(fixture)
    kernel.assign_core(fallback.manifest.module.module_id)
    installed = kernel.install_skill(
        agent=fallback_agent,
        source_skill=skill1.ref,
        skill_instance_id="installed-skill",
    )
    skill2 = skill1.model_copy(update={"ref": skill1.ref.model_copy(update={"revision": 2})})
    kernel.register_skill(skill2)
    assert installed.source_skill == skill1.ref

    plan, task = kernel.create_work_plan(
        owner_module_id=fixture.manifest.module.module_id,
        schema_ref=schema_ref,
        objective=BoundedPayload(text="restricted source material"),
        security=DataSecurityFacts(
            sensitivity=SecurityLevel.LEVEL_4,
            trust=SecurityLevel.LEVEL_4,
            scopes=frozenset({restricted_scope}),
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
                operation=invalid_project,
                input_contexts=(plan.objective,),
            )
        )
    invalid = next(
        record
        for record in kernel.operation_invocations()
        if record.operation == invalid_project
    )
    assert invalid.dispatched_at is not None
    assert invalid.outcome is not None
    assert invalid.outcome.kind is OperationOutcomeKind.FAILURE

    derived_ref = asyncio.run(
        kernel.invoke_operation(
            task_ref=task.ref,
            operation=project,
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


def test_core_role_reassignment_changes_future_fallback_without_rewriting_history(tmp_path):
    kernel = _kernel(tmp_path / "kernel.sqlite3")
    domain = _build_agentless_domain_module()
    first, first_definition, _, _ = _build_fallback_role_module()
    kernel.register_module(domain)
    kernel.register_module(first)
    kernel.assign_core(first.manifest.module.module_id)

    plan, task = kernel.create_work_plan(
        owner_module_id=DOMAIN_MODULE.module_id,
        schema_ref=OBJECTIVE_SCHEMA,
        objective=ObjectivePayload(text="old binding"),
        security=DataSecurityFacts(
            sensitivity=SecurityLevel.LEVEL_1,
            trust=SecurityLevel.LEVEL_3,
            scopes=frozenset({DOMAIN_SCOPE}),
        ),
    )
    bound = kernel.bind_task_agent(task.ref)
    assert bound.resolved_agent == first_definition

    replacement_module = ModuleRef(module_id="replacement-fallback-provider")
    replacement, replacement_definition, _, _ = _build_fallback_role_module(
        replacement_module,
        "replacement",
    )
    kernel.register_module(replacement)
    kernel.assign_core(replacement_module.module_id)

    assert kernel.resolve_agent_definition(bound.agent_requirement).ref == replacement_definition
    assert kernel.inspect_task(task.ref).resolved_agent == first_definition
    assert kernel.inspect_plan(plan.ref).orchestrator_definition == first_definition
    kernel.close()


def test_complete_vertical_loop_uses_test_only_modules_and_ordinary_runtime_work(
    tmp_path,
    monkeypatch,
):
    monkeypatch.setenv("MADRE_API_TOKEN", "test-token")
    model_calls = []

    async def fake_invoke(capability, request, constraints):
        model_calls.append(request)
        text = (
            '{"kind":"operation","operation_id":"transform","text":"bounded input"}'
            if len(model_calls) == 1
            else '{"kind":"final","text":"BOUNDED INPUT"}'
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
            domain = _build_agentless_domain_module()
            fallback, fallback_definition, _, final_schema = _build_fallback_role_module()
            assert domain.manifest.agents == ()
            kernel.register_module(domain)
            kernel.register_module(fallback)
            kernel.assign_core(fallback.manifest.module.module_id)
            plan = await kernel.run_objective(
                owner_module_id=DOMAIN_MODULE.module_id,
                schema_ref=OBJECTIVE_SCHEMA,
                objective=ObjectivePayload(text="Transform bounded input"),
                security=DataSecurityFacts(
                    sensitivity=SecurityLevel.LEVEL_1,
                    trust=SecurityLevel.LEVEL_3,
                    scopes=frozenset({DOMAIN_SCOPE}),
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
                fallback_definition,
                final_schema,
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
        fallback_definition,
        final_schema,
    ) = asyncio.run(exercise())
    assert plan.completion is not None and task.completion is not None
    assert task.ref.plan == plan.ref
    assert task.resolved_agent == fallback_definition
    assert invocation.operation == TRANSFORM
    assert invocation.outcome is not None
    assert invocation.outcome.kind is OperationOutcomeKind.SUCCESS
    assert len(links) == 2
    assert all(link.task == task.ref for link in links)
    assert all(link.purpose is RuntimeEvidencePurpose.AGENT_REASONING for link in links)
    assert len({link.runtime_work.work_id for link in links}) == 2
    assert len(decisions) == 3
    assert all(decision.accepted for decision in decisions)
    assert result is not None and result.payload.schema_ref == OPERATION_OUTPUT_SCHEMA
    assert "BOUNDED INPUT" in result.payload.canonical_json
    assert final is not None and final.payload.schema_ref == final_schema
    assert "BOUNDED INPUT" in final.payload.canonical_json
    assert len(model_calls) == 2

    with sqlite3.connect(settings.data_dir / "runtime.sqlite3") as connection:
        rows = connection.execute(
            "SELECT application_id, status FROM runtime_work ORDER BY queue_sequence"
        ).fetchall()
    assert rows == [
        ("madre-kernel-test", "succeeded"),
        ("madre-kernel-test", "succeeded"),
    ]

    reopened = _kernel(kernel_path)
    assert reopened.inspect_plan(plan.ref) == plan
    assert reopened.inspect_task(task.ref) == task
    assert reopened.runtime_links() == links
    assert reopened.operation_invocations() == (invocation,)
    assert reopened.inspect_context(plan.objective) is not None
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
