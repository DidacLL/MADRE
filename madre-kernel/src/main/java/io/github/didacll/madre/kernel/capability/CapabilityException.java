package io.github.didacll.madre.kernel.capability;

import java.io.Serial;
import java.util.Objects;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;

/** A categorized failure of the physical mechanism or connector. */
public final class CapabilityException extends Exception {
    @Serial private static final long serialVersionUID = 1L;
    private final PhysicalFailureCategory category;

    public CapabilityException(PhysicalFailureCategory category, String message) {
        super(message); this.category = Objects.requireNonNull(category, "category");
    }
    public CapabilityException(PhysicalFailureCategory category, String message, Throwable cause) {
        super(message, cause); this.category = Objects.requireNonNull(category, "category");
    }
    public PhysicalFailureCategory category() { return category; }
}
