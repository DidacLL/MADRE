package io.github.didacll.madre.sdk.execution;

import java.io.Serial;
import java.util.Objects;

/** Categorized failure returned through the Module-facing reasoning port. */
public final class ReasoningExecutionException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
    private final ReasoningFailureCategory category;

    public ReasoningExecutionException(ReasoningFailureCategory category, String message,
            Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category");
    }

    public ReasoningFailureCategory category() { return category; }
}
