package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/** Generic identity-scoped materialization of owner-installed Module providers. */
final class ModuleInstaller {
    private static final String CONFIGURATION_PREFIX = "modules.config[";

    private ModuleInstaller() { }

    static List<ModuleRegistration.Registration> install(List<ModuleProvider> providers,
            Function<ModuleId, ModuleContext> contextFactory, Properties properties,
            ModuleRegistration registry) {
        Objects.requireNonNull(providers, "providers");
        Objects.requireNonNull(contextFactory, "contextFactory");
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(registry, "registry");

        TreeMap<String, ProviderEntry> canonical = canonicalProviders(providers);
        Map<ModuleId, ModuleProviderConfiguration> configurations =
                configurations(properties, canonical.values());
        List<PreparedModule> prepared = new ArrayList<>();
        for (ProviderEntry entry : canonical.values()) {
            ModuleProviderConfiguration configuration = configurations.get(entry.moduleId());
            ModuleContext context = Objects.requireNonNull(contextFactory.apply(entry.moduleId()),
                    "ModuleContext factory returned null for " + entry.moduleId());
            ModuleInstance instance;
            try {
                instance = Objects.requireNonNull(
                        entry.provider().create(context, configuration),
                        "ModuleProvider returned null for " + entry.moduleId());
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "cannot materialize Module " + entry.moduleId(), exception);
            }
            if (!instance.definition().id().equals(entry.moduleId())) {
                throw new IllegalStateException("ModuleProvider identity " + entry.moduleId()
                        + " does not match materialized Module identity "
                        + instance.definition().id());
            }
            try {
                instance.validateBindings();
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "cannot validate materialized Module " + entry.moduleId(), exception);
            }
            prepared.add(new PreparedModule(entry.moduleId(), instance));
        }

        List<ModuleRegistration.Registration> registrations = new ArrayList<>();
        try {
            for (PreparedModule module : prepared) {
                try {
                    registrations.add(registry.register(module.instance()));
                } catch (RuntimeException exception) {
                    throw new IllegalStateException(
                            "cannot register Module " + module.moduleId(), exception);
                }
            }
            return List.copyOf(registrations);
        } catch (RuntimeException failure) {
            closeAfterFailure(registrations, failure);
            throw failure;
        }
    }

    private static TreeMap<String, ProviderEntry> canonicalProviders(List<ModuleProvider> providers) {
        TreeMap<String, ProviderEntry> canonical = new TreeMap<>();
        for (ModuleProvider provider : providers) {
            ModuleProvider installed = Objects.requireNonNull(provider, "ModuleProvider");
            ModuleId moduleId = Objects.requireNonNull(installed.moduleId(),
                    "ModuleProvider.moduleId()");
            ProviderEntry previous = canonical.putIfAbsent(moduleId.value(),
                    new ProviderEntry(moduleId, installed));
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate ModuleProvider identity: " + moduleId.value());
            }
        }
        return canonical;
    }

    private static Map<ModuleId, ModuleProviderConfiguration> configurations(
            Properties properties, Iterable<ProviderEntry> providers) {
        Set<String> configurationNames = new HashSet<>();
        properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith(CONFIGURATION_PREFIX))
                .forEach(configurationNames::add);

        Set<String> consumed = new HashSet<>();
        Map<ModuleId, ModuleProviderConfiguration> result = new java.util.HashMap<>();
        for (ProviderEntry provider : providers) {
            String prefix = CONFIGURATION_PREFIX + provider.moduleId().value() + "].";
            Map<String, String> values = new TreeMap<>();
            configurationNames.stream().filter(name -> name.startsWith(prefix)).sorted()
                    .forEach(name -> {
                        String key = name.substring(prefix.length());
                        values.put(key, properties.getProperty(name));
                        consumed.add(name);
                    });
            result.put(provider.moduleId(),
                    new ModuleProviderConfiguration(provider.moduleId(), values));
        }

        configurationNames.stream().filter(name -> !consumed.contains(name)).sorted().findFirst()
                .ifPresent(name -> {
                    throw new IllegalArgumentException(
                            "Module configuration does not target an installed canonical Module: "
                                    + name);
                });
        return Map.copyOf(result);
    }

    private static void closeAfterFailure(List<ModuleRegistration.Registration> registrations,
            RuntimeException failure) {
        for (int index = registrations.size() - 1; index >= 0; index--) {
            try {
                registrations.get(index).close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private record ProviderEntry(ModuleId moduleId, ModuleProvider provider) { }
    private record PreparedModule(ModuleId moduleId, ModuleInstance instance) { }
}
