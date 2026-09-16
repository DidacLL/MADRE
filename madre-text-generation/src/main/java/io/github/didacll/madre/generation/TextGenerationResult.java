package io.github.didacll.madre.generation;

import java.util.Objects;

/** Physical text-generation output; semantic interpretation remains Module-owned. */
public record TextGenerationResult(String text, CompletionReason completionReason,
        int inputTokens, int generatedTokens) {
    public TextGenerationResult {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(completionReason, "completionReason");
        if (inputTokens < -1 || generatedTokens < -1) {
            throw new IllegalArgumentException(
                    "token counts must be nonnegative or -1 when unavailable");
        }
    }

    public enum CompletionReason { STOP, LENGTH, OTHER }
}
