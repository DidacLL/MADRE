package io.github.didacll.madre.generation;

import java.util.Objects;

/** Text-generation output awaiting Module-owned semantic interpretation. */
public record TextGenerationResult(String text, CompletionReason completionReason,
        int inputTokens, int generatedTokens) {
    public TextGenerationResult {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(completionReason, "completionReason");
        if (inputTokens < -1 || generatedTokens < -1) {
            throw new IllegalArgumentException("token counts must be nonnegative or -1");
        }
    }
    public enum CompletionReason { STOP, LENGTH, OTHER }
}
