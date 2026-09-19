package io.github.didacll.madre.embedding;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.util.Objects;

/** Request to embed text into one explicitly identified vector space. */
public record TextEmbeddingCommand(String text, EmbeddingSpace space)
        implements ReasoningComputation<TextEmbeddingResult> {
    public TextEmbeddingCommand {
        if (Objects.requireNonNull(text, "text").isBlank()) throw new IllegalArgumentException("text must not be blank");
        Objects.requireNonNull(space, "space");
    }
    @Override public Class<TextEmbeddingResult> resultType() { return TextEmbeddingResult.class; }
}
