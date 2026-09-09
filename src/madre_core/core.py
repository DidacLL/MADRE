"""Shipped default CORE Module implemented only through the public MADRE SDK."""

from __future__ import annotations

import json
from itertools import count
from typing import Protocol

from madre_sdk import (
    Agent,
    AgentBehavior,
    AgentBrokerClient,
    AgentBrokering,
    Artifact,
    ContextBundle,
    CoreSelection,
    DurableWorkSubmission,
    InferenceClient,
    InferenceHardRequirements,
    InferencePreferences,
    InferenceRequirement,
    JsonValue,
    MaterialRepository,
    Module,
    SecurityContext,
    SecurityLevel,
    Skill,
    TransientInference,
    WorkClient,
    Workflow,
    actor_security,
    security_context,
)

CORE_MODULE_ID = "madre.core.default"
CORE_INTERACTION_AGENT_ID = "madre.core.interaction"
DEFAULT_CORE_SELECTION = CoreSelection(
    module_id=CORE_MODULE_ID,
    interaction_agent_id=CORE_INTERACTION_AGENT_ID,
)


class CoreContinuation:
    """CORE-private continuation decision; it is not a universal SDK planning ontology."""

    def __init__(
        self,
        *,
        durable_follow_up: bool = False,
        delegate_module_id: str | None = None,
        delegate_agent_id: str | None = None,
    ) -> None:
        if (delegate_module_id is None) != (delegate_agent_id is None):
            raise ValueError("delegation requires both target Module and Agent identities")
        self.durable_follow_up = durable_follow_up
        self.delegate_module_id = delegate_module_id
        self.delegate_agent_id = delegate_agent_id


class ContinuationPolicy(Protocol):
    def decide(self, *, user_input: JsonValue, immediate_result: JsonValue) -> CoreContinuation: ...


class NoContinuation:
    def decide(self, *, user_input: JsonValue, immediate_result: JsonValue) -> CoreContinuation:
        return CoreContinuation()


class _InteractionBehavior(AgentBehavior):
    def __init__(
        self,
        *,
        inference: InferenceClient,
        work: WorkClient | None,
        continuation: ContinuationPolicy,
        delegation: AgentBrokerClient | None,
    ) -> None:
        self._inference = inference
        self._work = work
        self._continuation = continuation
        self._delegation = delegation
        self._ids = count(1)

    async def execute(
        self,
        *,
        agent_id: str,
        instructions: tuple[str, ...],
        security: SecurityContext,
        payload: JsonValue,
    ) -> Artifact:
        sequence = next(self._ids)
        context = ContextBundle.create(
            bundle_id=f"{CORE_MODULE_ID}:interaction:{sequence}",
            purpose="default-general-interaction",
            payload={
                "messages": [
                    {"role": "system", "content": "\n".join(instructions)},
                    {"role": "user", "content": _user_text(payload)},
                ]
            },
            sensitivity=SecurityLevel.LEVEL_5,
        )
        immediate = await self._inference.infer(
            context,
            _interactive_requirement(),
            security=security,
        )
        generated = Artifact.from_inference_result(
            artifact_id=f"{CORE_MODULE_ID}:response:{sequence}",
            result=immediate,
            sensitivity=SecurityLevel.LEVEL_5,
        )

        decision = self._continuation.decide(
            user_input=payload,
            immediate_result=immediate.payload,
        )
        follow_up_work_id: str | None = None
        delegated_result: JsonValue = None

        if (
            decision.delegate_module_id is not None
            and decision.delegate_agent_id is not None
            and self._delegation is not None
        ):
            delegated_context = ContextBundle.create(
                bundle_id=f"{CORE_MODULE_ID}:delegation:{sequence}",
                purpose="explicit-agent-delegation",
                payload={"input": payload, "immediate": immediate.payload},
                sensitivity=SecurityLevel.LEVEL_5,
            )
            delegated_result = await self._delegation.invoke(
                decision.delegate_module_id,
                decision.delegate_agent_id,
                delegated_context,
                security=security,
            )

        if decision.durable_follow_up and self._work is not None:
            follow_up = ContextBundle.create(
                bundle_id=f"{CORE_MODULE_ID}:follow-up:{sequence}",
                purpose="continued-reasoning",
                payload={"input": payload, "immediate": immediate.payload},
                sensitivity=SecurityLevel.LEVEL_5,
            )
            accepted = await self._work.submit(
                follow_up,
                _follow_up_requirement(),
                correlation=(),
                security=security,
            )
            follow_up_work_id = accepted.id

        output_payload: dict[str, JsonValue] = {"response": generated.payload}
        if delegated_result is not None:
            output_payload["delegated_result"] = delegated_result
        if follow_up_work_id is not None:
            output_payload["follow_up_work_id"] = follow_up_work_id
        return Artifact.create(
            artifact_id=f"{CORE_MODULE_ID}:interaction-output:{sequence}",
            payload=output_payload,
            sensitivity=SecurityLevel.LEVEL_5,
        )


