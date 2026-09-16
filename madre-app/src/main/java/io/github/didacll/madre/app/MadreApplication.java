package io.github.didacll.madre.app;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.reasoning.ReasoningCapability;
import io.github.didacll.madre.kernel.runtime.KernelRuntime;
import io.github.didacll.madre.kernel.runtime.ReasoningCapabilityRegistry;
import io.github.didacll.madre.reasoning.installation.ReasoningMechanism;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderConfiguration;
import io.github.didacll.madre.reasoning.installation.ReasoningProviderDescriptor;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.EffectProfile;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OwnerInteractionAgent;
import io.github.didacll.madre.sdk.module.OwnerMessage;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Running installation assembly. CORE, local interaction and reasoning mechanisms are independent optional facts. */
public final class MadreApplication implements AutoCloseable {
    private final KernelRuntime kernel;
    private final InstalledModuleLoader moduleLoader;
    private final InstalledReasoningLoader reasoningLoader;
    private final ReasoningProviderConfiguration reasoningConfiguration;
    private final List<ModuleRegistration.Registration> moduleRegistrations;
    private final List<ReasoningCapabilityRegistry.Registration> reasoningRegistrations;

    private MadreApplication(KernelRuntime kernel, InstalledModuleLoader moduleLoader,
            InstalledReasoningLoader reasoningLoader,
            ReasoningProviderConfiguration reasoningConfiguration,
            List<ModuleRegistration.Registration> moduleRegistrations,
            List<ReasoningCapabilityRegistry.Registration> reasoningRegistrations) {
        this.kernel = kernel;
        this.moduleLoader = moduleLoader;
        this.reasoningLoader = reasoningLoader;
        this.reasoningConfiguration = Objects.requireNonNull(reasoningConfiguration,
                "reasoningConfiguration");
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
                    ReasoningConfigurationManager.configuration(properties);
            for (ReasoningMechanism<?, ?> mechanism
                    : reasoningLoader.materialize(reasoningConfiguration)) {
                capabilities.add(registerReasoning(kernel.reasoningCapabilities(), mechanism));
            }

            Path stateDirectory = stateDirectory(properties, database);
            Files.createDirectories(stateDirectory);
            moduleLoader = new InstalledModuleLoader(moduleDirectory(properties));
            modules.addAll(ModuleInstaller.install(moduleLoader.providers(), moduleId ->
                    new ModuleContext(kernel.reasoning(), kernel.modules().directoryFor(moduleId),
                            kernel.modules().invokerFor(moduleId), stateDirectory),
                    properties, kernel.modules()));
            return new MadreApplication(kernel, moduleLoader, reasoningLoader, reasoningConfiguration,
                    modules, capabilities);
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

    List<ReasoningProviderDescriptor> installedReasoningProviders() {
        return reasoningLoader.providerDescriptors();
    }

    List<InstalledReasoningLoader.ProviderInstance> configuredReasoningInstances() {
        return reasoningLoader.configuredInstances(reasoningConfiguration);
    }

    /** True when the Module currently selected as CORE exposes an executable semantic Agent. */
    boolean hasOwnerInteraction() { return coreOwnerInteractionAgent().isPresent(); }

    /** Ordinary owner conversation: host transport into the selected CORE Agent. */
    CompletionStage<OwnerMessage> converse(String ownerText, Sensitivity sensitivity) {
        OwnerInteractionAgent interaction = coreOwnerInteractionAgent().orElseThrow(() ->
                new IllegalStateException("selected CORE has no owner-interaction Agent"));
        return interaction.respond(kernel.modules(), ownerText, sensitivity);
    }

    /** Host polling transport for semantic follow-ups approved by the selected CORE Agent. */
    CompletionStage<List<OwnerMessage>> collectOwnerFollowUps() {
        Optional<OwnerInteractionAgent> interaction = coreOwnerInteractionAgent();
        if (interaction.isEmpty()) return CompletableFuture.completedFuture(List.of());
        return interaction.orElseThrow().followUps(kernel.modules());
    }

    private Optional<OwnerInteractionAgent> coreOwnerInteractionAgent() {
        return resolvedCore().flatMap(core -> runtimeModuleOptional(core)
                .flatMap(ModuleInstance::ownerInteractionAgent));
    }

    /** External/public-boundary adapter for one Module-owned Material input. */
    public CompletionStage<Material<?>> invokePublicText(ModuleId moduleId, String operationName,
            String materialTypeName, Sensitivity sensitivity, String encodedPayload) {
        return invokeText(InvocationBoundary.PUBLIC, moduleId, operationName, materialTypeName,
                sensitivity, encodedPayload);
    }

    /** Expert/debug owner path; Module exposure and public disclosure are independent. */
    public CompletionStage<Material<?>> invokeOwnerText(ModuleId moduleId, String operationName,
            String materialTypeName, Sensitivity sensitivity, String encodedPayload) {
        return invokeText(InvocationBoundary.OWNER_LOCAL, moduleId, operationName, materialTypeName,
                sensitivity, encodedPayload);
    }

    /** Diagnostic product path for an exact Operation explicitly offered for owner interaction. */
    CompletionStage<Material<?>> invokeInteractionText(ModuleId moduleId, String operationName,
            String materialTypeName, Sensitivity sensitivity, String encodedPayload) {
        return invokeText(InvocationBoundary.OWNER_INTERACTION, moduleId, operationName,
                materialTypeName, sensitivity, encodedPayload);
    }

    private CompletionStage<Material<?>> invokeText(InvocationBoundary boundary, ModuleId moduleId,
            String operationSpec, String materialTypeName, Sensitivity sensitivity,
            String encodedPayload) {
        ModuleInstance module = runtimeModule(moduleId);
        ResolvedTextOperation resolved = boundary == InvocationBoundary.OWNER_INTERACTION
                ? resolveInteractionTextOperation(module, operationSpec, materialTypeName)
                : resolveTextOperation(module, operationSpec, materialTypeName);
        Material<?> input = decodeMaterial(moduleId, resolved.inputType(), encodedPayload,
                sensitivity);
        return invokeExact(boundary, resolved.operation(), input, resolved.effectProfileName());
    }

    private ModuleInstance runtimeModule(ModuleId moduleId) {
        return runtimeModuleOptional(moduleId).orElseThrow(() ->
                new IllegalArgumentException("Module is not installed: " + moduleId));
    }

    private Optional<ModuleInstance> runtimeModuleOptional(ModuleId moduleId) {
        return moduleRegistrations.stream().map(ModuleRegistration.Registration::instance)
                .filter(instance -> instance.definition().id().equals(moduleId)).findFirst();
    }

    /** Resolves an exact installed Operation contract for generic host/debug or public entry. */
    static ResolvedTextOperation resolveTextOperation(ModuleInstance module,
            String operationSpec, String materialTypeName) {
        return resolveTextOperationContract(module, operationSpec, materialTypeName);
    }

    /** Resolves only an executable binding explicitly opted into owner interaction. */
    static ResolvedTextOperation resolveInteractionTextOperation(ModuleInstance module,
            String operationSpec, String materialTypeName) {
        ResolvedTextOperation resolved = resolveTextOperationContract(module, operationSpec,
                materialTypeName);
        OperationBinding<?, ?> binding = module.operations().get(resolved.operation().id());
        if (binding == null || !binding.ownerInteractionEntryPoint()) {
            throw new IllegalArgumentException("Operation is not an owner-interaction entry point: "
                    + resolved.operation().id());
        }
        return resolved;
    }

    private static ResolvedTextOperation resolveTextOperationContract(ModuleInstance module,
            String operationSpec, String materialTypeName) {
        Objects.requireNonNull(module, "module");
        ModuleDefinition definition = module.definition();
        OperationSelection selection = operationSelection(operationSpec);
        ModuleId moduleId = definition.id();
        OperationDefinition operation = definition.operations().get(
                new OperationId(moduleId, selection.operationName()));
        if (operation == null) {
            throw new IllegalArgumentException("Operation is not installed: "
                    + moduleId.value() + "/" + selection.operationName());
        }
        MaterialTypeId typeId = new MaterialTypeId(moduleId,
                Objects.requireNonNull(materialTypeName, "materialTypeName"));
        if (!operation.acceptedMaterial().containsKey(typeId)) {
            throw new IllegalArgumentException("Operation does not accept Material type " + typeId);
        }
        MaterialType<?> type = module.materialTypes().get(typeId);
        if (type == null) {
            throw new IllegalArgumentException(
                    "local invocation supports Module-owned input Material types only");
        }
        selectedEffectProfile(operation, selection.effectProfileName());
        return new ResolvedTextOperation(operation, type, selection.effectProfileName());
    }

    private static OperationSelection operationSelection(String operationSpec) {
        Objects.requireNonNull(operationSpec, "operationSpec");
        int separator = operationSpec.indexOf('@');
        if (separator < 0) return new OperationSelection(operationSpec, Optional.empty());
        if (separator == 0 || separator == operationSpec.length() - 1
                || operationSpec.indexOf('@', separator + 1) >= 0) {
            throw new IllegalArgumentException(
                    "operation profile selector must be <operation>@<effect-profile>");
        }
        return new OperationSelection(operationSpec.substring(0, separator),
                Optional.of(operationSpec.substring(separator + 1)));
    }

    private static <T> Material<T> decodeMaterial(ModuleId moduleId, MaterialType<T> type,
            String encodedPayload, Sensitivity sensitivity) {
        T payload = type.codec().decode(encodedPayload.getBytes(StandardCharsets.UTF_8));
        return new Material<>(new MaterialId(moduleId, UUID.randomUUID().toString()), type,
                payload, Objects.requireNonNull(sensitivity, "sensitivity"));
    }

    @SuppressWarnings("unchecked")
    private CompletionStage<Material<?>> invokeExact(InvocationBoundary boundary,
            OperationDefinition operation, Material<?> input,
            Optional<String> effectProfileName) {
        Material<Object> typedInput = (Material<Object>) input;
        OperationCall<Object, Object> call = operationCall(operation, typedInput,
                effectProfileName);
        CompletionStage<Material<Object>> result = switch (boundary) {
            case PUBLIC -> kernel.modules().invokePublic(call);
            case OWNER_LOCAL -> kernel.modules().invokeOwner(call);
            case OWNER_INTERACTION -> kernel.modules().invokeOwnerInteraction(call);
        };
        return result.thenApply(material -> material);
    }

    private static <I, O> OperationCall<I, O> operationCall(
            OperationDefinition operation, Material<I> input,
            Optional<String> effectProfileName) {
        Optional<EffectProfile> selected = selectedEffectProfile(operation, effectProfileName);
        if (selected.isEmpty()) return OperationCall.withoutEffect(operation, input);
        return OperationCall.withEffect(operation, selected.orElseThrow(), input, List.of());
    }

    private static Optional<EffectProfile> selectedEffectProfile(OperationDefinition operation,
            Optional<String> effectProfileName) {
        var profiles = operation.effectProfiles();
        if (profiles.isEmpty()) {
            if (effectProfileName.isPresent()) {
                throw new IllegalArgumentException("Operation has no EffectProfile: "
                        + operation.id());
            }
            return Optional.empty();
        }
        if (effectProfileName.isPresent()) {
            String requested = effectProfileName.orElseThrow();
            return Optional.of(profiles.values().stream()
                    .filter(profile -> profile.id().name().equals(requested))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "EffectProfile is not declared by Operation: " + requested)));
        }
        if (profiles.size() == 1) return Optional.of(profiles.values().iterator().next());
        throw new IllegalArgumentException("Operation has multiple EffectProfiles; use "
                + operation.id().name() + "@<effect-profile>");
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

    private enum InvocationBoundary { PUBLIC, OWNER_LOCAL, OWNER_INTERACTION }

    private record OperationSelection(String operationName, Optional<String> effectProfileName) { }

    record ResolvedTextOperation(OperationDefinition operation, MaterialType<?> inputType,
            Optional<String> effectProfileName) { }
}
