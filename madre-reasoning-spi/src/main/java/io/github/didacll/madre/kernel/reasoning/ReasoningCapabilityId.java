package io.github.didacll.madre.kernel.reasoning;

import java.util.Objects;

/** Installation-local identity of one reasoning mechanism. */
public record ReasoningCapabilityId(String value) implements Comparable<ReasoningCapabilityId> {
    public ReasoningCapabilityId {
        if (Objects.requireNonNull(value, "value").isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        value = value.strip();
    }

    @Override public int compareTo(ReasoningCapabilityId other) {
        return value.compareTo(other.value);
    }
}
