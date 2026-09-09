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
from madre.security import SecurityContext

from madre_sdk.material import Material, MaterialRepository


class InferenceClient:
    def __init__(
        self,
        *,
        originator: str,
        security: SecurityContext,
        inference: TransientInference,
    ) -> None:
        self._originator = originator
        self._security = security
        self._inference = inference

    async def infer(
        self,
        material: Material,
        requirement: InferenceRequirement,
        *,
        constraints: ExecutionConstraints | None = None,
    ) -> TransientInferenceResult:
        return await self._inference.infer(
            TransientInferenceRequest(
                originator=self._originator,
                security=self._security,
                inference=requirement,
                material=material.transient(),
                constraints=constraints or ExecutionConstraints(),
            )
        )


class WorkClient:
    """Durable submission helper; material remains in the supplied Module repository."""

    def __init__(
        self,
        *,
        originator: str,
        security: SecurityContext,
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
        eligible_at: datetime | None = None,
        priority: int = 0,
        constraints: ExecutionConstraints | None = None,
        correlation: tuple[CorrelationEntry, ...] = (),
        idempotency_key: str | None = None,
    ) -> WorkRecord:
        handle = self._materials.retain(material)
        return await self._submission.submit(
            WorkSubmission(
                originator=self._originator,
                security=self._security,
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
        self,
        work_id: str,
        *,
        idempotency_key: str,
        allow_unknown_outcome: bool = False,
    ) -> WorkRecord:
        return await self._results.retry(
            work_id,
            WorkRetryRequest(allow_unknown_outcome=allow_unknown_outcome),
            idempotency_key=idempotency_key,
        )

    async def cancel(self, work_id: str) -> WorkRecord:
        return await self._results.cancel(work_id)


class DiscoveryClient:
    def __init__(self, discovery: Discovery, security: SecurityContext) -> None:
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
    def __init__(
        self,
        *,
        requester_module_id: str,
        security: SecurityContext,
        broker: AgentBrokering,
    ) -> None:
        self._requester_module_id = requester_module_id
        self._security = security
        self._broker = broker

    async def invoke(self, agent_id: str, material: Material) -> JsonValue:
        return await self._broker.invoke_agent(
            self._requester_module_id,
            self._security,
            agent_id,
            material.transient(),
        )


class OperationBrokerClient:
    def __init__(
        self,
        *,
        requester_module_id: str,
        security: SecurityContext,
        broker: OperationBrokering,
    ) -> None:
        self._requester_module_id = requester_module_id
        self._security = security
        self._broker = broker

    async def invoke(self, operation_id: str, material: Material) -> JsonValue:
        return await self._broker.invoke_operation(
            self._requester_module_id,
            self._security,
            operation_id,
            material.transient(),
        )


class CoreSelection:
    """Ordinary user/module configuration naming the selected CORE interaction surface."""

    def __init__(self, *, module_id: str, interaction_agent_id: str) -> None:
        if not module_id or not interaction_agent_id:
            raise ValueError("CORE selection identities must not be empty")
        self.module_id = module_id
        self.interaction_agent_id = interaction_agent_id


class CoreDelegate:
    """Reusable fallback helper for UI-less/Agentless Modules."""

    def __init__(self, broker: AgentBrokerClient, selection: CoreSelection) -> None:
        self._broker = broker
        self._selection = selection

    @property
    def selection(self) -> CoreSelection:
        return self._selection

    async def interact(self, material: Material) -> JsonValue:
        return await self._broker.invoke(self._selection.interaction_agent_id, material)
