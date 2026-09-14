package io.github.didacll.madre.sdk.execution;

import java.time.Duration;
import java.util.Objects;

/** Bounded retry controls for reasoning-mechanism failures. */
public record ReasoningRetryPolicy(int maximumAttempts, Duration delay) {
    public ReasoningRetryPolicy {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) throw new IllegalArgumentException("delay must not be negative");
    }

    public static ReasoningRetryPolicy none() {
        return new ReasoningRetryPolicy(1, Duration.ZERO);
    }
}
