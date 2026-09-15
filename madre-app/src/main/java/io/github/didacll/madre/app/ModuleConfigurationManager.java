package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationField;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/** Host-owned transactional persistence for Module-owned installation configuration. */
final class ModuleConfigurationManager {
    private static final String CONFIGURATION_PREFIX = "modules.config[";

    private final Path configurationPath;
    private final Properties properties;
    private final Map<String, ModuleProvider> providers;

    ModuleConfigurationManager(Path configurationPath, Properties properties,
            List<ModuleProvider> providers) {
        this.configurationPath = Objects.requireNonNull(configurationPath, "configurationPath")
                .toAbsolutePath().normalize();
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(providers, "providers");
        TreeMap<String, ModuleProvider> canonical = new TreeMap<>();
        for (ModuleProvider provider : providers) {
            ModuleProvider installed = Objects.requireNonNull(provider, "ModuleProvider");
            ModuleId moduleId = Objects.requireNonNull(installed.moduleId(),
                    "ModuleProvider.moduleId()");
            if (canonical.putIfAbsent(moduleId.value(), installed) != null) {
                throw new IllegalStateException(
                        "duplicate ModuleProvider identity: " + moduleId.value());
            }
            descriptor(installed);
        }
        this.providers = Map.copyOf(canonical);
    }

    List<ModuleProvider> providers() {
        return providers.values().stream().sorted(java.util.Comparator.comparing(
                provider -> provider.moduleId().value())).toList();
    }

    ModuleProvider provider(String value) {
        ModuleId id = new ModuleId(value);
        ModuleProvider provider = providers.get(id.value());
        if (provider == null) {
            throw new IllegalArgumentException("Module is not installed: " + id);
        }
        return provider;
    }

    ModuleConfigurationDescriptor descriptor(ModuleProvider provider) {
        ModuleProvider installed = Objects.requireNonNull(provider, "provider");
        ModuleConfigurationDescriptor descriptor = Objects.requireNonNull(
                installed.configurationDescriptor(), "ModuleProvider.configurationDescriptor()");
        if (!descriptor.moduleId().equals(installed.moduleId())) {
            throw new IllegalStateException("Module configuration descriptor identity "
                    + descriptor.moduleId() + " does not match provider identity "
                    + installed.moduleId());
        }
        return descriptor;
    }

    ModuleProviderConfiguration configuration(ModuleProvider provider) {
        ModuleId moduleId = Objects.requireNonNull(provider, "provider").moduleId();
        String prefix = prefix(moduleId);
        Map<String, String> values = new TreeMap<>();
        properties.stringPropertyNames().stream().filter(name -> name.startsWith(prefix)).sorted()
                .forEach(name -> values.put(name.substring(prefix.length()),
                        properties.getProperty(name)));
        return new ModuleProviderConfiguration(moduleId, values);
    }

    void configure(String moduleId, Map<String, String> assignments) throws IOException {
        ModuleProvider provider = provider(moduleId);
        ModuleConfigurationDescriptor descriptor = descriptor(provider);
        Map<String, ModuleConfigurationField> fields = new LinkedHashMap<>();
        descriptor.fields().forEach(field -> fields.put(field.name(), field));

        Map<String, String> candidateValues = values(configuration(provider));
        Objects.requireNonNull(assignments, "assignments").forEach((name, value) -> {
            ModuleConfigurationField field = fields.get(name);
            if (field == null) {
                throw new IllegalArgumentException("Module " + provider.moduleId()
                        + " does not declare configuration field: " + name);
            }
            candidateValues.put(name, field.canonicalize(value));
        });
        for (String name : List.copyOf(candidateValues.keySet())) {
            ModuleConfigurationField field = fields.get(name);
            if (field == null) {
                throw new IllegalArgumentException("Module " + provider.moduleId()
                        + " does not declare configuration field: " + name);
            }
            candidateValues.put(name, field.canonicalize(candidateValues.get(name)));
        }
        descriptor.fields().stream().filter(ModuleConfigurationField::required)
                .filter(field -> !candidateValues.containsKey(field.name()))
                .forEach(field -> {
                    if (field.defaultValue().isPresent()) {
                        candidateValues.put(field.name(), field.defaultValue().orElseThrow());
                    } else {
                        throw new IllegalArgumentException("required Module configuration field is unset: "
                                + field.name());
                    }
                });

        ModuleProviderConfiguration candidate = new ModuleProviderConfiguration(
                provider.moduleId(), candidateValues);
        ModuleProviderConfiguration validated = Objects.requireNonNull(
                provider.validateConfiguration(candidate),
                "ModuleProvider.validateConfiguration() returned null");
        if (!validated.moduleId().equals(provider.moduleId())) {
            throw new IllegalStateException("Module configuration validation escaped identity "
                    + provider.moduleId() + " to " + validated.moduleId());
        }
        Map<String, String> validatedValues = values(validated);
        for (Map.Entry<String, String> entry : validatedValues.entrySet()) {
            ModuleConfigurationField field = fields.get(entry.getKey());
            if (field == null) {
                throw new IllegalStateException("Module configuration validation returned undeclared field: "
                        + entry.getKey());
            }
            field.canonicalize(entry.getValue());
        }
        persist(provider.moduleId(), validatedValues);
    }

    private void persist(ModuleId moduleId, Map<String, String> values) throws IOException {
        Properties candidate = copy(properties);
        String prefix = prefix(moduleId);
        candidate.stringPropertyNames().stream().filter(name -> name.startsWith(prefix)).toList()
                .forEach(candidate::remove);
        values.forEach((name, value) -> candidate.setProperty(prefix + name, value));
        HostEnvironment.replaceConfiguration(configurationPath, candidate);
        properties.clear();
        candidate.forEach(properties::put);
    }

    private static Map<String, String> values(ModuleProviderConfiguration configuration) {
        Map<String, String> result = new TreeMap<>();
        configuration.keys().forEach(key -> result.put(key, configuration.value(key).orElseThrow()));
        return result;
    }

    private static String prefix(ModuleId moduleId) {
        return CONFIGURATION_PREFIX + moduleId.value() + "].";
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        source.stringPropertyNames().forEach(name -> copy.setProperty(name, source.getProperty(name)));
        return copy;
    }
}
