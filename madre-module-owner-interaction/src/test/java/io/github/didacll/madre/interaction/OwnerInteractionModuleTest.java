package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.codec.ModuleDefinitionJsonCodec;
import io.github.didacll.madre.sdk.execution.ExecutionMode;
import io.github.didacll.madre.sdk.execution.ExecutionService;
import io.github.didacll.madre.sdk.execution.PhysicalFailureCategory;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkRequest;
import io.github.didacll.madre.sdk.execution.WorkState;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.text.TextInferenceCommand;
import io.github.didacll.madre.text.TextInferenceResult;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OwnerInteractionModuleTest {
    @TempDir Path temporary;

    @Test void standardPromptUsesPhysicalPortAndCreatesIndependentMaterial() {
        RecordingExecution execution = new RecordingExecution();
        execution.immediate.complete(result("A bounded answer"));
        OwnerInteractionModule module = new OwnerInteractionModule(execution,
                temporary.resolve("state"));
        Material<String> prompt = module.ownerPrompt("Explain the invariant", Sensitivity.S4);

        Material<String> answer = module.standardPrompt(prompt).toCompletableFuture().join();

        assertEquals(ExecutionMode.IMMEDIATE, execution.immediateRequest.mode());
        assertEquals("Explain the invariant",
                ((TextInferenceCommand) execution.immediateRequest.command()).prompt());
        assertEquals(OwnerInteractionModule.IMMEDIATE_ANSWER, answer.type());
        assertEquals(Sensitivity.S4, answer.sensitivity());
        assertNotEquals(prompt.id(), answer.id());
    }

    @Test void fastLaneReturnsForegroundWithoutWaitingForDurableAnalysis() {
        RecordingExecution execution = new RecordingExecution();
        OwnerInteractionModule module = new OwnerInteractionModule(execution,
                temporary.resolve("state"));
        CompletionStage<Material<String>> foreground = module.fastLane(
                module.ownerPrompt("Find the hard part", Sensitivity.S3));

        assertFalse(foreground.toCompletableFuture().isDone());
        assertEquals(1, execution.durableRequests.size());
        assertEquals(1, module.pendingBackgroundCount());

        execution.immediate.complete(result("Immediate result"));
        assertEquals("Immediate result", foreground.toCompletableFuture().join().payload());
        assertEquals(ExecutionMode.DURABLE, execution.durableRequests.get(0).mode());
        assertTrue(((TextInferenceCommand) execution.durableRequests.get(0).command())
                .prompt().contains("NO_FOLLOW_UP"));
    }

    @Test void shippedModuleInterpretsAndPersistsBackgroundAcrossRestart() {
        RecordingExecution execution = new RecordingExecution();
        execution.immediate.complete(result("Immediate"));
        Path state = temporary.resolve("core-state");
        OwnerInteractionModule first = new OwnerInteractionModule(execution, state);
        first.fastLane(first.ownerPrompt("Continue this", Sensitivity.S5)).toCompletableFuture().join();
        WorkId work = execution.lastSubmitted;
        execution.complete(work, result("A useful correction"));

        OwnerInteractionModule restarted = new OwnerInteractionModule(execution, state);
        List<BackgroundUpdate> updates = restarted.collectBackground();

        assertEquals(1, updates.size());
        assertEquals(OwnerInteractionModule.BACKGROUND_ANALYSIS,
                updates.get(0).backgroundAnalysis().orElseThrow().type());
        assertEquals(OwnerInteractionModule.VISIBLE_FOLLOW_UP,
                updates.get(0).visibleFollowUp().orElseThrow().type());
        assertEquals(Sensitivity.S5,
                updates.get(0).visibleFollowUp().orElseThrow().sensitivity());
        assertTrue(execution.acknowledged.contains(work));
        assertEquals(0, restarted.pendingBackgroundCount());
    }

    @Test void noFollowUpStopsSemanticContinuation() {
        RecordingExecution execution = new RecordingExecution();
        execution.immediate.complete(result("Immediate"));
        OwnerInteractionModule module = new OwnerInteractionModule(execution,
                temporary.resolve("state"));
        module.fastLane(module.ownerPrompt("Simple", Sensitivity.S2)).toCompletableFuture().join();
        execution.complete(execution.lastSubmitted, result("NO_FOLLOW_UP"));

        BackgroundUpdate update = module.collectBackground().get(0);
        assertTrue(update.backgroundAnalysis().isPresent());
        assertTrue(update.visibleFollowUp().isEmpty());
    }

    @Test void publicDefinitionRoundTripsWithoutBehavior() {
        OwnerInteractionModule module = new OwnerInteractionModule(
                new RecordingExecution(), temporary.resolve("state"));
        ModuleDefinitionJsonCodec codec = new ModuleDefinitionJsonCodec((id, contentType) ->
                module.definition().materialTypes().get(id));
        String encoded = codec.encode(module.definition());
        var decoded = codec.decode(encoded);

        assertEquals(OwnerInteractionModule.ID, decoded.id());
        assertEquals(2, decoded.operations().size());
        assertEquals(1, decoded.agents().size());
        assertFalse(encoded.contains("OwnerInteractionModule"));
        assertFalse(encoded.contains("NO_FOLLOW_UP"));
    }

    private static TextInferenceResult result(String text) {
        return new TextInferenceResult(text, TextInferenceResult.CompletionReason.STOP, 2, 3);
    }

    private static final class RecordingExecution implements ExecutionService {
        private final CompletableFuture<TextInferenceResult> immediate = new CompletableFuture<>();
        private final List<WorkRequest<?, ?>> durableRequests = new ArrayList<>();
        private final Map<WorkId, TextInferenceResult> results = new HashMap<>();
        private final List<WorkId> acknowledged = new ArrayList<>();
        private WorkRequest<?, ?> immediateRequest;
        private WorkId lastSubmitted;

        @Override public <C, R> CompletionStage<R> execute(WorkRequest<C, R> request) {
            immediateRequest = request;
            @SuppressWarnings("unchecked") CompletionStage<R> cast =
                    (CompletionStage<R>) (CompletionStage<?>) immediate;
            return cast;
        }

        @Override public <C, R> WorkId submit(WorkRequest<C, R> request) {
            durableRequests.add(request);
            lastSubmitted = WorkId.create();
            return lastSubmitted;
        }

        void complete(WorkId id, TextInferenceResult result) { results.put(id, result); }

        @Override public Optional<WorkStatus> inspect(WorkId id) {
            if (results.containsKey(id)) {
                return Optional.of(new WorkStatus(id, WorkState.SUCCEEDED, 1, Instant.now(),
                        Optional.empty(), Optional.of(Instant.now())));
            }
            return Optional.of(new WorkStatus(id, WorkState.QUEUED, 0, Instant.now(),
                    Optional.<PhysicalFailureCategory>empty(), Optional.empty()));
        }

        @Override public boolean cancel(WorkId id) { return false; }

        @Override public <R> Optional<R> collect(WorkId id, Class<R> resultType) {
            return Optional.ofNullable(results.get(id)).map(resultType::cast);
        }

        @Override public boolean acknowledge(WorkId id) {
            acknowledged.add(id);
            return results.remove(id) != null;
        }
    }
}
