from __future__ import annotations

import asyncio

import pytest

from madre.broker import InvalidModuleResult, ModuleEndpointUnavailable, SecurityDenied
from madre.security import InvocationContext, SecurityDerivation
from madre_sdk import (
    Artifact,
    ModuleServices,
    TransformBrokerClient,
    effect_profile,
)
from tests.test_registry_broker import (
    REQUESTER,
    TARGET,
    attachment,
    boundary,
    input_material,
    make_operation_module,
    requester_security,
)
from tests.v2_helpers import (
    L1,
    L5,
    ClassifiedOutput,
    Repackage,
    agent_module,
    as_artifact,
    attach,
    invoke,
    participant,
    transform_module,
)
from tests.v2_helpers import platform as platform


def requester():
    return InvocationContext(module=requester_security(), endpoint=attachment(REQUESTER))


def run_transform(broker, module, source):
    return asyncio.run(
        broker.invoke_transform(
            requester(),
            source.security_history,
            module.module_id,
            module.transforms[0].contract.id,
            source.transient(),
        )
    )


def test_containment_does_not_revalue_unknown_actor_and_egress_fails(platform):
    registry, broker, _ = platform
    inside = agent_module(Repackage(passthrough=True), assurance=L1, privacy=L5)
    attach(registry, broker, inside)
    secret = input_material(requester_security(), sensitivity=L5)
    output = invoke(broker, inside, secret)
    assert output.security == secret.security
    assert inside.agents[0].security.values.assurance == L1
    outside = agent_module(Repackage(passthrough=True), owner="outside", privacy=L1)
    attach(registry, broker, outside)
    with pytest.raises(SecurityDenied):
        invoke(broker, outside, as_artifact(output))


def test_wrappers_are_not_security_operands(platform):
    registry, broker, store = platform
    module = agent_module(Repackage())
    attach(registry, broker, module)
    output = invoke(broker, module)
    relation = next(
        x for x in output.history.derivations if x.output_security_id == output.security.security_id
    )
    assert relation.producer_security_ids == (module.agents[0].security.security_id,)
    assert module.security.security_id not in relation.producer_security_ids
    assert not hasattr(module.endpoint_binding, "security_id")
    for transition in output.history.transitions:
        for disclosure in transition.disclosures:
            assert all(
                output.history.resolve(sid).subject_ref.subject_kind == "disclosure_boundary"
                for sid in disclosure.boundary_security_ids
            )


@pytest.mark.parametrize(
    "payload,expected", [({"credential": "test-secret"}, L1), ({"medical": "private"}, L5)]
)
def test_transform_classification_depends_on_concrete_outcome(platform, payload, expected):
    registry, broker, store = platform
    module = transform_module(ClassifiedOutput())
    attach(registry, broker, module)
    source = Artifact.create(
        owner_module_id=REQUESTER,
        artifact_id="source",
        payload=payload,
        sensitivity=L5,
        assurance=L1,
    )
    output = run_transform(broker, module, source)
    assert output.security.values.sensitivity == expected
    assert output.security.values.assurance == L5
    assert output.security.security_id != source.security.security_id
    relation = next(
        x for x in output.history.derivations if x.output_security_id == output.security.security_id
    )
    assert relation.kind == "transform"
    assert relation.transform_security_id == module.transforms[0].contract.security.security_id
    assert relation.derivation_id in store.completed_derivation_ids()


def test_completed_transformed_source_is_provenance_not_control(platform):
    registry, broker, _ = platform
    module = transform_module(ClassifiedOutput())
    attach(registry, broker, module)
    source = input_material(requester_security(), sensitivity=L5, assurance=L1)
    result = run_transform(broker, module, source)
    profile = effect_profile(
        owner_module_id=TARGET,
        operation_id="target.operation",
        profile_id="high",
        control_risk=L5,
        effect_risk=L5,
        autonomy=L5,
        assurance=L5,
    )
    target, _ = make_operation_module((profile,))
    attach(registry, broker, target)
    evaluated = broker.evaluate_operation_profiles(
        requester(), result.history, TARGET, "target.operation", result
    )
    assert evaluated.feasible_profile_ids == ("high",)
    effect = evaluated.profiles[0].decision.effect
    assert source.security.security_id not in effect.controller_security_ids
    assert result.security.security_id in effect.controller_security_ids
    explicit = broker.evaluate_operation_profiles(
        requester(),
        result.history,
        TARGET,
        "target.operation",
        result,
        (source.security.security_id,),
    )
    assert not explicit.feasible_profile_ids


