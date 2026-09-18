package io.github.didacll.madre.kernel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Transient provider-neutral input; the Kernel never writes it to persistence or logs. */
public record TextInferenceInput(
        List<TextMessage> messages,
        Optional<Double> temperature,
        OptionalInt maximumOutputTokens) {
    public TextInferenceInput {
        messages = List.copyOf(messages);
        if (messages.isEmpty()) throw new IllegalArgumentException("messages must not be empty");
        Objects.requireNonNull(temperature, "temperature");
        temperature.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("temperature must be finite and non-negative");
        });
        Objects.requireNonNull(maximumOutputTokens, "maximumOutputTokens");
        if (maximumOutputTokens.isPresent() && maximumOutputTokens.getAsInt() <= 0) {
            throw new IllegalArgumentException("maximumOutputTokens must be positive");
        }
    }
}
