package io.github.didacll.madre.kernel;

import java.time.Instant;
import java.util.Objects;

/** Durable physical work description. The inference input is deliberately not a field. */
public record InferenceWork<I, O>(
        WorkId id,
        InferenceType<I, O> type,
        InferenceRequirements requirements,
        Instant createdAt) {
    public InferenceWork {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static <I, O> InferenceWork<I, O> create(
            InferenceType<I, O> type, InferenceRequirements requirements) {
        return new InferenceWork<>(WorkId.create(), type, requirements, Instant.now());
    }
}
