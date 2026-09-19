package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Physical result of the concrete chat-completion inference contract. */
public record ChatCompletionOutput(String text, FinishReason finishReason, TokenUsage usage) {
    public ChatCompletionOutput {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(finishReason, "finishReason");
        Objects.requireNonNull(usage, "usage");
    }

    public enum FinishReason { COMPLETE, ENGINE_STOP, LENGTH_LIMIT, UNKNOWN }

    public record TokenUsage(long inputTokens, long outputTokens) {
        public TokenUsage {
            if (inputTokens < -1 || outputTokens < -1) {
                throw new IllegalArgumentException("token counts must be non-negative or unavailable");
            }
        }
        public static TokenUsage unavailable() { return new TokenUsage(-1, -1); }
    }
}
