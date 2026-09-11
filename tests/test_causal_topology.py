from __future__ import annotations

import asyncio

import pytest

from madre.broker import Broker, InvalidModuleResult, ModuleEndpointUnavailable, SecurityDenied
from madre.registry import InteroperabilityRegistry
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    BindingEvidence,
    Control,
    Disclosure,
    InvocationContext,
    SecurityDerivation,
    SecurityHistory,
    SecurityLevel,
    SecurityObject,
    SecurityTransition,
)
from madre.storage import PlatformStore, open_database
from madre_sdk import (
    Agent,
    AgentBrokerClient,
    Artifact,
    Module,
    ModuleServices,
    OperationBrokerClient,
    participant_security,
)
from tests.test_registry_broker import (
    REQUESTER,
    TARGET,
    input_material,
    make_operation_module,
    profile,
    requester_security,
)

L1 = SecurityLevel.LEVEL_1
L5 = SecurityLevel.LEVEL_5


def participant(owner, name, kind="module", *, integrity=L5, privacy=L5, revision="1"):
    return participant_security(
        owner_module_id=owner,
        subject_id=name,
        subject_kind=kind,
        integrity=integrity,
        privacy=privacy,
        publication_revision=revision,
    )


class Transform:
    def __init__(self, *, validation=False, omit=False, borrowed=None, passthrough=False):
        self.validation = validation
        self.omit = omit
        self.borrowed = borrowed
        self.passthrough = passthrough

    async def execute(self, *, agent_id, instructions, security, invocation, material, services):
        if self.passthrough:
            return Artifact(
                id=material.reference,
                payload=material.payload,
                security=material.security,
                security_history=security,
            )
        source = Artifact(
            id=material.reference,
            payload=material.payload,
            security=material.security,
            security_history=security,
        )
        if self.validation and self.borrowed is None:
            return source.validated(
                procedure=invocation.module.subject_ref.model_copy(
                    update={"subject_kind": "transform", "local_id": "validate-control"}
                ),
                invocation=invocation,
                artifact_id="validated",
                payload={"checked": True},
                integrity=L5,
            )
        output = Artifact.derive_from(
            invocation=invocation,
            source=material,
            owner_module_id=invocation.module_id,
            artifact_id="new-output",
            payload={"transformed": material.payload},
            producer_security_ids=(),
            sensitivity=L5,
            security_history=security,
        )
        if not self.omit and self.borrowed is None:
            return output
        # Deliberately bypass SDK safeguards, as a custom endpoint can do.
        forged_security = SecurityObject.issue(
            subject_ref=output.security.subject_ref,
            values=output.security.values.model_copy(update={"integrity": L5}),
            binding_evidence=output.security.binding_evidence,
        )
        relation = SecurityDerivation.issue(
            kind="validation" if self.borrowed else "ordinary",
            procedure=forged_security.subject_ref if self.borrowed else None,
            output_security_id=forged_security.security_id,
            source_security_ids=(material.security.security_id,),
            validator_security_ids=(self.borrowed.security_id,) if self.borrowed else (),
        )
        history = security.extend(
            objects=(forged_security, *((self.borrowed,) if self.borrowed else ())),
            derivations=(relation,),
        )
        return output.model_copy(update={"security": forged_security, "security_history": history})


def agent_module(behavior, *, owner=TARGET, integrity=L5, privacy=L5, services=None):
    agent = Agent.from_instructions(
        agent_id="actor",
        purpose="test",
        instructions="test",
        security=participant(owner, "actor", "agent", integrity=integrity, privacy=privacy),
        behavior=behavior,
    )
    return Module(
        module_id=owner,
        version="1",
        description="test",
        security=participant(owner, owner),
        agents=(agent,),
        services=services,
    )


@pytest.fixture
def platform(tmp_path):
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        yield registry, Broker(registry, store), store


