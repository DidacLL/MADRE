package io.github.didacll.madre.kernel.reasoning;

import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import java.io.Serial;
import java.util.Objects;

/** Categorized failure of one reasoning mechanism or its transport. */
public final class ReasoningException extends Exception {
    @Serial private static final long serialVersionUID = 1L;
    private final ReasoningFailureCategory category;

    public ReasoningException(ReasoningFailureCategory category, String message) {
        super(message);
        this.category = Objects.requireNonNull(category, "category");
    }

    public ReasoningException(ReasoningFailureCategory category, String message,
            Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category");
    }

    public ReasoningFailureCategory category() { return category; }
}
