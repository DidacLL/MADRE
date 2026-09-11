from __future__ import annotations

import asyncio

from madre.contracts import TransientInferenceResult
from madre.security import Privacy, ScopeBinding, SecurityObject, SecurityScopeRef, Sensitivity
from madre_core import CORE_INTERACTION_AGENT_ID, CoreModule
from madre_sdk import Artifact, content_digest


class Inference:
    async def infer(self, request):
        payload = {"answer": "hello"}
        producer = SecurityObject.issue(
            scope=SecurityScopeRef(
                owner_module_id="mechanisms",
                scope_id="local-chat",
                publication_revision="1",
            ),
            privacy=Privacy.SECRET,
            binding=ScopeBinding(contract_digest="c" * 64),
        )
        evidence = request.evidence.extend(objects=(producer, request.material.security))
        return TransientInferenceResult(
            payload=payload,
            capability_id="local-chat",
            execution_boundary="local",
            output_digest=content_digest(payload),
            output_size=len('{"answer":"hello"}'),
            producer_security_ids=(producer.security_id,),
            source_security_ids=(request.material.security.security_id,),
            evidence=evidence,
        )


def test_core_remains_an_ordinary_sdk_module_without_security_bypass() -> None:
    core = CoreModule(inference=Inference())
    user_input = Artifact.create(
        owner_module_id=core.module_id,
        artifact_id="input",
        payload={"input": "hello"},
        sensitivity=Sensitivity.S5,
    )
    output = asyncio.run(core.execute_agent(CORE_INTERACTION_AGENT_ID, user_input))
    assert output.payload == {"response": {"answer": "hello"}}
    assert output.security.integrity is None
    assert output.evidence.derivations
