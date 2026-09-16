package io.github.didacll.madre.embedding;

import java.util.List;
import java.util.Objects;

/** Physical embedding output whose coordinates are meaningful only in its declared space. */
public record TextEmbeddingResult(EmbeddingSpace space, List<Double> values) {
    public TextEmbeddingResult {
        Objects.requireNonNull(space, "space");
        values = List.copyOf(Objects.requireNonNull(values, "values"));
        if (values.size() != space.dimensions()) {
            throw new IllegalArgumentException(
                    "embedding vector length must equal space dimensions");
        }
        if (values.stream().anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalArgumentException("embedding values must be finite");
        }
    }
}
