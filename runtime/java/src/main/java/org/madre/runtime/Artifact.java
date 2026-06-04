package org.madre.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Artifact(
        UUID artifactId,
        ArtifactKind kind,
        String contentRef,
        BoundaryProfile boundary,
        Status validationStatus,
        Status lifecycleStatus,
        Instant createdAt
) {
    public Artifact {
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(contentRef, "contentRef");
        Objects.requireNonNull(boundary, "boundary");
        Objects.requireNonNull(validationStatus, "validationStatus");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Artifact response(String content, BoundaryProfile boundary) {
        return new Artifact(UUID.randomUUID(), ArtifactKind.RESPONSE, content, boundary, Status.VALIDATED, Status.ACTIVE, Instant.now());
    }

    public static Artifact generated(String content, BoundaryProfile boundary) {
        return new Artifact(UUID.randomUUID(), ArtifactKind.GENERATED_OUTPUT, content, boundary, Status.REQUIRES_VALIDATION, Status.ACTIVE, Instant.now());
    }

    public static Artifact failure(String content, BoundaryProfile boundary) {
        return new Artifact(UUID.randomUUID(), ArtifactKind.FAILURE, content, boundary, Status.REQUIRES_VALIDATION, Status.ACTIVE, Instant.now());
    }

    public UUID id() {
        return artifactId;
    }

    public boolean requiresValidation() {
        return boundary.validationRequired() || validationStatus == Status.REQUIRES_VALIDATION;
    }
}
