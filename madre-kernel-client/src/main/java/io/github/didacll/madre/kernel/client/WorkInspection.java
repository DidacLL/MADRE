package io.github.didacll.madre.kernel.client;

import java.util.Objects;
import java.util.Optional;

public record WorkInspection(
        WorkStatus status,
        int attemptCount,
        Optional<InvocationId> selectedInvocation,
        Optional<String> selectedTargetIdentity,
        Optional<String> technicalFailure) {
    public WorkInspection {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(selectedInvocation, "selectedInvocation");
        Objects.requireNonNull(selectedTargetIdentity, "selectedTargetIdentity");
        Objects.requireNonNull(technicalFailure, "technicalFailure");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount must be >= 0");
        }
    }
}
