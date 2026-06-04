package org.madre.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ContextBundle(
        UUID bundleId,
        UUID createdForId,
        List<UUID> contentIds,
        String purpose,
        BoundaryProfile boundary,
        Instant createdAt
) {
    public ContextBundle {
        Objects.requireNonNull(bundleId, "bundleId");
        Objects.requireNonNull(createdForId, "createdForId");
        contentIds = List.copyOf(Objects.requireNonNull(contentIds, "contentIds"));
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public UUID id() {
        return bundleId;
    }

    public boolean isUsableFor(BoundaryProfile targetBoundary) {
        return boundary.compatibleWith(targetBoundary);
    }
}