def attach(registry, broker, module):
    module.register(registry)
    module.register_agent_endpoint(broker)
    module.register_operation_endpoint(broker)


def invoke(broker, module, *, requester=None, material=None):
    requester = requester or InvocationContext(module=requester_security())
    material = material or input_material(requester.module, sensitivity=L5)
    return asyncio.run(
        broker.invoke_agent(
            requester,
            material.security_history,
            module.module_id,
            "actor",
            material.transient(),
        )
    )


def test_active_selector_cannot_disappear_when_client_history_is_replaced(platform):
    registry, broker, _ = platform
    target, behavior = make_operation_module((profile("auto", risk=L5, autonomy=L5),))
    attach(registry, broker, target)
    client = OperationBrokerClient(security=SecurityHistory(), broker=broker)

    class SelectingAgent:
        async def execute(
            self, *, agent_id, instructions, security, invocation, material, services
        ):
            artifact = Artifact(
                id=material.reference,
                payload=material.payload,
                security=material.security,
                security_history=material.history,
            )
            assert services.operations is not None
            return await services.operations.invoke(
                TARGET,
                "target.operation",
                "auto",
                artifact,
                security=SecurityHistory(),
            )

    selector = agent_module(
        SelectingAgent(), owner="selector", integrity=L1, services=ModuleServices(operations=client)
    )
    attach(registry, broker, selector)
    with pytest.raises(SecurityDenied, match="control_integrity_below_demand"):
        invoke(broker, selector)
    assert not behavior.called


def test_routing_wrappers_are_not_automatically_controllers(platform):
    registry, broker, store = platform
    target, _ = make_operation_module((profile("auto", risk=L5, autonomy=L5),))
    attach(registry, broker, target)
    requester = InvocationContext(
        module=participant(REQUESTER, REQUESTER, integrity=L1),
        agent=participant(REQUESTER, "selector", "agent"),
        endpoint=participant(REQUESTER, "wrapper", "endpoint", integrity=L1),
    )
    material = input_material(requester.module)
    asyncio.run(
        broker.invoke_operation(
            requester,
            material.security_history,
            TARGET,
            "target.operation",
            "auto",
            material.transient(),
        )
    )
    row = store.connection.execute(
        "SELECT transition_json FROM security_decision WHERE crossing_kind='operation-input'"
    ).fetchone()
    control = SecurityTransition.model_validate_json(row[0]).control
    assert control is not None
    assert set(control.controller_security_ids) == {
        requester.agent.security_id,
    }


def test_sensitive_return_checks_active_agent_but_not_unobserving_wrapper(platform):
    registry, broker, _ = platform
    module = agent_module(Transform())
    attach(registry, broker, module)
    requester = InvocationContext(
        module=requester_security(),
        agent=participant(REQUESTER, "reader", "agent", privacy=L1),
    )
    with pytest.raises(SecurityDenied, match="confidentiality_capacity_below_sensitivity"):
        invoke(broker, module, requester=requester)
    wrapper = InvocationContext(
        module=requester_security(),
        endpoint=participant(REQUESTER, "wrapper", "endpoint", privacy=L1),
    )
    assert invoke(broker, module, requester=wrapper).payload


@pytest.mark.parametrize("omit", [False, True])
@pytest.mark.parametrize("low_role", ["agent", "endpoint"])
def test_actual_low_integrity_producer_is_bounded_or_rejected(platform, omit, low_role):
    registry, broker, _ = platform
    module = agent_module(Transform(omit=omit), integrity=L1 if low_role == "agent" else L5)
    if low_role == "endpoint":
        module.endpoint_security = participant(TARGET, "endpoint", "endpoint", integrity=L1)
    attach(registry, broker, module)
    if omit:
        with pytest.raises(InvalidModuleResult, match="missing_actual_derivation_participant"):
            invoke(broker, module)
    else:
        result = invoke(broker, module)
        assert result.security.values.integrity is None
        relation = next(
            r
            for r in result.history.derivations
            if r.output_security_id == result.security.security_id
        )
        assert set(
            InvocationContext(
                module=module.security,
                agent=module.agents[0].security,
                endpoint=module.endpoint_security,
            ).producer_security_ids
        ) <= set(relation.producer_security_ids)


