from __future__ import annotations

import asyncio
from pathlib import Path

from madre import CapabilityRegistry, FunctionCapability, Kernel
from madre.catalog import ModuleCatalog
from madre.contracts import WorkSubmission
from madre.storage import PlatformStore, open_database
from madre_sdk import (
    CapabilityDefinition,
    CapabilityProperties,
    CapabilityQuery,
    ExecutionBoundary,
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


def identifier(owner: str, name: str) -> ScopeIdentity:
    return ScopeIdentity(owner=owner, name=name)


def test_durable_work_persists_only_reference_and_execution_metadata(tmp_path: Path) -> None:
    module = identifier("module", "module")
    specialization = identifier("madre.execution", "inference")
    modality = identifier("madre.execution", "json")
    contract = MaterialContract(
        identity=identifier("module", "contract"),
        media_type="application/json",
    )
    source_identity = identifier("module", "source")
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

    capability_identity = identifier("physical", "fixture")
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

    output_identity = identifier("module", "output")
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
        kernel.register_material_resolver(module.owner, repository)
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
        assert catalog.module(module.owner) is not None

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
