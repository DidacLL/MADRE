package io.github.didacll.madre.kernel.client;

public record InferenceCapability(
        String capabilityId,
        String bindingId,
        String bindingVersion,
        CapabilityExecutionPath executionPath,
        ConfiguredFact<InferenceEffort> supportedEffort,
        int ownerPreference) {
}
