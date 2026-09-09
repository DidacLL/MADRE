"""Interface-segregated Module-facing contracts for the future MADRE SDK."""

from __future__ import annotations

from typing import Protocol

from pydantic import JsonValue

from madre.contracts import (
    MaterialHandle,
    TransientInferenceRequest,
    TransientInferenceResult,
    TransientMaterial,
    WorkRecord,
    WorkRetryRequest,
    WorkSubmission,
)
from madre.registry import (
    AgentDescriptor,
    ModuleManifest,
    OperationDescriptor,
    SkillDescriptor,
    WorkflowDescriptor,
)
from madre.security import ExecutionBoundary, SecurityContext, SecurityObject


class ModuleRegistration(Protocol):
    def register(self, manifest: ModuleManifest) -> None: ...


class TransientInference(Protocol):
    async def infer(self, request: TransientInferenceRequest) -> TransientInferenceResult: ...


class DurableWorkSubmission(Protocol):
    async def submit(
        self,
        submission: WorkSubmission,
        *,
        idempotency_key: str | None = None,
    ) -> WorkRecord: ...


class WorkInspection(Protocol):
    def inspect(self, work_id: str) -> WorkRecord | None: ...


class WorkResultAccess(Protocol):
    def consume_result(self, work_id: str) -> JsonValue: ...

    async def retry(
        self,
        work_id: str,
        request: WorkRetryRequest,
        *,
        idempotency_key: str,
    ) -> WorkRecord: ...

    async def cancel(self, work_id: str) -> WorkRecord: ...


class MaterialResolver(Protocol):
    async def resolve(self, handle: MaterialHandle) -> TransientMaterial | None: ...


class Discovery(Protocol):
    def discover_agents(self, security: SecurityContext) -> tuple[AgentDescriptor, ...]: ...

    def discover_skills(self, security: SecurityContext) -> tuple[SkillDescriptor, ...]: ...

    def discover_workflows(self, security: SecurityContext) -> tuple[WorkflowDescriptor, ...]: ...

    def discover_operations(self, security: SecurityContext) -> tuple[OperationDescriptor, ...]: ...


class AgentEndpoint(Protocol):
    @property
    def boundary(self) -> ExecutionBoundary: ...

    @property
    def security(self) -> SecurityObject: ...

    async def invoke_agent(self, agent_id: str, payload: JsonValue) -> TransientMaterial: ...


class OperationEndpoint(Protocol):
    @property
    def boundary(self) -> ExecutionBoundary: ...

    @property
    def security(self) -> SecurityObject: ...

    async def invoke_operation(
        self, operation_id: str, payload: JsonValue
    ) -> TransientMaterial: ...
