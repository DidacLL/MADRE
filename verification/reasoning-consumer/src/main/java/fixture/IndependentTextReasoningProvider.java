package fixture;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.kernel.reasoning.ReasoningAvailability;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapabilityId;
import io.github.didacll.madre.reasoning.installation.ReasoningConfigurationField;
import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import io.github.didacll.madre.sdk.execution.ReasoningLocation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

/** Independent provider proving public metadata/configuration and execution outside MADRE's build graph. */
public final class IndependentTextReasoningProvider
        implements ReasoningMechanismProvider, ReasoningProviderConfigurator {
    private static final String PREFIX = "reasoning.independent-text";
    private static final ReasoningProviderDescriptor DESCRIPTOR = new ReasoningProviderDescriptor(
            new ReasoningProviderId("independent-text"), "Independent deterministic text",
            "Deterministic third-party fixture used to prove the public reasoning-provider contract.",
            List.of(
                    ReasoningConfigurationField.text("capability-id", "Capability identity",
                            "Stable mechanism identity.", true, null),
                    ReasoningConfigurationField.choice("privacy", "Privacy",
                            "Explicit receiving Privacy.", true, null,
                            List.of("PUBLIC", "UNKNOWN", "LOCAL", "MODULE", "SECRET")),
                    ReasoningConfigurationField.choice("location", "Reasoning location",
                            "Explicit reasoning location.", true, null, List.of("LOCAL", "REMOTE")),
                    ReasoningConfigurationField.integer("expected-latency-ms", "Expected latency (ms)",
                            "Positive expected latency.", true, "1", OptionalLong.of(1), OptionalLong.empty()),
                    ReasoningConfigurationField.integer("preference", "Preference",
                            "Non-negative installation preference.", true, "1000",
                            OptionalLong.of(0), OptionalLong.empty()),
                    ReasoningConfigurationField.choice("availability", "Availability",
                            "Deterministic fixture availability state.", false, "AVAILABLE",
                            List.of("AVAILABLE", "DEGRADED", "UNAVAILABLE")),
                    ReasoningConfigurationField.text("background-gate-file", "Background gate file",
                            "Optional fixture path that blocks durable completion until present.", false, null),
                    ReasoningConfigurationField.text("background-completion-file", "Completion marker file",
                            "Optional fixture path written when background reasoning completes.", false, null)));

    @Override public ReasoningProviderDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ReasoningProviderConfigurator configurator() { return this; }

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : instances(configuration)) {
            String prefix = instancePrefix(instance);
            if (!enabled(configuration, prefix + ".enabled")) continue;
            mechanisms.add(new ReasoningMechanism<>(capability(configuration, prefix),
                    nonnegativeInt(configuration, prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }

    @Override public List<ReasoningConfiguredInstance> configuredInstances(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningConfiguredInstance> result = new ArrayList<>();
        for (String instance : instances(configuration)) {
            String prefix = instancePrefix(instance);
            Map<String, String> values = new LinkedHashMap<>();
            copy(configuration, prefix + ".id", values, "capability-id");
            copy(configuration, prefix + ".privacy", values, "privacy");
            copy(configuration, prefix + ".location", values, "location");
            copy(configuration, prefix + ".expected-latency-ms", values, "expected-latency-ms");
            copy(configuration, prefix + ".preference", values, "preference");
            copy(configuration, prefix + ".availability", values, "availability");
            copy(configuration, prefix + ".background-gate-file", values, "background-gate-file");
            copy(configuration, prefix + ".background-completion-file", values,
                    "background-completion-file");
            result.add(new ReasoningConfiguredInstance(instance,
                    enabled(configuration, prefix + ".enabled"), values));
        }
        return List.copyOf(result);
    }

    @Override public ReasoningProviderConfigurationUpdate configure(String instance,
            Map<String, String> supplied, ReasoningProviderConfiguration configuration) {
        String name = instanceName(instance);
        Map<String, String> values = new TreeMap<>();
        configuredInstances(configuration).stream().filter(item -> item.name().equals(name))
                .findFirst().ifPresent(item -> values.putAll(item.values()));
        Set<String> supported = DESCRIPTOR.fields().stream().map(ReasoningConfigurationField::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        supplied.forEach((field, value) -> {
            if (!supported.contains(field)) {
                throw new IllegalArgumentException("unsupported independent-text field: " + field);
            }
            if (value == null || value.isBlank()) values.remove(field);
            else values.put(field, value.strip());
        });
        DESCRIPTOR.fields().forEach(field -> field.defaultValue().ifPresent(defaultValue ->
                values.putIfAbsent(field.name(), defaultValue)));
        DESCRIPTOR.fields().stream().filter(ReasoningConfigurationField::required).forEach(field -> {
            if (!values.containsKey(field.name())) {
                throw new IllegalArgumentException("missing independent-text field " + field.name());
            }
        });

        List<String> configured = new ArrayList<>(instances(configuration));
        if (!configured.contains(name)) configured.add(name);
        String prefix = instancePrefix(name);
        Map<String, String> writes = new TreeMap<>();
        writes.put(PREFIX + ".instances", String.join(",", configured));
        writes.put(prefix + ".enabled", "true");
        map(values, writes, prefix, "capability-id", "id");
        map(values, writes, prefix, "privacy", "privacy");
        map(values, writes, prefix, "location", "location");
        map(values, writes, prefix, "expected-latency-ms", "expected-latency-ms");
        map(values, writes, prefix, "preference", "preference");
        Set<String> removals = new LinkedHashSet<>();
        optional(values, writes, removals, prefix, "availability", "availability");
        optional(values, writes, removals, prefix, "background-gate-file", "background-gate-file");
        optional(values, writes, removals, prefix, "background-completion-file",
                "background-completion-file");
        ReasoningProviderConfigurationUpdate update =
                new ReasoningProviderConfigurationUpdate(writes, removals);
        validateEnabledInstance(configuration.applying(update), name);
        return update;
    }

    @Override public ReasoningProviderConfigurationUpdate setEnabled(String instance, boolean enabled,
            ReasoningProviderConfiguration configuration) {
        String name = requireConfiguredInstance(instance, configuration);
        ReasoningProviderConfigurationUpdate update = new ReasoningProviderConfigurationUpdate(
                Map.of(instancePrefix(name) + ".enabled", Boolean.toString(enabled)), Set.of());
        if (enabled) validateEnabledInstance(configuration.applying(update), name);
        return update;
    }

    @Override public ReasoningProviderConfigurationUpdate remove(String instance,
            ReasoningProviderConfiguration configuration) {
        String name = requireConfiguredInstance(instance, configuration);
        List<String> original = instances(configuration);
        List<String> remaining = new ArrayList<>(original);
        remaining.remove(name);
        Set<String> removals = ownedInstanceKeys(configuration, name, original);
        Map<String, String> writes = new TreeMap<>();
        if (remaining.isEmpty()) removals.add(PREFIX + ".instances");
        else writes.put(PREFIX + ".instances", String.join(",", remaining));
        return new ReasoningProviderConfigurationUpdate(writes, removals);
    }

    private static IndependentTextReasoningCapability capability(
            ReasoningProviderConfiguration configuration, String prefix) {
        return new IndependentTextReasoningCapability(
                new ReasoningCapabilityId(required(configuration, prefix + ".id")),
                privacy(configuration, prefix + ".privacy"),
                location(configuration, prefix + ".location"),
                Duration.ofMillis(positiveLong(configuration, prefix + ".expected-latency-ms")),
                availability(configuration, prefix + ".availability"),
                optionalPath(configuration, prefix + ".background-gate-file"),
                optionalPath(configuration, prefix + ".background-completion-file"));
    }

    private static void validateEnabledInstance(ReasoningProviderConfiguration configuration,
            String instance) {
        String prefix = instancePrefix(instance);
        capability(configuration, prefix);
        nonnegativeInt(configuration, prefix + ".preference");
    }

    private static List<String> instances(ReasoningProviderConfiguration configuration) {
        String raw = configuration.value(PREFIX + ".instances").orElse(null);
        if (raw == null) return List.of();
        if (raw.isBlank()) throw new IllegalArgumentException(PREFIX + ".instances is blank");
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String item : raw.split(",", -1)) {
            String name = item.strip();
            if (name.isEmpty() || !names.add(name)) {
                throw new IllegalArgumentException(PREFIX + ".instances is malformed");
            }
        }
        return List.copyOf(names);
    }

    private static boolean enabled(ReasoningProviderConfiguration configuration, String key) {
        return switch (configuration.value(key).orElse("false").strip().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }

    private static String required(ReasoningProviderConfiguration configuration, String key) {
        String value = configuration.value(key).orElseThrow(
                () -> new IllegalArgumentException("missing property " + key));
        if (value.isBlank()) throw new IllegalArgumentException("missing property " + key);
        return value.strip();
    }

    private static Optional<Path> optionalPath(ReasoningProviderConfiguration configuration,
            String key) {
        return configuration.value(key).map(String::strip).filter(value -> !value.isEmpty()).map(Path::of);
    }

    private static Privacy privacy(ReasoningProviderConfiguration configuration, String key) {
        return switch (required(configuration, key).toUpperCase(Locale.ROOT)) {
            case "PUBLIC", "P1" -> Privacy.PUBLIC;
            case "UNKNOWN", "P2" -> Privacy.UNKNOWN;
            case "LOCAL", "P3" -> Privacy.LOCAL;
            case "MODULE", "P4" -> Privacy.MODULE;
            case "SECRET", "P5" -> Privacy.SECRET;
            default -> throw new IllegalArgumentException(key + " is not a valid Privacy");
        };
    }

    private static ReasoningLocation location(ReasoningProviderConfiguration configuration, String key) {
        try {
            return ReasoningLocation.valueOf(required(configuration, key).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " is not a valid ReasoningLocation", exception);
        }
    }

    private static ReasoningAvailability availability(ReasoningProviderConfiguration configuration,
            String key) {
        try {
            return ReasoningAvailability.valueOf(configuration.value(key).orElse("AVAILABLE")
                    .strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " is not a valid ReasoningAvailability", exception);
        }
    }

    private static long positiveLong(ReasoningProviderConfiguration configuration, String key) {
        try {
            long value = Long.parseLong(required(configuration, key));
            if (value < 1) throw new IllegalArgumentException(key + " must be positive");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static int nonnegativeInt(ReasoningProviderConfiguration configuration, String key) {
        try {
            int value = Integer.parseInt(required(configuration, key));
            if (value < 0) throw new IllegalArgumentException(key + " must not be negative");
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static String requireConfiguredInstance(String instance,
            ReasoningProviderConfiguration configuration) {
        String name = instanceName(instance);
        if (!instances(configuration).contains(name)) {
            throw new IllegalArgumentException("independent-text instance is not configured: " + name);
        }
        return name;
    }

    private static String instanceName(String instance) {
        String name = java.util.Objects.requireNonNull(instance, "instance").strip();
        if (name.isEmpty() || name.contains(",") || name.contains("/")) {
            throw new IllegalArgumentException("instance name must be non-blank and contain neither comma nor slash");
        }
        return name;
    }

    private static String instancePrefix(String instance) { return PREFIX + "." + instance; }

    private static void copy(ReasoningProviderConfiguration configuration, String rawKey,
            Map<String, String> values, String field) {
        configuration.value(rawKey).ifPresent(value -> values.put(field, value));
    }

    private static void map(Map<String, String> values, Map<String, String> writes, String prefix,
            String field, String rawName) {
        writes.put(prefix + "." + rawName, values.get(field));
    }

    private static void optional(Map<String, String> values, Map<String, String> writes,
            Set<String> removals, String prefix, String field, String rawName) {
        String rawKey = prefix + "." + rawName;
        if (values.containsKey(field)) writes.put(rawKey, values.get(field));
        else removals.add(rawKey);
    }

    private static Set<String> ownedInstanceKeys(ReasoningProviderConfiguration configuration,
            String instance, List<String> instances) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : configuration.keys()) {
            String owner = instances.stream().filter(candidate -> key.startsWith(instancePrefix(candidate) + "."))
                    .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
            if (instance.equals(owner)) keys.add(key);
        }
        return keys;
    }
}
