package io.github.didacll.madre.app;

import io.github.didacll.madre.adapter.llamacpp.LlamaCppCapability;
import io.github.didacll.madre.adapter.llamacpp.LlamaCppConfiguration;
import io.github.didacll.madre.adapter.openai.OpenAiCompatibleCapability;
import io.github.didacll.madre.adapter.openai.OpenAiCompatibleConfiguration;
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
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** Running installation assembly with one ordinary Module assigned to CORE. */
public final class MadreApplication implements AutoCloseable {
    private final KernelRuntime kernel;
    private final OwnerInteractionModule interaction;
    private final ModuleRegistration.Registration moduleRegistration;
    private final List<CapabilityRegistry.Registration> capabilityRegistrations;

    private MadreApplication(KernelRuntime kernel, OwnerInteractionModule interaction,
            ModuleRegistration.Registration moduleRegistration,
            List<CapabilityRegistry.Registration> capabilityRegistrations) {
        this.kernel = kernel;
        this.interaction = interaction;
        this.moduleRegistration = moduleRegistration;
        this.capabilityRegistrations = List.copyOf(capabilityRegistrations);
    }

    public static MadreApplication start(Properties properties) {
        Objects.requireNonNull(properties, "properties");
        if (!OwnerInteractionModule.ID.value().equals(required(properties, "roles.core"))) {
            throw new IllegalArgumentException(
                    "this assembly installs only " + OwnerInteractionModule.ID.value()
                            + "; another CORE assignment requires another installation assembly");
        }
        Path database = Path.of(required(properties, "kernel.database")).toAbsolutePath();
        Path interactionState = Path.of(
                required(properties, "module.owner-interaction.state")).toAbsolutePath();
        createParent(database);
        createParent(interactionState);
        KernelRuntime kernel = new KernelRuntime(KernelConfiguration.from(properties));
        List<CapabilityRegistry.Registration> capabilities = new ArrayList<>();
        try {
            if (enabled(properties, "connector.llamacpp.enabled")) {
                LlamaCppConfiguration configuration = new LlamaCppConfiguration(
                        new CapabilityId(required(properties, "connector.llamacpp.id")),
                        URI.create(required(properties, "connector.llamacpp.endpoint")),
                        required(properties, "connector.llamacpp.model"),
                        Privacy.valueOf(required(properties, "connector.llamacpp.privacy")),
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
                        Privacy.valueOf(required(properties, "connector.openai-compatible.privacy")),
                        Integrity.valueOf(required(properties, "connector.openai-compatible.integrity")),
                        PhysicalLocation.valueOf(required(properties,
                                "connector.openai-compatible.location")),
                        duration(properties, "connector.openai-compatible.expected-latency-ms"),
                        resources(properties, "connector.openai-compatible.resource."));
                capabilities.add(kernel.capabilities().register(
                        new OpenAiCompatibleCapability(configuration),
                        integer(properties, "connector.openai-compatible.preference")));
            }
            if (capabilities.isEmpty()) {
                throw new IllegalArgumentException("at least one physical connector must be enabled");
            }
            OwnerInteractionModule interaction = new OwnerInteractionModule(
                    kernel.execution(), interactionState);
            ModuleRegistration.Registration registration = kernel.modules().register(
                    interaction.definition());
            if (!kernel.modules().resolvedCore()
                    .filter(OwnerInteractionModule.ID::equals).isPresent()) {
                registration.close();
                throw new IllegalStateException("configured CORE Module did not resolve after registration");
            }
            return new MadreApplication(kernel, interaction, registration, capabilities);
        } catch (RuntimeException exception) {
            capabilities.forEach(CapabilityRegistry.Registration::close);
            kernel.close();
            throw exception;
        }
    }

    public OwnerInteractionModule interaction() { return interaction; }
    public KernelRuntime kernel() { return kernel; }

    @Override public void close() {
        moduleRegistration.close();
        capabilityRegistrations.forEach(CapabilityRegistry.Registration::close);
        kernel.close();
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

    private static List<ResourceClaim> resources(Properties properties, String prefix) {
        return properties.stringPropertyNames().stream().filter(name -> name.startsWith(prefix))
                .sorted().map(name -> new ResourceClaim(new ResourceId(name.substring(prefix.length())),
                        Long.parseLong(required(properties, name)))).toList();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing property " + key);
        return value.strip();
    }

    private static void createParent(Path path) {
        Path parent = path.getParent();
        if (parent == null) return;
        try { Files.createDirectories(parent); }
        catch (IOException exception) { throw new IllegalStateException("cannot create " + parent, exception); }
    }
}
