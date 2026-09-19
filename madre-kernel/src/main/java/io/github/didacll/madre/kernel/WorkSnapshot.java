package io.github.didacll.madre.kernel;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Technical inspection view containing neither inference content nor semantic identity. */
public record WorkSnapshot(
        WorkId id,
        String inferenceType,
        WorkStatus status,
        int attempts,
        Optional<EngineId> lastEngine,
        Optional<TechnicalFailure> failure,
        Instant createdAt,
        Instant updatedAt) {
    public WorkSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(inferenceType, "inferenceType");
        Objects.requireNonNull(status, "status");
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
        lastEngine = Objects.requireNonNull(lastEngine, "lastEngine");
        failure = Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
