package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Units of a concrete machine resource required for an execution. */
public record ResourceClaim(ResourceId resource, long units) {
    public ResourceClaim {
        Objects.requireNonNull(resource, "resource");
        if (units <= 0) throw new IllegalArgumentException("units must be positive");
    }
}
