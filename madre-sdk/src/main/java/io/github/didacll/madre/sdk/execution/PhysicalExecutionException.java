package io.github.didacll.madre.sdk.execution;

import java.io.Serial;
import java.util.Objects;

/** Categorized physical failure returned through the Module-facing execution port. */
public final class PhysicalExecutionException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
    private final PhysicalFailureCategory category;
    public PhysicalExecutionException(PhysicalFailureCategory category, String message, Throwable cause) {
        super(message, cause); this.category = Objects.requireNonNull(category, "category");
    }
    public PhysicalFailureCategory category() { return category; }
}
