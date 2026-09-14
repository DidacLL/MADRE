package io.github.didacll.madre.app;

import io.github.didacll.madre.adapter.llamacpp.LlamaCppCapability;
import io.github.didacll.madre.adapter.llamacpp.LlamaCppConfiguration;
import io.github.didacll.madre.adapter.llamacpp.LlamaCppUnixSocketCapability;
import io.github.didacll.madre.adapter.llamacpp.LlamaCppUnixSocketConfiguration;
import io.github.didacll.madre.adapter.openai.OpenAiCompatibleCapability;
import io.github.didacll.madre.adapter.openai.OpenAiCompatibleConfiguration;
import io.github.didacll.madre.adapter.searxng.SearxngCapability;
import io.github.didacll.madre.adapter.searxng.SearxngConfiguration;
import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ResourceClaim;
import io.github.didacll.madre.kernel.capability.ResourceId;
import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.runtime.CapabilityRegistry;
import io.github.didacll.madre.kernel.runtime.KernelRuntime;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Running installation assembly. CORE and physical connectors are optional installation facts. */
public final class MadreApplication implements AutoCloseable {
    private final KernelRuntime kernel;
    private final InstalledModuleLoader moduleLoader;
    private final List<ModuleRegistration.Registration> moduleRegistrations;
    private final List<CapabilityRegistry.Registration> capabilityRegistrations;

    private MadreApplication(KernelRuntime kernel, InstalledModuleLoader moduleLoader,
            List<ModuleRegistration.Registration> moduleRegistrations,
            List<CapabilityRegistry.Registration> capabilityRegistrations) {
        this.kernel = kernel;
        this.moduleLoader = moduleLoader;
        this.moduleRegistrations = List.copyOf(moduleRegistrations);
        this.capabilityRegistrations = List.copyOf(capabilityRegistrations);
    }

    public static MadreApplication start(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        Path database = Path.of(required(properties, "kernel.database")).toAbsolutePath();
        createParent(database);
        KernelRuntime kernel = new KernelRuntime(KernelConfiguration.from(properties));
        List<CapabilityRegistry.Registration> capabilities = new ArrayList<>();
        List<ModuleRegistration.Registration> modules = new ArrayList<>();
        InstalledModuleLoader loader = null;
        try {
            registerCapabilities(properties, kernel, capabilities);
            Path stateDirectory = stateDirectory(properties, database);
            Files.createDirectories(stateDirectory);
            Path moduleDirectory = moduleDirectory(properties);
            loader = new InstalledModuleLoader(moduleDirectory);
            ModuleContext context = new ModuleContext(kernel.execution(), kernel.modules(),
                    kernel.modules(), stateDirectory);
            for (var provider : loader.providers()) {
                ModuleInstance instance = Objects.requireNonNull(provider.create(context),
                        "ModuleProvider returned null");
                modules.add(kernel.modules().register(instance));
            }
            return new MadreApplication(kernel, loader, modules, capabilities);
        } catch (IOException | RuntimeException exception) {
            closeReverse(modules);
            capabilities.forEach(CapabilityRegistry.Registration::close);
            if (loader != null) loader.close();
            kernel.close();
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("cannot initialize Module state directory", exception);
        }
    }

    private static Path moduleDirectory(Properties properties) {
        String configured = properties.getProperty("modules.directory");
        return configured == null || configured.isBlank()
                ? InstalledModuleLoader.defaultDirectory()
                : Path.of(configured.strip()).toAbsolutePath().normalize();
    }

