package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ResourceClaim;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Explicit installed facts for one local llama-server Unix-domain socket. */
public record LlamaCppUnixSocketConfiguration(ReasoningCapabilityId id, Path socketPath,
        String modelAlias, Privacy privacy, Duration expectedLatency,
        List<ResourceClaim> resources) {
    public LlamaCppUnixSocketConfiguration {
        Objects.requireNonNull(id, "id");
        socketPath = Objects.requireNonNull(socketPath, "socketPath").normalize();
        if (!socketPath.isAbsolute()) {
            throw new IllegalArgumentException("socketPath must be absolute");
        }
        if (Objects.requireNonNull(modelAlias, "modelAlias").isBlank()) {
            throw new IllegalArgumentException("modelAlias must not be blank");
        }
        Objects.requireNonNull(privacy, "privacy");
        Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isNegative() || expectedLatency.isZero()) {
            throw new IllegalArgumentException("expectedLatency must be positive");
        }
        resources = List.copyOf(resources);
    }
}
