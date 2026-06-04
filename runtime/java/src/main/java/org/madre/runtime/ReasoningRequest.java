package org.madre.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ReasoningRequest(
        UUID requestId,
        String objective,
        UUID originId,
        UUID targetModuleId,
        List<UUID> contextIds,
        BoundaryProfile boundary,
        String expectedResult,
        List<UUID> requestChain,
        Instant createdAt
) {
    public ReasoningRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(originId, "originId");
        contextIds = List.copyOf(Objects.requireNonNull(contextIds, "contextIds"));
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(expectedResult, "expectedResult");
        requestChain = List.copyOf(Objects.requireNonNull(requestChain, "requestChain"));
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static ReasoningRequest coreRequest(String objective, MADREKernel origin, BoundaryProfile boundary) {
        return new ReasoningRequest(
                UUID.randomUUID(),
                objective,
                origin.id(),
                null,
                List.of(),
                boundary,
                "artifact",
                List.of(),
                Instant.now()
        );
    }

    public UUID id() {
        return requestId;
    }

    public Optional<UUID> targetModuleRef() {
        return Optional.ofNullable(targetModuleId);
    }

    public boolean isRoutable() {
        return !objective.isBlank() && originId != null && boundary != null && createdAt != null;
    }
}
