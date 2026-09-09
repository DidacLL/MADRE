import asyncio
from pathlib import Path

import pytest
from pydantic import ValidationError

from madre.adapters.openai import OpenAIChatConfig, OpenAICompatibleChatCapability
from madre.capabilities import CapabilityDescriptor, CapabilityRegistry, FunctionCapability
from madre.contracts import (
    FallbackPolicy,
    InferenceHardRequirements,
    InferencePreferences,
    InferenceRequirement,
    TransientInferenceRequest,
    TransientMaterial,
)
from madre.runtime import WorkRuntime, content_digest
from madre.security import (
    ActorSecurityValues,
    CapabilitySecurityValues,
    CompatibilitySecurityEvaluator,
    MaterialSecurityValues,
    SecurityContext,
    SecurityLevel,
    SecurityObject,
)
from madre.storage import PlatformStore, open_database


def actor(subject: str, *, kind: str = "module", trust=SecurityLevel.LEVEL_5) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=subject,
        subject_kind=kind,  # type: ignore[arg-type]
        values=ActorSecurityValues(trust=trust, isolation=SecurityLevel.LEVEL_5),
        origin="fixture",
    )


def material(reference: str, payload, *, sensitivity=SecurityLevel.LEVEL_2) -> TransientMaterial:
    return TransientMaterial(
        reference=reference,
        payload=payload,
        digest=content_digest(payload),
        security=SecurityObject.issue(
            subject_id=reference,
            subject_kind="artifact",
            values=MaterialSecurityValues(sensitivity=sensitivity),
            origin="fixture",
        ),
    )


def capability_security(subject: str, *, trust=SecurityLevel.LEVEL_5) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=subject,
        subject_kind="capability",
        values=CapabilitySecurityValues(
            trust=trust,
            privacy=SecurityLevel.LEVEL_5,
            risk=SecurityLevel.LEVEL_1,
        ),
        origin="fixture",
    )


def requirement(**hard_overrides) -> InferenceRequirement:
    hard = {"specialization": "structured.compute", "modality": "json", **hard_overrides}
    return InferenceRequirement(hard=InferenceHardRequirements(**hard))


def adapter(
    capability_id: str,
    function=lambda payload: payload,
    *,
    provider_id: str | None = None,
    model_id: str | None = None,
    boundary: str = "local",
    latency: str = "standard",
    quality: str = "standard",
    efforts=frozenset({"low", "medium", "high"}),
    paid: bool = False,
    resources=frozenset(),
    trust=SecurityLevel.LEVEL_5,
) -> FunctionCapability:
    return FunctionCapability(
        CapabilityDescriptor(
            id=capability_id,
            specialization="structured.compute",
            modality="json",
            provider_id=provider_id,
            model_id=model_id,
            execution_boundary=boundary,  # type: ignore[arg-type]
            latency_class=latency,  # type: ignore[arg-type]
            supported_reasoning_efforts=efforts,
            quality_tier=quality,  # type: ignore[arg-type]
            paid=paid,
            resources=resources,
            security=capability_security(capability_id, trust=trust),
        ),
        function,
    )


def test_security_object_binding_detects_downstream_rewrite() -> None:
    original = actor("module.a", trust=SecurityLevel.LEVEL_4)
    assert original.verify_integrity()
    tampered = original.model_copy(
        update={
            "values": ActorSecurityValues(
                trust=SecurityLevel.LEVEL_5,
                isolation=SecurityLevel.LEVEL_5,
            )
        }
    )
    assert not tampered.verify_integrity()
    decision = CompatibilitySecurityEvaluator().evaluate(SecurityContext(objects=(tampered,)))
    assert not decision.admissible
    assert f"invalid_integrity:{original.security_id}" in decision.deficits


def test_security_object_rejects_irrelevant_universal_dimensions() -> None:
    with pytest.raises(ValidationError):
        SecurityObject.issue(
            subject_id="artifact.a",
            subject_kind="artifact",
            values=ActorSecurityValues(
                trust=SecurityLevel.LEVEL_5,
                isolation=SecurityLevel.LEVEL_5,
            ),
            origin="fixture",
        )


