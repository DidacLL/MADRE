package io.github.didacll.madre.sdk.execution;

import java.util.Objects;
import java.util.Optional;

/** Exact technical selection requested explicitly by the Owner. */
public record InferenceSelection(Optional<String> engineId, Optional<String> provider,
        Optional<String> model) {
    public InferenceSelection {
        engineId = normalized(engineId, "engineId");
        provider = normalized(provider, "provider");
        model = normalized(model, "model");
        if (engineId.isEmpty() && provider.isEmpty() && model.isEmpty()) {
            throw new IllegalArgumentException("At least one exact selection value is required");
        }
    }

    public static InferenceSelection engine(String engineId) {
        return new InferenceSelection(Optional.of(engineId), Optional.empty(), Optional.empty());
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name).map(item -> {
            String result = item.strip();
            if (result.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
            return result;
        });
    }
}
