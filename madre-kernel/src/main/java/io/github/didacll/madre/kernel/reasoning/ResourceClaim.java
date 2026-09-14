package io.github.didacll.madre.kernel.reasoning;

import java.util.Objects;

/** One quantified resource reservation required by a reasoning mechanism. */
public record ResourceClaim(ResourceId resource, long units) {
    public ResourceClaim {
        Objects.requireNonNull(resource, "resource");
        if (units < 1) throw new IllegalArgumentException("units must be positive");
    }
}
