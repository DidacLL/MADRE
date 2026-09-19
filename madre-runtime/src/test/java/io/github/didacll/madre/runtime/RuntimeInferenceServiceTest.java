package io.github.didacll.madre.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationMessage;
import io.github.didacll.madre.generation.TextGenerationResult;
import io.github.didacll.madre.kernel.ChatCompletionInput;
import io.github.didacll.madre.kernel.ChatCompletionOutput;
import io.github.didacll.madre.kernel.EngineAvailability;
import io.github.didacll.madre.kernel.EngineCharacteristics;
import io.github.didacll.madre.kernel.EngineExecution;
import io.github.didacll.madre.kernel.EngineId;
import io.github.didacll.madre.kernel.InferenceEngine;
import io.github.didacll.madre.kernel.InferenceKernel;
import io.github.didacll.madre.kernel.InferenceType;
import io.github.didacll.madre.kernel.InferenceTypes;
import io.github.didacll.madre.sdk.execution.InferenceSelection;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
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
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.OperationCall;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RuntimeInferenceServiceTest {
    @TempDir Path temporary;

    @Test
    void configuredDefaultResolvesToExactPhysicalFactsAndPreservesChatContract() {
        AtomicInteger otherExecutions = new AtomicInteger();
        AtomicInteger chosenExecutions = new AtomicInteger();
        AtomicReference<ChatCompletionInput> observed = new AtomicReference<>();

        try (InferenceKernel kernel = kernel("default")) {
            kernel.register(new CapturingEngine(
                    "other", "other-provider", "other-model",
                    URI.create("https://other.example/api"), otherExecutions,
                    new AtomicReference<>()));
            kernel.register(new CapturingEngine(
                    "chosen", "chosen-provider", "chosen-model",
                    URI.create("https://chosen.example/api"), chosenExecutions, observed));

            InferenceSelection configuredDefault = new InferenceSelection(
                    Optional.empty(),
                    Optional.of("chosen-provider"),
                    Optional.of("chosen-model"),
                    Optional.of("https://chosen.example/api"));
            RuntimeInferenceService service = new RuntimeInferenceService(
                    kernel, temporary.resolve("semantic-default"),
                    Optional.of(configuredDefault));

            Fixture fixture = fixture();
            TextGenerationCommand command = new TextGenerationCommand(
                    List.of(
                            new TextGenerationMessage(
                                    TextGenerationMessage.Role.SYSTEM, "system context"),
                            new TextGenerationMessage(
                                    TextGenerationMessage.Role.USER, "owner question")),
                    77,
                    List.of("STOP-A", "STOP-B"),
                    2048);
            ReasoningRequest<TextGenerationResult, TextGenerationCommand> request =
                    ReasoningRequest.immediate(
                            fixture.actor(), fixture.call(), command, 10,
                            Duration.ofSeconds(2), ReasoningRetryPolicy.none(),
                            Optional.empty(),
                            new ReasoningPreferences(
                                    Optional.of(Duration.ofSeconds(1)), Optional.empty()));

            TextGenerationResult result = service.forAgent(fixture.actor().id())
                    .execute(request).toCompletableFuture().join();

            assertEquals("chosen-answer", result.text());
            assertEquals(0, otherExecutions.get());
            assertEquals(1, chosenExecutions.get());
            ChatCompletionInput input = observed.get();
            assertEquals(2, input.messages().size());
            assertEquals("system context", input.messages().get(0).text());
            assertEquals("owner question", input.messages().get(1).text());
            assertEquals(77, input.maximumOutputTokens().orElseThrow());
            assertEquals(List.of("STOP-A", "STOP-B"), input.stopSequences());
        }
    }

    @Test
    void concreteChatIntentMayLeaveMultiplePhysicalEnginesForKernelSelection() {
        AtomicInteger firstExecutions = new AtomicInteger();
        AtomicInteger secondExecutions = new AtomicInteger();
        try (InferenceKernel kernel = kernel("multiple")) {
            kernel.register(new CapturingEngine(
                    "z-engine", "provider-z", "model-z",
                    URI.create("https://z.example/api"), secondExecutions,
                    new AtomicReference<>()));
            kernel.register(new CapturingEngine(
                    "a-engine", "provider-a", "model-a",
                    URI.create("https://a.example/api"), firstExecutions,
                    new AtomicReference<>()));
            RuntimeInferenceService service = new RuntimeInferenceService(
                    kernel, temporary.resolve("semantic-multiple"));

            Fixture fixture = fixture();
            ReasoningRequest<TextGenerationResult, TextGenerationCommand> request =
                    ReasoningRequest.immediate(
                            fixture.actor(), fixture.call(),
                            TextGenerationCommand.prompt("question", 32), 10,
                            Duration.ofSeconds(2), ReasoningRetryPolicy.none(),
                            Optional.empty(), ReasoningPreferences.requirements());

            service.forAgent(fixture.actor().id()).execute(request)
                    .toCompletableFuture().join();

            assertEquals(1, firstExecutions.get());
            assertEquals(0, secondExecutions.get());
        }
    }

    @Test
    void durableSemanticRequestRecoversWithCurrentChatContract() throws Exception {
        Path database = temporary.resolve("durable.db");
        Path semantic = temporary.resolve("semantic-durable");
        Fixture fixture = fixture();
        TextGenerationCommand command = new TextGenerationCommand(
                List.of(new TextGenerationMessage(
                        TextGenerationMessage.Role.USER, "recover me")),
                41, List.of("DONE"), 1024);
        ReasoningPreferences exact = new ReasoningPreferences(
                Optional.empty(),
                Optional.of(InferenceSelection.engine("restart-engine")));
        ReasoningRequest<TextGenerationResult, TextGenerationCommand> request =
                ReasoningRequest.durable(
                        fixture.actor(), fixture.call(), command, 10,
                        Instant.now().plusMillis(400), Duration.ofSeconds(2),
                        ReasoningRetryPolicy.none(), Optional.empty(), exact);

        io.github.didacll.madre.sdk.execution.WorkId workId;
        try (InferenceKernel first = new InferenceKernel(database, 1, Map.of())) {
            first.register(new CapturingEngine(
                    "restart-engine", "provider", "model",
                    URI.create("https://restart.example/api"), new AtomicInteger(),
                    new AtomicReference<>()));
            RuntimeInferenceService service =
                    new RuntimeInferenceService(first, semantic);
            workId = service.forAgent(fixture.actor().id()).submit(request);
        }

        AtomicReference<ChatCompletionInput> recoveredInput = new AtomicReference<>();
        try (InferenceKernel second = new InferenceKernel(database, 1, Map.of())) {
            second.register(new CapturingEngine(
                    "restart-engine", "provider", "model",
                    URI.create("https://restart.example/api"), new AtomicInteger(),
                    recoveredInput));
            RuntimeInferenceService recovered =
                    new RuntimeInferenceService(second, semantic);
            recovered.recoverPending();

            Optional<TextGenerationResult> result = Optional.empty();
            for (int index = 0; index < 300 && result.isEmpty(); index++) {
                Thread.sleep(10);
                result = recovered.forAgent(fixture.actor().id())
                        .collect(workId, TextGenerationResult.class);
            }

            assertTrue(result.isPresent());
            assertEquals("answer", result.orElseThrow().text());
            assertEquals(41, recoveredInput.get().maximumOutputTokens().orElseThrow());
            assertEquals(List.of("DONE"), recoveredInput.get().stopSequences());
            assertEquals("recover me", recoveredInput.get().messages().get(0).text());
        }
    }

    @Test
    void unsupportedSemanticComputationDoesNotBecomeGenericInferenceWork() {
        try (InferenceKernel kernel = kernel("unsupported")) {
            kernel.register(new CapturingEngine(
                    "chat", "provider", "model",
                    URI.create("https://chat.example/api"), new AtomicInteger(),
                    new AtomicReference<>()));
            RuntimeInferenceService service = new RuntimeInferenceService(
                    kernel, temporary.resolve("semantic-unsupported"));
            Fixture fixture = fixture();
            ReasoningRequest<String, FixtureComputation> request =
                    ReasoningRequest.immediate(
                            fixture.actor(), fixture.call(),
                            new FixtureComputation(), 10, Duration.ofSeconds(1),
                            ReasoningRetryPolicy.none(), Optional.empty(),
                            ReasoningPreferences.requirements());

            assertThrows(IllegalArgumentException.class,
                    () -> service.forAgent(fixture.actor().id()).execute(request));
        }
    }

    private InferenceKernel kernel(String name) {
        return new InferenceKernel(
                temporary.resolve(name + ".db"), 1, Map.of());
    }

    private static Fixture fixture() {
        ModuleId module = new ModuleId("reasoning.fixture");
        MaterialType<String> text = new MaterialType<>(
                new MaterialTypeId(module, "text"), String.class,
                "text/plain", MaterialCodecs.utf8String());
        OperationId operationId = new OperationId(module, "respond");
        OperationDefinition operation = new OperationDefinition(
                operationId, "Respond", Map.of(text.id(), Privacy.SECRET),
                Map.of(text.id(), Sensitivity.S5), Map.of());
        Material<String> input = new Material<>(
                new MaterialId(module, "input"), text, "question",
                Sensitivity.S4);
        Agent actor = new Agent() {
            @Override public AgentId id() {
                return new AgentId(module, "agent");
            }
            @Override public String purpose() {
                return "Own reasoning";
            }
            @Override public Integrity integrity() {
                return Integrity.I3;
            }
            @Override public Set<OperationId> operations() {
                return Set.of(operationId);
            }
        };
        return new Fixture(
                actor, OperationCall.withoutEffect(operation, input));
    }

    private record Fixture(Agent actor, OperationCall<String, String> call) { }

    private record FixtureComputation() implements ReasoningComputation<String> {
        @Override public Class<String> resultType() {
            return String.class;
        }
    }

    private static final class CapturingEngine
            implements InferenceEngine<ChatCompletionInput, ChatCompletionOutput> {
        private final EngineId id;
        private final EngineCharacteristics characteristics;
        private final AtomicInteger executions;
        private final AtomicReference<ChatCompletionInput> observed;

        private CapturingEngine(
                String id,
                String provider,
                String model,
                URI endpoint,
                AtomicInteger executions,
                AtomicReference<ChatCompletionInput> observed) {
            this.id = new EngineId(id);
            this.characteristics = new EngineCharacteristics(
                    provider, model, endpoint, Duration.ofMillis(10),
                    Optional.of(
                            new EngineCharacteristics.ChatCompletionCapability(8192)));
            this.executions = executions;
            this.observed = observed;
        }

        @Override public EngineId id() {
            return id;
        }

        @Override public InferenceType<ChatCompletionInput, ChatCompletionOutput> type() {
            return InferenceTypes.CHAT_COMPLETION;
        }

        @Override public EngineCharacteristics characteristics() {
            return characteristics;
        }

        @Override public EngineAvailability availability() {
            return EngineAvailability.AVAILABLE;
        }

        @Override public ChatCompletionOutput execute(
                ChatCompletionInput input, EngineExecution execution) {
            executions.incrementAndGet();
            observed.set(input);
            return new ChatCompletionOutput(
                    id.value().equals("chosen")
                            ? "chosen-answer"
                            : "answer",
                    ChatCompletionOutput.FinishReason.ENGINE_STOP,
                    ChatCompletionOutput.TokenUsage.unavailable());
        }
    }
}
