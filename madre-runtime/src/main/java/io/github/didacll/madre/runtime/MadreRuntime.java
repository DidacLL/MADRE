package io.github.didacll.madre.runtime;

import io.github.didacll.madre.kernel.client.EngineDescriptor;
import io.github.didacll.madre.sdk.MADREAgent;
import io.github.didacll.madre.sdk.MADREModule;
import io.github.didacll.madre.sdk.ModuleEnvironment;
import io.github.didacll.madre.sdk.ModuleOperation;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Live installed Module environment. It does not interpret Module work. */
public final class MadreRuntime implements AutoCloseable {
    private final RuntimeInstallation installation;
    private final RuntimeWakeups wakeups;
    private final Path runtimeCopies;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private final Map<String, Loaded> loaded = new LinkedHashMap<>();
    private final Map<String, Failure> failures = new HashMap<>();
    private final Map<String, Instant> retryAfter = new HashMap<>();
    private String lastInspection = "";
    private volatile boolean closed;

    public MadreRuntime(Path home) throws IOException {
        installation = new RuntimeInstallation(home);
        wakeups = new RuntimeWakeups(installation.home());
        runtimeCopies = installation.home().resolve("runtime-load");
        Files.createDirectories(runtimeCopies);
        lockChannel = FileChannel.open(installation.home().resolve("runtime.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
        } catch (IOException | OverlappingFileLockException occupied) {
            lockChannel.close();
            throw new IOException("Runtime installation is already active: " + installation.home(), occupied);
        }
        if (lock == null) {
            lockChannel.close();
            throw new IOException("Runtime installation is already active: " + installation.home());
        }
        try {
            try (DirectoryStream<Path> stale = Files.newDirectoryStream(runtimeCopies, "*.jar")) {
                for (Path copy : stale) Files.deleteIfExists(copy);
            }
            discover();
        } catch (IOException | RuntimeException failure) {
            try { close(); }
            catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    public RuntimeInstallation installation() { return installation; }

    public synchronized String install(Path jar) throws IOException {
        ensureOpen();
        String artifact = installation.installArtifact(jar);
        discover();
        return artifact;
    }

    public synchronized void remove(String artifact) throws IOException {
        ensureOpen();
        Loaded removed = loaded.remove(artifact);
        if (removed != null) removed.close();
        failures.clear();
        try {
            installation.removeArtifact(artifact);
        } finally {
            discover();
        }
    }

    public synchronized void assignCore(String moduleId) throws IOException {
        ensureOpen();
        if (module(moduleId).isEmpty()) {
            throw new IllegalArgumentException("No live Module with identity " + moduleId);
        }
        installation.assignCore(moduleId);
        writeInspection();
    }

    public Optional<String> core() throws IOException { return installation.core(); }

    public synchronized List<ModuleInfo> modules() throws IOException {
        ensureOpen();
        List<ModuleInfo> result = new ArrayList<>();
        for (String artifact : installation.artifacts()) {
            Loaded module = loaded.get(artifact);
            if (module != null) result.add(new ModuleInfo(artifact, module.id, true,
                    List.copyOf(module.agents.keySet()), List.copyOf(module.operations.keySet()), ""));
            else result.add(new ModuleInfo(artifact, "", false, List.of(), List.of(),
                    failures.containsKey(artifact) ? failures.get(artifact).message : "Not loaded"));
        }
        return List.copyOf(result);
    }

    public synchronized Optional<MADREModule> module(String id) {
        return loaded.values().stream().filter(item -> item.id.equals(id))
                .map(item -> item.module).findFirst();
    }

    public synchronized Optional<MADREAgent> agent(String moduleId, String agentId) {
        return loaded.values().stream().filter(item -> item.id.equals(moduleId))
                .map(item -> item.agents.get(agentId)).filter(Objects::nonNull).findFirst();
    }

    public synchronized Optional<ModuleOperation> operation(String moduleId, String operationId) {
        return loaded.values().stream().filter(item -> item.id.equals(moduleId))
                .map(item -> item.operations.get(operationId)).filter(Objects::nonNull).findFirst();
    }

    public List<EngineDescriptor> engines() throws IOException { return installation.engines(); }

    /** Detect added and removed artifacts without restarting unrelated Modules. */
    public synchronized void discover() throws IOException {
        ensureOpen();
        List<String> artifacts = installation.artifacts();
        IOException closeFailure = null;
        boolean removed = false;
        for (String artifact : List.copyOf(loaded.keySet())) {
            if (!artifacts.contains(artifact)) {
                removed = true;
                try { loaded.remove(artifact).close(); }
                catch (IOException failure) { closeFailure = failure; }
            }
        }
        if (failures.keySet().removeIf(artifact -> !artifacts.contains(artifact))) removed = true;
        if (removed) failures.clear(); // a duplicate identity may become available
        for (String artifact : artifacts) {
            if (loaded.containsKey(artifact)) continue;
            Path path = installation.home().resolve("artifacts").resolve(artifact);
            String stamp;
            try {
                stamp = Files.size(path) + ":" + Files.getLastModifiedTime(path);
            } catch (NoSuchFileException removedWhileScanning) {
                continue;
            }
            Failure previous = failures.get(artifact);
            if (previous != null && previous.stamp.equals(stamp)) continue;
            try {
                Loaded module = load(path);
                loaded.put(artifact, module);
                failures.remove(artifact);
            } catch (Exception | ServiceConfigurationError failure) {
                failures.put(artifact, new Failure(stamp, failure.toString()));
            }
        }
        writeInspection();
        if (closeFailure != null) throw closeFailure;
    }

    private void writeInspection() throws IOException {
        String core = installation.core().orElse("unassigned");
        StringBuilder report = new StringBuilder("CORE: ").append(core);
        if (!core.equals("unassigned") && module(core).isEmpty()) report.append(" (unavailable)");
        report.append(System.lineSeparator());
        for (ModuleInfo info : modules()) report.append(info).append(System.lineSeparator());
        String value = report.toString();
        if (value.equals(lastInspection)) return;
        Path target = installation.home().resolve("runtime-modules.txt");
        Path temporary = Files.createTempFile(installation.home(), ".modules-", ".tmp");
        try {
            Files.writeString(temporary, value);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            lastInspection = value;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Deliver due non-inference wakeups; the Module persists its own semantic effect. */
    public synchronized int runDue(Instant now) throws IOException {
        ensureOpen();
        int delivered = 0;
        for (RuntimeWakeups.Wakeup wakeup : wakeups.pending()) {
            if (wakeup.due().isAfter(now)) break;
            if (retryAfter.getOrDefault(wakeup.id(), Instant.MIN).isAfter(now)) continue;
            MADREModule recipient = module(wakeup.moduleId()).orElse(null);
            if (recipient == null) continue;
            try {
                recipient.onWakeup(wakeup.id(), wakeup.reference());
                wakeups.delivered(wakeup);
                retryAfter.remove(wakeup.id());
                delivered++;
            } catch (Exception failure) {
                wakeups.failed(wakeup, failure);
                retryAfter.put(wakeup.id(), now.plusSeconds(60));
            }
        }
        return delivered;
    }

    /** Inspection omits the Module-owned reference. */
    public synchronized List<WakeupInfo> pendingWakeups() throws IOException {
        ensureOpen();
        return wakeups.pending().stream().map(wakeup -> new WakeupInfo(wakeup.id(),
                wakeup.moduleId(), wakeup.due(), module(wakeup.moduleId()).isPresent(),
                wakeup.attempts(), wakeup.error())).toList();
    }

    private Loaded load(Path jar) throws Exception {
        Path copy = Files.createTempFile(runtimeCopies, "module-", ".jar");
        try {
            Files.copy(jar, copy, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            Files.deleteIfExists(copy);
            throw failure;
        }
        URLClassLoader loader = new URLClassLoader(new URL[] {copy.toUri().toURL()},
                MADREModule.class.getClassLoader());
        MADREModule module = null;
        try {
            List<ServiceLoader.Provider<MADREModule>> providers = ServiceLoader
                    .load(MADREModule.class, loader).stream()
                    .filter(provider -> provider.type().getClassLoader() == loader).toList();
            if (providers.size() != 1) {
                throw new IllegalArgumentException("JAR must provide exactly one MADREModule");
            }
            module = Objects.requireNonNull(providers.getFirst().get(), "Module");
            String id = Objects.requireNonNull(module.id(), "Module identity");
            if (id.isBlank() || this.module(id).isPresent()) {
                throw new IllegalArgumentException("Blank or duplicate Module identity: " + id);
            }
            Map<String, MADREAgent> agents = new LinkedHashMap<>();
            for (MADREAgent agent : Objects.requireNonNull(module.agents(), "agents")) {
                String agentId = Objects.requireNonNull(agent.id(), "Agent identity");
                if (agentId.isBlank() || agents.putIfAbsent(agentId, agent) != null) {
                    throw new IllegalArgumentException("Blank or duplicate Agent identity");
                }
            }
            Map<String, ModuleOperation> operations = new LinkedHashMap<>();
            for (ModuleOperation operation : Objects.requireNonNull(module.operations(), "operations")) {
                String operationId = Objects.requireNonNull(operation.id(), "Operation identity");
                if (operationId.isBlank() || operations.putIfAbsent(operationId, operation) != null) {
                    throw new IllegalArgumentException("Blank or duplicate Operation identity");
                }
            }
            InstalledEnvironment environment = new InstalledEnvironment(id,
                    installation.moduleDataDirectory(id));
            module.start(environment);
            return new Loaded(id, module, loader, copy, environment, Map.copyOf(agents),
                    Map.copyOf(operations));
        } catch (Exception | ServiceConfigurationError failure) {
            if (module != null) {
                try { module.close(); }
                catch (RuntimeException closeFailure) { failure.addSuppressed(closeFailure); }
            }
            try { loader.close(); }
            catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            try { Files.deleteIfExists(copy); }
            catch (IOException deleteFailure) { failure.addSuppressed(deleteFailure); }
            throw failure;
        }
    }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        IOException first = null;
        for (Loaded module : loaded.values()) {
            try { module.close(); }
            catch (IOException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
        }
        loaded.clear();
        try { lock.release(); }
        catch (IOException failure) {
            if (first == null) first = failure;
            else first.addSuppressed(failure);
        }
        try { lockChannel.close(); }
        catch (IOException failure) {
            if (first == null) first = failure;
            else first.addSuppressed(failure);
        }
        if (first != null) throw first;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Runtime is closed");
    }

    private final class InstalledEnvironment implements ModuleEnvironment {
        private final String moduleId;
        private final Path dataDirectory;
        private volatile boolean active = true;

        private InstalledEnvironment(String moduleId, Path dataDirectory) {
            this.moduleId = moduleId;
            this.dataDirectory = dataDirectory;
        }

        @Override public Path dataDirectory() { return dataDirectory; }

        @Override public String schedule(Instant due, String reference) throws IOException {
            if (!active || closed) throw new IllegalStateException("Module is not active");
            return wakeups.schedule(moduleId, due, reference);
        }

        @Override public boolean cancel(String wakeupId) throws IOException {
            if (!active || closed) throw new IllegalStateException("Module is not active");
            return wakeups.cancel(moduleId, wakeupId);
        }
    }

    private record Failure(String stamp, String message) { }

    private static final class Loaded {
        private final String id;
        private final MADREModule module;
        private final URLClassLoader loader;
        private final Path copy;
        private final InstalledEnvironment environment;
        private final Map<String, MADREAgent> agents;
        private final Map<String, ModuleOperation> operations;

        private Loaded(String id, MADREModule module, URLClassLoader loader, Path copy,
                       InstalledEnvironment environment, Map<String, MADREAgent> agents,
                       Map<String, ModuleOperation> operations) {
            this.id = id;
            this.module = module;
            this.loader = loader;
            this.copy = copy;
            this.environment = environment;
            this.agents = agents;
            this.operations = operations;
        }

        private void close() throws IOException {
            IOException first = null;
            try { module.close(); }
            catch (RuntimeException failure) {
                first = new IOException("Module " + id + " close failed", failure);
            }
            environment.active = false;
            try { loader.close(); }
            catch (IOException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
            try { Files.deleteIfExists(copy); }
            catch (IOException failure) {
                if (first == null) first = failure;
                else first.addSuppressed(failure);
            }
            if (first != null) throw first;
        }
    }

    public record ModuleInfo(String artifact, String id, boolean available,
                             List<String> agentIds, List<String> operationIds, String diagnostic) { }

    public record WakeupInfo(String id, String moduleId, Instant due, boolean moduleAvailable,
                             int attempts, String diagnostic) { }
}
