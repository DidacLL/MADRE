package io.github.didacll.madre.interaction;

import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import java.time.Duration;
import java.util.Objects;

/** Reasoning controls chosen by the shipped Module's bounded behavior. */
public record OwnerInteractionSettings(int foregroundMaximumTokens,
        int backgroundMaximumTokens, Duration foregroundTimeout,
        Duration backgroundTimeout, ReasoningRetryPolicy backgroundRetry,
        ReasoningPreferences foregroundPreferences,
        ReasoningPreferences backgroundPreferences) {
    public OwnerInteractionSettings {
        if (foregroundMaximumTokens < 1 || backgroundMaximumTokens < 1) {
            throw new IllegalArgumentException("generation limits must be positive");
        }
        Objects.requireNonNull(foregroundTimeout, "foregroundTimeout");
        Objects.requireNonNull(backgroundTimeout, "backgroundTimeout");
        if (foregroundTimeout.isNegative() || foregroundTimeout.isZero()
                || backgroundTimeout.isNegative() || backgroundTimeout.isZero()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        Objects.requireNonNull(backgroundRetry, "backgroundRetry");
        Objects.requireNonNull(foregroundPreferences, "foregroundPreferences");
        Objects.requireNonNull(backgroundPreferences, "backgroundPreferences");
    }

    public static OwnerInteractionSettings defaults() {
        return new OwnerInteractionSettings(256, 512, Duration.ofSeconds(90),
                Duration.ofMinutes(5), new ReasoningRetryPolicy(3, Duration.ofSeconds(5)),
                ReasoningPreferences.unconstrained(), ReasoningPreferences.unconstrained());
    }
}