def test_pass_through_does_not_relabel_a_source_as_endpoint_production(platform):
    registry, broker, _ = platform
    module = agent_module(Transform(passthrough=True), integrity=L1)
    attach(registry, broker, module)
    result = invoke(broker, module)
    assert result.security.values.integrity == L5
    assert not result.history.derivations


def test_historical_validator_did_not_perform_current_validation(platform):
    registry, broker, _ = platform
    borrowed = participant("unrelated", "unrelated")
    module = agent_module(Transform(borrowed=borrowed), integrity=L1)
    attach(registry, broker, module)
    with pytest.raises(
        InvalidModuleResult, match="derivation_participant|validation_participation"
    ):
        invoke(broker, module)


def test_real_validation_is_stable_and_completion_survives_broker_recreation(platform):
    registry, broker, store = platform
    validator = agent_module(Transform(validation=True))
    attach(registry, broker, validator)
    source = input_material(requester_security(), integrity=L1, sensitivity=L5)
    first = invoke(broker, validator, material=source)
    assert first.security.values.integrity == L5
    relation = first.history.derivations[0]
    assert relation.derivation_id in store.completed_derivation_ids()
    fresh_broker = Broker(registry, store)
    validator.register_agent_endpoint(fresh_broker)
    second = invoke(fresh_broker, validator, material=source)
    assert second.history.derivations == first.history.derivations
    assert first.security == second.security
    rows = store.connection.execute(
        "SELECT invocation_id FROM broker_event WHERE event='completed'"
    ).fetchall()
    assert len({row[0] for row in rows}) == 2


def test_same_version_publication_replacement_invalidates_attachment(platform):
    registry, broker, _ = platform
    module = agent_module(Transform())
    attach(registry, broker, module)
    registry.register(module.manifest().model_copy(update={"description": "changed publication"}))
    with pytest.raises(ModuleEndpointUnavailable, match="stale"):
        invoke(broker, module)


def test_registry_change_during_execution_does_not_rebind_completion(platform):
    registry, broker, store = platform

    class Republishing(Transform):
        async def execute(self, **kwargs):
            registry.register(module.manifest().model_copy(update={"description": "new"}))
            return await super().execute(**kwargs)

    module = agent_module(Republishing())
    attach(registry, broker, module)
    assert invoke(broker, module).payload
    row = store.connection.execute(
        "SELECT completion_context_json FROM broker_event WHERE event='completed'"
    ).fetchone()
    context = InvocationContext.model_validate_json(row[0])
    assert context.module == module.security


@pytest.mark.parametrize(
    "bad",
    [
        participant("wrong", "endpoint", "endpoint"),
        participant(TARGET, "endpoint", "agent"),
        participant(TARGET, "endpoint", "endpoint", revision="2"),
    ],
)
def test_custom_endpoint_security_must_match_publication(bad):
    with pytest.raises(ValueError, match="publication"):
        Module(
            module_id=TARGET,
            version="1",
            description="test",
            security=participant(TARGET, TARGET),
            endpoint_security=bad,
        )


