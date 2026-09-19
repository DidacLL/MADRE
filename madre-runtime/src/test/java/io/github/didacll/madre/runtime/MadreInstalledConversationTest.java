package io.github.didacll.madre.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.didacll.madre.algebra.Sensitivity;
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
import io.github.didacll.madre.sdk.identity.AgentId;
import io.github.didacll.madre.sdk.identity.ModuleId;
import io.github.didacll.madre.sdk.module.ConversationMessage;
import io.github.didacll.madre.sdk.registration.ModuleProvider;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MadreInstalledConversationTest {
    @TempDir Path temporary;

    @Test
    void ownerEntersAssignedCoreAndReceivesRealInferenceResult() {
        AtomicReference<ChatCompletionInput> observed = new AtomicReference<>();
        try (InferenceKernel kernel = new InferenceKernel(
                temporary.resolve("kernel.db"), 1, Map.of())) {
            kernel.register(new ConversationEngine(observed));

            RuntimeModuleRegistry registry = new RuntimeModuleRegistry();
            RuntimeInferenceService inference = new RuntimeInferenceService(
                    kernel, temporary.resolve("semantic"));
            MadreRuntime runtime =
                    new MadreRuntime(registry, inference, temporary);
            ServiceLoader.load(ModuleProvider.class).forEach(runtime::install);

            ModuleId core = new ModuleId("owner-interaction");
            AgentId conversation = new AgentId(core, "conversation");
            assertTrue(registry.modules().stream()
                    .anyMatch(module -> module.definition().id().equals(core)));
            registry.assignCore(core, conversation);

            ConversationMessage result = runtime.respond(
                    new ConversationMessage(
                            ConversationMessage.Role.HUMAN,
                            "hello from owner",
                            Sensitivity.S4))
                    .toCompletableFuture()
                    .join();

            assertEquals(ConversationMessage.Role.AGENT, result.role());
            assertEquals("integrated answer", result.text());
            assertEquals(Sensitivity.S4, result.sensitivity());
            assertEquals(1, observed.get().messages().size());
            assertEquals("hello from owner", observed.get().messages().get(0).text());
        }
    }

    private static final class ConversationEngine
            implements InferenceEngine<ChatCompletionInput, ChatCompletionOutput> {
        private final AtomicReference<ChatCompletionInput> observed;

        private ConversationEngine(AtomicReference<ChatCompletionInput> observed) {
            this.observed = observed;
        }

        @Override public EngineId id() {
            return new EngineId("conversation-engine");
        }

        @Override public InferenceType<ChatCompletionInput, ChatCompletionOutput> type() {
            return InferenceTypes.CHAT_COMPLETION;
        }

        @Override public EngineCharacteristics characteristics() {
            return new EngineCharacteristics(
                    "fixture-provider",
                    "fixture-model",
                    URI.create("https://fixture.invalid/api"),
                    Duration.ofMillis(1),
                    Optional.of(
                            new EngineCharacteristics.ChatCompletionCapability(4096)));
        }

        @Override public EngineAvailability availability() {
            return EngineAvailability.AVAILABLE;
        }

        @Override public ChatCompletionOutput execute(
                ChatCompletionInput input, EngineExecution execution) {
            observed.set(input);
            return new ChatCompletionOutput(
                    "integrated answer",
                    ChatCompletionOutput.FinishReason.ENGINE_STOP,
                    ChatCompletionOutput.TokenUsage.unavailable());
        }
    }
}
