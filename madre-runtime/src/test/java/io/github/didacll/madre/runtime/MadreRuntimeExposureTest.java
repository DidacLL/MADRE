package io.github.didacll.madre.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.MaterialTypeId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.material.MaterialCodecs;
import io.github.didacll.madre.sdk.material.MaterialType;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentContext;
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreRuntimeExposureTest {
    @TempDir Path temporary;

    @Test
    void localAgentExecutesItsOwnNonExposedOperation() {
        AtomicReference<AgentContext> observed = new AtomicReference<>();
        SingleOperationModule local = new SingleOperationModule("local", false, 1, observed);
        RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
        registry.install(local.instance());

        try (InferenceKernel kernel = kernel("local")) {
            Material<String> result = runtime(registry, kernel).invoke(local.call("hello"))
                    .toCompletableFuture().join();

            assertSame(local.agent(0), observed.get().actor());
            assertEquals("HELLO", result.payload());
        }
    }

    @Test
    void coreFallbackUsesConfiguredDefaultAgentAndOperationReceivesItsContext() {
        AtomicReference<AgentContext> observed = new AtomicReference<>();
        SingleOperationModule target = new SingleOperationModule("agentless", true, 0, observed);
        SingleOperationModule core = new SingleOperationModule("core", false, 2,
                new AtomicReference<>());
        RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
        registry.install(target.instance());
        registry.install(core.instance());
        registry.assignCore(core.id(), core.agent(1).id());

        try (InferenceKernel kernel = kernel("fallback")) {
            Material<String> result = runtime(registry, kernel).invoke(target.call("hello"))
                    .toCompletableFuture().join();

            assertEquals(Optional.of(core.agent(1).id()), registry.defaultCoreAgentId());
            assertSame(core.agent(1), observed.get().actor());
            assertEquals("HELLO", result.payload());
        }
    }

    @Test
    void coreFallbackCannotExecuteNonExposedForeignOperation() {
        SingleOperationModule target = new SingleOperationModule("private-target", false, 0,
                new AtomicReference<>());
        SingleOperationModule core = new SingleOperationModule("core-private", false, 1,
                new AtomicReference<>());
        RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
        registry.install(target.instance());
        registry.install(core.instance());
        registry.assignCore(core.id(), core.agent(0).id());

        try (InferenceKernel kernel = kernel("private-fallback")) {
            MadreRuntime runtime = runtime(registry, kernel);
            assertThrows(IllegalArgumentException.class, () -> runtime.invoke(target.call("x")));
        }
    }

    @Test
    void explicitForeignAgentSelectionCannotBypassExposure() {
        SingleOperationModule target = new SingleOperationModule("explicit-private", false, 0,
                new AtomicReference<>());
        SingleOperationModule foreign = new SingleOperationModule("foreign", false, 1,
                new AtomicReference<>());
        RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
        registry.install(target.instance());
        registry.install(foreign.instance());

        try (InferenceKernel kernel = kernel("explicit")) {
            MadreRuntime runtime = runtime(registry, kernel);
            assertThrows(IllegalArgumentException.class, () ->
                    runtime.invoke(target.call("x"), Optional.of(foreign.agent(0).id())));
        }
    }

    @Test
    void nestedForeignInvocationKeepsActorAndRechecksTargetExposure() {
        AtomicReference<AgentContext> publicNestedContext = new AtomicReference<>();
        NestedModule target = new NestedModule("nested-target", publicNestedContext);
        SingleOperationModule core = new SingleOperationModule("nested-core", false, 1,
                new AtomicReference<>());
        RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
        registry.install(target.instance());
        registry.install(core.instance());
        registry.assignCore(core.id(), core.agent(0).id());

        try (InferenceKernel kernel = kernel("nested")) {
            MadreRuntime runtime = runtime(registry, kernel);

            Material<String> publicResult = runtime.invoke(target.outerCall("public"))
                    .toCompletableFuture().join();
            assertEquals("PUBLIC", publicResult.payload());
            assertSame(core.agent(0), publicNestedContext.get().actor());

            assertThrows(IllegalArgumentException.class,
                    () -> runtime.invoke(target.outerCall("private")));
        }
    }

    private InferenceKernel kernel(String name) {
        return new InferenceKernel(temporary.resolve(name + ".db"), 1, Map.of());
    }

    private MadreRuntime runtime(RuntimeModuleRegistry registry, InferenceKernel kernel) {
        return new MadreRuntime(registry,
                new RuntimeInferenceService(kernel, temporary.resolve("semantic")), temporary);
    }

    private static final class SingleOperationModule implements Module {
        private final ModuleId id;
        private final MaterialType<String> text;
        private final OperationDefinition operation;
        private final OperationBinding<String, String> binding;
        private final boolean exposed;
        private final List<TestAgent> agents;

        private SingleOperationModule(String name, boolean exposed, int agentCount,
                AtomicReference<AgentContext> observed) {
            this.id = new ModuleId(name);
            this.text = new MaterialType<>(new MaterialTypeId(id, "text"), String.class,
                    "text/plain", MaterialCodecs.utf8String());
            this.operation = operation(new OperationId(id, "uppercase"), "Uppercase text");
            this.binding = OperationBinding.operation(operation, Operation.of((context, call) -> {
                observed.set(context);
                return CompletableFuture.completedFuture(material("output",
                        call.input().payload().toUpperCase(java.util.Locale.ROOT)));
            }));
            this.exposed = exposed;
            List<TestAgent> values = new ArrayList<>();
            for (int index = 0; index < agentCount; index++) {
                values.add(new TestAgent(new AgentId(id, "agent-" + index),
                        Set.of(operation.id())));
            }
            this.agents = List.copyOf(values);
        }

        private TestAgent agent(int index) { return agents.get(index); }

        private OperationCall<String, String> call(String payload) {
            return OperationCall.withoutEffect(operation, material("input", payload));
        }

        private Material<String> material(String name, String payload) {
            return new Material<>(new MaterialId(id, name), text, payload, Sensitivity.S5);
        }

        private OperationDefinition operation(OperationId operationId, String purpose) {
            return new OperationDefinition(operationId, purpose,
                    Map.of(text.id(), Privacy.SECRET),
                    Map.of(text.id(), Sensitivity.S5), Map.of());
        }

        @Override public ModuleId id() { return id; }
        @Override public String version() { return "1"; }
        @Override public String purpose() { return "execution-boundary fixture"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() { return Set.of(text); }
        @Override public Collection<? extends Agent> agents() { return agents; }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() { return Set.of(binding); }
        @Override public Set<OperationId> exposedOperations() {
            return exposed ? Set.of(operation.id()) : Set.of();
        }
    }

    private static final class NestedModule implements Module {
        private final ModuleId id;
        private final MaterialType<String> text;
        private final OperationDefinition outer;
        private final OperationDefinition publicNested;
        private final OperationDefinition privateNested;
        private final OperationBinding<String, String> outerBinding;
        private final OperationBinding<String, String> publicBinding;
        private final OperationBinding<String, String> privateBinding;

        private NestedModule(String name, AtomicReference<AgentContext> publicNestedContext) {
            id = new ModuleId(name);
            text = new MaterialType<>(new MaterialTypeId(id, "text"), String.class,
                    "text/plain", MaterialCodecs.utf8String());
            outer = operation(new OperationId(id, "outer"), "Invoke another operation");
            publicNested = operation(new OperationId(id, "public-nested"), "Public nested operation");
            privateNested = operation(new OperationId(id, "private-nested"), "Private nested operation");
            outerBinding = OperationBinding.operation(outer, Operation.of((context, call) -> {
                OperationDefinition nested = "private".equals(call.input().payload())
                        ? privateNested : publicNested;
                return context.modules().invoke(OperationCall.withoutEffect(nested, call.input()));
            }));
            publicBinding = OperationBinding.operation(publicNested, Operation.of((context, call) -> {
                publicNestedContext.set(context);
                return CompletableFuture.completedFuture(material("public-output", "PUBLIC"));
            }));
            privateBinding = OperationBinding.operation(privateNested, Operation.of((context, call) ->
                    CompletableFuture.completedFuture(material("private-output", "PRIVATE"))));
        }

        private OperationCall<String, String> outerCall(String payload) {
            return OperationCall.withoutEffect(outer, material("input-" + payload, payload));
        }

        private Material<String> material(String name, String payload) {
            return new Material<>(new MaterialId(id, name), text, payload, Sensitivity.S5);
        }

        private OperationDefinition operation(OperationId operationId, String purpose) {
            return new OperationDefinition(operationId, purpose,
                    Map.of(text.id(), Privacy.SECRET),
                    Map.of(text.id(), Sensitivity.S5), Map.of());
        }

        @Override public ModuleId id() { return id; }
        @Override public String version() { return "1"; }
        @Override public String purpose() { return "nested execution-boundary fixture"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() { return Set.of(text); }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() {
            return Set.of(outerBinding, publicBinding, privateBinding);
        }
        @Override public Set<OperationId> exposedOperations() {
            return Set.of(outer.id(), publicNested.id());
        }
    }

    private record TestAgent(AgentId id, Set<OperationId> operations) implements Agent {
        @Override public String purpose() { return "test actor"; }
    }
}
