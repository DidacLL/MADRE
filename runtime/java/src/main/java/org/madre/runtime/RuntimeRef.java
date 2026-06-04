package org.madre.runtime;

import java.util.Objects;
import java.util.UUID;

public record RuntimeRef<T>(UUID id, Class<T> type) {
    public RuntimeRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
    }

    public static <T> RuntimeRef<T> of(UUID id, Class<T> type) {
        return new RuntimeRef<>(id, type);
    }
}
