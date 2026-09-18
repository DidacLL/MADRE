package io.github.didacll.madre.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Integrity;
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
import io.github.didacll.madre.sdk.module.Module;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.Operation;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreRuntimeAgencyTest {
    @TempDir Path temporary;

    @Test
    void agentlessModuleFallsBackToCoreAgentAndRetainsItsMaterialOwnership() {
        AtomicReference<Agent> observedActor = new AtomicReference<>();
        TestModule target = new TestModule("target", false, new AtomicReference<>());
        TestModule core = new TestModule("core", true, observedActor);
        RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
        registry.install(target.instance());
        registry.install(core.instance());
        registry.assignCore(core.id());

        try (InferenceKernel kernel = new InferenceKernel(temporary.resolve("kernel.db"), 1,
                Map.of())) {
            MadreRuntime runtime = new MadreRuntime(registry,
                    new RuntimeInferenceService(kernel, temporary.resolve("semantic")), temporary);
            Material<String> input = target.material("input", "hello");
            OperationCall<String, String> call = OperationCall.withoutEffect(
                    target.operation, input);
            Material<String> output = runtime.invoke(call).toCompletableFuture().join();

            assertSame(core.agent, observedActor.get());
            assertEquals(target.id(), output.id().moduleId());
            assertEquals("HELLO", output.payload());
        }
    }

    @Test
    void missingAndAmbiguousAgencyFailExplicitly() {
        RuntimeModuleRegistry missing = new RuntimeModuleRegistry();
        TestModule agentless = new TestModule("agentless", false, new AtomicReference<>());
        missing.install(agentless.instance());
        assertThrows(AgentResolutionException.class,
                () -> missing.resolveAgent(agentless.operation.id(), java.util.Optional.empty()));

        RuntimeModuleRegistry ambiguous = new RuntimeModuleRegistry();
        TestModule twoAgents = new TestModule("ambiguous", true, new AtomicReference<>(), true);
        ambiguous.install(twoAgents.instance());
        assertThrows(AgentResolutionException.class,
                () -> ambiguous.resolveAgent(twoAgents.operation.id(), java.util.Optional.empty()));
    }

    private static final class TestModule implements Module {
        private final ModuleId id;
        private final MaterialType<String> text;
        private final OperationDefinition operation;
        private final OperationBinding<String, String> binding;
        private final TestAgent agent;
        private final boolean secondAgent;

        private TestModule(String name, boolean hasAgent, AtomicReference<Agent> actor) {
            this(name, hasAgent, actor, false);
        }

        private TestModule(String name, boolean hasAgent, AtomicReference<Agent> actor,
                boolean secondAgent) {
            id = new ModuleId(name);
            text = new MaterialType<>(new MaterialTypeId(id, "text"), String.class,
                    "text/plain", MaterialCodecs.utf8String());
            operation = new OperationDefinition(new OperationId(id, "uppercase"), "Uppercase text",
                    Map.of(text.id(), Privacy.SECRET), Map.of(text.id(), Sensitivity.S5), Map.of());
            binding = OperationBinding.operation(operation, new Operation<>() {
                @Override protected java.util.concurrent.CompletionStage<Material<String>> execute(
                        OperationCall<String, String> call) {
                    return CompletableFuture.completedFuture(material("output",
                            call.input().payload().toUpperCase(java.util.Locale.ROOT)));
                }
            });
            agent = hasAgent ? new TestAgent(new AgentId(id, "agent"), operation.id(), actor) : null;
            this.secondAgent = secondAgent;
        }

        private Material<String> material(String name, String payload) {
            return new Material<>(new MaterialId(id, name), text, payload, Sensitivity.S5);
        }
        @Override public ModuleId id() { return id; }
        @Override public String version() { return "1"; }
        @Override public String purpose() { return "test"; }
        @Override public Collection<? extends MaterialType<?>> materialTypes() { return Set.of(text); }
        @Override public Collection<? extends Agent> agents() {
            if (agent == null) return Set.of();
            if (!secondAgent) return Set.of(agent);
            return Set.of(agent, new TestAgent(new AgentId(id, "other"), operation.id(),
                    new AtomicReference<>()));
        }
        @Override public Collection<? extends OperationBinding<?, ?>> operations() { return Set.of(binding); }
    }

    private record TestAgent(AgentId id, OperationId operation,
            AtomicReference<Agent> observer) implements Agent {
        @Override public String purpose() { return "test actor"; }
        @Override public Integrity integrity() { return Integrity.I3; }
        @Override public Set<OperationId> operations() { return Set.of(operation); }
        @Override public <I, O> java.util.concurrent.CompletionStage<Material<O>> execute(
                io.github.didacll.madre.sdk.module.AgentContext context,
                OperationBinding<I, O> binding, OperationCall<I, O> call) {
            observer.set(this);
            return Agent.super.execute(context, binding, call);
        }
    }
}
