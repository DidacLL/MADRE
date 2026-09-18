package io.github.didacll.madre.kernel;

/** Family-specific physical requirements; these carry no semantic or Security Algebra values. */
public sealed interface TechnicalCapabilityRequirement permits
        TechnicalCapabilityRequirement.None,
        TechnicalCapabilityRequirement.Text,
        TechnicalCapabilityRequirement.Embedding,
        TechnicalCapabilityRequirement.Vision {

    record None() implements TechnicalCapabilityRequirement { }

    record Text(boolean demandingReasoning, int minimumContextTokens) implements TechnicalCapabilityRequirement {
        public Text {
            if (minimumContextTokens < 0) throw new IllegalArgumentException("minimumContextTokens must not be negative");
        }
    }

    /** A value of zero accepts the dimensions chosen by the engine. */
    record Embedding(int dimensions) implements TechnicalCapabilityRequirement {
        public Embedding {
            if (dimensions < 0) throw new IllegalArgumentException("dimensions must not be negative");
        }
    }

    record Vision(boolean imageInput, boolean videoInput) implements TechnicalCapabilityRequirement {
        public Vision {
            if (!imageInput && !videoInput) throw new IllegalArgumentException("At least one vision input is required");
        }
    }
}
