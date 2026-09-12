from __future__ import annotations

from pathlib import Path

from fastapi.testclient import TestClient

from madre.config import Settings
from madre.service import create_app
from madre_sdk import (
    Material,
    MaterialId,
    MaterialSet,
    MaterialSetJsonCodec,
    ModuleDefinitionJsonCodec,
    Sensitivity,
)
from tests.sdk_fixtures import build_module_definition


def test_http_module_directory_is_live_and_returns_only_reachable_surfaces(
    tmp_path: Path,
) -> None:
    definition = build_module_definition()
    secret_type = definition.material_types[0]
    materials = MaterialSet.of(
        Material(
            MaterialId(definition.identity, "http-secret"),
            secret_type,
            "secret",
            Sensitivity.S5,
        )
    )
    app = create_app(Settings(data_dir=tmp_path))

    with TestClient(app) as client:
        registered = client.post(
            "/v1/modules",
            content=ModuleDefinitionJsonCodec().encode(definition),
        )
        reachable = client.post(
            "/v1/modules/reachable",
            content=MaterialSetJsonCodec().encode(materials),
        )

    assert registered.status_code == 200
    assert reachable.status_code == 200
    body = reachable.json()
    assert [operation["identity"]["name"] for operation in body["entries"][0]["operations"]] == [
        "private-summary"
    ]
