package io.github.didacll.madre.sdk.execution;

/** Caller-supplied opaque key for ordinary cancellation of submitted physical work. */
public record CancellationKey(String value) {
    public CancellationKey {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("cancellation key must not be blank");
        value = value.strip();
    }
}
