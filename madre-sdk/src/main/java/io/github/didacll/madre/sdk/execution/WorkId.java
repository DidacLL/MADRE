package io.github.didacll.madre.sdk.execution;

import java.util.Objects;
import java.util.UUID;

/** Kernel physical-work identity. */
public record WorkId(String value) {
    public WorkId { if (Objects.requireNonNull(value, "value").isBlank()) throw new IllegalArgumentException("value must not be blank"); value = value.strip(); }
    public static WorkId create() { return new WorkId(UUID.randomUUID().toString()); }
}
