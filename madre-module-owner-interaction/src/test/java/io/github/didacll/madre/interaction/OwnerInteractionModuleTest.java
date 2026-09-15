package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Autonomy;
import io.github.didacll.madre.algebra.Risk;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.codec.ModuleDefinitionJsonCodec;
import io.github.didacll.madre.sdk.execution.ExecutionMode;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningFailureCategory;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkId;
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

    @Test void standardPromptUsesReasoningPortAndCreatesIndependentMaterial() {
        RecordingReasoning reasoning = new RecordingReasoning();
        reasoning.immediate.complete(result("A bounded answer"));
        OwnerInteractionModule module = new OwnerInteractionModule(reasoning,
                temporary.resolve("state"));
        Material<String> prompt = module.ownerPrompt("Explain the invariant", Sensitivity.S4);

        Material<String> answer = module.standardPrompt(prompt).toCompletableFuture().join();

        assertEquals(ExecutionMode.IMMEDIATE, reasoning.immediateRequest.mode());
        assertEquals("Explain the invariant",
                ((TextInferenceCommand) reasoning.immediateRequest.computation()).prompt());
        assertEquals(Sensitivity.S4, reasoning.immediateRequest.carriedSensitivity());
        assertEquals(OwnerInteractionModule.IMMEDIATE_ANSWER, answer.type());
        assertEquals(Sensitivity.S4, answer.sensitivity());
        assertNotEquals(prompt.id(), answer.id());
    }

    @Test void laterTurnsUseActualContextMaterialAtCombinedSensitivity() {
        RecordingReasoning reasoning = new RecordingReasoning();
        reasoning.immediate.complete(result("Sensitive first answer"));
        OwnerInteractionModule module = new OwnerInteractionModule(reasoning,
                temporary.resolve("state"));
        module.standardPrompt(module.ownerPrompt("Sensitive first question", Sensitivity.S4))
                .toCompletableFuture().join();

        Material<String> second = module.standardPrompt(
                module.ownerPrompt("Low-labelled follow-up", Sensitivity.S2))
                .toCompletableFuture().join();
        TextInferenceCommand computation =
                (TextInferenceCommand) reasoning.immediateRequest.computation();

        assertTrue(computation.prompt().contains("Sensitive first question"));
        assertTrue(computation.prompt().contains("Sensitive first answer"));
        assertTrue(computation.prompt().contains("Low-labelled follow-up"));
        assertEquals(Sensitivity.S4, reasoning.immediateRequest.carriedSensitivity());
        assertEquals(Sensitivity.S4, second.sensitivity());
    }

    @Test void fastLaneUsesCombinedContextSensitivityForForegroundAndBackground() {
        RecordingReasoning reasoning = new RecordingReasoning();
        reasoning.immediate.complete(result("Sensitive first answer"));
        OwnerInteractionModule module = new OwnerInteractionModule(reasoning,
                temporary.resolve("state"));
        module.standardPrompt(module.ownerPrompt("Sensitive first question", Sensitivity.S4))
                .toCompletableFuture().join();

        Material<String> answer = module.fastLane(
                module.ownerPrompt("Continue cheaply", Sensitivity.S2)).toCompletableFuture().join();

        assertEquals(Sensitivity.S4, reasoning.immediateRequest.carriedSensitivity());
        assertEquals(Sensitivity.S4, reasoning.durableRequests.get(0).carriedSensitivity());
        assertEquals(Sensitivity.S4, answer.sensitivity());
        assertTrue(((TextInferenceCommand) reasoning.durableRequests.get(0).computation())
                .prompt().contains("Sensitive first question"));
    }

    @Test void conversationStateIsBoundedAndDurableAcrossRestart() {
        RecordingReasoning reasoning = new RecordingReasoning();
        reasoning.immediate.complete(result("Repeated answer"));
        Path state = temporary.resolve("state");
        OwnerInteractionModule module = new OwnerInteractionModule(reasoning, state);
        for (int turn = 0; turn < 6; turn++) {
            module.standardPrompt(module.ownerPrompt("turn-" + turn, Sensitivity.S2))
                    .toCompletableFuture().join();
        }
        String bounded = ((TextInferenceCommand) reasoning.immediateRequest.computation()).prompt();
        assertFalse(bounded.contains("turn-0"));
        assertTrue(bounded.contains("turn-1"));
        assertTrue(bounded.contains("turn-4"));
        assertTrue(bounded.contains("turn-5"));

        RecordingReasoning restartedReasoning = new RecordingReasoning();
        restartedReasoning.immediate.complete(result("Fresh runtime"));
        OwnerInteractionModule restarted = new OwnerInteractionModule(restartedReasoning, state);
        restarted.standardPrompt(restarted.ownerPrompt("after restart", Sensitivity.S1))
                .toCompletableFuture().join();
        String persisted = ((TextInferenceCommand) restartedReasoning.immediateRequest.computation())
                .prompt();
        assertFalse(persisted.contains("turn-1"));
        assertTrue(persisted.contains("turn-2"));
        assertTrue(persisted.contains("turn-5"));
        assertTrue(persisted.contains("after restart"));
        assertEquals(Sensitivity.S2, restartedReasoning.immediateRequest.carriedSensitivity());
    }

    @Test void effectProfilesDescribeConversationAndBackgroundConsequences() {
        OwnerInteractionModule module = new OwnerInteractionModule(new RecordingReasoning(),
                temporary.resolve("state"));
        var standard = module.definition().operations().get(OwnerInteractionModule.STANDARD_PROMPT);
        var fast = module.definition().operations().get(OwnerInteractionModule.FAST_LANE);
        var collect = module.definition().operations().get(OwnerInteractionModule.COLLECT_BACKGROUND);

        var standardProfile = standard.effectProfiles().values().iterator().next();
        assertEquals(Risk.WRITE, standardProfile.risk());
        assertEquals(Autonomy.LIVE_INTERACTION, standardProfile.autonomy());
        var fastProfile = fast.effectProfiles().values().iterator().next();
        assertEquals(Risk.WRITE, fastProfile.risk());
        assertEquals(Autonomy.AUTONOMOUS, fastProfile.autonomy());
        var collectProfile = collect.effectProfiles().values().iterator().next();
        assertEquals(Risk.DELETE, collectProfile.risk());
        assertEquals(Autonomy.LIVE_INTERACTION, collectProfile.autonomy());
    }

    @Test void fastLaneReturnsForegroundWithoutWaitingForDurableAnalysis() {
        RecordingReasoning reasoning = new RecordingReasoning();
        OwnerInteractionModule module = new OwnerInteractionModule(reasoning,
                temporary.resolve("state"));
        CompletionStage<Material<String>> foreground = module.fastLane(
                module.ownerPrompt("Find the hard part", Sensitivity.S3));

        assertFalse(foreground.toCompletableFuture().isDone());
        assertEquals(1, reasoning.durableRequests.size());
        assertEquals(1, module.pendingBackgroundCount());

        reasoning.immediate.complete(result("Immediate result"));
        assertEquals("Immediate result", foreground.toCompletableFuture().join().payload());
        assertEquals(ExecutionMode.DURABLE, reasoning.durableRequests.get(0).mode());
        assertTrue(((TextInferenceCommand) reasoning.durableRequests.get(0).computation())
                .prompt().contains("NO_FOLLOW_UP"));
    }

    @Test void shippedModuleInterpretsAndPersistsBackgroundAcrossRestart() {
        RecordingReasoning reasoning = new RecordingReasoning();
        reasoning.immediate.complete(result("Immediate"));
        Path state = temporary.resolve("core-state");
        OwnerInteractionModule first = new OwnerInteractionModule(reasoning, state);
        first.fastLane(first.ownerPrompt("Continue this", Sensitivity.S5))
                .toCompletableFuture().join();
        WorkId work = reasoning.lastSubmitted;
        reasoning.complete(work, result("A useful correction"));

        OwnerInteractionModule restarted = new OwnerInteractionModule(reasoning, state);
        List<BackgroundUpdate> updates = restarted.collectBackground();

        assertEquals(1, updates.size());
        assertEquals(OwnerInteractionModule.BACKGROUND_ANALYSIS,
                updates.get(0).backgroundAnalysis().orElseThrow().type());
        assertEquals(OwnerInteractionModule.VISIBLE_FOLLOW_UP,
                updates.get(0).visibleFollowUp().orElseThrow().type());
        assertEquals(Sensitivity.S5,
                updates.get(0).visibleFollowUp().orElseThrow().sensitivity());
        assertTrue(reasoning.acknowledged.contains(work));
        assertEquals(0, restarted.pendingBackgroundCount());
    }

    @Test void noFollowUpStopsSemanticContinuation() {
        RecordingReasoning reasoning = new RecordingReasoning();
        reasoning.immediate.complete(result("Immediate"));
        OwnerInteractionModule module = new OwnerInteractionModule(reasoning,
                temporary.resolve("state"));
        module.fastLane(module.ownerPrompt("Simple", Sensitivity.S2))
                .toCompletableFuture().join();
        reasoning.complete(reasoning.lastSubmitted, result("NO_FOLLOW_UP"));

        BackgroundUpdate update = module.collectBackground().get(0);
        assertTrue(update.backgroundAnalysis().isPresent());
        assertTrue(update.visibleFollowUp().isEmpty());
    }

    @Test void publicDefinitionRoundTripsWithoutBehavior() {
        OwnerInteractionModule module = new OwnerInteractionModule(
                new RecordingReasoning(), temporary.resolve("state"));
        ModuleDefinitionJsonCodec codec = new ModuleDefinitionJsonCodec();
        String encoded = codec.encode(module.definition());
        var decoded = codec.decode(encoded);

        assertEquals(OwnerInteractionModule.ID, decoded.id());
        assertEquals(3, decoded.operations().size());
        assertEquals(1, decoded.agents().size());
        assertFalse(encoded.contains("OwnerInteractionModule"));
        assertFalse(encoded.contains("NO_FOLLOW_UP"));
    }

    private static TextInferenceResult result(String text) {
        return new TextInferenceResult(text, TextInferenceResult.CompletionReason.STOP, 2, 3);
    }

    private static final class RecordingReasoning implements ReasoningService {
        private final CompletableFuture<TextInferenceResult> immediate = new CompletableFuture<>();
        private final List<ReasoningRequest<?, ?>> durableRequests = new ArrayList<>();
        private final Map<WorkId, TextInferenceResult> results = new HashMap<>();
        private final List<WorkId> acknowledged = new ArrayList<>();
        private ReasoningRequest<?, ?> immediateRequest;
        private WorkId lastSubmitted;

        @Override public <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
                ReasoningRequest<R, C> request) {
            immediateRequest = request;
            @SuppressWarnings("unchecked") CompletionStage<R> cast =
                    (CompletionStage<R>) (CompletionStage<?>) immediate;
            return cast;
        }

        @Override public <R, C extends ReasoningComputation<R>> WorkId submit(
                ReasoningRequest<R, C> request) {
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
                    Optional.<ReasoningFailureCategory>empty(), Optional.empty()));
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
