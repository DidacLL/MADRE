package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.runtime.KernelRuntime;
import io.github.didacll.madre.kernel.runtime.ReasoningCapabilityRegistry;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Running installation assembly. CORE and reasoning mechanisms are optional facts. */
public final class MadreApplication implements AutoCloseable {
    private final KernelRuntime kernel;
    private final InstalledModuleLoader moduleLoader;
    private final InstalledReasoningLoader reasoningLoader;
    private final List<ModuleRegistration.Registration> moduleRegistrations;
    private final List<ReasoningCapabilityRegistry.Registration> reasoningRegistrations;

    private MadreApplication(KernelRuntime kernel, InstalledModuleLoader moduleLoader,
            InstalledReasoningLoader reasoningLoader,
            List<ModuleRegistration.Registration> moduleRegistrations,
            List<ReasoningCapabilityRegistry.Registration> reasoningRegistrations) {
        this.kernel = kernel;
        this.moduleLoader = moduleLoader;
        this.reasoningLoader = reasoningLoader;
        this.moduleRegistrations = List.copyOf(moduleRegistrations);
        this.reasoningRegistrations = List.copyOf(reasoningRegistrations);
    }

    public static MadreApplication start(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        Path database = Path.of(required(properties, "kernel.database")).toAbsolutePath();
        createParent(database);
        KernelRuntime kernel = new KernelRuntime(KernelConfiguration.from(properties));
        List<ReasoningCapabilityRegistry.Registration> capabilities = new ArrayList<>();
        List<ModuleRegistration.Registration> modules = new ArrayList<>();
        InstalledReasoningLoader reasoningLoader = null;
        InstalledModuleLoader moduleLoader = null;
        try {
            reasoningLoader = new InstalledReasoningLoader(reasoningDirectory(properties));
            ReasoningProviderConfiguration reasoningConfiguration =
                    reasoningConfiguration(properties);
            for (ReasoningMechanism<?, ?> mechanism
                    : reasoningLoader.materialize(reasoningConfiguration)) {
                capabilities.add(registerReasoning(kernel.reasoningCapabilities(), mechanism));
            }

            Path stateDirectory = stateDirectory(properties, database);
            Files.createDirectories(stateDirectory);
            moduleLoader = new InstalledModuleLoader(moduleDirectory(properties));
            ModuleContext context = new ModuleContext(kernel.reasoning(), kernel.modules(),
                    kernel.modules(), stateDirectory);
            for (var provider : moduleLoader.providers()) {
                ModuleInstance instance = Objects.requireNonNull(provider.create(context),
                        "ModuleProvider returned null");
                modules.add(kernel.modules().register(instance));
            }
            return new MadreApplication(kernel, moduleLoader, reasoningLoader, modules,
                    capabilities);
        } catch (IOException | RuntimeException exception) {
            closeAfterFailure(modules, exception);
            closeAfterFailure(capabilities, exception);
            closeAfterFailure(moduleLoader, exception);
            closeAfterFailure(reasoningLoader, exception);
            closeAfterFailure(kernel, exception);
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

    private static Path reasoningDirectory(Properties properties) {
        String configured = properties.getProperty("reasoning.directory");
        return configured == null || configured.isBlank()
                ? InstalledReasoningLoader.defaultDirectory()
                : Path.of(configured.strip()).toAbsolutePath().normalize();
    }

    private static ReasoningProviderConfiguration reasoningConfiguration(Properties properties) {
        Map<String, String> values = new TreeMap<>();
        properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith("reasoning."))
                .filter(name -> !name.equals("reasoning.directory"))
                .forEach(name -> values.put(name, properties.getProperty(name)));
        return new ReasoningProviderConfiguration(values);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ReasoningCapabilityRegistry.Registration registerReasoning(
            ReasoningCapabilityRegistry registry, ReasoningMechanism<?, ?> mechanism) {
        ReasoningCapability capability = mechanism.capability();
        return registry.register(capability, mechanism.preference());
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
        RuntimeException failure = null;
        failure = closeAll(moduleRegistrations, failure);
        failure = closeOne(moduleLoader, failure);
        failure = closeAll(reasoningRegistrations, failure);
        failure = closeOne(reasoningLoader, failure);
        failure = closeOne(kernel, failure);
        if (failure != null) throw failure;
    }

    private static RuntimeException closeAll(List<? extends AutoCloseable> closeables,
            RuntimeException failure) {
        RuntimeException current = failure;
        for (int index = closeables.size() - 1; index >= 0; index--) {
            current = closeOne(closeables.get(index), current);
        }
        return current;
    }

    private static RuntimeException closeOne(AutoCloseable closeable, RuntimeException failure) {
        if (closeable == null) return failure;
        try {
            closeable.close();
            return failure;
        } catch (Exception exception) {
            RuntimeException wrapped = exception instanceof RuntimeException runtime
                    ? runtime : new IllegalStateException("cannot close MADRE runtime resource",
                            exception);
            if (failure == null) return wrapped;
            failure.addSuppressed(wrapped);
            return failure;
        }
    }

    private static void closeAfterFailure(List<? extends AutoCloseable> closeables,
            Throwable failure) {
        for (int index = closeables.size() - 1; index >= 0; index--) {
            closeAfterFailure(closeables.get(index), failure);
        }
    }

    private static void closeAfterFailure(AutoCloseable closeable, Throwable failure) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception closeFailure) {
            failure.addSuppressed(closeFailure);
        }
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
