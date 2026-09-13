package io.github.didacll.madre.kernel.capability;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Installed facts about one physical connector. */
public record CapabilityManifest<C, R>(CapabilityId id, PhysicalContract<C, R> contract,
        Privacy receivingPrivacy, Optional<Integrity> physicalIntegrity, PhysicalLocation location,
        Duration expectedLatency, List<ResourceClaim> resources) {
    public CapabilityManifest {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(receivingPrivacy, "receivingPrivacy"); physicalIntegrity = Objects.requireNonNull(physicalIntegrity, "physicalIntegrity");
        Objects.requireNonNull(location, "location"); Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isNegative() || expectedLatency.isZero()) throw new IllegalArgumentException("expectedLatency must be positive");
        resources = List.copyOf(resources);
    }
}
