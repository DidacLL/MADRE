package io.github.didacll.madre.text;

import java.util.Objects;

/** Physical completion output; interpretation belongs to the calling Module. */
public record TextInferenceResult(String text, CompletionReason completionReason,
        int promptTokens, int generatedTokens) {
    public TextInferenceResult {
        Objects.requireNonNull(text, "text"); Objects.requireNonNull(completionReason, "completionReason");
        if (promptTokens < -1 || generatedTokens < -1) throw new IllegalArgumentException("token counts must be nonnegative or -1 when unavailable");
    }
    public enum CompletionReason { STOP, LENGTH, OTHER }
}
