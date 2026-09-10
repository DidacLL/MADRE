"""Small Agentless Module used as a protocol proof for the public SDK."""

from madre_sdk import (
    Artifact,
    ContextBundle,
    MaterialRepository,
    Module,
    SecurityLevel,
    participant_security,
)


class ReferenceAgentlessModule(Module):
    def __init__(self) -> None:
        materials = MaterialRepository()
        security = participant_security(
            owner_module_id="reference.notes",
            subject_id="reference.notes",
            subject_kind="module",
            privacy=SecurityLevel.LEVEL_5,
            integrity=SecurityLevel.LEVEL_5,
        )
        super().__init__(
            module_id="reference.notes",
            version="1",
            description="Agentless reference Module that prepares bounded note analysis material",
            security=security,
            discovery_terms=("notes", "analysis"),
            materials=materials,
        )

    def note(self, text: str) -> Artifact:
        return Artifact.create(
            owner_module_id=self.module_id,
            artifact_id="reference.notes:source",
            payload={"text": text},
            sensitivity=SecurityLevel.LEVEL_3,
            integrity=SecurityLevel.LEVEL_5,
            security_history=self.agentless_history(),
        )

    def analysis_context(self, source: Artifact) -> ContextBundle:
        return ContextBundle.derive_from(
            source=source,
            owner_module_id=self.module_id,
            bundle_id="reference.notes:analysis-context",
            purpose="note-analysis",
            payload={"source": source.payload},
            producer_security_ids=(self.security.security_id,),
            sensitivity=SecurityLevel.LEVEL_3,
            security_history=self.agentless_history(),
        )

    def agentless_history(self):
        from madre_sdk import SecurityHistory

        return SecurityHistory(objects=(self.security, self.endpoint_security))
