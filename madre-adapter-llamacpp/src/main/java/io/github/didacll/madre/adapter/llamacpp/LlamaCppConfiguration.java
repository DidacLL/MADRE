package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ResourceClaim;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Explicit installed facts for the loopback llama-server reasoning adapter. */
public record LlamaCppConfiguration(ReasoningCapabilityId id, URI endpoint,
        String modelAlias, Privacy privacy, Duration expectedLatency,
        List<ResourceClaim> resources) {
    public LlamaCppConfiguration {
        Objects.requireNonNull(id);
        Objects.requireNonNull(endpoint);
        if (!endpoint.isAbsolute()) throw new IllegalArgumentException("endpoint must be absolute");
        if (!endpoint.getScheme().equalsIgnoreCase("http")
                && !endpoint.getScheme().equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("endpoint must use HTTP");
        }
        String host = endpoint.getHost();
        if (!("127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host))) {
            throw new IllegalArgumentException(
                    "local llama.cpp HTTP endpoint must use a loopback IP literal");
        }
        if (Objects.requireNonNull(modelAlias).isBlank()) {
            throw new IllegalArgumentException("modelAlias must not be blank");
        }
        Objects.requireNonNull(privacy);
        Objects.requireNonNull(expectedLatency);
        if (expectedLatency.isNegative() || expectedLatency.isZero()) {
            throw new IllegalArgumentException("expectedLatency must be positive");
        }
        resources = List.copyOf(resources);
    }
}
