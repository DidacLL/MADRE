package io.github.didacll.madre.kernel.reasoning;

import java.util.Objects;

/** Identity of a measurable resource reserved for reasoning execution. */
public record ResourceId(String value) implements Comparable<ResourceId> {
    public ResourceId {
        if (Objects.requireNonNull(value, "value").isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.strip();
    }

    @Override public int compareTo(ResourceId other) { return value.compareTo(other.value); }
}
