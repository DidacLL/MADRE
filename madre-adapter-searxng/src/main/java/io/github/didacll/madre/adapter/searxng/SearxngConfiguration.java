package io.github.didacll.madre.adapter.searxng;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ResourceClaim;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Installation-supplied physical facts for one SearXNG search endpoint. */
public record SearxngConfiguration(CapabilityId id, URI endpoint, Privacy privacy,
        Integrity integrity, Duration expectedLatency, List<ResourceClaim> resources) {
    public SearxngConfiguration {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(integrity, "integrity");
        Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isZero() || expectedLatency.isNegative()) {
            throw new IllegalArgumentException("expectedLatency must be positive");
        }
        resources = List.copyOf(resources);
    }
}
