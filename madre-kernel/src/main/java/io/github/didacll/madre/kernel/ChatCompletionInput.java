package io.github.didacll.madre.kernel;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Transient input for the concrete chat-completion contract; never persisted by Kernel. */
public record ChatCompletionInput(
        List<ChatMessage> messages,
        OptionalInt maximumOutputTokens,
        List<String> stopSequences) {
    public ChatCompletionInput {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) throw new IllegalArgumentException("messages must not be empty");
        Objects.requireNonNull(maximumOutputTokens, "maximumOutputTokens");
        if (maximumOutputTokens.isPresent() && maximumOutputTokens.getAsInt() <= 0) {
            throw new IllegalArgumentException("maximumOutputTokens must be positive");
        }
        stopSequences = List.copyOf(Objects.requireNonNull(stopSequences, "stopSequences"));
        if (stopSequences.stream().anyMatch(value -> value == null || value.isEmpty())) {
            throw new IllegalArgumentException("stop sequences must not be null or empty");
        }
    }
}
