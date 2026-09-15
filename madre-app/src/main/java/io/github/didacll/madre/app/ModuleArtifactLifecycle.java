package io.github.didacll.madre.app;

import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Host-owned local-file lifecycle for independently packaged Module JARs. */
final class ModuleArtifactLifecycle {
    private static final String CONFIGURATION_PREFIX = "modules.config[";

    private ModuleArtifactLifecycle() { }

    static InstallResult install(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, Path source, boolean replace) throws IOException {
        Objects.requireNonNull(loaded, "loaded");
        Objects.requireNonNull(properties, "properties");
        Path candidate = ManagedJarFiles.requireSourceJar(source);
        ModuleId id = inspectCandidate(candidate, properties);
        InstalledArtifact existing = installed(host).get(id.value());
        if (existing != null && existing.source() == Source.SHIPPED) {
            throw new IllegalArgumentException("Module " + id
                    + " is shipped with MADRE and cannot be overridden by an owner artifact");
        }
        if (existing != null && existing.source() == Source.MANUAL) {
            throw new IllegalArgumentException("Module " + id + " is discovered from a manually placed owner JAR ("
                    + existing.path().getFileName() + "); remove that file manually before managed installation");
        }

        Path owner = host.ownerModuleDirectory().toAbsolutePath().normalize();
        if (replace) {
            if (existing == null || existing.source() != Source.OWNER) {
                throw new IllegalArgumentException(
                        "--replace requires an existing owner-installed Module: " + id);
            }
            ManagedJarFiles.requireManagedPath(owner, "module", id.value(), existing.path());
        } else if (existing != null) {
            throw new IllegalArgumentException("Module is already owner-installed: " + id
                    + "; use --replace to replace the same canonical identity");
        }

        Path destination = ManagedJarFiles.managedPath(owner, "module", id.value());
        if (existing == null && Files.exists(destination)) {
            throw new IllegalStateException("managed Module destination already exists without the expected "
                    + "provider identity: " + destination.getFileName());
        }

        Path staged = ManagedJarFiles.stage(candidate, owner);
        try {
            ModuleId stagedId = inspectCandidate(staged, properties);
            if (!stagedId.equals(id)) {
                throw new IllegalStateException("staged Module identity changed from " + id
                        + " to " + stagedId);
            }
            String digest = ManagedJarFiles.sha256(staged);
            ManagedJarFiles.commit(staged, destination);
            return new InstallResult(id, destination, digest, replace);
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    static UninstallResult uninstall(HostEnvironment host, HostEnvironment.LoadedConfiguration loaded,
            Properties properties, String moduleId, boolean purgeConfiguration) throws IOException {
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
        if (artifact.source() == Source.MANUAL) {
            throw new IllegalArgumentException("Module " + id + " is discovered from a manually placed owner JAR ("
                    + artifact.path().getFileName() + "); MADRE will not delete it. Remove that file manually if intended");
        }
        if (artifact.source() != Source.OWNER) {
            throw new IllegalArgumentException("Module is not in MADRE's owner-managed Module root: " + id);
        }
        ManagedJarFiles.requireManagedPath(host.ownerModuleDirectory(), "module", id.value(),
                artifact.path());

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
        boolean changed = hasConfiguration && purgeConfiguration;
        if (changed) {
            Properties candidate = copy(properties);
            candidate.stringPropertyNames().stream().filter(name -> name.startsWith(prefix)).toList()
                    .forEach(candidate::remove);
            HostEnvironment.replaceConfiguration(loaded.path(), candidate);
            replaceProperties(properties, candidate);
        }
        try {
            Files.delete(artifact.path());
        } catch (IOException | RuntimeException failure) {
            if (changed) restoreConfiguration(loaded.path(), properties, previous, failure);
            throw failure;
        }
        return new UninstallResult(id, artifact.path(), changed);
    }

    static Source classify(HostEnvironment host, ModuleId id, Path source) {
        Path path = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        Path parent = path.getParent();
        Path owner = host.ownerModuleDirectory().toAbsolutePath().normalize();
        if (host.moduleDirectories().getFirst().toAbsolutePath().normalize().equals(parent)) {
            return Source.SHIPPED;
        }
        if (owner.equals(parent)) {
            return ManagedJarFiles.isManagedPath(owner, "module", id.value(), path)
                    ? Source.OWNER : Source.MANUAL;
        }
        return Source.DEVELOPMENT;
    }

    private static ModuleId inspectCandidate(Path jar, Properties properties) {
        try (InstalledModuleLoader modules = InstalledModuleLoader.forJar(jar);
                InstalledReasoningLoader reasoning = InstalledReasoningLoader.forJar(jar)) {
            List<ModuleProvider> providers = modules.providers();
            boolean reasoningPresent = !reasoning.providerDescriptors().isEmpty();
            if (providers.isEmpty()) {
                if (reasoningPresent) {
                    throw new IllegalArgumentException("reasoning-provider artifact cannot be installed as a Module");
                }
                throw new IllegalArgumentException("Module artifact exposes no ModuleProvider");
            }
            if (providers.size() != 1) {
                throw new IllegalArgumentException("managed Module artifact must expose exactly one ModuleProvider");
            }
            if (reasoningPresent) {
                throw new IllegalArgumentException("managed Module artifact cannot also expose a reasoning provider");
            }

            ModuleProvider provider = providers.getFirst();
            ModuleId id = Objects.requireNonNull(provider.moduleId(), "ModuleProvider.moduleId()");
            var descriptor = Objects.requireNonNull(provider.configurationDescriptor(),
                    "ModuleProvider.configurationDescriptor()");
            if (!descriptor.moduleId().equals(id)) {
                throw new IllegalStateException("Module configuration descriptor identity "
                        + descriptor.moduleId() + " does not match provider identity " + id);
            }
            ModuleProviderConfiguration validated = Objects.requireNonNull(
                    provider.validateConfiguration(configuration(properties, id)),
                    "ModuleProvider.validateConfiguration() returned null");
            if (!validated.moduleId().equals(id)) {
                throw new IllegalStateException("Module configuration validation escaped identity " + id
                        + " to " + validated.moduleId());
            }
            Set<String> declared = descriptor.fields().stream().map(field -> field.name())
                    .collect(Collectors.toSet());
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
            for (var discovered : loader.discoveredProviders()) {
                ModuleId id = Objects.requireNonNull(discovered.provider().moduleId(),
                        "ModuleProvider.moduleId()");
                InstalledArtifact artifact = new InstalledArtifact(id, discovered.sourceJar(),
                        classify(host, id, discovered.sourceJar()));
                if (result.putIfAbsent(id.value(), artifact) != null) {
                    throw new IllegalStateException("duplicate ModuleProvider identity: " + id.value());
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Path developmentSource(Properties properties, ModuleId id) {
        String configured = properties.getProperty("modules.directory");
        if (configured == null || configured.isBlank()) return null;
        try (InstalledModuleLoader loader = new InstalledModuleLoader(
                List.of(Path.of(configured.strip()).toAbsolutePath().normalize()))) {
            return loader.discoveredProviders().stream()
                    .filter(item -> item.provider().moduleId().equals(id))
                    .map(InstalledModuleLoader.DiscoveredProvider::sourceJar).findFirst().orElse(null);
        }
    }

    private static ModuleProviderConfiguration configuration(Properties properties, ModuleId id) {
        String prefix = prefix(id);
        Map<String, String> values = new TreeMap<>();
        properties.stringPropertyNames().stream().filter(name -> name.startsWith(prefix)).sorted()
                .forEach(name -> values.put(name.substring(prefix.length()), properties.getProperty(name)));
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

    private static void restoreConfiguration(Path path, Properties destination, Properties previous,
            Throwable failure) {
        try {
            HostEnvironment.replaceConfiguration(path, previous);
            replaceProperties(destination, previous);
        } catch (IOException restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
    }

    enum Source {
        SHIPPED("shipped"), OWNER("owner"), MANUAL("manual"), DEVELOPMENT("development");
        private final String label;
        Source(String label) { this.label = label; }
        String label() { return label; }
    }

    record InstallResult(ModuleId moduleId, Path path, String sha256, boolean replacement) { }
    record UninstallResult(ModuleId moduleId, Path path, boolean configurationPurged) { }
    private record InstalledArtifact(ModuleId id, Path path, Source source) { }
}
