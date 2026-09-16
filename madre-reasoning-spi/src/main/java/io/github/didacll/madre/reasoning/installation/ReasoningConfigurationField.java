package io.github.didacll.madre.reasoning.installation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Provider-owned presentation metadata for one owner-configurable instance field. */
public record ReasoningConfigurationField(
        String name,
        String displayName,
        String help,
        ReasoningConfigurationFieldKind kind,
        boolean required,
        Optional<String> defaultValue,
        List<String> allowedValues,
        OptionalLong minimum,
        OptionalLong maximum) {
    public ReasoningConfigurationField {
        name = text(name, "name");
        displayName = text(displayName, "displayName");
        help = text(help, "help");
        kind = Objects.requireNonNull(kind, "kind");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue")
                .map(String::strip).filter(value -> !value.isEmpty());
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        if (kind == ReasoningConfigurationFieldKind.CHOICE) {
            if (allowedValues.isEmpty()) {
                throw new IllegalArgumentException("CHOICE field requires allowed values: " + name);
            }
            for (String allowed : allowedValues) text(allowed, "allowed value");
        } else if (!allowedValues.isEmpty()) {
            throw new IllegalArgumentException("allowed values apply only to CHOICE fields: " + name);
        }
        if (kind != ReasoningConfigurationFieldKind.INTEGER
                && (minimum.isPresent() || maximum.isPresent())) {
            throw new IllegalArgumentException("bounds apply only to INTEGER fields: " + name);
        }
        if (minimum.isPresent() && maximum.isPresent()
                && minimum.getAsLong() > maximum.getAsLong()) {
            throw new IllegalArgumentException("minimum exceeds maximum for field " + name);
        }
    }

    public static ReasoningConfigurationField text(String name, String displayName, String help,
            boolean required, String defaultValue) {
        return new ReasoningConfigurationField(name, displayName, help,
                ReasoningConfigurationFieldKind.TEXT, required, Optional.ofNullable(defaultValue),
                List.of(), OptionalLong.empty(), OptionalLong.empty());
    }

    public static ReasoningConfigurationField integer(String name, String displayName, String help,
            boolean required, String defaultValue, OptionalLong minimum, OptionalLong maximum) {
        return new ReasoningConfigurationField(name, displayName, help,
                ReasoningConfigurationFieldKind.INTEGER, required, Optional.ofNullable(defaultValue),
                List.of(), minimum, maximum);
    }

    public static ReasoningConfigurationField choice(String name, String displayName, String help,
            boolean required, String defaultValue, List<String> allowedValues) {
        return new ReasoningConfigurationField(name, displayName, help,
                ReasoningConfigurationFieldKind.CHOICE, required, Optional.ofNullable(defaultValue),
                allowedValues, OptionalLong.empty(), OptionalLong.empty());
    }

    private static String text(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
