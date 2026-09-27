package io.github.didacll.madre.kernel.client;

public record InferenceCapability(
        String capabilityId,
        String bindingId,
        String bindingVersion,
        ConfiguredFact<ExecutionBoundary> executionBoundary,
        ConfiguredFact<InferenceEffort> supportedEffort,
        int ownerPreference) {
}
