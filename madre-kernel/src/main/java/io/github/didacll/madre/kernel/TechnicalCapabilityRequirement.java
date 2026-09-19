package io.github.didacll.madre.kernel;

/** Family-specific physical requirements; these carry no semantic or Security Algebra values. */
public sealed interface TechnicalCapabilityRequirement permits
        TechnicalCapabilityRequirement.None,
        TechnicalCapabilityRequirement.ChatCompletion {

    record None() implements TechnicalCapabilityRequirement { }

    record ChatCompletion(int minimumContextTokens) implements TechnicalCapabilityRequirement {
        public ChatCompletion {
            if (minimumContextTokens < 0) throw new IllegalArgumentException("minimumContextTokens must not be negative");
        }
    }
}
