package io.github.didacll.madre.kernel.client;

import java.util.Objects;

public record WorkId(String value) {
    public WorkId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
