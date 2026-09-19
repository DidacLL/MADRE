package io.github.didacll.madre.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.kernel.EngineAvailability;
import io.github.didacll.madre.kernel.EngineCharacteristics;
import io.github.didacll.madre.kernel.EngineExecution;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.EngineLocation;
import io.github.didacll.madre.kernel.InferenceEngine;
import io.github.didacll.madre.kernel.InferenceType;
import io.github.didacll.madre.kernel.InferenceTypes;
import io.github.didacll.madre.kernel.TextInferenceInput;
import io.github.didacll.madre.kernel.TextInferenceOutput;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.sdk.execution.InferenceSelection;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
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
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
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
        registry.assignCore(core.id(), core.agent.id());

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

    @Test
    void ownerExactEngineSelectionCrossesAsTechnicalRequirementAndFailsHonestly() {
        AtomicInteger executions = new AtomicInteger();
        try (InferenceKernel kernel = new InferenceKernel(temporary.resolve("selection.db"), 1,
                Map.of())) {
            kernel.register(new TestTextEngine("installed", executions));
            RuntimeInferenceService inference = new RuntimeInferenceService(kernel,
                    temporary.resolve("selection-semantic"));
            TestModule source = new TestModule("source", true, new AtomicReference<>());
            OperationCall<String, String> call = OperationCall.withoutEffect(source.operation,
                    source.material("request", "hard question"));
            ReasoningPreferences preferences = new ReasoningPreferences(Optional.empty(),
                    Optional.empty(), Optional.of(InferenceSelection.engine("missing")));
            ReasoningRequest<?, ?> request = ReasoningRequest.immediate(call,
                    TextGenerationCommand.demandingPrompt("hard question", 64, 2048), 100,
                    Duration.ofSeconds(1), ReasoningRetryPolicy.none(), Optional.empty(),
                    preferences);

            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> inference.forAgent(source.agent.id()).execute(request)
                            .toCompletableFuture().join());
            assertEquals(0, executions.get());
        }
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
                        io.github.didacll.madre.sdk.module.AgentContext context,
                        OperationCall<String, String> call) {
                    actor.set(context.actor());
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
        @Override public Set<OperationId> exposedOperations() { return Set.of(operation.id()); }
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

    private static final class TestTextEngine
            implements InferenceEngine<TextInferenceInput, TextInferenceOutput> {
        private final EngineId id;
        private final AtomicInteger executions;

        private TestTextEngine(String id, AtomicInteger executions) {
            this.id = new EngineId(id);
            this.executions = executions;
        }
        @Override public EngineId id() { return id; }
        @Override public InferenceType<TextInferenceInput, TextInferenceOutput> type() {
            return InferenceTypes.TEXT_GENERATION;
        }
        @Override public EngineCharacteristics characteristics() {
            return new EngineCharacteristics(EngineLocation.LOCAL, "test", id.value(),
                    Duration.ofMillis(10), 0,
                    Optional.of(new EngineCharacteristics.TextCapability(true, 4096)),
                    Optional.empty(), Optional.empty());
        }
        @Override public EngineAvailability availability() { return EngineAvailability.AVAILABLE; }
        @Override public TextInferenceOutput execute(TextInferenceInput input,
                EngineExecution execution) {
            executions.incrementAndGet();
            return new TextInferenceOutput("unexpected", TextInferenceOutput.FinishReason.COMPLETE,
                    TextInferenceOutput.TokenUsage.unavailable());
        }
    }
}
