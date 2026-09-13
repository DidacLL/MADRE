package io.github.didacll.madre.sdk.execution;

import java.time.Duration;
import java.util.Objects;

/** Bounded retry controls for failures of a physical mechanism. */
public record PhysicalRetryPolicy(int maximumAttempts, Duration delay) {
    public PhysicalRetryPolicy {
        if (maximumAttempts < 1) throw new IllegalArgumentException("maximumAttempts must be positive");
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) throw new IllegalArgumentException("delay must not be negative");
    }
    public static PhysicalRetryPolicy none() { return new PhysicalRetryPolicy(1, Duration.ZERO); }
}