def test_relation_set_identity_and_ordered_disclosure_paths():
    a, b = "security:a", "security:b"
    first = Control(effect_profile_security_id="effect", controller_security_ids=(a, b, a))
    second = Control(effect_profile_security_id="effect", controller_security_ids=(b, a))
    assert first == second
    args = dict(kind="ordinary", output_security_id="output")
    x = SecurityDerivation.issue(**args, source_security_ids=(a, b), producer_security_ids=(b, a))
    y = SecurityDerivation.issue(
        **args, source_security_ids=(b, a, a), producer_security_ids=(a, b)
    )
    assert x == y
    assert SecurityDerivation.model_validate_json(x.model_dump_json()) == x
    d1 = Disclosure(material_security_id=a, path_security_ids=(a, b))
    d2 = Disclosure(material_security_id=b, path_security_ids=(a, b))
    assert SecurityTransition.issue(disclosures=(d1, d2, d1)) == SecurityTransition.issue(
        disclosures=(d2, d1)
    )
    assert SecurityTransition.issue(disclosures=(d1,)) != SecurityTransition.issue(
        disclosures=(d1.model_copy(update={"path_security_ids": (b, a)}),)
    )


def test_conflicting_and_cyclic_derivations_are_rejected():
    module = requester_security()
    a = input_material(module)
    b = Artifact.create(
        owner_module_id=REQUESTER, artifact_id="b", payload="b", sensitivity=L5, integrity=L5
    )
    c = Artifact.create(
        owner_module_id=REQUESTER, artifact_id="c", payload="c", sensitivity=L5, integrity=L5
    )

    def relation(output, source):
        return SecurityDerivation.issue(
            kind="ordinary",
            output_security_id=output.security.security_id,
            source_security_ids=(source.security.security_id,),
        )

    history = a.security_history.merge(b.security_history, c.security_history)
    for relations in [
        (relation(a, b), relation(b, c), relation(c, a)),
        (relation(c, a), relation(c, b)),
    ]:
        decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
            history.extend(derivations=relations), SecurityTransition.issue()
        )
        assert "invalid_derivation" in decision.failure_codes


def test_binding_evidence_rejects_metadata_and_unbounded_values():
    for key, value in [
        ("arbitrary", "text"),
        ("model", "x" * 257),
        ("model", "https://user:secret@host/"),
        ("content_digest", "not-a-digest"),
    ]:
        with pytest.raises(ValueError):
            BindingEvidence(key=key, value=value)


def test_nested_validation_and_concurrent_returns_preserve_explicit_callers(platform):
    registry, broker, store = platform
    inner = agent_module(Transform(validation=True), owner="inner")
    attach(registry, broker, inner)
    client = AgentBrokerClient(security=SecurityHistory(), broker=broker)

    class Delegate:
        async def execute(self, *, invocation, material, services, **kwargs):
            source = Artifact(
                id=material.reference,
                payload=material.payload,
                security=material.security,
                security_history=material.history,
            )
            await asyncio.sleep(0)
            assert services.agents is not None
            result = await services.agents.invoke("inner", "actor", source)
            return Artifact(
                id=result.reference,
                payload=result.payload,
                security=result.security,
                security_history=result.history,
            )

    outer = agent_module(Delegate(), owner="outer", services=ModuleServices(agents=client))
    attach(registry, broker, outer)
    source = input_material(requester_security(), integrity=L1, sensitivity=L1)
    callers = [
        InvocationContext(
            module=requester_security(),
            agent=participant(REQUESTER, name, "agent", privacy=privacy),
        )
        for name, privacy in [("strong-reader", L5), ("weak-reader", L1)]
    ]

    async def run():
        return await asyncio.gather(
            *[
                broker.invoke_agent(
                    caller, source.security_history, "outer", "actor", source.transient()
                )
                for caller in callers
            ],
            return_exceptions=True,
        )

    results = asyncio.run(run())
    # Validation preserves source Sensitivity here, so both callers receive level 1.
    assert all(not isinstance(result, Exception) for result in results)
    rows = store.connection.execute(
        "SELECT transition_json FROM security_decision "
        "WHERE crossing_kind='agent-output' AND target_id=?",
        (REQUESTER,),
    ).fetchall()
    paths = [
        SecurityTransition.model_validate_json(row[0]).disclosures[0].path_security_ids
        for row in rows
    ]
    assert set(paths) == {caller.recipient_security_ids for caller in callers}
    assert all(len(path) == 2 for path in paths)


