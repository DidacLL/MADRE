package io.github.didacll.madre.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.kernel.module.LiveModuleRegistry;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.ModuleDefinition;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleContext;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import io.github.didacll.madre.sdk.registration.ModuleRegistration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

final class ModuleInstallerTest {
    @Test void scopesConfigurationByExactCanonicalIdentityWithoutPrefixCollisions() {
        ModuleId parentId = new ModuleId("fixture.module");
        ModuleId childId = new ModuleId("fixture.module.child");
        AtomicReference<String> parentMarker = new AtomicReference<>();
        AtomicReference<String> childMarker = new AtomicReference<>();
        ModuleProvider parent = provider(parentId, configuration ->
                parentMarker.set(configuration.value("marker").orElseThrow()));
        ModuleProvider child = provider(childId, configuration ->
                childMarker.set(configuration.value("marker").orElseThrow()));
        Properties properties = new Properties();
        properties.setProperty("modules.config[fixture.module].marker", "parent");
        properties.setProperty("modules.config[fixture.module.child].marker", "child");
        LiveModuleRegistry registry = new LiveModuleRegistry();

        List<ModuleRegistration.Registration> registrations = ModuleInstaller.install(
                List.of(child, parent), contexts(), properties, registry);
        try {
            assertEquals("parent", parentMarker.get());
            assertEquals("child", childMarker.get());
            assertEquals(Set.of(parentId, childId), registry.definitions().stream()
                    .map(ModuleDefinition::id).collect(java.util.stream.Collectors.toSet()));
        } finally {
            close(registrations);
        }
    }

    @Test void bindsContextFactoryToEachCanonicalProviderIdentity() {
        ModuleId firstId = new ModuleId("a.module");
        ModuleId secondId = new ModuleId("b.module");
        java.util.Set<ModuleId> bound = new java.util.HashSet<>();
        LiveModuleRegistry registry = new LiveModuleRegistry();

        List<ModuleRegistration.Registration> registrations = ModuleInstaller.install(
                List.of(provider(secondId, configuration -> { }),
                        provider(firstId, configuration -> { })),
                moduleId -> {
                    bound.add(moduleId);
                    return context();
                }, new Properties(), registry);
        try {
            assertEquals(Set.of(firstId, secondId), bound);
        } finally {
            close(registrations);
        }
    }

    @Test void rejectsDuplicateProviderIdentityBeforeAnyProviderMaterializes() {
        ModuleId id = new ModuleId("fixture.duplicate");
        AtomicBoolean materialized = new AtomicBoolean();
        ModuleProvider first = provider(id, configuration -> materialized.set(true));
        ModuleProvider second = provider(id, configuration -> materialized.set(true));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleInstaller.install(List.of(first, second), contexts(), new Properties(),
                        new LiveModuleRegistry()));

