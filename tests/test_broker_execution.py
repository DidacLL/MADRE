from __future__ import annotations

import asyncio
from pathlib import Path

import pytest

from madre.broker import (
    Broker,
    ModuleEndpointUnavailable,
    SecurityDenied,
    UnknownOperationEffect,
)
from madre.interfaces import OperationEndpoint
from madre.registry import InteroperabilityRegistry, ModuleManifest, OperationDescriptor
from madre.security import (
    Autonomy,
    EffectProfile,
    Integrity,
    InvocationContext,
    OperationReference,
    OperationUse,
    Privacy,
    Risk,
    SecurityEvidence,
    SecurityObject,
    Sensitivity,
)
from madre.storage import PlatformStore, open_database
from madre_sdk import Artifact, participant_security


def participant(module: str, name: str, integrity: Integrity) -> SecurityObject:
    return participant_security(
        owner_module_id=module,
        scope_id=name,
        privacy=Privacy.SECRET,
        integrity=integrity,
    )


def effect(module: str, risk: Risk = Risk.R5) -> EffectProfile:
    return EffectProfile.issue(
        id="bounded",
        operation=OperationReference(
            module_id=module, operation_id="write", publication_revision="1"
        ),
        risk=risk,
        autonomy=Autonomy.A5,
    )


class PassThrough(OperationEndpoint):
    def __init__(self, security: SecurityObject, *, fail: bool = False) -> None:
        self._security = security
        self._fail = fail
        self.seen: InvocationContext | None = None

    @property
    def boundary(self):
        return "remote"

    @property
    def security(self) -> SecurityObject:
        return self._security

    async def invoke_operation(
        self, operation_id, effect_profile_id, invocation, evidence, material
    ):
        del operation_id, effect_profile_id, evidence
        self.seen = invocation
        if self._fail:
            raise RuntimeError("outcome unknown")
        return material


def configured_broker(path: Path, *, executor: Integrity = Integrity.I5, fail: bool = False):
    connection_context = open_database(path)
    connection = connection_context.__enter__()
    platform = PlatformStore(connection)
    registry = InteroperabilityRegistry(platform)
    target_module = participant("target", "target", Integrity.I5)
    target_endpoint = participant("target", "target:endpoint", executor)
    profile = effect("target")
    registry.register(
        ModuleManifest(
            module_id="target",
            version="1",
            description="Target",
            security=target_module,
            operations=(
                OperationDescriptor(
                    id="write",
                    module_id="target",
                    purpose="bounded write",
                    input_contract="json:any",
                    output_contract="json:any",
                    effect="external",
                    repeatability="unknown",
                    effect_profiles=(profile,),
                ),
            ),
        )
    )
    endpoint = PassThrough(target_endpoint, fail=fail)
    broker = Broker(registry, platform)
    broker.attach_operation_endpoint("target", endpoint)
    return connection_context, connection, registry, broker, endpoint, profile


def test_operation_dispatch_requires_all_three_relations(tmp_path: Path) -> None:
    context, connection, _, broker, endpoint, profile = configured_broker(tmp_path)
    try:
        requester_module = participant("requester", "requester", Integrity.I5)
        requester = InvocationContext(module=requester_module)
        material = Artifact.create(
            owner_module_id="requester",
            artifact_id="input",
            payload="secret",
            sensitivity=Sensitivity.S5,
        ).transient()
        result = asyncio.run(
            broker.invoke_operation(
                requester,
                SecurityEvidence(objects=requester.objects),
                "target",
                "write",
                OperationUse(profile_id=profile.id),
                material,
            )
        )
        assert result.payload == "secret"
        assert endpoint.seen is not None
        rows = connection.execute(
            "SELECT relation_kind,admissible FROM security_decision "
            "WHERE crossing_kind LIKE 'operation-%' ORDER BY id"
        ).fetchall()
        assert [(row[0], row[1]) for row in rows] == [
            ("disclosure", 1),
            ("control", 1),
            ("effect_execution", 1),
            ("disclosure", 1),
        ]
    finally:
        context.__exit__(None, None, None)


def test_insufficient_actual_executor_prevents_dispatch(tmp_path: Path) -> None:
    context, _, _, broker, endpoint, profile = configured_broker(tmp_path, executor=Integrity.I1)
    try:
        requester = InvocationContext(module=participant("requester", "requester", Integrity.I5))
        material = Artifact.create(
            owner_module_id="requester",
            artifact_id="input",
            payload="secret",
            sensitivity=Sensitivity.S5,
        ).transient()
        with pytest.raises(SecurityDenied, match="executor_integrity_below_risk"):
            asyncio.run(
                broker.invoke_operation(
                    requester,
                    SecurityEvidence(objects=requester.objects),
                    "target",
                    "write",
                    OperationUse(profile_id=profile.id),
                    material,
                )
            )
        assert endpoint.seen is None
    finally:
        context.__exit__(None, None, None)


def test_unknown_external_effect_is_not_retried_or_reclassified(tmp_path: Path) -> None:
    context, _, _, broker, endpoint, profile = configured_broker(tmp_path, fail=True)
    try:
        requester = InvocationContext(module=participant("requester", "requester", Integrity.I5))
        material = Artifact.create(
            owner_module_id="requester",
            artifact_id="input",
            payload="secret",
            sensitivity=Sensitivity.S5,
        ).transient()
        with pytest.raises(UnknownOperationEffect):
            asyncio.run(
                broker.invoke_operation(
                    requester,
                    SecurityEvidence(objects=requester.objects),
                    "target",
                    "write",
                    OperationUse(profile_id=profile.id),
                    material,
                )
            )
        assert endpoint.seen is not None
    finally:
        context.__exit__(None, None, None)


def test_endpoint_attachment_is_bound_to_the_exact_publication_snapshot(tmp_path: Path) -> None:
    context, connection, registry, broker, endpoint, profile = configured_broker(tmp_path)
    try:
        target_security = participant("target", "target", Integrity.I5)
        registry.register(
            ModuleManifest(
                module_id="target",
                version="1",
                description="Same revision was replaced",
                security=target_security,
                operations=(
                    OperationDescriptor(
                        id="write",
                        module_id="target",
                        purpose="replacement",
                        input_contract="json:any",
                        output_contract="json:any",
                        effect="external",
                        repeatability="unknown",
                        effect_profiles=(profile,),
                    ),
                ),
            )
        )
        requester = InvocationContext(module=participant("requester", "requester", Integrity.I5))
        material = Artifact.create(
            owner_module_id="requester",
            artifact_id="input",
            payload="secret",
            sensitivity=Sensitivity.S5,
        ).transient()
        with pytest.raises(ModuleEndpointUnavailable, match="stale"):
            asyncio.run(
                broker.invoke_operation(
                    requester,
                    SecurityEvidence(objects=requester.objects),
                    "target",
                    "write",
                    OperationUse(profile_id=profile.id),
                    material,
                )
            )
        assert endpoint.seen is None
        assert connection.execute("SELECT COUNT(*) FROM broker_event").fetchone()[0] == 0
    finally:
        context.__exit__(None, None, None)
