package io.github.didacll.madre.kernel.client;

public record RetryPolicy(int maxAttempts, long retryDelayMs) {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (retryDelayMs < 0) {
            throw new IllegalArgumentException("retryDelayMs must be >= 0");
        }
    }

    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0);
    }
}
