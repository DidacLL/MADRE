package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Installation-scoped identity of an inference engine. */
public record EngineId(String value) implements Comparable<EngineId> {
    public EngineId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Engine id must not be blank");
    }

    @Override public int compareTo(EngineId other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
