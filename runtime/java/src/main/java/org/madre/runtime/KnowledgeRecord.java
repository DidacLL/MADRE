package org.madre.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record KnowledgeRecord(
        UUID knowledgeId,
        UUID ownerModuleId,
        String contentRef,
        String provenance,
        String intendedUse,
        String truthLevel,
        String truthAuthority,
        BoundaryProfile boundary,
        Instant createdAt
) {
    public KnowledgeRecord {
        Objects.requireNonNull(knowledgeId, "knowledgeId");
        Objects.requireNonNull(ownerModuleId, "ownerModuleId");
        Objects.requireNonNull(contentRef, "contentRef");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(intendedUse, "intendedUse");
        Objects.requireNonNull(truthLevel, "truthLevel");
        Objects.requireNonNull(truthAuthority, "truthAuthority");
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() {
        return knowledgeId;
    }
}
