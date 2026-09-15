package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.registration.ModuleConfigurationDescriptor;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/** Host-owned local-file lifecycle for independently packaged Module JARs. */
final class ModuleArtifactLifecycle {
    private static final String CONFIGURATION_PREFIX = "modules.config[";

    private ModuleArtifactLifecycle() { }

    static InstallResult install(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, Path source, boolean replace) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(properties, "properties");
        Path candidateJar = ManagedJarFiles.requireSourceJar(source);
        ModuleId candidateId = inspectCandidate(candidateJar, properties);

        Map<String, InstalledArtifact> installed = installed(host);
        InstalledArtifact existing = installed.get(candidateId.value());
        if (existing != null && existing.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("Module " + candidateId
                    + " is shipped with MADRE and cannot be overridden by an owner artifact");
        }
        if (replace) {
            if (existing == null || existing.source() != Source.OWNER) {
                throw new IllegalArgumentException("--replace requires an existing owner-installed Module: "
                        + candidateId);
            }
        } else if (existing != null) {
            throw new IllegalArgumentException("Module is already owner-installed: " + candidateId
                    + "; use --replace to replace the same canonical identity");
        }

        Path ownerDirectory = host.ownerModuleDirectory().toAbsolutePath().normalize();
        Path destination = existing == null
                ? ownerDirectory.resolve(ManagedJarFiles.managedFileName("module", candidateId.value()))
                : existing.path();
        if (existing == null && Files.exists(destination)) {
            throw new IllegalStateException("managed Module destination already exists without the expected "
                    + "provider identity: " + destination.getFileName());
        }

        Path staged = ManagedJarFiles.stage(candidateJar, ownerDirectory);
        final String digest;
        try {
            ModuleId stagedId = inspectCandidate(staged, properties);
            if (!stagedId.equals(candidateId)) {
                throw new IllegalStateException("staged Module identity changed from " + candidateId
                        + " to " + stagedId);
            }
            digest = ManagedJarFiles.sha256(staged);
            ManagedJarFiles.commit(staged, destination);
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        return new InstallResult(candidateId, destination, digest, replace);
    }

