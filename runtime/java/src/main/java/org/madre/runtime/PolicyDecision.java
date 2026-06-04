package org.madre.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PolicyDecision(
        UUID decisionId,
        PolicyOutcome outcome,
        String reason,
        UUID subjectId,
        BoundaryProfile effectiveBoundary,
        Instant createdAt
) {
    public PolicyDecision {
        Objects.requireNonNull(decisionId, "decisionId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(effectiveBoundary, "effectiveBoundary");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static PolicyDecision allow(UUID subjectId, BoundaryProfile boundary, String reason) {
        return new PolicyDecision(UUID.randomUUID(), PolicyOutcome.ALLOW, reason, subjectId, boundary, Instant.now());
    }

    public static PolicyDecision block(UUID subjectId, BoundaryProfile boundary, String reason) {
        return new PolicyDecision(UUID.randomUUID(), PolicyOutcome.BLOCK, reason, subjectId, boundary, Instant.now());
    }

    public UUID id() {
        return decisionId;
    }

    public boolean allowsExecution() {
        return outcome == PolicyOutcome.ALLOW;
    }
}
