package io.github.didacll.madre.sdk.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Optional technical constraints translated by runtime for Kernel matching. */
public record ReasoningPreferences(Optional<ReasoningLocation> location,
        Optional<Duration> maximumLatency, Optional<InferenceSelection> exactSelection) {
    public ReasoningPreferences {
        location = Objects.requireNonNull(location, "location");
        maximumLatency = Objects.requireNonNull(maximumLatency, "maximumLatency");
        exactSelection = Objects.requireNonNull(exactSelection, "exactSelection");
        maximumLatency.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("maximumLatency must be positive");
            }
        });
    }

    public static ReasoningPreferences unconstrained() {
        return new ReasoningPreferences(Optional.empty(), Optional.empty(), Optional.empty());
    }
}
