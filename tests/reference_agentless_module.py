"""Small Agentless Module used as a protocol proof for the public SDK."""

from madre_sdk import (
    Artifact,
    ContextBundle,
    MaterialRepository,
    Module,
    SecurityLevel,
    actor_security,
)


class ReferenceAgentlessModule(Module):
    def __init__(self) -> None:
        materials = MaterialRepository()
        super().__init__(
            module_id="reference.notes",
            version="1",
            description="Agentless reference Module that prepares bounded note analysis material",
            security=actor_security(
                subject_id="reference.notes",
                subject_kind="module",
                trust=SecurityLevel.LEVEL_5,
                isolation=SecurityLevel.LEVEL_5,
            ),
            discovery_terms=("notes", "analysis"),
            materials=materials,
        )

    def note(self, text: str) -> Artifact:
        return Artifact.create(
            artifact_id="reference.notes:source",
            payload={"text": text},
            sensitivity=SecurityLevel.LEVEL_3,
            intended_use=SecurityLevel.LEVEL_2,
        )

    def analysis_context(self, source: Artifact) -> ContextBundle:
        return ContextBundle.create(
            bundle_id="reference.notes:analysis-context",
            purpose="note-analysis",
            payload={"source": source.payload},
            sensitivity=SecurityLevel.LEVEL_3,
            intended_use=SecurityLevel.LEVEL_2,
        )
