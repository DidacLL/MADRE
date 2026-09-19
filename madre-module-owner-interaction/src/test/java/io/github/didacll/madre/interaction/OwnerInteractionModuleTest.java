package io.github.didacll.madre.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.didacll.madre.algebra.Sensitivity;
import io.github.didacll.madre.generation.TextGenerationCommand;
import io.github.didacll.madre.generation.TextGenerationResult;
import io.github.didacll.madre.sdk.material.Material;
import io.github.didacll.madre.sdk.module.Agent;
import io.github.didacll.madre.sdk.module.AgentContext;
import io.github.didacll.madre.sdk.module.ConversationMessage;
import io.github.didacll.madre.sdk.module.ConversationalAgent;
import io.github.didacll.madre.sdk.module.ModuleInstance;
import io.github.didacll.madre.sdk.module.OperationBinding;
import io.github.didacll.madre.sdk.operation.ModuleInvoker;
import io.github.didacll.madre.sdk.operation.OperationCall;
import io.github.didacll.madre.sdk.testing.ProgrammableReasoningService;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class OwnerInteractionModuleTest {
    @Test
    void isAnOrdinarySdkModuleWithOneConversationalAgent() {
        OwnerInteractionModule module = new OwnerInteractionModule();
        ModuleInstance installed = ModuleInstance.from(module);

        assertEquals(OwnerInteractionModule.ID, installed.definition().id());
        assertEquals(Set.of(OwnerInteractionModule.RESPOND),
                installed.definition().exposedOperations());
        assertEquals(1, installed.agents().size());
        assertInstanceOf(ConversationalAgent.class,
                installed.agents().get(OwnerInteractionModule.CONVERSATION_AGENT));
    }

    @Test
    void conversationalAdapterEntersRespondOperationExactlyOnce() {
        OwnerInteractionModule module = new OwnerInteractionModule();
        ModuleInstance installed = module.instance();
        ConversationalAgent agent = module.conversationalAgent();
        AtomicInteger operationInvocations = new AtomicInteger();
        AtomicInteger reasoningInvocations = new AtomicInteger();
        ProgrammableReasoningService reasoning = new ProgrammableReasoningService()
                .respond(TextGenerationCommand.class, command -> {
                    reasoningInvocations.incrementAndGet();
                    return new TextGenerationResult("answer",
                            TextGenerationResult.CompletionReason.STOP, -1, -1);
                });
        AtomicReference<AgentContext> contextReference = new AtomicReference<>();
        ModuleInvoker invoker = new ModuleInvoker() {
            @Override
            public <I, O> CompletionStage<Material<O>> invoke(OperationCall<I, O> call) {
                assertEquals(OwnerInteractionModule.RESPOND, call.operation().id());
                operationInvocations.incrementAndGet();
                return executeInstalled(installed, agent, contextReference.get(), call);
            }
        };
        AgentContext context = new AgentContext(agent, reasoning, query -> List.of(), invoker,
                Path.of("."));
        contextReference.set(context);

        ConversationMessage response = agent.respond(context,
                new ConversationMessage(ConversationMessage.Role.HUMAN, "hello",
                        Sensitivity.S5))
                .toCompletableFuture().join();

        assertEquals(1, operationInvocations.get());
        assertEquals(1, reasoningInvocations.get());
        assertEquals(ConversationMessage.Role.AGENT, response.role());
        assertEquals("answer", response.text());
    }

    @SuppressWarnings("unchecked")
    private static <I, O> CompletionStage<Material<O>> executeInstalled(
            ModuleInstance installed, Agent actor, AgentContext context,
            OperationCall<I, O> call) {
        OperationBinding<?, ?> raw = installed.operations().get(call.operation().id());
        if (raw == null) {
            throw new IllegalArgumentException("Operation is not installed: " + call.operation().id());
        }
        return actor.execute(context, (OperationBinding<I, O>) raw, call);
    }
}
