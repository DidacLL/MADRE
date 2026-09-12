from __future__ import annotations

import asyncio
from pathlib import Path

import pytest

from madre import CapabilityRegistry, FunctionCapability, Kernel
from madre.catalog import ModuleCatalog
from madre.contracts import RetryDisposition, WorkRetryRequest, WorkSubmission
from madre.runtime import RetryConflict
from madre.storage import PlatformStore, open_database
from madre_sdk import (
    CapabilityDefinition,
    CapabilityProperties,
    CapabilityQuery,
    ExecutionBoundary,
    IdentityKind,
    Material,
    MaterialContract,
    MaterialRepository,
    MaterialSpecification,
    ModuleDefinition,
    Privacy,
    ScopeIdentity,
    SecurityScope,
    SecuritySurface,
    Sensitivity,
)


def identifier(
    owner: str,
    name: str,
    kind: IdentityKind = IdentityKind.SURFACE,
) -> ScopeIdentity:
    return ScopeIdentity(kind=kind, owner=owner, name=name)


def test_durable_work_persists_only_reference_and_execution_metadata(tmp_path: Path) -> None:
    module = identifier("module", "module", IdentityKind.MODULE)
    specialization = identifier("madre.execution", "inference", IdentityKind.SPECIALIZATION)
    modality = identifier("madre.execution", "json", IdentityKind.MODALITY)
    contract = MaterialContract(
        identity=identifier("module", "contract", IdentityKind.MATERIAL_CONTRACT),
        media_type="application/json",
    )
    source_identity = identifier("module", "source", IdentityKind.MATERIAL)
    source = Material[dict[str, str]](
        identity=source_identity,
        contract=contract,
        payload={"request": "execute"},
        security=SecurityScope(
            identity=source_identity,
            sensitivity=Sensitivity.S2,
        ),
    )
    repository = MaterialRepository()
    repository.put(source)

    capability_identity = identifier("physical", "fixture", IdentityKind.CAPABILITY)
    definition = CapabilityDefinition(
        identity=capability_identity,
        properties=CapabilityProperties(
            specialization=specialization,
            modality=modality,
            boundary=ExecutionBoundary.LOCAL,
        ),
        security=SecuritySurface.compose(
            SecurityScope(
                identity=capability_identity,
                privacy=Privacy.SECRET,
            )
        ),
    )
    capabilities = CapabilityRegistry()
    capabilities.register(FunctionCapability(definition, lambda payload: {"result": payload}))

    output_identity = identifier("module", "output", IdentityKind.MATERIAL)
    with open_database(tmp_path) as connection:
        store = PlatformStore(connection)
        catalog = ModuleCatalog(store)
        catalog.register(
            ModuleDefinition(
                identity=module,
                description="Ordinary Module",
                managed_scopes=(source.security,),
            )
        )
        kernel = Kernel(capabilities, store)
        kernel.register_material_resolver(module, repository)
        record = asyncio.run(
            kernel.submit(
                WorkSubmission(
                    originator=module,
                    capability=CapabilityQuery(
                        specialization=specialization,
                        modality=modality,
                    ),
                    material=source.handle(),
                    output=MaterialSpecification(
                        identity=output_identity,
                        contract=contract,
                        security=SecurityScope(
                            identity=output_identity,
                            sensitivity=Sensitivity.S2,
                        ),
                    ),
                )
            )
        )

        assert source.payload["request"] not in record.model_dump_json()
        assert asyncio.run(kernel.run_eligible()) == 1
        completed = kernel.inspect(record.id)
        assert completed is not None
        assert completed.status == "succeeded"
        result = kernel.consume_result(record.id)
        assert result.identity == output_identity
        assert result.payload == {"result": source.payload}
        assert catalog.module(module) is not None

        public_capability_identity = identifier(
            "physical", "public-fixture", IdentityKind.CAPABILITY
        )
        capabilities.register(
            FunctionCapability(
                CapabilityDefinition(
                    identity=public_capability_identity,
                    properties=CapabilityProperties(
                        specialization=specialization,
                        modality=modality,
                        boundary=ExecutionBoundary.REMOTE,
                    ),
                    security=SecuritySurface.compose(
                        SecurityScope(
                            identity=public_capability_identity,
                            privacy=Privacy.PUBLIC,
                        )
                    ),
                ),
                lambda payload: payload,
            )
        )
        secret_identity = identifier("module", "terminal-secret", IdentityKind.MATERIAL)
        secret = Material[dict[str, str]](
            identity=secret_identity,
            contract=contract,
            payload={"private": "value"},
            security=SecurityScope(
                identity=secret_identity,
                sensitivity=Sensitivity.S5,
            ),
        )
        repository.put(secret)
        rejected_output = identifier("module", "never-produced", IdentityKind.MATERIAL)
        rejected = asyncio.run(
            kernel.submit(
                WorkSubmission(
                    originator=module,
                    capability=CapabilityQuery(
                        specialization=specialization,
                        modality=modality,
                        mechanism=public_capability_identity,
                    ),
                    material=secret.handle(),
                    output=MaterialSpecification(
                        identity=rejected_output,
                        contract=contract,
                        security=SecurityScope(
                            identity=rejected_output,
                            sensitivity=Sensitivity.S5,
                        ),
                    ),
                )
            )
        )
        assert asyncio.run(kernel.run_eligible()) == 1
        terminal = kernel.inspect(rejected.id)
        assert terminal is not None
        assert terminal.failure is not None
        assert terminal.failure.retry is RetryDisposition.TERMINAL
        assert "security" not in terminal.failure.code
        with pytest.raises(RetryConflict, match="submit a different request"):
            asyncio.run(
                kernel.retry(
                    rejected.id,
                    WorkRetryRequest(),
                    idempotency_key="terminal-request",
                )
            )
        assert terminal.retries == ()

        tables = {
            row["name"]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
        }
        assert not tables.intersection(
            {
                "security_object",
                "security_relation",
                "security_derivation",
                "security_decision",
                "broker_event",
            }
        )
