package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Transient provider-neutral text result; semantic interpretation belongs to MADRE runtime. */
public record TextInferenceOutput(String text, FinishReason finishReason, TokenUsage usage) {
    public enum FinishReason { COMPLETE, LENGTH_LIMIT, ENGINE_STOP, UNKNOWN }

    public TextInferenceOutput {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(finishReason, "finishReason");
        Objects.requireNonNull(usage, "usage");
    }

    public record TokenUsage(long inputTokens, long outputTokens) {
        public TokenUsage {
            if (inputTokens < 0 || outputTokens < 0) throw new IllegalArgumentException("Token counts must not be negative");
        }
        public static TokenUsage unavailable() { return new TokenUsage(0, 0); }
    }
}
