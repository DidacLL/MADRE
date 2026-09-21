package io.github.didacll.madre.kernel.client;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record EngineDescriptor(
        String engineId,
        Set<String> supportedWorkTypes,
        Set<String> supportedCapabilities,
        Set<String> modelIds,
        Set<Effort> supportedEfforts,
        String placement,
        String availability,
        int requiredCpuSlots,
        long requiredRamBytes,
        Optional<String> requiredGpuId,
        long requiredGpuVramBytes,
        boolean warm) {
    public EngineDescriptor {
        Objects.requireNonNull(engineId, "engineId");
        supportedWorkTypes = Set.copyOf(supportedWorkTypes);
        supportedCapabilities = Set.copyOf(supportedCapabilities);
        modelIds = Set.copyOf(modelIds);
        supportedEfforts = Set.copyOf(supportedEfforts);
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(availability, "availability");
        requiredGpuId = Objects.requireNonNull(requiredGpuId, "requiredGpuId");
    }
}
