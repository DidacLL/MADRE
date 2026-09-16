package io.github.didacll.madre.generation;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.util.List;
import java.util.Objects;

/** Richer portable text-generation computation kept distinct from text-inference v1. */
public record TextGenerationCommand(List<TextGenerationMessage> messages,
        int maximumGeneratedTokens, List<String> stopSequences)
        implements ReasoningComputation<TextGenerationResult> {
    public TextGenerationCommand {
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (maximumGeneratedTokens < 1) {
            throw new IllegalArgumentException("maximumGeneratedTokens must be positive");
        }
        stopSequences = List.copyOf(Objects.requireNonNull(stopSequences, "stopSequences"));
        if (stopSequences.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("stop sequences must not be empty");
        }
    }

    @Override public Class<TextGenerationResult> resultType() {
        return TextGenerationResult.class;
    }
}
