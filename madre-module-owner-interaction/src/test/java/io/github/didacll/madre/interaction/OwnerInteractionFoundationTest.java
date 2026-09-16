package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Integrity;
import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.sdk.execution.ReasoningComputation;
import io.github.didacll.madre.sdk.execution.ReasoningRequest;
import io.github.didacll.madre.sdk.execution.ReasoningService;
import io.github.didacll.madre.sdk.execution.WorkId;
import io.github.didacll.madre.sdk.execution.WorkStatus;
import io.github.didacll.madre.sdk.identity.MaterialId;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.OwnerInteractionAgent;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.operation.OwnerInteractionInvoker;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OwnerInteractionFoundationTest {
    @TempDir Path temporary;

    @Test void shippedCoreAgentUsesOnlyTheCurrentlyDefensibleIntegrityClaim() {
        OwnerInteractionModule module = new OwnerInteractionModule(new InertReasoning(),
                temporary.resolve("state"));

        var agent = module.agents().stream().findFirst().orElseThrow();

        assertEquals(Integrity.I2, agent.integrity());
    }

    @Test void privateNotesStillExerciseHighlySensitiveKnowledgeWithoutCredentialSemantics() {
        OwnerKnowledgeIntent intent = OwnerKnowledgeIntent.parse(
                "Remember my private note is cedar-7391-private").orElseThrow();

        assertEquals(OwnerKnowledgeIntent.Action.PUT, intent.action());
        assertEquals(OwnerKnowledgeKind.HIGHLY_SENSITIVE, intent.kind());
        assertEquals("private note", intent.key());
    }

    @Test void credentialLikeStoreAndRevealRequestsBecomeNonStoringRemovalIntents() {
        OwnerKnowledgeIntent store = OwnerKnowledgeIntent.parse(
                "Remember my API key is should-not-be-stored").orElseThrow();
        OwnerKnowledgeIntent reveal = OwnerKnowledgeIntent.parse(
                "Show me my private key").orElseThrow();

        assertEquals(OwnerKnowledgeIntent.Action.REMOVE, store.action());
        assertEquals("API key", store.key());
        assertTrue(store.value().isEmpty());
        assertEquals(OwnerKnowledgeIntent.Action.REMOVE, reveal.action());
        assertEquals("private key", reveal.key());
    }

    @Test void rejectedCredentialTextRetainsHighlySensitiveClassificationUntilDiscarded() {
        OwnerInteractionModule module = new OwnerInteractionModule(new InertReasoning(),
                temporary.resolve("credential-state"));
        OwnerInteractionAgent agent = module.agents().stream()
                .filter(OwnerInteractionAgent.class::isInstance)
                .map(OwnerInteractionAgent.class::cast)
                .findFirst().orElseThrow();
        AtomicReference<OperationCall<?, ?>> captured = new AtomicReference<>();
        OwnerInteractionInvoker invoker = new OwnerInteractionInvoker() {
            @Override public <I, O> CompletionStage<Material<O>> invokeOwnerInteraction(
                    OperationCall<I, O> call) {
                captured.set(call);
                Material<String> answer = new Material<>(
                        new MaterialId(OwnerInteractionModule.ID, "credential-rejection"),
                        OwnerInteractionModule.IMMEDIATE_ANSWER,
                        "I wasn't storing your API key.", Sensitivity.S2);
                @SuppressWarnings("unchecked") Material<O> cast = (Material<O>) answer;
                return CompletableFuture.completedFuture(cast);
            }
        };

        agent.respond(invoker, "Remember my API key is should-not-be-stored", Sensitivity.S1)
                .toCompletableFuture().join();

        assertEquals(Sensitivity.S5, captured.get().input().sensitivity());
    }

    private static final class InertReasoning implements ReasoningService {
        @Override public <R, C extends ReasoningComputation<R>> CompletionStage<R> execute(
                ReasoningRequest<R, C> request) {
            throw new UnsupportedOperationException("not needed by this contract test");
        }

        @Override public <R, C extends ReasoningComputation<R>> WorkId submit(
                ReasoningRequest<R, C> request) {
            throw new UnsupportedOperationException("not needed by this contract test");
        }

        @Override public Optional<WorkStatus> inspect(WorkId id) { return Optional.empty(); }
        @Override public boolean cancel(WorkId id) { return false; }
        @Override public <R> Optional<R> collect(WorkId id, Class<R> resultType) {
            return Optional.empty();
        }
        @Override public boolean acknowledge(WorkId id) { return false; }
    }
}
