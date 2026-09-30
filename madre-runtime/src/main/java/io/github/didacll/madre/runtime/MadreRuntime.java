package io.github.didacll.madre.runtime;

import io.github.didacll.madre.kernel.client.EngineDescriptor;
import io.github.didacll.madre.sdk.MADREModule;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Installed Module discovery and configuration, with no Agent or Operation policy. */
public final class MadreRuntime implements AutoCloseable {
    private final RuntimeInstallation installation;
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private List<ModuleInfo> modules = List.of();
    private Map<String, MADREModule> available = Map.of();

    public MadreRuntime(Path home) throws IOException {
        installation = new RuntimeInstallation(home);
        discover();
    }

    public RuntimeInstallation installation() { return installation; }

    public String install(Path jar) throws IOException {
        String artifact = installation.installArtifact(jar);
        discover();
        return artifact;
    }

    public void remove(String artifact) throws IOException {
        closeLoaders();
        installation.removeArtifact(artifact);
        discover();
    }

    public void assignCore(String moduleId) throws IOException {
        if (!available.containsKey(moduleId)) {
            throw new IllegalArgumentException("No live Module with identity " + moduleId);
        }
        installation.assignCore(moduleId);
    }

    public Optional<String> core() throws IOException { return installation.core(); }

    public List<ModuleInfo> modules() { return modules; }

    public Optional<MADREModule> module(String id) {
        return Optional.ofNullable(available.get(Objects.requireNonNull(id, "id")));
    }

    public List<EngineDescriptor> engines() throws IOException { return installation.engines(); }

    /** Re-read JARs; each JAR supplies one Module through the standard Java service entry. */
    public void discover() throws IOException {
        closeLoaders();
        List<ModuleInfo> found = new ArrayList<>();
        Map<String, MADREModule> loaded = new LinkedHashMap<>();
        for (String artifact : installation.artifacts()) {
            Path path = installation.home().resolve("artifacts").resolve(artifact);
            URLClassLoader loader = null;
            try {
                URL url = path.toUri().toURL();
                URLClassLoader moduleLoader = new URLClassLoader(new URL[] {url},
                        MADREModule.class.getClassLoader());
                loader = moduleLoader;
                List<ServiceLoader.Provider<MADREModule>> providers = ServiceLoader
                        .load(MADREModule.class, moduleLoader).stream()
                        .filter(provider -> provider.type().getClassLoader() == moduleLoader).toList();
                if (providers.size() != 1) {
                    throw new IllegalArgumentException("JAR must provide exactly one MADREModule");
                }
                MADREModule module = Objects.requireNonNull(providers.getFirst().get(), "Module");
                String id = Objects.requireNonNull(module.id(), "Module identity");
                if (id.isBlank() || loaded.containsKey(id)) {
                    throw new IllegalArgumentException("Blank or duplicate Module identity");
                }
                int agents = Objects.requireNonNull(module.agents(), "agents").size();
                int operations = Objects.requireNonNull(module.operations(), "operations").size();
                loaders.add(loader);
                loaded.put(id, module);
                found.add(new ModuleInfo(artifact, id, true, agents, operations, ""));
            } catch (RuntimeException | ServiceConfigurationError failure) {
                if (loader != null) loader.close();
                found.add(new ModuleInfo(artifact, "", false, 0, 0,
                        failure.getClass().getSimpleName()));
            }
        }
        modules = List.copyOf(found);
        available = Map.copyOf(loaded);
    }

    @Override public void close() throws IOException { closeLoaders(); }

    private void closeLoaders() throws IOException {
        IOException first = null;
        for (URLClassLoader loader : loaders) {
            try { loader.close(); }
            catch (IOException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        loaders.clear();
        modules = List.of();
        available = Map.of();
        if (first != null) throw first;
    }

    /** Inspection facts only; Module behavior and state remain its own. */
    public record ModuleInfo(String artifact, String id, boolean available,
                             int agentCount, int operationCount, String diagnostic) { }
}
