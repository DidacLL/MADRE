package io.github.didacll.madre.sdk.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Typed, optional physical constraints used during Capability selection. */
public record PhysicalPreferences(Optional<PhysicalLocation> location, Optional<Duration> maximumLatency) {
    public PhysicalPreferences {
        location = Objects.requireNonNull(location, "location");
        maximumLatency = Objects.requireNonNull(maximumLatency, "maximumLatency");
        maximumLatency.ifPresent(value -> {
            if (value.isNegative() || value.isZero()) throw new IllegalArgumentException("maximumLatency must be positive");
        });
    }

    public static PhysicalPreferences unconstrained() {
        return new PhysicalPreferences(Optional.empty(), Optional.empty());
    }
}
