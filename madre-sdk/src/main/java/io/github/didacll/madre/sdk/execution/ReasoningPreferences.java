package io.github.didacll.madre.sdk.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Explicit technical constraints accompanying a typed semantic inference computation. */
public record ReasoningPreferences(Optional<Duration> maximumLatency,
        Optional<InferenceSelection> exactSelection) {
    public ReasoningPreferences {
        maximumLatency = Objects.requireNonNull(maximumLatency, "maximumLatency");
        exactSelection = Objects.requireNonNull(exactSelection, "exactSelection");
        maximumLatency.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("maximumLatency must be positive");
            }
        });
    }

    /** Uses the concrete computation requirements without silently widening exact selection. */
    public static ReasoningPreferences requirements() {
        return new ReasoningPreferences(Optional.empty(), Optional.empty());
    }
}
