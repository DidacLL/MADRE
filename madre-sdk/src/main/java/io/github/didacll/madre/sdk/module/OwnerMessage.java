package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Sensitivity;
import java.util.Objects;

/** One semantic owner-visible message approved by the Agent responsible for owner interaction. */
public record OwnerMessage(String text, Sensitivity sensitivity) {
    public OwnerMessage {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(sensitivity, "sensitivity");
        text = text.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("owner-visible text must not be blank");
        if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED is not owner-visible Material");
        }
    }
}