def test_transform_receipt_cannot_be_borrowed_for_another_output(platform):
    registry, broker, _ = platform
    module = transform_module(ClassifiedOutput())
    attach(registry, broker, module)
    source = input_material(requester_security(), assurance=L1)
    output = run_transform(broker, module, source)
    fake = Artifact.create(
        owner_module_id=REQUESTER,
        artifact_id="unexecuted",
        payload={"fake": True},
        sensitivity=L1,
        assurance=L5,
    )
    relation = SecurityDerivation.issue(
        kind="transform",
        output_security_id=fake.security.security_id,
        source_security_ids=(source.security.security_id,),
        producer_security_ids=(module.transforms[0].contract.security.security_id,),
        transform_security_id=module.transforms[0].contract.security.security_id,
    )
    forged = fake.model_copy(
        update={
            "security_history": output.history.extend(
                objects=(fake.security,), derivations=(relation,)
            )
        }
    )
    target = agent_module(Repackage())
    attach(registry, broker, target)
    with pytest.raises(SecurityDenied, match="unverified_transform_execution"):
        invoke(broker, target, forged)


def test_profile_specific_topologies_ties_and_prospective_locality(platform):
    registry, broker, store = platform
    weak = participant(TARGET, "weak", "agent", assurance=L1)

    def profile(name, cr=L1, a=L5, **kwargs):
        return effect_profile(
            owner_module_id=TARGET,
            operation_id="target.operation",
            profile_id=name,
            control_risk=cr,
            effect_risk=L5,
            autonomy=a,
            assurance=L5,
            **kwargs,
        )

    profiles = (
        profile("a"),
        profile("b"),
        profile("blocked-control", L5),
        profile("blocked-executor", executors=(weak,)),
        profile("published", disclosure_boundaries=(boundary("public", L1),)),
        profile("ack", L5, L1),
        profile("exact", L1, L1),
    )
    module, behavior = make_operation_module(profiles)
    attach(registry, broker, module)
    source = input_material(requester_security(), assurance=L1, sensitivity=L5)
    before = store.connection.total_changes
    result = broker.evaluate_operation_profiles(
        requester(), source.security_history, TARGET, "target.operation", source.transient()
    )
    assert result.feasible_profile_ids == ("a", "b", "exact")
    assert result.highest_profile_ids == ("a", "b")
    assert result.maximum_feasible_autonomy == L5
    assert store.connection.total_changes == before and not behavior.called
    decisions = {x.profile_id: x.decision for x in result.profiles}
    assert "effect_assurance_below_risk" in decisions["blocked-executor"].failure_codes
    assert "confidentiality_capacity_below_sensitivity" in decisions["published"].failure_codes
    assert "control_assurance_below_demand" in decisions["ack"].failure_codes


def test_only_a1_is_returned_when_real_a5_fails(platform):
    registry, broker, _ = platform
    profiles = tuple(
        effect_profile(
            owner_module_id=TARGET,
            operation_id="target.operation",
            profile_id=name,
            control_risk=cr,
            effect_risk=L5,
            autonomy=a,
            assurance=L5,
        )
        for name, cr, a in (("a1", L1, L1), ("a5", L5, L5))
    )
    module, _ = make_operation_module(profiles)
    attach(registry, broker, module)
    source = input_material(requester_security(), assurance=L1)
    result = broker.evaluate_operation_profiles(
        requester(), source.security_history, TARGET, "target.operation", source.transient()
    )
    assert result.feasible_profile_ids == result.highest_profile_ids == ("a1",)


def test_same_version_replacement_rejects_stale_attachment(platform):
    registry, broker, _ = platform
    module = agent_module(Repackage())
    attach(registry, broker, module)
    replacement = module.manifest().model_copy(update={"description": "changed publication"})
    registry.register(replacement)
    with pytest.raises(ModuleEndpointUnavailable):
        invoke(broker, module)


def test_transform_contract_replacement_requires_reattachment(platform):
    registry, broker, _ = platform
    module = transform_module(ClassifiedOutput())
    attach(registry, broker, module)
    original = module.transforms[0].contract
    from madre.security import SecurityObject

    changed = SecurityObject.issue(
        subject_ref=original.security.subject_ref,
        values=original.security.values.model_copy(update={"contract_id": "other"}),
    )
    registry.register(
        module.manifest().model_copy(
            update={"transforms": (original.model_copy(update={"security": changed}),)}
        )
    )
    with pytest.raises(ModuleEndpointUnavailable):
        run_transform(broker, module, input_material(requester_security()))


def test_publication_change_during_execution_uses_captured_route(platform):
    registry, broker, _ = platform

    class Change:
        async def execute(self, *, material, **kwargs):
            registry.register(module.manifest().model_copy(update={"description": "changed"}))
            return as_artifact(material)

    module = agent_module(Change())
    attach(registry, broker, module)
    assert invoke(broker, module).payload
    with pytest.raises(ModuleEndpointUnavailable):
        invoke(broker, module)


