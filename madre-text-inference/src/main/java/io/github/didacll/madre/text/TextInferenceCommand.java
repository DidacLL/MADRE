package io.github.didacll.madre.text;

import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import java.util.List;
import java.util.Objects;

/** Typed text-inference computation shared by the current reasoning mechanisms. */
public record TextInferenceCommand(String prompt, int maximumGeneratedTokens,
        List<String> stopSequences) implements ReasoningComputation<TextInferenceResult> {
    public TextInferenceCommand {
        if (Objects.requireNonNull(prompt, "prompt").isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (maximumGeneratedTokens < 1) {
            throw new IllegalArgumentException("maximumGeneratedTokens must be positive");
        }
        stopSequences = List.copyOf(stopSequences);
        if (stopSequences.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("stop sequences must not be empty");
        }
    }

    @Override public Class<TextInferenceResult> resultType() {
        return TextInferenceResult.class;
    }
}
