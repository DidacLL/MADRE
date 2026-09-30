package io.github.didacll.madre.runtime;

import io.github.didacll.madre.kernel.client.EngineDescriptor;
import io.github.didacll.madre.sdk.MADREAgent;
import io.github.didacll.madre.sdk.MADREModule;
import io.github.didacll.madre.sdk.ModuleOperation;

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
    private Map<String, Map<String, MADREAgent>> agents = Map.of();
    private Map<String, Map<String, ModuleOperation>> operations = Map.of();

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

    public Optional<MADREAgent> agent(String moduleId, String agentId) {
        return Optional.ofNullable(agents.getOrDefault(moduleId, Map.of()).get(agentId));
    }

    public Optional<ModuleOperation> operation(String moduleId, String operationId) {
        return Optional.ofNullable(operations.getOrDefault(moduleId, Map.of()).get(operationId));
    }

    public List<EngineDescriptor> engines() throws IOException { return installation.engines(); }

    /** Re-read JARs; each JAR supplies one Module through the standard Java service entry. */
    public void discover() throws IOException {
        closeLoaders();
        List<ModuleInfo> found = new ArrayList<>();
        Map<String, MADREModule> loaded = new LinkedHashMap<>();
        Map<String, Map<String, MADREAgent>> foundAgents = new LinkedHashMap<>();
        Map<String, Map<String, ModuleOperation>> foundOperations = new LinkedHashMap<>();
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
                Map<String, MADREAgent> moduleAgents = new LinkedHashMap<>();
                for (MADREAgent agent : Objects.requireNonNull(module.agents(), "agents")) {
                    String agentId = Objects.requireNonNull(agent.id(), "Agent identity");
                    if (agentId.isBlank() || moduleAgents.putIfAbsent(agentId, agent) != null) {
                        throw new IllegalArgumentException("Blank or duplicate Agent identity");
                    }
                }
                Map<String, ModuleOperation> moduleOperations = new LinkedHashMap<>();
                for (ModuleOperation operation : Objects.requireNonNull(module.operations(), "operations")) {
                    String operationId = Objects.requireNonNull(operation.id(), "Operation identity");
                    if (operationId.isBlank() || moduleOperations.putIfAbsent(operationId, operation) != null) {
                        throw new IllegalArgumentException("Blank or duplicate Operation identity");
                    }
                }
                loaders.add(loader);
                loaded.put(id, module);
                foundAgents.put(id, Map.copyOf(moduleAgents));
                foundOperations.put(id, Map.copyOf(moduleOperations));
                found.add(new ModuleInfo(artifact, id, true,
                        List.copyOf(moduleAgents.keySet()), List.copyOf(moduleOperations.keySet()), ""));
            } catch (RuntimeException | ServiceConfigurationError failure) {
                if (loader != null) loader.close();
                found.add(new ModuleInfo(artifact, "", false, List.of(), List.of(),
                        failure.toString()));
            }
        }
        modules = List.copyOf(found);
        available = Map.copyOf(loaded);
        agents = Map.copyOf(foundAgents);
        operations = Map.copyOf(foundOperations);
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
        agents = Map.of();
        operations = Map.of();
        if (first != null) throw first;
    }

    /** Inspection facts only; Module behavior and state remain its own. */
    public record ModuleInfo(String artifact, String id, boolean available,
                             List<String> agentIds, List<String> operationIds, String diagnostic) { }
}
