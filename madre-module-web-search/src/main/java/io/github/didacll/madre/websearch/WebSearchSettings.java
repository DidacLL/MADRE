package io.github.didacll.madre.websearch;

import io.github.didacll.madre.sdk.execution.PhysicalPreferences;
import java.time.Duration;
import java.util.Objects;

/** Module-owned physical execution choices for bounded search/review behavior. */
public record WebSearchSettings(int maximumResults, int reviewMaximumTokens,
        Duration searchTimeout, Duration reviewTimeout,
        PhysicalPreferences searchPreferences, PhysicalPreferences reviewPreferences) {
    public WebSearchSettings {
        if (maximumResults < 1 || maximumResults > 50) {
            throw new IllegalArgumentException("maximumResults must be between 1 and 50");
        }
        if (reviewMaximumTokens < 1) {
            throw new IllegalArgumentException("reviewMaximumTokens must be positive");
        }
        Objects.requireNonNull(searchTimeout, "searchTimeout");
        Objects.requireNonNull(reviewTimeout, "reviewTimeout");
        if (searchTimeout.isZero() || searchTimeout.isNegative()
                || reviewTimeout.isZero() || reviewTimeout.isNegative()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        Objects.requireNonNull(searchPreferences, "searchPreferences");
        Objects.requireNonNull(reviewPreferences, "reviewPreferences");
    }

    public static WebSearchSettings defaults() {
        return new WebSearchSettings(5, 700, Duration.ofSeconds(20), Duration.ofSeconds(60),
                PhysicalPreferences.unconstrained(), PhysicalPreferences.unconstrained());
    }
}
