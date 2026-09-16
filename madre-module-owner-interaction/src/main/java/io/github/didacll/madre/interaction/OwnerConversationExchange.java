package io.github.didacll.madre.interaction;

import io.github.didacll.madre.algebra.Sensitivity;
import java.util.Objects;

/** One retained owner/assistant exchange in Module-owned conversation state. */
record OwnerConversationExchange(String ownerPrompt, Sensitivity ownerSensitivity,
        String assistantAnswer, Sensitivity assistantSensitivity) {
    OwnerConversationExchange {
        if (Objects.requireNonNull(ownerPrompt, "ownerPrompt").isBlank()) {
            throw new IllegalArgumentException("owner prompt must not be blank");
        }
        if (Objects.requireNonNull(assistantAnswer, "assistantAnswer").isBlank()) {
            throw new IllegalArgumentException("assistant answer must not be blank");
        }
        requireOrdinary(Objects.requireNonNull(ownerSensitivity, "ownerSensitivity"));
        requireOrdinary(Objects.requireNonNull(assistantSensitivity, "assistantSensitivity"));
    }

    private static void requireOrdinary(Sensitivity sensitivity) {
        if (sensitivity == Sensitivity.SYSTEM_RESERVED) {
            throw new IllegalArgumentException("conversation state cannot use SYSTEM_RESERVED");
        }
    }
}
