package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ResourceClaim;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Explicit installed facts for one local llama-server Unix-domain socket. */
public record LlamaCppUnixSocketConfiguration(CapabilityId id, Path socketPath,
        String modelAlias, Privacy privacy, Integrity integrity, Duration expectedLatency,
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
        Objects.requireNonNull(integrity, "integrity");
        Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isNegative() || expectedLatency.isZero()) {
            throw new IllegalArgumentException("expectedLatency must be positive");
        }
        resources = List.copyOf(resources);
    }
}
