package io.github.didacll.madre.sdk.module;

import io.github.didacll.madre.algebra.Sensitivity;
import java.util.Objects;

/** One typed conversational value handled by a conversational Agent. */
public record ConversationMessage(Role role, String text, Sensitivity sensitivity) {
    public ConversationMessage {
        Objects.requireNonNull(role, "role");
        if (Objects.requireNonNull(text, "text").isBlank()) {
            throw new IllegalArgumentException("conversation text must not be blank");
        }
        text = text.strip();
        Objects.requireNonNull(sensitivity, "sensitivity");
        if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("SYSTEM_RESERVED is not conversational Material");
        }
    }

    public enum Role { HUMAN, AGENT, SYSTEM }
}
