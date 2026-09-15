package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/** Cross-platform JVM discovery of installed reasoning-provider JARs. */
final class InstalledReasoningLoader implements AutoCloseable {
    private final URLClassLoader classLoader;
    private final Map<ReasoningProviderId, ReasoningMechanismProvider> providers;
    private final Map<ReasoningProviderId, Path> providerSources;

    InstalledReasoningLoader(Path directory) { this(installationDirectories(directory)); }

    InstalledReasoningLoader(List<Path> directories) {
        this(new JarSelection(jars(directories)), false);
    }

    private InstalledReasoningLoader(JarSelection selection, boolean requireProviderFromSelectedJar) {
        List<Path> jars = selection.jars();
        URL[] urls = jars.stream().map(InstalledReasoningLoader::url).toArray(URL[]::new);
        Set<Path> selected = new HashSet<>(jars);
        classLoader = new URLClassLoader(urls, ReasoningMechanismProvider.class.getClassLoader());
        List<ReasoningMechanismProvider> discovered = new ArrayList<>();
        try {
            Stream<ServiceLoader.Provider<ReasoningMechanismProvider>> entries =
                    ServiceLoader.load(ReasoningMechanismProvider.class, classLoader).stream();
            if (requireProviderFromSelectedJar) {
                entries = entries.filter(provider -> selected.contains(sourceJar(provider.type())));
            }
            entries.map(ServiceLoader.Provider::get).forEach(discovered::add);
            Map<ReasoningProviderId, ReasoningMechanismProvider> indexed = new TreeMap<>();
            Map<ReasoningProviderId, Path> sources = new TreeMap<>();
            for (ReasoningMechanismProvider provider : discovered) {
                ReasoningProviderDescriptor descriptor = Objects.requireNonNull(provider.descriptor(),
                        "reasoning provider descriptor");
                Objects.requireNonNull(provider.configurator(),
                        "reasoning provider configurator for " + descriptor.id());
                ReasoningMechanismProvider previous = indexed.putIfAbsent(descriptor.id(), provider);
                if (previous != null) {
                    throw new IllegalStateException("duplicate reasoning provider identity "
                            + descriptor.id());
                }
                sources.put(descriptor.id(), sourceJar(provider.getClass()));
            }
            providers = Collections.unmodifiableMap(new TreeMap<>(indexed));
            providerSources = Collections.unmodifiableMap(new TreeMap<>(sources));
        } catch (ServiceConfigurationError | RuntimeException failure) {
            closeProviders(discovered, failure);
            try {
                classLoader.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw new IllegalStateException("cannot load installed reasoning provider", failure);
        }
    }

    static InstalledReasoningLoader forJar(Path jar) {
        Path selected = Objects.requireNonNull(jar, "jar").toAbsolutePath().normalize();
        if (!Files.isRegularFile(selected)) {
            throw new IllegalArgumentException("reasoning-provider JAR is not a regular file: "
                    + selected);
        }
        return new InstalledReasoningLoader(new JarSelection(List.of(selected)), true);
    }

    List<ReasoningProviderDescriptor> providerDescriptors() {
        return providers.values().stream().map(ReasoningMechanismProvider::descriptor).toList();
    }

    List<DiscoveredProvider> discoveredProviders() {
        return providers.entrySet().stream().map(entry -> new DiscoveredProvider(entry.getValue(),
                providerSources.get(entry.getKey()))).toList();
    }

    Optional<ReasoningMechanismProvider> provider(ReasoningProviderId id) {
        return Optional.ofNullable(providers.get(Objects.requireNonNull(id, "id")));
    }

    List<ProviderInstance> configuredInstances(ReasoningProviderConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<ProviderInstance> result = new ArrayList<>();
        providers.forEach((id, provider) -> {
            List<ReasoningConfiguredInstance> configured =
                    provider.configurator().configuredInstances(configuration);
            if (configured == null) {
                throw new IllegalStateException("reasoning provider " + id
                        + " returned null configured-instance list");
            }
            configured.forEach(instance -> result.add(new ProviderInstance(id,
                    Objects.requireNonNull(instance,
                            "reasoning provider " + id + " returned null configured instance"))));
        });
        return List.copyOf(result);
    }

    List<ReasoningMechanism<?, ?>> materialize(ReasoningProviderConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        List<ReasoningMechanism<?, ?>> mechanisms = new ArrayList<>();
        providers.forEach((id, provider) -> {
            final List<ReasoningMechanism<?, ?>> provided;
            try {
                provided = provider.materialize(configuration);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("reasoning provider " + id + ": "
                        + message(exception), exception);
            }
            if (provided == null) {
                throw new IllegalStateException("reasoning provider " + id
                        + " returned null reasoning materialization");
            }
            for (ReasoningMechanism<?, ?> mechanism : provided) {
                mechanisms.add(Objects.requireNonNull(mechanism,
                        "reasoning provider " + id + " returned a null reasoning mechanism"));
            }
        });
        return List.copyOf(mechanisms);
    }

    static Path defaultDirectory() {
        try {
            Path location = Path.of(MadreApplication.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path parent = Files.isDirectory(location) ? location : location.getParent();
            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equals("lib")
                    && parent.getParent() != null) {
                return parent.getParent().resolve("reasoning");
            }
            if (parent != null) return parent.resolve("reasoning");
        } catch (URISyntaxException | RuntimeException ignored) {
            // Development execution can use an explicit reasoning.directory.
        }
        return Path.of("reasoning").toAbsolutePath().normalize();
    }

    private static List<Path> installationDirectories(Path directory) {
        Path requested = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Path shipped = defaultDirectory().toAbsolutePath().normalize();
        if (!requested.equals(shipped)) return List.of(requested);
        HostEnvironment host = HostEnvironment.resolve();
        if (!host.hasPackagedDefaults()) return List.of(shipped);
        Path owner = host.ownerReasoningDirectory();
        return owner.equals(shipped) ? List.of(shipped) : List.of(shipped, owner);
    }

    private static List<Path> jars(List<Path> directories) {
        Objects.requireNonNull(directories, "directories");
        List<Path> result = new ArrayList<>();
        for (Path directory : directories.stream().map(path -> Objects.requireNonNull(path, "directory")
                .toAbsolutePath().normalize()).distinct().toList()) {
            if (Files.exists(directory) && !Files.isDirectory(directory)) {
                throw new IllegalArgumentException("reasoning.directory is not a directory: " + directory);
            }
            if (!Files.exists(directory)) continue;
            try (var entries = Files.list(directory)) {
                result.addAll(entries.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".jar"))
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted().toList());
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "cannot inspect installed reasoning mechanisms in " + directory, exception);
            }
        }
        return List.copyOf(result);
    }

    private static URL url(Path path) {
        try {
            return path.toUri().toURL();
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalArgumentException("invalid reasoning-mechanism JAR path: " + path,
                    exception);
        }
    }

    private static Path sourceJar(Class<?> type) {
        try {
            URL location = Objects.requireNonNull(type.getProtectionDomain().getCodeSource(),
                    "provider code source").getLocation();
            return Path.of(location.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | RuntimeException exception) {
            throw new IllegalStateException("cannot resolve reasoning provider source for "
                    + type.getName(), exception);
        }
    }

    @Override public void close() {
        RuntimeException failure = null;
        List<ReasoningMechanismProvider> values = new ArrayList<>(providers.values());
        for (int index = values.size() - 1; index >= 0; index--) {
            try {
                values.get(index).close();
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        try {
            classLoader.close();
        } catch (IOException exception) {
            IllegalStateException wrapped = new IllegalStateException(
                    "cannot close installed reasoning loader", exception);
            if (failure == null) failure = wrapped;
            else failure.addSuppressed(wrapped);
        }
        if (failure != null) throw failure;
    }

    private static void closeProviders(List<ReasoningMechanismProvider> providers, Throwable failure) {
        for (int index = providers.size() - 1; index >= 0; index--) {
            try {
                providers.get(index).close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    record DiscoveredProvider(ReasoningMechanismProvider provider, Path sourceJar) {
        DiscoveredProvider {
            Objects.requireNonNull(provider, "provider");
            sourceJar = Objects.requireNonNull(sourceJar, "sourceJar").toAbsolutePath().normalize();
        }
    }

    record ProviderInstance(ReasoningProviderId providerId, ReasoningConfiguredInstance instance) {
        ProviderInstance {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(instance, "instance");
        }
    }

    private record JarSelection(List<Path> jars) {
        JarSelection {
            jars = List.copyOf(Objects.requireNonNull(jars, "jars"));
        }
    }
}
