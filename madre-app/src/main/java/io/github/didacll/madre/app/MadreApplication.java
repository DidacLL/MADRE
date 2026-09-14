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
import io.github.didacll.madre.interaction.OwnerInteractionModule;
import io.github.didacll.madre.kernel.capability.CapabilityId;
import io.github.didacll.madre.kernel.capability.ResourceClaim;
import io.github.didacll.madre.kernel.capability.ResourceId;
import io.github.didacll.madre.kernel.config.KernelConfiguration;
import io.github.didacll.madre.kernel.runtime.CapabilityRegistry;
import io.github.didacll.madre.kernel.runtime.KernelRuntime;
import io.github.didacll.madre.sdk.execution.PhysicalLocation;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import io.github.didacll.madre.websearch.WebSearchModule;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Running installation assembly with ordinary Modules and one configured CORE role. */
public final class MadreApplication implements AutoCloseable {
    private static final Set<String> CORE_OPERATION_NAMES = Set.of("standard-prompt", "fast-lane");

    private final KernelRuntime kernel;
    private final OwnerInteractionModule interaction;
    private final WebSearchModule webSearch;
    private final List<ModuleRegistration.Registration> moduleRegistrations;
    private final List<CapabilityRegistry.Registration> capabilityRegistrations;

    private MadreApplication(KernelRuntime kernel, OwnerInteractionModule interaction,
            WebSearchModule webSearch,
            List<ModuleRegistration.Registration> moduleRegistrations,
            List<CapabilityRegistry.Registration> capabilityRegistrations) {
        this.kernel = kernel;
        this.interaction = interaction;
        this.webSearch = webSearch;
        this.moduleRegistrations = List.copyOf(moduleRegistrations);
        this.capabilityRegistrations = List.copyOf(capabilityRegistrations);
    }

    public static MadreApplication start(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        Path database = Path.of(required(properties, "kernel.database")).toAbsolutePath();
        Path interactionState = Path.of(
                required(properties, "module.owner-interaction.state")).toAbsolutePath();
        createParent(database);
        createParent(interactionState);
        KernelRuntime kernel = new KernelRuntime(KernelConfiguration.from(properties));
        List<CapabilityRegistry.Registration> capabilities = new ArrayList<>();
        List<ModuleRegistration.Registration> modules = new ArrayList<>();
        try {
            registerCapabilities(properties, kernel, capabilities);
            if (capabilities.isEmpty()) {
                throw new IllegalArgumentException("at least one physical connector must be enabled");
            }

            OwnerInteractionModule interaction = new OwnerInteractionModule(
                    kernel.execution(), interactionState);
            WebSearchModule webSearch = new WebSearchModule(kernel.execution());
            modules.add(kernel.modules().register(interaction.definition()));
            modules.add(kernel.modules().register(webSearch.definition()));

            ModuleId coreId = kernel.modules().resolvedCore().orElseThrow(() ->
                    new IllegalStateException("configured CORE Module is not registered"));
            ModuleDefinition coreDefinition = definitionFor(coreId, interaction, webSearch);
            if (!qualifiesForCore(coreDefinition)) {
                throw new IllegalArgumentException("configured CORE Module does not expose the required public interaction behavior: "
                        + coreId.value());
            }
            return new MadreApplication(kernel, interaction, webSearch, modules, capabilities);
        } catch (RuntimeException exception) {
            closeReverse(modules);
            capabilities.forEach(CapabilityRegistry.Registration::close);
            kernel.close();
            throw exception;
        }
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

    private static ModuleDefinition definitionFor(ModuleId id, OwnerInteractionModule interaction,
            WebSearchModule webSearch) {
        if (interaction.definition().id().equals(id)) return interaction.definition();
        if (webSearch.definition().id().equals(id)) return webSearch.definition();
        throw new IllegalStateException("registered CORE definition is unavailable to this assembly");
    }

    /** Qualifies an ordinary Module definition without adding a CORE type or execution path. */
    private static boolean qualifiesForCore(ModuleDefinition definition) {
        Set<String> publicOperations = definition.operations().values().stream()
                .filter(operation -> operation.visibility() == OperationVisibility.PUBLIC)
                .map(operation -> operation.id().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!publicOperations.containsAll(CORE_OPERATION_NAMES)) return false;
        return definition.agents().values().stream().anyMatch(agent -> {
            Set<String> names = agent.operations().stream().map(operation -> operation.name())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            return names.containsAll(CORE_OPERATION_NAMES);
        });
    }

    public OwnerInteractionModule interaction() { return interaction; }
    public WebSearchModule webSearch() { return webSearch; }
    public KernelRuntime kernel() { return kernel; }

    @Override public void close() {
        closeReverse(moduleRegistrations);
        capabilityRegistrations.forEach(CapabilityRegistry.Registration::close);
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
