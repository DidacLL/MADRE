import ast
import asyncio
from pathlib import Path

from madre.broker import Broker
from madre.capabilities import CapabilityDescriptor, CapabilityRegistry, FunctionCapability
from madre.registry import InteroperabilityRegistry
from madre.runtime import WorkRuntime
from madre.security import (
    ActorSecurityValues,
    CapabilitySecurityValues,
    MaterialSecurityValues,
    OperationSecurityValues,
    SecurityLevel,
    SecurityObject,
)
from madre.storage import PlatformStore, open_database
from madre_core import CoreContinuation, CoreModule, DEFAULT_CORE_SELECTION
from madre_sdk import (
    Agent,
    AgentBehavior,
    AgentBrokerClient,
    Artifact,
    ContextBundle,
    CoreDelegate,
    CoreSelection,
    InferenceClient,
    InferenceHardRequirements,
    InferenceRequirement,
    Module,
    Operation,
    OperationBehavior,
    SecurityContext,
    Skill,
    Workflow,
    WorkClient,
    WorkPlan,
    actor_security,
    operation_security,
)
from tests.reference_agentless_module import ReferenceAgentlessModule


def _capability_security(subject: str) -> SecurityObject:
    return SecurityObject.issue(
        subject_id=subject,
        subject_kind="capability",
        values=CapabilitySecurityValues(
            trust=SecurityLevel.LEVEL_5,
            privacy=SecurityLevel.LEVEL_5,
            risk=SecurityLevel.LEVEL_1,
        ),
    )


def _chat_capability() -> FunctionCapability:
    def execute(payload):
        if isinstance(payload, dict) and "messages" in payload:
            return {"text": "immediate inference response"}
        if isinstance(payload, dict) and "immediate" in payload:
            return {"text": "durable follow-up result"}
        return {"text": "reused generated material"}

    return FunctionCapability(
        CapabilityDescriptor(
            id="local-chat",
            specialization="model.inference.chat",
            modality="text",
            provider_id="fixture",
            model_id="fixture-chat",
            execution_boundary="local",
            latency_class="interactive",
            supported_reasoning_efforts=frozenset({"low", "medium", "high"}),
            quality_tier="standard",
            paid=False,
            resources=frozenset(),
            heavyweight=False,
            security=_capability_security("local-chat"),
        ),
        execute,
    )


def _chat_requirement() -> InferenceRequirement:
    return InferenceRequirement(
        hard=InferenceHardRequirements(
            specialization="model.inference.chat",
            modality="text",
        )
    )


class _AlwaysContinue:
    def decide(self, *, user_input, immediate_result) -> CoreContinuation:
        return CoreContinuation(durable_follow_up=True)


class _DelegateTo:
    def __init__(self, agent_id: str) -> None:
        self._agent_id = agent_id

    def decide(self, *, user_input, immediate_result) -> CoreContinuation:
        return CoreContinuation(delegate_agent_id=self._agent_id)


class _EchoAgent(AgentBehavior):
    async def execute(self, *, agent_id: str, instructions: tuple[str, ...], payload):
        return Artifact.create(
            artifact_id=f"{agent_id}:result",
            payload={"handled_by": agent_id, "input": payload},
            sensitivity=SecurityLevel.LEVEL_2,
        )


class _BoundedOperation(OperationBehavior):
    async def execute(self, *, operation_id: str, payload):
        return Artifact.create(
            artifact_id=f"{operation_id}:result",
            payload={"operation": operation_id, "input": payload},
            sensitivity=SecurityLevel.LEVEL_2,
        )


class _SimplePlan:
    @property
    def id(self) -> str:
        return "module-plan"

    def project_work(self):
        return ()


