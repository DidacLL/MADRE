from __future__ import annotations

import asyncio

import pytest

from madre.broker import SecurityDenied
from madre.security import InvocationContext, SecurityDecision, SecurityHistory
from madre_sdk import AgentBrokerClient, Artifact, ModuleServices, OperationBrokerClient
from tests.test_registry_broker import TARGET, attachment, boundary, make_operation_module, profile
from tests.v2_helpers import (
    L1,
    L5,
    Repackage,
    agent_module,
    attach,
    invoke,
)
from tests.v2_helpers import platform as platform


def artifact(material):
    return Artifact(
        id=material.reference,
        payload=material.payload,
        security=material.security,
        security_history=material.history,
    )


def test_executing_agent_cannot_narrow_operation_selector(platform):
    registry, broker, store = platform
    target, effect = make_operation_module((profile("auto", risk=L5, autonomy=L5),))
    attach(registry, broker, target)
    client = OperationBrokerClient(security=SecurityHistory(), broker=broker)

    class Selecting:
        async def execute(self, *, invocation, material, services, **kwargs):
            narrowed = InvocationContext(module=invocation.module, endpoint=invocation.endpoint)
            assert services.operations is not None
            with pytest.raises(TypeError, match="invocation"):
                await services.operations.invoke(
                    TARGET, "target.operation", "auto", artifact(material), invocation=narrowed
                )
            result = await services.operations.invoke(
                TARGET, "target.operation", "auto", artifact(material), security=SecurityHistory()
            )
            return artifact(result)

    selector = agent_module(
        Selecting(), owner="selector", assurance=L1, services=ModuleServices(operations=client)
    )
    attach(registry, broker, selector)
    with pytest.raises(SecurityDenied, match="control_assurance_below_demand"):
        invoke(broker, selector)
    assert not effect.called
    row = store.connection.execute(
        "SELECT decision_json FROM security_decision WHERE crossing_kind='operation-input'"
    ).fetchone()
    decision = SecurityDecision.model_validate_json(row[0])
    assert decision.effect.controller_assurance == L1
    assert selector.agents[0].security.security_id in decision.effect.controller_security_ids


def test_executing_agent_cannot_narrow_return_boundary(platform):
    registry, broker, store = platform
    target = agent_module(Repackage(), owner="generator")
    attach(registry, broker, target)
    client = AgentBrokerClient(security=SecurityHistory(), broker=broker)
    observed = []

    class Reading:
        async def execute(self, *, invocation, material, services, **kwargs):
            narrowed = InvocationContext(module=invocation.module, endpoint=invocation.endpoint)
            assert services.agents is not None
            with pytest.raises(TypeError, match="invocation"):
                await services.agents.invoke(
                    "generator", "actor", artifact(material), invocation=narrowed
                )
            result = await services.agents.invoke(
                "generator", "actor", artifact(material), security=SecurityHistory()
            )
            observed.append(result.payload)
            return artifact(result)

    reader = agent_module(
        Reading(), owner="reader", privacy=L1, services=ModuleServices(agents=client)
    )
    # Enter through the Module's ordinary execution entry: input is public,
    # but Transform classifies its new representation at Sensitivity 5.
    from tests.test_registry_broker import input_material, requester_security

    source = input_material(requester_security(), sensitivity=L1)
    with pytest.raises(SecurityDenied, match="confidentiality_capacity_below_sensitivity"):
        asyncio.run(reader.execute_agent("actor", source))
    assert not observed
    row = store.connection.execute(
        "SELECT decision_json FROM security_decision "
        "WHERE crossing_kind='agent-output' AND target_id='reader'"
    ).fetchone()
    decision = SecurityDecision.model_validate_json(row[0])
    assert decision.disclosures[0].boundary_privacy == L1
    assert (
        reader.endpoint_binding.disclosure_boundaries[0].security_id
        in decision.disclosures[0].boundary_security_ids
    )


def test_unbound_client_cannot_recreate_a_module_only_nested_call(platform):
    registry, broker, _ = platform
    target = agent_module(Repackage())
    attach(registry, broker, target)
    client = AgentBrokerClient(security=SecurityHistory(), broker=broker)
    from tests.test_registry_broker import input_material, requester_security

    source = input_material(requester_security())
    with pytest.raises(TypeError, match="invocation"):
        AgentBrokerClient(
            broker=broker,
            security=source.security_history,
            invocation=InvocationContext(
                module=requester_security(), endpoint=attachment("module.requester")
            ),
        )
    with pytest.raises(RuntimeError, match="requires Module execution binding"):
        asyncio.run(client.invoke(TARGET, "actor", source))


