package io.github.didacll.madre.kernel.client;

import java.util.Objects;
import java.util.Set;

public record EngineDescriptor(
        String engineId,
        Set<String> supportedWorkTypes,
        Set<String> supportedCapabilities,
        String placement,
        String availability,
        boolean warm) {
    public EngineDescriptor {
        Objects.requireNonNull(engineId, "engineId");
        supportedWorkTypes = Set.copyOf(supportedWorkTypes);
        supportedCapabilities = Set.copyOf(supportedCapabilities);
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(availability, "availability");
    }
}
