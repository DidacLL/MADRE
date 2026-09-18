package io.github.didacll.madre.kernel;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Stable technical facts advertised by an executable engine object. */
public record EngineCharacteristics(
        EngineLocation location,
        String provider,
        String model,
        Duration expectedLatency,
        int preference,
        Optional<TextCapability> text,
        Optional<EmbeddingCapability> embedding,
        Optional<VisionCapability> vision) {

    public EngineCharacteristics {
        Objects.requireNonNull(location, "location");
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        Objects.requireNonNull(expectedLatency, "expectedLatency");
        if (expectedLatency.isNegative()) throw new IllegalArgumentException("expectedLatency must not be negative");
        text = Objects.requireNonNull(text, "text");
        embedding = Objects.requireNonNull(embedding, "embedding");
        vision = Objects.requireNonNull(vision, "vision");
    }

    public boolean satisfies(TechnicalCapabilityRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return switch (requirement) {
            case TechnicalCapabilityRequirement.None ignored -> true;
            case TechnicalCapabilityRequirement.Text requested -> text
                    .filter(actual -> !requested.demandingReasoning() || actual.demandingReasoning())
                    .filter(actual -> actual.maximumContextTokens() >= requested.minimumContextTokens())
                    .isPresent();
            case TechnicalCapabilityRequirement.Embedding requested -> embedding
                    .filter(actual -> requested.dimensions() == 0 || actual.supports(requested.dimensions()))
                    .isPresent();
            case TechnicalCapabilityRequirement.Vision requested -> vision
                    .filter(actual -> !requested.imageInput() || actual.imageInput())
                    .filter(actual -> !requested.videoInput() || actual.videoInput())
                    .isPresent();
        };
    }

    public record TextCapability(boolean demandingReasoning, int maximumContextTokens) {
        public TextCapability {
            if (maximumContextTokens <= 0) throw new IllegalArgumentException("maximumContextTokens must be positive");
        }
    }

    public record EmbeddingCapability(int fixedDimensions) {
        public EmbeddingCapability {
            if (fixedDimensions < 0) throw new IllegalArgumentException("fixedDimensions must not be negative");
        }
        public boolean supports(int dimensions) { return fixedDimensions == 0 || fixedDimensions == dimensions; }
    }

    public record VisionCapability(boolean imageInput, boolean videoInput) {
        public VisionCapability {
            if (!imageInput && !videoInput) throw new IllegalArgumentException("At least one vision input must be supported");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