def test_validation_completion_evidence_survives_database_reopen(tmp_path):
    validator = agent_module(Transform(validation=True))
    source = input_material(requester_security(), integrity=L1, sensitivity=L5)
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        broker = Broker(registry, store)
        attach(registry, broker, validator)
        result = invoke(broker, validator, material=source)
        accepted = result.history.derivations[0].derivation_id
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        assert accepted in store.completed_derivation_ids()
        registry = InteroperabilityRegistry(store)
        broker = Broker(registry, store)
        reader = agent_module(Transform(passthrough=True), owner="reader")
        attach(registry, broker, reader)
        restored = Artifact(
            id=result.reference,
            payload=result.payload,
            security=result.security,
            security_history=result.history,
        )
        assert invoke(broker, reader, material=restored).security == result.security


def test_completed_validator_cannot_be_borrowed_for_a_different_output(platform):
    registry, broker, _ = platform
    validator = agent_module(Transform(validation=True))
    attach(registry, broker, validator)
    source = input_material(requester_security(), integrity=L1, sensitivity=L5)
    invoke(broker, validator, material=source)
    borrowed_context = InvocationContext(
        module=validator.security,
        agent=validator.agents[0].security,
        endpoint=validator.endpoint_security,
    )

    class BorrowCompletion:
        async def execute(self, *, material, security, **kwargs):
            source = Artifact(
                id=material.reference,
                payload=material.payload,
                security=material.security,
                security_history=security,
            )
            return source.validated(
                invocation=borrowed_context,
                procedure=borrowed_context.module.subject_ref.model_copy(
                    update={"subject_kind": "transform", "local_id": "validate-control"}
                ),
                artifact_id="different-output",
                payload={"never-validated-by-target": True},
            )

    attacker = agent_module(BorrowCompletion(), owner="attacker", integrity=L1)
    attach(registry, broker, attacker)
    with pytest.raises(InvalidModuleResult, match="validation_participation"):
        invoke(broker, attacker, material=source)


def test_unrelated_private_ordinary_ancestry_does_not_acquire_current_producer(platform):
    registry, broker, _ = platform
    private_context = InvocationContext(module=participant("private", "private", integrity=L1))
    private_source = Artifact.create(
        owner_module_id="private",
        artifact_id="private-source",
        payload="old",
        sensitivity=L1,
        integrity=L1,
    )
    private_output = private_source.derive(
        invocation=private_context,
        artifact_id="private-output",
        payload="old derived",
        producer_security_ids=(),
    )

    class CarryPrivate(Transform):
        async def execute(self, **kwargs):
            result = await super().execute(**kwargs)
            return result.model_copy(
                update={
                    "security_history": result.security_history.merge(
                        private_output.security_history
                    )
                }
            )

    module = agent_module(CarryPrivate())
    attach(registry, broker, module)
    result = invoke(broker, module)
    assert result.security.values.integrity is None
    private_relation = next(
        r
        for r in result.history.derivations
        if r.output_security_id == private_output.security.security_id
    )
    assert private_relation.producer_security_ids == private_context.producer_security_ids


@pytest.mark.parametrize(
    "endpoint",
    [
        "https://user:secret@example.com/v1",
        "https://example.com/v1?api_key=secret",
        "https://example.com/v1#secret",
    ],
)
def test_capability_endpoint_secrets_are_rejected_before_binding(endpoint):
    from madre.adapters.openai import OpenAIChatConfig
    from madre.config import Settings
    from madre.service import _capabilities

    settings = Settings(
        capabilities={
            "test": OpenAIChatConfig(endpoint=endpoint, model="test", privacy=L5, integrity=L5)
        }
    )
    with pytest.raises(ValueError, match="non-secret structural URL"):
        _capabilities(settings)


