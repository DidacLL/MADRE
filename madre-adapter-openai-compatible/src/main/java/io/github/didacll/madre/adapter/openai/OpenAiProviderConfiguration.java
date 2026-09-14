package io.github.didacll.madre.adapter.openai;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.kernel.reasoning.ResourceClaim;
import io.github.didacll.madre.kernel.reasoning.ResourceId;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Adapter-owned parsing for OpenAI-compatible reasoning-mechanism instances. */
final class OpenAiProviderConfiguration {
    private OpenAiProviderConfiguration() { }

    static List<String> instances(ReasoningProviderConfiguration configuration, String prefix) {
        String raw = configuration.value(prefix + ".instances").orElse(null);
        if (raw == null) return List.of();
        if (raw.isBlank()) {
            throw new IllegalArgumentException(prefix + ".instances must not be blank");
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String field : raw.split(",", -1)) {
            String name = field.strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException(prefix + ".instances contains a blank instance");
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException(prefix + ".instances contains duplicate " + name);
            }
        }
        return List.copyOf(names);
    }

    static boolean enabled(ReasoningProviderConfiguration configuration, String prefix) {
        String key = prefix + ".enabled";
        String value = configuration.value(key).orElse("false").strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }

    static String required(ReasoningProviderConfiguration configuration, String key) {
        String value = configuration.value(key).orElseThrow(
                () -> new IllegalArgumentException("missing property " + key));
        if (value.isBlank()) throw new IllegalArgumentException("missing property " + key);
        return value.strip();
    }

    static int preference(ReasoningProviderConfiguration configuration, String key) {
        int value;
        try {
            value = Integer.parseInt(required(configuration, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
        if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
        return value;
    }

    static Duration duration(ReasoningProviderConfiguration configuration, String key) {
        long millis;
        try {
            millis = Long.parseLong(required(configuration, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer number of milliseconds",
                    exception);
        }
        if (millis < 1) throw new IllegalArgumentException(key + " must be positive");
        return Duration.ofMillis(millis);
    }

    static Privacy privacy(ReasoningProviderConfiguration configuration, String key) {
        return switch (required(configuration, key).toUpperCase(Locale.ROOT)) {
            case "P1", "PUBLIC" -> Privacy.PUBLIC;
            case "P2", "UNKNOWN" -> Privacy.UNKNOWN;
            case "P3", "LOCAL" -> Privacy.LOCAL;
            case "P4", "MODULE" -> Privacy.MODULE;
            case "P5", "SECRET" -> Privacy.SECRET;
            default -> throw new IllegalArgumentException(
                    key + " must be PUBLIC/P1, UNKNOWN/P2, LOCAL/P3, MODULE/P4 or SECRET/P5");
        };
    }

    static ReasoningLocation location(ReasoningProviderConfiguration configuration, String key) {
        try {
            return ReasoningLocation.valueOf(required(configuration, key).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " is not a valid ReasoningLocation", exception);
        }
    }

    static ReasoningCapabilityId capabilityId(ReasoningProviderConfiguration configuration,
            String key) {
        return new ReasoningCapabilityId(required(configuration, key));
    }

    static List<ResourceClaim> resources(ReasoningProviderConfiguration configuration,
            String prefix) {
        String resourcePrefix = prefix + ".resource.";
        List<ResourceClaim> claims = new ArrayList<>();
        configuration.keys().stream().filter(key -> key.startsWith(resourcePrefix)).sorted()
                .forEach(key -> {
                    String name = key.substring(resourcePrefix.length());
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("blank resource name in " + key);
                    }
                    long units;
                    try {
                        units = Long.parseLong(required(configuration, key));
                    } catch (NumberFormatException exception) {
                        throw new IllegalArgumentException(key + " must be an integer", exception);
                    }
                    claims.add(new ResourceClaim(new ResourceId(name), units));
                });
        return List.copyOf(claims);
    }
}