def test_inference_work_and_core_delegate_have_no_context_substitution(platform):
    from madre_sdk import (
        CoreDelegate,
        CoreSelection,
        InferenceClient,
        MaterialRepository,
        WorkClient,
    )
    from tests.test_registry_broker import input_material, requester_security
    from tests.test_runtime_lifecycle import requirement
    from tests.test_sdk_core import FakeDurableWork, FakeInference

    registry, broker, _ = platform
    target = agent_module(Repackage(), owner="generator")
    attach(registry, broker, target)
    inference, work = FakeInference(), FakeDurableWork()
    config = ModuleServices(
        inference=InferenceClient(
            originator="reader", security=SecurityHistory(), inference=inference
        ),
        work=WorkClient(
            originator="reader",
            security=SecurityHistory(),
            submission=work,
            materials=MaterialRepository(),
        ),
        agents=AgentBrokerClient(security=SecurityHistory(), broker=broker),
    )

    class UsingServices:
        async def execute(self, *, services, invocation, material, **kwargs):
            source = artifact(material)
            narrowed = InvocationContext(module=invocation.module, endpoint=invocation.endpoint)
            delegate = CoreDelegate(
                services.agents, CoreSelection(module_id="generator", interaction_agent_id="actor")
            )
            for call, args in [
                (services.inference.infer, (source, requirement())),
                (services.work.submit, (source, requirement())),
                (delegate.interact, (source,)),
            ]:
                with pytest.raises(TypeError, match="invocation"):
                    await call(*args, invocation=narrowed)
            await services.inference.infer(source, requirement(), security=SecurityHistory())
            await services.work.submit(source, requirement(), security=SecurityHistory())
            for request in (inference.requests[0], work.submissions[0]):
                assert set(invocation.producer_security_ids) <= set(request.security.security_ids)
            await delegate.interact(source)
            pytest.fail("weak reader must not receive CORE-delegated sensitive output")

    reader = agent_module(UsingServices(), owner="reader", privacy=L1, services=config)
    source = input_material(requester_security(), sensitivity=L1)
    with pytest.raises(SecurityDenied, match="confidentiality_capacity_below_sensitivity"):
        asyncio.run(reader.execute_agent("actor", source))


@pytest.mark.parametrize("ending", ["return", "exception", "cancel"])
def test_execution_clients_expire_on_every_exit(platform, ending):
    from tests.test_registry_broker import input_material, requester_security

    registry, broker, _ = platform
    target = agent_module(Repackage())
    attach(registry, broker, target)
    saved = []
    ready = asyncio.Event()

    class Capture:
        async def execute(self, *, services, material, **kwargs):
            saved.append(services.agents)
            ready.set()
            if ending == "exception":
                raise ValueError("behavior failed")
            if ending == "cancel":
                await asyncio.Event().wait()
            return artifact(material)

    module = agent_module(
        Capture(),
        services=ModuleServices(
            agents=AgentBrokerClient(security=SecurityHistory(), broker=broker)
        ),
    )
    source = input_material(requester_security())

    async def run():
        task = asyncio.create_task(module.execute_agent("actor", source))
        await ready.wait()
        if ending == "cancel":
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        elif ending == "exception":
            with pytest.raises(ValueError, match="behavior failed"):
                await task
        else:
            await task
        with pytest.raises(RuntimeError, match="execution has ended"):
            await saved[0].invoke(TARGET, "actor", source)
        # A previously issued handle cannot become configuration for a new actor.
        replacement = agent_module(
            Repackage(), owner="replacement", services=ModuleServices(agents=saved[0])
        )
        with pytest.raises(RuntimeError, match="cannot be rebound"):
            await replacement.execute_agent("actor", source)

    asyncio.run(run())


def test_shared_configuration_binds_concurrent_agents_independently(platform):
    from tests.test_registry_broker import input_material, requester_security

    registry, broker, _ = platform
    target = agent_module(Repackage())
    attach(registry, broker, target)
    configuration = ModuleServices(
        agents=AgentBrokerClient(security=SecurityHistory(), broker=broker)
    )

    class Reading:
        async def execute(self, *, services, material, **kwargs):
            await asyncio.sleep(0)
            return artifact(await services.agents.invoke(TARGET, "actor", artifact(material)))

    strong = agent_module(Reading(), owner="strong", privacy=L5, services=configuration)
    weak = agent_module(Reading(), owner="weak", privacy=L1, services=configuration)
    source = input_material(requester_security(), sensitivity=L1)

    async def run():
        return await asyncio.gather(
            strong.execute_agent("actor", source),
            weak.execute_agent("actor", source),
            return_exceptions=True,
        )

    good, denied = asyncio.run(run())
    assert isinstance(good, Artifact)
    assert isinstance(denied, SecurityDenied)


def test_agentless_operation_uses_module_binding_for_nested_calls(platform):
    from madre_sdk import Module, Operation, effect_profile
    from tests.test_registry_broker import input_material, requester_security
    from tests.v2_helpers import participant

    registry, broker, store = platform
    target, effect = make_operation_module((profile("auto", risk=L5, autonomy=L5),))
    attach(registry, broker, target)

    class ModuleBehavior:
        async def execute(self, *, services, invocation, material, **kwargs):
            assert invocation.agent is None
            return artifact(
                await services.operations.invoke(
                    TARGET, "target.operation", "auto", artifact(material)
                )
            )

    operation = Operation(
        operation_id="start",
        purpose="test",
        input_contract="json:any",
        output_contract="json:any",
        effect="test",
        repeatability="explicit",
        behavior=ModuleBehavior(),
        effect_profiles=(
            effect_profile(
                owner_module_id="root",
                operation_id="start",
                profile_id="local",
                control_risk=L1,
                effect_risk=L1,
                autonomy=L1,
                assurance=L5,
            ),
        ),
    )
    module = Module(
        module_id="root",
        version="1",
        description="Agentless caller",
        security=participant("root", "root"),
        operations=(operation,),
        disclosure_boundaries=(boundary("root"),),
        services=ModuleServices(
            operations=OperationBrokerClient(security=SecurityHistory(), broker=broker)
        ),
    )
    result = asyncio.run(
        module.execute_operation("start", "local", input_material(requester_security()))
    )
    assert result.payload and effect.called
    row = store.connection.execute(
        "SELECT decision_json FROM security_decision WHERE crossing_kind='operation-input'"
    ).fetchone()
    decision = SecurityDecision.model_validate_json(row[0])
    assert (
        operation.effect_profiles[0].security.security_id in decision.effect.controller_security_ids
    )
