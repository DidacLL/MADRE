"""Trusted Kernel orchestration and Operation gateway for the first vertical AgenticLoop."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from uuid import uuid4

from pydantic import BaseModel

from madre_kernel.contracts import (
    AgentDefinition,
    AgentDefinitionRef,
    AgentInstance,
    AgentInstanceRef,
    AgentRef,
    AgentRequirement,
    AgentSkillInstance,
    AgentSkillInstanceRef,
    AgentTask,
    AgentTaskRef,
    ClassificationTransform,
    ContextBundle,
    ContextBundleRef,
    ContextProvenance,
    CoreRoleAssignment,
    DataSecurityFacts,
    DiscoveryPolicyRef,
    ExecutionBoundary,
    ModuleManifest,
    ModuleRef,
    OperationDescriptor,
    OperationInvocationRecord,
    OperationInvocationRef,
    OperationOutcome,
    OperationOutcomeKind,
    OperationRef,
    OwnerPlanOrigin,
    PlanCompletion,
    RuntimeEvidenceLink,
    RuntimeEvidencePurpose,
    SchemaRef,
    SecurityDecisionEvidence,
    SecurityDecisionRef,
    SkillDefinition,
    SkillInstanceProvenance,
    SkillRef,
    TaskCompletion,
    WorkflowDefinition,
    WorkflowRef,
    WorkPlan,
    WorkPlanRef,
    utc_now,
)
from madre_kernel.modules import (
    AgentExecutionServices,
    ModuleAdapter,
    SchemaCodecRegistry,
    UnknownOperationEffect,
    ref_key,
)
from madre_kernel.runtime_client import KernelRuntimeClient
from madre_kernel.security import SecurityAlgebra
from madre_kernel.storage import KernelStore


class SecurityRejectedError(RuntimeError):
    def __init__(self, decision: SecurityDecisionEvidence) -> None:
        super().__init__("Kernel security algebra rejected the crossing")
        self.decision = decision


@dataclass(frozen=True)
class DiscoveryRule:
    policy: DiscoveryPolicyRef
    visible_to: frozenset[str] | None


class DiscoveryPolicy:
    def __init__(self, rules: Sequence[DiscoveryRule] = ()) -> None:
        self._rules = {ref_key(rule.policy): rule for rule in rules}

    def visible(
        self,
        policy: DiscoveryPolicyRef,
        requester_module_id: str | None,
    ) -> bool:
        if policy.policy_id == "public":
            return True
        rule = self._rules.get(ref_key(policy))
        return (
            rule is not None
            and rule.visible_to is not None
            and requester_module_id in rule.visible_to
        )


class Kernel:
    def __init__(
        self,
        *,
        store: KernelStore,
        runtime: KernelRuntimeClient,
        security: SecurityAlgebra | None = None,
        discovery: DiscoveryPolicy | None = None,
    ) -> None:
        self.store = store
        self.runtime = runtime
        self.security = security or SecurityAlgebra()
        self.discovery = discovery or DiscoveryPolicy()
        self.codecs = SchemaCodecRegistry()
        self._modules: dict[str, ModuleAdapter] = {}

    def close(self) -> None:
        self.store.close()

    def register_module(self, module: ModuleAdapter) -> None:
        self._modules[ref_key(module.manifest.module)] = module
        self.store.put(
            "module_manifest",
            ref_key(module.manifest.module),
            module.manifest,
        )
        for schema, model in module.schemas().items():
            self.codecs.register(schema, model)
        for operation_ref in module.manifest.operations:
            descriptor = module.operation(operation_ref)
            if descriptor is None:
                raise ValueError("manifest OperationRef does not resolve through its Module")
            self.store.put("operation", ref_key(descriptor.ref), descriptor)
        for skill_ref in module.manifest.skills:
            skill = module.skill(skill_ref)
            if skill is None:
                raise ValueError("manifest SkillRef does not resolve through its Module")
            self.register_skill(skill)
            for workflow_ref in skill.workflow_refs:
                workflow = module.workflow(workflow_ref)
                if workflow is None:
                    raise ValueError("SkillDefinition pins an unresolved WorkflowDefinition")
                self.register_workflow(workflow)
        for agent_ref in module.manifest.agents:
            definition = module.agent_definition(agent_ref)
            if definition is None:
                raise ValueError(
                    "manifest AgentDefinitionRef does not resolve through its Module"
                )
            for instance_ref in definition.skill_instances:
                instance = module.agent_skill_instance(instance_ref)
                if instance is None:
                    raise ValueError(
                        "AgentDefinition pins an unresolved AgentSkillInstance"
                    )
                self.register_skill_instance(instance)
            for workflow_ref in definition.direct_workflows:
                workflow = module.workflow(workflow_ref)
                if workflow is None:
                    raise ValueError(
                        "AgentDefinition pins an unresolved direct WorkflowDefinition"
                    )
                self.register_workflow(workflow)
            self.register_agent_definition(definition)

    def assign_core(self, module_id: str) -> CoreRoleAssignment:
        module = self._module_by_id(module_id)
        assignment = CoreRoleAssignment(
            module=module.manifest.module,
            assigned_at=utc_now(),
            policy_revision=self.security.policy.revision,
        )
        self.store.put("core_role", "active", assignment)
        return assignment

    def core_assignment(self) -> CoreRoleAssignment | None:
        return self.store.get("core_role", "active", CoreRoleAssignment)

    def discover_modules(
        self,
        requester_module_id: str | None,
    ) -> tuple[ModuleManifest, ...]:
        return tuple(
            module.manifest
            for module in self._modules.values()
            if self.discovery.visible(
                module.manifest.visibility,
                requester_module_id,
            )
        )

    def discover_operations(
        self,
        requester_module_id: str | None,
    ) -> tuple[OperationDescriptor, ...]:
        result: list[OperationDescriptor] = []
        for module in self._modules.values():
            if not self.discovery.visible(
                module.manifest.visibility,
                requester_module_id,
            ):
                continue
            for operation_ref in module.manifest.operations:
                descriptor = module.operation(operation_ref)
                if descriptor is not None and self.discovery.visible(
                    descriptor.visibility,
                    requester_module_id,
                ):
                    result.append(descriptor)
        return tuple(result)

    def inspect_plan(self, ref: WorkPlanRef) -> WorkPlan | None:
        return self.store.get("work_plan", ref_key(ref), WorkPlan)

    def inspect_task(self, ref: AgentTaskRef) -> AgentTask | None:
        return self.store.get("agent_task", ref_key(ref), AgentTask)

    def inspect_context(self, ref: ContextBundleRef) -> ContextBundle | None:
        return self.store.get("context", ref_key(ref), ContextBundle)

    def inspect_instance(self, ref: AgentInstanceRef) -> AgentInstance | None:
        return self.store.get("agent_instance", ref_key(ref), AgentInstance)

    def operation_invocations(self) -> tuple[OperationInvocationRecord, ...]:
        return self.store.all("operation_invocation", OperationInvocationRecord)

    def runtime_links(self) -> tuple[RuntimeEvidenceLink, ...]:
        return self.store.all("runtime_link", RuntimeEvidenceLink)

    def security_decisions(self) -> tuple[SecurityDecisionEvidence, ...]:
        return self.store.all("security_decision", SecurityDecisionEvidence)

    def resolve_agent_definition(self, requirement: AgentRequirement) -> AgentDefinition:
        if requirement.preferred_definition is not None:
            definition = self._definition(requirement.preferred_definition)
            if definition is None:
                raise LookupError("preferred AgentDefinition does not resolve exactly")
            return definition
        assignment = self.core_assignment()
        if assignment is None:
            raise LookupError("no CORE role is assigned for fallback Agent resolution")
        module = self._module(assignment.module)
        for ref in module.manifest.agents:
            definition = module.agent_definition(ref)
            if definition is not None and self.discovery.visible(
                definition.visibility,
                None,
            ):
                return definition
        raise LookupError("assigned CORE Module exposes no fallback AgentDefinition")

    def create_work_plan(
        self,
        *,
        owner_module_id: str,
        schema: SchemaRef,
        objective: BaseModel,
        security: DataSecurityFacts,
        requirement: AgentRequirement | None = None,
    ) -> tuple[WorkPlan, AgentTask]:
        owner = self._module_by_id(owner_module_id).manifest.module
        objective_bundle = self.create_context(
            owner=owner,
            schema=schema,
            payload=objective,
            security=security,
            purpose="work-plan-objective",
        )
        now = utc_now()
        plan_ref = WorkPlanRef(plan_id=uuid4().hex)
        agent_requirement = requirement or AgentRequirement()
        plan = WorkPlan(
            ref=plan_ref,
            objective=objective_bundle.ref,
            origin=OwnerPlanOrigin(request_context=objective_bundle.ref),
            orchestrator_requirement=agent_requirement,
            created_at=now,
            updated_at=now,
        )
        task = AgentTask(
            ref=AgentTaskRef(plan=plan_ref, task_id=uuid4().hex),
            parent_task=None,
            objective=objective_bundle.ref,
            agent_requirement=agent_requirement,
            context_refs=(objective_bundle.ref,),
            created_at=now,
        )
        self.store.put("work_plan", ref_key(plan.ref), plan)
        self.store.put("agent_task", ref_key(task.ref), task)
        return plan, task

    def bind_task_agent(self, task_ref: AgentTaskRef) -> AgentTask:
        task = self.inspect_task(task_ref)
        if task is None:
            raise LookupError("AgentTask does not resolve")
        definition = self.resolve_agent_definition(task.agent_requirement)
        responsible_module = self._module(definition.ref.agent.module)
        manager = responsible_module.agent_manager(definition.ref)
        if manager is None:
            raise LookupError("resolved AgentDefinition has no Module Agent manager")
        instance = manager.instantiate(definition)
        self.store.put("agent_instance", ref_key(instance.ref), instance)
        task = task.model_copy(
            update={
                "resolved_agent": definition.ref,
                "agent_instance": instance.ref,
                "skill_instances": definition.skill_instances,
            }
        )
        self.store.put("agent_task", ref_key(task.ref), task)
        plan = self.inspect_plan(task.ref.plan)
        if plan is not None:
            plan = plan.model_copy(
                update={
                    "orchestrator_definition": definition.ref,
                    "updated_at": utc_now(),
                }
            )
            self.store.put("work_plan", ref_key(plan.ref), plan)
        return task

    async def run_objective(
        self,
        *,
        owner_module_id: str,
        schema: SchemaRef,
        objective: BaseModel,
        security: DataSecurityFacts,
        requirement: AgentRequirement | None = None,
    ) -> WorkPlan:
        plan, task = self.create_work_plan(
            owner_module_id=owner_module_id,
            schema=schema,
            objective=objective,
            security=security,
            requirement=requirement,
        )
        task = self.bind_task_agent(task.ref)
        if task.resolved_agent is None or task.agent_instance is None:
            raise RuntimeError("bound AgentTask is missing exact Agent binding")
        definition = self._definition(task.resolved_agent)
        instance = self.inspect_instance(task.agent_instance)
        if definition is None or instance is None:
            raise RuntimeError("bound AgentTask references missing Agent evidence")
        manager = self._module(definition.ref.agent.module).agent_manager(definition.ref)
        if manager is None:
            raise RuntimeError("bound Agent manager disappeared")
        objective_bundle = self.inspect_context(task.objective)
        if objective_bundle is None:
            raise RuntimeError("AgentTask objective ContextBundle disappeared")
        services = _TaskServices(self, task, definition, instance)
        final_ref = await manager.run_task(instance, objective_bundle, services)
        completed_at = utc_now()
        task = task.model_copy(
            update={
                "completion": TaskCompletion(
                    completed_at=completed_at,
                    context_outputs=(final_ref,),
                )
            }
        )
        plan = plan.model_copy(
            update={
                "orchestrator_definition": definition.ref,
                "updated_at": completed_at,
                "completion": PlanCompletion(
                    completed_at=completed_at,
                    context_outputs=(final_ref,),
                ),
            }
        )
        self.store.put("agent_task", ref_key(task.ref), task)
        self.store.put("work_plan", ref_key(plan.ref), plan)
        return plan

    def create_context(
        self,
        *,
        owner: ModuleRef,
        schema: SchemaRef,
        payload: BaseModel,
        security: DataSecurityFacts,
        purpose: str,
        derived_from: Sequence[ContextBundleRef] = (),
        derivation_operation: OperationInvocationRef | None = None,
    ) -> ContextBundle:
        if ref_key(owner) not in self._modules:
            raise LookupError("Context owner Module is not registered")
        typed = self.codecs.encode(schema, payload)
        bundle = ContextBundle(
            ref=ContextBundleRef(bundle_id=uuid4().hex),
            owner_module=owner,
            purpose=purpose,
            payload=typed,
            security=security,
            created_at=utc_now(),
            derived_from=tuple(derived_from),
            derivation_operation=derivation_operation,
            provenance=ContextProvenance(
                producer_module=owner,
                source_contexts=tuple(derived_from),
            ),
        )
        self.codecs.validate(bundle.payload)
        self.store.put("context", ref_key(bundle.ref), bundle)
        return bundle

    async def invoke_operation(
        self,
        *,
        task_ref: AgentTaskRef,
        operation: OperationRef,
        input_contexts: Sequence[ContextBundleRef],
    ) -> tuple[ContextBundleRef, ...]:
        task = self.inspect_task(task_ref)
        if task is None or task.resolved_agent is None or task.agent_instance is None:
            raise LookupError("Operation invocation requires a bound AgentTask")
        definition = self._definition(task.resolved_agent)
        instance = self.inspect_instance(task.agent_instance)
        if definition is None or instance is None:
            raise LookupError("bound Agent evidence does not resolve")
        return await _TaskServices(
            self,
            task,
            definition,
            instance,
        ).invoke_operation(operation, input_contexts)

    def install_skill(
        self,
        *,
        agent: AgentRef,
        source_skill: SkillRef,
        skill_instance_id: str,
        revision: int = 1,
        adopted_workflows: Sequence[WorkflowRef] = (),
    ) -> AgentSkillInstance:
        skill = self.skill(source_skill)
        if skill is None:
            raise LookupError("source SkillDefinition does not resolve exactly")
        if any(workflow not in skill.workflow_refs for workflow in adopted_workflows):
            raise ValueError(
                "adopted Workflow must be published by the exact source Skill revision"
            )
        instance = AgentSkillInstance(
            ref=AgentSkillInstanceRef(
                agent=agent,
                skill_instance_id=skill_instance_id,
                revision=revision,
            ),
            source_skill=source_skill,
            adopted_workflows=tuple(adopted_workflows),
            created_at=utc_now(),
            provenance=SkillInstanceProvenance(
                created_by=agent.module,
                source_skill=source_skill,
            ),
        )
        self.register_skill_instance(instance)
        return instance

    def register_workflow(self, workflow: WorkflowDefinition) -> None:
        self.store.put("workflow", ref_key(workflow.ref), workflow)

    def register_skill(self, skill: SkillDefinition) -> None:
        self.store.put("skill", ref_key(skill.ref), skill)

    def register_skill_instance(self, instance: AgentSkillInstance) -> None:
        self.store.put("agent_skill_instance", ref_key(instance.ref), instance)

    def register_agent_definition(self, definition: AgentDefinition) -> None:
        for instance_ref in definition.skill_instances:
            instance = self.skill_instance(instance_ref)
            if instance is None:
                raise ValueError("AgentDefinition pins an unresolved AgentSkillInstance")
            if instance.ref.agent != definition.ref.agent:
                raise ValueError(
                    "AgentDefinition cannot pin another logical Agent's Skill instance"
                )
        self.store.put("agent_definition", ref_key(definition.ref), definition)

    def skill(self, ref: SkillRef) -> SkillDefinition | None:
        return self.store.get("skill", ref_key(ref), SkillDefinition)

    def workflow(self, ref: WorkflowRef) -> WorkflowDefinition | None:
        return self.store.get("workflow", ref_key(ref), WorkflowDefinition)

    def skill_instance(self, ref: AgentSkillInstanceRef) -> AgentSkillInstance | None:
        return self.store.get(
            "agent_skill_instance",
            ref_key(ref),
            AgentSkillInstance,
        )

    def agent_definition(self, ref: AgentDefinitionRef) -> AgentDefinition | None:
        return self.store.get("agent_definition", ref_key(ref), AgentDefinition)

    def _module_by_id(self, module_id: str) -> ModuleAdapter:
        for module in self._modules.values():
            if module.manifest.module.module_id == module_id:
                return module
        raise LookupError(f"Module is not registered: {module_id}")

    def _module(self, ref: ModuleRef) -> ModuleAdapter:
        module = self._modules.get(ref_key(ref))
        if module is None:
            raise LookupError("referenced Module is not registered")
        return module

    def _definition(self, ref: AgentDefinitionRef) -> AgentDefinition | None:
        module = self._module(ref.agent.module)
        return module.agent_definition(ref) or self.agent_definition(ref)


class _TaskServices(AgentExecutionServices):
    def __init__(
        self,
        kernel: Kernel,
        task: AgentTask,
        definition: AgentDefinition,
        instance: AgentInstance,
    ) -> None:
        self.kernel = kernel
        self.task = task
        self.definition = definition
        self.instance = instance

    async def reasoning(
        self,
        messages: Sequence[tuple[str, str]],
        material: Sequence[ContextBundleRef],
    ) -> str:
        bundles = tuple(self.context(ref) for ref in material)
        decision = self.kernel.security.evaluate_agent_context(
            decision_ref=SecurityDecisionRef(decision_id=uuid4().hex),
            task=self.task.ref,
            material=bundles,
            agent_ref=self.instance.ref,
            agent=self.definition.security,
            actual_boundary=ExecutionBoundary.LOCAL_TRUSTED,
        )
        self.kernel.store.put(
            "security_decision",
            ref_key(decision.ref),
            decision,
        )
        if not decision.accepted:
            raise SecurityRejectedError(decision)
        result = await self.kernel.runtime.reason(messages)
        link = RuntimeEvidenceLink(
            task=self.task.ref,
            runtime_work=result.work,
            operation_invocation=None,
            purpose=RuntimeEvidencePurpose.AGENT_REASONING,
            created_at=utc_now(),
        )
        existing = self.kernel.store.get(
            "runtime_link",
            ref_key(result.work),
            RuntimeEvidenceLink,
        )
        if existing is not None:
            raise ValueError("Runtime WorkRecord is already linked to an AgentTask")
        self.kernel.store.put("runtime_link", ref_key(result.work), link)
        return result.text

    def visible_operations(self) -> tuple[OperationDescriptor, ...]:
        return self.kernel.discover_operations(
            self.definition.ref.agent.module.module_id
        )

    def project_operation_input(
        self,
        operation: OperationRef,
        payload: BaseModel,
    ) -> ContextBundleRef:
        module = self.kernel._module(operation.module)
        material = module.project_operation_input(operation, payload)
        descriptor = module.operation(operation)
        if descriptor is None or material.schema != descriptor.input_schema:
            raise ValueError(
                "Module projected input with a schema outside the Operation contract"
            )
        bundle = self.kernel.create_context(
            owner=operation.module,
            schema=material.schema,
            payload=material.payload,
            security=material.security,
            purpose=material.purpose,
        )
        return bundle.ref

    async def invoke_operation(
        self,
        operation: OperationRef,
        input_contexts: Sequence[ContextBundleRef],
    ) -> tuple[ContextBundleRef, ...]:
        module = self.kernel._module(operation.module)
        descriptor = module.operation(operation)
        if descriptor is None:
            raise LookupError("Operation does not resolve through its owning Module")
        bundles = tuple(self.context(ref) for ref in input_contexts)
        decision = self.kernel.security.evaluate_operation(
            decision_ref=SecurityDecisionRef(decision_id=uuid4().hex),
            task=self.task.ref,
            material=bundles,
            agent_ref=self.instance.ref,
            agent=self.definition.security,
            operation=descriptor,
            actual_boundary=ExecutionBoundary.LOCAL_TRUSTED,
        )
        self.kernel.store.put(
            "security_decision",
            ref_key(decision.ref),
            decision,
        )
        invocation_ref = OperationInvocationRef(invocation_id=uuid4().hex)
        requested_at = utc_now()
        record = OperationInvocationRecord(
            ref=invocation_ref,
            task=self.task.ref,
            operation=operation,
            requested_by=self.instance.ref,
            requested_at=requested_at,
            input_contexts=tuple(input_contexts),
            security_decision=decision.ref,
        )
        self.kernel.store.put(
            "operation_invocation",
            ref_key(record.ref),
            record,
        )

        if not decision.accepted:
            record = record.model_copy(
                update={
                    "outcome": OperationOutcome(
                        kind=OperationOutcomeKind.SECURITY_REJECTED,
                        observed_at=utc_now(),
                    )
                }
            )
            self.kernel.store.put(
                "operation_invocation",
                ref_key(record.ref),
                record,
            )
            raise SecurityRejectedError(decision)

        dispatched_at = utc_now()
        record = record.model_copy(update={"dispatched_at": dispatched_at})
        self.kernel.store.put(
            "operation_invocation",
            ref_key(record.ref),
            record,
        )

        try:
            material = await module.invoke_operation(operation, bundles)
            if material.schema != descriptor.output_schema:
                raise ValueError(
                    "Operation returned a schema outside its exact descriptor"
                )
            if bundles:
                source_sensitivity = max(
                    bundle.security.sensitivity for bundle in bundles
                )
                if (
                    material.security.sensitivity < source_sensitivity
                    and descriptor.security.classification_transform
                    is not ClassificationTransform.MAY_RECALCULATE
                ):
                    raise ValueError(
                        "lower-sensitivity derivation requires MAY_RECALCULATE"
                    )
            if not material.security.scopes.issubset(
                descriptor.security.destination_scopes
            ):
                raise ValueError(
                    "Operation output Scope is outside declared destination scopes"
                )
            output = self.kernel.create_context(
                owner=operation.module,
                schema=material.schema,
                payload=material.payload,
                security=material.security,
                purpose=material.purpose,
                derived_from=input_contexts,
                derivation_operation=invocation_ref,
            )
        except UnknownOperationEffect:
            record = record.model_copy(
                update={
                    "outcome": OperationOutcome(
                        kind=OperationOutcomeKind.UNKNOWN_EXTERNAL_EFFECT,
                        observed_at=utc_now(),
                    )
                }
            )
            self.kernel.store.put(
                "operation_invocation",
                ref_key(record.ref),
                record,
            )
            raise
        except Exception:
            record = record.model_copy(
                update={
                    "outcome": OperationOutcome(
                        kind=OperationOutcomeKind.FAILURE,
                        observed_at=utc_now(),
                        code="operation-failure",
                    )
                }
            )
            self.kernel.store.put(
                "operation_invocation",
                ref_key(record.ref),
                record,
            )
            raise

        record = record.model_copy(
            update={
                "outcome": OperationOutcome(
                    kind=OperationOutcomeKind.SUCCESS,
                    observed_at=utc_now(),
                ),
                "produced_contexts": (output.ref,),
            }
        )
        self.kernel.store.put(
            "operation_invocation",
            ref_key(record.ref),
            record,
        )
        return (output.ref,)

    def context(self, ref: ContextBundleRef) -> ContextBundle:
        value = self.kernel.inspect_context(ref)
        if value is None:
            raise LookupError("ContextBundle does not resolve")
        return value

    def emit_agent_context(
        self,
        schema: SchemaRef,
        payload: BaseModel,
        security: DataSecurityFacts,
        purpose: str,
        derived_from: Sequence[ContextBundleRef],
    ) -> ContextBundleRef:
        bundle = self.kernel.create_context(
            owner=self.definition.ref.agent.module,
            schema=schema,
            payload=payload,
            security=security,
            purpose=purpose,
            derived_from=derived_from,
        )
        return bundle.ref


def repeat_permitted(
    descriptor: OperationDescriptor,
    record: OperationInvocationRecord,
) -> bool:
    if (
        record.outcome is None
        or record.outcome.kind is OperationOutcomeKind.UNKNOWN_EXTERNAL_EFFECT
    ):
        return False
    return descriptor.effect_semantics.repeatability.value in {
        "repeatable",
        "idempotent",
    }
