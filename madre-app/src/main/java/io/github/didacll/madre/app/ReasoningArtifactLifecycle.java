package io.github.didacll.madre.app;

import io.github.didacll.madre.reasoning.installation.ReasoningConfiguredInstance;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanismProvider;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfigurationUpdate;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
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
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(properties, "properties");
        Path candidateJar = ManagedJarFiles.requireSourceJar(source);
        ReasoningProviderId candidateId = inspectCandidate(candidateJar, properties);

        Map<ReasoningProviderId, InstalledArtifact> installed = installed(host);
        InstalledArtifact existing = installed.get(candidateId);
        if (existing != null && existing.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("reasoning provider " + candidateId
                    + " is shipped with MADRE and cannot be overridden by an owner artifact");
        }
        if (replace) {
            if (existing == null || existing.source() != Source.OWNER) {
                throw new IllegalArgumentException(
                        "--replace requires an existing owner-installed reasoning provider: "
                                + candidateId);
            }
        } else if (existing != null) {
            throw new IllegalArgumentException("reasoning provider is already owner-installed: "
                    + candidateId + "; use --replace to replace the same canonical identity");
        }

        Path ownerDirectory = host.ownerReasoningDirectory().toAbsolutePath().normalize();
        Path destination = existing == null
                ? ownerDirectory.resolve(ManagedJarFiles.managedFileName("reasoning", candidateId.value()))
                : existing.path();
        if (existing == null && Files.exists(destination)) {
            throw new IllegalStateException("managed reasoning destination already exists without the "
                    + "expected provider identity: " + destination.getFileName());
        }

        String digest;
        Path staged = ManagedJarFiles.stage(candidateJar, ownerDirectory);
        try {
            ReasoningProviderId stagedId = inspectCandidate(staged, properties);
            if (!stagedId.equals(candidateId)) {
                throw new IllegalStateException("staged reasoning provider identity changed from "
                        + candidateId + " to " + stagedId);
            }
            digest = ManagedJarFiles.sha256(staged);
            ManagedJarFiles.commit(staged, destination);
        } finally {
            Files.deleteIfExists(staged);
        }
        return new InstallResult(candidateId, destination, digest, replace);
    }

    static UninstallResult uninstall(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, String providerId, boolean purgeConfiguration) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(properties, "properties");
        ReasoningProviderId id = new ReasoningProviderId(providerId);
        InstalledArtifact artifact = installed(host).get(id);
        if (artifact == null) {
            Path external = developmentSource(properties, id);
            if (external != null) {
                throw new IllegalArgumentException("reasoning provider " + id + " is discovered from "
                        + external + " outside MADRE's owner-managed reasoning root; remove that "
                        + "developer artifact manually if intended");
            }
            throw new IllegalArgumentException("reasoning provider is not installed: " + id);
        }
        if (artifact.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("shipped reasoning provider cannot be uninstalled: " + id);
        }
        if (artifact.source() != Source.OWNER) {
            throw new IllegalArgumentException(
                    "reasoning provider is not in MADRE's owner-managed reasoning root: " + id);
        }

        Properties previous = copy(properties);
        Properties purged = previous;
        List<ReasoningConfiguredInstance> configured;
        try (InstalledReasoningLoader loader = InstalledReasoningLoader.forJar(artifact.path())) {
            ReasoningMechanismProvider provider = loader.provider(id).orElseThrow(() ->
                    new IllegalStateException("owner reasoning artifact no longer exposes provider " + id));
            configured = configured(provider, ReasoningConfigurationManager.configuration(properties));
            if (!configured.isEmpty() && purgeConfiguration) {
                purged = purge(provider, previous);
            }
        }
        if (!configured.isEmpty() && !purgeConfiguration) {
            throw new IllegalArgumentException("reasoning provider " + id + " has configured instances ("
                    + configured.stream().map(ReasoningConfiguredInstance::name).sorted()
                            .reduce((left, right) -> left + "," + right).orElse("")
                    + "); rerun uninstall with --purge-configuration to remove provider-owned "
                    + "instance configuration before uninstalling");
        }

        boolean configurationChanged = !configured.isEmpty() && purgeConfiguration;
        if (configurationChanged) {
            HostEnvironment.replaceConfiguration(loaded.path(), purged);
            replaceProperties(properties, purged);
        }
        try {
            Path owner = host.ownerReasoningDirectory().toAbsolutePath().normalize();
            if (!artifact.path().getParent().equals(owner)) {
                throw new IllegalStateException(
                        "refusing to delete reasoning artifact outside owner root: " + artifact.path());
            }
            Files.delete(artifact.path());
        } catch (IOException | RuntimeException failure) {
            if (configurationChanged) {
                try {
                    HostEnvironment.replaceConfiguration(loaded.path(), previous);
                    replaceProperties(properties, previous);
                } catch (IOException restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
        return new UninstallResult(id, artifact.path(), configurationChanged);
    }

    static Source classify(HostEnvironment host, Path source) {
        Path path = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        Path parent = path.getParent();
        Path owner = host.ownerReasoningDirectory().toAbsolutePath().normalize();
        Path shipped = host.reasoningDirectories().get(0).toAbsolutePath().normalize();
        if (owner.equals(parent)) return Source.OWNER;
        if (shipped.equals(parent)) return Source.SHIPPED;
        return Source.DEVELOPMENT;
    }

    private static ReasoningProviderId inspectCandidate(Path jar, Properties properties) {
        try (InstalledReasoningLoader reasoning = InstalledReasoningLoader.forJar(jar);
                InstalledModuleLoader modules = InstalledModuleLoader.forJar(jar)) {
            List<InstalledReasoningLoader.DiscoveredProvider> reasoningProviders =
                    reasoning.discoveredProviders();
            boolean modulePresent = !modules.providers().isEmpty();
            if (reasoningProviders.isEmpty()) {
                if (modulePresent) {
                    throw new IllegalArgumentException(
                            "Module artifact cannot be installed as a reasoning provider");
                }
                throw new IllegalArgumentException(
                        "reasoning artifact exposes no ReasoningMechanismProvider");
            }
            if (reasoningProviders.size() != 1) {
                throw new IllegalArgumentException(
                        "managed reasoning artifact must expose exactly one ReasoningMechanismProvider");
            }
            if (modulePresent) {
                throw new IllegalArgumentException(
                        "managed reasoning artifact cannot also expose a ModuleProvider");
            }
            ReasoningMechanismProvider provider = reasoningProviders.getFirst().provider();
            ReasoningProviderDescriptor descriptor = Objects.requireNonNull(provider.descriptor(),
                    "reasoning provider descriptor");
            Objects.requireNonNull(provider.configurator(),
                    "reasoning provider configurator for " + descriptor.id());
            configured(provider, ReasoningConfigurationManager.configuration(properties));
            return descriptor.id();
        }
    }

    private static Map<ReasoningProviderId, InstalledArtifact> installed(HostEnvironment host) {
        Map<ReasoningProviderId, InstalledArtifact> result = new TreeMap<>();
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(host.reasoningDirectories())) {
            for (InstalledReasoningLoader.DiscoveredProvider discovered : loader.discoveredProviders()) {
                ReasoningProviderId id = Objects.requireNonNull(discovered.provider().descriptor(),
                        "reasoning provider descriptor").id();
                InstalledArtifact artifact = new InstalledArtifact(id, discovered.sourceJar(),
                        classify(host, discovered.sourceJar()));
                InstalledArtifact previous = result.putIfAbsent(id, artifact);
                if (previous != null) {
                    throw new IllegalStateException("duplicate reasoning provider identity " + id);
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Path developmentSource(Properties properties, ReasoningProviderId id) {
        String configured = properties.getProperty("reasoning.directory");
        if (configured == null || configured.isBlank()) return null;
        Path directory = Path.of(configured.strip()).toAbsolutePath().normalize();
        try (InstalledReasoningLoader loader = new InstalledReasoningLoader(List.of(directory))) {
            return loader.discoveredProviders().stream()
                    .filter(item -> item.provider().descriptor().id().equals(id))
                    .map(InstalledReasoningLoader.DiscoveredProvider::sourceJar)
                    .findFirst().orElse(null);
        }
    }

    private static List<ReasoningConfiguredInstance> configured(ReasoningMechanismProvider provider,
            ReasoningProviderConfiguration configuration) {
        List<ReasoningConfiguredInstance> instances = provider.configurator()
                .configuredInstances(configuration);
        if (instances == null) {
            throw new IllegalStateException("reasoning provider " + provider.descriptor().id()
                    + " returned null configured-instance list");
        }
        return instances.stream().map(instance -> Objects.requireNonNull(instance,
                "reasoning provider returned null configured instance")).toList();
    }

    private static Properties purge(ReasoningMechanismProvider provider, Properties source) {
        Properties candidate = copy(source);
        List<ReasoningConfiguredInstance> instances = configured(provider,
                ReasoningConfigurationManager.configuration(candidate));
        for (ReasoningConfiguredInstance instance : instances) {
            ReasoningProviderConfigurationUpdate update = Objects.requireNonNull(
                    provider.configurator().remove(instance.name(),
                            ReasoningConfigurationManager.configuration(candidate)),
                    "reasoning provider configurator returned null removal update");
            apply(candidate, update);
        }
        List<ReasoningConfiguredInstance> remaining = configured(provider,
                ReasoningConfigurationManager.configuration(candidate));
        if (!remaining.isEmpty()) {
            throw new IllegalStateException("reasoning provider " + provider.descriptor().id()
                    + " still reports configured instances after provider-owned purge");
        }
        return candidate;
    }

    private static void apply(Properties properties, ReasoningProviderConfigurationUpdate update) {
        update.removals().forEach(properties::remove);
        update.values().forEach(properties::setProperty);
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        source.stringPropertyNames().forEach(name -> copy.setProperty(name, source.getProperty(name)));
        return copy;
    }

    private static void replaceProperties(Properties destination, Properties source) {
        destination.clear();
        source.forEach(destination::put);
    }

    enum Source {
        SHIPPED("shipped"), OWNER("owner"), DEVELOPMENT("development");
        private final String label;
        Source(String label) { this.label = label; }
        String label() { return label; }
    }

    record InstallResult(ReasoningProviderId providerId, Path path, String sha256,
            boolean replacement) { }
    record UninstallResult(ReasoningProviderId providerId, Path path,
            boolean configurationPurged) { }
    private record InstalledArtifact(ReasoningProviderId id, Path path, Source source) { }
}