def test_sdk_semantic_contracts_are_small_and_security_bound() -> None:
    source = Artifact.create(
        artifact_id="artifact/source",
        payload={"secret": "value"},
        sensitivity=SecurityLevel.LEVEL_5,
        intended_use=SecurityLevel.LEVEL_3,
    )
    derived = source.derive(
        artifact_id="artifact/minimized",
        payload={"summary": "value omitted"},
        sensitivity=SecurityLevel.LEVEL_2,
    )
    assert derived.id != source.id
    assert derived.security.security_id != source.security.security_id
    assert isinstance(source.security.values, MaterialSecurityValues)
    assert source.security.values.sensitivity == SecurityLevel.LEVEL_5
    assert source.security.values.intended_use == SecurityLevel.LEVEL_3
    assert isinstance(derived.security.values, MaterialSecurityValues)
    assert derived.security.values.sensitivity == SecurityLevel.LEVEL_2
    assert derived.security.values.intended_use == SecurityLevel.LEVEL_3
    assert derived.transient().to_handle().security == derived.security

    bundle = ContextBundle.create(
        bundle_id="context/analysis",
        purpose="analysis",
        payload={"artifact": derived.payload},
        sensitivity=SecurityLevel.LEVEL_4,
        intended_use=SecurityLevel.LEVEL_2,
    )
    assert bundle.transient().security.subject_kind == "context_bundle"
    assert bundle.handle().digest == bundle.transient().digest

    skill = Skill(
        id="portable.skill",
        purpose="Portable instructions",
        instructions=("Inspect input",),
    )
    workflow = Workflow(
        id="portable.workflow",
        purpose="Reusable semantic recipe",
        instructions=("Use the skill", "Produce a bounded result"),
    )
    agent = Agent.from_instructions(
        agent_id="minimal.agent",
        purpose="Minimal inferred Agent",
        instructions="Follow the supplied instructions",
        security=actor_security(
            subject_id="minimal.agent",
            subject_kind="agent",
            trust=SecurityLevel.LEVEL_4,
            isolation=SecurityLevel.LEVEL_4,
        ),
        behavior=_EchoAgent(),
        skills=(skill,),
        workflows=(workflow,),
    )
    assert agent.skills == (skill,)
    assert agent.workflows == (workflow,)
    assert skill.descriptor("module.a").instructions == ("Inspect input",)
    assert workflow.descriptor("module.a").instructions == (
        "Use the skill",
        "Produce a bounded result",
    )
    assert not hasattr(agent, "session")
    assert not hasattr(agent, "memory")

    operation = Operation(
        operation_id="bounded.publish",
        purpose="Represent one bounded effect",
        input_contract="json:any",
        output_contract="json:any",
        effect="external-publish",
        repeatability="not-repeatable",
        security=operation_security(
            operation_id="bounded.publish",
            risk=SecurityLevel.LEVEL_5,
            autonomy=SecurityLevel.LEVEL_2,
        ),
        behavior=_BoundedOperation(),
    )
    descriptor = operation.descriptor("module.a")
    assert isinstance(descriptor.security.values, OperationSecurityValues)
    assert descriptor.security.values.risk == SecurityLevel.LEVEL_5
    assert descriptor.security.values.autonomy == SecurityLevel.LEVEL_2

    plan: WorkPlan = _SimplePlan()
    assert plan.id == "module-plan"
    assert plan.project_work() == ()


