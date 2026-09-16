package consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.embedding.EmbeddingSpace;
import io.github.didacll.madre.embedding.TextEmbeddingCommand;
import io.github.didacll.madre.embedding.TextEmbeddingResult;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationMessage;
import io.github.didacll.madre.generation.TextGenerationResult;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import io.github.didacll.madre.sdk.testkit.ModuleTestContext;
import io.github.didacll.madre.sdk.testkit.ModuleTestHarness;
import io.github.didacll.madre.sdk.testkit.ProgrammableReasoningService;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class IndependentModuleTest {
    @Test
    void testsReasoningBackedModuleSemanticsWithoutKernelOrApplicationInternals() {
        ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
                .respond(TextInferenceCommand.class, command -> new TextInferenceResult(
                        "model:" + command.prompt(), TextInferenceResult.CompletionReason.STOP,
                        -1, -1));

        try (ModuleTestContext testContext = ModuleTestContext.create(reasoning)) {
            ModuleTestHarness module = ModuleTestHarness.materialize(
                    new IndependentModuleProvider(), testContext.context(),
                    new ModuleProviderConfiguration(IndependentDefinition.ID,
                            Map.of("result-prefix", "fixture-")));
            Material<String> input = new Material<>(
                    new MaterialId(IndependentDefinition.ID, "reasoning-test-input"),
                    IndependentDefinition.REQUEST, "hello", Sensitivity.S2);

            Material<String> result = module.<String, String>invoke(
                    IndependentDefinition.REASON_OPERATION, input)
                    .toCompletableFuture().join();

            assertEquals("private:reasoned:fixture-model:hello", result.payload());
            assertEquals(Sensitivity.S4, result.sensitivity());
            assertTrue(testContext.stateDirectory().toFile().isDirectory());
        }
    }

    @Test
    void agentlessWorkspaceOwnsDurableReadWriteBehaviorAndExposure() {
        try (ModuleTestContext testContext = ModuleTestContext.create(
                new ProgrammableReasoningService())) {
            ModuleTestHarness module = ModuleTestHarness.materialize(
                    new IndependentModuleProvider(), testContext.context(),
                    new ModuleProviderConfiguration(IndependentDefinition.ID, Map.of()));

            assertTrue(module.instance().definition().agents().isEmpty());
            assertEquals(Set.of(IndependentDefinition.SAVE_NOTE,
                            IndependentDefinition.COUNT_NOTES, IndependentDefinition.PRIVATE_NOTES),
                    module.instance().definition().exposedOperations());
            assertFalse(module.instance().definition().exposedOperations()
                    .contains(IndependentDefinition.RESET_NOTES));

            Material<String> save = new Material<>(
                    new MaterialId(IndependentDefinition.ID, "workspace-save"),
                    IndependentDefinition.WORKSPACE_COMMAND,
                    "Save this workspace note: source-independent R4 evidence", Sensitivity.S2);
            Material<String> saved = module.<String, String>invoke(OperationCall.withEffect(
                    IndependentDefinition.SAVE_OPERATION, IndependentDefinition.SAVE_PROFILE, save,
                    List.of(Integrity.I1))).toCompletableFuture().join();
            assertEquals("Saved 1 workspace note.", saved.payload());

            Material<String> count = new Material<>(
                    new MaterialId(IndependentDefinition.ID, "workspace-count"),
                    IndependentDefinition.WORKSPACE_COMMAND,
                    "Report how many workspace notes are saved", Sensitivity.S2);
            Material<String> counted = module.<String, String>invoke(OperationCall.withoutEffect(
                    IndependentDefinition.COUNT_OPERATION, count)).toCompletableFuture().join();
            assertEquals("There is 1 saved workspace note.", counted.payload());
        }
    }

    @Test
    void stableHeterogeneousContractsWorkThroughThePublicTestkit() {
        EmbeddingSpace space = new EmbeddingSpace("consumer.embedding/space-v1", 2);
        ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
                .respond(TextGenerationCommand.class, command -> new TextGenerationResult(
                        command.messages().getLast().content().toUpperCase(),
                        TextGenerationResult.CompletionReason.STOP, -1, -1))
                .respond(TextEmbeddingCommand.class, command ->
                        new TextEmbeddingResult(command.space(), List.of(0.25, -0.5)));
        OperationDefinition operation = testReasoningOperation("heterogeneous-test");
        Material<String> input = new Material<>(
                new MaterialId(IndependentDefinition.ID, "heterogeneous-test-input"),
                IndependentDefinition.REQUEST, "ignored", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(operation, input);

        ReasoningRequest<TextGenerationResult, TextGenerationCommand> generation =
                ReasoningRequest.immediate(call, new TextGenerationCommand(List.of(
                        new TextGenerationMessage(TextGenerationMessage.Role.SYSTEM, "Be concise."),
                        new TextGenerationMessage(TextGenerationMessage.Role.USER, "hello")),
                        16, List.of()), 0, Duration.ofSeconds(1), ReasoningRetryPolicy.none(),
                        Optional.empty(), ReasoningPreferences.unconstrained());
        assertEquals("HELLO", reasoning.execute(generation).toCompletableFuture().join().text());

        ReasoningRequest<TextEmbeddingResult, TextEmbeddingCommand> embedding =
                ReasoningRequest.immediate(call, new TextEmbeddingCommand("hello", space),
                        0, Duration.ofSeconds(1), ReasoningRetryPolicy.none(), Optional.empty(),
                        ReasoningPreferences.unconstrained());
        TextEmbeddingResult embedded = reasoning.execute(embedding).toCompletableFuture().join();
        assertEquals(space, embedded.space());
        assertEquals(List.of(0.25, -0.5), embedded.values());
    }

    @Test
    void testkitConsumerCanDriveNonTextDurableReasoningDeterministically() {
        ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
                .respond(ScoreComputation.class, computation -> computation.value() * 2);
        OperationDefinition operation = testReasoningOperation("score-test");
        Material<String> input = new Material<>(
                new MaterialId(IndependentDefinition.ID, "score-test-input"),
                IndependentDefinition.REQUEST, "ignored", Sensitivity.S2);
        OperationCall<String, String> call = OperationCall.withoutEffect(operation, input);
        ReasoningRequest<Integer, ScoreComputation> request = ReasoningRequest.durable(
                call, new ScoreComputation(21), 0, Instant.EPOCH, Duration.ofSeconds(1),
                ReasoningRetryPolicy.none(), Optional.empty(), ReasoningPreferences.unconstrained());

        var workId = reasoning.submit(request);
        assertEquals(WorkState.QUEUED, reasoning.inspect(workId).orElseThrow().state());
        assertTrue(reasoning.complete(workId));
        assertEquals(42, reasoning.collect(workId, Integer.class).orElseThrow());
        assertTrue(reasoning.acknowledge(workId));
        assertFalse(reasoning.inspect(workId).isPresent());
    }

    @Test
    void codeFirstAuthoringProjectsPortableSdkDomainObjects() {
        var module = IndependentDefinition.create(new ProgrammableReasoningService(), "fixture-");
        var definition = module.definition();

        assertEquals(IndependentDefinition.ID, definition.id());
        assertEquals(5, definition.materialTypes().size());
        assertEquals(6, definition.operations().size());
        assertEquals("text/plain; charset=utf-8",
                definition.materialTypes().get(IndependentDefinition.REQUEST.id()).contentType());
    }

    private static OperationDefinition testReasoningOperation(String name) {
        return new OperationDefinition(new OperationId(IndependentDefinition.ID, name),
                "Test-only reasoning origin",
                Map.of(IndependentDefinition.REQUEST.id(), Privacy.SECRET),
                Map.of(IndependentDefinition.RESULT.id(), Sensitivity.S4), Map.of());
    }

    private record ScoreComputation(int value) implements ReasoningComputation<Integer> {
        @Override public Class<Integer> resultType() { return Integer.class; }
    }
}
