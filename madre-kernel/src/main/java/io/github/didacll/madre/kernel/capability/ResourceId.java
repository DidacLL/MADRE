package io.github.didacll.madre.kernel.capability;

import java.util.Objects;

/** Identity of a measurable physical resource such as CPU, GPU memory or a model slot. */
public record ResourceId(String value) implements Comparable<ResourceId> {
    public ResourceId { if (Objects.requireNonNull(value, "value").isBlank()) throw new IllegalArgumentException("value must not be blank"); value = value.strip(); }
    @Override public int compareTo(ResourceId other) { return value.compareTo(other.value); }
}
