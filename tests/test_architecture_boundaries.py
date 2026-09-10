from __future__ import annotations

import ast
from pathlib import Path

import pytest
from pydantic import ValidationError

import madre.security as kernel_security
import madre_sdk
from madre.adapters.openai import OpenAIChatConfig
from madre.config import Settings
from madre.contracts import InferenceHardRequirements, InferenceRequirement
from madre.security import (
    DEFAULT_SECURITY_EVALUATOR,
    MaterialSecurityValues,
    ParticipantSecurityValues,
    SecurityHistory,
    SecurityLevel,
    SecurityObject,
    SecuritySubjectRef,
    SecurityTransition,
)
from madre.service import _capabilities
from madre.storage import open_database
from madre_sdk import participant_security

ROOT = Path(__file__).parents[1]
SRC = ROOT / "src"


def imported_modules(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    modules: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            modules.update(alias.name for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module is not None:
            modules.add(node.module)
    return modules


def test_sdk_imports_only_public_kernel_contract_namespaces() -> None:
    allowed = {
        "madre.contracts",
        "madre.interfaces",
        "madre.registry",
        "madre.security",
    }
    imported = set()
    for path in (SRC / "madre_sdk").glob("*.py"):
        imported.update(
            module
            for module in imported_modules(path)
            if module == "madre" or module.startswith("madre.")
        )
    assert imported <= allowed


def test_core_reaches_madre_only_through_sdk() -> None:
    imported = set()
    for path in (SRC / "madre_core").glob("*.py"):
        imported.update(imported_modules(path))
    assert not any(module == "madre" or module.startswith("madre.") for module in imported)
    assert any(module == "madre_sdk" or module.startswith("madre_sdk.") for module in imported)


def test_kernel_does_not_import_sdk_or_core() -> None:
    imported = set()
    for path in (SRC / "madre").rglob("*.py"):
        imported.update(imported_modules(path))
    assert not any(module == "madre_sdk" or module.startswith("madre_sdk.") for module in imported)
    assert not any(
        module == "madre_core" or module.startswith("madre_core.") for module in imported
    )


def test_pre_freeze_security_symbols_are_removed() -> None:
    obsolete = {
        "ActorSecurityValues",
        "OperationSecurityValues",
        "SecurityContext",
        "CompatibilitySecurityEvaluator",
    }
    assert all(not hasattr(kernel_security, name) for name in obsolete)
    assert not hasattr(madre_sdk, "actor_security")
    assert not hasattr(madre_sdk, "operation_security")
    assert not hasattr(madre_sdk, "security_context")


def test_pre_freeze_security_fields_are_removed() -> None:
    assert "intended_use" not in MaterialSecurityValues.model_fields
    assert "trust" not in ParticipantSecurityValues.model_fields
    assert "isolation" not in ParticipantSecurityValues.model_fields
    assert "trust" not in OpenAIChatConfig.model_fields
    assert "risk" not in OpenAIChatConfig.model_fields
    assert {"privacy", "integrity"} <= set(OpenAIChatConfig.model_fields)


def test_security_binding_tamper_is_structural_failure() -> None:
    subject = participant_security(
        owner_module_id="module.example",
        subject_id="module.example",
        subject_kind="module",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
    )
    tampered = subject.model_copy(update={"binding_digest": "0" * 64})
    decision = DEFAULT_SECURITY_EVALUATOR.evaluate(
        SecurityHistory(objects=(tampered,)),
        SecurityTransition.issue(),
    )
    assert "invalid_security_binding" in decision.failure_codes


def test_subject_kind_cannot_carry_irrelevant_value_schema() -> None:
    with pytest.raises(ValidationError):
        SecurityObject.issue(
            subject_ref=SecuritySubjectRef(
                owner_module_id="module.example",
                subject_kind="artifact",
                publication_revision="1",
                local_id="artifact",
            ),
            values=ParticipantSecurityValues(
                privacy=SecurityLevel.LEVEL_5,
                integrity=SecurityLevel.LEVEL_5,
            ),
        )


def capability_security_id(config: OpenAIChatConfig) -> str:
    registry = _capabilities(Settings(capabilities={"chat": config}))
    requirement = InferenceRequirement(
        hard=InferenceHardRequirements(specialization="model.inference.chat")
    )
    return registry.candidates(requirement)[0].descriptor.security.security_id


def test_physical_capability_facts_change_binding_but_credentials_do_not() -> None:
    base = OpenAIChatConfig(
        endpoint="http://127.0.0.1:11434/v1",
        model="local-model",
        privacy=SecurityLevel.LEVEL_5,
        integrity=SecurityLevel.LEVEL_5,
        api_key_env="TOKEN_A",
    )
    other_credential = base.model_copy(update={"api_key_env": "TOKEN_B"})
    other_endpoint = base.model_copy(update={"endpoint": "http://127.0.0.1:11435/v1"})
    assert capability_security_id(base) == capability_security_id(other_credential)
    assert capability_security_id(base) != capability_security_id(other_endpoint)


def test_sqlite_schema_stores_security_metadata_not_private_payload_columns(tmp_path: Path) -> None:
    with open_database(tmp_path) as connection:
        columns = {
            row["name"] for row in connection.execute("PRAGMA table_info(runtime_work)").fetchall()
        }
        assert "security_history_json" in columns
        assert "security_context_json" not in columns
        assert "payload" not in columns
        assert "prompt" not in columns
        tables = {
            row["name"]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type='table'"
            ).fetchall()
        }
        assert {"security_object", "security_transition", "security_derivation"} <= tables
