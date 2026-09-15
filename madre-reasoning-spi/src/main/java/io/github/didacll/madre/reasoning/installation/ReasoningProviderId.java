package io.github.didacll.madre.reasoning.installation;

import java.util.Objects;

/** Stable provider-owned identity for one configurable reasoning provider type. */
public record ReasoningProviderId(String value) implements Comparable<ReasoningProviderId> {
    public ReasoningProviderId {
        value = Objects.requireNonNull(value, "value").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("reasoning provider identity must not be blank");
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException(
                    "reasoning provider identity must contain only letters, digits, dots, dashes or underscores");
        }
    }

    @Override public int compareTo(ReasoningProviderId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override public String toString() { return value; }
}
