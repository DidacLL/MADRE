package io.github.didacll.madre.kernel.client;

import java.util.Objects;

public record InvocationId(String value) {
    public InvocationId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("invocation id must be nonblank single-line text");
        }
    }
}
