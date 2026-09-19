package io.github.didacll.madre.kernel;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Stable technical facts advertised by an executable engine object. */
public record EngineCharacteristics(
        String provider,
        String model,
        URI endpoint,
        Duration expectedLatency,
        Optional<ChatCompletionCapability> chatCompletion) {

    public EngineCharacteristics {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isNegative()) throw new IllegalArgumentException("expectedLatency must not be negative");
        chatCompletion = Objects.requireNonNull(chatCompletion, "chatCompletion");
    }

    public boolean satisfies(TechnicalCapabilityRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return switch (requirement) {
            case TechnicalCapabilityRequirement.None ignored -> true;
            case TechnicalCapabilityRequirement.ChatCompletion requested -> chatCompletion
                    .filter(actual -> actual.maximumContextTokens() >= requested.minimumContextTokens())
                    .isPresent();
        };
    }

    public record ChatCompletionCapability(int maximumContextTokens) {
        public ChatCompletionCapability {
            if (maximumContextTokens <= 0) throw new IllegalArgumentException("maximumContextTokens must be positive");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
