package io.github.didacll.madre.kernel;

import java.time.Duration;
import java.util.Objects;

public record RetryPolicy(int maximumAttempts, Duration delay) {
    public RetryPolicy {
        if (maximumAttempts <= 0) throw new IllegalArgumentException("maximumAttempts must be positive");
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) throw new IllegalArgumentException("delay must not be negative");
    }

    public static RetryPolicy noRetry() { return new RetryPolicy(1, Duration.ZERO); }
}