    private static Path stateDirectory(Properties properties, Path database) {
        String configured = properties.getProperty("modules.state-directory");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.strip()).toAbsolutePath().normalize();
        }
        Path parent = database.getParent();
        return (parent == null ? Path.of("module-state") : parent.resolve("module-state"))
                .toAbsolutePath().normalize();
    }

    private static void registerCapabilities(Properties properties, KernelRuntime kernel,
            List<CapabilityRegistry.Registration> capabilities) {
        if (enabled(properties, "connector.llamacpp-unix.enabled")) {
            LlamaCppUnixSocketConfiguration configuration = new LlamaCppUnixSocketConfiguration(
                    new CapabilityId(required(properties, "connector.llamacpp-unix.id")),
                    Path.of(required(properties, "connector.llamacpp-unix.socket")),
                    required(properties, "connector.llamacpp-unix.model"),
                    privacy(properties, "connector.llamacpp-unix.privacy"),
                    Integrity.valueOf(required(properties, "connector.llamacpp-unix.integrity")),
                    duration(properties, "connector.llamacpp-unix.expected-latency-ms"),
                    resources(properties, "connector.llamacpp-unix.resource."));
            capabilities.add(kernel.capabilities().register(
                    new LlamaCppUnixSocketCapability(configuration),
                    integer(properties, "connector.llamacpp-unix.preference")));
        }
        if (enabled(properties, "connector.llamacpp.enabled")) {
            LlamaCppConfiguration configuration = new LlamaCppConfiguration(
                    new CapabilityId(required(properties, "connector.llamacpp.id")),
                    URI.create(required(properties, "connector.llamacpp.endpoint")),
                    required(properties, "connector.llamacpp.model"),
                    privacy(properties, "connector.llamacpp.privacy"),
                    Integrity.valueOf(required(properties, "connector.llamacpp.integrity")),
                    duration(properties, "connector.llamacpp.expected-latency-ms"),
                    resources(properties, "connector.llamacpp.resource."));
            capabilities.add(kernel.capabilities().register(new LlamaCppCapability(configuration),
                    integer(properties, "connector.llamacpp.preference")));
        }
        if (enabled(properties, "connector.openai-compatible.enabled")) {
            OpenAiCompatibleConfiguration configuration = new OpenAiCompatibleConfiguration(
                    new CapabilityId(required(properties, "connector.openai-compatible.id")),
                    URI.create(required(properties, "connector.openai-compatible.endpoint")),
                    required(properties, "connector.openai-compatible.model"),
                    privacy(properties, "connector.openai-compatible.privacy"),
                    Integrity.valueOf(required(properties, "connector.openai-compatible.integrity")),
                    PhysicalLocation.valueOf(required(properties,
                            "connector.openai-compatible.location")),
                    duration(properties, "connector.openai-compatible.expected-latency-ms"),
                    resources(properties, "connector.openai-compatible.resource."));
            capabilities.add(kernel.capabilities().register(
                    new OpenAiCompatibleCapability(configuration),
                    integer(properties, "connector.openai-compatible.preference")));
        }
        if (enabled(properties, "connector.searxng.enabled")) {
            SearxngConfiguration configuration = new SearxngConfiguration(
                    new CapabilityId(required(properties, "connector.searxng.id")),
                    URI.create(required(properties, "connector.searxng.endpoint")),
                    privacy(properties, "connector.searxng.privacy"),
                    Integrity.valueOf(required(properties, "connector.searxng.integrity")),
                    duration(properties, "connector.searxng.expected-latency-ms"),
                    resources(properties, "connector.searxng.resource."));
            capabilities.add(kernel.capabilities().register(new SearxngCapability(configuration),
                    integer(properties, "connector.searxng.preference")));
        }
    }

    public List<ModuleDefinition> installedModules() { return kernel.modules().definitions(); }

    /** Console/public-boundary adapter for one no-effect Module-owned Material input. */
    public CompletionStage<Material<?>> invokePublicText(ModuleId moduleId, String operationName,
            String materialTypeName, Sensitivity sensitivity, String encodedPayload) {
        ModuleDefinition definition = kernel.modules().definition(moduleId).orElseThrow(() ->
                new IllegalArgumentException("Module is not installed: " + moduleId));
        OperationDefinition<?, ?> operation = definition.operations().get(
                new OperationId(moduleId, operationName));
        if (operation == null || operation.visibility() != OperationVisibility.PUBLIC) {
            throw new IllegalArgumentException("PUBLIC Operation is not installed: "
                    + moduleId.value() + "/" + operationName);
        }
        if (!operation.effectProfiles().isEmpty()) {
            throw new IllegalArgumentException(
                    "console invocation requires an Operation without an EffectProfile");
        }
        MaterialTypeId typeId = new MaterialTypeId(moduleId, materialTypeName);
        if (!operation.acceptedMaterial().containsKey(typeId)) {
            throw new IllegalArgumentException("Operation does not accept Material type " + typeId);
        }
        MaterialType<?> type = definition.materialTypes().get(typeId);
        if (type == null) {
            throw new IllegalArgumentException(
                    "console invocation supports Module-owned input Material types only");
        }
        Material<?> input = decodeMaterial(moduleId, type, encodedPayload, sensitivity);
        return invokeNoEffect(operation, input);
    }

    private static <T> Material<T> decodeMaterial(ModuleId moduleId, MaterialType<T> type,
            String encodedPayload, Sensitivity sensitivity) {
        T payload = type.codec().decode(encodedPayload.getBytes(StandardCharsets.UTF_8));
        return new Material<>(new MaterialId(moduleId, UUID.randomUUID().toString()), type,
                payload, Objects.requireNonNull(sensitivity, "sensitivity"));
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Material<?>> invokeNoEffect(OperationDefinition<?, ?> operation,
            Material<?> input) {
        OperationDefinition<Object, Object> typedOperation =
                (OperationDefinition<Object, Object>) operation;
        Material<Object> typedInput = (Material<Object>) input;
        OperationCall<Object, Object> call = OperationCall.withoutEffect(
                typedOperation, typedInput);
        return kernel.modules().invokePublic(call).thenApply(result -> result);
    }

    public Optional<ModuleId> resolvedCore() { return kernel.modules().resolvedCore(); }
    public KernelRuntime kernel() { return kernel; }

    @Override public void close() {
        closeReverse(moduleRegistrations);
        capabilityRegistrations.forEach(CapabilityRegistry.Registration::close);
        moduleLoader.close();
        kernel.close();
    }

    private static void closeReverse(List<ModuleRegistration.Registration> registrations) {
        for (int index = registrations.size() - 1; index >= 0; index--) {
            registrations.get(index).close();
        }
    }

    private static boolean enabled(Properties properties, String key) {
        return Boolean.parseBoolean(properties.getProperty(key, "false"));
    }

    private static int integer(Properties properties, String key) {
        return Integer.parseInt(required(properties, key));
    }

    private static Duration duration(Properties properties, String key) {
        long millis = Long.parseLong(required(properties, key));
        if (millis < 1) throw new IllegalArgumentException(key + " must be positive");
        return Duration.ofMillis(millis);
    }

    private static Privacy privacy(Properties properties, String key) {
        return switch (required(properties, key)) {
            case "P1", "PUBLIC" -> Privacy.PUBLIC;
            case "P2", "UNKNOWN" -> Privacy.UNKNOWN;
            case "P3", "LOCAL" -> Privacy.LOCAL;
            case "P4", "MODULE" -> Privacy.MODULE;
            case "P5", "SECRET" -> Privacy.SECRET;
            default -> throw new IllegalArgumentException(
                    key + " must be one of PUBLIC/P1, UNKNOWN/P2, LOCAL/P3, MODULE/P4 or SECRET/P5");
        };
    }

    private static List<ResourceClaim> resources(Properties properties, String prefix) {
        return properties.stringPropertyNames().stream().filter(name -> name.startsWith(prefix))
                .sorted().map(name -> new ResourceClaim(new ResourceId(name.substring(prefix.length())),
                        Long.parseLong(required(properties, name)))).toList();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing property " + key);
        }
        return value.strip();
    }

    private static void createParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) return;
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot create " + parent, exception);
        }
    }
}
