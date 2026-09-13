package io.github.didacll.madre.kernel.capability;

import java.util.Objects;

/** Installation-local identity of one physical connector. */
public record CapabilityId(String value) implements Comparable<CapabilityId> {
    public CapabilityId { if (Objects.requireNonNull(value, "value").isBlank()) throw new IllegalArgumentException("value must not be blank"); value = value.strip(); }
    @Override public int compareTo(CapabilityId other) { return value.compareTo(other.value); }
}
