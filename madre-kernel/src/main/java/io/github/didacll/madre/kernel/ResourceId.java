package io.github.didacll.madre.kernel;

import java.util.Objects;

public record ResourceId(String value) implements Comparable<ResourceId> {
    public ResourceId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Resource id must not be blank");
    }
    @Override public int compareTo(ResourceId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
