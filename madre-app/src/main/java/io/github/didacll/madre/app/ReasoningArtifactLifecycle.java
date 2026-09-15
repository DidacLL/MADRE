package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;

/** Host-owned local-file lifecycle for independently packaged reasoning-provider JARs. */
final class ReasoningArtifactLifecycle {
    private ReasoningArtifactLifecycle() { }

    static InstallResult install(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, Path source, boolean replace) throws IOException {
        Path candidate = ManagedJarFiles.requireSourceJar(source);
        ReasoningProviderId id = inspect(candidate, properties);
        InstalledArtifact existing = installed(host).get(id);
        if (existing != null && existing.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("reasoning provider " + id
                    + " is shipped with MADRE and cannot be overridden by an owner artifact");
        }
        Path owner = host.ownerReasoningDirectory();
        if (replace) {
            if (existing == null || existing.source() != Source.OWNER) {
                throw new IllegalArgumentException(
                        "--replace requires an existing owner-installed reasoning provider: " + id);
            }
            ManagedJarFiles.requireManagedPath(owner, "reasoning", id.value(), existing.path());
        } else if (existing != null) {
            throw new IllegalArgumentException("reasoning provider is already owner-installed: " + id
                    + "; use --replace to replace the same canonical identity");
        }
        Path destination = ManagedJarFiles.managedPath(owner, "reasoning", id.value());
        if (existing == null && Files.exists(destination)) {
            throw new IllegalStateException("managed reasoning destination already exists: "
                    + destination.getFileName());
        }
        Path staged = ManagedJarFiles.stage(candidate, owner);
        try {
            ReasoningProviderId stagedId = inspect(staged, properties);
            if (!stagedId.equals(id)) {
                throw new IllegalStateException("staged reasoning provider identity changed from "
                        + id + " to " + stagedId);
            }
            String digest = ManagedJarFiles.sha256(staged);
            ManagedJarFiles.commit(staged, destination);
            return new InstallResult(id, destination, digest, replace);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    static UninstallResult uninstall(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, String providerId, boolean purge) throws IOException {
        ReasoningProviderId id = new ReasoningProviderId(providerId);
        InstalledArtifact artifact = installed(host).get(id);
        if (artifact == null) {
            Path external = developmentSource(properties, id);
            if (external != null) {
                throw new IllegalArgumentException("reasoning provider " + id + " is discovered from "
                        + external + " outside MADRE's owner-managed reasoning root; remove it manually");
            }
            throw new IllegalArgumentException("reasoning provider is not installed: " + id);
        }
        if (artifact.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("shipped reasoning provider cannot be uninstalled: " + id);
        }
        ManagedJarFiles.requireManagedPath(host.ownerReasoningDirectory(), "reasoning", id.value(),
                artifact.path());

        Properties previous = copy(properties);
        Properties next = previous;
        List<ReasoningConfiguredInstance> instances;
        try (InstalledReasoningLoader loader = InstalledReasoningLoader.forJar(artifact.path())) {
            ReasoningMechanismProvider provider = loader.provider(id).orElseThrow(() ->
                    new IllegalStateException("owner reasoning artifact no longer exposes provider " + id));
            instances = configured(provider, ReasoningConfigurationManager.configuration(properties));
            if (!instances.isEmpty() && purge) next = purge(provider, previous);
        }
        if (!instances.isEmpty() && !purge) {
            throw new IllegalArgumentException("reasoning provider " + id
                    + " has configured instances; rerun uninstall with --purge-configuration");
        }
        boolean changed = !instances.isEmpty() && purge;
        if (changed) {
            HostEnvironment.replaceConfiguration(loaded.path(), next);
            replace(properties, next);
        }
        try {
            Files.delete(artifact.path());
        } catch (IOException | RuntimeException failure) {
            if (changed) restore(loaded.path(), properties, previous, failure);
            throw failure;
        }
        return new UninstallResult(id, artifact.path(), changed);
    }

    static Source classify(HostEnvironment host, Path source) {
        Path parent = source.toAbsolutePath().normalize().getParent();
        if (host.ownerReasoningDirectory().toAbsolutePath().normalize().equals(parent)) return Source.OWNER;
        if (host.reasoningDirectories().getFirst().toAbsolutePath().normalize().equals(parent)) return Source.SHIPPED;
        return Source.DEVELOPMENT;
    }

    private static ReasoningProviderId inspect(Path jar, Properties properties) {
        try (InstalledReasoningLoader reasoning = InstalledReasoningLoader.forJar(jar);
                InstalledModuleLoader modules = InstalledModuleLoader.forJar(jar)) {
            var providers = reasoning.discoveredProviders();
            boolean module = !modules.providers().isEmpty();
            if (providers.isEmpty()) {
                if (module) throw new IllegalArgumentException(
                        "Module artifact cannot be installed as a reasoning provider");
                throw new IllegalArgumentException("reasoning artifact exposes no ReasoningMechanismProvider");
            }
            if (providers.size() != 1) throw new IllegalArgumentException(
                    "managed reasoning artifact must expose exactly one ReasoningMechanismProvider");
            if (module) throw new IllegalArgumentException(
                    "managed reasoning artifact cannot also expose a ModuleProvider");
            ReasoningMechanismProvider provider = providers.getFirst().provider();
            var descriptor = Objects.requireNonNull(provider.descriptor(), "reasoning provider descriptor");
            Objects.requireNonNull(provider.configurator(), "reasoning provider configurator");
            configured(provider, ReasoningConfigurationManager.configuration(properties));
            return descriptor.id();
        }
    }

    private static Map<ReasoningProviderId, InstalledArtifact> installed(HostEnvironment host) {
        Map<ReasoningProviderId, InstalledArtifact> result = new TreeMap<>();
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(host.reasoningDirectories())) {
            for (var discovered : loader.discoveredProviders()) {
                ReasoningProviderId id = discovered.provider().descriptor().id();
                InstalledArtifact artifact = new InstalledArtifact(discovered.sourceJar(),
                        classify(host, discovered.sourceJar()));
                if (result.putIfAbsent(id, artifact) != null) {
                    throw new IllegalStateException("duplicate reasoning provider identity " + id);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Path developmentSource(Properties properties, ReasoningProviderId id) {
        String value = properties.getProperty("reasoning.directory");
        if (value == null || value.isBlank()) return null;
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(List.of(Path.of(value.strip()).toAbsolutePath().normalize()))) {
            return loader.discoveredProviders().stream()
                    .filter(item -> item.provider().descriptor().id().equals(id))
                    .map(InstalledReasoningLoader.DiscoveredProvider::sourceJar).findFirst().orElse(null);
        }
    }

    private static List<ReasoningConfiguredInstance> configured(ReasoningMechanismProvider provider,
            ReasoningProviderConfiguration configuration) {
        var instances = provider.configurator().configuredInstances(configuration);
        if (instances == null) throw new IllegalStateException("reasoning provider returned null configured-instance list");
        return instances.stream().map(Objects::requireNonNull).toList();
    }

    private static Properties purge(ReasoningMechanismProvider provider, Properties source) {
        Properties result = copy(source);
        for (var instance : configured(provider, ReasoningConfigurationManager.configuration(result))) {
            var update = Objects.requireNonNull(provider.configurator().remove(instance.name(), ReasoningConfigurationManager.configuration(result)));
            update.removals().forEach(result::remove);
            update.values().forEach(result::setProperty);
        }
        if (!configured(provider, ReasoningConfigurationManager.configuration(result)).isEmpty()) {
            throw new IllegalStateException("reasoning provider still has configured instances after purge");
        }
        return result;
    }

    private static Properties copy(Properties source) {
        Properties result = new Properties();
        source.stringPropertyNames().forEach(name -> result.setProperty(name, source.getProperty(name)));
        return result;
    }

    private static void replace(Properties destination, Properties source) {
        destination.clear();
        source.forEach(destination::put);
    }

    private static void restore(Path path, Properties destination, Properties previous, Throwable failure) {
        try {
            HostEnvironment.replaceConfiguration(path, previous);
            replace(destination, previous);
        } catch (IOException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    enum Source {
        SHIPPED("shipped"), OWNER("owner"), DEVELOPMENT("development");
        private final String label;
        Source(String label) { this.label = label; }
        String label() { return label; }
    }

    record InstallResult(ReasoningProviderId providerId, Path path, String sha256, boolean replacement) { }
    record UninstallResult(ReasoningProviderId providerId, Path path, boolean configurationPurged) { }
    private record InstalledArtifact(Path path, Source source) { }
}
