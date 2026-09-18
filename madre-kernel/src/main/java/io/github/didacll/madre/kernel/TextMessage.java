package io.github.didacll.madre.kernel;

import java.util.Objects;

/** Provider-neutral physical text-generation message. */
public record TextMessage(Role role, String text) {
    public enum Role { SYSTEM, USER, ASSISTANT }

    public TextMessage {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(text, "text");
    }
}
