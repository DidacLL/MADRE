"""Interface-segregated SDK clients over MADRE's public Kernel protocols."""

from __future__ import annotations

from datetime import datetime

from pydantic import JsonValue

from madre.contracts import (
    CorrelationEntry,
    ExecutionConstraints,
    InferenceRequirement,
    TransientInferenceRequest,
    TransientInferenceResult,
    TransientMaterial,
    WorkRecord,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.interfaces import (
    AgentBrokering,
    Discovery,
    DurableWorkSubmission,
    OperationBrokering,
    TransientInference,
    WorkInspection,
    WorkResultAccess,
)
from madre.registry import AgentDescriptor, OperationDescriptor, SkillDescriptor, WorkflowDescriptor
from madre.security import InvocationContext, SecurityHistory, SecurityObject
from madre_sdk.material import Material, MaterialRepository


class InferenceClient:
    def __init__(
        self, *, originator: str, security: SecurityHistory, inference: TransientInference
    ) -> None:
        self._originator = originator
        self._security = security
        self._inference = inference

    async def infer(
        self,
        material: Material,
        requirement: InferenceRequirement,
        *,
        invocation: InvocationContext,
        constraints: ExecutionConstraints | None = None,
        security: SecurityHistory | None = None,
    ) -> TransientInferenceResult:
        return await self._inference.infer(
            TransientInferenceRequest(
                originator=self._originator,
                security=(security or self._security).extend(objects=invocation.objects),
                inference=requirement,
                material=material.transient(),
                constraints=constraints or ExecutionConstraints(),
            )
        )


class WorkClient:
    def __init__(
        self,
        *,
        originator: str,
        security: SecurityHistory,
        submission: DurableWorkSubmission,
        materials: MaterialRepository,
    ) -> None:
        self._originator = originator
        self._security = security
        self._submission = submission
        self._materials = materials

    async def submit(
        self,
        material: Material,
        requirement: InferenceRequirement,
        *,
        invocation: InvocationContext,
        eligible_at: datetime | None = None,
        priority: int = 0,
        constraints: ExecutionConstraints | None = None,
        correlation: tuple[CorrelationEntry, ...] = (),
        idempotency_key: str | None = None,
        security: SecurityHistory | None = None,
    ) -> WorkRecord:
        handle = self._materials.retain(material)
        return await self._submission.submit(
            WorkSubmission(
                originator=self._originator,
                security=(security or self._security).extend(objects=invocation.objects),
                inference=requirement,
                material=handle,
                eligible_at=eligible_at,
                priority=priority,
                constraints=constraints or ExecutionConstraints(),
                correlation=correlation,
            ),
            idempotency_key=idempotency_key,
        )


class WorkResults:
    def __init__(self, inspection: WorkInspection, results: WorkResultAccess) -> None:
        self._inspection = inspection
        self._results = results

    def inspect(self, work_id: str) -> WorkRecord | None:
        return self._inspection.inspect(work_id)

    def consume(self, work_id: str) -> JsonValue:
        return self._results.consume_result(work_id)

    async def retry(
        self, work_id: str, *, idempotency_key: str, allow_unknown_outcome: bool = False
    ) -> WorkRecord:
        return await self._results.retry(
            work_id,
            WorkRetryRequest(allow_unknown_outcome=allow_unknown_outcome),
            idempotency_key=idempotency_key,
        )

    async def cancel(self, work_id: str) -> WorkRecord:
        return await self._results.cancel(work_id)


class DiscoveryClient:
    def __init__(self, discovery: Discovery, security: SecurityHistory) -> None:
        self._discovery = discovery
        self._security = security

    def agents(self) -> tuple[AgentDescriptor, ...]:
        return self._discovery.discover_agents(self._security)

    def skills(self) -> tuple[SkillDescriptor, ...]:
        return self._discovery.discover_skills(self._security)

    def workflows(self) -> tuple[WorkflowDescriptor, ...]:
        return self._discovery.discover_workflows(self._security)

    def operations(self) -> tuple[OperationDescriptor, ...]:
        return self._discovery.discover_operations(self._security)


class AgentBrokerClient:
    def __init__(self, *, security: SecurityHistory, broker: AgentBrokering) -> None:
        self._security = security
        self._broker = broker

    async def invoke(
        self,
        module_id: str,
        agent_id: str,
        material: Material,
        *,
        invocation: InvocationContext,
        security: SecurityHistory | None = None,
    ) -> TransientMaterial:
        return await self._broker.invoke_agent(
            invocation,
            (security or self._security).extend(objects=invocation.objects),
            module_id,
            agent_id,
            material.transient(),
        )


class OperationBrokerClient:
    def __init__(self, *, security: SecurityHistory, broker: OperationBrokering) -> None:
        self._security = security
        self._broker = broker

    async def invoke(
        self,
        module_id: str,
        operation_id: str,
        effect_profile_id: str,
        material: Material,
        *,
        invocation: InvocationContext,
        controllers: tuple[SecurityObject, ...] = (),
        security: SecurityHistory | None = None,
    ) -> TransientMaterial:
        carried = (security or self._security).extend(objects=(*controllers, *invocation.objects))
        return await self._broker.invoke_operation(
            invocation,
            carried,
            module_id,
            operation_id,
            effect_profile_id,
            material.transient(),
            tuple(item.security_id for item in controllers),
        )


class CoreSelection:
    def __init__(self, *, module_id: str, interaction_agent_id: str) -> None:
        if not module_id or not interaction_agent_id:
            raise ValueError("CORE selection identities must not be empty")
        self.module_id = module_id
        self.interaction_agent_id = interaction_agent_id


class CoreDelegate:
    def __init__(self, broker: AgentBrokerClient, selection: CoreSelection) -> None:
        self._broker = broker
        self._selection = selection

    @property
    def selection(self) -> CoreSelection:
        return self._selection

    async def interact(
        self, material: Material, *, invocation: InvocationContext
    ) -> TransientMaterial:
        return await self._broker.invoke(
            self._selection.module_id,
            self._selection.interaction_agent_id,
            material,
            invocation=invocation,
        )
