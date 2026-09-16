package io.github.didacll.madre.sdk.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Optional constraints for selecting one compatible reasoning mechanism. */
public record ReasoningPreferences(Optional<ReasoningLocation> location,
        Optional<Duration> maximumLatency) {
    public ReasoningPreferences {
        location = Objects.requireNonNull(location, "location");
        maximumLatency = Objects.requireNonNull(maximumLatency, "maximumLatency");
        maximumLatency.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("maximumLatency must be positive");
            }
        });
    }

    public static ReasoningPreferences unconstrained() {
        return new ReasoningPreferences(Optional.empty(), Optional.empty());
    }
}
