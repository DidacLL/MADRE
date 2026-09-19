package io.github.didacll.madre.sdk.execution;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Inspection view of durable reasoning work. */
public record WorkStatus(WorkId id, WorkState state, int attempts, Instant eligibleAt,
        Optional<ReasoningFailureCategory> lastFailureCategory, Optional<Instant> completedAt) {
    public WorkStatus {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(eligibleAt, "eligibleAt");
        lastFailureCategory = Objects.requireNonNull(lastFailureCategory,
                "lastFailureCategory");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
    }
}
