package io.github.didacll.madre.kernel.capability;

import java.util.Objects;

/** Positive quantity reserved for one physical execution. */
public record ResourceClaim(ResourceId resource, long units) {
    public ResourceClaim { Objects.requireNonNull(resource, "resource"); if (units < 1) throw new IllegalArgumentException("units must be positive"); }
}
