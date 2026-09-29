package io.github.didacll.madre.kernel.client;

public record CapabilityExecutionPath(
        ConfiguredFact<ExecutionLocation> location,
        ConfiguredFact<String> destination,
        ConfiguredFact<String> route,
        ConfiguredFact<String> dataRetention) {
}
