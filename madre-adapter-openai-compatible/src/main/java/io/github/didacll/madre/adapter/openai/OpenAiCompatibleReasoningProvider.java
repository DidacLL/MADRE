package io.github.didacll.madre.adapter.openai;

import io.github.didacll.madre.reasoning.installation.ReasoningConfigurationField;
import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurator;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;

/** Service-provider materializer and owner configurator for OpenAI-compatible mechanisms. */
public final class OpenAiCompatibleReasoningProvider
        implements ReasoningMechanismProvider, ReasoningProviderConfigurator {
    private static final String PREFIX = "reasoning.openai-compatible";
    private static final ReasoningProviderDescriptor DESCRIPTOR = new ReasoningProviderDescriptor(
            new ReasoningProviderId("openai-compatible"),
            "OpenAI-compatible HTTP",
            "Configure an explicit OpenAI-compatible HTTP reasoning endpoint. No account integration is implied.",
            List.of(
                    ReasoningConfigurationField.text("capability-id", "Capability identity",
                            "Stable identity used by MADRE for this materialized reasoning mechanism.", true, null),
                    ReasoningConfigurationField.text("endpoint", "Endpoint",
                            "Absolute HTTP or HTTPS base URI exposed by the compatible service.", true, null),
                    ReasoningConfigurationField.text("model", "Model",
                            "Provider-specific model identifier sent to the compatible endpoint.", true, null),
                    ReasoningConfigurationField.choice("privacy", "Privacy",
                            "Explicit receiving Privacy. MADRE never infers this from endpoint or location.", true,
                            null, List.of("PUBLIC", "UNKNOWN", "LOCAL", "MODULE", "SECRET")),
                    ReasoningConfigurationField.choice("location", "Reasoning location",
                            "Explicit ReasoningLocation used for selection.", true, null,
                            List.of("LOCAL", "REMOTE")),
                    ReasoningConfigurationField.integer("expected-latency-ms", "Expected latency (ms)",
                            "Positive expected mechanism latency used for reasoning selection.", true, "30000",
                            OptionalLong.of(1), OptionalLong.empty()),
                    ReasoningConfigurationField.integer("preference", "Preference",
                            "Non-negative installation preference; higher values are preferred after compatibility.",
                            true, "0", OptionalLong.of(0), OptionalLong.empty()),
                    ReasoningConfigurationField.integer("model-slot-units", "Model-slot units",
                            "Optional units claimed from the conventional model-slot resource.", false, null,
                            OptionalLong.of(1), OptionalLong.empty())));

    @Override public ReasoningProviderDescriptor descriptor() { return DESCRIPTOR; }
    @Override public ReasoningProviderConfigurator configurator() { return this; }

    @Override public List<ReasoningMechanism<?, ?>> materialize(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        for (String instance : OpenAiProviderConfiguration.instances(configuration, PREFIX)) {
            String prefix = instancePrefix(instance);
            if (!OpenAiProviderConfiguration.enabled(configuration, prefix)) continue;
            OpenAiCompatibleConfiguration adapter = adapterConfiguration(configuration, prefix);
            mechanisms.add(new ReasoningMechanism<>(
                    new OpenAiCompatibleReasoningCapability(adapter),
                    OpenAiProviderConfiguration.preference(configuration, prefix + ".preference")));
        }
        return List.copyOf(mechanisms);
    }

    @Override public List<ReasoningConfiguredInstance> configuredInstances(
            ReasoningProviderConfiguration configuration) {
        List<ReasoningConfiguredInstance> result = new ArrayList<>();
        for (String instance : OpenAiProviderConfiguration.instances(configuration, PREFIX)) {
            String prefix = instancePrefix(instance);
            Map<String, String> values = new LinkedHashMap<>();
            copy(configuration, prefix + ".id", values, "capability-id");
            copy(configuration, prefix + ".endpoint", values, "endpoint");
            copy(configuration, prefix + ".model", values, "model");
            copy(configuration, prefix + ".privacy", values, "privacy");
            copy(configuration, prefix + ".location", values, "location");
            copy(configuration, prefix + ".expected-latency-ms", values, "expected-latency-ms");
            copy(configuration, prefix + ".preference", values, "preference");
            copy(configuration, prefix + ".resource.model-slot", values, "model-slot-units");
            result.add(new ReasoningConfiguredInstance(instance,
                    OpenAiProviderConfiguration.enabled(configuration, prefix), values));
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
                throw new IllegalArgumentException("unsupported OpenAI-compatible field: " + field);
            }
            if (value == null || value.isBlank()) values.remove(field);
            else values.put(field, value.strip());
        });
        DESCRIPTOR.fields().forEach(field -> field.defaultValue().ifPresent(defaultValue ->
                values.putIfAbsent(field.name(), defaultValue)));
        DESCRIPTOR.fields().stream().filter(ReasoningConfigurationField::required).forEach(field -> {
            if (!values.containsKey(field.name())) {
                throw new IllegalArgumentException("missing OpenAI-compatible field " + field.name());
            }
        });

        List<String> instances = new ArrayList<>(OpenAiProviderConfiguration.instances(configuration, PREFIX));
        if (!instances.contains(name)) instances.add(name);
        String prefix = instancePrefix(name);
        Map<String, String> writes = new TreeMap<>();
        writes.put(PREFIX + ".instances", String.join(",", instances));
        writes.put(prefix + ".enabled", "true");
        writes.put(prefix + ".id", values.get("capability-id"));
        writes.put(prefix + ".endpoint", values.get("endpoint"));
        writes.put(prefix + ".model", values.get("model"));
        writes.put(prefix + ".privacy", values.get("privacy"));
        writes.put(prefix + ".location", values.get("location"));
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
        List<String> instances = new ArrayList<>(OpenAiProviderConfiguration.instances(configuration, PREFIX));
        instances.remove(name);
        Set<String> removals = ownedInstanceKeys(configuration, name,
                OpenAiProviderConfiguration.instances(configuration, PREFIX));
        Map<String, String> writes = new TreeMap<>();
        if (instances.isEmpty()) removals.add(PREFIX + ".instances");
        else writes.put(PREFIX + ".instances", String.join(",", instances));
        return new ReasoningProviderConfigurationUpdate(writes, removals);
    }

    private static OpenAiCompatibleConfiguration adapterConfiguration(
            ReasoningProviderConfiguration configuration, String prefix) {
        return new OpenAiCompatibleConfiguration(
                OpenAiProviderConfiguration.capabilityId(configuration, prefix + ".id"),
                URI.create(OpenAiProviderConfiguration.required(configuration, prefix + ".endpoint")),
                OpenAiProviderConfiguration.required(configuration, prefix + ".model"),
                OpenAiProviderConfiguration.privacy(configuration, prefix + ".privacy"),
                OpenAiProviderConfiguration.location(configuration, prefix + ".location"),
                OpenAiProviderConfiguration.duration(configuration, prefix + ".expected-latency-ms"),
                OpenAiProviderConfiguration.resources(configuration, prefix));
    }

    private static void validateEnabledInstance(ReasoningProviderConfiguration configuration,
            String instance) {
        String prefix = instancePrefix(instance);
        adapterConfiguration(configuration, prefix);
        OpenAiProviderConfiguration.preference(configuration, prefix + ".preference");
    }

    private static String requireConfiguredInstance(String instance,
            ReasoningProviderConfiguration configuration) {
        String name = instanceName(instance);
        if (!OpenAiProviderConfiguration.instances(configuration, PREFIX).contains(name)) {
            throw new IllegalArgumentException("OpenAI-compatible instance is not configured: " + name);
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