def _user_text(payload: JsonValue) -> str:
    if isinstance(payload, str):
        return payload
    if isinstance(payload, dict):
        value = payload.get("input")
        if isinstance(value, str):
            return value
    return json.dumps(payload, sort_keys=True, separators=(",", ":"))


def _interactive_requirement() -> InferenceRequirement:
    return InferenceRequirement(
        hard=InferenceHardRequirements(
            specialization="model.inference.chat",
            modality="text",
            latency_class="interactive",
        ),
        preferences=InferencePreferences(
            execution_boundaries=("local", "isolated"),
            reasoning_efforts=("low", "medium"),
        ),
    )


def _follow_up_requirement() -> InferenceRequirement:
    return InferenceRequirement(
        hard=InferenceHardRequirements(
            specialization="model.inference.chat",
            modality="text",
            reasoning_effort="high",
        ),
        preferences=InferencePreferences(execution_boundaries=("local", "isolated")),
    )


class CoreModule(Module):
    """Default CORE-capable Module with no Kernel privilege or hidden dependency."""

    def __init__(
        self,
        *,
        inference: TransientInference,
        durable_work: DurableWorkSubmission | None = None,
        continuation: ContinuationPolicy | None = None,
        agent_broker: AgentBrokering | None = None,
    ) -> None:
        module_security = actor_security(
            subject_id=CORE_MODULE_ID,
            subject_kind="module",
            trust=SecurityLevel.LEVEL_5,
            isolation=SecurityLevel.LEVEL_5,
        )
        interaction_security = actor_security(
            subject_id=CORE_INTERACTION_AGENT_ID,
            subject_kind="agent",
            trust=SecurityLevel.LEVEL_5,
            isolation=SecurityLevel.LEVEL_5,
        )
        carried = security_context(module_security, interaction_security)
        materials = MaterialRepository()
        inference_client = InferenceClient(
            originator=CORE_MODULE_ID,
            security=carried,
            inference=inference,
        )
        work_client = (
            WorkClient(
                originator=CORE_MODULE_ID,
                security=carried,
                submission=durable_work,
                materials=materials,
            )
            if durable_work is not None
            else None
        )
        delegation_client = (
            AgentBrokerClient(
                requester_module_id=CORE_MODULE_ID,
                security=carried,
                broker=agent_broker,
            )
            if agent_broker is not None
            else None
        )
        skill = Skill(
            id="madre.core.general-assistance",
            purpose="Provide default general and MADRE-oriented intelligent assistance",
            instructions=(
                "Respond naturally to the user's immediate request.",
                "Provide setup, configuration, installation, and general assistance when relevant.",
            ),
        )
        workflow = Workflow(
            id="madre.core.continued-reasoning",
            purpose="Continue or delegate reasoning after an immediate interaction when useful",
            instructions=(
                "Keep immediate interaction low latency.",
                "Use ordinary delegation or durable work for substantive continuation.",
            ),
        )
        behavior = _InteractionBehavior(
            inference=inference_client,
            work=work_client,
            continuation=continuation or NoContinuation(),
            delegation=delegation_client,
        )
        interaction_agent = Agent.from_instructions(
            agent_id=CORE_INTERACTION_AGENT_ID,
            purpose="Default general interaction, fallback intelligence, and system assistance",
            instructions=(
                "You are MADRE's configured default interaction Agent.",
                "Give a genuine response to the request; do not expose scheduler ceremony.",
                "Use bounded delegation or continued work when the task benefits from it.",
            ),
            security=interaction_security,
            behavior=behavior,
            skills=(skill,),
            workflows=(workflow,),
        )
        super().__init__(
            module_id=CORE_MODULE_ID,
            version="1",
            description="Shipped default CORE-capable MADRE Module",
            security=module_security,
            discovery_terms=(
                "core",
                "general",
                "interaction",
                "fallback",
                "setup",
                "configuration",
            ),
            agents=(interaction_agent,),
            skills=(skill,),
            workflows=(workflow,),
            materials=materials,
        )
