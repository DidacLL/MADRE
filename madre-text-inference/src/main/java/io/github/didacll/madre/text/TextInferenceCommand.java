package io.github.didacll.madre.text;

import java.util.List;
import java.util.Objects;

/** Physical text-generation input shared by the real text connectors. */
public record TextInferenceCommand(String prompt, int maximumGeneratedTokens, List<String> stopSequences) {
    public TextInferenceCommand {
        if (Objects.requireNonNull(prompt, "prompt").isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        if (maximumGeneratedTokens < 1) throw new IllegalArgumentException("maximumGeneratedTokens must be positive");
        stopSequences = List.copyOf(stopSequences);
        if (stopSequences.stream().anyMatch(String::isEmpty)) throw new IllegalArgumentException("stop sequences must not be empty");
    }
}
