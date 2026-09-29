package io.github.didacll.madre.kernel.client;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record PhysicalInferenceRequest(
        String preparedInput,
        InferenceEffort requestedEffort,
        WorkUrgency urgency,
        OffsetDateTime eligibleAt,
        OffsetDateTime deadline,
        ExecutionBoundary executionBoundary,
        List<String> eligibleCapabilityIds) {
    public PhysicalInferenceRequest {
        Objects.requireNonNull(preparedInput, "preparedInput");
        Objects.requireNonNull(requestedEffort, "requestedEffort");
        Objects.requireNonNull(urgency, "urgency");
        Objects.requireNonNull(executionBoundary, "executionBoundary");
        if (eligibleCapabilityIds != null) {
            if (eligibleCapabilityIds.isEmpty()) {
                throw new IllegalArgumentException("eligibleCapabilityIds must not be empty when present");
            }
            if (eligibleCapabilityIds.stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new IllegalArgumentException("eligibleCapabilityIds must not contain blank ids");
            }
            if (new HashSet<>(eligibleCapabilityIds).size() != eligibleCapabilityIds.size()) {
                throw new IllegalArgumentException("eligibleCapabilityIds must not contain duplicates");
            }
            eligibleCapabilityIds = List.copyOf(eligibleCapabilityIds);
        }
    }

    public PhysicalInferenceRequest(
            String preparedInput,
            InferenceEffort requestedEffort,
            WorkUrgency urgency,
            OffsetDateTime eligibleAt,
            OffsetDateTime deadline,
            ExecutionBoundary executionBoundary) {
        this(preparedInput, requestedEffort, urgency, eligibleAt, deadline, executionBoundary, null);
    }
}
