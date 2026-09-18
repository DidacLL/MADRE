package io.github.didacll.madre.embedding;

import java.util.Objects;

/** Identity of one vector space whose coordinates are mutually comparable. */
public record EmbeddingSpace(String id, int dimensions) {
    public EmbeddingSpace {
        if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("id must not be blank");
        id = id.strip();
        if (dimensions < 1) throw new IllegalArgumentException("dimensions must be positive");
    }
}
