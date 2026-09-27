package io.github.didacll.madre.kernel.client;

public record CapabilitySnapshot(
        InferenceCapability capability,
        CapabilityState state,
        Double successfulLatencyMs,
        int successfulObservationCount,
        int failureObservationCount) {
}