def test_capability_evidence_does_not_retain_endpoint_path():
    from madre.adapters.openai import OpenAIChatConfig
    from madre.config import Settings
    from madre.service import _capabilities

    settings = Settings(
        capabilities={
            "test": OpenAIChatConfig(
                endpoint="https://example.com/private-routing-marker/v1",
                model="test",
                privacy=L5,
                integrity=L5,
            )
        }
    )
    registry = _capabilities(settings)
    # Inspect the actual adapter binding emitted by service construction.
    from tests.test_runtime_lifecycle import requirement

    descriptor = registry.candidates(requirement("test"))[0].descriptor
    encoded = descriptor.security.model_dump_json()
    assert "private-routing-marker" not in encoded
    assert "endpoint_path_digest" in encoded


def test_inference_rejects_uncompleted_validation_claim(platform):
    from madre.capabilities import CapabilityRegistry
    from madre.contracts import TransientInferenceRequest
    from madre.runtime import TransientInferenceError, WorkRuntime
    from tests.test_runtime_lifecycle import requirement

    _, _, store = platform
    context = InvocationContext(module=requester_security())
    source = input_material(context.module, integrity=L1)
    uncompleted = source.validated(
        procedure=context.module.subject_ref.model_copy(
            update={"subject_kind": "transform", "local_id": "validate-control"}
        ),
        invocation=context,
        artifact_id="uncompleted",
        payload="claimed",
    )
    runtime = WorkRuntime(store, CapabilityRegistry())
    with pytest.raises(TransientInferenceError, match="security_denied"):
        asyncio.run(
            runtime.infer(
                TransientInferenceRequest(
                    originator=REQUESTER,
                    security=uncompleted.security_history,
                    inference=requirement(),
                    material=uncompleted.transient(),
                )
            )
        )


def test_stored_ancestry_cannot_fork_or_cycle_across_separate_histories(platform):
    _, _, store = platform
    context = InvocationContext(module=requester_security())
    a = input_material(context.module)
    b = a.derive(invocation=context, artifact_id="b", payload="b", producer_security_ids=())
    with store.connection:
        store._persist_security_history(b.security_history)
    # Each supplied history alone is acyclic; their immutable union is not.
    cycle = SecurityDerivation.issue(
        kind="ordinary",
        output_security_id=a.security.security_id,
        source_security_ids=(b.security.security_id,),
    )
    history = SecurityHistory(objects=(a.security, b.security), derivations=(cycle,))
    with pytest.raises(ValueError, match="invalid_derivation"), store.connection:
        store._persist_security_history(history)
    fork = SecurityDerivation.issue(
        kind="ordinary",
        output_security_id=b.security.security_id,
        source_security_ids=(a.security.security_id,),
        producer_security_ids=(),
    )
    with pytest.raises(ValueError, match="invalid_derivation"), store.connection:
        store._persist_security_history(
            SecurityHistory(objects=(a.security, b.security), derivations=(fork,))
        )


def test_endpoint_binding_cannot_change_after_attachment(platform):
    registry, broker, _ = platform
    module = agent_module(Transform())
    attach(registry, broker, module)
    module.endpoint_security = participant(TARGET, "replacement", "endpoint")
    with pytest.raises(ModuleEndpointUnavailable, match="stale"):
        invoke(broker, module)


def test_historical_object_is_not_an_input_pass_through(platform):
    registry, broker, _ = platform
    historical = Artifact.create(
        owner_module_id=TARGET,
        artifact_id="historical",
        payload="old",
        sensitivity=L5,
        integrity=L5,
    )

    class HistoricalOutput:
        async def execute(self, *, security, **kwargs):
            return historical.model_copy(update={"security_history": security})

    module = agent_module(HistoricalOutput())
    attach(registry, broker, module)
    source = input_material(requester_security(), sensitivity=L5)
    source = source.model_copy(
        update={"security_history": source.security_history.merge(historical.security_history)}
    )
    with pytest.raises(InvalidModuleResult, match="derivation continuity"):
        invoke(broker, module, material=source)
