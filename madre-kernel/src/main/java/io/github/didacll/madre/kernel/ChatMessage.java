package io.github.didacll.madre.kernel;

import java.util.Objects;

/** One ordered message in the concrete chat-completion inference contract. */
public record ChatMessage(Role role, String text) {
    public ChatMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
    }

    public enum Role { SYSTEM, USER, ASSISTANT }
}