        assertTrue(failure.getMessage().contains("duplicate ModuleProvider identity"));
        assertFalse(materialized.get());
    }

    @Test void materializationFailureOccursBeforeAnyModuleBecomesReachable() {
        ModuleId firstId = new ModuleId("a.module");
        ModuleId failingId = new ModuleId("z.module");
        LiveModuleRegistry registry = new LiveModuleRegistry();
        ModuleProvider first = provider(firstId, configuration -> { });
        ModuleProvider failing = new ModuleProvider() {
            @Override public ModuleId moduleId() { return failingId; }
            @Override public ModuleInstance create(ModuleContext context,
                    ModuleProviderConfiguration configuration) {
                assertTrue(registry.definitions().isEmpty());
                throw new IllegalArgumentException("malformed fixture configuration");
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleInstaller.install(List.of(failing, first), contexts(), new Properties(),
                        registry));

        assertTrue(failure.getMessage().contains("cannot materialize Module z.module"));
        assertTrue(registry.definitions().isEmpty());
    }

    @Test void rejectsMaterializedIdentityMismatchBeforeRegistration() {
        ModuleId declared = new ModuleId("fixture.declared");
        ModuleId returned = new ModuleId("fixture.returned");
        ModuleProvider provider = new ModuleProvider() {
            @Override public ModuleId moduleId() { return declared; }
            @Override public ModuleInstance create(ModuleContext context,
                    ModuleProviderConfiguration configuration) {
                return instance(returned);
            }
        };
        LiveModuleRegistry registry = new LiveModuleRegistry();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleInstaller.install(List.of(provider), contexts(), new Properties(),
                        registry));

        assertTrue(failure.getMessage().contains("does not match materialized Module identity"));
        assertTrue(registry.definitions().isEmpty());
    }

    @Test void rejectsConfigurationForAnUninstalledIdentityBeforeMaterialization() {
        ModuleId installed = new ModuleId("fixture.installed");
        AtomicBoolean materialized = new AtomicBoolean();
        ModuleProvider provider = provider(installed, configuration -> materialized.set(true));
        Properties properties = new Properties();
        properties.setProperty("modules.config[missing.module].setting", "value");
        LiveModuleRegistry registry = new LiveModuleRegistry();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ModuleInstaller.install(List.of(provider), contexts(), properties, registry));

        assertTrue(failure.getMessage().contains("does not target an installed canonical Module"));
        assertFalse(materialized.get());
        assertTrue(registry.definitions().isEmpty());
    }

    @Test void registrationFailureRollsBackEarlierRegistrations() {
        AtomicBoolean firstReachable = new AtomicBoolean();
        AtomicInteger registrations = new AtomicInteger();
        ModuleRegistration registry = new ModuleRegistration() {
            @Override public Registration register(ModuleInstance module) {
                if (registrations.incrementAndGet() == 2) {
                    throw new IllegalStateException("synthetic registration failure");
                }
                firstReachable.set(true);
                return new Registration() {
                    private boolean open = true;
                    @Override public ModuleInstance instance() { return module; }
                    @Override public void close() {
                        if (open) {
                            firstReachable.set(false);
                            open = false;
                        }
                    }
                };
            }
        };
        ModuleProvider first = provider(new ModuleId("a.module"), configuration -> { });
        ModuleProvider second = provider(new ModuleId("b.module"), configuration -> { });

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ModuleInstaller.install(List.of(first, second), contexts(), new Properties(),
                        registry));

        assertTrue(failure.getMessage().contains("cannot register Module b.module"));
        assertFalse(firstReachable.get());
    }

    private static ModuleProvider provider(ModuleId id,
            java.util.function.Consumer<ModuleProviderConfiguration> configurationConsumer) {
        return new ModuleProvider() {
            @Override public ModuleId moduleId() { return id; }
            @Override public ModuleInstance create(ModuleContext context,
                    ModuleProviderConfiguration configuration) {
                configurationConsumer.accept(configuration);
                return instance(id);
            }
        };
    }

    private static ModuleInstance instance(ModuleId id) {
        ModuleDefinition definition = new ModuleDefinition(id, "1.0.0", "test Module",
                Map.of(), Set.of(), Map.of(), Map.of(), Map.of());
        return new ModuleInstance(definition, Map.of());
    }

    private static Function<ModuleId, ModuleContext> contexts() {
        return ignored -> context();
    }

    private static ModuleContext context() {
        ReasoningService reasoning = new ReasoningService() {
            @Override public <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
                    ReasoningRequest<R, C> request) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            @Override public <R, C extends ReasoningComputation<R>> WorkId submit(
                    ReasoningRequest<R, C> request) {
                throw new UnsupportedOperationException();
            }
            @Override public Optional<WorkStatus> inspect(WorkId id) { return Optional.empty(); }
            @Override public boolean cancel(WorkId id) { return false; }
            @Override public <R> Optional<R> collect(WorkId id, Class<R> resultType) {
                return Optional.empty();
            }
            @Override public boolean acknowledge(WorkId id) { return false; }
        };
        ModuleInvoker invoker = new ModuleInvoker() {
            @Override public <I, O> CompletionStage<Material<O>> invoke(
                    OperationCall<I, O> call) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
        };
        return new ModuleContext(reasoning, query -> List.of(), invoker,
                Path.of("module-installer-test-state"));
    }

    private static void close(List<ModuleRegistration.Registration> registrations) {
        for (int index = registrations.size() - 1; index >= 0; index--) {
            registrations.get(index).close();
        }
    }
}
