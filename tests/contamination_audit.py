#!/usr/bin/env python3
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION = [ROOT / "kernel" / "src", ROOT / "madre-kernel-client" / "src" / "main"]
AUTHORITY = [
    ROOT / "NORTH_STAR.md",
    ROOT / "AGENTS.md",
    ROOT / "MADRE.md",
    ROOT / "README.md",
    ROOT / "docs" / "product" / "lane-c-owner-decision.md",
    ROOT / "docs" / "product" / "owner-intent-corpus.md",
    ROOT / "docs" / "architecture" / "mid-level-architecture.md",
    ROOT / "docs" / "architecture" / "kernel.md",
]

forbidden_patterns = {
    r"\bReasoningRequest\b": "semantic ReasoningRequest leaked into Lane C production",
    r"\bSPIRA\b": "semantic SPIRA leaked into Lane C production",
    r"\bWorkPlan\b": "semantic WorkPlan leaked into Lane C production",
    r"\bCORE\b": "semantic CORE leaked into Lane C production",
    r"semantic continuation": "semantic continuation leaked into Lane C production",
    r"\bEngineDescriptor\b": "rejected engine inventory ontology returned",
    r"\bWorkerPool\b": "rejected worker ownership returned",
    r"engine_inventory": "rejected engine inventory returned",
    r"model lifecycle": "rejected model lifecycle ownership returned",
    r"exact_model_id": "preselected exact-model routing fossil returned",
    r"exact_engine_id": "preselected exact-engine routing fossil returned",
    r"protocol[-_ ]?v4": "protocol-v4 compatibility returned",
    r"madre-sdk": "physical Lane C depends on semantic SDK",
    r"Kestrel|Microsoft\.AspNetCore": "web-host control plane returned",
    r"HttpClient|HttpRequest|HttpResponse|HttpStatus|MapGet|MapPost": "web control-plane vocabulary returned",
    r"\bTcpListener\b|\bTcpClient\b|AddressFamily\.InterNetwork(?:V6)?": "TCP control plane returned",
    r"127\.0\.0\.1": "loopback TCP control plane returned",
    r"--port": "configurable control-plane port returned",
    r"Microsoft\.Agents\.AI\.Workflows|MafTwoStage|maf-two-stage|maf-checkpoint": "MAF validation strategy residue returned",
    r"CheckpointSessionId|CheckpointId|\bCheckpointed\b": "checkpoint scaffolding returned",
    r"SuccessfulObservationCount|FailureObservationCount": "unused capability aggregate returned",
    r"UnknownReobserveInterval|AvailableReobserveInterval": "idle periodic capability probe policy returned",
}

provider_patterns = re.compile(r"\b(llama\.cpp|Ollama|OpenAI|Anthropic|Gemini|LM Studio|vLLM|Hugging Face)\b", re.IGNORECASE)

failures: list[str] = []
for root in PRODUCTION:
    for path in root.rglob("*"):
        if not path.is_file() or path.suffix.lower() not in {".cs", ".java", ".csproj", ".kts"}:
            continue
        text = path.read_text(encoding="utf-8")
        for pattern, message in forbidden_patterns.items():
            if re.search(pattern, text):
                failures.append(f"{path.relative_to(ROOT)}: {message}")
        if provider_patterns.search(text):
            failures.append(f"{path.relative_to(ROOT)}: provider/model-specific production routing or ownership found")

for path in AUTHORITY:
    text = path.read_text(encoding="utf-8")
    for pattern, label in {
        r"loopback HTTP|production control boundary is loopback|current /v1|speaks only the current /v1|Kestrel": "authority still presents the superseded web control plane",
        r"current richer strategy uses MAF|current two-stage MAF|MAF checkpoint is current|concrete checkpointed two-stage strategy|MAF checkpoint/hard-restart suite": "authority still presents MAF validation behavior as current",
        r"bf81a342": "authority still claims prior Lane C closure SHA",
        r"Lane C (?:is|was) (?:finally )?closed": "authority claims Lane C closure that belongs to Owner/orchestrator audit",
    }.items():
        if re.search(pattern, text, re.IGNORECASE):
            failures.append(f"{path.relative_to(ROOT)}: {label}")

obsolete = [
    ROOT / "kernel-dotnet",
    ROOT / "kernel" / "CMakeLists.txt",
    ROOT / "tests" / "Madre.Kernel.MafAcceptance",
    ROOT / "tests" / "Madre.Kernel.ClosureAcceptance",
    ROOT / "kernel" / "src" / "Madre.Kernel" / "MafTwoStagePhysicalStrategy.cs",
    ROOT / "kernel" / "src" / "Madre.Kernel.Host" / "KernelWebHost.cs",
]
for path in obsolete:
    if path.exists():
        failures.append(f"obsolete active-tree artifact remains: {path.relative_to(ROOT)}")

process_fixture = ROOT / "tests" / "Madre.Kernel.ProcessFixture" / "Program.cs"
if process_fixture.exists() and "MAF_STAGE_A_MARKER" in process_fixture.read_text(encoding="utf-8"):
    failures.append("tests/Madre.Kernel.ProcessFixture/Program.cs: validation-only MAF fixture behavior remains")

client_build = (ROOT / "madre-kernel-client" / "build.gradle.kts").read_text(encoding="utf-8")
if "sourceSets.test" in client_build and "Jar::class" in client_build:
    failures.append("madre-kernel-client/build.gradle.kts: test-source classes are packaged into a Java artifact")

production_helper = "io/github/didacll/madre/kernel/client/KernelClientProcess.class"
for jar in (ROOT / "madre-kernel-client" / "build" / "libs").glob("*.jar"):
    try:
        with zipfile.ZipFile(jar) as archive:
            if production_helper in archive.namelist():
                failures.append(f"{jar.relative_to(ROOT)}: acceptance/test helper was packaged in a production Java artifact")
    except zipfile.BadZipFile:
        failures.append(f"{jar.relative_to(ROOT)}: Java artifact is not a readable jar")

sqlite = (ROOT / "kernel" / "src" / "Madre.Kernel" / "SqliteDatabase.cs").read_text(encoding="utf-8")
if "CREATE TABLE IF NOT EXISTS" in sqlite.upper():
    failures.append("kernel/src/Madre.Kernel/SqliteDatabase.cs: schema identity regressed to shape-probing IF NOT EXISTS")
if "PRAGMA user_version" not in sqlite:
    failures.append("kernel/src/Madre.Kernel/SqliteDatabase.cs: explicit current schema identity missing")

native_sources = list((ROOT / "kernel").rglob("*.cpp")) + list((ROOT / "kernel").rglob("*.hpp"))
for path in native_sources:
    failures.append(f"native superseded Kernel source remains: {path.relative_to(ROOT)}")

if failures:
    print("Lane C contamination audit failed:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

print("Lane C contamination audit passed")
