package io.github.didacll.madre.adapter.openai;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ResourceClaim;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Explicit installed OpenAI-compatible connector facts, excluding account mechanics. */
public record OpenAiCompatibleConfiguration(CapabilityId id, URI endpoint, String model,
        Privacy privacy, Integrity integrity, PhysicalLocation location,
        Duration expectedLatency, List<ResourceClaim> resources) {
    public OpenAiCompatibleConfiguration {
        Objects.requireNonNull(id); Objects.requireNonNull(endpoint);
        if (!endpoint.isAbsolute()) throw new IllegalArgumentException("endpoint must be absolute");
        if (!endpoint.getScheme().equalsIgnoreCase("http") && !endpoint.getScheme().equalsIgnoreCase("https")) throw new IllegalArgumentException("endpoint must use HTTP");
        if (Objects.requireNonNull(model).isBlank()) throw new IllegalArgumentException("model must not be blank");
        Objects.requireNonNull(privacy); Objects.requireNonNull(integrity); Objects.requireNonNull(location); Objects.requireNonNull(expectedLatency);
        if (expectedLatency.isNegative() || expectedLatency.isZero()) throw new IllegalArgumentException("expectedLatency must be positive");
        resources = List.copyOf(resources);
    }
}
