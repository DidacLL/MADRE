package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/** Host-owned transactional persistence for provider-owned reasoning instance configuration. */
final class ReasoningConfigurationManager {
    private final Path configurationPath;
    private final Properties properties;
    private final InstalledReasoningLoader loader;

    ReasoningConfigurationManager(Path configurationPath, Properties properties,
            InstalledReasoningLoader loader) {
        this.configurationPath = Objects.requireNonNull(configurationPath, "configurationPath")
                .toAbsolutePath().normalize();
        this.properties = Objects.requireNonNull(properties, "properties");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    ReasoningProviderConfiguration configuration() { return configuration(properties); }

    ReasoningMechanismProvider provider(String value) {
        ReasoningProviderId id = new ReasoningProviderId(value);
        return loader.provider(id).orElseThrow(() ->
                new IllegalArgumentException("reasoning provider is not installed: " + id));
    }

    void configure(String providerId, String instance, Map<String, String> values) throws IOException {
        ReasoningMechanismProvider provider = provider(providerId);
        persist(provider.configurator().configure(instance, Map.copyOf(values), configuration()));
    }

    void setEnabled(String providerId, String instance, boolean enabled) throws IOException {
        ReasoningMechanismProvider provider = provider(providerId);
        persist(provider.configurator().setEnabled(instance, enabled, configuration()));
    }

    void remove(String providerId, String instance) throws IOException {
        ReasoningMechanismProvider provider = provider(providerId);
        persist(provider.configurator().remove(instance, configuration()));
    }

    private void persist(ReasoningProviderConfigurationUpdate update) throws IOException {
        Objects.requireNonNull(update, "update");
        Properties candidate = copy(properties);
        update.removals().forEach(candidate::remove);
        update.values().forEach(candidate::setProperty);
        HostEnvironment.replaceConfiguration(configurationPath, candidate);
        properties.clear();
        candidate.forEach(properties::put);
    }

    static ReasoningProviderConfiguration configuration(Properties properties) {
        Map<String, String> values = new TreeMap<>();
        Objects.requireNonNull(properties, "properties").stringPropertyNames().stream()
                .filter(name -> name.startsWith("reasoning."))
                .filter(name -> !name.equals("reasoning.directory"))
                .forEach(name -> values.put(name, properties.getProperty(name)));
        return new ReasoningProviderConfiguration(values);
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        source.stringPropertyNames().forEach(name -> copy.setProperty(name, source.getProperty(name)));
        return copy;
    }
}
