package io.github.didacll.madre.generation;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.util.List;
import java.util.Objects;

/** Typed semantic request for text generation. */
public record TextGenerationCommand(List<TextGenerationMessage> messages,
        int maximumGeneratedTokens, List<String> stopSequences, int minimumContextTokens)
        implements ReasoningComputation<TextGenerationResult> {
    public TextGenerationCommand {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) throw new IllegalArgumentException("messages must not be empty");
        if (maximumGeneratedTokens < 1) {
            throw new IllegalArgumentException("maximumGeneratedTokens must be positive");
        }
        stopSequences = List.copyOf(Objects.requireNonNull(stopSequences, "stopSequences"));
        if (stopSequences.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("stop sequences must not be empty");
        }
        if (minimumContextTokens < 0) {
            throw new IllegalArgumentException("minimumContextTokens must not be negative");
        }
    }
    public static TextGenerationCommand prompt(String prompt, int maximumGeneratedTokens) {
        return new TextGenerationCommand(List.of(new TextGenerationMessage(
                TextGenerationMessage.Role.USER, prompt)), maximumGeneratedTokens, List.of(), 0);
    }
    @Override public Class<TextGenerationResult> resultType() { return TextGenerationResult.class; }
}
