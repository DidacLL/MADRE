#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION = [ROOT / "kernel" / "src", ROOT / "madre-kernel-client" / "src" / "main"]

forbidden_patterns = {
    r"\bReasoningRequest\b": "semantic ReasoningRequest leaked into Lane C",
    r"\bSPIRA\b": "semantic SPIRA leaked into Lane C",
    r"\bWorkPlan\b": "semantic WorkPlan leaked into Lane C",
    r"\bCORE\b": "semantic CORE leaked into Lane C",
    r"semantic continuation": "semantic continuation leaked into Lane C",
    r"\bEngineDescriptor\b": "rejected engine inventory ontology returned",
    r"\bWorkerPool\b": "rejected worker ownership returned",
    r"engine_inventory": "rejected engine inventory returned",
    r"model lifecycle": "rejected model lifecycle ownership returned",
    r"exact_model_id": "preselected exact-model routing fossil returned",
    r"exact_engine_id": "preselected exact-engine routing fossil returned",
    r"protocol[-_ ]?v4": "protocol-v4 compatibility returned",
    r"/_validation/": "validation-only production route returned",
    r"hold-checkpointed": "validation-only checkpoint hold returned",
    r"madre-sdk": "physical Lane C depends on semantic SDK",
}

provider_patterns = re.compile(r"\b(llama\.cpp|Ollama|OpenAI|Anthropic|Gemini|LM Studio|vLLM|Hugging Face)\b", re.IGNORECASE)

failures: list[str] = []
for root in PRODUCTION:
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".cs", ".java", ".csproj", ".kts"}:
            continue
        text = path.read_text(encoding="utf-8")
        for pattern, message in forbidden_patterns.items():
            if re.search(pattern, text, re.IGNORECASE):
                failures.append(f"{path.relative_to(ROOT)}: {message}")
        if provider_patterns.search(text):
            failures.append(f"{path.relative_to(ROOT)}: provider/model-specific production routing or ownership found")

obsolete = [
    ROOT / "kernel-dotnet",
    ROOT / "kernel" / "CMakeLists.txt",
    ROOT / "kernel" / "tests" / "lcr2_acceptance.py",
    ROOT / "kernel" / "tests" / "lcr3_acceptance.py",
    ROOT / "madre-kernel-client" / "src" / "main" / "java" / "io" / "github" / "didacll" / "madre" / "kernel" / "client" / "Protocol.java",
    ROOT / "madre-kernel-client" / "src" / "main" / "java" / "io" / "github" / "didacll" / "madre" / "kernel" / "client" / "ConcretePhysicalInvocation.java",
]
for path in obsolete:
    if path.exists():
        failures.append(f"obsolete active-tree artifact remains: {path.relative_to(ROOT)}")

native_sources = list((ROOT / "kernel").rglob("*.cpp")) + list((ROOT / "kernel").rglob("*.hpp"))
for path in native_sources:
    failures.append(f"native superseded Kernel source remains: {path.relative_to(ROOT)}")

if failures:
    print("Lane C contamination audit failed:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("Lane C contamination audit passed")
