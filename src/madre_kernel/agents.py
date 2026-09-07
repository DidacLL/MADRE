"""Standard CORE Module fallback Agent implementation for the first vertical loop."""

from __future__ import annotations

from typing import Annotated, Literal
from uuid import uuid4

from pydantic import Field, TypeAdapter

from madre_kernel.contracts import (
    ActorSecurityFacts,
    AgentDefinition,
    AgentDefinitionRef,
    AgentInstance,
    AgentInstanceRef,
    AgentRef,
    AgentResolutionDescriptor,
    CalculateInput,
    CalculateOutput,
    ContextBundle,
    ContextBundleRef,
    DataSecurityFacts,
    DefinitionProvenance,
    FinalAnswer,
    ModuleAgentDefinitionRef,
    ModuleAgentInstanceRef,
    ModuleManifest,
    ModuleRef,
    SchemaRef,
    SecurityLevel,
    SemanticModel,
    utc_now,
)
from madre_kernel.modules import PUBLIC_DISCOVERY, AgentExecutionServices, InProcessModule

CORE_MODULE = ModuleRef(module_id="madre-core")
CORE_AGENT = AgentRef(module=CORE_MODULE, agent_id="fallback-general")
CORE_AGENT_DEFINITION = AgentDefinitionRef(agent=CORE_AGENT, revision=1)
CORE_FINAL_SCHEMA = SchemaRef(module=CORE_MODULE, schema_id="final-answer", revision=1)


class _OperationRequest(SemanticModel):
    kind: Literal["operation"]
    operation_id: Annotated[str, Field(min_length=1)]
    left: int
    right: int


class _FinalResponse(SemanticModel):
    kind: Literal["final"]
    answer: Annotated[str, Field(min_length=1)]


_CORE_TURN: TypeAdapter[_OperationRequest | _FinalResponse] = TypeAdapter(
    _OperationRequest | _FinalResponse
)


class CoreAgentManager:
    def instantiate(self, definition: AgentDefinition) -> AgentInstance:
        return AgentInstance(
            ref=AgentInstanceRef(instance_id=uuid4().hex),
            definition=definition.ref,
            manager_instance_ref=ModuleAgentInstanceRef(
                module=CORE_MODULE,
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
        operation_lines = "\n".join(
            f"- {item.ref.operation_id}: {item.name}; {item.purpose}" for item in operations
        )
        first_text = await services.reasoning(
            (
                (
                    "system",
                    "You are the standard CORE fallback Agent. Return only one JSON object. "
                    "To request an operation use "
                    '{"kind":"operation","operation_id":"...","left":int,"right":int}. '
                    "Do not invent operations. Visible operations:\n" + operation_lines,
                ),
                ("user", objective.payload.canonical_json),
            ),
            (objective.ref,),
        )
        first = _CORE_TURN.validate_json(first_text)
        if not isinstance(first, _OperationRequest):
            raise ValueError("first CORE reasoning pass must request an Operation")
        descriptor = next(
            (item for item in operations if item.ref.operation_id == first.operation_id),
            None,
        )
        if descriptor is None:
            raise ValueError(
                "CORE requested an Operation outside its visible descriptor projection"
            )
        input_ref = services.project_operation_input(
            descriptor.ref,
            CalculateInput(left=first.left, right=first.right),
        )
        produced = await services.invoke_operation(descriptor.ref, (input_ref,))
        if len(produced) != 1:
            raise ValueError("calculator Operation must produce one ContextBundle")
        result_bundle = services.context(produced[0])
        result = CalculateOutput.model_validate_json(result_bundle.payload.canonical_json)
        second_text = await services.reasoning(
            (
                (
                    "system",
                    "Return only JSON in the form "
                    '{"kind":"final","answer":"..."}. Use the observed Operation result.',
                ),
                ("user", f"Observed calculate result: {result.value}"),
            ),
            produced,
        )
        second = _CORE_TURN.validate_json(second_text)
        if not isinstance(second, _FinalResponse):
            raise ValueError("second CORE reasoning pass must return a final response")
        return services.emit_agent_context(
            CORE_FINAL_SCHEMA,
            FinalAnswer(text=second.answer),
            DataSecurityFacts(
                sensitivity=result_bundle.security.sensitivity,
                trust=SecurityLevel.LEVEL_3,
                scopes=result_bundle.security.scopes,
            ),
            "agent-final-answer",
            produced,
        )


def build_core_module() -> InProcessModule:
    definition = AgentDefinition(
        ref=CORE_AGENT_DEFINITION,
        name="CORE fallback general Agent",
        description="Standard fallback reasoning actor managed opaquely by the CORE Module.",
        manager_definition_ref=ModuleAgentDefinitionRef(
            module=CORE_MODULE,
            definition_id="core-private-chat-manager-v1",
        ),
        skill_instances=(),
        direct_workflows=(),
        security=ActorSecurityFacts(
            trust=SecurityLevel.LEVEL_4,
            maximum_handled_sensitivity=SecurityLevel.LEVEL_4,
            execution_risk=SecurityLevel.LEVEL_1,
        ),
        resolution_descriptors=(
            AgentResolutionDescriptor(
                namespace_module=CORE_MODULE,
                descriptor_id="general",
            ),
        ),
        visibility=PUBLIC_DISCOVERY,
        provenance=DefinitionProvenance(
            created_at=utc_now(),
            created_by=CORE_MODULE,
        ),
    )
    manifest = ModuleManifest(
        module=CORE_MODULE,
        revision=1,
        name="MADRE CORE",
        description="Standard replaceable CORE Module.",
        visibility=PUBLIC_DISCOVERY,
        agents=(definition.ref,),
    )
    return InProcessModule(
        manifest=manifest,
        schemas={CORE_FINAL_SCHEMA: FinalAnswer},
        agents=(definition,),
        manager=CoreAgentManager(),
    )
