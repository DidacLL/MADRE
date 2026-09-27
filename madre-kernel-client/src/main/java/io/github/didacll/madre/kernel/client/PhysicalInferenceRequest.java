package io.github.didacll.madre.kernel.client;

import java.time.OffsetDateTime;
import java.util.Objects;

public record PhysicalInferenceRequest(
        String preparedInput,
        InferenceEffort requestedEffort,
        WorkUrgency urgency,
        OffsetDateTime eligibleAt,
        OffsetDateTime deadline,
        ExecutionBoundary executionBoundary) {
    public PhysicalInferenceRequest {
        Objects.requireNonNull(preparedInput, "preparedInput");
        Objects.requireNonNull(requestedEffort, "requestedEffort");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(executionBoundary, "executionBoundary");
    }
}
