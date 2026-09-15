package io.github.didacll.madre.sdk.registration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Provider-owned owner-facing metadata for one Module installation setting. */
public record ModuleConfigurationField(
        String name,
        String displayName,
        String help,
        ModuleConfigurationFieldKind kind,
        boolean required,
        Optional<String> defaultValue,
        List<String> allowedValues,
        OptionalLong minimum,
        OptionalLong maximum) {
    public ModuleConfigurationField {
        name = text(name, "name");
        displayName = text(displayName, "displayName");
        help = text(help, "help");
        kind = Objects.requireNonNull(kind, "kind");
        defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
        minimum = Objects.requireNonNull(minimum, "minimum");
        maximum = Objects.requireNonNull(maximum, "maximum");
        if (kind == ModuleConfigurationFieldKind.CHOICE) {
            if (allowedValues.isEmpty()) {
                throw new IllegalArgumentException("CHOICE field requires allowed values: " + name);
            }
            for (String allowed : allowedValues) text(allowed, "allowed value");
            if (allowedValues.stream().distinct().count() != allowedValues.size()) {
                throw new IllegalArgumentException("CHOICE field has duplicate allowed values: " + name);
            }
        } else if (!allowedValues.isEmpty()) {
            throw new IllegalArgumentException("allowed values apply only to CHOICE fields: " + name);
        }
        if (kind != ModuleConfigurationFieldKind.INTEGER
                && (minimum.isPresent() || maximum.isPresent())) {
            throw new IllegalArgumentException("bounds apply only to INTEGER fields: " + name);
        }
        if (minimum.isPresent() && maximum.isPresent()
                && minimum.getAsLong() > maximum.getAsLong()) {
            throw new IllegalArgumentException("minimum exceeds maximum for field " + name);
        }
        defaultValue = defaultValue.map(value -> validate(kind, name, value, allowedValues,
                minimum, maximum));
    }

    public static ModuleConfigurationField text(String name, String displayName, String help,
            boolean required, String defaultValue) {
        return new ModuleConfigurationField(name, displayName, help,
                ModuleConfigurationFieldKind.TEXT, required, Optional.ofNullable(defaultValue),
                List.of(), OptionalLong.empty(), OptionalLong.empty());
    }

    public static ModuleConfigurationField integer(String name, String displayName, String help,
            boolean required, String defaultValue, OptionalLong minimum, OptionalLong maximum) {
        return new ModuleConfigurationField(name, displayName, help,
                ModuleConfigurationFieldKind.INTEGER, required, Optional.ofNullable(defaultValue),
                List.of(), minimum, maximum);
    }

    public static ModuleConfigurationField choice(String name, String displayName, String help,
            boolean required, String defaultValue, List<String> allowedValues) {
        return new ModuleConfigurationField(name, displayName, help,
                ModuleConfigurationFieldKind.CHOICE, required, Optional.ofNullable(defaultValue),
                allowedValues, OptionalLong.empty(), OptionalLong.empty());
    }

    /** Applies generic shape validation and returns the canonical generic representation. */
    public String canonicalize(String value) {
        return validate(kind, name, Objects.requireNonNull(value, "value"), allowedValues,
                minimum, maximum);
    }

    private static String validate(ModuleConfigurationFieldKind kind, String name, String value,
            List<String> allowedValues, OptionalLong minimum, OptionalLong maximum) {
        return switch (kind) {
            case TEXT -> value;
            case CHOICE -> {
                String normalized = value.strip();
                if (!allowedValues.contains(normalized)) {
                    throw new IllegalArgumentException(name + " must be one of "
                            + String.join(", ", allowedValues) + ": " + value);
                }
                yield normalized;
            }
            case INTEGER -> {
                final long parsed;
                try {
                    parsed = Long.parseLong(value.strip());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(name + " must be an integer: " + value,
                            exception);
                }
                if (minimum.isPresent() && parsed < minimum.getAsLong()) {
                    throw new IllegalArgumentException(name + " must be at least "
                            + minimum.getAsLong() + ": " + value);
                }
                if (maximum.isPresent() && parsed > maximum.getAsLong()) {
                    throw new IllegalArgumentException(name + " must be at most "
                            + maximum.getAsLong() + ": " + value);
                }
                yield Long.toString(parsed);
            }
        };
    }

    private static String text(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }
}
