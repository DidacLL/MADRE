package io.github.didacll.madre.kernel;

import java.util.Objects;
import java.util.UUID;

/** Opaque identity of one physical inference execution. */
public record WorkId(UUID value) {
    public WorkId {
        Objects.requireNonNull(value, "value");
    }

    public static WorkId create() {
        return new WorkId(UUID.randomUUID());
    }

    public static WorkId parse(String value) {
        return new WorkId(UUID.fromString(value));
    }

    @Override public String toString() { return value.toString(); }
}
