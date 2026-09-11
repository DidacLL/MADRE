from __future__ import annotations

import ast
import inspect
from pathlib import Path

import madre.broker
import madre.registry
import madre.runtime
from madre.registry import InteroperabilityRegistry

ROOT = Path(__file__).parents[1]
SRC = ROOT / "src"


def imported_roots(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"))
    roots: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            roots.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.module:
            roots.add(node.module.split(".")[0])
    return roots


def test_core_uses_only_sdk_and_kernel_does_not_import_sdk_or_core() -> None:
    assert "madre" not in imported_roots(SRC / "madre_core" / "core.py")
    for path in (SRC / "madre").glob("*.py"):
        assert not ({"madre_sdk", "madre_core"} & imported_roots(path)), path


def test_sdk_imports_only_public_kernel_contract_namespaces() -> None:
    allowed = {"madre.contracts", "madre.interfaces", "madre.registry", "madre.security"}
    for path in (SRC / "madre_sdk").glob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        kernel_imports = {
            node.module
            for node in ast.walk(tree)
            if isinstance(node, ast.ImportFrom)
            and node.module is not None
            and node.module.startswith("madre.")
        }
        assert kernel_imports <= allowed, (path, kernel_imports - allowed)


def test_runtime_registry_and_broker_have_no_policy_injection() -> None:
    for constructor in (
        madre.runtime.WorkRuntime.__init__,
        madre.broker.Broker.__init__,
        madre.registry.InteroperabilityRegistry.__init__,
    ):
        assert "security_evaluator" not in inspect.signature(constructor).parameters


def test_registry_enumeration_has_no_evidence_or_history_operand() -> None:
    for name in ("list_agents", "list_skills", "list_workflows", "list_operations"):
        parameters = inspect.signature(getattr(InteroperabilityRegistry, name)).parameters
        assert tuple(parameters) == ("self",)


def test_superseded_security_fossils_are_absent_from_public_source() -> None:
    forbidden = (
        "SecurityEvaluator",
        "DEFAULT_SECURITY_EVALUATOR",
        "SecurityAlgebra.evaluate",
        "SecurityTransition",
        "SecurityHistory",
        "SecurityValues",
        "SecurityLevel",
        "OrdinarySecurityLevel",
        "material_security_id",
        "discloses_material",
        "output_integrity",
        "security_history_json",
        "security_transition",
        "discover_agents",
        "discover_skills",
        "discover_workflows",
        "discover_operations",
    )
    source = "\n".join(
        path.read_text(encoding="utf-8")
        for path in SRC.rglob("*.py")
        if path.name != "_facet_type_regression.py"
    )
    for symbol in forbidden:
        assert symbol not in source


def test_effect_profiles_and_evidence_schema_store_no_private_payload() -> None:
    ddl = (SRC / "madre" / "storage_db.py").read_text(encoding="utf-8")
    assert "payload" not in ddl.lower()
    assert "security_evidence_json" in ddl
    assert "security_relation" in ddl
    assert "output_integrity" not in ddl
