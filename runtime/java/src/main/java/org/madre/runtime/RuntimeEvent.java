package org.madre.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RuntimeEvent(
        UUID eventId,
        String type,
        UUID subjectId,
        UUID actorId,
        String summary,
        UUID payloadId,
        Instant createdAt
) {
    public RuntimeEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RuntimeEvent now(String type, UUID subjectId, UUID actorId, String summary, UUID payloadId) {
        return new RuntimeEvent(UUID.randomUUID(), type, subjectId, actorId, summary, payloadId, Instant.now());
    }

    public UUID id() {
        return eventId;
    }

    public Optional<UUID> payloadRef() {
        return Optional.ofNullable(payloadId);
    }
}
