package io.github.didacll.madre.core;

import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import io.github.didacll.madre.sdk.execution.PhysicalRetryPolicy;
import java.time.Duration;
import java.util.Objects;

/** Ordinary physical controls chosen by the shipped Module's bounded behavior. */
public record CoreModuleSettings(int foregroundMaximumTokens, int backgroundMaximumTokens,
        Duration foregroundTimeout, Duration backgroundTimeout,
        PhysicalRetryPolicy backgroundRetry, PhysicalPreferences foregroundPreferences,
        PhysicalPreferences backgroundPreferences) {
    public CoreModuleSettings {
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

    public static CoreModuleSettings defaults() {
        return new CoreModuleSettings(256, 512, Duration.ofSeconds(90), Duration.ofMinutes(5),
                new PhysicalRetryPolicy(3, Duration.ofSeconds(5)),
                PhysicalPreferences.unconstrained(), PhysicalPreferences.unconstrained());
    }
}
