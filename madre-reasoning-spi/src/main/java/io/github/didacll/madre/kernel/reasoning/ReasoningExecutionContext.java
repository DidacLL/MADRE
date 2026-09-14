package io.github.didacll.madre.kernel.reasoning;

import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Timeout, cancellation and attempt mechanics visible to a reasoning mechanism. */
public record ReasoningExecutionContext(Instant deadline, BooleanSupplier cancelled,
        int attempt) {
    public ReasoningExecutionContext {
        Objects.requireNonNull(deadline, "deadline");
        Objects.requireNonNull(cancelled, "cancelled");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }

    public void requireActive() throws ReasoningException {
        if (cancelled.getAsBoolean()) {
            throw new ReasoningException(ReasoningFailureCategory.CANCELLED,
                    "reasoning execution cancelled");
        }
        if (!Instant.now().isBefore(deadline)) {
            throw new ReasoningException(ReasoningFailureCategory.TIMEOUT,
                    "reasoning execution timed out");
        }
    }
}