def test_hard_inference_requirements_are_never_silently_violated() -> None:
    registry = CapabilityRegistry()
    registry.register(
        adapter(
            "remote-paid",
            provider_id="provider.a",
            model_id="model.a",
            boundary="remote",
            paid=True,
            efforts=frozenset({"low"}),
        )
    )
    request = requirement(
        locality="local_only",
        cost_policy="free_only",
        reasoning_effort="high",
    )
    assert registry.candidates(request) == ()


def test_preferences_are_soft_and_fallback_order_is_deterministic() -> None:
    registry = CapabilityRegistry()
    registry.register(adapter("z", provider_id="fallback", model_id="fallback-model"))
    registry.register(adapter("a", provider_id="preferred", model_id="preferred-model"))
    request = InferenceRequirement(
        hard=InferenceHardRequirements(specialization="structured.compute", modality="json"),
        preferences=InferencePreferences(
            provider_ids=("preferred",),
            model_ids=("preferred-model",),
        ),
    )
    assert [item.descriptor.id for item in registry.candidates(request)] == ["a", "z"]

    no_fallback = request.model_copy(update={"fallback": FallbackPolicy(allow_unlisted=False)})
    assert [item.descriptor.id for item in registry.candidates(no_fallback)] == ["a"]


def test_provider_and_model_preferences_do_not_become_exact_requirements() -> None:
    registry = CapabilityRegistry()
    registry.register(adapter("only", provider_id="available", model_id="available-model"))
    request = InferenceRequirement(
        hard=InferenceHardRequirements(specialization="structured.compute", modality="json"),
        preferences=InferencePreferences(
            provider_ids=("missing-provider",),
            model_ids=("missing-model",),
        ),
    )
    assert registry.select(request) is not None
    hard = requirement(provider_id="missing-provider")
    assert registry.select(hard) is None


def test_provider_endpoint_policy_remains_inside_adapter_not_generic_kernel() -> None:
    config = OpenAIChatConfig(
        endpoint="https://api.example.test/v1",
        model="provider-model",
        provider_id="provider",
        boundary="remote",
    )
    descriptor = CapabilityDescriptor(
        id="provider-chat",
        specialization="model.inference.chat",
        modality="text",
        provider_id=config.provider_id,
        model_id=config.model,
        execution_boundary=config.boundary,
        security=capability_security("provider-chat"),
    )
    concrete = OpenAICompatibleChatCapability(descriptor, config)
    assert concrete.descriptor.provider_id == "provider"
    assert concrete.descriptor.id == "provider-chat"


def test_transient_inference_creates_no_work_state_or_persisted_payload(tmp_path: Path) -> None:
    private_input = "TRANSIENT-INPUT-MUST-NOT-PERSIST"
    private_output = "TRANSIENT-OUTPUT-MUST-NOT-PERSIST"
    data_dir = tmp_path / "runtime"
    with open_database(data_dir) as connection:
        store = PlatformStore(connection)
        registry = CapabilityRegistry()
        registry.register(adapter("compute", lambda _: {"answer": private_output}))
        runtime = WorkRuntime(store, registry)
        request = TransientInferenceRequest(
            originator="module.a",
            security=SecurityContext(objects=(actor("module.a"),)),
            inference=requirement(),
            material=material("ephemeral/1", {"prompt": private_input}),
        )
        result = asyncio.run(runtime.infer(request))
        assert result.payload == {"answer": private_output}
        assert connection.execute("SELECT COUNT(*) FROM runtime_work").fetchone()[0] == 0
        assert connection.execute("SELECT COUNT(*) FROM runtime_attempt").fetchone()[0] == 0

    for database_file in data_dir.glob("runtime.sqlite3*"):
        raw = database_file.read_bytes()
        assert private_input.encode() not in raw
        assert private_output.encode() not in raw