def test_new_output_requires_actual_production(platform):
    registry, broker, _ = platform

    class Fake:
        async def execute(self, *, material, **kwargs):
            return Artifact.create(
                owner_module_id=TARGET, artifact_id="fake", payload={}, sensitivity=L5, assurance=L5
            )

    module = agent_module(Fake())
    attach(registry, broker, module)
    with pytest.raises(InvalidModuleResult):
        invoke(broker, module)


def test_completed_transform_survives_restart(tmp_path):
    from madre.broker import Broker
    from madre.registry import InteroperabilityRegistry
    from madre.storage import PlatformStore, open_database

    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        broker = Broker(registry, store)
        module = transform_module(ClassifiedOutput())
        attach(registry, broker, module)
        output = run_transform(broker, module, input_material(requester_security(), assurance=L1))
        restored = output.model_dump_json()
    with open_database(tmp_path) as connection:
        from madre.contracts import TransientMaterial

        output = TransientMaterial.model_validate_json(restored)
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        broker = Broker(registry, store)
        target = agent_module(Repackage(passthrough=True))
        attach(registry, broker, target)
        assert invoke(broker, target, as_artifact(output)).security == output.security


def test_v1_storage_is_rejected_without_deletion(tmp_path):
    import sqlite3

    from madre.storage import open_database

    file = tmp_path / "runtime.sqlite3"
    with sqlite3.connect(file) as connection:
        connection.execute("PRAGMA user_version=1")
        connection.execute("CREATE TABLE keep(value)")
    with pytest.raises(RuntimeError, match="incompatible persisted security format"):
        with open_database(tmp_path):
            pass
    with sqlite3.connect(file) as connection:
        assert connection.execute("SELECT name FROM sqlite_master WHERE name='keep'").fetchone()


def test_real_transform_can_leave_both_security_projections_unchanged(platform):
    from madre_sdk import TransformOutput

    registry, broker, _ = platform

    class Normalize:
        async def execute(self, *, material, services):
            return TransformOutput(
                representation_id="normalized",
                payload={"normalized": material.payload},
                sensitivity=material.security.values.sensitivity,
                assurance=material.security.values.assurance,
            )

    module = transform_module(Normalize(), name="normalize")
    attach(registry, broker, module)
    source = input_material(requester_security(), sensitivity=L5, assurance=L1)
    result = run_transform(broker, module, source)
    assert result.security.values == source.security.values
    assert result.security.security_id != source.security.security_id


def test_ordinary_repackaging_cannot_claim_lower_sensitivity(platform):
    registry, broker, _ = platform

    class Relabel:
        async def execute(self, *, invocation, material, security, **kwargs):
            return Artifact.derive_from(
                invocation=invocation,
                source=material,
                owner_module_id=TARGET,
                artifact_id="repackaged",
                payload={"same": material.payload},
                sensitivity=L1,
                producer_security_ids=(),
                security_history=security,
            )

    module = agent_module(Relabel())
    attach(registry, broker, module)
    with pytest.raises(ValueError, match="invalid_derivation"):
        invoke(broker, module, input_material(requester_security(), sensitivity=L5))


def test_bound_profile_declares_actual_residual_control_not_owner_hierarchy(platform):
    registry, broker, _ = platform
    # This fixed-target profile exports no material-controlled choices. The caller
    # still controls triggering; that known selector cannot be removed by history.
    profile = effect_profile(
        owner_module_id=TARGET,
        operation_id="target.operation",
        profile_id="fixed",
        control_risk=L5,
        effect_risk=L5,
        autonomy=L5,
        assurance=L5,
        input_controls=False,
    )
    target, _ = make_operation_module((profile,))
    attach(registry, broker, target)
    source = input_material(requester_security(), assurance=L1)
    result = broker.evaluate_operation_profiles(
        requester(), source.security_history, TARGET, "target.operation", source.transient()
    )
    assert result.feasible_profile_ids == ("fixed",)
    assert result.profiles[0].decision.effect.controller_security_ids == (
        requester().actor.security_id,
    )


def test_sdk_nested_transform_completes_before_reuse(platform):
    registry, broker, _ = platform
    transformer = transform_module(ClassifiedOutput())
    attach(registry, broker, transformer)

    class Delegate:
        async def execute(self, *, services, material, invocation, **kwargs):
            with pytest.raises(TypeError):
                await services.transforms.invoke(
                    "transformer", "rewrite", as_artifact(material), invocation=invocation
                )
            result = await services.transforms.invoke(
                "transformer", "rewrite", as_artifact(material)
            )
            return as_artifact(result)

    caller = agent_module(
        Delegate(),
        owner="caller",
        assurance=L1,
        services=ModuleServices(transforms=TransformBrokerClient(broker=broker)),
    )
    attach(registry, broker, caller)
    result = invoke(broker, caller, input_material(requester_security(), assurance=L1))
    assert result.security.values.assurance == L5
