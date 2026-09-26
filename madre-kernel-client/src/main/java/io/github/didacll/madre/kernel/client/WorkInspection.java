package io.github.didacll.madre.kernel.client;

import java.util.Objects;
import java.util.Optional;

public record WorkInspection(
        WorkStatus status,
        int attemptCount,
        Optional<AttemptInspection> latestAttempt,
        Optional<String> technicalFailure,
        boolean payloadReleased) {
    public WorkInspection {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(latestAttempt, "latestAttempt");
        Objects.requireNonNull(technicalFailure, "technicalFailure");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must be >= 0");
    }
}
