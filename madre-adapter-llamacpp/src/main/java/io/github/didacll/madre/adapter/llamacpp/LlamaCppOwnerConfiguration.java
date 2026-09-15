package io.github.didacll.madre.adapter.llamacpp;

import io.github.didacll.madre.reasoning.installation.ReasoningConfigurationField;
import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Shared provider-owned owner-configuration mechanics for the two distinct llama.cpp providers. */
final class LlamaCppOwnerConfiguration {
    private LlamaCppOwnerConfiguration() { }

    static List<ReasoningConfiguredInstance> configuredInstances(
            ReasoningProviderConfiguration configuration, String providerPrefix,
            String transportRawName, String transportFieldName) {
        List<ReasoningConfiguredInstance> result = new ArrayList<>();
        for (String instance : LlamaCppProviderConfiguration.instances(configuration, providerPrefix)) {
            String prefix = instancePrefix(providerPrefix, instance);
            Map<String, String> values = new LinkedHashMap<>();
            copy(configuration, prefix + ".id", values, "capability-id");
            copy(configuration, prefix + "." + transportRawName, values, transportFieldName);
            copy(configuration, prefix + ".model", values, "model");
            copy(configuration, prefix + ".privacy", values, "privacy");
            copy(configuration, prefix + ".expected-latency-ms", values, "expected-latency-ms");
            copy(configuration, prefix + ".preference", values, "preference");
            copy(configuration, prefix + ".resource.model-slot", values, "model-slot-units");
            result.add(new ReasoningConfiguredInstance(instance,
                    LlamaCppProviderConfiguration.enabled(configuration, prefix), values));
        }
        return List.copyOf(result);
    }

    static ReasoningProviderConfigurationUpdate configure(String instance,
            Map<String, String> supplied, ReasoningProviderConfiguration configuration,
            String providerPrefix, ReasoningProviderDescriptor descriptor,
            String transportRawName, String transportFieldName,
            Consumer<ReasoningProviderConfiguration> validator) {
        String name = instanceName(instance);
        Map<String, String> values = new TreeMap<>();
        configuredInstances(configuration, providerPrefix, transportRawName, transportFieldName)
                .stream().filter(item -> item.name().equals(name)).findFirst()
                .ifPresent(item -> values.putAll(item.values()));
        Set<String> supported = descriptor.fields().stream().map(ReasoningConfigurationField::name)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        supplied.forEach((field, value) -> {
            if (!supported.contains(field)) {
                throw new IllegalArgumentException("unsupported " + descriptor.id() + " field: " + field);
            }
            if (value == null || value.isBlank()) values.remove(field);
            else values.put(field, value.strip());
        });
        descriptor.fields().forEach(field -> field.defaultValue().ifPresent(defaultValue ->
                values.putIfAbsent(field.name(), defaultValue)));
        descriptor.fields().stream().filter(ReasoningConfigurationField::required).forEach(field -> {
            if (!values.containsKey(field.name())) {
                throw new IllegalArgumentException("missing " + descriptor.id() + " field " + field.name());
            }
        });

        List<String> instances = new ArrayList<>(
                LlamaCppProviderConfiguration.instances(configuration, providerPrefix));
        if (!instances.contains(name)) instances.add(name);
        String prefix = instancePrefix(providerPrefix, name);
        Map<String, String> writes = new TreeMap<>();
        writes.put(providerPrefix + ".instances", String.join(",", instances));
        writes.put(prefix + ".enabled", "true");
        writes.put(prefix + ".id", values.get("capability-id"));
        writes.put(prefix + "." + transportRawName, values.get(transportFieldName));
        writes.put(prefix + ".model", values.get("model"));
        writes.put(prefix + ".privacy", values.get("privacy"));
        writes.put(prefix + ".expected-latency-ms", values.get("expected-latency-ms"));
        writes.put(prefix + ".preference", values.get("preference"));
        Set<String> removals = new LinkedHashSet<>();
        if (values.containsKey("model-slot-units")) {
            writes.put(prefix + ".resource.model-slot", values.get("model-slot-units"));
        } else {
            removals.add(prefix + ".resource.model-slot");
        }
        ReasoningProviderConfigurationUpdate update =
                new ReasoningProviderConfigurationUpdate(writes, removals);
        validator.accept(configuration.applying(update));
        return update;
    }

    static ReasoningProviderConfigurationUpdate setEnabled(String instance, boolean enabled,
            ReasoningProviderConfiguration configuration, String providerPrefix,
            Consumer<ReasoningProviderConfiguration> validator) {
        String name = requireConfiguredInstance(instance, configuration, providerPrefix);
        ReasoningProviderConfigurationUpdate update = new ReasoningProviderConfigurationUpdate(
                Map.of(instancePrefix(providerPrefix, name) + ".enabled", Boolean.toString(enabled)),
                Set.of());
        if (enabled) validator.accept(configuration.applying(update));
        return update;
    }

    static ReasoningProviderConfigurationUpdate remove(String instance,
            ReasoningProviderConfiguration configuration, String providerPrefix) {
        String name = requireConfiguredInstance(instance, configuration, providerPrefix);
        List<String> original = LlamaCppProviderConfiguration.instances(configuration, providerPrefix);
        List<String> remaining = new ArrayList<>(original);
        remaining.remove(name);
        Set<String> removals = ownedInstanceKeys(configuration, name, original, providerPrefix);
        Map<String, String> writes = new TreeMap<>();
        if (remaining.isEmpty()) removals.add(providerPrefix + ".instances");
        else writes.put(providerPrefix + ".instances", String.join(",", remaining));
        return new ReasoningProviderConfigurationUpdate(writes, removals);
    }

    private static String requireConfiguredInstance(String instance,
            ReasoningProviderConfiguration configuration, String providerPrefix) {
        String name = instanceName(instance);
        if (!LlamaCppProviderConfiguration.instances(configuration, providerPrefix).contains(name)) {
            throw new IllegalArgumentException(providerPrefix.substring("reasoning.".length())
                    + " instance is not configured: " + name);
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

    private static String instancePrefix(String providerPrefix, String instance) {
        return providerPrefix + "." + instance;
    }

    private static void copy(ReasoningProviderConfiguration configuration, String rawKey,
            Map<String, String> values, String field) {
        configuration.value(rawKey).ifPresent(value -> values.put(field, value));
    }

    private static Set<String> ownedInstanceKeys(ReasoningProviderConfiguration configuration,
            String instance, List<String> instances, String providerPrefix) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : configuration.keys()) {
            String owner = instances.stream()
                    .filter(candidate -> key.startsWith(instancePrefix(providerPrefix, candidate) + "."))
                    .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
            if (instance.equals(owner)) keys.add(key);
        }
        return keys;
    }
}