def test_sdk_reference_module_core_and_replaceability(tmp_path: Path) -> None:
    async def scenario() -> None:
        with open_database(tmp_path / "runtime") as connection:
            store = PlatformStore(connection)
            capabilities = CapabilityRegistry()
            capabilities.register(_chat_capability())
            runtime = WorkRuntime(store, capabilities)
            registry = InteroperabilityRegistry(store)
            broker = Broker(registry, store)

            reference = ReferenceAgentlessModule()
            reference.register(registry)
            reference.register_material_resolution(runtime)
            assert registry.get_module(reference.module_id) is not None
            assert reference.manifest().agents == ()

            reference_context = SecurityContext(objects=(reference.security,))
            inference = InferenceClient(
                originator=reference.module_id,
                security=reference_context,
                inference=runtime,
            )
            work = WorkClient(
                originator=reference.module_id,
                security=reference_context,
                submission=runtime,
                materials=reference.materials,
            )
            source = reference.note("private note")
            analysis_context = reference.analysis_context(source)
            transient = await inference.infer(analysis_context, _chat_requirement())
            assert transient.payload == {"text": "reused generated material"}

            accepted = await work.submit(analysis_context, _chat_requirement())
            assert accepted.status == "accepted"
            assert accepted.spec.material.reference == analysis_context.id
            assert await runtime.run_eligible() == 1
            durable_payload = runtime.consume_result(accepted.id)
            assert durable_payload == {"text": "reused generated material"}

            generated = Artifact.create(
                artifact_id="reference.notes:generated",
                payload=durable_payload,
                sensitivity=SecurityLevel.LEVEL_3,
                intended_use=SecurityLevel.LEVEL_2,
            )
            reused = await inference.infer(generated, _chat_requirement())
            assert reused.payload == {"text": "reused generated material"}

            core = CoreModule(
                inference=runtime,
                durable_work=runtime,
                continuation=_AlwaysContinue(),
                agent_broker=broker,
            )
            core.register(registry)
            core.register_material_resolution(runtime)
            core.register_agent_endpoint(broker)
            manifest = core.manifest()
            assert isinstance(manifest.security.values, ActorSecurityValues)
            assert manifest.security.values.trust == SecurityLevel.LEVEL_5
            assert manifest.security.values.isolation == SecurityLevel.LEVEL_5

            delegate = CoreDelegate(
                AgentBrokerClient(
                    requester_module_id=reference.module_id,
                    security=reference_context,
                    broker=broker,
                ),
                DEFAULT_CORE_SELECTION,
            )
            delegated = await delegate.interact(source)
            assert delegated["response"] == {"text": "immediate inference response"}
            follow_up_id = delegated["follow_up_work_id"]
            assert isinstance(follow_up_id, str)
            assert runtime.inspect(follow_up_id).status == "accepted"
            assert await runtime.run_eligible() == 1
            assert runtime.consume_result(follow_up_id) == {"text": "durable follow-up result"}

            specialist = Agent.from_instructions(
                agent_id="specialist.agent",
                purpose="Handle explicit delegated work",
                instructions="Handle the bounded request",
                security=actor_security(
                    subject_id="specialist.agent",
                    subject_kind="agent",
                    trust=SecurityLevel.LEVEL_5,
                    isolation=SecurityLevel.LEVEL_5,
                ),
                behavior=_EchoAgent(),
            )
            specialist_module = Module(
                module_id="specialist.module",
                version="1",
                description="Specialist Module",
                security=actor_security(
                    subject_id="specialist.module",
                    subject_kind="module",
                    trust=SecurityLevel.LEVEL_5,
                    isolation=SecurityLevel.LEVEL_5,
                ),
                agents=(specialist,),
            )
            specialist_module.register(registry)
            specialist_module.register_agent_endpoint(broker)
            native_result = await specialist.execute({"input": "native UI request"})
            assert native_result.payload["handled_by"] == "specialist.agent"

            delegating_core = CoreModule(
                inference=runtime,
                continuation=_DelegateTo("specialist.agent"),
                agent_broker=broker,
            )
            delegating_core.register(registry)
            delegating_core.register_agent_endpoint(broker)
            delegated_to_specialist = await delegate.interact(source)
            assert delegated_to_specialist["delegated_result"]["handled_by"] == "specialist.agent"

            alternate_agent = Agent.from_instructions(
                agent_id="alternate.core.interaction",
                purpose="Alternative compatible CORE interaction",
                instructions="Return the alternate CORE response",
                security=actor_security(
                    subject_id="alternate.core.interaction",
                    subject_kind="agent",
                    trust=SecurityLevel.LEVEL_5,
                    isolation=SecurityLevel.LEVEL_5,
                ),
                behavior=_EchoAgent(),
            )
            alternate_core = Module(
                module_id="alternate.core",
                version="1",
                description="Test replacement CORE-capable Module",
                security=actor_security(
                    subject_id="alternate.core",
                    subject_kind="module",
                    trust=SecurityLevel.LEVEL_5,
                    isolation=SecurityLevel.LEVEL_5,
                ),
                agents=(alternate_agent,),
            )
            alternate_core.register(registry)
            alternate_core.register_agent_endpoint(broker)
            alternate_delegate = CoreDelegate(
                AgentBrokerClient(
                    requester_module_id=reference.module_id,
                    security=reference_context,
                    broker=broker,
                ),
                CoreSelection(
                    module_id="alternate.core",
                    interaction_agent_id="alternate.core.interaction",
                ),
            )
            alternate_result = await alternate_delegate.interact(source)
            assert alternate_result["handled_by"] == "alternate.core.interaction"

    asyncio.run(scenario())


def _imported_modules(path: Path) -> set[str]:
    tree = ast.parse(path.read_text())
    modules: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            modules.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module is not None:
            modules.add(node.module)
    return modules


def test_sdk_and_core_architecture_boundaries() -> None:
    root = Path(__file__).parents[1]
    sdk_allowed = {
        "madre.contracts",
        "madre.interfaces",
        "madre.registry",
        "madre.security",
    }
    for path in (root / "src" / "madre_sdk").glob("*.py"):
        forbidden = {
            module
            for module in _imported_modules(path)
            if module.startswith("madre.") and module not in sdk_allowed
        }
        assert forbidden == set(), f"{path} imports non-public Kernel surface: {forbidden}"

    for path in (root / "src" / "madre_core").glob("*.py"):
        forbidden = {
            module
            for module in _imported_modules(path)
            if module == "madre" or module.startswith("madre.")
        }
        assert forbidden == set(), f"{path} bypasses SDK: {forbidden}"
        text = path.read_text()
        assert "[[MADRE_REASONING:" not in text

    for path in (root / "src" / "madre").rglob("*.py"):
        imported = _imported_modules(path)
        assert not any(module.startswith("madre_core") for module in imported)
        assert not any(module.startswith("madre_sdk") for module in imported)
