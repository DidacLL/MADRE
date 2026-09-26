package io.github.didacll.madre.kernel.client;

import java.util.Objects;

public record RetryPolicy(int maxAttempts, long retryDelayMs, RetrySafety safety) {
    public RetryPolicy {
        Objects.requireNonNull(safety, "safety");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("retryDelayMs must be >= 0");
        }
        if (safety == RetrySafety.NEVER && maxAttempts != 1) {
            throw new IllegalArgumentException("RetrySafety.NEVER requires maxAttempts == 1");
        }
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0, RetrySafety.NEVER);
    }

    public static RetryPolicy definiteFailures(int maxAttempts, long retryDelayMs) {
        return new RetryPolicy(maxAttempts, retryDelayMs, RetrySafety.DEFINITE_FAILURES);
    }

    public static RetryPolicy includingUnknownCompletion(int maxAttempts, long retryDelayMs) {
        return new RetryPolicy(maxAttempts, retryDelayMs, RetrySafety.INCLUDING_UNKNOWN_COMPLETION);
    }
}
