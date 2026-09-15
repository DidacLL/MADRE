package consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Privacy;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningPreferences;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningRetryPolicy;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.experimental.ModuleDefinitionBuilder;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.identity.OperationId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.OperationDefinition;
import io.github.didacll.madre.sdk.module.OperationVisibility;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.registration.ModuleProviderConfiguration;
import io.github.didacll.madre.sdk.testkit.ModuleTestContext;
import io.github.didacll.madre.sdk.testkit.ModuleTestHarness;
import io.github.didacll.madre.sdk.testkit.ProgrammableReasoningService;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
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

            Material<String> result = module.invoke(IndependentDefinition.REASON_OPERATION, input)
                    .toCompletableFuture().join();

            assertEquals("private:reasoned:fixture-model:hello", result.payload());
            assertEquals(Sensitivity.S4, result.sensitivity());
            assertTrue(testContext.stateDirectory().toFile().isDirectory());
        }
    }

    @Test
    void testkitConsumerCanDriveNonTextDurableReasoningDeterministically() {
        ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
                .respond(ScoreComputation.class, computation -> computation.value() * 2);
        OperationDefinition<String, String> operation = new OperationDefinition<>(
                new OperationId(IndependentDefinition.ID, "score-test"),
                "Test-only non-text reasoning origin", OperationVisibility.PRIVATE,
                Map.of(IndependentDefinition.REQUEST.id(), Privacy.SECRET),
                Map.of(IndependentDefinition.RESULT.id(), Sensitivity.S4), Map.of());
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
    void experimentalAuthoringStillProducesStableSdkDomainObjects() {
        var definition = ModuleDefinitionBuilder.module(
                        IndependentDefinition.ID, "0.0.0-test", "Experimental authoring proof")
                .materialType(IndependentDefinition.REQUEST)
                .materialType(IndependentDefinition.RESULT)
                .build();

        assertEquals(IndependentDefinition.ID, definition.id());
        assertEquals(2, definition.materialTypes().size());
    }

    private record ScoreComputation(int value) implements ReasoningComputation<Integer> {
        @Override public Class<Integer> resultType() { return Integer.class; }
    }
}