    static UninstallResult uninstall(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, String moduleId, boolean purgeConfiguration) throws IOException {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(properties, "properties");
        ModuleId id = new ModuleId(moduleId);
        InstalledArtifact artifact = installed(host).get(id.value());
        if (artifact == null) {
            Path external = developmentSource(properties, id);
            if (external != null) {
                throw new IllegalArgumentException("Module " + id + " is discovered from " + external
                        + " outside MADRE's owner-managed Module root; remove that developer artifact "
                        + "manually if intended");
            }
            throw new IllegalArgumentException("Module is not installed: " + id);
        }
        if (artifact.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("shipped Module cannot be uninstalled: " + id);
        }
        if (artifact.source() != Source.OWNER) {
            throw new IllegalArgumentException("Module is not in MADRE's owner-managed Module root: " + id);
        }

        String interaction = properties.getProperty("interaction.module");
        if (interaction != null && interaction.strip().equals(id.value())) {
            throw new IllegalArgumentException("Module " + id
                    + " is required by interaction.module; change the interaction binding before uninstalling it");
        }

        String prefix = prefix(id);
        boolean hasConfiguration = properties.stringPropertyNames().stream()
                .anyMatch(name -> name.startsWith(prefix));
        if (hasConfiguration && !purgeConfiguration) {
            throw new IllegalArgumentException("Module " + id
                    + " has retained owner configuration; rerun uninstall with --purge-configuration "
                    + "to remove only modules.config[" + id + "].* while uninstalling");
        }

        Properties previous = copy(properties);
        boolean configurationChanged = hasConfiguration && purgeConfiguration;
        if (configurationChanged) {
            Properties candidate = copy(properties);
            candidate.stringPropertyNames().stream().filter(name -> name.startsWith(prefix)).toList()
                    .forEach(candidate::remove);
            HostEnvironment.replaceConfiguration(loaded.path(), candidate);
            replaceProperties(properties, candidate);
        }
        try {
            Path owner = host.ownerModuleDirectory().toAbsolutePath().normalize();
            if (!artifact.path().getParent().equals(owner)) {
                throw new IllegalStateException("refusing to delete Module artifact outside owner root: "
                        + artifact.path());
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
        Path owner = host.ownerModuleDirectory().toAbsolutePath().normalize();
        Path shipped = host.moduleDirectories().get(0).toAbsolutePath().normalize();
        if (owner.equals(parent)) return Source.OWNER;
        if (shipped.equals(parent)) return Source.SHIPPED;
        return Source.DEVELOPMENT;
    }

    private static ModuleId inspectCandidate(Path jar, Properties properties) {
        try (InstalledModuleLoader modules = InstalledModuleLoader.forJar(jar);
                InstalledReasoningLoader reasoning = InstalledReasoningLoader.forJar(jar)) {
            List<ModuleProvider> moduleProviders = modules.providers();
            boolean reasoningPresent = !reasoning.providerDescriptors().isEmpty();
            if (moduleProviders.isEmpty()) {
                if (reasoningPresent) {
                    throw new IllegalArgumentException("reasoning-provider artifact cannot be installed as a Module");
                }
                throw new IllegalArgumentException("Module artifact exposes no ModuleProvider");
            }
            if (moduleProviders.size() != 1) {
                throw new IllegalArgumentException("managed Module artifact must expose exactly one ModuleProvider");
            }
            if (reasoningPresent) {
                throw new IllegalArgumentException(
                        "managed Module artifact cannot also expose a reasoning provider");
            }
            ModuleProvider provider = moduleProviders.getFirst();
            ModuleId id = Objects.requireNonNull(provider.moduleId(), "ModuleProvider.moduleId()");
            ModuleConfigurationDescriptor descriptor = Objects.requireNonNull(
                    provider.configurationDescriptor(), "ModuleProvider.configurationDescriptor()");
            if (!descriptor.moduleId().equals(id)) {
                throw new IllegalStateException("Module configuration descriptor identity "
                        + descriptor.moduleId() + " does not match provider identity " + id);
            }
            ModuleProviderConfiguration current = configuration(properties, id);
            ModuleProviderConfiguration validated = Objects.requireNonNull(
                    provider.validateConfiguration(current),
                    "ModuleProvider.validateConfiguration() returned null");
            if (!validated.moduleId().equals(id)) {
                throw new IllegalStateException("Module configuration validation escaped identity " + id
                        + " to " + validated.moduleId());
            }
            Set<String> declared = new HashSet<>();
            descriptor.fields().forEach(field -> declared.add(field.name()));
            validated.keys().stream().filter(name -> !declared.contains(name)).findFirst()
                    .ifPresent(name -> {
                        throw new IllegalStateException(
                                "Module configuration validation returned undeclared field: " + name);
                    });
            return id;
        }
    }

    private static Map<String, InstalledArtifact> installed(HostEnvironment host) {
        Map<String, InstalledArtifact> result = new TreeMap<>();
        try (InstalledModuleLoader loader = new InstalledModuleLoader(host.moduleDirectories())) {
            for (InstalledModuleLoader.DiscoveredProvider discovered : loader.discoveredProviders()) {
                ModuleId id = Objects.requireNonNull(discovered.provider().moduleId(),
                        "ModuleProvider.moduleId()");
                InstalledArtifact artifact = new InstalledArtifact(id, discovered.sourceJar(),
                        classify(host, discovered.sourceJar()));
                InstalledArtifact previous = result.putIfAbsent(id.value(), artifact);
                if (previous != null) {
                    throw new IllegalStateException("duplicate ModuleProvider identity: " + id.value());
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Path developmentSource(Properties properties, ModuleId id) {
        String configured = properties.getProperty("modules.directory");
        if (configured == null || configured.isBlank()) return null;
        Path directory = Path.of(configured.strip()).toAbsolutePath().normalize();
        try (InstalledModuleLoader loader = new InstalledModuleLoader(List.of(directory))) {
            return loader.discoveredProviders().stream()
                    .filter(item -> item.provider().moduleId().equals(id))
                    .map(InstalledModuleLoader.DiscoveredProvider::sourceJar).findFirst().orElse(null);
        }
    }

    private static ModuleProviderConfiguration configuration(Properties properties, ModuleId id) {
        String prefix = prefix(id);
        Map<String, String> values = new TreeMap<>();
        properties.stringPropertyNames().stream().filter(name -> name.startsWith(prefix)).sorted()
                .forEach(name -> values.put(name.substring(prefix.length()),
                        properties.getProperty(name)));
        return new ModuleProviderConfiguration(id, values);
    }

    private static String prefix(ModuleId id) {
        return CONFIGURATION_PREFIX + id.value() + "].";
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

    record InstallResult(ModuleId moduleId, Path path, String sha256, boolean replacement) { }
    record UninstallResult(ModuleId moduleId, Path path, boolean configurationPurged) { }
    private record InstalledArtifact(ModuleId id, Path path, Source source) { }
}
