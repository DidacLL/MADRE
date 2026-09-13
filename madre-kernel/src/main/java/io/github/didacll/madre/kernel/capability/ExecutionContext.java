package io.github.didacll.madre.kernel.capability;

import java.time.Instant;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;

/** Timeout, cancellation and attempt mechanics visible to a physical connector. */
public record ExecutionContext(Instant deadline, BooleanSupplier cancelled, int attempt) {
    public ExecutionContext {
        Objects.requireNonNull(deadline, "deadline"); Objects.requireNonNull(cancelled, "cancelled");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }
    public void requireActive() throws CapabilityException {
        if (cancelled.getAsBoolean()) throw new CapabilityException(PhysicalFailureCategory.CANCELLED, "execution cancelled");
        if (!Instant.now().isBefore(deadline)) throw new CapabilityException(PhysicalFailureCategory.TIMEOUT, "execution timed out");
    }
}
