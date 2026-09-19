package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Sanitized physical failure evidence. It must never contain inference input or output. */
public record TechnicalFailure(Category category, String message, boolean retryable) {
    public enum Category { NO_ENGINE, RESOURCE_UNAVAILABLE, ENGINE_FAILURE, TIMEOUT, DELIVERY_FAILURE, CANCELLED }

    public TechnicalFailure {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) throw new IllegalArgumentException("message must not be blank");
    }
}
