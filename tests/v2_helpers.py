from __future__ import annotations

import asyncio

import pytest

from madre.broker import Broker
from madre.registry import InteroperabilityRegistry
from madre.security import (
    InvocationContext,
    SecurityLevel,
    SecurityObject,
    SecuritySubjectRef,
    TransformSecurityValues,
)
from madre.storage import PlatformStore, open_database
from madre_sdk import (
    Agent,
    Artifact,
    Module,
    Transform,
    TransformContract,
    TransformOutput,
    participant_security,
)
from tests.test_registry_broker import (
    REQUESTER,
    TARGET,
    attachment,
    boundary,
    input_material,
    requester_security,
)

L1 = SecurityLevel.LEVEL_1
L5 = SecurityLevel.LEVEL_5


def participant(owner, name, kind="module", *, assurance=L5, revision="1"):
    return participant_security(
        owner_module_id=owner,
        subject_id=name,
        subject_kind=kind,
        assurance=assurance,
        publication_revision=revision,
    )


def as_artifact(material):
    return Artifact(
        id=material.reference,
        payload=material.payload,
        security=material.security,
        security_history=material.history,
    )


class Repackage:
    def __init__(self, *, passthrough=False):
        self.passthrough = passthrough

    async def execute(self, *, invocation, material, security, **kwargs):
        if self.passthrough:
            return as_artifact(material)
        return Artifact.derive_from(
            invocation=invocation,
            source=material,
            owner_module_id=invocation.module_id,
            artifact_id="new-output",
            payload={"transformed": material.payload},
            producer_security_ids=(),
            sensitivity=L5,
            security_history=security,
        )


def agent_module(behavior, *, owner=TARGET, assurance=L5, privacy=L5, services=None):
    agent = Agent.from_instructions(
        agent_id="actor",
        purpose="test",
        instructions="test",
        security=participant(owner, "actor", "agent", assurance=assurance),
        behavior=behavior,
    )
    return Module(
        module_id=owner,
        version="1",
        description="test",
        security=participant(owner, owner),
        agents=(agent,),
        services=services,
        disclosure_boundaries=(boundary(owner, privacy),),
    )


def attach(registry, broker, module):
    module.register(registry)
    module.register_agent_endpoint(broker)
    module.register_operation_endpoint(broker)
    module.register_transform_endpoint(broker)


def invoke(broker, module, source=None):
    source = source or input_material(requester_security())
    requester = InvocationContext(module=requester_security(), endpoint=attachment(REQUESTER))
    return asyncio.run(
        broker.invoke_agent(
            requester, source.security_history, module.module_id, "actor", source.transient()
        )
    )


@pytest.fixture
def platform(tmp_path):
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        registry = InteroperabilityRegistry(store)
        yield registry, Broker(registry, store), store


def transform_module(behavior, *, name="rewrite", owner="transformer", revision="1"):
    contract = TransformContract(
        id=name,
        module_id=owner,
        security=SecurityObject.issue(
            subject_ref=SecuritySubjectRef(
                owner_module_id=owner,
                subject_kind="transform",
                local_id=name,
                publication_revision=revision,
                subject_revision="1",
            ),
            values=TransformSecurityValues(
                contract_id="test:concrete-classification", evidence_schema="test:v1"
            ),
        ),
    )
    return Module(
        module_id=owner,
        version=revision,
        description="test transform",
        security=participant(owner, owner, revision=revision),
        transforms=(Transform(contract, behavior),),
        disclosure_boundaries=(boundary(owner),),
    )


class ClassifiedOutput:
    async def execute(self, *, material, services):
        # Contract-specific test fixture: only an exact synthetic record is reduced.
        applicable = material.payload == {"credential": "test-secret"}
        return TransformOutput(
            representation_id="redacted" if applicable else "unchanged-classification",
            payload={} if applicable else material.payload,
            sensitivity=L1 if applicable else L5,
            assurance=L5,
        )
