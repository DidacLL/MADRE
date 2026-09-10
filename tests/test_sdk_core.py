from __future__ import annotations

import asyncio
import json
from datetime import UTC, datetime

from madre.contracts import WorkSpec
from madre.security import MaterialSecurityValues
from madre_core import (
    CORE_INTERACTION_AGENT_ID,
    CORE_MODULE_ID,
    CoreContinuation,
    CoreModule,
)
from madre_sdk import (
    Artifact,
    CapabilitySecurityValues,
    CoreSelection,
    Disclosure,
    InferenceHardRequirements,
    InferenceRequirement,
    SecurityHistory,
    SecurityLevel,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
    TransientInferenceResult,
    WorkRecord,
    content_digest,
    participant_security,
)
from tests.reference_agentless_module import ReferenceAgentlessModule


def payload_size(payload) -> int:
    return len(json.dumps(payload, sort_keys=True, separators=(",", ":")).encode())


def client_input() -> Artifact:
    client = participant_security(
        owner_module_id="module.client",
        subject_id="module.client",
        subject_kind="module",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    return Artifact.create(
        owner_module_id="module.client",
        artifact_id="client.input",
        payload={"input": "hello"},
        sensitivity=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
        security_history=SecurityHistory(objects=(client,)),
    )


class FakeInference:
    def __init__(self, *, integrity: SecurityLevel = SecurityLevel.LEVEL_3) -> None:
        self.integrity = integrity
        self.requests = []
        self.capability_security = SecurityObject.issue(
            subject_ref=SecuritySubjectRef(
                owner_module_id="madre.platform",
                subject_kind="capability",
                publication_revision="1",
                local_id="test.chat",
            ),
            values=CapabilitySecurityValues(
                privacy=SecurityLevel.LEVEL_5,
                integrity=integrity,
            ),
        )

    async def infer(self, request):
        self.requests.append(request)
        result_payload = {"answer": "hello"}
        transition = SecurityTransition.issue(
            disclosures=(
                Disclosure(
                    material_security_id=request.material.security.security_id,
                    path_security_ids=(self.capability_security.security_id,),
                ),
            )
        )
        history = request.security.merge(request.material.history).extend(
            objects=(self.capability_security,),
            transitions=(transition,),
        )
        return TransientInferenceResult(
            payload=result_payload,
            capability_id="test.chat",
            execution_boundary="local",
            output_digest=content_digest(result_payload),
            output_size=payload_size(result_payload),
            output_integrity=self.integrity,
            producer_security_ids=(self.capability_security.security_id,),
            source_security_ids=(request.material.security.security_id,),
            security=history,
        )


class FollowUpPolicy:
    def decide(self, *, user_input, immediate_result):
        del user_input, immediate_result
        return CoreContinuation(durable_follow_up=True)


class DelegationPolicy:
    def decide(self, *, user_input, immediate_result):
        del user_input, immediate_result
        return CoreContinuation(
            delegate_module_id="module.delegate",
            delegate_agent_id="delegate.agent",
        )


class FakeDurableWork:
    def __init__(self) -> None:
        self.submissions = []

    async def submit(self, submission, *, idempotency_key=None):
        del idempotency_key
        self.submissions.append(submission)
        spec = WorkSpec(
            originator=submission.originator,
            security=submission.security,
            inference=submission.inference,
            material=submission.material,
            eligible_at=submission.eligible_at,
            priority=submission.priority,
            constraints=submission.constraints,
            correlation=submission.correlation,
        )
        return WorkRecord(
            id="work-follow-up",
            spec=spec,
            status="accepted",
            submitted_at=datetime.now(UTC),
        )


class FakeAgentBroker:
    def __init__(self) -> None:
        self.calls = []

    async def invoke_agent(
        self,
        requester_module_id,
        security,
        target_module_id,
        agent_id,
        material,
    ):
        self.calls.append((requester_module_id, target_module_id, agent_id))
        producer = next(
            obj.security_id
            for obj in security.objects
            if obj.subject_ref.subject_kind == "module"
            and obj.subject_ref.local_id == requester_module_id
        )
        output = Artifact.derive_from(
            source=material,
            owner_module_id=target_module_id,
            artifact_id="delegate.output",
            payload={"delegated": True},
            producer_security_ids=(producer,),
            sensitivity=SecurityLevel.LEVEL_5,
            security_history=security,
        )
        return output.transient()


def test_core_manifest_uses_final_participant_security_values() -> None:
    core = CoreModule(inference=FakeInference())
    manifest = core.manifest()
    module_values = manifest.security.values
    agent_values = manifest.agents[0].security.values
    assert module_values.privacy == SecurityLevel.LEVEL_5
    assert module_values.integrity == SecurityLevel.LEVEL_5
    assert agent_values.privacy == SecurityLevel.LEVEL_5
    assert agent_values.integrity == SecurityLevel.LEVEL_5
    assert not hasattr(module_values, "trust")
    assert not hasattr(module_values, "isolation")


def test_core_immediate_path_preserves_capability_and_derivation_history() -> None:
    inference = FakeInference(integrity=SecurityLevel.LEVEL_3)
    core = CoreModule(inference=inference)
    output = asyncio.run(core.execute_agent(CORE_INTERACTION_AGENT_ID, client_input()))
    assert output.payload == {"response": {"answer": "hello"}}
    values = output.security.values
    assert isinstance(values, MaterialSecurityValues)
    assert values.integrity == SecurityLevel.LEVEL_3
    assert inference.capability_security.security_id in output.security_history.security_ids
    assert len(output.security_history.transitions) == 1
    assert len(output.security_history.derivations) >= 3


def test_core_durable_continuation_is_ordinary_work_with_same_security_history() -> None:
    inference = FakeInference()
    durable = FakeDurableWork()
    core = CoreModule(
        inference=inference,
        durable_work=durable,
        continuation=FollowUpPolicy(),
    )
    output = asyncio.run(core.execute_agent(CORE_INTERACTION_AGENT_ID, client_input()))
    assert output.payload["follow_up_work_id"] == "work-follow-up"
    assert len(durable.submissions) == 1
    submitted = durable.submissions[0]
    assert inference.capability_security.security_id in submitted.security.security_ids
    assert submitted.material.security.security_id in submitted.security.security_ids
    assert submitted.material.history == submitted.security


def test_core_delegation_uses_configured_module_and_agent_and_keeps_history() -> None:
    inference = FakeInference()
    broker = FakeAgentBroker()
    core = CoreModule(
        inference=inference,
        continuation=DelegationPolicy(),
        agent_broker=broker,
    )
    output = asyncio.run(core.execute_agent(CORE_INTERACTION_AGENT_ID, client_input()))
    assert broker.calls == [(CORE_MODULE_ID, "module.delegate", "delegate.agent")]
    assert output.payload["delegated_result"] == {"delegated": True}
    assert any(
        relation.output_security_id == output.security.security_id
        for relation in output.security_history.derivations
    )


def test_reference_agentless_module_derives_analysis_context_from_source() -> None:
    module = ReferenceAgentlessModule()
    note = module.note("secret note")
    context = module.analysis_context(note)
    relation = next(
        item
        for item in context.security_history.derivations
        if item.output_security_id == context.security.security_id
    )
    assert relation.source_security_ids == (note.security.security_id,)
    assert relation.producer_security_ids == (module.security.security_id,)


def test_core_selection_is_ordinary_replaceable_configuration() -> None:
    selection = CoreSelection(
        module_id="custom.core",
        interaction_agent_id="custom.interaction",
    )
    assert selection.module_id == "custom.core"
    assert selection.interaction_agent_id == "custom.interaction"


def test_core_inference_requirements_do_not_depend_on_special_kernel_lane() -> None:
    inference = FakeInference()
    core = CoreModule(inference=inference)
    asyncio.run(core.execute_agent(CORE_INTERACTION_AGENT_ID, client_input()))
    request = inference.requests[0]
    assert request.inference.hard.specialization == "model.inference.chat"
    assert request.inference.hard.latency_class is None
    assert request.inference.preferences.latency_classes == ("interactive", "standard")


def test_public_sdk_requirement_contract_remains_generic() -> None:
    requirement = InferenceRequirement(
        hard=InferenceHardRequirements(specialization="model.inference.chat")
    )
    assert requirement.hard.specialization == "model.inference.chat"
