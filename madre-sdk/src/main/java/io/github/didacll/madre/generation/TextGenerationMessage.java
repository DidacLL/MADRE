package io.github.didacll.madre.generation;

import java.util.Objects;

/** One ordered portable text-generation message. */
public record TextGenerationMessage(Role role, String content) {
    public TextGenerationMessage {
        Objects.requireNonNull(role, "role");
        if (Objects.requireNonNull(content, "content").isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
    public enum Role { SYSTEM, USER, ASSISTANT }
}
